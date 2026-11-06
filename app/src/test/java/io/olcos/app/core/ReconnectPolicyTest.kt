package io.olcos.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {
    @Test fun theFastPhaseIsFullJitterUnderADoublingCeiling() {
        // random 1.0 is the ceiling: 1 s, 2 s, 4 s ... capped at 60 s on the 7th try.
        val ceilings = (0 until ReconnectPolicy.FAST_ATTEMPTS).map { ReconnectPolicy.delayMs(it, 1.0) }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L), ceilings)
    }

    @Test fun aWaitIsNeverUnderHalfASecond() {
        for (attempt in 0 until ReconnectPolicy.FAST_ATTEMPTS) {
            assertEquals("try $attempt", 500L, ReconnectPolicy.delayMs(attempt, 0.0))
        }
        assertEquals(500L, ReconnectPolicy.delayMs(0, 0.1))
    }

    @Test fun twoPhonesThatLostARoomTogetherDoNotComeBackTogether() {
        val waits = listOf(0.1, 0.4, 0.7, 0.95).map { ReconnectPolicy.delayMs(5, it) }
        assertEquals(waits.size, waits.toSet().size)
        assertTrue(waits.all { it in 500L..32_000L })
    }

    @Test fun afterTheFastPhaseItIsOneTryEverySixtyToOneTwentySecondsForever() {
        for (attempt in listOf(ReconnectPolicy.FAST_ATTEMPTS, 8, 50, 10_000, Int.MAX_VALUE)) {
            assertEquals("try $attempt at random 0", 60_000L, ReconnectPolicy.delayMs(attempt, 0.0))
            assertEquals("try $attempt at random 1", 120_000L, ReconnectPolicy.delayMs(attempt, 1.0))
            assertEquals("try $attempt at random .5", 90_000L, ReconnectPolicy.delayMs(attempt, 0.5))
        }
        assertFalse(ReconnectPolicy.isSlowPhase(ReconnectPolicy.FAST_ATTEMPTS - 1))
        assertTrue(ReconnectPolicy.isSlowPhase(ReconnectPolicy.FAST_ATTEMPTS))
    }

    @Test fun aRandomOutsideZeroToOneIsClamped() {
        assertEquals(500L, ReconnectPolicy.delayMs(3, -5.0))
        assertEquals(8_000L, ReconnectPolicy.delayMs(3, 9.0))
        assertEquals(120_000L, ReconnectPolicy.delayMs(20, 9.0))
    }

    @Test fun aNegativeTryCountsAsTheFirst() {
        assertEquals(ReconnectPolicy.delayMs(0, 1.0), ReconnectPolicy.delayMs(-4, 1.0))
    }

    @Test fun aNetworkThatCameBackIsTriedAfterAShortSettle() {
        assertEquals(500L, ReconnectPolicy.networkSettleMs(0.0))
        assertEquals(1_000L, ReconnectPolicy.networkSettleMs(1.0))
        assertEquals(750L, ReconnectPolicy.networkSettleMs(0.5))
    }

    @Test fun aConnectionThatHeldAMinuteStartsTheCountOverAndOneThatDidNotCarriesOn() {
        assertEquals(0, ReconnectPolicy.startingAttempt(previous = 4, heldForMs = 60_000))
        assertEquals(0, ReconnectPolicy.startingAttempt(previous = 4, heldForMs = 600_000))
        assertEquals(4, ReconnectPolicy.startingAttempt(previous = 4, heldForMs = 59_999))
        assertEquals(4, ReconnectPolicy.startingAttempt(previous = 4, heldForMs = 0))
        assertEquals(0, ReconnectPolicy.startingAttempt(previous = 4, heldForMs = null))
        assertEquals(0, ReconnectPolicy.startingAttempt(previous = -3, heldForMs = 10))
    }

    @Test fun aMissedProbeIsConfirmedFiveSecondsLaterAndTwoMissesMeanDead() {
        assertEquals(5_000L, ReconnectPolicy.CONFIRM_PROBE_DELAY_MS)
        assertEquals(2, ReconnectPolicy.MISSES_FOR_DEAD)
    }

    // What can only fail again the same way stops the loop; the rest is tried until the person stops it.

    @Test fun aWrongKeyIsPermanent() {
        assertTrue(ReconnectPolicy.isPermanent("handshake: key does not match the peer", severalLanes = false))
        assertTrue(ReconnectPolicy.isPermanent("handshake: key does not match the peer", severalLanes = true))
        assertTrue(ReconnectPolicy.isPermanent("record authentication failed", severalLanes = true))
        assertTrue(ReconnectPolicy.isPermanent("read welcome: EOF", severalLanes = false))
        assertTrue(ReconnectPolicy.isPermanent("challenge mismatch", severalLanes = false))
    }

    @Test fun aServerCoreThatDoesNotSpeakOursIsPermanent() {
        assertTrue(ReconnectPolicy.isPermanent("incompatible olcrtc protocol: server v2", severalLanes = true))
    }

    @Test fun aHandshakeThatRanOutOfTimeIsNotAKeyProblem() {
        assertFalse(ReconnectPolicy.isPermanent("read welcome: timeout", severalLanes = false))
        assertFalse(ReconnectPolicy.isPermanent("send hello: deadline exceeded", severalLanes = false))
        assertFalse(ReconnectPolicy.isPermanent("peer did not answer the handshake", severalLanes = false))
        assertFalse(ReconnectPolicy.isPermanent("no peer in room", severalLanes = false))
    }

    @Test fun aRoomThatIsGoneIsPermanentForOneRoomButNotForSeveral() {
        for (text in listOf("room not found", "the service has no such room", "invalid room url", "malformed room reference")) {
            assertTrue(text, ReconnectPolicy.isPermanent(text, severalLanes = false))
            assertFalse("$text with several lanes", ReconnectPolicy.isPermanent(text, severalLanes = true))
        }
    }

    @Test fun aCarrierThatRefusedForGoodIsPermanentForOneRoom() {
        for (text in listOf("telemost api: 404", "join room: 410 gone", "get token: forbidden", "guest register: unauthorized",
            "telemost api: 406", "api error 403", "the service did not accept the guest")) {
            assertTrue(text, ReconnectPolicy.isPermanent(text, severalLanes = false))
            assertFalse("$text with several lanes", ReconnectPolicy.isPermanent(text, severalLanes = true))
        }
    }

    @Test fun aCarrierErrorThatWillPassIsNot() {
        for (text in listOf("telemost api: 500", "join room: 429 too many requests", "get token: i/o timeout",
            "telemost api: 502 bad gateway", "join room: connection reset by peer")) {
            assertFalse(text, ReconnectPolicy.isPermanent(text, severalLanes = false))
        }
    }

    @Test fun aStatusCodeInsideALongerNumberOrAnAddressIsNotACode() {
        assertFalse(ReconnectPolicy.isPermanent("telemost api: dial 10.0.4.4:4040 refused", severalLanes = false))
        assertFalse(ReconnectPolicy.isPermanent("join room: request 14043 failed", severalLanes = false))
    }

    @Test fun nothingAboutTheNetworkOrThePhoneIsPermanent() {
        for (text in listOf("network is unreachable", "no route to host", "i/o timeout", "connection refused",
            "address already in use", "lookup meet.example.org on 8.8.8.8:53: i/o timeout", "no answer", "", "something new")) {
            assertFalse(text, ReconnectPolicy.isPermanent(text, severalLanes = false))
        }
    }

    @Test fun theClassificationIgnoresCase() {
        assertTrue(ReconnectPolicy.isPermanent("Handshake: Key Does Not Match The Peer", severalLanes = false))
        assertTrue(ReconnectPolicy.isPermanent("JOIN ROOM: FORBIDDEN", severalLanes = false))
    }

    @Test fun theBundleKeepsItsWholeFastPhaseAndThreeSlowTriesBeforeAnotherConnectionStandsIn() {
        assertEquals(3, ReconnectPolicy.failoverAfter(bundle = false))
        assertEquals(ReconnectPolicy.FAST_ATTEMPTS + 3, ReconnectPolicy.failoverAfter(bundle = true))
        assertNotEquals(ReconnectPolicy.failoverAfter(true), ReconnectPolicy.failoverAfter(false))
    }
}
