package io.olcos.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JitsiProbeTest {
    @Test fun theDefaultServerIsTheFirstCandidate() {
        assertEquals(JitsiProbe.hostOf(Maintenance.DEFAULT_JITSI_BASE), JitsiProbe.CANDIDATES.first())
        assertEquals(JitsiProbe.CANDIDATES.size, JitsiProbe.CANDIDATES.toSet().size)
    }

    @Test fun theScriptChecksEveryCandidateAndOnlyHostNames() {
        val script = JitsiProbe.script()
        for (h in JitsiProbe.CANDIDATES) assertTrue(h, script.contains(" $h"))
        assertTrue(script.contains("https://\$h/config.js"))
        assertTrue(script.contains("https://\$h/xmpp-websocket"))
        assertTrue(script.contains("echo \"JITSI \$h \${c:-000} \${w:-000} \$l\""))
        assertTrue(script.startsWith("command -v curl"))
        // curl fails after a 101 (it waits on the upgraded connection): its status must not replace the code.
        assertFalse(script.contains("|| w="))
        assertFalse(script.contains("|| c="))
        val odd = JitsiProbe.script(listOf("meet.example.org", "x;reboot", "a b.example", "\$(id).example"))
        assertTrue(odd.contains("for h in meet.example.org; do"))
        assertFalse(odd.contains("reboot"))
        assertFalse(odd.contains("(id)"))
    }

    @Test fun anOpenServerAnswersConfigUpgradeAndNoLogin() {
        val out = """
            JITSI meet.egovm.ru 000 000 0
            JITSI meet.riddlerx.org 200 101 0
            JITSI meet.mamba.group 200 101 0
            JITSI conf.hyperia.space 200 101 1
            JITSI meet.mirox.chat 200 403 0
            Warning: some line from the shell
        """.trimIndent()
        val answers = JitsiProbe.parse(out)
        assertEquals(5, answers.size)
        assertEquals(listOf("meet.riddlerx.org", "meet.mamba.group"), answers.filter { it.usable }.map { it.host })
        assertEquals("meet.riddlerx.org", JitsiProbe.best(answers))
        // The candidates' order decides, not the order the answers came in.
        assertEquals("meet.riddlerx.org", JitsiProbe.best(answers.reversed()))
    }

    @Test fun noOpenServerGivesNone() {
        val answers = JitsiProbe.parse("JITSI meet.egovm.ru 000 000 0\nJITSI conf.hyperia.space 200 101 1\n")
        assertNull(JitsiProbe.best(answers))
        assertNull(JitsiProbe.best(emptyList()))
        // An answer from a host the app did not ask about picks nothing.
        assertNull(JitsiProbe.best(JitsiProbe.parse("JITSI evil.example 200 101 0")))
    }

    @Test fun hostOfReadsBasesAndRoomLinks() {
        assertEquals("meet.egovm.ru", JitsiProbe.hostOf("https://meet.egovm.ru"))
        assertEquals("meet.egovm.ru", JitsiProbe.hostOf("https://Meet.Egovm.ru/olcrtc-ab12cd34"))
        assertEquals("meet.example.org", JitsiProbe.hostOf("https://meet.example.org:8443/room"))
        assertNull(JitsiProbe.hostOf("olcrtc-ab12cd34"))
        assertNull(JitsiProbe.hostOf(""))
    }
}
