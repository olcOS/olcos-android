package io.olcos.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The key the bond groups lanes by (bond SetLaneGroup): rooms on one conference
 * host fail together, so the bond takes its backup off that host. The mapping
 * only: what the bond then does with a group is the bond's own tests' business.
 */
class LaneGroupTest {
    @Test fun twoRoomsOnOneHostShareAGroup() {
        val a = LaneGroup.key("jitsi", "https://meet.example.org/room-a")
        val b = LaneGroup.key("jitsi", "https://meet.example.org/room-b")
        assertEquals(a, b)
        assertEquals("jitsi:meet.example.org", a)
    }

    @Test fun theSchemeDoesNotSplitAHost() {
        assertEquals(
            LaneGroup.key("jitsi", "https://meet.example.org/a"),
            LaneGroup.key("jitsi", "http://meet.example.org/b"),
        )
    }

    @Test fun twoHostsAreTwoGroupsWhateverTheirCase() {
        val one = LaneGroup.key("jitsi", "https://one.example.org/a")
        val other = LaneGroup.key("jitsi", "https://other.example.org/b")
        assertNotEquals(one, other)
        assertEquals(other, LaneGroup.key("jitsi", "https://OTHER.example.org/x"))
    }

    @Test fun aPathPrefixIsPartOfTheHost() {
        val mounted = LaneGroup.key("jitsi", "https://meet.example.org/jitsi/a")
        val root = LaneGroup.key("jitsi", "https://meet.example.org/b")
        assertNotEquals(mounted, root)
        assertEquals("jitsi:meet.example.org/jitsi", LaneGroup.key("jitsi", "meet.example.org/jitsi/b"))
    }

    @Test fun aRoomWithoutAHostIsItsOwnGroup() {
        for (room in listOf("room-id", "", "   ", "https://meet.example.org", "https://", "/room")) {
            assertEquals("\"$room\" names no host", "", LaneGroup.key("jitsi", room))
        }
        assertNull(LaneGroup.host("room-id"))
    }

    @Test fun theCarrierKeepsTwoHostsApart() {
        assertNotEquals(
            LaneGroup.key("jitsi", "https://meet.example.org/a"),
            LaneGroup.key("vkcalls", "https://meet.example.org/a"),
        )
    }

    @Test fun whitespaceAroundARoomIsIgnored() {
        assertEquals("jitsi:meet.example.org", LaneGroup.key("jitsi", "  https://meet.example.org/a  "))
    }
}
