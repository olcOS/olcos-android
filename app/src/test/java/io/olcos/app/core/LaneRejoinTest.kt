package io.olcos.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaneRejoinTest {
    @Test fun aLanesNextTryIsSomewhereInTheUpperHalfOfItsBackoff() {
        assertEquals(1_000L, LaneRejoin.jitteredDelayMs(2_000, 0.0))
        assertEquals(2_000L, LaneRejoin.jitteredDelayMs(2_000, 1.0))
        assertEquals(30_000L, LaneRejoin.jitteredDelayMs(40_000, 0.5))
        assertEquals(60_000L, LaneRejoin.jitteredDelayMs(120_000, 0.0))
    }

    @Test fun aRandomOutsideZeroToOneIsClamped() {
        assertEquals(1_000L, LaneRejoin.jitteredDelayMs(2_000, -3.0))
        assertEquals(2_000L, LaneRejoin.jitteredDelayMs(2_000, 7.0))
    }

    @Test fun lanesThatFailedTogetherDriftApart() {
        val waits = listOf(0.05, 0.3, 0.6, 0.95).map { LaneRejoin.jitteredDelayMs(16_000, it) }
        assertEquals(waits.size, waits.toSet().size)
        assertTrue(waits.all { it in 8_000L..16_000L })
    }

    @Test fun theBackoffStillDoublesFromTwoSecondsToTwoMinutes() {
        assertEquals(2_000L, OlcrtcEngine.nextLaneDelay(0))
        assertEquals(4_000L, OlcrtcEngine.nextLaneDelay(2_000))
        assertEquals(120_000L, OlcrtcEngine.nextLaneDelay(100_000))
        assertEquals(120_000L, OlcrtcEngine.nextLaneDelay(120_000))
    }

    @Test fun theLaneStatesAreTheLiveSessionsNotEveryRuntimeEverMade() {
        val all = listOf("running", "running", "idle", "idle", "idle")
        assertEquals(listOf("running", "running"), OlcrtcEngine.currentLaneStates(all, lanes = 2))
        assertEquals(all, OlcrtcEngine.currentLaneStates(all, lanes = null))
        assertEquals(all, OlcrtcEngine.currentLaneStates(all, lanes = 9))
        assertEquals(emptyList<String>(), OlcrtcEngine.currentLaneStates(all, lanes = 0))
    }

    @Test fun onlyOneLanePerServiceRejoinsInACheck() {
        val check = LaneRejoin.Check()
        assertTrue(check.allow("jitsi"))
        assertFalse("a second Jitsi room waits for the next check", check.allow("jitsi"))
        assertTrue("another service is its own host", check.allow("vkcalls"))
        assertTrue(check.allow("telemost"))
        assertFalse(check.allow("vkcalls"))
    }

    @Test fun aNewCheckStartsFromNothing() {
        val first = LaneRejoin.Check()
        first.allow("jitsi")
        assertTrue(LaneRejoin.Check().allow("jitsi"))
    }

    @Test fun theNameOfAServiceIsComparedWithoutCase() {
        val check = LaneRejoin.Check()
        assertTrue(check.allow("Jitsi"))
        assertFalse(check.allow("jitsi"))
    }
}
