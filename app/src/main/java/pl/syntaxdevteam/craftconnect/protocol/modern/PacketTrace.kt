package pl.syntaxdevteam.craftconnect.protocol.modern

/** Bounded metadata only: never packet payloads, chat, URLs, cookies or credentials. */
internal class PacketTrace {
    private val entries = ArrayDeque<String>()
    @Synchronized fun record(direction: String, id: Int) {
        if (entries.size == 12) entries.removeFirst()
        entries.addLast(direction + id.toString(16))
    }
    @Synchronized fun suffix(): String = entries.joinToString("_", prefix = "_trace_")
    @Synchronized fun clear() = entries.clear()
}
