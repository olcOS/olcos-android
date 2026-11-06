package io.olcos.app.core

import io.olcos.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the protocol choice allows and what reaches the server's scripts:
 * MTS Link carries vp8channel alone, its room is pasted, and the server gets
 * its link with the typed ICE hint (else `both`). Pure: no agent, no assets.
 */
class MaintenanceTest {
    private val meeting = "https://my.mts-link.ru/j/100200300/400500600700"

    @Test fun mtsLinkIsOfferedAndBringsItsRoom() {
        assertTrue("mtslink" in Maintenance.CARRIERS)
        assertTrue("mtslink" in Maintenance.ROOM_REQUIRED)
    }

    @Test fun mtsLinkCarriesVp8ChannelAlone() {
        assertEquals(listOf("vp8channel"), Maintenance.allowedTransports("mtslink"))
    }

    /** The core's compatibility matrix (olcrtc docs/settings.md), the transports the app offers. */
    @Test fun eachProtocolIsOfferedWhatTheCoreCarriesOnIt() {
        assertEquals(listOf("vp8channel"), Maintenance.allowedTransports("telemost"))
        assertEquals(listOf("vp8channel"), Maintenance.allowedTransports("vkcalls"))
        assertEquals(listOf("datachannel"), Maintenance.allowedTransports("salutejazz"))
        assertEquals(Maintenance.TRANSPORTS, Maintenance.allowedTransports("jitsi"))
        for (carrier in Maintenance.CARRIERS) {
            assertTrue(carrier, Maintenance.allowedTransports(carrier).isNotEmpty())
            assertTrue(carrier, Maintenance.TRANSPORTS.containsAll(Maintenance.allowedTransports(carrier)))
        }
    }

    @Test fun pickingMtsLinkSwitchesToVp8Channel() {
        for (current in Maintenance.TRANSPORTS) {
            assertEquals(current, "vp8channel", Maintenance.transportFor("mtslink", current))
        }
    }

    @Test fun pickingAProtocolKeepsATransportItCarriesAndSwitchesOtherwise() {
        for (carrier in Maintenance.CARRIERS) {
            val allowed = Maintenance.allowedTransports(carrier)
            for (current in Maintenance.TRANSPORTS) {
                val expected = if (current in allowed) current else allowed.first()
                assertEquals("$carrier/$current", expected, Maintenance.transportFor(carrier, current))
            }
        }
        assertEquals("datachannel", Maintenance.transportFor("salutejazz", "vp8channel"))
        assertEquals("vp8channel", Maintenance.transportFor("telemost", "datachannel"))
        assertEquals("seichannel", Maintenance.transportFor("jitsi", "seichannel"))
    }

    @Test fun aRoomChangeGivesTheServerTheTypedIceHint() {
        val script = Maintenance.reconfigureScript("olcrtc-server-ab12", "mtslink", "vp8channel", "$meeting?ice=tcp")
        assertTrue(script.contains("id: \"$meeting?ice=tcp\""))
        assertTrue(script.contains("@$meeting?ice=tcp#"))
        assertFalse(script.contains("ice=both"))
    }

    @Test fun aRoomChangeWithoutAHintGivesTheServerBoth() {
        val script = Maintenance.reconfigureScript("olcrtc-server-ab12", "mtslink", "vp8channel", meeting)
        assertTrue(script.contains("id: \"$meeting?ice=both\""))
        assertTrue(script.contains("@$meeting?ice=both#"))
    }

    @Test fun aContainerReconfiguredToMtsLinkGetsSixtyFramesAndAtLeastSixtyFourPackets() {
        val script = Maintenance.reconfigureScript("olcrtc-server-ab12", "mtslink", "vp8channel", meeting, vp8FPS = 30, vp8Batch = 32)
        assertTrue(script.contains("sed -i '/^vp8:/,/^[^ ]/{s|^  fps: .*|  fps: 60|;s|^  batch_size: .*|  batch_size: 64|}' \"\$CONFIG\""))
        // A yaml with no vp8 block gets one, and the sed comes before the restart that reads it.
        assertTrue(script.contains("printf 'vp8:\\n  fps: 60\\n  batch_size: 64\\n' >> \"\$CONFIG\""))
        assertTrue(script.indexOf("^vp8:") < script.indexOf("podman restart"))
    }

