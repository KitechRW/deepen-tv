package rw.kitech.deepen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import rw.kitech.deepen.model.ChannelSearchResult
import java.util.Locale

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ChannelSearchScreen(
    query: String,
    results: List<ChannelSearchResult>,
    searching: Boolean,
    error: String?,
    existingChannelIds: Set<String>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onChooseResult: (ChannelSearchResult) -> Unit,
    onBack: () -> Unit,
) {
    var inputText by remember { mutableStateOf(query) }
    var voiceAlternatives by remember { mutableStateOf<List<String>>(emptyList()) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var isListening by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val voiceFocusRequester = remember { FocusRequester() }
    val textFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val recognitionIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault().toLanguageTag(),
            )
        }
    }

    val recognitionAvailable = remember(context) {
        SpeechRecognizer.isRecognitionAvailable(context)
    }

    val speechRecognizer = remember(context, recognitionAvailable) {
        if (recognitionAvailable) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    fun renderSpeechText(text: String) {
        val normalized = text.trim()
        if (normalized.isBlank()) return

        inputText = normalized
        onQueryChange(normalized)
    }

    DisposableEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    voiceError = null
                }

                override fun onBeginningOfSpeech() {
                    isListening = true
                }

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    isListening = false

                    voiceError = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "Didn't catch that. Press the mic and try again."

                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "Microphone permission is required for voice search."

                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                            "Voice search is busy. Try again."

                        else ->
                            "Voice search stopped. Press the mic to try again."
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false

                    val alternatives = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        ?.distinct()
                        ?.take(3)
                        .orEmpty()

                    voiceAlternatives = alternatives
                    if (alternatives.isNotEmpty()) {
                        voiceError = null
                        renderSpeechText(alternatives.first())
                    } else {
                        voiceError = "Didn't catch that. Press the mic and try again."
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()

                    if (partial.isNotBlank()) {
                        renderSpeechText(partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )

        onDispose {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        }
    }

    val beginInlineRecognition: () -> Unit = {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            isListening = false
            voiceError = "Voice recognition is not available on this TV."
        } else {
            voiceAlternatives = emptyList()
            voiceError = null
            keyboardController?.hide()
            isListening = true

            runCatching {
                recognizer.startListening(recognitionIntent)
            }.onFailure {
                isListening = false
                voiceError = "Voice search could not start. Try again."
            }
        }
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            beginInlineRecognition()
        } else {
            isListening = false
            voiceError = "Microphone permission is required for voice search."
        }
    }

    fun startVoiceSearch() {
        if (isListening) {
            speechRecognizer?.stopListening()
            return
        }

        if (!recognitionAvailable) {
            voiceError = "Voice recognition is not available on this TV."
            return
        }

        val microphoneGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (microphoneGranted) {
            beginInlineRecognition()
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(query) {
        if (query != inputText) {
            inputText = query
        }
    }

    LaunchedEffect(Unit) {
        voiceFocusRequester.requestFocus()
    }

    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SearchBackground),
    ) {
        SearchAtmosphere()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 46.dp,
                    end = 46.dp,
                    top = 28.dp,
                    bottom = 30.dp,
                ),
        ) {
            SearchHeader(onBack = onBack)

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "Find a YouTube teaching channel",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = "Voice is fastest. If it hears you incorrectly, correct it immediately or switch to typing.",
                color = SearchMuted,
                fontSize = 13.sp,
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchTextField(
                    value = inputText,
                    onValueChange = { value ->
                        inputText = value
                        voiceAlternatives = emptyList()
                        voiceError = null
                        onQueryChange(value)
                    },
                    onVoiceClick = ::startVoiceSearch,
                    onClear = {
                        inputText = ""
                        voiceAlternatives = emptyList()
                        voiceError = null
                        keyboardController?.hide()
                        onClear()
                    },
                    onSearch = onSearch,
                    listening = isListening,
                    voiceFocusRequester = voiceFocusRequester,
                    textFocusRequester = textFocusRequester,
                    modifier = Modifier.weight(1f),
                )

                Spacer(modifier = Modifier.width(10.dp))

                SearchActionButton(
                    label = if (searching) "SEARCHING…" else "SEARCH",
                    onClick = onSearch,
                    enabled = inputText.isNotBlank() && !searching,
                    emphasized = true,
                )
            }

            voiceError?.let { message ->
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = message,
                        color = Color(0xFFFFB4AB),
                        fontSize = 12.sp,
                    )

                    SearchActionButton(
                        label = "TRY AGAIN",
                        onClick = ::startVoiceSearch,
                    )
                }
            }

            if (voiceAlternatives.size > 1 && !isListening) {
                Spacer(modifier = Modifier.height(8.dp))

                VoiceAlternativesRow(
                    currentText = inputText,
                    alternatives = voiceAlternatives,
                    onChoose = { alternative ->
                        inputText = alternative
                        onQueryChange(alternative)
                    },
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "CHANNEL RESULTS",
                    color = SearchBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                )

                when {
                    searching -> Text(
                        text = "Searching YouTube…",
                        color = SearchMuted,
                        fontSize = 12.sp,
                    )

                    results.isNotEmpty() -> Text(
                        text = "${results.size} found",
                        color = SearchMuted,
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            error?.let { message ->
                Text(
                    text = message,
                    color = Color(0xFFFF9A91),
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (results.isEmpty() && !searching && error == null) {
                EmptySearchState()
            } else if (results.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(end = 18.dp),
                ) {
                    items(
                        items = results,
                        key = { result -> result.youtubeChannelId },
                    ) { result ->
                        SearchChannelCard(
                            result = result,
                            alreadyAdded = result.youtubeChannelId in existingChannelIds,
                            onClick = { onChooseResult(result) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchAtmosphere() {
    Image(
        painter = painterResource(R.drawable.deepen_channels_background),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.FillBounds,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF020711).copy(alpha = 0.94f),
                        Color(0xFF061127).copy(alpha = 0.76f),
                        Color(0xFF061127).copy(alpha = 0.36f),
                    ),
                ),
            ),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF020711).copy(alpha = 0.24f),
                        Color.Transparent,
                        Color(0xFF020711).copy(alpha = 0.70f),
                    ),
                ),
            ),
    )
}

@Composable
private fun SearchHeader(
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "SETTINGS",
                color = SearchBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )

            Spacer(modifier = Modifier.height(1.dp))

            Text(
                text = "Search Channels",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        SearchActionButton(
            label = "←  CHANNELS",
            onClick = onBack,
        )
    }
}

