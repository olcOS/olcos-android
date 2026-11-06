package io.olcos.app.data

import android.content.SharedPreferences
import io.olcos.app.core.AppLog
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reading a saved list one entry at a time. An entry this version cannot read (one a newer
 * version wrote with a kind it does not know, or one damaged on disk) used to make the whole
 * list read as empty, and the next save then wrote that empty list over everything. Now the
 * readable entries load, and the rest are kept apart in a backup the saves never touch.
 */
object StoredList {
    class Loaded<T>(val items: List<T>, val skipped: List<JsonElement>)

    fun <T> decode(text: String?, serializer: KSerializer<T>, json: Json): Loaded<T> {
        if (text.isNullOrBlank()) return Loaded(emptyList(), emptyList())
        val array = runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull()
            ?: return Loaded(emptyList(), listOf(JsonPrimitive(text)))
        val items = mutableListOf<T>()
        val skipped = mutableListOf<JsonElement>()
        for (element in array) {
            runCatching { json.decodeFromJsonElement(serializer, element) }
                .onSuccess { items += it }
                .onFailure { skipped += element }
        }
        return Loaded(items, skipped)
    }

    /**
     * The list saved under [key], every entry that can be read; the ones that cannot go to
     * "[key].unreadable", where no save of the list touches them.
     */
    fun <T> load(prefs: SharedPreferences, key: String, serializer: KSerializer<T>, json: Json): List<T> {
        val loaded = decode(prefs.getString(key, null), serializer, json)
        if (loaded.skipped.isNotEmpty()) {
            val apart = "$key.unreadable"
            prefs.edit().putString(apart, backup(prefs.getString(apart, null), loaded.skipped)).apply()
            AppLog.w("olcos.store", "${loaded.skipped.size} entries of $key cannot be read here; kept apart, not deleted")
        }
        return loaded.items
    }

    /**
     * The text of the backup after [skipped] joined [existing]: what was there stays, what is
     * new is added, what is already there is not added twice. Null when there is nothing to keep.
     */
    fun backup(existing: String?, skipped: List<JsonElement>): String? {
        if (skipped.isEmpty()) return existing
        val kept = runCatching { Json.parseToJsonElement(existing.orEmpty()) as? JsonArray }.getOrNull()?.toList().orEmpty()
        return JsonArray(kept + skipped.filterNot { it in kept }).toString()
    }
}
