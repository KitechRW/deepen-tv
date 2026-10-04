package rw.kitech.deepen

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent as AndroidKeyEvent
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rw.kitech.deepen.data.ChannelCatalog
import rw.kitech.deepen.data.JourneyStats
import rw.kitech.deepen.data.VideoStore
import rw.kitech.deepen.data.YouTubeArchive
import rw.kitech.deepen.model.ChannelSearchResult
import rw.kitech.deepen.model.ChannelSource
import rw.kitech.deepen.model.VideoItem
import kotlin.math.abs
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    private var tvPlayerKeyHandler: ((AndroidKeyEvent) -> Boolean)? = null
    private var playerProgressSaveHandler: (() -> Unit)? = null

    fun setTvPlayerKeyHandler(handler: ((AndroidKeyEvent) -> Boolean)?) {
        tvPlayerKeyHandler = handler
    }

    fun setPlayerProgressSaveHandler(handler: (() -> Unit)?) {
        playerProgressSaveHandler = handler
    }

    override fun dispatchKeyEvent(event: AndroidKeyEvent): Boolean {
        if (tvPlayerKeyHandler?.invoke(event) == true) {
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            MaterialTheme {
                DeepenApp()
            }
        }
    }

    override fun onPause() {
        playerProgressSaveHandler?.invoke()
        super.onPause()
    }
}

private enum class PlayerMode {
    VIDEO,
    LISTEN,
}

private data class InitialChannelState(
    val channel: ChannelSource,
    val featured: List<ChannelSource>,
    val personal: List<ChannelSource>,
    val video: VideoItem?,
    val stats: JourneyStats,
)

