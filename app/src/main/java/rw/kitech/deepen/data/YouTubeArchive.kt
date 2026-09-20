package rw.kitech.deepen.data

import org.json.JSONObject
import rw.kitech.deepen.model.VideoItem
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SyncResult(
    val added: Int,
    val total: Int,
)

object YouTubeArchive {
    private const val API_BASE = "https://www.googleapis.com/youtube/v3"
    private const val CHANNEL_HANDLE = "drpaulmgitwaza"

    fun sync(
        apiKey: String,
        store: VideoStore,
    ): SyncResult {
        require(apiKey.isNotBlank()) {
            "YOUTUBE_API_KEY is missing. Add it to the build configuration."
        }

        val uploadsPlaylistId = resolveUploadsPlaylist(apiKey)
        val hasExistingArchive = store.stats().total > 0
        val additions = mutableListOf<VideoItem>()

        var pageToken: String? = null
        var reachedKnownVideo = false

        do {
            val json = getJson(
                endpoint = "playlistItems",
                parameters = buildMap {
                    put("part", "snippet,contentDetails")
                    put("playlistId", uploadsPlaylistId)
                    put("maxResults", "50")
                    put("key", apiKey)
                    pageToken?.let { put("pageToken", it) }
                },
            )

            val items = json.optJSONArray("items")
            if (items != null) {
                for (index in 0 until items.length()) {
                    val item = items.getJSONObject(index)
                    val snippet = item.optJSONObject("snippet") ?: continue
                    val contentDetails = item.optJSONObject("contentDetails") ?: continue

                    val videoId = contentDetails.optString("videoId")
                    val title = snippet.optString("title")
                    val publishedAt = contentDetails.optString("videoPublishedAt")
                        .ifBlank { snippet.optString("publishedAt") }

                    if (
                        videoId.isBlank() ||
                        publishedAt.isBlank() ||
                        title == "Deleted video" ||
                        title == "Private video"
                    ) {
                        continue
                    }

                    if (hasExistingArchive && store.isKnown(videoId)) {
                        reachedKnownVideo = true
                        break
                    }

                    additions += VideoItem(
                        videoId = videoId,
                        title = title,
                        publishedAt = publishedAt,
                    )
                }
            }

            if (reachedKnownVideo) break

            pageToken = json.optString("nextPageToken")
                .takeIf { it.isNotBlank() }
        } while (pageToken != null)

        store.upsertArchiveItems(additions)

        return SyncResult(
            added = additions.size,
            total = store.stats().total,
        )
    }

    private fun resolveUploadsPlaylist(apiKey: String): String {
        val json = getJson(
            endpoint = "channels",
            parameters = mapOf(
                "part" to "contentDetails",
                "forHandle" to CHANNEL_HANDLE,
                "key" to apiKey,
            ),
        )

        val items = json.optJSONArray("items")
        if (items == null || items.length() == 0) {
            error("Could not resolve the YouTube channel @$CHANNEL_HANDLE.")
        }

        val uploads = items
            .getJSONObject(0)
            .getJSONObject("contentDetails")
            .getJSONObject("relatedPlaylists")
            .optString("uploads")

        if (uploads.isBlank()) {
            error("The YouTube channel does not expose an uploads playlist.")
        }

        return uploads
    }

    private fun getJson(
        endpoint: String,
        parameters: Map<String, String>,
    ): JSONObject {
        val query = parameters.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }

        val connection = URL("$API_BASE/$endpoint?$query")
            .openConnection() as HttpURLConnection

        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/json")

        try {
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val body = stream?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()

            if (responseCode !in 200..299) {
                val apiMessage = runCatching {
                    JSONObject(body)
                        .optJSONObject("error")
                        ?.optString("message")
                }.getOrNull()

                error(
                    apiMessage?.takeIf { it.isNotBlank() }
                        ?: "YouTube API request failed with HTTP $responseCode."
                )
            }

            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name())
    }
}
