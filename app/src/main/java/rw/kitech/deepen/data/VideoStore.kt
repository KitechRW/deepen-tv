package rw.kitech.deepen.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import rw.kitech.deepen.model.VideoItem

data class JourneyStats(
    val watched: Int,
    val total: Int,
)

class VideoStore(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE videos (
                video_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                published_at TEXT NOT NULL,
                completed INTEGER NOT NULL DEFAULT 0,
                progress_seconds REAL NOT NULL DEFAULT 0,
                duration_seconds REAL NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX idx_videos_journey
            ON videos(completed, published_at, video_id)
            """.trimIndent()
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        // Schema version 1 only.
    }

    fun upsertArchiveItems(items: List<VideoItem>) {
        if (items.isEmpty()) return

        writableDatabase.beginTransaction()
        try {
            items.forEach { item ->
                val values = ContentValues().apply {
                    put("video_id", item.videoId)
                    put("title", item.title)
                    put("published_at", item.publishedAt)
                }

                writableDatabase.insertWithOnConflict(
                    "videos",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE,
                )

                val metadata = ContentValues().apply {
                    put("title", item.title)
                    put("published_at", item.publishedAt)
                }
                writableDatabase.update(
                    "videos",
                    metadata,
                    "video_id = ?",
                    arrayOf(item.videoId),
                )
            }

            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun isKnown(videoId: String): Boolean {
        readableDatabase.rawQuery(
            "SELECT 1 FROM videos WHERE video_id = ? LIMIT 1",
            arrayOf(videoId),
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    fun currentVideo(): VideoItem? {
        readableDatabase.rawQuery(
            """
            SELECT video_id, title, published_at, completed, progress_seconds, duration_seconds
            FROM videos
            WHERE completed = 0
            ORDER BY published_at ASC, video_id ASC
            LIMIT 1
            """.trimIndent(),
            emptyArray(),
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toVideoItem() else null
        }
    }

    fun stats(): JourneyStats {
        readableDatabase.rawQuery(
            """
            SELECT
                COALESCE(SUM(CASE WHEN completed = 1 THEN 1 ELSE 0 END), 0),
                COUNT(*)
            FROM videos
            """.trimIndent(),
            emptyArray(),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return JourneyStats(0, 0)
            return JourneyStats(
                watched = cursor.getInt(0),
                total = cursor.getInt(1),
            )
        }
    }

    fun saveProgress(
        videoId: String,
        progressSeconds: Double,
        durationSeconds: Double,
    ) {
        val values = ContentValues().apply {
            put("progress_seconds", progressSeconds.coerceAtLeast(0.0))

            if (durationSeconds > 0.0) {
                put("duration_seconds", durationSeconds)
            }

            if (
                durationSeconds > 0.0 &&
                progressSeconds / durationSeconds >= COMPLETION_THRESHOLD
            ) {
                put("completed", 1)
            }
        }

        writableDatabase.update(
            "videos",
            values,
            "video_id = ?",
            arrayOf(videoId),
        )
    }

    fun markCompleted(videoId: String) {
        val values = ContentValues().apply {
            put("completed", 1)
        }

        writableDatabase.update(
            "videos",
            values,
            "video_id = ?",
            arrayOf(videoId),
        )
    }

    private fun Cursor.toVideoItem(): VideoItem {
        return VideoItem(
            videoId = getString(0),
            title = getString(1),
            publishedAt = getString(2),
            completed = getInt(3) == 1,
            progressSeconds = getDouble(4),
            durationSeconds = getDouble(5),
        )
    }

    companion object {
        private const val DATABASE_NAME = "deepen.db"
        private const val DATABASE_VERSION = 1
        private const val COMPLETION_THRESHOLD = 0.95
    }
}