@Composable
private fun DeepenApp() {
    val context = LocalContext.current
    val store = remember(context) { VideoStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var currentVideo by remember { mutableStateOf<VideoItem?>(null) }
    var stats by remember { mutableStateOf(JourneyStats(0, 0)) }
    var syncing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var playerVideo by remember { mutableStateOf<VideoItem?>(null) }

    var defaultChannel by remember {
        mutableStateOf(ChannelCatalog.featuredChannels.first())
    }
    var featuredChannels by remember {
        mutableStateOf(ChannelCatalog.featuredChannels)
    }
    var userChannels by remember {
        mutableStateOf<List<ChannelSource>>(emptyList())
    }
    var settingsOpen by remember { mutableStateOf(false) }
    var selectedSettingsChannel by remember {
        mutableStateOf(ChannelCatalog.featuredChannels.first())
    }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember {
        mutableStateOf<List<ChannelSearchResult>>(emptyList())
    }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    suspend fun refreshChannels(
        selectedChannelId: String = selectedSettingsChannel.id,
    ) {
        val snapshot = withContext(Dispatchers.IO) {
            val currentDefault = store.defaultChannel()
            val featured = store.featuredChannels()
            val personal = store.userChannels()
            Triple(currentDefault, featured, personal)
        }

        defaultChannel = snapshot.first
        featuredChannels = snapshot.second
        userChannels = snapshot.third

        selectedSettingsChannel = (snapshot.second + snapshot.third)
            .firstOrNull { channel -> channel.id == selectedChannelId }
            ?: snapshot.first
    }

    suspend fun refreshLocalState(
        channelId: String = defaultChannel.id,
    ) {
        val snapshot = withContext(Dispatchers.IO) {
            store.currentVideo(channelId) to store.stats(channelId)
        }
        currentVideo = snapshot.first
        stats = snapshot.second
    }

    suspend fun syncArchive(channel: ChannelSource = defaultChannel) {
        if (BuildConfig.YOUTUBE_API_KEY.isBlank()) {
            val channelTotal = withContext(Dispatchers.IO) {
                store.stats(channel.id).total
            }
            if (channelTotal == 0) {
                errorMessage = "Add YOUTUBE_API_KEY to build Deepen and load the channel archive."
            }
            return
        }

        syncing = true
        errorMessage = null

        runCatching {
            withContext(Dispatchers.IO) {
                YouTubeArchive.sync(
                    apiKey = BuildConfig.YOUTUBE_API_KEY,
                    channel = channel,
                    store = store,
                )
            }
        }.onSuccess {
            val activeDefaultId = withContext(Dispatchers.IO) {
                store.defaultChannel().id
            }
            refreshChannels(selectedSettingsChannel.id)
            if (channel.id == activeDefaultId) {
                refreshLocalState(channel.id)
            }
        }.onFailure { error ->
            errorMessage = error.message ?: "Deepen could not synchronize with YouTube."
        }

        syncing = false
    }

    suspend fun searchYouTubeChannels() {
        val query = searchQuery.trim()
        if (query.isBlank()) return

        if (BuildConfig.YOUTUBE_API_KEY.isBlank()) {
            searchError = "This build does not contain a YouTube API key."
            return
        }

        searching = true
        searchError = null

        runCatching {
            withContext(Dispatchers.IO) {
                YouTubeArchive.searchChannels(
                    apiKey = BuildConfig.YOUTUBE_API_KEY,
                    query = query,
                )
            }
        }.onSuccess { results ->
            searchResults = results
            if (results.isEmpty()) {
                searchError = "No YouTube channels matched this search."
            }
        }.onFailure { error ->
            searchResults = emptyList()
            searchError = error.message ?: "Deepen could not search YouTube."
        }

        searching = false
    }

    LaunchedEffect(Unit) {
        val initial = withContext(Dispatchers.IO) {
            val channel = store.defaultChannel()
            val featured = store.featuredChannels()
            val personal = store.userChannels()
            val video = store.currentVideo(channel.id)
            val channelStats = store.stats(channel.id)
            InitialChannelState(
                channel = channel,
                featured = featured,
                personal = personal,
                video = video,
                stats = channelStats,
            )
        }

        defaultChannel = initial.channel
        selectedSettingsChannel = initial.channel
        featuredChannels = initial.featured
        userChannels = initial.personal
        currentVideo = initial.video
        stats = initial.stats

        if (BuildConfig.YOUTUBE_API_KEY.isNotBlank()) {
            syncArchive(initial.channel)
        } else if (initial.stats.total == 0) {
            errorMessage = "This build does not contain a YouTube API key."
        }
    }

    val activePlayerVideo = playerVideo

    if (activePlayerVideo != null) {
        PlayerScreen(
            video = activePlayerVideo,
            channel = defaultChannel,
            onProgress = { videoId, position, duration ->
                scope.launch(Dispatchers.IO) {
                    store.saveProgress(videoId, position, duration)
                }
            },
            onEnded = { videoId ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        store.markCompleted(videoId)
                    }

                    refreshLocalState(defaultChannel.id)

                    if (currentVideo != null) {
                        playerVideo = currentVideo
                    } else {
                        playerVideo = null
                    }
                }
            },
            onSkip = { videoId ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        store.markCompleted(videoId)
                    }

                    refreshLocalState(defaultChannel.id)

                    if (currentVideo != null) {
                        playerVideo = currentVideo
                    } else {
                        playerVideo = null
                    }
                }
            },
            onPrevious = { videoId, onResult ->
                scope.launch {
                    val previous = withContext(Dispatchers.IO) {
                        store.rewindToPrevious(defaultChannel.id, videoId)
                    }

                    if (previous != null) {
                        refreshLocalState(defaultChannel.id)
                        playerVideo = previous
                        onResult(true)
                    } else {
                        onResult(false)
                    }
                }
            },
            onExit = {
                playerVideo = null
                scope.launch {
                    refreshLocalState(defaultChannel.id)
                }
            },
        )
    } else if (settingsOpen && searchOpen) {
        ChannelSearchScreen(
            query = searchQuery,
            results = searchResults,
            searching = searching,
            error = searchError,
            existingChannelIds = (featuredChannels + userChannels)
                .mapNotNull { channel -> channel.youtubeChannelId }
                .toSet(),
            onQueryChange = { query ->
                searchQuery = query
                searchResults = emptyList()
                searchError = null
            },
            onSearch = {
                scope.launch {
                    searchYouTubeChannels()
                }
            },
            onClear = {
                searchQuery = ""
                searchResults = emptyList()
                searchError = null
            },
            onChooseResult = { result ->
                scope.launch {
                    val existing = (featuredChannels + userChannels)
                        .firstOrNull { channel ->
                            channel.youtubeChannelId == result.youtubeChannelId
                        }

                    val selected = if (existing != null) {
                        existing
                    } else {
                        val added = withContext(Dispatchers.IO) {
                            store.addUserChannel(result)
                        }
                        refreshChannels(added.id)
                        withContext(Dispatchers.IO) {
                            store.channel(added.id)
                        } ?: added
                    }

                    selectedSettingsChannel = selected
                    searchOpen = false
                    searchQuery = ""
                    searchResults = emptyList()
                    searchError = null
                }
            },
            onBack = {
                searchOpen = false
                searchError = null
            },
        )
    } else if (settingsOpen) {
        ChannelSettingsScreen(
            defaultChannel = defaultChannel,
            selectedChannel = selectedSettingsChannel,
            featuredChannels = featuredChannels,
            userChannels = userChannels,
            onSearchOpen = {
                searchQuery = ""
                searchResults = emptyList()
                searchError = null
                searchOpen = true
            },
            onSelectChannel = { channel ->
                selectedSettingsChannel = channel
            },
            onSetDefault = { channel ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        store.setDefaultChannel(channel.id)
                    }

                    refreshChannels(channel.id)
                    val selected = withContext(Dispatchers.IO) {
                        store.channel(channel.id)
                    } ?: channel
                    defaultChannel = selected.copy(isDefault = true)
                    selectedSettingsChannel = selected.copy(isDefault = true)
                    refreshLocalState(channel.id)

                    if (BuildConfig.YOUTUBE_API_KEY.isNotBlank()) {
                        syncArchive(selected.copy(isDefault = true))
                    }
                }
            },
            onRemoveUserChannel = { channel ->
                scope.launch {
                    val removedDefault = channel.id == defaultChannel.id

                    val fallback = withContext(Dispatchers.IO) {
                        if (removedDefault) {
                            store.setDefaultChannel(ChannelCatalog.DEFAULT_CHANNEL_ID)
                        }

                        store.removeUserChannel(channel.id)
                        store.defaultChannel()
                    }

                    refreshChannels(selectedSettingsChannel.id)

                    if (removedDefault) {
                        defaultChannel = fallback
                        selectedSettingsChannel = fallback
                        refreshLocalState(fallback.id)

                        if (BuildConfig.YOUTUBE_API_KEY.isNotBlank()) {
                            syncArchive(fallback)
                        }
                    }
                }
            },
            onBack = {
                settingsOpen = false
                searchOpen = false
                searchQuery = ""
                searchResults = emptyList()
                searchError = null
                selectedSettingsChannel = defaultChannel
                scope.launch {
                    refreshLocalState(defaultChannel.id)
                }
            },
        )
    } else {
        HomeScreen(
            currentVideo = currentVideo,
            stats = stats,
            activeChannel = defaultChannel,
            syncing = syncing,
            errorMessage = errorMessage,
            onContinue = {
                currentVideo?.let {
                    playerVideo = it
                }
            },
            onSync = {
                scope.launch {
                    syncArchive(defaultChannel)
                }
            },
            onSettings = {
                selectedSettingsChannel = defaultChannel
                settingsOpen = true

                if (BuildConfig.YOUTUBE_API_KEY.isNotBlank()) {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            YouTubeArchive.refreshChannelMetadata(
                                apiKey = BuildConfig.YOUTUBE_API_KEY,
                                channels = featuredChannels,
                                store = store,
                            )
                        }
                        refreshChannels(defaultChannel.id)
                    }
                }
            },
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            store.close()
        }
    }
}

