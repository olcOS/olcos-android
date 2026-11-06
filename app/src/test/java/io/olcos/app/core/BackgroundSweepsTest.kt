package io.olcos.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundSweepsTest {
    @Test fun aRoomBeingChangedIsNotChangedAgainAtTheSameTime() {
        val inFlight = InFlight()
        assertTrue(inFlight.tryEnter("room-a"))
        assertFalse("the server page and the sweep reached the same room", inFlight.tryEnter("room-a"))
        assertTrue("another room is its own", inFlight.tryEnter("room-b"))
    }

    @Test fun aRoomIsFreeAgainOnceItsChangeIsOver() {
        val inFlight = InFlight()
        inFlight.tryEnter("room-a")
        inFlight.leave("room-a")
        assertTrue(inFlight.tryEnter("room-a"))
    }

    @Test fun leavingARoomThatWasNeverEnteredIsHarmless() {
        val inFlight = InFlight()
        inFlight.leave("never")
        assertTrue(inFlight.tryEnter("never"))
    }

    @Test fun theTelemostSweepRunsAtMostOnceAnHourWhateverTheRotationPassesAre() {
        val hour = BackgroundSweeps.TELEMOST_EVERY_MS
        assertEquals(60 * 60_000L, hour)
        assertTrue(BackgroundSweeps.telemostDue(lastMs = null, nowMs = 0))
        assertFalse(BackgroundSweeps.telemostDue(lastMs = 1_000, nowMs = 1_000 + hour - 1))
        assertTrue(BackgroundSweeps.telemostDue(lastMs = 1_000, nowMs = 1_000 + hour))
    }

    @Test fun aClockThatWentBackDoesNotStopTheSweepForever() {
        assertTrue(BackgroundSweeps.telemostDue(lastMs = 10_000_000, nowMs = 5_000))
    }

    @Test fun thePassesAreNeverFartherApartThanTheRotationPolicyWants() {
        assertTrue(BackgroundSweeps.waitMs(RoomRotationPolicy.SWEEP_MS) <= 60 * 60_000L)
        assertEquals(RoomRotationPolicy.WINDOWED_SWEEP_MS, BackgroundSweeps.waitMs(RoomRotationPolicy.WINDOWED_SWEEP_MS))
    }

    @Test fun aWaitIsNeverUnderAMinute() {
        assertEquals(60_000L, BackgroundSweeps.waitMs(1_000))
    }
}
