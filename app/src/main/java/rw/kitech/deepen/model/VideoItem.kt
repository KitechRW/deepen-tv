package rw.kitech.deepen.model

data class VideoItem(
    val videoId: String,
    val title: String,
    val publishedAt: String,
    val completed: Boolean = false,
    val progressSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
)
