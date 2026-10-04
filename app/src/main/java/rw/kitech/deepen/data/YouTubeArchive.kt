package rw.kitech.deepen.data

import org.json.JSONObject
import rw.kitech.deepen.model.ChannelSearchResult
import rw.kitech.deepen.model.ChannelSource
import rw.kitech.deepen.model.VideoItem
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class SyncResult(
    val added: Int,
    val total: Int,
)

private data class ResolvedChannel(
    val youtubeChannelId: String,
    val title: String,
    val handle: String?,
    val uploadsPlaylistId: String,
)

object YouTubeArchive {
    private const val API_BASE = "https://www.googleapis.com/youtube/v3"

    fun sync(
        apiKey: String,
        channel: ChannelSource,
        store: VideoStore,
    ): SyncResult {
        require(apiKey.isNotBlank()) {
            "YOUTUBE_API_KEY is missing. Add it to the build configuration."
        }

        val resolved = resolveChannel(apiKey, channel)
        store.updateChannelIdentity(
            channelId = channel.id,
            youtubeChannelId = resolved.youtubeChannelId,
            sourceName = resolved.title,
            handle = resolved.handle,
        )

        val hasExistingArchive = store.stats(channel.id).total > 0
        val additions = mutableListOf<VideoItem>()

        var pageToken: String? = null
        var reachedKnownVideo = false

        do {
            val json = getJson(
                endpoint = "playlistItems",
                parameters = buildMap {
                    put("part", "snippet,contentDetails")
                    put("playlistId", resolved.uploadsPlaylistId)
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

                    if (hasExistingArchive && store.isKnown(channel.id, videoId)) {
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

        store.upsertArchiveItems(channel.id, additions)

        return SyncResult(
            added = additions.size,
            total = store.stats(channel.id).total,
        )
    }

    fun searchChannels(
        apiKey: String,
        query: String,
    ): List<ChannelSearchResult> {
        require(apiKey.isNotBlank()) {
            "YOUTUBE_API_KEY is missing. Add it to the build configuration."
        }

        val cleanedQuery = query.trim()
        if (cleanedQuery.isBlank()) return emptyList()

        val searchJson = getJson(
            endpoint = "search",
            parameters = mapOf(
                "part" to "snippet",
                "type" to "channel",
                "q" to cleanedQuery,
                "maxResults" to "8",
                "key" to apiKey,
            ),
        )

        val ordered = mutableListOf<Pair<String, String>>()
        val items = searchJson.optJSONArray("items")
        if (items != null) {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val channelId = item
                    .optJSONObject("id")
                    ?.optString("channelId")
                    .orEmpty()
                val title = item
                    .optJSONObject("snippet")
                    ?.optString("title")
                    .orEmpty()

                if (channelId.isNotBlank() && title.isNotBlank()) {
                    ordered += channelId to title
                }
            }
        }

        if (ordered.isEmpty()) return emptyList()

        val details = getJson(
            endpoint = "channels",
            parameters = mapOf(
                "part" to "snippet",
                "id" to ordered.joinToString(",") { it.first },
                "maxResults" to ordered.size.toString(),
                "key" to apiKey,
            ),
        )

        val handlesById = mutableMapOf<String, String?>()
        val titlesById = mutableMapOf<String, String>()
        val detailItems = details.optJSONArray("items")
        if (detailItems != null) {
            for (index in 0 until detailItems.length()) {
                val item = detailItems.getJSONObject(index)
                val id = item.optString("id")
                val snippet = item.optJSONObject("snippet")
                val handle = snippet
                    ?.optString("customUrl")
                    ?.removePrefix("@")
                    ?.takeIf { it.isNotBlank() }
                val title = snippet?.optString("title").orEmpty()

                if (id.isNotBlank()) {
                    handlesById[id] = handle
                    if (title.isNotBlank()) {
                        titlesById[id] = title
                    }
                }
            }
        }

        return ordered.map { (channelId, searchTitle) ->
            ChannelSearchResult(
                youtubeChannelId = channelId,
                displayName = titlesById[channelId] ?: searchTitle,
                handle = handlesById[channelId],
            )
        }
    }

    private fun resolveChannel(
        apiKey: String,
        channel: ChannelSource,
    ): ResolvedChannel {
        channel.youtubeChannelId
            ?.takeIf { it.isNotBlank() }
            ?.let { return fetchChannelDetails(apiKey, "id", it) }

        channel.youtubeHandle
            ?.removePrefix("@")
            ?.takeIf { it.isNotBlank() }
            ?.let { handle ->
                return fetchChannelDetails(apiKey, "forHandle", handle)
            }

        val candidates = searchChannels(
            apiKey = apiKey,
            query = channel.lookupQuery,
        )

        val candidate = candidates
            .maxByOrNull { result ->
                similarityScore(
                    target = channel.displayName + " " + channel.sourceName,
                    candidate = result.displayName,
                )
            }
            ?: error("Could not find the YouTube channel for " + channel.displayName + ".")

        return fetchChannelDetails(
            apiKey = apiKey,
            selector = "id",
            selectorValue = candidate.youtubeChannelId,
        )
    }

    private fun fetchChannelDetails(
        apiKey: String,
        selector: String,
        selectorValue: String,
    ): ResolvedChannel {
        val json = getJson(
            endpoint = "channels",
            parameters = mapOf(
                "part" to "snippet,contentDetails",
                selector to selectorValue,
                "key" to apiKey,
            ),
        )

        val items = json.optJSONArray("items")
        if (items == null || items.length() == 0) {
            error("Could not resolve the selected YouTube channel.")
        }

        val item = items.getJSONObject(0)
        val snippet = item.optJSONObject("snippet")
        val contentDetails = item.optJSONObject("contentDetails")
        val youtubeChannelId = item.optString("id")
        val title = snippet?.optString("title").orEmpty()
        val handle = snippet
            ?.optString("customUrl")
            ?.removePrefix("@")
            ?.takeIf { it.isNotBlank() }
        val uploads = contentDetails
            ?.optJSONObject("relatedPlaylists")
            ?.optString("uploads")
            .orEmpty()

        if (youtubeChannelId.isBlank() || uploads.isBlank()) {
            error("The selected YouTube channel does not expose an uploads playlist.")
        }

        return ResolvedChannel(
            youtubeChannelId = youtubeChannelId,
            title = title.ifBlank { selectorValue },
            handle = handle,
            uploadsPlaylistId = uploads,
        )
    }

    private fun similarityScore(
        target: String,
        candidate: String,
    ): Int {
        val targetTokens = normalize(target)
            .split(" ")
            .filter { it.length >= 3 }
            .toSet()
        val candidateTokens = normalize(candidate)
            .split(" ")
            .filter { it.length >= 3 }
            .toSet()

        return targetTokens.count { it in candidateTokens }
    }

    private fun normalize(value: String): String {
        return value
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    private fun getJson(
        endpoint: String,
        parameters: Map<String, String>,
    ): JSONObject {
        val query = parameters.entries.joinToString("&") { (key, value) ->
            encode(key) + "=" + encode(value)
        }

        val connection = URL(API_BASE + "/" + endpoint + "?" + query)
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
                        ?: "YouTube API request failed with HTTP " + responseCode + "."
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
