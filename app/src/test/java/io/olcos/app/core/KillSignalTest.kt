package io.olcos.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KillSignalTest {
    private fun killed(
        sessionWanted: Boolean = true,
        restartedBySystem: Boolean = false,
        alwaysOn: Boolean = false,
        sameBoot: Boolean = true,
        updatedSince: Boolean = false,
        serviceRunning: Boolean = false,
    ) = KillSignal.wasKilled(sessionWanted, restartedBySystem, alwaysOn, sameBoot, updatedSince, serviceRunning)

    @Test fun aServiceTheSystemRestartedAfterAKillIsOne() {
        assertTrue(killed(restartedBySystem = true))
    }

    @Test fun anAlwaysOnVpnTheSystemStartsIsNotAKillWhatever() {
        assertFalse(killed(restartedBySystem = true, alwaysOn = true))
    }

    @Test fun aCleanStopLeavesNothingToRecognise() {
        assertFalse(killed(sessionWanted = false, restartedBySystem = true))
        assertFalse(killed(sessionWanted = false))
    }

    @Test fun aProcessThatDiedWithASessionWantedIsOneWhenNothingElseExplainsIt() {
        assertTrue(killed())
    }

    @Test fun aRebootExplainsAProcessThatIsGone() {
        assertFalse(killed(sameBoot = false))
    }

    @Test fun anUpdateOfTheAppExplainsAProcessThatIsGone() {
        assertFalse(killed(updatedSince = true))
    }

    @Test fun aServiceThatIsRunningHasNotBeenKilled() {
        assertFalse(killed(serviceRunning = true))
    }

    @Test fun theSystemsRestartIsNotExcusedByARebootOrAnUpdate() {
        // A restart the system delivers says the service was killed in this run of the phone.
        assertTrue(killed(restartedBySystem = true, sameBoot = false, updatedSince = true))
    }

    @Test fun theGuideIsShownOnceThenNeverAgain() {
        assertTrue(KillSignal.shouldShowGuide(killed = true, alreadyShown = false))
        assertFalse(KillSignal.shouldShowGuide(killed = true, alreadyShown = true))
        assertFalse(KillSignal.shouldShowGuide(killed = false, alreadyShown = false))
    }

    // Settings -> Connection -> Background work.

    @Test fun theAppIsUnrestrictedOnlyWhenNeitherTheBatteryNorTheSystemLimitsIt() {
        assertFalse(KillSignal.isRestricted(ignoresBatteryOptimization = true, backgroundRestricted = false))
        assertTrue(KillSignal.isRestricted(ignoresBatteryOptimization = false, backgroundRestricted = false))
        assertTrue(KillSignal.isRestricted(ignoresBatteryOptimization = true, backgroundRestricted = true))
        assertTrue(KillSignal.isRestricted(ignoresBatteryOptimization = false, backgroundRestricted = true))
    }

    @Test fun anAlwaysOnVpnIsNotLimitedByTheBatteryTheWayAnAppIs() {
        // The system keeps its own always-on VPN up; the row would only worry people for nothing.
        assertEquals(false, KillSignal.isRestricted(ignoresBatteryOptimization = false, backgroundRestricted = false, alwaysOn = true))
        assertEquals(true, KillSignal.isRestricted(ignoresBatteryOptimization = false, backgroundRestricted = true, alwaysOn = true))
    }
}
