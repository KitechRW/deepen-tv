package rw.kitech.deepen.model

data class ChannelSource(
    val id: String,
    val displayName: String,
    val sourceName: String,
    val youtubeChannelId: String? = null,
    val youtubeHandle: String? = null,
    val lookupQuery: String,
    val featured: Boolean = false,
    val featuredOrder: Int = 0,
    val isDefault: Boolean = false,
    val isUserAdded: Boolean = false,
) {
    val secondaryLabel: String
        get() = youtubeHandle
            ?.takeIf { it.isNotBlank() }
            ?.let { handle -> if (handle.startsWith("@")) handle else "@$handle" }
            ?: sourceName
}

data class ChannelSearchResult(
    val youtubeChannelId: String,
    val displayName: String,
    val handle: String? = null,
)
