package io.olcos.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.olcos.app.core.AppLog

/** PURE: which values of a plain preferences file move into the encrypted one. */
object SecureMigration {
    /**
     * The text values of [plain] to carry over: those named in [only] (null: all of them), that
     * are not empty, and that the encrypted store does not hold yet ([secureHas]) (an older plain
     * value never overwrites a newer encrypted one). Values that are not text stay where they are.
     */
    fun toCarry(plain: Map<String, Any?>, only: Set<String>?, secureHas: (String) -> Boolean): Map<String, String> =
        plain.mapNotNull { (key, value) ->
            val text = value as? String ?: return@mapNotNull null
            if (text.isEmpty() || (only != null && key !in only) || secureHas(key)) null else key to text
        }.toMap()
}

/**
 * The preferences file a bearer credential lives in, encrypted at rest with
 * a key the Android keystore keeps (EncryptedSharedPreferences). The first
 * open moves a value the plain predecessor kept over and wipes it there, so
 * an update from an earlier install is invisible; if the encrypted store
 * cannot be opened at all (a backup restored onto another keystore), the
 * plain file stands in rather than the sign-in being lost.
 */
internal object SecurePrefs {
    private const val LOG_TAG = "olcos.sessions"

    private fun secure(context: Context, name: String): SharedPreferences? = runCatching {
        val master = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            name,
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrNull()

    /** One credential under [key] in the file [name]; the plain file of that name hands it over once. */
    fun open(context: Context, name: String, key: String): SharedPreferences {
        val plain = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val secure = secure(context, "$name.secure") ?: run {
            AppLog.w(LOG_TAG, "the encrypted store for $name could not be opened; the plain file stands in")
            return plain
        }
        // Once only: carry a plain value over, then wipe where it came from.
        val legacy = plain.getString(key, null)
        if (!legacy.isNullOrEmpty() && secure.getString(key, null) == null) {
            secure.edit().putString(key, legacy).commit()
            plain.edit().clear().commit()
        }
        return secure
    }

    /** Every credential of the file [name] (several keys: a server's password, key and passphrase). */
    fun openAll(context: Context, name: String): SharedPreferences = openKeys(context, name, "$name.secure", null)

    /**
     * The keys [keys] of the plain file [plainName] (null: all of them) in an encrypted file of their own,
     * [secureName]: for a secret that sits among ordinary settings, such as the proxy's password. A key moves
     * once and is removed from the plain file only when the encrypted one took it.
     */
    fun openKeys(context: Context, plainName: String, secureName: String, keys: Set<String>?): SharedPreferences {
        val plain = context.getSharedPreferences(plainName, Context.MODE_PRIVATE)
        val secure = secure(context, secureName) ?: run {
            AppLog.w(LOG_TAG, "the encrypted store for $plainName could not be opened; the plain file stands in")
            return plain
        }
        val carry = SecureMigration.toCarry(plain.all, keys) { secure.contains(it) }
        if (carry.isNotEmpty()) {
            val write = secure.edit()
            carry.forEach { (key, value) -> write.putString(key, value) }
            write.commit()
        }
        // What the encrypted store holds now is wiped from the plain file, an older copy included;
        // what it did not take (the commit failed) stays, so no credential is lost.
        val moved = plain.all.keys.filter { (keys == null || it in keys) && secure.contains(it) }
        if (moved.isNotEmpty()) plain.edit().apply { moved.forEach { remove(it) } }.commit()
        return secure
    }
}
