package io.olcos.app.core

import android.os.Handler
import android.os.Looper
import io.olcos.app.AppGraph
import kotlin.concurrent.thread

/**
 * Keeps a live session true to its name, as the iOS app's keep-alive does.
 * Every [KeepAlivePolicy.INTERVAL_MS] a request goes through the session; a
 * miss is confirmed by a second probe a few seconds later and two in a row
 * tell the service once that the session is lost. Traffic that came back to
 * the phone through the session skips a round's probe, but never more than two
 * rounds in a row: the byte counter of the tunnel also moves with packets the
 * phone itself makes, so it cannot prove the far end alive. The rooms' own
 * dropping out is the lane watch's (OlcrtcEngine) and SessionWatch's, and a
 * change of the phone's network is the service's (NetworkPath); this catches a
 * session that looks up and carries nothing.
 */
class SessionKeeper(
    private val session: SessionInfo,
    /** Bytes the session has handed back to the phone so far; null when that cannot be told (proxy mode). */
    private val received: () -> Long?,
    /** Called once, on the main thread, with the reason. */
    private val onLost: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var stopped = false
    private var loop: Thread? = null

    fun start() {
        loop = thread(name = "olcos-keep-alive", isDaemon = true) { probeLoop() }
    }

    /** Safe to call more than once, from any thread. */
    fun stop() {
        stopped = true
        loop?.interrupt()
        loop = null
    }

    private fun probe(): Boolean =
        TunnelProbe.verify(session.port, session.socksUser, session.socksPass, KeepAlivePolicy.PROBE_TIMEOUT_MS)

    private fun probeLoop() {
        var skipped = 0
        var lastReceived = received()
        while (!stopped) {
            try {
                Thread.sleep(KeepAlivePolicy.INTERVAL_MS)
            } catch (e: InterruptedException) {
                return
            }
            if (stopped) return
            // Switched off, rooms being joined again (SessionWatch has that), or
            // a speed test filling the rooms: nothing for this round to decide.
            if (!AppGraph.settings.keepAlive.value ||
                TunnelManager.state.value !is TunnelManager.State.Connected ||
                SpeedTest.running.value
            ) {
                skipped = 0
                lastReceived = received()
                continue
            }
            val now = received()
            val before = lastReceived
            lastReceived = now
            val trafficCameBack = now != null && before != null && now - before >= ACTIVITY_BYTES
            if (!KeepAlivePolicy.shouldProbe(trafficCameBack, skipped)) {
                skipped++
                continue
            }
            skipped = 0
            if (probe()) continue
            if (stopped) return
            // One miss is not a verdict: ask again shortly, whatever traffic came meanwhile.
            var misses = 1
            AppLog.w(TAG, "keep-alive: no answer through the tunnel, asking again")
            try {
                Thread.sleep(KeepAlivePolicy.CONFIRM_DELAY_MS)
            } catch (e: InterruptedException) {
                return
            }
            if (stopped) return
            if (!probe()) misses++
            if (stopped) return
            if (KeepAlivePolicy.isDead(misses)) {
                lost("no answer through the tunnel $misses times in a row")
                return
            }
            AppLog.i(TAG, "keep-alive: the tunnel answered the second time")
        }
    }

    private fun lost(reason: String) {
        main.post {
            if (stopped) return@post
            stop()
            AppLog.w(TAG, "session lost: $reason")
            onLost(reason)
        }
    }

    companion object {
        private const val TAG = "olcos.keepalive"
        /** Traffic back to the phone in one interval that shows the session moving. */
        private const val ACTIVITY_BYTES = 4_096L
    }
}
