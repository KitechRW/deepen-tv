package rw.kitech.deepen

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import rw.kitech.deepen.model.ChannelSearchResult

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
    var nativeInput by remember { mutableStateOf<EditText?>(null) }
    val context = LocalContext.current

    fun focusNativeSearchInput() {
        nativeInput?.let { editText ->
            editText.requestFocus()
            editText.setSelection(editText.text.length)

            val inputMethodManager = context.getSystemService(
                InputMethodManager::class.java,
            )
            inputMethodManager?.showSoftInput(
                editText,
                InputMethodManager.SHOW_IMPLICIT,
            )
        }
    }

    fun submitSearch() {
        onSearch()
    }

    LaunchedEffect(query) {
        if (query != inputText) {
            inputText = query
        }
    }

    LaunchedEffect(nativeInput) {
        if (nativeInput != null) {
            focusNativeSearchInput()
        }
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
                text = "Find a YouTube channel",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = "Use the MiBox remote microphone or type with the TV keyboard. Speech appears in this field.",
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
                        onQueryChange(value)
                    },
                    onVoiceClick = ::focusNativeSearchInput,
                    onClear = {
                        inputText = ""
                        onClear()
                    },
                    onSearch = ::submitSearch,
                    onNativeInputReady = { editText ->
                        nativeInput = editText
                    },
                    modifier = Modifier.weight(1f),
                )

                Spacer(modifier = Modifier.width(10.dp))

                SearchActionButton(
                    label = if (searching) "SEARCHING…" else "SEARCH",
                    onClick = ::submitSearch,
                    enabled = inputText.isNotBlank() && !searching,
                    emphasized = true,
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
private fun SearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onVoiceClick: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    onNativeInputReady: (EditText) -> Unit,
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
            contentDescription = "Use MiBox remote microphone",
            onClick = onVoiceClick,
        )

        Spacer(modifier = Modifier.width(8.dp))

        AndroidView(
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            factory = { viewContext ->
                EditText(viewContext).apply {
                    setSingleLine(true)
                    hint = "Search name, ministry or YouTube channel"
                    setTextColor(android.graphics.Color.WHITE)
                    setHintTextColor(android.graphics.Color.rgb(168, 180, 194))
                    textSize = 16f
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    inputType = InputType.TYPE_CLASS_TEXT
                    imeOptions = EditorInfo.IME_ACTION_SEARCH
                    setPadding(0, 0, 0, 0)
                    isFocusable = true
                    isFocusableInTouchMode = true
                    showSoftInputOnFocus = true

                    setOnFocusChangeListener { _, hasFocus ->
                        textFocused = hasFocus
                    }

                    setOnEditorActionListener { _, actionId, event ->
                        val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                            event.action == KeyEvent.ACTION_UP

                        if (actionId == EditorInfo.IME_ACTION_SEARCH || enterPressed) {
                            onSearch()
                            true
                        } else {
                            false
                        }
                    }

                    addTextChangedListener(
                        object : TextWatcher {
                            override fun beforeTextChanged(
                                text: CharSequence?,
                                start: Int,
                                count: Int,
                                after: Int,
                            ) = Unit

                            override fun onTextChanged(
                                text: CharSequence?,
                                start: Int,
                                before: Int,
                                count: Int,
                            ) = Unit

                            override fun afterTextChanged(editable: Editable?) {
                                onValueChange(editable?.toString().orEmpty())
                            }
                        },
                    )

                    setText(value)
                    setSelection(text.length)
                    onNativeInputReady(this)
                }
            },
            update = { editText ->
                if (editText.text.toString() != value) {
                    editText.setText(value)
                    editText.setSelection(editText.text.length)
                }
                onNativeInputReady(editText)
            },
        )

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
                color = if (focused) {
                    Color(0xFFBDEBFF)
                } else {
                    SearchBlue.copy(alpha = 0.28f)
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
            containerColor = Color(0xFF103357).copy(alpha = 0.90f),
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
                text = "Search with your MiBox remote microphone",
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
