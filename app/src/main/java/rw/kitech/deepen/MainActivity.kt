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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyEvent\nimport androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rw.kitech.deepen.data.JourneyStats
import rw.kitech.deepen.data.VideoStore
import rw.kitech.deepen.data.YouTubeArchive
import rw.kitech.deepen.model.VideoItem
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
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
            .background(DeepenBackground)
            .padding(horizontal = 64.dp, vertical = 48.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(0.82f),
            ) {
                Text(
                    text = "DEEPEN",
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Dr. Paul Gitwaza · oldest → newest",
                    color = DeepenMuted,
                    fontSize = 18.sp,
                )

                Spacer(modifier = Modifier.height(48.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(DeepenPanel)
                        .padding(32.dp),
                ) {
                    Column {
                        Text(
                            text = if (currentVideo == null && stats.total > 0) {
                                "CAUGHT UP"
                            } else {
                                "NEXT MESSAGE"
                            },
                            color = DeepenBlue,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        when {
                            currentVideo != null -> {
                                Text(
                                    text = currentVideo.title,
                                    color = Color.White,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = publishedLabel(currentVideo.publishedAt),
                                    color = DeepenMuted,
                                    fontSize = 17.sp,
                                )

                                if (currentVideo.progressSeconds > 1.0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Resume from ${formatPlaybackTime(currentVideo.progressSeconds)}",
                                        color = DeepenMuted,
                                        fontSize = 17.sp,
                                    )
                                }
                            }

                            stats.total > 0 -> {
                                Text(
                                    text = "You have reached the latest synchronized message.",
                                    color = Color.White,
                                    fontSize = 27.sp,
                                )
                            }

                            syncing -> {
                                Text(
                                    text = "Loading the teaching archive…",
                                    color = Color.White,
                                    fontSize = 27.sp,
                                )
                            }

                            else -> {
                                Text(
                                    text = "Deepen is ready to load the teaching archive.",
                                    color = Color.White,
                                    fontSize = 27.sp,
                                )
                            }
                        }

                        errorMessage?.let { message ->
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = message,
                                color = DeepenError,
                                fontSize = 16.sp,
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (currentVideo != null) {
                                Button(onClick = onContinue) {
                                    Text(
                                        text = if (currentVideo.progressSeconds > 1.0) {
                                            "CONTINUE"
                                        } else {
                                            "PLAY"
                                        }
                                    )
                                }
                            }

                            Button(
                                onClick = onSync,
                                enabled = !syncing,
                            ) {
                                Text(text = if (syncing) "SYNCING…" else "SYNC")
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(0.82f),
            ) {
                Text(
                    text = "${stats.watched} of ${stats.total} watched",
                    color = DeepenMuted,
                    fontSize = 16.sp,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DeepenTrack),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(journeyProgress.coerceIn(0f, 1f))
                            .background(DeepenBlue),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerScreen(
    video: VideoItem,
    onProgress: (String, Double, Double) -> Unit,
    onEnded: (String) -> Unit,
    onExit: () -> Unit,
) {
    BackHandler(onBack = onExit)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        key(video.videoId) {
            YouTubePlayer(
                video = video,
                onProgress = onProgress,
                onEnded = onEnded,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(28.dp),
        ) {
            Text(
                text = video.title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "OK Play/Pause  ·  ← 10s  ·  → 30s  ·  Back Journey",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubePlayer(
    video: VideoItem,
    onProgress: (String, Double, Double) -> Unit,
    onEnded: (String) -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                val script = when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "togglePlayback();"

                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> "seekBy(-10);"
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> "seekBy(30);"
                    else -> null
                }

                if (script != null) {
                    webView?.evaluateJavascript(script, null)
                    true
                } else {
                    false
                }
            },
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
                        onEnded = onEnded,
                    ),
                    "AndroidBridge",
                )

                isFocusable = true
                isFocusableInTouchMode = true

                loadDataWithBaseURL(
                    "https://www.youtube.com",
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

                requestFocus()
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
                html, body, #player {
                    width: 100%;
                    height: 100%;
                    margin: 0;
                    padding: 0;
                    overflow: hidden;
                    background: #000;
                }
            </style>
        </head>
        <body>
            <div id="player"></div>
            <script src="https://www.youtube.com/iframe_api"></script>
            <script>
                var player;
                var progressTimer;

                function onYouTubeIframeAPIReady() {
                    player = new YT.Player('player', {
                        width: '100%',
                        height: '100%',
                        videoId: '$videoId',
                        playerVars: {
                            autoplay: 1,
                            controls: 0,
                            rel: 0,
                            playsinline: 1
                        },
                        events: {
                            onReady: onPlayerReady,
                            onStateChange: onPlayerStateChange
                        }
                    });
                }

                function onPlayerReady(event) {
                    if ($startSeconds > 0) {
                        event.target.seekTo($startSeconds, true);
                    }

                    event.target.playVideo();

                    progressTimer = setInterval(function() {
                        if (!player || typeof player.getCurrentTime !== 'function') return;

                        var current = player.getCurrentTime() || 0;
                        var duration = player.getDuration() || 0;

                        AndroidBridge.onProgress(current, duration);
                    }, 5000);
                }

                function onPlayerStateChange(event) {
                    if (event.data === YT.PlayerState.ENDED) {
                        if (progressTimer) {
                            clearInterval(progressTimer);
                        }

                        var current = player.getCurrentTime() || 0;
                        var duration = player.getDuration() || 0;
                        AndroidBridge.onProgress(current, duration);
                        AndroidBridge.onEnded();
                    }
                }

                function togglePlayback() {
                    if (!player || typeof player.getPlayerState !== 'function') return;

                    var state = player.getPlayerState();
                    if (state === YT.PlayerState.PLAYING) {
                        player.pauseVideo();
                    } else {
                        player.playVideo();
                    }
                }

                function seekBy(seconds) {
                    if (!player || typeof player.getCurrentTime !== 'function') return;

                    var current = player.getCurrentTime() || 0;
                    var duration = player.getDuration() || 0;
                    var target = Math.max(0, current + seconds);

                    if (duration > 0) {
                        target = Math.min(duration, target);
                    }

                    player.seekTo(target, true);
                }
            </script>
        </body>
        </html>
    """.trimIndent()
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

private fun publishedLabel(value: String): String {
    return value.take(10)
}

private val DeepenBlue = Color(0xFF3399FF)
private val DeepenBackground = Color(0xFF080A0D)
private val DeepenPanel = Color(0xFF15191F)
private val DeepenTrack = Color(0xFF2A3038)
private val DeepenMuted = Color(0xFFA5AFBC)
private val DeepenError = Color(0xFFFF8A80)
