package rw.kitech.deepen

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import rw.kitech.deepen.data.JourneyStats
import rw.kitech.deepen.data.VideoStore
import rw.kitech.deepen.data.YouTubeArchive
import rw.kitech.deepen.model.VideoItem
import kotlin.math.abs
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    private var tvPlayerKeyHandler: ((AndroidKeyEvent) -> Boolean)? = null

    fun setTvPlayerKeyHandler(handler: ((AndroidKeyEvent) -> Boolean)?) {
        tvPlayerKeyHandler = handler
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
}

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

    suspend fun refreshLocalState() {
        val snapshot = withContext(Dispatchers.IO) {
            store.currentVideo() to store.stats()
        }
        currentVideo = snapshot.first
        stats = snapshot.second
    }

    suspend fun syncArchive() {
        if (BuildConfig.YOUTUBE_API_KEY.isBlank()) {
            if (stats.total == 0) {
                errorMessage = "Add YOUTUBE_API_KEY to build Deepen and load the teaching archive."
            }
            return
        }

        syncing = true
        errorMessage = null

        runCatching {
            withContext(Dispatchers.IO) {
                YouTubeArchive.sync(
                    apiKey = BuildConfig.YOUTUBE_API_KEY,
                    store = store,
                )
            }
        }.onSuccess {
            refreshLocalState()
        }.onFailure { error ->
            errorMessage = error.message ?: "Deepen could not synchronize with YouTube."
        }

        syncing = false
    }

    LaunchedEffect(Unit) {
        refreshLocalState()
        if (BuildConfig.YOUTUBE_API_KEY.isNotBlank()) {
            syncArchive()
        } else if (stats.total == 0) {
            errorMessage = "This build does not contain a YouTube API key."
        }
    }

    val activePlayerVideo = playerVideo

    if (activePlayerVideo != null) {
        PlayerScreen(
            video = activePlayerVideo,
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

                    refreshLocalState()

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

                    refreshLocalState()

                    if (currentVideo != null) {
                        playerVideo = currentVideo
                    } else {
                        playerVideo = null
                    }
                }
            },
            onPrevious = { videoId ->
                scope.launch {
                    val previous = withContext(Dispatchers.IO) {
                        store.previousVideo(videoId)
                    }

                    if (previous != null) {
                        playerVideo = previous.copy(progressSeconds = 0.0)
                    }
                }
            },
            onExit = {
                playerVideo = null
                scope.launch {
                    refreshLocalState()
                }
            },
        )
    } else {
        HomeScreen(
            currentVideo = currentVideo,
            stats = stats,
            syncing = syncing,
            errorMessage = errorMessage,
            onContinue = {
                currentVideo?.let {
                    playerVideo = it
                }
            },
            onSync = {
                scope.launch {
                    syncArchive()
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
    syncing: Boolean,
    errorMessage: String?,
    onContinue: () -> Unit,
    onSync: () -> Unit,
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
            painter = painterResource(R.drawable.deepen_home_hero),
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
                    Text(
                        text = currentVideo.title,
                        color = Color.White,
                        fontSize = 31.sp,
                        lineHeight = 43.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

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
                                    text = "Dr. Paul Gitwaza",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "@drpaulmgitwaza",
                                    color = Color(0xFFBAC5D3),
                                    fontSize = 12.sp,
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
                        text = "Sync to check for new teachings.",
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
                        text = "Sync the teaching archive to begin.",
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
    onProgress: (String, Double, Double) -> Unit,
    onEnded: (String) -> Unit,
    onSkip: (String) -> Unit,
    onPrevious: (String) -> Unit,
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
    var lastPersistedPosition by remember(video.videoId) {
        mutableStateOf(video.progressSeconds)
    }
    var overlayVisible by remember(video.videoId) {
        mutableStateOf(true)
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
    var lastUpPressAt by remember(video.videoId) {
        mutableStateOf(0L)
    }

    LaunchedEffect(playbackState, controlPulse) {
        if (playbackState == "PLAYING") {
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

    fun persistProgress() {
        onProgress(
            video.videoId,
            playbackPosition,
            durationSeconds,
        )
        lastPersistedPosition = playbackPosition
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        key(video.videoId) {
            YouTubePlayer(
                video = video,
                onProgress = { videoId, position, duration ->
                    playbackPosition = position
                    if (duration > 0.0) {
                        durationSeconds = duration
                    }
                    if (
                        position > 0.0 &&
                        (playbackState == "LOADING" || playbackState == "READY")
                    ) {
                        playbackState = "PLAYING"
                        playbackError = null
                    }

                    if (
                        abs(position - lastPersistedPosition) >= 5.0 ||
                        (duration > 0.0 && position / duration >= 0.95)
                    ) {
                        onProgress(videoId, position, duration)
                        lastPersistedPosition = position
                    }
                },
                onPlaybackState = { state ->
                    playbackState = state
                    if (state == "PLAYING") {
                        playbackError = null
                    }

                    if (state == "PAUSED") {
                        persistProgress()
                    }
                },
                onPlaybackError = { code ->
                    playbackState = "ERROR"
                    playbackError = playerErrorMessage(code)
                },
                onControl = { action ->
                    controlPulse += 1

                    if (action == "UP") {
                        val now = SystemClock.elapsedRealtime()
                        val isDoubleUp =
                            lastUpPressAt > 0L &&
                            now - lastUpPressAt <= 500L

                        overlayVisible = false
                        skipArmed = false
                        pendingSeekSeconds = 0
                        controlFeedback = null

                        if (isDoubleUp) {
                            lastUpPressAt = 0L
                            persistProgress()
                            onPrevious(video.videoId)
                        } else {
                            lastUpPressAt = now
                        }
                    } else {
                        overlayVisible = true
                        lastUpPressAt = 0L

                        when (action) {
                            "SEEK_BACK" -> {
                                skipArmed = false
                                pendingSeekSeconds -= 10
                                controlFeedback = if (pendingSeekSeconds < 0) {
                                    "−" + abs(pendingSeekSeconds) + "s"
                                } else {
                                    "+" + pendingSeekSeconds + "s"
                                }
                            }

                            "SEEK_FORWARD" -> {
                                skipArmed = false
                                pendingSeekSeconds += 30
                                controlFeedback = if (pendingSeekSeconds < 0) {
                                    "−" + abs(pendingSeekSeconds) + "s"
                                } else {
                                    "+" + pendingSeekSeconds + "s"
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

                            "TOGGLE" -> {
                                skipArmed = false
                                pendingSeekSeconds = 0
                                controlFeedback = if (playbackState == "PLAYING") "Pause" else "Play"
                            }

                            "PLAY" -> {
                                skipArmed = false
                                pendingSeekSeconds = 0
                                controlFeedback = "Play"
                            }

                            "PAUSE" -> {
                                skipArmed = false
                                pendingSeekSeconds = 0
                                controlFeedback = "Pause"
                            }

                            "SHOW" -> {
                                skipArmed = false
                                pendingSeekSeconds = 0
                                controlFeedback = null
                            }
                        }
                    }
                },
                onEnded = { videoId ->
                    persistProgress()
                    onEnded(videoId)
                },
            )
        }

        if (overlayVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(Color.Black),
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.deepen_icon),
                    contentDescription = "Deepen",
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "DEEPEN - From the beginning",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            Image(
                painter = painterResource(R.drawable.deepen_icon),
                contentDescription = "Deepen",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .size(26.dp)
                    .clip(RoundedCornerShape(7.dp)),
                contentScale = ContentScale.Crop,
            )
        }

        if (skipArmed) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
                    .padding(horizontal = 28.dp, vertical = 18.dp),
            ) {
                Text(
                    text = "Press ↓ again to mark viewed & skip",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        controlFeedback?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
                    .padding(horizontal = 26.dp, vertical = 16.dp),
            ) {
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        playbackError?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.72f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.88f))
                    .padding(28.dp),
            ) {
                Column {
                    Text(
                        text = "Playback problem",
                        color = DeepenError,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = message,
                        color = Color.White,
                        fontSize = 17.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Press Back to return to your journey.",
                        color = DeepenMuted,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        if (overlayVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(horizontal = 24.dp, vertical = 15.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = video.title,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.70f),
                    )

                    Text(
                        text = "${formatPlaybackTime(playbackPosition)} / ${formatPlaybackTime(durationSeconds)}",
                        color = Color.White,
                        fontSize = 12.sp,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(DeepenTrack),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(DeepenBlue),
                    )
                }

                Spacer(modifier = Modifier.height(9.dp))

                Text(
                    text = "OK Play/Pause  ·  ← ×10s  ·  → ×30s  ·  ↑ Hide / ↑↑ Previous  ·  ↓ Skip  ·  Back",
                    color = DeepenMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubePlayer(
    video: VideoItem,
    onProgress: (String, Double, Double) -> Unit,
    onPlaybackState: (String) -> Unit,
    onPlaybackError: (String) -> Unit,
    onControl: (String) -> Unit,
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
                AndroidKeyEvent.KEYCODE_BUTTON_A,
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
                        "TOGGLE" -> "togglePlayback();"
                        "PLAY" -> "playVideo();"
                        "PAUSE" -> "pauseVideo();"
                        "SEEK_BACK" -> "queueSeekBy(-10);"
                        "SEEK_FORWARD" -> "queueSeekBy(30);"
                        else -> ""
                    }

                    onControl(action)
                    if (script.isNotEmpty()) {
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
        modifier = Modifier.fillMaxSize(),
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
    private val onPlaybackState: (String) -> Unit,
    private val onPlaybackError: (String) -> Unit,
    private val onEnded: (String) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onProgress(
        currentSeconds: Double,
        durationSeconds: Double,
    ) {
        mainHandler.post {
            onProgress(videoId, currentSeconds, durationSeconds)
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

                    progressTimer = setInterval(function() {
                        if (!player || typeof player.getCurrentTime !== 'function') return;

                        var current = player.getCurrentTime() || 0;
                        var duration = player.getDuration() || 0;
                        var state = player.getPlayerState();

                        if (state === YT.PlayerState.PLAYING) {
                            disableCaptions();
                            AndroidBridge.onPlaybackState('PLAYING');
                        } else if (state === YT.PlayerState.PAUSED) {
                            AndroidBridge.onPlaybackState('PAUSED');
                        } else if (state === YT.PlayerState.BUFFERING) {
                            AndroidBridge.onPlaybackState('BUFFERING');
                        }

                        AndroidBridge.onProgress(current, duration);
                    }, 2000);
                }

                function onPlayerStateChange(event) {
                    disableCaptions();

                    if (event.data === YT.PlayerState.PLAYING) {
                        var playerElement = document.getElementById('player');
                        if (playerElement) playerElement.style.opacity = '1';
                        var poster = document.getElementById('poster');
                        if (poster) poster.style.display = 'none';
                        AndroidBridge.onPlaybackState('PLAYING');
                    } else if (event.data === YT.PlayerState.PAUSED) {
                        AndroidBridge.onPlaybackState('PAUSED');
                    } else if (event.data === YT.PlayerState.BUFFERING) {
                        AndroidBridge.onPlaybackState('BUFFERING');
                    } else if (event.data === YT.PlayerState.CUED) {
                        AndroidBridge.onPlaybackState('READY');
                    }

                    if (event.data === YT.PlayerState.ENDED) {
                        AndroidBridge.onPlaybackState('ENDED');
                        if (progressTimer) {
                            clearInterval(progressTimer);
                        }

                        var current = player.getCurrentTime() || 0;
                        var duration = player.getDuration() || 0;
                        AndroidBridge.onProgress(current, duration);
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

private val DeepenBlue = Color(0xFF3399FF)
private val DeepenBackground = Color(0xFF080A0D)
private val DeepenPanel = Color(0xFF15191F)
private val DeepenTrack = Color(0xFF2A3038)
private val DeepenMuted = Color(0xFFA5AFBC)
private val DeepenError = Color(0xFFFF8A80)
