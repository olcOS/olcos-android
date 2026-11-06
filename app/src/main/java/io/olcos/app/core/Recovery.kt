package io.olcos.app.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.olcos.app.data.ConnectionStore
import io.olcos.app.model.ConnectionRecord
import io.olcos.app.model.ServerRecord

/**
 * How a session that was lost comes back, as the iOS app's reconnect loop
 * does it (TunnelManager.requestReconnect): the rooms are joined again under
 * ReconnectPolicy, a fast phase of jittered waits and then one try every
 * minute or two, with no limit while the person has not disconnected. A
 * connection that keeps failing gives way to another protocol of the same
 * server, then to a connection whose last check passed. Only a permanent
 * error (a wrong key, a room that is gone) stops it, and its real text is
 * shown. A start the person asked for never enters this: its failure is
 * shown at once.
 */
object Recovery {
    /** With no network at all, a recovery waits for one (a tunnel, the underground) and spends no try. */
    const val OFFLINE_RETRY_MS = 10_000L

    /**
     * Whether the phone has a network with internet now. The app's own
     * sockets stay off the VPN, so in VPN mode too this is the network the
     * rooms would run over.
     */
    fun networkUp(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * PURE: where to go when [current] stops working: the other protocols of
     * its server first (the ones the server made, or its lists brought), then
     * a connection of another server this phone runs itself whose last check
     * passed, fastest first. Nothing unchecked or failing (a tour of dead
     * servers would only make the outage longer) and nothing the person did
     * not set up: traffic never moves to a public or somebody else's server
     * on its own.
     */
    fun candidates(
        current: ConnectionRecord,
        records: List<ConnectionRecord>,
        servers: List<ServerRecord>,
        checks: Map<String, Health.Check>,
    ): List<ConnectionRecord> {
        val home = servers.firstOrNull { AgentClient.isThrough(it, current.id, current.subSource) }
        val siblings = records.filter { record ->
            record.id != current.id && when {
                home != null -> AgentClient.isThrough(home, record.id, record.subSource)
                current.subSource != null -> record.subSource?.let { ConnectionStore.canonicalSource(it) } ==
                    ConnectionStore.canonicalSource(current.subSource)
                else -> false
            }
        }.sortedBy { checks[it.id]?.ms ?: Long.MAX_VALUE }
        val taken = siblings.map { it.id }.toSet() + current.id
        // Only a server this phone runs itself: a free public node, or one from a list somebody else keeps, passed
        // its check once, but the person never chose to send their traffic there.
        val working = records.filter { r ->
            r.id !in taken && checks[r.id]?.ms != null && servers.any { AgentClient.isThrough(it, r.id, r.subSource) }
        }.sortedBy { checks[it.id]?.ms ?: Long.MAX_VALUE }
        return siblings + working
    }

    /**
     * One recovery run of a service: the connection it is on now, the tries
     * spent on it and the connections already tried, so a move never comes
     * back to one of them.
     */
    class Run(
        start: ConnectionRecord,
        startAttempt: Int = 0,
        private val random: () -> Double = { Math.random() },
    ) {
        var record: ConnectionRecord = start
            private set
        /** Tries spent on [record] so far; where the count stood when the session was joined. */
        var attempt = startAttempt.coerceAtLeast(0)
            private set
        private val tried = mutableSetOf(start.id)

        /** The wait before the first try. */
        fun firstDelay(): Long = ReconnectPolicy.delayMs(attempt, random())

        /** A network came back or changed: the tries spent on the old one say nothing of the new. */
        fun restart() {
            attempt = 0
        }

        /**
         * After a failed try with [raw] as the core's words: the wait before
         * the next one, on [record] (which may now be another connection), or
         * null when the error is permanent and the real text should be shown.
         * [next] is the connection to move to once this one has had its tries.
         */
        fun afterFailure(online: Boolean, raw: String?, next: (ConnectionRecord, Set<String>) -> ConnectionRecord?): Long? {
            // No network: nothing was learned about this connection, so no try is spent on it.
            if (!online) return OFFLINE_RETRY_MS
            if (raw != null && ReconnectPolicy.isPermanent(raw, severalLanes = (record.olcrtc?.lanes?.size ?: 1) > 1)) return null
            attempt = (attempt + 1).coerceAtMost(ATTEMPT_CEILING)
            if (attempt >= ReconnectPolicy.failoverAfter(record.olcrtc?.isBundle == true)) {
                val hop = next(record, tried)
                if (hop != null) {
                    AppLog.i("olcos.recovery", "${record.displayName} keeps failing: moving to ${hop.displayName}")
                    tried += hop.id
                    record = hop
                    attempt = 0
                }
            }
            return ReconnectPolicy.delayMs(attempt, random())
        }
    }

    /** Keeps the count finite however long a recovery runs. */
    private const val ATTEMPT_CEILING = 1_000_000

    /**
     * The count one recovery leaves for the next: a session that held for a
     * minute starts the next from zero, one that dropped again at once goes
     * on where the count stood.
     */
    class Carry {
        private var connectedAtMs: Long? = null
        private var attempts = 0

        fun connected(nowMs: Long, attempts: Int) {
            connectedAtMs = nowMs
            this.attempts = attempts
        }

        fun nextStart(nowMs: Long): Int =
            ReconnectPolicy.startingAttempt(attempts, connectedAtMs?.let { nowMs - it })
    }
}
