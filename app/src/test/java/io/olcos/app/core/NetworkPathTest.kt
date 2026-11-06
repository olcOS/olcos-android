package io.olcos.app.core

import io.olcos.app.core.NetworkPath.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkPathTest {
    @Test fun theFirstUpdateWhileOnlineIsOnlyTheBaseline() {
        assertEquals(Action.NONE, NetworkPath().changed(available = true, transport = "wifi"))
    }

    @Test fun anotherUpdateOfTheSameNetworkChangesNothing() {
        val path = NetworkPath()
        path.changed(true, "wifi")
        assertEquals(Action.NONE, path.changed(true, "wifi"))
        assertEquals(Action.NONE, path.changed(true, "wifi"))
    }

    @Test fun wifiHandingOverToCellularJoinsTheRoomsAgain() {
        val path = NetworkPath()
        path.changed(true, "wifi")
        assertEquals(Action.REJOIN, path.changed(true, "cellular"))
        assertEquals(Action.NONE, path.changed(true, "cellular"))
    }

    @Test fun losingTheNetworkHoldsOnce() {
        val path = NetworkPath()
        path.changed(true, "wifi")
        assertEquals(Action.HOLD, path.changed(false, null))
        assertEquals(Action.NONE, path.changed(false, null))
        assertEquals(Action.NONE, path.changed(false, null))
    }

    @Test fun aNetworkThatComesBackJoinsTheRoomsAgainEvenIfItIsTheSameKind() {
        val path = NetworkPath()
        path.changed(true, "wifi")
        path.changed(false, null)
        assertEquals(Action.REJOIN, path.changed(true, "wifi"))
        assertEquals(Action.NONE, path.changed(true, "wifi"))
    }

    @Test fun startingOfflineHoldsAndTheFirstNetworkJoins() {
        val path = NetworkPath()
        assertEquals(Action.HOLD, path.changed(false, null))
        assertEquals(Action.REJOIN, path.changed(true, "wifi"))
    }

    @Test fun aTunnelOfTheDeviceItselfIsNotANetworkChange() {
        // The phone's own VPN becomes the default network of other apps; this app's sockets stay off it,
        // so an update that names it says nothing about the network the rooms run over.
        val path = NetworkPath()
        path.changed(true, "wifi")
        assertEquals(Action.NONE, path.changed(true, "vpn"))
        assertEquals(Action.NONE, path.changed(true, "wifi"))
    }

    @Test fun cellularToWifiAndBackJoinsEachTime() {
        val path = NetworkPath()
        path.changed(true, "cellular")
        assertEquals(Action.REJOIN, path.changed(true, "wifi"))
        assertEquals(Action.REJOIN, path.changed(true, "cellular"))
    }

    @Test fun aNetworkOfNoKnownKindStillCountsAsOnline() {
        val path = NetworkPath()
        path.changed(true, "wifi")
        assertEquals(Action.REJOIN, path.changed(true, "other"))
    }
}