@Composable
private fun HomeScreen(
    currentVideo: VideoItem?,
    stats: JourneyStats,
    activeChannel: ChannelSource,
    syncing: Boolean,
    errorMessage: String?,
    onContinue: () -> Unit,
    onSync: () -> Unit,
    onSettings: () -> Unit,
) {
    val journeyProgress = if (stats.total == 0) {
        0f
    } else {
        stats.watched.toFloat() / stats.total.toFloat()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepenBackground),
    ) {
        Image(
            painter = painterResource(R.drawable.deepen_home_background_exact),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(0.64f)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF020711).copy(alpha = 0.96f),
                            Color(0xFF041126).copy(alpha = 0.82f),
                            Color(0xFF071B35).copy(alpha = 0.38f),
                            Color.Transparent,
                        ),
                    )
                ),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 64.dp, top = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.deepen_icon),
                contentDescription = "Deepen",
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(13.dp)),
                contentScale = ContentScale.Crop,
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = "DEEPEN",
                    color = Color.White,
                    fontSize = 31.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "From the beginning",
                    color = Color(0xFFC7D0DC),
                    fontSize = 15.sp,
                )
            }
        }

        DeepenSyncButton(
            syncing = syncing,
            onClick = onSync,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 44.dp, end = 60.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.62f)
                .padding(start = 64.dp, end = 24.dp),
        ) {
            when {
                currentVideo != null -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HomeChannelAvatar(
                            imageUrl = activeChannel.thumbnailUrl,
                            contentDescription = activeChannel.sourceName,
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            text = currentVideo.title,
                            color = Color.White,
                            fontSize = 31.sp,
                            lineHeight = 43.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_calendar_white),
                                contentDescription = "Published date",
                                modifier = Modifier.size(27.dp),
                            )

                            Spacer(modifier = Modifier.width(11.dp))

                            Column {
                                Text(
                                    text = publishedDayLabel(currentVideo.publishedAt),
                                    color = Color(0xFFBAC5D3),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = publishedDateLabel(currentVideo.publishedAt),
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(25.dp))

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(44.dp)
                                .background(Color.White.copy(alpha = 0.32f)),
                        )

                        Spacer(modifier = Modifier.width(25.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_youtube_white),
                                contentDescription = "YouTube channel",
                                modifier = Modifier.size(30.dp),
                            )

                            Spacer(modifier = Modifier.width(11.dp))

                            Column {
                                Text(
                                    text = activeChannel.sourceName,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = activeChannel.secondaryLabel,
                                    color = Color(0xFFBAC5D3),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                        }
                    }

                    errorMessage?.let { message ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = message,
                            color = DeepenError,
                            fontSize = 15.sp,
                        )
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DeepenPlayButton(
                            onClick = onContinue,
                        )

                        if (currentVideo.progressSeconds > 1.0) {
                            Spacer(modifier = Modifier.width(22.dp))

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(42.dp)
                                    .background(Color.White.copy(alpha = 0.32f)),
                            )

                            Spacer(modifier = Modifier.width(22.dp))

                            Text(
                                text = "Resume · ${formatPlaybackTime(currentVideo.progressSeconds)}",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                stats.total > 0 -> {
                    Text(
                        text = "You’re caught up.",
                        color = Color.White,
                        fontSize = 31.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Sync to check for new videos.",
                        color = DeepenMuted,
                        fontSize = 17.sp,
                    )
                }

                syncing -> {
                    Text(
                        text = "Preparing your journey…",
                        color = Color.White,
                        fontSize = 31.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                else -> {
                    Text(
                        text = "Start from the beginning.",
                        color = Color.White,
                        fontSize = 31.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Sync this channel to begin.",
                        color = DeepenMuted,
                        fontSize = 17.sp,
                    )
                }
            }

            if (currentVideo == null) {
                errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = message,
                        color = DeepenError,
                        fontSize = 16.sp,
                    )
                }
            }
        }

        DeepenSettingsButton(
            onClick = onSettings,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 60.dp, bottom = 42.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.52f)
                .padding(start = 64.dp, bottom = 42.dp),
        ) {
            Text(
                text = "${stats.watched} of ${stats.total} watched",
                color = Color(0xFFC2CCD8),
                fontSize = 15.sp,
            )

            Spacer(modifier = Modifier.height(11.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF64758A).copy(alpha = 0.70f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(journeyProgress.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(5.dp))
                        .background(DeepenBlue),
                )
            }
        }
    }
}

@Composable
private fun HomeChannelAvatar(
    imageUrl: String?,
    contentDescription: String?,
) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(Color(0xFF111D2F)),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl.isNullOrBlank()) {
            Image(
                painter = painterResource(R.drawable.ic_youtube_white),
                contentDescription = contentDescription,
                modifier = Modifier.size(28.dp),
            )
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                placeholder = painterResource(R.drawable.ic_youtube_white),
                error = painterResource(R.drawable.ic_youtube_white),
                fallback = painterResource(R.drawable.ic_youtube_white),
            )
        }
    }
}

