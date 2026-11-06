package io.olcos.app.core

/**
 * Lanes on one conference host fail together: the owner's two Jitsi rooms sit
 * on one self-hosted server, and the old server's history has them going down
 * together in most cases. The bond takes a lane's backup, and sends a stalled
 * lane's frames again, on a lane of another group first (SetLaneGroup), so a
 * second room on the same host stops counting as a spare. A lane with no key is
 * a group of its own. The iOS app's LaneGroup.
 */
object LaneGroup {
    /** The group a lane belongs to, as the bond takes it: the conference host its room names, or "" for a room that names none. */
    fun key(carrier: String, room: String): String {
        val host = host(room) ?: return ""
        return "${carrier.lowercase()}:${host.lowercase()}"
    }

    /**
     * PURE: the host a room URL names, everything before its last path segment,
     * the way the core's jitsi provider reads one ("https://meet.example.org/room"
     * and "meet.example.org/room" give "meet.example.org", "meet.example.com/jitsi/room"
     * gives "meet.example.com/jitsi"), so rooms behind one host share it whatever
     * their scheme or path prefix. Null for a bare room id: nothing says where it lives.
     */
    fun host(room: String): String? {
        var text = room.trim()
        if (text.isEmpty()) return null
        val scheme = text.indexOf("://")
        if (scheme >= 0) text = text.substring(scheme + 3)
        val cut = text.lastIndexOf('/')
        if (cut <= 0) return null
        if (text.substring(cut + 1).isEmpty()) return null
        return text.substring(0, cut)
    }
}
