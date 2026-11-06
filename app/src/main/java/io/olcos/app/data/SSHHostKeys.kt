package io.olcos.app.data

import android.content.Context

/**
 * The SSH host keys this phone has seen, per server address and key type:
 * the first connection trusts and remembers the key; a later connection
 * that meets a different key, or a key of a type never seen at an address
 * that already has one, is refused (SSHClient.HostKeyChanged).
 */
class SSHHostKeys(context: Context) {
    private val prefs = context.getSharedPreferences("olcos.ssh.hostkeys", Context.MODE_PRIVATE)

    /** The remembered key (base64) of this type for the address, or null when none was seen. */
    fun known(host: String, port: Int, type: String): String? = prefs.getString(key(host, port, type), null)

    /** Every remembered key of the address, type to key (base64); empty when the address is new. */
    fun knownTypes(host: String, port: Int): Map<String, String> {
        val prefix = "$host:$port/"
        return prefs.all.entries
            .filter { it.key.startsWith(prefix) && it.value is String }
            .associate { it.key.removePrefix(prefix) to it.value as String }
    }

    fun remember(host: String, port: Int, type: String, keyBase64: String) {
        prefs.edit().putString(key(host, port, type), keyBase64).apply()
    }

    /** Forgets every key of the address, for a server that was reinstalled on purpose. */
    fun forget(host: String, port: Int) {
        val prefix = "$host:$port/"
        val edit = prefs.edit()
        for (name in prefs.all.keys) if (name.startsWith(prefix)) edit.remove(name)
        edit.apply()
    }

    private fun key(host: String, port: Int, type: String): String = "$host:$port/$type"
}

/**
 * The decision on a presented host key, apart from the storage so that it
 * can be tested: an address with no remembered key is new; the remembered
 * key of the same type is known; anything else is a change, including a
 * key of another type (a man in the middle would otherwise present a type
 * the phone has not seen yet and be trusted as new).
 */
object HostKeyTrust {
    enum class Verdict { NEW, KNOWN, CHANGED }

    fun verdict(known: Map<String, String>, type: String, keyBase64: String): Verdict = when {
        known.isEmpty() -> Verdict.NEW
        known[type] == keyBase64 -> Verdict.KNOWN
        else -> Verdict.CHANGED
    }
}
