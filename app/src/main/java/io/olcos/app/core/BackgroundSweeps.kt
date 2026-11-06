package io.olcos.app.core

import android.content.Context
import io.olcos.app.AppGraph
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The rooms being changed right now: the sweep and a server page can reach the same one together. */
class InFlight {
    private val keys = HashSet<String>()

    @Synchronized
    fun tryEnter(key: String): Boolean = keys.add(key)

    @Synchronized
    fun leave(key: String) {
        keys.remove(key)
    }
}

/**
 * The room renewal and the room schedules ran only while the app had a screen: a phone kept connected in the
 * background, the typical always-on one, never renewed a Telemost room or kept a schedule. While a tunnel
 * service runs it now asks the same questions as the open app does, the Telemost one at most once an hour and
 * the rotation one as often as RoomRotationPolicy wants (every ten minutes while a rule has a window).
 */
object BackgroundSweeps {
    const val TELEMOST_EVERY_MS = 60 * 60_000L
    private const val MIN_WAIT_MS = 60_000L

    /** PURE: whether the Telemost pass is due; a clock that went back counts as due. */
    fun telemostDue(lastMs: Long?, nowMs: Long): Boolean = lastMs == null || nowMs < lastMs || nowMs - lastMs >= TELEMOST_EVERY_MS

    /** PURE: the wait before the next pass: what the rotation policy asks, never under a minute. */
    fun waitMs(policyMs: Long): Long = policyMs.coerceAtLeast(MIN_WAIT_MS)

    /** Runs until [alive] says the session is over or the returned job is cancelled. */
    fun start(context: Context, alive: () -> Boolean): Job = AppGraph.scope.launch {
        var lastTelemost: Long? = null
        while (isActive && alive()) {
            delay(waitMs(RoomRotationPolicy.sweepIntervalMs(AppGraph.settings.roomRules.value, AppGraph.settings.roomWindows.value)))
            if (!isActive || !alive()) break
            val now = System.currentTimeMillis()
            if (telemostDue(lastTelemost, now)) {
                lastTelemost = now
                runCatching { TelemostRenewal.sweep(context) }
            }
            runCatching { RoomRotation.sweep(context) }
        }
    }
}
