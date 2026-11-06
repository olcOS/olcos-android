package io.olcos.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanePolicyTest {
    private val allUp: (Int) -> Boolean = { true }

    @Test fun lanesTakeTurns() {
        val policy = LanePolicy(3)
        assertEquals(listOf(0, 1, 2, 0, 1), (1..5).map { policy.pick(allUp, 0) })
    }

    @Test fun aLaneThatIsNotRunningIsSkipped() {
        val policy = LanePolicy(3)
        val up: (Int) -> Boolean = { it != 1 }
        assertEquals(listOf(0, 2, 0, 2), (1..4).map { policy.pick(up, 0) })
    }

    @Test fun whenNoneIsUpTheyStillTakeTurns() {
        val policy = LanePolicy(2)
        assertEquals(listOf(0, 1, 0), (1..3).map { policy.pick({ false }, 0) })
    }

    @Test fun aLaneTwiceDeadInARowIsBenchedForAWhile() {
        val policy = LanePolicy(2)
        policy.record(0, dead = true, now = 1_000)
        policy.record(0, dead = true, now = 2_000)
        assertEquals(listOf(1, 1, 1), (1..3).map { policy.pick(allUp, 3_000) })
        assertEquals("rested", 0, (1..2).map { policy.pick(allUp, 2_000 + LanePolicy.BENCH_MS) }.first { it == 0 })
    }

    @Test fun aGoodConnectionClearsTheCount() {
        val policy = LanePolicy(2)
        policy.record(0, dead = true, now = 0)
        policy.record(0, dead = false, now = 1)
        policy.record(0, dead = true, now = 2)
        assertEquals(listOf(0, 1), (1..2).map { policy.pick(allUp, 3) })
    }

    // A room whose listener is gone is set aside at once: every connection to it fails the same way.

    @Test fun aLaneWhoseListenerIsGoneIsBenchedAtOnce() {
        val policy = LanePolicy(2)
        policy.recordGone(0, now = 5_000)
        assertEquals(listOf(1, 1, 1), (1..3).map { policy.pick(allUp, 6_000) })
    }

    @Test fun aBenchedLaneIsTriedAgainWhenItsTimeIsUp() {
        val policy = LanePolicy(2)
        policy.recordGone(0, now = 5_000)
        assertTrue((1..4).map { policy.pick(allUp, 5_000 + LanePolicy.BENCH_MS) }.contains(0))
    }

    @Test fun whenEveryLaneIsBenchedTheOnesThatAreUpStillTakeTheConnections() {
        val policy = LanePolicy(2)
        policy.recordGone(0, now = 0)
        policy.recordGone(1, now = 0)
        val picks = (1..4).map { policy.pick(allUp, 1) }
        assertEquals(setOf(0, 1), picks.toSet())
    }

    @Test fun aLaneOutOfRangeIsIgnored() {
        val policy = LanePolicy(2)
        policy.recordGone(7, now = 0)
        policy.recordGone(-1, now = 0)
        assertEquals(listOf(0, 1), (1..2).map { policy.pick(allUp, 1) })
    }

    // A connection that waits for its first byte for a minute is not slow, it is stuck.

    @Test fun aConnectionWithNothingBackAfterAMinuteIsHung() {
        assertTrue(LanePolicy.looksHung(bytesBack = 0, waitedMs = LanePolicy.HUNG_AFTER_MS))
        assertTrue(LanePolicy.looksHung(bytesBack = 0, waitedMs = 10 * 60_000))
    }

    @Test fun aConnectionThatAnsweredOrThatIsYoungIsNotHung() {
        assertFalse(LanePolicy.looksHung(bytesBack = 0, waitedMs = LanePolicy.HUNG_AFTER_MS - 1))
        assertFalse(LanePolicy.looksHung(bytesBack = 1, waitedMs = 10 * 60_000))
        assertFalse(LanePolicy.looksHung(bytesBack = 4_096, waitedMs = LanePolicy.HUNG_AFTER_MS))
    }

    @Test fun theWaitIsAboutAMinute() {
        assertEquals(60_000L, LanePolicy.HUNG_AFTER_MS)
    }

    @Test fun aShortConnectionWithNothingBackStillLooksDead() {
        assertTrue(LanePolicy.looksDead(bytesBack = 0, durationMs = 1_000))
        assertTrue(LanePolicy.looksDead(bytesBack = 10, durationMs = 4_999))
        assertFalse(LanePolicy.looksDead(bytesBack = 33, durationMs = 100))
        assertFalse(LanePolicy.looksDead(bytesBack = 0, durationMs = 5_000))
    }
}
