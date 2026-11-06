package io.olcos.app.data

import io.olcos.app.model.ConnectionRecord
import io.olcos.app.model.Details
import io.olcos.app.model.OlcrtcLink
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredListTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ConnectionRecord.serializer()
    private val key = "0".repeat(64)

    private fun record(id: String) = ConnectionRecord(id = id, name = id, details = Details.Olcrtc(OlcrtcLink("jitsi", "datachannel", "room-$id", key)))
    private fun text(vararg ids: String) = json.encodeToString(ListSerializer(serializer), ids.map(::record))

    @Test fun aReadableListComesBackWhole() {
        val loaded = StoredList.decode(text("a", "b", "c"), serializer, json)
        assertEquals(listOf("a", "b", "c"), loaded.items.map { it.id })
        assertTrue(loaded.skipped.isEmpty())
    }

    @Test fun nothingSavedIsAnEmptyList() {
        for (empty in listOf(null, "", "   ", "[]")) {
            val loaded = StoredList.decode(empty, serializer, json)
            assertTrue(loaded.items.isEmpty())
            assertTrue(loaded.skipped.isEmpty())
        }
    }

    @Test fun oneEntryThatCannotBeReadDoesNotTakeTheOthersWithIt() {
        // An entry a newer version wrote with a kind of connection this one does not know.
        val mixed = text("a", "b").dropLast(1) + """,{"id":"x","name":"x","details":{"type":"from-the-future","blob":1}}]"""
        val loaded = StoredList.decode(mixed, serializer, json)
        assertEquals(listOf("a", "b"), loaded.items.map { it.id })
        assertEquals(1, loaded.skipped.size)
        assertTrue(loaded.skipped[0].toString().contains("from-the-future"))
    }

    @Test fun textThatIsNotAListIsKeptWholeAndNothingIsLoaded() {
        val loaded = StoredList.decode("{this is not json", serializer, json)
        assertTrue(loaded.items.isEmpty())
        assertEquals(1, loaded.skipped.size)
        assertTrue(loaded.skipped[0].toString().contains("this is not json"))
    }

    @Test fun whatWasSkippedIsKeptApartSoASaveCannotEraseIt() {
        val skipped = StoredList.decode("[1,2]", serializer, json).skipped
        val backup = StoredList.backup(existing = null, skipped = skipped)
        assertTrue(backup!!.contains("1") && backup.contains("2"))
        // A later load that skips the same again does not grow the backup.
        assertEquals(backup, StoredList.backup(existing = backup, skipped = skipped))
    }

    @Test fun aBackupGrowsWithWhatIsNewAndKeepsWhatWasThere() {
        val first = StoredList.backup(null, StoredList.decode("[1]", serializer, json).skipped)
        val second = StoredList.backup(first, StoredList.decode("[2]", serializer, json).skipped)!!
        assertTrue(second.contains("1") && second.contains("2"))
    }

    @Test fun nothingSkippedLeavesTheBackupAlone() {
        assertNull(StoredList.backup(null, emptyList()))
        assertEquals("[1]", StoredList.backup("[1]", emptyList()))
    }

    @Test fun theReadableOnesSaveWithoutTheUnreadable() {
        val mixed = text("a").dropLast(1) + """,{"id":"x","details":{"type":"from-the-future"}}]"""
        val loaded = StoredList.decode(mixed, serializer, json)
        val saved = json.encodeToString(ListSerializer(serializer), loaded.items)
        assertEquals(listOf("a"), StoredList.decode(saved, serializer, json).items.map { it.id })
        assertTrue("the unreadable one is not lost, it sits in the backup", StoredList.backup(null, loaded.skipped)!!.contains("from-the-future"))
    }
}
