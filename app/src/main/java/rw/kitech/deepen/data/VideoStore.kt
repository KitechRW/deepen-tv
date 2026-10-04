package rw.kitech.deepen.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import rw.kitech.deepen.model.ChannelSearchResult
import rw.kitech.deepen.model.ChannelSource
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
        createChannelsTable(db)
        createVideosTable(db)
        seedFeaturedChannels(db)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        if (oldVersion < 2) {
            createChannelsTable(db)
            seedFeaturedChannels(db)

            db.execSQL(
                """
                ALTER TABLE videos
                ADD COLUMN channel_id TEXT NOT NULL DEFAULT 'paul-gitwaza'
                """.trimIndent()
            )

            db.execSQL("DROP INDEX IF EXISTS idx_videos_journey")
            db.execSQL(
                """
                CREATE INDEX idx_videos_journey
                ON videos(channel_id, completed, published_at, video_id)
                """.trimIndent()
            )
        }

        if (oldVersion == 2 && newVersion >= 3) {
            db.execSQL(
                """
                ALTER TABLE channels
                ADD COLUMN thumbnail_url TEXT
                """.trimIndent()
            )
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        seedFeaturedChannels(db)
    }

    fun defaultChannel(): ChannelSource {
        readableDatabase.rawQuery(
            """
            SELECT id, display_name, source_name, youtube_channel_id, youtube_handle,
                   lookup_query, featured, featured_order, is_default, user_added, thumbnail_url
            FROM channels
            WHERE is_default = 1
            ORDER BY featured_order ASC, display_name ASC
            LIMIT 1
            """.trimIndent(),
            emptyArray(),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.toChannelSource()
            }
        }

        val fallback = ChannelCatalog.featuredChannels.first()
        setDefaultChannel(fallback.id)
        return channel(fallback.id) ?: fallback
    }

    fun channel(channelId: String): ChannelSource? {
        readableDatabase.rawQuery(
            """
            SELECT id, display_name, source_name, youtube_channel_id, youtube_handle,
                   lookup_query, featured, featured_order, is_default, user_added, thumbnail_url
            FROM channels
            WHERE id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(channelId),
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toChannelSource() else null
        }
    }

    fun featuredChannels(): List<ChannelSource> {
        return readChannels(
            """
            SELECT id, display_name, source_name, youtube_channel_id, youtube_handle,
                   lookup_query, featured, featured_order, is_default, user_added, thumbnail_url
            FROM channels
            WHERE featured = 1
            ORDER BY featured_order ASC, display_name ASC
            """.trimIndent()
        )
    }

    fun userChannels(): List<ChannelSource> {
        return readChannels(
            """
            SELECT id, display_name, source_name, youtube_channel_id, youtube_handle,
                   lookup_query, featured, featured_order, is_default, user_added, thumbnail_url
            FROM channels
            WHERE user_added = 1
            ORDER BY display_name COLLATE NOCASE ASC
            """.trimIndent()
        )
    }

    fun addUserChannel(result: ChannelSearchResult): ChannelSource {
        val id = "youtube:" + result.youtubeChannelId
        val handle = result.handle?.removePrefix("@")?.takeIf { it.isNotBlank() }

        val values = ContentValues().apply {
            put("id", id)
            put("display_name", result.displayName)
            put("source_name", result.displayName)
            put("youtube_channel_id", result.youtubeChannelId)
            handle?.let { put("youtube_handle", it) }
            result.thumbnailUrl?.let { put("thumbnail_url", it) }
            put("lookup_query", result.displayName)
            put("featured", 0)
            put("featured_order", 999)
            put("is_default", 0)
            put("user_added", 1)
        }

        writableDatabase.insertWithOnConflict(
            "channels",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE,
        )

        return channel(id) ?: ChannelSource(
            id = id,
            displayName = result.displayName,
            sourceName = result.displayName,
            youtubeChannelId = result.youtubeChannelId,
            youtubeHandle = handle,
            thumbnailUrl = result.thumbnailUrl,
            lookupQuery = result.displayName,
            isUserAdded = true,
        )
    }

    fun setDefaultChannel(channelId: String) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.execSQL("UPDATE channels SET is_default = 0")
            val values = ContentValues().apply {
                put("is_default", 1)
            }
            val updated = writableDatabase.update(
                "channels",
                values,
                "id = ?",
                arrayOf(channelId),
            )
            if (updated == 0) {
                error("Could not set the selected Deepen channel as default.")
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun removeUserChannel(channelId: String): Boolean {
        val deleted = writableDatabase.delete(
            "channels",
            "id = ? AND user_added = 1",
            arrayOf(channelId),
        )
        return deleted > 0
    }

    fun updateChannelIdentity(
        channelId: String,
        youtubeChannelId: String,
        sourceName: String,
        handle: String?,
        thumbnailUrl: String?,
    ) {
        val values = ContentValues().apply {
            put("youtube_channel_id", youtubeChannelId)
            put("source_name", sourceName)
            handle?.removePrefix("@")?.takeIf { it.isNotBlank() }?.let {
                put("youtube_handle", it)
            }
            thumbnailUrl?.takeIf { it.isNotBlank() }?.let {
                put("thumbnail_url", it)
            }
        }

        writableDatabase.update(
            "channels",
            values,
            "id = ?",
            arrayOf(channelId),
        )
    }

    fun upsertArchiveItems(
        channelId: String,
        items: List<VideoItem>,
    ) {
        if (items.isEmpty()) return

        writableDatabase.beginTransaction()
        try {
            items.forEach { item ->
                val values = ContentValues().apply {
                    put("video_id", item.videoId)
                    put("channel_id", channelId)
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
                    put("channel_id", channelId)
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

    fun isKnown(
        channelId: String,
        videoId: String,
    ): Boolean {
        readableDatabase.rawQuery(
            """
            SELECT 1
            FROM videos
            WHERE channel_id = ? AND video_id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(channelId, videoId),
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    fun currentVideo(channelId: String): VideoItem? {
        readableDatabase.rawQuery(
            """
            SELECT video_id, title, published_at, completed, progress_seconds, duration_seconds
            FROM videos
            WHERE channel_id = ? AND completed = 0
            ORDER BY published_at ASC, video_id ASC
            LIMIT 1
            """.trimIndent(),
            arrayOf(channelId),
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toVideoItem() else null
        }
    }

    fun previousVideo(
        channelId: String,
        videoId: String,
    ): VideoItem? {
        val publishedAt = readableDatabase.rawQuery(
            """
            SELECT published_at
            FROM videos
            WHERE channel_id = ? AND video_id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(channelId, videoId),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            cursor.getString(0)
        }

        readableDatabase.rawQuery(
            """
            SELECT video_id, title, published_at, completed, progress_seconds, duration_seconds
            FROM videos
            WHERE channel_id = ?
              AND (
                    published_at < ?
                    OR (published_at = ? AND video_id < ?)
              )
            ORDER BY published_at DESC, video_id DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(channelId, publishedAt, publishedAt, videoId),
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toVideoItem() else null
        }
    }

    fun rewindToPrevious(
        channelId: String,
        videoId: String,
    ): VideoItem? {
        val previous = previousVideo(channelId, videoId) ?: return null

        val values = ContentValues().apply {
            put("completed", 0)
            put("progress_seconds", 0.0)
        }

        writableDatabase.update(
            "videos",
            values,
            "channel_id = ? AND video_id = ?",
            arrayOf(channelId, previous.videoId),
        )

        return previous.copy(
            completed = false,
            progressSeconds = 0.0,
        )
    }

    fun stats(channelId: String): JourneyStats {
        readableDatabase.rawQuery(
            """
            SELECT
                COALESCE(SUM(CASE WHEN completed = 1 THEN 1 ELSE 0 END), 0),
                COUNT(*)
            FROM videos
            WHERE channel_id = ?
            """.trimIndent(),
            arrayOf(channelId),
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

    private fun createChannelsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS channels (
                id TEXT PRIMARY KEY,
                display_name TEXT NOT NULL,
                source_name TEXT NOT NULL,
                youtube_channel_id TEXT,
                youtube_handle TEXT,
                lookup_query TEXT NOT NULL,
                featured INTEGER NOT NULL DEFAULT 0,
                featured_order INTEGER NOT NULL DEFAULT 999,
                is_default INTEGER NOT NULL DEFAULT 0,
                user_added INTEGER NOT NULL DEFAULT 0,
                thumbnail_url TEXT
            )
            """.trimIndent()
        )
    }

    private fun createVideosTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE videos (
                video_id TEXT PRIMARY KEY,
                channel_id TEXT NOT NULL,
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
            ON videos(channel_id, completed, published_at, video_id)
            """.trimIndent()
        )
    }

    private fun seedFeaturedChannels(db: SQLiteDatabase) {
        if (!db.isOpen) return

        ChannelCatalog.featuredChannels.forEach { channel ->
            val values = ContentValues().apply {
                put("id", channel.id)
                put("display_name", channel.displayName)
                put("source_name", channel.sourceName)
                channel.youtubeChannelId?.let { put("youtube_channel_id", it) }
                channel.youtubeHandle?.let { put("youtube_handle", it.removePrefix("@")) }
                channel.thumbnailUrl?.let { put("thumbnail_url", it) }
                put("lookup_query", channel.lookupQuery)
                put("featured", 1)
                put("featured_order", channel.featuredOrder)
                put("is_default", if (channel.id == ChannelCatalog.DEFAULT_CHANNEL_ID) 1 else 0)
                put("user_added", 0)
            }

            db.insertWithOnConflict(
                "channels",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE,
            )

            val metadata = ContentValues().apply {
                put("display_name", channel.displayName)
                put("lookup_query", channel.lookupQuery)
                put("featured", 1)
                put("featured_order", channel.featuredOrder)

                channel.youtubeChannelId?.takeIf { it.isNotBlank() }?.let {
                    put("youtube_channel_id", it)
                }
                channel.youtubeHandle?.removePrefix("@")?.takeIf { it.isNotBlank() }?.let {
                    put("youtube_handle", it)
                }
                channel.thumbnailUrl?.takeIf { it.isNotBlank() }?.let {
                    put("thumbnail_url", it)
                }

                if (channel.sourceName.isNotBlank()) {
                    put("source_name", channel.sourceName)
                }
            }

            db.update(
                "channels",
                metadata,
                "id = ?",
                arrayOf(channel.id),
            )
        }
    }

    private fun readChannels(query: String): List<ChannelSource> {
        val channels = mutableListOf<ChannelSource>()
        readableDatabase.rawQuery(query, emptyArray()).use { cursor ->
            while (cursor.moveToNext()) {
                channels += cursor.toChannelSource()
            }
        }
        return channels
    }

    private fun Cursor.toChannelSource(): ChannelSource {
        return ChannelSource(
            id = getString(0),
            displayName = getString(1),
            sourceName = getString(2),
            youtubeChannelId = getStringOrNull(3),
            youtubeHandle = getStringOrNull(4),
            lookupQuery = getString(5),
            featured = getInt(6) == 1,
            featuredOrder = getInt(7),
            isDefault = getInt(8) == 1,
            isUserAdded = getInt(9) == 1,
            thumbnailUrl = getStringOrNull(10),
        )
    }

    private fun Cursor.getStringOrNull(index: Int): String? {
        return if (isNull(index)) null else getString(index)
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
        private const val DATABASE_VERSION = 3
        private const val COMPLETION_THRESHOLD = 0.95
    }
}
