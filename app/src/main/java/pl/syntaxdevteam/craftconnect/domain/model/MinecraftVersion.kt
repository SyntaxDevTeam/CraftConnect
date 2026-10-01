package pl.syntaxdevteam.craftconnect.domain.model

/** Stable persisted keys; never persist enum ordinals. */
enum class MinecraftVersion(val label: String, val protocol: Int) {
    JAVA_26_1("26.1", 775),
    JAVA_26_2("26.2", 776),
    JAVA_26_3("26.3", 777);

    companion object {
        fun fromKey(key: String): MinecraftVersion = entries.firstOrNull { it.name == key }
            ?: JAVA_26_1
    }
}
