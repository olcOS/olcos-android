package io.olcos.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepAlivePolicyTest {
    @Test fun aQuietTunnelIsProbedEveryRound() {
        for (skipped in 0..5) assertTrue(KeepAlivePolicy.shouldProbe(trafficCameBack = false, skippedInARow = skipped))
    }

    @Test fun trafficThatCameBackSkipsTheProbeButOnlyForAFewRounds() {
        assertFalse(KeepAlivePolicy.shouldProbe(trafficCameBack = true, skippedInARow = 0))
        assertFalse(KeepAlivePolicy.shouldProbe(trafficCameBack = true, skippedInARow = 1))
        assertTrue("a real probe at least every third round", KeepAlivePolicy.shouldProbe(trafficCameBack = true, skippedInARow = 2))
        assertTrue(KeepAlivePolicy.shouldProbe(trafficCameBack = true, skippedInARow = 9))
    }

    @Test fun theCounterOfTheTunnelAloneNeverKeepsADeadTunnelUpForever() {
        // Packets the phone itself makes move the byte counter of a dead tunnel too: the
        // probe still runs every KeepAlivePolicy.MAX_SKIPPED + 1 rounds.
        var skipped = 0
        var probes = 0
        repeat(30) {
            if (KeepAlivePolicy.shouldProbe(trafficCameBack = true, skippedInARow = skipped)) {
                probes++
                skipped = 0
            } else {
                skipped++
            }
        }
        assertEquals(10, probes)
    }

    @Test fun oneMissIsConfirmedByASecondProbeFiveSecondsLaterAndTwoMeanDead() {
        assertEquals(5_000L, KeepAlivePolicy.CONFIRM_DELAY_MS)
        assertEquals(2, KeepAlivePolicy.MISSES_FOR_DEAD)
        assertFalse(KeepAlivePolicy.isDead(misses = 0))
        assertFalse(KeepAlivePolicy.isDead(misses = 1))
        assertTrue(KeepAlivePolicy.isDead(misses = 2))
        assertTrue(KeepAlivePolicy.isDead(misses = 3))
    }

    @Test fun theNumbersAreTheIosOnes() {
        assertEquals(ReconnectPolicy.CONFIRM_PROBE_DELAY_MS, KeepAlivePolicy.CONFIRM_DELAY_MS)
        assertEquals(ReconnectPolicy.MISSES_FOR_DEAD, KeepAlivePolicy.MISSES_FOR_DEAD)
    }

    @Test fun theWorstCaseToNoticeADeadTunnelIsUnderAMinute() {
        // One interval, a probe that times out, the confirmation wait, a second probe that times out.
        val worst = KeepAlivePolicy.INTERVAL_MS + KeepAlivePolicy.PROBE_TIMEOUT_MS +
            KeepAlivePolicy.CONFIRM_DELAY_MS + KeepAlivePolicy.PROBE_TIMEOUT_MS
        assertTrue("$worst ms", worst < 60_000)
    }
}
