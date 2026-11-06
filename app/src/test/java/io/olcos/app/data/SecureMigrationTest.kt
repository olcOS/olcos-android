package io.olcos.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SecureMigrationTest {
    @Test fun everyStringOfAPlainFileMovesWhenNothingIsSpecified() {
        val plain = mapOf("password:a" to "pw", "key:b" to "pem", "passphrase:b" to "pp")
        assertEquals(plain, SecureMigration.toCarry(plain, only = null) { false })
    }

    @Test fun onlyTheNamedKeysMoveOutOfASharedFile() {
        val plain = mapOf("socks.pass" to "pw", "socks.user" to "me", "dns" to "1.1.1.1:53")
        assertEquals(mapOf("socks.pass" to "pw"), SecureMigration.toCarry(plain, only = setOf("socks.pass")) { false })
    }

    @Test fun aValueTheEncryptedStoreAlreadyHoldsIsNotOverwrittenByAnOlderPlainOne() {
        val plain = mapOf("session" to "old", "twin" to "old-twin")
        val moved = SecureMigration.toCarry(plain, only = null) { it == "session" }
        assertEquals(mapOf("twin" to "old-twin"), moved)
    }

    @Test fun anEmptyValueIsNotACredentialToCarry() {
        assertEquals(emptyMap<String, String>(), SecureMigration.toCarry(mapOf("session" to "", "twin" to null), only = null) { false })
    }

    @Test fun valuesThatAreNotTextStayWhereTheyAre() {
        val plain = mapOf("count" to 3, "flag" to true, "password:a" to "pw")
        assertEquals(mapOf("password:a" to "pw"), SecureMigration.toCarry(plain, only = null) { false })
    }

    @Test fun anEmptyPlainFileCarriesNothing() {
        assertEquals(emptyMap<String, String>(), SecureMigration.toCarry(emptyMap(), only = null) { false })
    }

    @Test fun aNamedKeyThatIsNotThereCarriesNothing() {
        assertEquals(emptyMap<String, String>(), SecureMigration.toCarry(mapOf("dns" to "x"), only = setOf("socks.pass")) { false })
    }
}
