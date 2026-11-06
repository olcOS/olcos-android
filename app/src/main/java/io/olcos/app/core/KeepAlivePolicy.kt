package io.olcos.app.core

/**
 * When the keep-alive probes a live session and when it calls it dead; the
 * iOS app's numbers (ReconnectPolicy.confirmProbeDelay and missesForDead).
 *
 * The byte counter of the tunnel moves with packets the phone itself makes, so
 * it cannot prove the far end alive: traffic that came back skips a round's
 * probe, but a real end-to-end probe still runs at least every third round. A
 * miss is confirmed by a second probe five seconds later (traffic does not
 * excuse it), and two misses in a row mean dead.
 */
object KeepAlivePolicy {
    /** A check every half minute. */
    const val INTERVAL_MS = 30_000L
    /** A round's probe may be skipped this many times in a row before one must run. */
    const val MAX_SKIPPED = 2
    const val CONFIRM_DELAY_MS = ReconnectPolicy.CONFIRM_PROBE_DELAY_MS
    const val MISSES_FOR_DEAD = ReconnectPolicy.MISSES_FOR_DEAD
    const val PROBE_TIMEOUT_MS = 10_000

    /** PURE: whether this round runs its probe. */
    fun shouldProbe(trafficCameBack: Boolean, skippedInARow: Int): Boolean =
        !trafficCameBack || skippedInARow >= MAX_SKIPPED

    /** PURE: whether [misses] consecutive missed probes mean the session is dead. */
    fun isDead(misses: Int): Boolean = misses >= MISSES_FOR_DEAD
}
