package io.olcos.app.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Recognising that the system, not the person, ended a session, and what the
 * person can do about it. A session the person ended leaves nothing remembered
 * (AppSettings.rememberSession(null)); one that is still wanted when the
 * process is gone, or when the system restarts the service on its own, was cut
 * by the system (a battery saver, a vendor's cleaner). The first time that
 * happens a short guide points at the app's battery settings and the system's
 * always-on VPN setting.
 */
object KillSignal {
    /**
     * PURE: whether the session that was wanted was cut by the system.
     * [restartedBySystem]: the service was started with no intent of the app's
     * (a sticky restart after a kill, or the system's always-on VPN, which
     * [alwaysOn] then explains). [sameBoot] and [updatedSince]: the phone has
     * not restarted and the app has not been updated since the session began,
     * which would explain a process that is gone just as well.
     */
    fun wasKilled(
        sessionWanted: Boolean,
        restartedBySystem: Boolean,
        alwaysOn: Boolean,
        sameBoot: Boolean,
        updatedSince: Boolean,
        serviceRunning: Boolean,
    ): Boolean {
        if (!sessionWanted) return false
        if (restartedBySystem) return !alwaysOn
        return sameBoot && !updatedSince && !serviceRunning
    }

    fun shouldShowGuide(killed: Boolean, alreadyShown: Boolean): Boolean = killed && !alreadyShown

    /** PURE: whether the system limits what the app may do in the background. */
    fun isRestricted(ignoresBatteryOptimization: Boolean, backgroundRestricted: Boolean, alwaysOn: Boolean = false): Boolean =
        backgroundRestricted || (!ignoresBatteryOptimization && !alwaysOn)

    fun isRestricted(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java)
        val ignoring = power?.isIgnoringBatteryOptimizations(context.packageName) == true
        val manager = context.getSystemService(ActivityManager::class.java)
        val restricted = Build.VERSION.SDK_INT >= 28 && manager?.isBackgroundRestricted == true
        return isRestricted(ignoring, restricted)
    }

    /** The app's own page in the system settings: its battery use is one tap from there. */
    fun batterySettings(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The system's VPN settings, where "Always-on VPN" is. */
    fun vpnSettings(): Intent = Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The phone's boot count (-1 where it cannot be read): a reboot explains a process that is gone. */
    fun bootCount(context: Context): Int =
        runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)
}
