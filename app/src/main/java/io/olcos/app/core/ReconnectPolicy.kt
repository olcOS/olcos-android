package io.olcos.app.core

/**
 * How long a lost session waits before it joins its rooms again, and what stops
 * it. The iOS app runs the same numbers (ReconnectPolicy in VPNConfig.swift).
 *
 * A fast phase of full jitter: each wait is a random point in
 * [0, min(60 s, 1 s * 2^n)], never under half a second, so phones that lost a
 * room together do not come back to it together. Once that ceiling reaches the
 * cap, one try every 60-120 s with no limit, for as long as the person wants
 * the connection and a network is there. Only a permanent error stops it. A
 * connection that held for a minute starts the count over; a network that came
 * back or changed is tried after a short settle, since a hand-over sends
 * several updates.
 */
object ReconnectPolicy {
    const val FLOOR_MS = 500L
    const val CAP_MS = 60_000L
    /** Tries in the fast phase: its last ceiling, 1 s * 2^6, is the cap. */
    const val FAST_ATTEMPTS = 7
    const val SLOW_MIN_MS = 60_000L
    const val SLOW_MAX_MS = 120_000L
    /** How long a connection must hold before a later drop counts from zero. */
    const val STABLE_AFTER_MS = 60_000L
    const val NETWORK_SETTLE_MIN_MS = 500L
    const val NETWORK_SETTLE_MAX_MS = 1_000L
    /** A missed liveness probe is confirmed by another this much later. */
    const val CONFIRM_PROBE_DELAY_MS = 5_000L
    /** Consecutive missed probes that mean the connection is dead. */
    const val MISSES_FOR_DEAD = 2
    /** Tries on one connection before auto-failover may move to another one. */
    private const val FAILOVER_PER_CANDIDATE = 3

    fun isSlowPhase(attempt: Int): Boolean = attempt >= FAST_ATTEMPTS

    /** PURE: the wait before try [attempt] (0 is the first); [random] in 0..1. */
    fun delayMs(attempt: Int, random: Double): Long {
        val r = random.coerceIn(0.0, 1.0)
        if (isSlowPhase(attempt)) return SLOW_MIN_MS + ((SLOW_MAX_MS - SLOW_MIN_MS) * r).toLong()
        val ceiling = minOf(CAP_MS, 1_000L shl attempt.coerceIn(0, 20))
        return maxOf(FLOOR_MS, (ceiling * r).toLong())
    }

    /** PURE: the wait before the first try on a network that came back or changed. */
    fun networkSettleMs(random: Double): Long =
        NETWORK_SETTLE_MIN_MS + ((NETWORK_SETTLE_MAX_MS - NETWORK_SETTLE_MIN_MS) * random.coerceIn(0.0, 1.0)).toLong()

    /**
     * PURE: where a new recovery starts counting: on from [previous] when the
     * connection it brought back held for less than a minute, else from zero
     * (null: it never connected).
     */
    fun startingAttempt(previous: Int, heldForMs: Long?): Int {
        if (heldForMs == null || heldForMs >= STABLE_AFTER_MS) return 0
        return maxOf(previous, 0)
    }

    /**
     * PURE: tries on a connection before auto-failover may leave it. A
     * whole-server connection is the person's choice of every room at once and
     * its try fails only when none of them answers: it keeps its whole fast
     * phase and three slow tries before another connection stands in.
     */
    fun failoverAfter(bundle: Boolean): Int =
        if (bundle) FAST_ATTEMPTS + FAILOVER_PER_CANDIDATE else FAILOVER_PER_CANDIDATE

    private enum class Cause { KEY, OUTDATED, NO_PEER, ROOM_INVALID, DNS, CARRIER_REFUSED, OTHER }

    /**
     * PURE: whether a failed join can only fail again the same way, so the
     * loop stops and shows it: a wrong key, a core the server does not speak,
     * a room that is gone, or a carrier that refused access or no longer has
     * the room (HTTP 401/403/404/406/410). A connection over several lanes
     * stops only on what they all share, the key and the server's core: one
     * lane's room says nothing of the others.
     */
    fun isPermanent(raw: String, severalLanes: Boolean): Boolean = when (causeOf(raw.lowercase())) {
        Cause.KEY, Cause.OUTDATED -> true
        Cause.ROOM_INVALID -> !severalLanes
        Cause.CARRIER_REFUSED -> !severalLanes && refusedForGood(raw.lowercase())
        else -> false
    }

    /** The first match wins: keep this order (the iOS HealthFailureMapper's). */
    private fun causeOf(s: String): Cause {
        fun any(vararg needles: String) = needles.any { it in s }
        // The core names the cause of a failed first handshake itself; a wrong key still ends in
        // "read welcome ... timeout", which must not be filed as nobody answering.
        if ("key does not match the peer" in s) return Cause.KEY
        if ("incompatible olcrtc protocol" in s) return Cause.OUTDATED
        if (any("peer did not answer the handshake", "no peer in room")) return Cause.NO_PEER
        // A handshake that ran out of time is not a key problem: the hello went out and nobody answered.
        if (any("read welcome", "handshake client", "send hello") && any("timeout", "timed out", "deadline exceeded", "context canceled")) {
            return Cause.NO_PEER
        }
        if (any("read welcome", "handshake client", "handshake rejected", "record authentication failed",
                "bad record magic", "invalid key size", "challenge mismatch")) return Cause.KEY
        if (any("readiness timed out", "stopped before becoming ready", "no peer")) return Cause.NO_PEER
        if (any("invalid room url", "room not found", "room id is required", "room is required", "room url must",
                "room url required", "malformed room reference", "<code>:<password>", "event join link",
                "no such room", "has no session")) return Cause.ROOM_INVALID
        // The configured resolver itself did not answer: says nothing of the room.
        if ("lookup " in s && ":53" in s && any("i/o timeout", "timed out", "timeout", "deadline exceeded",
                "connection refused", "network is unreachable", "no route to host", "server misbehaving")) return Cause.DNS
        if (any("telemost api", "guest register", "join room", "get token", "api error", "auth provider not found",
                "unsupported provider", "did not accept the guest", " 403", " 401", "forbidden", "unauthorized")) return Cause.CARRIER_REFUSED
        return Cause.OTHER
    }

    private val STATUS = Regex("(?<![0-9.:])(401|403|404|406|410)(?![0-9.:])")

    /** A carrier's answer that will not change on a retry; a server error, a rate limit or a dropped connection will. */
    private fun refusedForGood(s: String): Boolean {
        if (listOf("forbidden", "unauthorized", "not found", "gone", "not acceptable", "did not accept the guest",
                "auth provider not found", "unsupported provider").any { it in s }) return true
        return STATUS.containsMatchIn(s)
    }
}