@Composable
private fun DeepenSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)

    Button(
        onClick = onClick,
        modifier = modifier
            .size(42.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) {
                    Color(0xFFBDEBFF)
                } else {
                    Color.White.copy(alpha = 0.18f)
                },
                shape = shape,
            ),
        scale = ButtonDefaults.scale(
            scale = 1.0f,
            focusedScale = 1.10f,
            pressedScale = 0.96f,
        ),
        shape = ButtonDefaults.shape(
            shape = shape,
            focusedShape = shape,
            pressedShape = shape,
        ),
        colors = ButtonDefaults.colors(
            containerColor = Color(0xFF0A1525).copy(alpha = 0.92f),
            contentColor = Color.White,
            focusedContainerColor = Color(0xFF164F86),
            focusedContentColor = Color.White,
            pressedContainerColor = Color(0xFF0E355C),
            pressedContentColor = Color.White,
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            text = "⚙",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DeepenPlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val pill = RoundedCornerShape(50)

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            surfaceVariant = Color(0xFF208BFF),
            onSurface = Color.White,
            inverseSurface = Color(0xFF48B7FF),
            inverseOnSurface = Color.White,
        ),
    ) {
        Button(
            onClick = onClick,
            modifier = modifier
                .onFocusChanged { focused = it.isFocused }
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) {
                        Color(0xFFBDEBFF)
                    } else {
                        Color.White.copy(alpha = 0.10f)
                    },
                    shape = pill,
                ),
            scale = ButtonDefaults.scale(
                scale = 1.0f,
                focusedScale = 1.06f,
                pressedScale = 0.98f,
            ),
            shape = ButtonDefaults.shape(
                shape = pill,
                focusedShape = pill,
                pressedShape = pill,
            ),
            colors = ButtonDefaults.colors(
                containerColor = Color(0xFF208BFF),
                contentColor = Color.White,
                focusedContainerColor = Color(0xFF35B9FF),
                focusedContentColor = Color.White,
                pressedContainerColor = Color(0xFF1378E8),
                pressedContentColor = Color.White,
                disabledContainerColor = Color(0xFF31516E),
                disabledContentColor = Color(0xFFB8C7D6),
            ),
            contentPadding = PaddingValues(
                horizontal = 28.dp,
                vertical = 15.dp,
            ),
        ) {
            Text(
                text = "▶",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(11.dp))
            Text(
                text = "PLAY",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DeepenSyncButton(
    syncing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val pill = RoundedCornerShape(50)

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            surfaceVariant = Color(0xFF0B1422),
            onSurface = Color.White,
            inverseSurface = Color(0xFF173A63),
            inverseOnSurface = Color.White,
        ),
    ) {
        Button(
            onClick = onClick,
            enabled = !syncing,
            modifier = modifier
                .onFocusChanged { focused = it.isFocused }
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) {
                        Color(0xFF8FD6FF)
                    } else {
                        Color.White.copy(alpha = 0.24f)
                    },
                    shape = pill,
                ),
            scale = ButtonDefaults.scale(
                scale = 1.0f,
                focusedScale = 1.06f,
                pressedScale = 0.98f,
            ),
            shape = ButtonDefaults.shape(
                shape = pill,
                focusedShape = pill,
                pressedShape = pill,
            ),
            colors = ButtonDefaults.colors(
                containerColor = Color(0xFF0A1525),
                contentColor = Color.White,
                focusedContainerColor = Color(0xFF164F86),
                focusedContentColor = Color.White,
                pressedContainerColor = Color(0xFF0E355C),
                pressedContentColor = Color.White,
                disabledContainerColor = Color(0xFF111A25),
                disabledContentColor = Color(0xFF708090),
            ),
            contentPadding = PaddingValues(
                horizontal = 20.dp,
                vertical = 11.dp,
            ),
        ) {
            Text(
                text = "↻",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (syncing) "SYNCING…" else "SYNC",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PlayerScreen(
    video: VideoItem,
    channel: ChannelSource,
    onProgress: (String, Double, Double) -> Unit,
    onEnded: (String) -> Unit,
    onSkip: (String) -> Unit,
    onPrevious: (String, (Boolean) -> Unit) -> Unit,
    onExit: () -> Unit,
) {
    var playbackPosition by remember(video.videoId) {
        mutableStateOf(video.progressSeconds)
    }
    var durationSeconds by remember(video.videoId) {
        mutableStateOf(video.durationSeconds)
    }
    var playbackState by remember(video.videoId) {
        mutableStateOf("LOADING")
    }
    var playbackError by remember(video.videoId) {
        mutableStateOf<String?>(null)
    }
    var loadedFraction by remember(video.videoId) {
        mutableStateOf(0.0)
    }
    var bufferAheadSeconds by remember(video.videoId) {
        mutableStateOf(0.0)
    }
    var overlayVisible by remember(video.videoId) {
        mutableStateOf(true)
    }
    var controlsForcedHidden by remember(video.videoId) {
        mutableStateOf(false)
    }
    var controlPulse by remember(video.videoId) {
        mutableStateOf(0)
    }
    var controlFeedback by remember(video.videoId) {
        mutableStateOf<String?>(null)
    }
    var pendingSeekSeconds by remember(video.videoId) {
        mutableStateOf(0)
    }
    var skipArmed by remember(video.videoId) {
        mutableStateOf(false)
    }
    var skipArmPulse by remember(video.videoId) {
        mutableStateOf(0)
    }
    var previousArmed by remember(video.videoId) {
        mutableStateOf(false)
    }
    var previousArmPulse by remember(video.videoId) {
        mutableStateOf(0)
    }
    var playerMode by remember {
        mutableStateOf(PlayerMode.VIDEO)
    }
    var modeContextActive by remember(video.videoId) {
        mutableStateOf(false)
    }
    var modeContextSuppressed by remember(video.videoId) {
        mutableStateOf(false)
    }
    var modeContextPulse by remember(video.videoId) {
        mutableStateOf(0)
    }

    LaunchedEffect(
        playbackState,
        controlPulse,
        controlsForcedHidden,
        playerMode,
    ) {
        if (playerMode == PlayerMode.LISTEN) {
            controlsForcedHidden = false
            overlayVisible = true
        } else if (controlsForcedHidden) {
            overlayVisible = false
        } else if (playbackState == "PLAYING") {
            delay(5000)
            overlayVisible = false
        } else {
            overlayVisible = true
        }
    }

    LaunchedEffect(controlPulse) {
        if (controlPulse > 0) {
            delay(900)
            controlFeedback = null
            pendingSeekSeconds = 0
        }
    }

    LaunchedEffect(skipArmPulse) {
        if (skipArmed) {
            delay(2500)
            skipArmed = false
        }
    }

    LaunchedEffect(previousArmPulse) {
        if (previousArmed) {
            delay(2500)
            previousArmed = false
        }
    }

    LaunchedEffect(modeContextPulse) {
        if (modeContextActive || modeContextSuppressed) {
            delay(2200)
            modeContextActive = false
            modeContextSuppressed = false
        }
    }

    LaunchedEffect(playbackState, video.videoId, durationSeconds) {
        while (playbackState == "PLAYING") {
            delay(1000)
            playbackPosition = if (durationSeconds > 0.0) {
                (playbackPosition + 1.0).coerceAtMost(durationSeconds)
            } else {
                playbackPosition + 1.0
            }
        }
    }

    fun persistProgress() {
        onProgress(
            video.videoId,
            playbackPosition,
            durationSeconds,
        )
    }

    val activity = LocalContext.current as? MainActivity
    val currentPersistProgress = rememberUpdatedState {
        persistProgress()
    }

    DisposableEffect(activity, video.videoId) {
        activity?.setPlayerProgressSaveHandler {
            currentPersistProgress.value.invoke()
        }

        onDispose {
            activity?.setPlayerProgressSaveHandler(null)
        }
    }

    BackHandler {
        persistProgress()
        onExit()
    }

    val progress = if (durationSeconds > 0.0) {
        (playbackPosition / durationSeconds).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (playerMode == PlayerMode.LISTEN) {
                    DeepenBackground
                } else {
                    Color.Black
                },
            ),
    ) {
        val density = LocalDensity.current
        val minimumVideoWidth = with(density) { 480f.toDp() }
        val listenVideoWidth = maxOf(
            maxWidth * 0.26f,
            minimumVideoWidth,
        )

        if (playerMode == PlayerMode.LISTEN) {
            Image(
                painter = painterResource(R.drawable.deepen_listen_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        key(video.videoId) {
            YouTubePlayer(
                video = video,
                modifier = if (playerMode == PlayerMode.LISTEN) {
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 28.dp, end = 28.dp)
                        .width(listenVideoWidth)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(10.dp))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                        )
                } else {
                    Modifier.fillMaxSize()
                },
                onProgress = { _, position, duration ->
                    if (duration > 0.0) {
                        durationSeconds = duration
                    }

                    if (playbackState != "PLAYING" || playbackPosition <= 0.0) {
                        playbackPosition = position
                    }

                    if (
                        position > 0.0 &&
                        (playbackState == "LOADING" || playbackState == "READY")
                    ) {
                        playbackState = "PLAYING"
                        playbackError = null
                    }
                },
                onBufferTelemetry = { current, duration, loaded ->
                    loadedFraction = loaded.coerceIn(0.0, 1.0)
                    bufferAheadSeconds = if (duration > 0.0) {
                        (loadedFraction * duration - current).coerceAtLeast(0.0)
                    } else {
                        0.0
                    }
                },
                onPlaybackState = { state ->
                    playbackState = state
                    if (state == "PLAYING") {
                        playbackError = null
                    }

                    if (state == "PAUSED") {
                        controlsForcedHidden = false
                        overlayVisible = true
                        controlFeedback = null
                        persistProgress()
                    }
                },
                onPlaybackError = { code ->
                    playbackState = "ERROR"
                    playbackError = playerErrorMessage(code)
                },
                onControl = { action ->
                    controlPulse += 1

                    val directionalAction = action == "UP" ||
                        action == "SKIP" ||
                        action == "SEEK_BACK" ||
                        action == "SEEK_FORWARD"

                    fun registerDirectionalContext() {
                        when {
                            modeContextActive -> {
                                modeContextActive = false
                                modeContextSuppressed = true
                                modeContextPulse += 1
                            }

                            modeContextSuppressed -> {
                                modeContextPulse += 1
                            }

                            else -> {
                                modeContextActive = true
                                modeContextPulse += 1
                            }
                        }
                    }

                    fun switchPlayerMode() {
                        playerMode = if (playerMode == PlayerMode.VIDEO) {
                            PlayerMode.LISTEN
                        } else {
                            PlayerMode.VIDEO
                        }
                        modeContextActive = false
                        modeContextSuppressed = false
                        controlsForcedHidden = false
                        overlayVisible = true
                        previousArmed = false
                        skipArmed = false
                        pendingSeekSeconds = 0
                        controlFeedback = null
                    }

                    if (!overlayVisible) {
                        controlsForcedHidden = false
                        overlayVisible = true
                        previousArmed = false
                        skipArmed = false
                        pendingSeekSeconds = 0
                        controlFeedback = null

                        if (directionalAction) {
                            registerDirectionalContext()
                        }

                        false
                    } else if (action == "CONFIRM" && modeContextActive) {
                        switchPlayerMode()
                        false
                    } else {
                        controlsForcedHidden = false

                        if (directionalAction) {
                            registerDirectionalContext()
                        }

                        if (action == "UP") {
                            skipArmed = false
                            pendingSeekSeconds = 0
                            controlFeedback = null

                            if (previousArmed) {
                                previousArmed = false
                                persistProgress()
                                onPrevious(video.videoId) { moved ->
                                    if (!moved) {
                                        controlFeedback = "BEGINNING OF ARCHIVE"
                                        controlPulse += 1
                                    }
                                }
                            } else {
                                previousArmed = true
                                previousArmPulse += 1
                            }
                        } else {
                            previousArmed = false

                            when (action) {
                                "SEEK_BACK" -> {
                                    skipArmed = false
                                    playbackPosition = (playbackPosition - 10.0).coerceAtLeast(0.0)
                                    pendingSeekSeconds -= 10
                                    controlFeedback = if (pendingSeekSeconds < 0) {
                                        "↶  " + abs(pendingSeekSeconds) + " SEC"
                                    } else {
                                        "↷  " + pendingSeekSeconds + " SEC"
                                    }
                                }

                                "SEEK_FORWARD" -> {
                                    skipArmed = false
                                    playbackPosition = if (durationSeconds > 0.0) {
                                        (playbackPosition + 30.0).coerceAtMost(durationSeconds)
                                    } else {
                                        playbackPosition + 30.0
                                    }
                                    pendingSeekSeconds += 30
                                    controlFeedback = if (pendingSeekSeconds < 0) {
                                        "↶  " + abs(pendingSeekSeconds) + " SEC"
                                    } else {
                                        "↷  " + pendingSeekSeconds + " SEC"
                                    }
                                }

                                "SKIP" -> {
                                    pendingSeekSeconds = 0
                                    controlFeedback = null
                                    if (skipArmed) {
                                        skipArmed = false
                                        persistProgress()
                                        onSkip(video.videoId)
                                    } else {
                                        skipArmed = true
                                        skipArmPulse += 1
                                    }
                                }

                                "CONFIRM",
                                "TOGGLE" -> {
                                    skipArmed = false
                                    pendingSeekSeconds = 0
                                    controlFeedback = null
                                }

                                "PLAY" -> {
                                    skipArmed = false
                                    pendingSeekSeconds = 0
                                    controlFeedback = null
                                }

                                "PAUSE" -> {
                                    skipArmed = false
                                    pendingSeekSeconds = 0
                                    controlFeedback = null
                                }

                                "SHOW" -> {
                                    skipArmed = false
                                    pendingSeekSeconds = 0
                                    controlFeedback = null
                                }
                            }
                        }

                        true
                    }
                },
                onEnded = { videoId ->
                    persistProgress()
                    onEnded(videoId)
                },
            )
        }


        if (overlayVisible && playerMode == PlayerMode.VIDEO) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(Color.Black)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.deepen_icon),
                            contentDescription = "Deepen",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp)),
                            contentScale = ContentScale.Crop,
                        )

                        Spacer(modifier = Modifier.width(11.dp))

                        Column {
                            Text(
                                text = "DEEPEN",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "From the beginning",
                                color = DeepenMuted,
                                fontSize = 11.sp,
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            text = channel.sourceName,
                            color = DeepenMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = publishedMonthDayLabel(video.publishedAt),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(DeepenBlue),
                )
            }
        } else if (playerMode == PlayerMode.VIDEO) {
            Image(
                painter = painterResource(R.drawable.deepen_icon),
                contentDescription = "Deepen",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 20.dp, top = 15.dp)
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp)),
                contentScale = ContentScale.Crop,
            )
        }

        if (
            playbackError == null &&
            (playbackState == "LOADING" || playbackState == "READY")
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF08111E))
                    .border(
                        width = 1.dp,
                        color = DeepenBlue.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(18.dp),
                    )
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.deepen_icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "DEEPEN",
                        color = DeepenBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Preparing…",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (playbackState == "BUFFERING" && playbackError == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF08111E))
                    .border(
                        width = 1.dp,
                        color = DeepenBlue.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(18.dp),
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "DEEPEN  ·  BUFFERING…",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Loaded ${(loadedFraction * 100).roundToInt()}%  ·  Ahead ≈ ${formatPlaybackTime(bufferAheadSeconds)}",
                    color = DeepenMuted,
                    fontSize = 11.sp,
                )
            }
        }

        if (previousArmed) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.50f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF08111E))
                    .border(
                        width = 1.dp,
                        color = DeepenBlue,
                        shape = RoundedCornerShape(18.dp),
                    )
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "GO TO PREVIOUS VIDEO?",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    text = "Press ↑ again to confirm",
                    color = DeepenMuted,
                    fontSize = 14.sp,
                )
            }
        }

        if (skipArmed) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.50f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF08111E))
                    .border(
                        width = 1.dp,
                        color = DeepenBlue,
                        shape = RoundedCornerShape(18.dp),
                    )
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "MARK AS WATCHED & CONTINUE?",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    text = "Press ↓ again to confirm",
                    color = DeepenMuted,
                    fontSize = 14.sp,
                )
            }
        }

        if (
            playbackState == "PAUSED" &&
            playbackError == null &&
            !previousArmed &&
            !skipArmed &&
            controlFeedback == null
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF08111E))
                    .border(
                        width = 2.dp,
                        color = DeepenBlue,
                        shape = RoundedCornerShape(50),
                    )
                    .padding(horizontal = 30.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "▶  PLAY",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        controlFeedback?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF08111E))
                    .border(
                        width = 2.dp,
                        color = DeepenBlue.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(50),
                    )
                    .padding(horizontal = 26.dp, vertical = 15.dp),
            ) {
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        playbackError?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.68f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF08111E))
                    .border(
                        width = 1.dp,
                        color = DeepenError.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(20.dp),
                    )
                    .padding(28.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.deepen_icon),
                        contentDescription = null,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp)),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "DEEPEN",
                            color = DeepenBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Playback problem",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 16.sp,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Press Back to return to your journey.",
                    color = DeepenMuted,
                    fontSize = 13.sp,
                )
            }
        }

        if (overlayVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(horizontal = 26.dp, vertical = 17.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = video.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.72f),
                    )

                    Text(
                        text = "${formatPlaybackTime(playbackPosition)}  /  ${formatPlaybackTime(durationSeconds)}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF273242)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .clip(RoundedCornerShape(5.dp))
                            .background(DeepenBlue),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerControlChip(
                            "OK",
                            if (modeContextActive) {
                                "Switch mode"
                            } else if (playbackState == "PLAYING") {
                                "Pause"
                            } else {
                                "Play"
                            },
                        )
                        PlayerControlChip("←", "10s")
                        PlayerControlChip("→", "30s")
                        PlayerControlChip("↑", "Previous")
                        PlayerControlChip("↓", "Skip")
                        PlayerControlChip("BACK", "Journey")
                    }

                    PlayerModeContextChip(
                        mode = playerMode,
                        contextActive = modeContextActive,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerModeContextChip(
    mode: PlayerMode,
    contextActive: Boolean,
) {
    val targetMode = if (mode == PlayerMode.VIDEO) {
        "LISTEN"
    } else {
        "VIDEO"
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (contextActive) {
                    Color(0xFF0B2E52)
                } else {
                    Color(0xFF0D1724)
                },
            )
            .border(
                width = 1.dp,
                color = if (contextActive) {
                    DeepenBlue.copy(alpha = 0.86f)
                } else {
                    Color.White.copy(alpha = 0.10f)
                },
                shape = RoundedCornerShape(50),
            )
            .padding(
                horizontal = if (contextActive) 13.dp else 11.dp,
                vertical = 6.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (contextActive) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(DeepenBlue.copy(alpha = 0.16f))
                    .border(
                        width = 1.dp,
                        color = DeepenBlue.copy(alpha = 0.42f),
                        shape = RoundedCornerShape(50),
                    )
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "OK",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "SWITCH TO $targetMode",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.35.sp,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(DeepenBlue),
            )

            Spacer(modifier = Modifier.width(7.dp))

            Text(
                text = if (mode == PlayerMode.LISTEN) {
                    "LISTEN"
                } else {
                    "VIDEO"
                },
                color = DeepenMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

@Composable
private fun PlayerControlChip(
    keyLabel: String,
    actionLabel: String,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF111A26))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = keyLabel,
            color = DeepenBlue,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = actionLabel,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubePlayer(
    video: VideoItem,
    modifier: Modifier = Modifier,
    onProgress: (String, Double, Double) -> Unit,
    onBufferTelemetry: (Double, Double, Double) -> Unit,
    onPlaybackState: (String) -> Unit,
    onPlaybackError: (String) -> Unit,
    onControl: (String) -> Boolean,
    onEnded: (String) -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    val activity = LocalContext.current as? MainActivity

    DisposableEffect(activity, webView, video.videoId) {
        val handler: (AndroidKeyEvent) -> Boolean = { event ->
            val action = when (event.keyCode) {
                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                AndroidKeyEvent.KEYCODE_ENTER,
                AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                AndroidKeyEvent.KEYCODE_BUTTON_A -> "CONFIRM"

                AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "TOGGLE"
                AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> "PLAY"
                AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> "PAUSE"

                AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> "SEEK_BACK"

                AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "SEEK_FORWARD"

                AndroidKeyEvent.KEYCODE_DPAD_UP,
                AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS -> "UP"

                AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> "SKIP"

                else -> null
            }

            if (action == null) {
                false
            } else {
                if (
                    event.action == AndroidKeyEvent.ACTION_DOWN &&
                    event.repeatCount == 0
                ) {
                    val script = when (action) {
                        "CONFIRM",
                        "TOGGLE" -> "togglePlayback();"
                        "PLAY" -> "playVideo();"
                        "PAUSE" -> "pauseVideo();"
                        "SEEK_BACK" -> "queueSeekBy(-10);"
                        "SEEK_FORWARD" -> "queueSeekBy(30);"
                        else -> ""
                    }

                    val executeAction = onControl(action)
                    if (executeAction && script.isNotEmpty()) {
                        webView?.evaluateJavascript(script, null)
                    }
                }

                true
            }
        }

        activity?.setTvPlayerKeyHandler(handler)

        onDispose {
            activity?.setTvPlayerKeyHandler(null)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.loadsImagesAutomatically = true

                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                addJavascriptInterface(
                    PlayerBridge(
                        videoId = video.videoId,
                        onProgress = onProgress,
                        onBufferTelemetry = onBufferTelemetry,
                        onPlaybackState = onPlaybackState,
                        onPlaybackError = onPlaybackError,
                        onEnded = onEnded,
                    ),
                    "AndroidBridge",
                )

                isFocusable = false
                isFocusableInTouchMode = false

                loadDataWithBaseURL(
                    "https://rw.kitech.deepen/",
                    youtubePlayerHtml(
                        videoId = video.videoId,
                        startSeconds = (video.progressSeconds - 2.0)
                            .coerceAtLeast(0.0)
                            .roundToInt(),
                    ),
                    "text/html",
                    "UTF-8",
                    null,
                )

                webView = this
            }
        },
    )

    DisposableEffect(video.videoId) {
        onDispose {
            webView?.apply {
                removeJavascriptInterface("AndroidBridge")
                stopLoading()
                destroy()
            }
            webView = null
        }
    }
}

private class PlayerBridge(
    private val videoId: String,
    private val onProgress: (String, Double, Double) -> Unit,
    private val onBufferTelemetry: (Double, Double, Double) -> Unit,
    private val onPlaybackState: (String) -> Unit,
    private val onPlaybackError: (String) -> Unit,
    private val onEnded: (String) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onProgress(
        currentSeconds: Double,
        durationSeconds: Double,
        loadedFraction: Double,
    ) {
        mainHandler.post {
            onProgress(videoId, currentSeconds, durationSeconds)
            onBufferTelemetry(currentSeconds, durationSeconds, loadedFraction)
        }
    }

    @JavascriptInterface
    fun onPlaybackState(state: String) {
        mainHandler.post {
            onPlaybackState(state)
        }
    }

    @JavascriptInterface
    fun onPlayerError(code: String) {
        mainHandler.post {
            onPlaybackError(code)
        }
    }

    @JavascriptInterface
    fun onEnded() {
        mainHandler.post {
            onEnded(videoId)
        }
    }
}

private fun youtubePlayerHtml(
    videoId: String,
    startSeconds: Int,
): String {
    return """
        <!doctype html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                html, body {
                    width: 100%;
                    height: 100%;
                    margin: 0;
                    padding: 0;
                    overflow: hidden;
                    background: #000;
                }
                #poster {
                    position: fixed;
                    inset: 0;
                    background:
                        linear-gradient(rgba(0,0,0,.18), rgba(0,0,0,.55)),
                        url('https://i.ytimg.com/vi/$videoId/hqdefault.jpg') center center / cover no-repeat;
                }
                #player {
                    position: fixed;
                    inset: 0;
                    width: 100%;
                    height: 100%;
                    opacity: 0;
                    transition: opacity 180ms ease;
                    background: #000;
                }
            </style>
        </head>
        <body>
            <div id="poster"></div>
            <div id="player"></div>
            <script src="https://www.youtube.com/iframe_api"></script>
            <script>
                var player;
                var progressTimer;
                var pendingSeekDelta = 0;
                var pendingSeekBase = null;
                var seekCommitTimer = null;

                function onYouTubeIframeAPIReady() {
                    player = new YT.Player('player', {
                        width: '100%',
                        height: '100%',
                        videoId: '$videoId',
                        playerVars: {
                            autoplay: 1,
                            controls: 0,
                            rel: 0,
                            playsinline: 1,
                            iv_load_policy: 3,
                            disablekb: 1,
                            enablejsapi: 1,
                            origin: 'https://rw.kitech.deepen',
                            widget_referrer: 'https://rw.kitech.deepen'
                        },
                        events: {
                            onReady: onPlayerReady,
                            onStateChange: onPlayerStateChange,
                            onError: onPlayerError,
                            onAutoplayBlocked: onAutoplayBlocked
                        }
                    });
                }

                function disableCaptions() {
                    if (!player) return;
                    try { player.unloadModule('captions'); } catch (e) {}
                    try { player.unloadModule('cc'); } catch (e) {}
                    try { player.setOption('captions', 'track', {}); } catch (e) {}
                }

                function onPlayerReady(event) {
                    AndroidBridge.onPlaybackState('READY');
                    disableCaptions();
                    setTimeout(disableCaptions, 300);
                    setTimeout(disableCaptions, 1200);

                    if ($startSeconds > 0) {
                        event.target.seekTo($startSeconds, true);
                    }

                    event.target.playVideo();

                    reportProgress();
                    progressTimer = setInterval(reportProgress, 5000);
                }

                function reportProgress() {
                    if (!player || typeof player.getCurrentTime !== 'function') return;

                    var current = player.getCurrentTime() || 0;
                    var duration = player.getDuration() || 0;
                    var loaded = 0;

                    if (typeof player.getVideoLoadedFraction === 'function') {
                        loaded = player.getVideoLoadedFraction() || 0;
                    }

                    AndroidBridge.onProgress(current, duration, loaded);
                }

                function onPlayerStateChange(event) {
                    if (event.data === YT.PlayerState.PLAYING) {
                        disableCaptions();
                        var playerElement = document.getElementById('player');
                        if (playerElement) playerElement.style.opacity = '1';
                        var poster = document.getElementById('poster');
                        if (poster) poster.style.display = 'none';
                        AndroidBridge.onPlaybackState('PLAYING');
                    } else if (event.data === YT.PlayerState.PAUSED) {
                        AndroidBridge.onPlaybackState('PAUSED');
                        reportProgress();
                    } else if (event.data === YT.PlayerState.BUFFERING) {
                        AndroidBridge.onPlaybackState('BUFFERING');
                        reportProgress();
                    } else if (event.data === YT.PlayerState.CUED) {
                        AndroidBridge.onPlaybackState('READY');
                    }

                    if (event.data === YT.PlayerState.ENDED) {
                        reportProgress();
                        AndroidBridge.onPlaybackState('ENDED');
                        if (progressTimer) {
                            clearInterval(progressTimer);
                        }
                        AndroidBridge.onEnded();
                    }
                }

                function onPlayerError(event) {
                    var code = String(event && event.data != null ? event.data : 'UNKNOWN');
                    var playerElement = document.getElementById('player');
                    if (playerElement) playerElement.style.opacity = '0';
                    AndroidBridge.onPlayerError(code);
                }

                function onAutoplayBlocked() {
                    AndroidBridge.onPlaybackState('READY');
                }

                function playVideo() {
                    if (!player || typeof player.playVideo !== 'function') return;
                    player.playVideo();
                }

                function pauseVideo() {
                    if (!player || typeof player.pauseVideo !== 'function') return;
                    player.pauseVideo();
                }

                function togglePlayback() {
                    if (!player || typeof player.getPlayerState !== 'function') return;

                    var state = player.getPlayerState();
                    if (state === YT.PlayerState.PLAYING) {
                        pauseVideo();
                    } else {
                        playVideo();
                    }
                }

                function commitQueuedSeek() {
                    if (!player || pendingSeekBase === null) return;

                    var duration = player.getDuration() || 0;
                    var target = Math.max(0, pendingSeekBase + pendingSeekDelta);

                    if (duration > 0) {
                        target = Math.min(duration, target);
                    }

                    player.seekTo(target, true);
                    setTimeout(reportProgress, 150);
                    pendingSeekDelta = 0;
                    pendingSeekBase = null;
                    seekCommitTimer = null;
                }

                function queueSeekBy(seconds) {
                    if (!player || typeof player.getCurrentTime !== 'function') return;

                    if (pendingSeekBase === null) {
                        pendingSeekBase = player.getCurrentTime() || 0;
                    }

                    pendingSeekDelta += seconds;

                    if (seekCommitTimer) {
                        clearTimeout(seekCommitTimer);
                    }

                    seekCommitTimer = setTimeout(commitQueuedSeek, 420);
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}

private fun playerErrorMessage(code: String): String {
    return when (code) {
        "2" -> "YouTube rejected this video ID."
        "5" -> "YouTube could not start HTML5 playback on this TV."
        "100" -> "This video is unavailable or has been removed."
        "101", "150" -> "The owner of this video does not allow embedded playback."
        "153" -> "YouTube could not identify Deepen as the embedding app. Please install the latest Deepen build."
        else -> "YouTube player error $code. Please return and try again."
    }
}

private fun formatPlaybackTime(seconds: Double): String {
    val totalSeconds = seconds.coerceAtLeast(0.0).roundToInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val remainingSeconds = totalSeconds % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, remainingSeconds)
    } else {
        "%d:%02d".format(minutes, remainingSeconds)
    }
}

private fun parsePublishedDate(value: String) =
    runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(value.take(10))
    }.getOrNull()

private fun publishedDayLabel(value: String): String {
    val date = parsePublishedDate(value) ?: return ""
    return SimpleDateFormat("EEEE", Locale.US)
        .format(date)
        .uppercase(Locale.US)
}

private fun publishedDateLabel(value: String): String {
    val date = parsePublishedDate(value) ?: return value.take(10)
    return SimpleDateFormat("MMMM d, yyyy", Locale.US)
        .format(date)
        .uppercase(Locale.US)
}

private fun publishedMonthDayLabel(value: String): String {
    val date = parsePublishedDate(value) ?: return value.take(10)
    return SimpleDateFormat("MMMM d, yyyy", Locale.US)
        .format(date)
}

private val DeepenBlue = Color(0xFF3399FF)
private val DeepenBackground = Color(0xFF080A0D)
private val DeepenPanel = Color(0xFF15191F)
private val DeepenTrack = Color(0xFF2A3038)
private val DeepenMuted = Color(0xFFA5AFBC)
private val DeepenError = Color(0xFFFF8A80)
