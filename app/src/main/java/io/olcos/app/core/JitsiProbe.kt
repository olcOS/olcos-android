package io.olcos.app.core

/**
 * Which public Jitsi servers let the VPS in, asked from the VPS itself. Many
 * Jitsi servers turn away some countries (meet.egovm.ru and meet1.arbitr.ru
 * answer only Russian addresses), so the app's default can be closed to a
 * server abroad. The candidates and the check are the ones olcos-core's
 * server/install.sh and the iOS add-server wizard use: config.js is served,
 * /xmpp-websocket upgrades, and config.js asks for no token or guest-domain
 * login. Passing the check does not prove data flows; it rules out the
 * servers that cannot work at all.
 */
object JitsiProbe {
    /**
     * In the order the install prefers them, the app's default first; the same
     * list and order as olcos-core's server/install.sh. meet.mirox.chat is left
     * out: it lets both sides in and carried nothing (2026-10 and 2026-11).
     */
    val CANDIDATES = listOf(
        "meet.egovm.ru",
        "meet.riddlerx.org",
        "meet.mamba.group",
        "conf.expressmoney.com",
        "meet.ecopsy.com",
        "conference.ct.placetime.team",
        "jitsy.amateusfox.online",
        "webinar.knomary.dev",
        "meet.playform.ru",
        "webinar.devknomarylms.ru",
        "zgn-y-vc01.zignotch.com",
        "conf.hyperia.space",
        "jitsi.etudevs.ru",
        "meet1.arbitr.ru",
    )

    private val HOST = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$")

    /** What one server answered: the HTTP status of config.js, of the WebSocket upgrade, and whether it asks for a login. */
    data class Answer(val host: String, val config: Int, val websocket: Int, val login: Boolean) {
        val usable: Boolean get() = config == 200 && websocket == 101 && !login
    }

    /** What the script prints when the VPS has no curl to check with. */
    const val NO_CURL = "JITSI_NO_CURL"

    /**
     * A script for the VPS that checks the hosts at once and prints one line
     * per host: `JITSI <host> <config.js status> <upgrade status> <login 0|1>`.
     * Hosts that are not plain host names are left out. curl's exit status is
     * not used: after a 101 it waits on the upgraded connection until its
     * timeout and fails, with the status already printed.
     */
    fun script(hosts: List<String> = CANDIDATES): String {
        val list = hosts.map { it.lowercase() }.filter { HOST.matches(it) }.joinToString(" ")
        return """
            command -v curl >/dev/null 2>&1 || { echo $NO_CURL; exit 0; }
            probe() {
              h=${'$'}1; f="${'$'}D/${'$'}h.js"
              c=${'$'}(curl -s -m 6 -o "${'$'}f" -w '%{http_code}' "https://${'$'}h/config.js" 2>/dev/null)
              w=${'$'}(curl -s -o /dev/null -m 6 --http1.1 -H 'Connection: Upgrade' -H 'Upgrade: websocket' -H 'Sec-WebSocket-Version: 13' -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' -H 'Sec-WebSocket-Protocol: xmpp' -H "Origin: https://${'$'}h" -w '%{http_code}' "https://${'$'}h/xmpp-websocket" 2>/dev/null)
              l=0
              grep -Eq '^[[:space:]]*(anonymousdomain|tokenAuthUrl)[[:space:]]*:' "${'$'}f" 2>/dev/null && l=1
              echo "JITSI ${'$'}h ${'$'}{c:-000} ${'$'}{w:-000} ${'$'}l"
            }
            D=${'$'}(mktemp -d)
            for h in $list; do probe "${'$'}h" & done
            wait
            rm -rf "${'$'}D"
        """.trimIndent()
    }

    /** The answers in the script's output, one per host; other lines are ignored. */
    fun parse(output: String): List<Answer> = output.lines().mapNotNull { line ->
        val f = line.trim().split(Regex("\\s+"))
        if (f.size != 5 || f[0] != "JITSI") return@mapNotNull null
        Answer(f[1], f[2].toIntOrNull() ?: 0, f[3].toIntOrNull() ?: 0, f[4] == "1")
    }.distinctBy { it.host }

    /** The first host of [order] whose answer is usable; null when none is. */
    fun best(answers: List<Answer>, order: List<String> = CANDIDATES): String? {
        val usable = answers.filter { it.usable }.map { it.host }.toSet()
        return order.firstOrNull { it in usable }
    }

    /** The host of a Jitsi base or room URL, lowercased; null when it names none. */
    fun hostOf(url: String): String? {
        val rest = url.trim().substringAfter("://", "")
        if (rest.isEmpty()) return null
        return rest.substringBefore('/').substringBefore(':').lowercase().ifEmpty { null }
    }
}
