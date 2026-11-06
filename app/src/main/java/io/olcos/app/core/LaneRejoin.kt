package io.olcos.app.core

/**
 * How lanes that dropped together come back to their rooms. Two rooms of one
 * carrier sit on one host and fail together (the owner's two Jitsi rooms do,
 * most of the time), and asking every room again in the same second only
 * repeats the burst that lost them. The iOS app does the same
 * (VPNConfig.jitteredLaneDelay and laneRejoinOffset).
 */
object LaneRejoin {
    /** PURE: the wait before a lane's next try, somewhere in the upper half of its [delayMs] backoff; [random] in 0..1. */
    fun jitteredDelayMs(delayMs: Long, random: Double): Long =
        (delayMs * (0.5 + 0.5 * random.coerceIn(0.0, 1.0))).toLong()

    /**
     * One pass of the lane watch: at most one lane per carrier rejoins in it,
     * the others wait for the next pass (a few seconds later), so rooms on one
     * host do not come back in step.
     */
    class Check {
        private val started = mutableSetOf<String>()

        fun allow(carrier: String): Boolean = started.add(carrier.lowercase())
    }
}