    @Test fun aRoomChangeAnswersBeforeTheContainerRestarts() {
        // A tunnel that runs through this very server dies with the container: an answer written after the
        // restart never arrives, and the app takes a change that was made for one that failed.
        val script = Maintenance.reconfigureScript("olcrtc-server-ab12", "jitsi", "datachannel", "room")
        val answer = script.indexOf("echo \"OLCRTC_RECONFIGURED=ok\"")
        val uri = script.indexOf("echo \"OLCRTC_URI=")
        val restart = script.indexOf("podman restart")
        assertTrue(uri in 0 until answer)
        assertTrue("the restart follows the answer", answer in 0 until restart)
    }

    @Test fun theRestartAfterARoomChangeRunsOnItsOwnAndStartsTheContainerIfRestartFailed() {
        val line = Maintenance.reconfigureScript("olcrtc-server-ab12", "jitsi", "datachannel", "room")
            .lines().single { it.contains("podman restart") }
        assertTrue("detached, so the script ends and the answer goes out first", line.startsWith("nohup ") && line.trimEnd().endsWith("&"))
        assertTrue("a restart that failed halfway leaves the container down: start it", line.contains("podman start"))
        assertTrue(line.contains("sleep "))
    }

    @Test fun aLargerBatchOfThePersonIsKeptOnReconfigure() {
        val script = Maintenance.reconfigureScript("olcrtc-server-ab12", "mtslink", "vp8channel", meeting, vp8FPS = 12, vp8Batch = 128)
        assertTrue(script.contains("  fps: 60|"))
        assertTrue(script.contains("  batch_size: 128|"))
    }

    @Test fun reconfigureLeavesTheVp8BlockAloneForEveryoneElse() {
        for ((carrier, transport) in listOf("vkcalls" to "vp8channel", "jitsi" to "datachannel", "mtslink" to "datachannel")) {
            val script = Maintenance.reconfigureScript("olcrtc-server-ab12", carrier, transport, "room")
            assertFalse("$carrier/$transport", script.contains("^vp8:"))
        }
        assertEquals(emptyList<String>(), Maintenance.vp8TuningLines("vkcalls", "vp8channel", 30, 64))
    }

    @Test fun aReconfigureDropsATokenLineTheContainerCarried() {
        // A container an earlier version set up for WB Stream keeps working as it is; changing its room cannot restore the token.
        val script = Maintenance.reconfigureScript("olcrtc-server-ab12", "jitsi", "datachannel", "room")
        assertTrue(script.contains("-e '/^  token: /d'"))
        assertFalse(script.contains("token: \""))
    }

    @Test fun anotherServiceGetsItsRoomAsItIs() {
        val script = Maintenance.reconfigureScript("olcrtc-server-ab12", "vkcalls", "vp8channel", "https://vk.ru/call/join/Ab-1")
        assertTrue(script.contains("id: \"https://vk.ru/call/join/Ab-1\""))
    }

    // MARK: the make-a-room button

    @Test fun everyProtocolTheAppMakesRoomsForOffersOne() {
        for (carrier in listOf("jitsi", "telemost", "vkcalls", "salutejazz")) {
            assertTrue(carrier, Maintenance.canMakeRoom(carrier))
        }
    }

    @Test fun wbStreamIsNoLongerOfferedAnywhere() {
        assertFalse("wbstream" in Maintenance.CARRIERS)
        assertFalse("wbstream" in Maintenance.ROOM_REQUIRED)
        assertFalse(Maintenance.canMakeRoom("wbstream"))
    }

    @Test fun mtsLinkAlwaysOffersTheButton() {
        // Learned and signed in, the room is made silently; otherwise the sign-in sheet opens and the app takes the link made there.
        assertTrue(Maintenance.canMakeRoom("mtslink"))
    }

    @Test fun everyProtocolHasItsOwnRoomWording() {
        // The Jitsi wording names Jitsi alone: no other protocol shows it.
        assertEquals(R.string.add_server_room_jitsi, Maintenance.roomHintFor("jitsi"))
        assertEquals(R.string.room_placeholder_jitsi, Maintenance.roomPlaceholderFor("jitsi"))
        for (carrier in Maintenance.CARRIERS - "jitsi") {
            assertNotEquals(carrier, R.string.add_server_room_jitsi, Maintenance.roomHintFor(carrier))
            assertNotEquals(carrier, R.string.room_placeholder_jitsi, Maintenance.roomPlaceholderFor(carrier))
        }
    }
}