@Composable
private fun VoiceAlternativesRow(
    currentText: String,
    alternatives: List<String>,
    onChoose: (String) -> Unit,
) {
    val otherAlternatives = alternatives
        .filter { it != currentText }
        .take(2)

    if (otherAlternatives.isEmpty()) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Other possibilities",
            color = SearchMuted,
            fontSize = 11.sp,
        )

        otherAlternatives.forEach { alternative ->
            SearchActionButton(
                label = alternative,
                onClick = { onChoose(alternative) },
            )
        }
    }
}

@Composable
private fun SearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onVoiceClick: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    listening: Boolean,
    voiceFocusRequester: FocusRequester,
    textFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var textFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .height(58.dp)
            .background(
                color = Color(0xFF081321).copy(alpha = 0.96f),
                shape = shape,
            )
            .border(
                width = if (textFocused) 2.dp else 1.dp,
                color = if (textFocused) {
                    Color(0xFFBDEBFF)
                } else {
                    Color.White.copy(alpha = 0.12f)
                },
                shape = shape,
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchInputIconButton(
            drawableRes = R.drawable.ic_mic_search,
            contentDescription = if (listening) "Finish voice search" else "Voice search",
            onClick = onVoiceClick,
            active = listening,
            modifier = Modifier.focusRequester(voiceFocusRequester),
        )

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isBlank()) {
                Text(
                    text = "Search preacher, ministry or YouTube channel",
                    color = SearchMuted,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(textFocusRequester)
                    .onFocusChanged { textFocused = it.isFocused },
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { onSearch() },
                ),
            )
        }

        if (listening) {
            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "LISTENING…",
                color = SearchBlue,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
        }

        if (value.isNotBlank()) {
            Spacer(modifier = Modifier.width(8.dp))

            SearchClearButton(
                onClick = onClear,
            )
        }
    }
}

