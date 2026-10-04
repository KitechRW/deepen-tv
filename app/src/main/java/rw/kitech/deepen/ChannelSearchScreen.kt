package rw.kitech.deepen

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
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
    var voiceAlternatives by remember { mutableStateOf<List<String>>(emptyList()) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    val voiceFocusRequester = remember { FocusRequester() }
    val textFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val alternatives = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.distinct()
                ?.take(3)
                .orEmpty()

            if (alternatives.isEmpty()) {
                voiceAlternatives = emptyList()
                voiceError = "Didn't catch that. Try again or type instead."
            } else {
                voiceAlternatives = alternatives
                voiceError = null
                onQueryChange(alternatives.first())
            }
        }
    }

    fun startVoiceSearch() {
        voiceError = null

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Say a preacher, ministry or YouTube channel",
            )
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        }

        try {
            voiceLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            voiceAlternatives = emptyList()
            voiceError = "Voice search is not available on this TV. Use Type Search."
        }
    }

    fun focusTyping() {
        textFocusRequester.requestFocus()
        keyboardController?.show()
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchActionButton(
                    label = "🎙  VOICE SEARCH",
                    onClick = ::startVoiceSearch,
                    emphasized = true,
                    modifier = Modifier.focusRequester(voiceFocusRequester),
                )

                SearchActionButton(
                    label = "⌨  TYPE SEARCH",
                    onClick = ::focusTyping,
                )

                SearchActionButton(
                    label = "CLEAR",
                    onClick = {
                        voiceAlternatives = emptyList()
                        voiceError = null
                        keyboardController?.hide()
                        onClear()
                    },
                    enabled = query.isNotBlank() || results.isNotEmpty() || voiceAlternatives.isNotEmpty(),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchTextField(
                    value = query,
                    onValueChange = { value ->
                        voiceAlternatives = emptyList()
                        voiceError = null
                        onQueryChange(value)
                    },
                    onSearch = onSearch,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(textFocusRequester),
                )

                Spacer(modifier = Modifier.width(10.dp))

                SearchActionButton(
                    label = if (searching) "SEARCHING…" else "SEARCH",
                    onClick = onSearch,
                    enabled = query.isNotBlank() && !searching,
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

                    SearchActionButton(
                        label = "TYPE INSTEAD",
                        onClick = ::focusTyping,
                    )
                }
            }

            if (voiceAlternatives.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                VoiceReviewPanel(
                    query = query,
                    alternatives = voiceAlternatives,
                    onChooseAlternative = { alternative ->
                        onQueryChange(alternative)
                    },
                    onSearch = onSearch,
                    onTryAgain = ::startVoiceSearch,
                    onEdit = ::focusTyping,
                    searching = searching,
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
private fun VoiceReviewPanel(
    query: String,
    alternatives: List<String>,
    onChooseAlternative: (String) -> Unit,
    onSearch: () -> Unit,
    onTryAgain: () -> Unit,
    onEdit: () -> Unit,
    searching: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF0A1C31).copy(alpha = 0.96f),
                        Color(0xFF0A1627).copy(alpha = 0.92f),
                    ),
                ),
                shape = RoundedCornerShape(16.dp),
            )
            .border(
                width = 1.dp,
                color = SearchBlue.copy(alpha = 0.30f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            text = "VOICE RESULT",
            color = SearchBlue,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = "Heard: “$query”",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (alternatives.size > 1) {
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Other matches:",
                    color = SearchMuted,
                    fontSize = 11.sp,
                )

                alternatives
                    .filter { it != query }
                    .take(2)
                    .forEach { alternative ->
                        SearchActionButton(
                            label = alternative,
                            onClick = { onChooseAlternative(alternative) },
                        )
                    }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchActionButton(
                label = if (searching) "SEARCHING…" else "SEARCH THIS",
                onClick = onSearch,
                enabled = query.isNotBlank() && !searching,
                emphasized = true,
            )

            SearchActionButton(
                label = "TRY AGAIN",
                onClick = onTryAgain,
            )

            SearchActionButton(
                label = "EDIT",
                onClick = onEdit,
            )
        }
    }
}

@Composable
private fun SearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .height(54.dp)
            .background(
                color = Color(0xFF081321).copy(alpha = 0.94f),
                shape = shape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) {
                    Color(0xFFBDEBFF)
                } else {
                    Color.White.copy(alpha = 0.11f)
                },
                shape = shape,
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isBlank()) {
            Text(
                text = "Say or type a preacher, ministry or YouTube channel",
                color = SearchMuted,
                fontSize = 14.sp,
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 16.sp,
            ),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(
                onSearch = { onSearch() },
            ),
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
                text = "Voice search is ready",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Or choose Type Search at any time.",
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
