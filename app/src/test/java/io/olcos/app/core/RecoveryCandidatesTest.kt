package io.olcos.app.core

import io.olcos.app.model.ConnectionRecord
import io.olcos.app.model.Details
import io.olcos.app.model.OlcrtcLink
import io.olcos.app.model.ServerRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryCandidatesTest {
    private val key = "0".repeat(64)

    private fun record(id: String, source: String? = null) =
        ConnectionRecord(id = id, name = id, details = Details.Olcrtc(OlcrtcLink("jitsi", "datachannel", "room-$id", key)), subSource = source)

    private fun server(id: String, vararg records: String) =
        ServerRecord(id = id, name = id, host = "$id.example.org", port = 8443, certSHA256 = "0".repeat(64), prefix = "p", recordIDs = records.toList())

    private fun checked(vararg ids: String) = ids.associateWith { Health.Check(40, 1, 1, null, 0L) }

    @Test fun theOtherProtocolsOfTheSameServerComeFirst() {
        val home = server("home", "a", "b", "c")
        val records = listOf(record("a"), record("b"), record("c"))
        val got = Recovery.candidates(records[0], records, listOf(home), checked("b", "c"))
        assertEquals(listOf("b", "c"), got.map { it.id })
    }

    @Test fun anotherServerOfTheSamePhoneIsNextWhenItsLastCheckPassed() {
        val home = server("home", "a", "b")
        val other = server("other", "x")
        val records = listOf(record("a"), record("b"), record("x"))
        val got = Recovery.candidates(records[0], records, listOf(home, other), checked("x"))
        assertEquals(listOf("b", "x"), got.map { it.id })
    }

    @Test fun aConnectionTheDeviceDoesNotRunItselfIsNeverMovedToUnasked() {
        // A free public node from some list that passed its check once: the person never chose to send traffic there.
        val home = server("home", "a", "b")
        val records = listOf(record("a"), record("b"), record("public-vless", source = "https://lists.example.net/free"))
        val got = Recovery.candidates(records[0], records, listOf(home), checked("public-vless"))
        assertEquals(listOf("b"), got.map { it.id })
    }

    @Test fun aServerWhoseLastCheckFailedIsNotAHop() {
        val home = server("home", "a")
        val other = server("other", "x")
        val records = listOf(record("a"), record("x"))
        assertEquals(emptyList<String>(), Recovery.candidates(records[0], records, listOf(home, other), emptyMap()).map { it.id })
    }

    @Test fun theFastestPassedCheckGoesFirst() {
        val home = server("home", "a")
        val one = server("one", "x")
        val two = server("two", "y")
        val records = listOf(record("a"), record("x"), record("y"))
        val checks = mapOf("x" to Health.Check(300, 1, 1, null, 0L), "y" to Health.Check(50, 1, 1, null, 0L))
        assertEquals(listOf("y", "x"), Recovery.candidates(records[0], records, listOf(home, one, two), checks).map { it.id })
    }

    @Test fun withoutAnyServerOfItsOwnTheOnlyHopsAreTheOnesFromTheSameList() {
        val records = listOf(record("a", "https://lists.example.net/mine"), record("b", "https://lists.example.net/mine"),
            record("c", "https://other.example.net/free"))
        val got = Recovery.candidates(records[0], records, emptyList(), checked("c"))
        assertEquals(listOf("b"), got.map { it.id })
    }
}