@Composable
private fun SearchInputIconButton(
    drawableRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = CircleShape

    Button(
        onClick = onClick,
        modifier = modifier
            .size(42.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> Color(0xFFBDEBFF)
                    active -> SearchBlue
                    else -> SearchBlue.copy(alpha = 0.28f)
                },
                shape = shape,
            ),
        shape = ButtonDefaults.shape(
            shape = shape,
            focusedShape = shape,
            pressedShape = shape,
        ),
        scale = ButtonDefaults.scale(
            scale = 1f,
            focusedScale = 1.08f,
            pressedScale = 0.96f,
        ),
        colors = ButtonDefaults.colors(
            containerColor = if (active) {
                Color(0xFF1B6EB4)
            } else {
                Color(0xFF103357).copy(alpha = 0.90f)
            },
            contentColor = Color.White,
            focusedContainerColor = Color(0xFF1B6EB4),
            focusedContentColor = Color.White,
            pressedContainerColor = Color(0xFF0E355C),
            pressedContentColor = Color.White,
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Image(
            painter = painterResource(drawableRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun SearchClearButton(
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = CircleShape

    Button(
        onClick = onClick,
        modifier = Modifier
            .size(38.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) {
                    Color(0xFFBDEBFF)
                } else {
                    Color.White.copy(alpha = 0.08f)
                },
                shape = shape,
            ),
        shape = ButtonDefaults.shape(
            shape = shape,
            focusedShape = shape,
            pressedShape = shape,
        ),
        scale = ButtonDefaults.scale(
            scale = 1f,
            focusedScale = 1.08f,
            pressedScale = 0.96f,
        ),
        colors = ButtonDefaults.colors(
            containerColor = Color(0xFF101A28),
            contentColor = SearchMuted,
            focusedContainerColor = Color(0xFF17466F),
            focusedContentColor = Color.White,
            pressedContainerColor = Color(0xFF0E355C),
            pressedContentColor = Color.White,
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            text = "×",
            color = if (focused) Color.White else SearchMuted,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SearchChannelCard(
    result: ChannelSearchResult,
    alreadyAdded: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(15.dp)

    Button(
        onClick = onClick,
        modifier = Modifier
            .width(250.dp)
            .height(112.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) {
                    Color(0xFFBDEBFF)
                } else {
                    Color.White.copy(alpha = 0.07f)
                },
                shape = shape,
            ),
        shape = ButtonDefaults.shape(
            shape = shape,
            focusedShape = shape,
            pressedShape = shape,
        ),
        scale = ButtonDefaults.scale(
            scale = 1.0f,
            focusedScale = 1.04f,
            pressedScale = 0.985f,
        ),
        colors = ButtonDefaults.colors(
            containerColor = SearchPanel.copy(alpha = 0.92f),
            contentColor = Color.White,
            focusedContainerColor = Color(0xFF153354),
            focusedContentColor = Color.White,
            pressedContainerColor = Color(0xFF102844),
            pressedContentColor = Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchChannelAvatar(
                imageUrl = result.thumbnailUrl,
                contentDescription = result.displayName,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = result.displayName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = result.handle
                        ?.let { handle -> if (handle.startsWith("@")) handle else "@$handle" }
                        ?: "YouTube channel",
                    color = SearchMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = if (alreadyAdded) "ADDED · SELECT" else "ADD + SELECT",
                    color = SearchBlue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun SearchChannelAvatar(
    imageUrl: String?,
    contentDescription: String?,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(0xFF111D2F)),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl.isNullOrBlank()) {
            Image(
                painter = painterResource(R.drawable.ic_youtube_white),
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp),
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
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun EmptySearchState() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .background(
                color = SearchPanel.copy(alpha = 0.66f),
                shape = RoundedCornerShape(15.dp),
            )
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "🎙",
            fontSize = 21.sp,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = "Use the microphone in the search field",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Speak naturally. Your words appear in the field while you are talking, then you can correct or search.",
                color = SearchMuted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun SearchActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> Color(0xFFBDEBFF)
                    emphasized -> SearchBlue.copy(alpha = 0.38f)
                    else -> Color.White.copy(alpha = 0.11f)
                },
                shape = shape,
            ),
        shape = ButtonDefaults.shape(
            shape = shape,
            focusedShape = shape,
            pressedShape = shape,
        ),
        scale = ButtonDefaults.scale(
            scale = 1.0f,
            focusedScale = 1.05f,
            pressedScale = 0.98f,
        ),
        colors = ButtonDefaults.colors(
            containerColor = if (emphasized) {
                Color(0xFF103C67).copy(alpha = 0.94f)
            } else {
                Color(0xFF081321).copy(alpha = 0.88f)
            },
            contentColor = Color.White,
            focusedContainerColor = if (emphasized) {
                Color(0xFF1B6EB4)
            } else {
                Color(0xFF164F86)
            },
            focusedContentColor = Color.White,
            pressedContainerColor = Color(0xFF0E355C),
            pressedContentColor = Color.White,
            disabledContainerColor = Color(0xFF0B1320).copy(alpha = 0.66f),
            disabledContentColor = Color(0xFF8092A4),
        ),
        contentPadding = PaddingValues(horizontal = 17.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            color = if (enabled) Color.White else Color(0xFF8092A4),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val SearchBlue = Color(0xFF3399FF)
private val SearchBackground = Color(0xFF020711)
private val SearchPanel = Color(0xFF0D1828)
private val SearchMuted = Color(0xFFA8B4C2)
