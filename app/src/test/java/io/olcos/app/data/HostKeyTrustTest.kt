package io.olcos.app.data

import io.olcos.app.data.HostKeyTrust.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Trust on first use per address: a new address is trusted, the remembered
 * key passes, and both a different key of the same type and a key of a
 * type the address never presented are refused as a change.
 */
class HostKeyTrustTest {
    private val ed = "ssh-ed25519"
    private val ecdsa = "ecdsa-sha2-nistp256"

    @Test
    fun aNewAddressIsTrusted() {
        assertEquals(Verdict.NEW, HostKeyTrust.verdict(emptyMap(), ed, "AAAA"))
    }

    @Test
    fun theRememberedKeyPasses() {
        assertEquals(Verdict.KNOWN, HostKeyTrust.verdict(mapOf(ed to "AAAA"), ed, "AAAA"))
        assertEquals(Verdict.KNOWN, HostKeyTrust.verdict(mapOf(ed to "AAAA", ecdsa to "BBBB"), ecdsa, "BBBB"))
    }

    @Test
    fun anotherKeyOfTheSameTypeIsAChange() {
        assertEquals(Verdict.CHANGED, HostKeyTrust.verdict(mapOf(ed to "AAAA"), ed, "CCCC"))
    }

    @Test
    fun aKeyOfAnUnseenTypeIsAChange() {
        assertEquals(Verdict.CHANGED, HostKeyTrust.verdict(mapOf(ed to "AAAA"), ecdsa, "BBBB"))
    }
}
