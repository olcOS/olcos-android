package io.olcos.app.core

import io.olcos.app.model.ConnectionRecord
import io.olcos.app.model.Details
import io.olcos.app.model.Lane
import io.olcos.app.model.OlcrtcLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryRunTest {
    private val key = "0".repeat(64)

    private fun record(id: String, link: OlcrtcLink = OlcrtcLink("telemost", "datachannel", "room-$id", key)) =
        ConnectionRecord(id = id, name = id, details = Details.Olcrtc(link))

    private fun bundle(id: String) = record(id, OlcrtcLink("telemost", "datachannel", "room-$id", key,
        with = listOf(Lane("jitsi", "datachannel", "https://meet.example.org/a"))))

    private val never: (ConnectionRecord, Set<String>) -> ConnectionRecord? = { _, _ -> null }

    @Test fun aRecoveryNeverGivesUpWhileANetworkIsThere() {
        val run = Recovery.Run(record("a"), random = { 1.0 })
        var last = 0L
        repeat(500) {
            last = run.afterFailure(true, "i/o timeout", never) ?: throw AssertionError("gave up at try ${it + 1}")
        }
        assertEquals("slow phase: a minute to two", 120_000L, last)
    }

    @Test fun theWaitsFollowThePolicyFromTheFirstTry() {
        val run = Recovery.Run(record("a"), random = { 1.0 })
        assertEquals(1_000L, run.firstDelay())
        val waits = (1..8).map { run.afterFailure(true, "i/o timeout", never)!! }
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 120_000L, 120_000L), waits)
    }

    @Test fun aRecoveryThatCarriedOnStartsWhereTheLastOneStood() {
        val run = Recovery.Run(record("a"), startAttempt = 4, random = { 1.0 })
        assertEquals(16_000L, run.firstDelay())
    }

    @Test fun aNetworkThatCameBackStartsTheCountOver() {
        val run = Recovery.Run(record("a"), startAttempt = 9, random = { 1.0 })
        assertEquals(120_000L, run.firstDelay())
        run.restart()
        assertEquals(0, run.attempt)
        assertEquals(1_000L, run.firstDelay())
    }

    @Test fun aPermanentErrorStopsAtOnce() {
        val run = Recovery.Run(record("a"), random = { 0.5 })
        assertNull(run.afterFailure(true, "handshake: key does not match the peer", never))
        assertNull(Recovery.Run(record("b")).afterFailure(true, "telemost api: 404 not found", never))
    }

    @Test fun oneRoomsErrorDoesNotStopAWholeServerConnection() {
        val run = Recovery.Run(bundle("a"), random = { 0.5 })
        assertNotNull(run.afterFailure(true, "telemost api: 404 not found", never))
        assertNull(Recovery.Run(bundle("b")).afterFailure(true, "handshake: key does not match the peer", never))
    }

    @Test fun noNetworkSpendsNoTryAndHasNoLimit() {
        val run = Recovery.Run(record("a"), random = { 1.0 })
        repeat(1_000) { assertEquals(Recovery.OFFLINE_RETRY_MS, run.afterFailure(false, "network is unreachable", never)) }
        // The network is back: the count is where it was, not spent in the dark.
        assertEquals(2_000L, run.afterFailure(true, "i/o timeout", never))
    }

    @Test fun noNetworkHidesNoPermanentError() {
        // A wrong key is the same offline; but nothing was learned about the connection without a network.
        val run = Recovery.Run(record("a"))
        assertEquals(Recovery.OFFLINE_RETRY_MS, run.afterFailure(false, "handshake: key does not match the peer", never))
    }

    @Test fun anotherConnectionStandsInAfterThreeTriesAndTheCountStartsOver() {
        val run = Recovery.Run(record("a"), random = { 1.0 })
        val hop = record("b")
        val next: (ConnectionRecord, Set<String>) -> ConnectionRecord? = { _, tried -> hop.takeIf { it.id !in tried } }
        run.afterFailure(true, "i/o timeout", next)
        run.afterFailure(true, "i/o timeout", next)
        assertEquals("a", run.record.id)
        run.afterFailure(true, "i/o timeout", next)
        assertEquals("b", run.record.id)
        assertEquals("the new connection waits its first wait", 1_000L, run.firstDelay())
    }

    @Test fun aWholeServerConnectionKeepsItsFastPhaseAndThreeSlowTriesFirst() {
        val run = Recovery.Run(bundle("a"), random = { 1.0 })
        val hop = record("b")
        val next: (ConnectionRecord, Set<String>) -> ConnectionRecord? = { _, tried -> hop.takeIf { it.id !in tried } }
        repeat(ReconnectPolicy.failoverAfter(true) - 1) { run.afterFailure(true, "i/o timeout", next) }
        assertEquals("a", run.record.id)
        run.afterFailure(true, "i/o timeout", next)
        assertEquals("b", run.record.id)
    }

    @Test fun withNowhereToMoveToItKeepsTryingTheSameOne() {
        val run = Recovery.Run(record("a"), random = { 0.0 })
        repeat(40) { assertNotNull(run.afterFailure(true, "i/o timeout", never)) }
        assertEquals("a", run.record.id)
    }

    @Test fun aMoveNeverComesBackToAConnectionAlreadyTried() {
        val run = Recovery.Run(record("a"), random = { 0.0 })
        val b = record("b")
        val c = record("c")
        val next: (ConnectionRecord, Set<String>) -> ConnectionRecord? = { _, tried -> listOf(b, c).firstOrNull { it.id !in tried } }
        repeat(3) { run.afterFailure(true, "i/o timeout", next) }
        assertEquals("b", run.record.id)
        repeat(3) { run.afterFailure(true, "i/o timeout", next) }
        assertEquals("c", run.record.id)
        repeat(30) { run.afterFailure(true, "i/o timeout", next) }
        assertEquals("every one has had its turn: it stays on the last, trying", "c", run.record.id)
    }

    // The count a recovery carries into the next one.

    @Test fun aSessionThatHeldAMinuteStartsTheNextRecoveryFromZero() {
        val carry = Recovery.Carry()
        carry.connected(nowMs = 10_000, attempts = 5)
        assertEquals(0, carry.nextStart(nowMs = 10_000 + 60_000))
        assertEquals(0, carry.nextStart(nowMs = 10_000 + 600_000))
    }

    @Test fun aSessionThatDroppedAgainAtOnceKeepsTheCount() {
        val carry = Recovery.Carry()
        carry.connected(nowMs = 10_000, attempts = 5)
        assertEquals(5, carry.nextStart(nowMs = 10_000 + 20_000))
    }

    @Test fun aSessionThatNeverConnectedStartsFromZero() {
        assertEquals(0, Recovery.Carry().nextStart(nowMs = 123))
    }

    @Test fun aStartTheFirstTimeAlwaysCountsAsTheFirst() {
        val carry = Recovery.Carry()
        carry.connected(nowMs = 0, attempts = 0)
        assertTrue(carry.nextStart(nowMs = 1_000) == 0)
    }
}
