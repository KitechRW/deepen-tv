package rw.kitech.deepen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import rw.kitech.deepen.model.ChannelSearchResult
import rw.kitech.deepen.model.ChannelSource

@Composable
internal fun ChannelSettingsScreen(
    defaultChannel: ChannelSource,
    selectedChannel: ChannelSource,
    featuredChannels: List<ChannelSource>,
    userChannels: List<ChannelSource>,
    searchOpen: Boolean,
    searchQuery: String,
    searchResults: List<ChannelSearchResult>,
    searching: Boolean,
    searchError: String?,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectChannel: (ChannelSource) -> Unit,
    onAddSearchResult: (ChannelSearchResult) -> Unit,
    onSetDefault: (ChannelSource) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ChannelSettingsBackground),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF020711),
                            Color(0xFF071225),
                            Color(0xFF0A1730),
                        ),
                    )
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 58.dp, vertical = 38.dp),
        ) {
            SettingsHeader(
                searchOpen = searchOpen,
                onSearchToggle = onSearchToggle,
                onBack = onBack,
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (searchOpen) {
                SearchPanel(
                    query = searchQuery,
                    results = searchResults,
                    searching = searching,
                    error = searchError,
                    onQueryChange = onSearchQueryChange,
                    onSearch = onSearch,
                    onAdd = onAddSearchResult,
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            SelectedChannelPanel(
                channel = selectedChannel,
                isDefault = selectedChannel.id == defaultChannel.id,
                onSetDefault = { onSetDefault(selectedChannel) },
            )

            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = "Featured in Rwanda",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            ChannelRow(
                channels = featuredChannels,
                selectedChannelId = selectedChannel.id,
                defaultChannelId = defaultChannel.id,
                onSelect = onSelectChannel,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Your Channels",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                if (userChannels.isEmpty()) {
                    Text(
                        text = "Search above to add another YouTube channel",
                        color = ChannelSettingsMuted,
                        fontSize = 13.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (userChannels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(86.dp)
                        .background(
                            color = ChannelSettingsPanel.copy(alpha = 0.62f),
                            shape = RoundedCornerShape(14.dp),
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(14.dp),
                        ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = "No personal channels added yet.",
                        modifier = Modifier.padding(horizontal = 22.dp),
                        color = ChannelSettingsMuted,
                        fontSize = 15.sp,
                    )
                }
            } else {
                ChannelRow(
                    channels = userChannels,
                    selectedChannelId = selectedChannel.id,
                    defaultChannelId = defaultChannel.id,
                    onSelect = onSelectChannel,
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader(
    searchOpen: Boolean,
    onSearchToggle: () -> Unit,
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
                color = ChannelSettingsBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Channels",
                color = Color.White,
                fontSize = 31.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsPillButton(
                label = if (searchOpen) "CLOSE SEARCH" else "⌕  SEARCH",
                onClick = onSearchToggle,
            )
            SettingsPillButton(
                label = "←  BACK",
                onClick = onBack,
            )
        }
    }
}

@Composable
private fun SelectedChannelPanel(
    channel: ChannelSource,
    isDefault: Boolean,
    onSetDefault: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .background(
                color = Color(0xFF0A1525).copy(alpha = 0.94f),
                shape = RoundedCornerShape(18.dp),
            )
            .border(
                width = 1.dp,
                color = ChannelSettingsBlue.copy(alpha = 0.58f),
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(
                        color = Color(0xFF111D2F),
                        shape = RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_youtube_white),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            }

            Spacer(modifier = Modifier.width(18.dp))

            Column {
                Text(
                    text = "Selected channel",
                    color = ChannelSettingsMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = channel.displayName,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = channel.secondaryLabel,
                    color = ChannelSettingsMuted,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        DefaultButton(
            isDefault = isDefault,
            onClick = onSetDefault,
        )
    }
}

@Composable
private fun ChannelRow(
    channels: List<ChannelSource>,
    selectedChannelId: String,
    defaultChannelId: String,
    onSelect: (ChannelSource) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(end = 12.dp),
    ) {
        items(
            items = channels,
            key = { channel -> channel.id },
        ) { channel ->
            ChannelCard(
                channel = channel,
                selected = channel.id == selectedChannelId,
                isDefault = channel.id == defaultChannelId,
                onClick = { onSelect(channel) },
            )
        }
    }
}

@Composable
private fun ChannelCard(
    channel: ChannelSource,
    selected: Boolean,
    isDefault: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(15.dp)

    Button(
        onClick = onClick,
        modifier = Modifier
            .width(220.dp)
            .height(108.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (selected || focused) 2.dp else 1.dp,
                color = when {
                    focused -> Color(0xFFBDEBFF)
                    selected -> ChannelSettingsBlue
                    else -> Color.White.copy(alpha = 0.10f)
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
            pressedScale = 0.99f,
        ),
        colors = ButtonDefaults.colors(
            containerColor = ChannelSettingsPanel,
            contentColor = Color.White,
            focusedContainerColor = Color(0xFF142A45),
            focusedContentColor = Color.White,
            pressedContainerColor = Color(0xFF10233A),
            pressedContentColor = Color.White,
        ),
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_youtube_white),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )

                Text(
                    text = when {
                        isDefault -> "★"
                        selected -> "●"
                        else -> "○"
                    },
                    color = if (isDefault || selected) {
                        ChannelSettingsBlue
                    } else {
                        ChannelSettingsMuted
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = channel.displayName,
                color = Color.White,
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = channel.secondaryLabel,
                color = ChannelSettingsMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchPanel(
    query: String,
    results: List<ChannelSearchResult>,
    searching: Boolean,
    error: String?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onAdd: (ChannelSearchResult) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF081221).copy(alpha = 0.96f),
                shape = RoundedCornerShape(16.dp),
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(
                value = query,
                onValueChange = onQueryChange,
                onSearch = onSearch,
                modifier = Modifier.weight(1f),
            )

            Spacer(modifier = Modifier.width(12.dp))

            SettingsPillButton(
                label = if (searching) "SEARCHING…" else "SEARCH",
                onClick = onSearch,
                enabled = !searching && query.isNotBlank(),
            )
        }

        error?.let { message ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                color = Color(0xFFFF8A80),
                fontSize = 13.sp,
            )
        }

        if (results.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = results,
                    key = { result -> result.youtubeChannelId },
                ) { result ->
                    SearchResultCard(
                        result = result,
                        onAdd = { onAdd(result) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)

    Box(
        modifier = modifier
            .height(48.dp)
            .background(
                color = Color(0xFF111A27),
                shape = shape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) {
                    Color(0xFF8FD6FF)
                } else {
                    Color.White.copy(alpha = 0.16f)
                },
                shape = shape,
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isBlank()) {
            Text(
                text = "Search preacher, ministry or YouTube channel",
                color = ChannelSettingsMuted,
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
                fontSize = 15.sp,
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
private fun SearchResultCard(
    result: ChannelSearchResult,
    onAdd: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)

    Button(
        onClick = onAdd,
        modifier = Modifier
            .width(235.dp)
            .height(78.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) {
                    Color(0xFFBDEBFF)
                } else {
                    Color.White.copy(alpha = 0.10f)
                },
                shape = shape,
            ),
        shape = ButtonDefaults.shape(
            shape = shape,
            focusedShape = shape,
            pressedShape = shape,
        ),
        colors = ButtonDefaults.colors(
            containerColor = ChannelSettingsPanel,
            contentColor = Color.White,
            focusedContainerColor = Color(0xFF142A45),
            focusedContentColor = Color.White,
            pressedContainerColor = Color(0xFF10233A),
            pressedContentColor = Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = result.displayName,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = result.handle
                    ?.let { handle -> if (handle.startsWith("@")) handle else "@" + handle }
                    ?: "ADD CHANNEL",
                color = if (result.handle == null) ChannelSettingsBlue else ChannelSettingsMuted,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DefaultButton(
    isDefault: Boolean,
    onClick: () -> Unit,
) {
    SettingsPillButton(
        label = if (isDefault) "★  DEFAULT" else "☆  SET AS DEFAULT",
        onClick = onClick,
        enabled = !isDefault,
        emphasized = !isDefault,
    )
}

@Composable
private fun SettingsPillButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    emphasized: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> Color(0xFFBDEBFF)
                    emphasized -> ChannelSettingsBlue.copy(alpha = 0.72f)
                    else -> Color.White.copy(alpha = 0.16f)
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
            containerColor = if (emphasized) Color(0xFF123B66) else Color(0xFF0A1525),
            contentColor = Color.White,
            focusedContainerColor = if (emphasized) Color(0xFF1B6EB4) else Color(0xFF164F86),
            focusedContentColor = Color.White,
            pressedContainerColor = Color(0xFF0E355C),
            pressedContentColor = Color.White,
            disabledContainerColor = Color(0xFF111A25),
            disabledContentColor = Color(0xFF7F91A5),
        ),
        contentPadding = PaddingValues(
            horizontal = 18.dp,
            vertical = 10.dp,
        ),
    ) {
        Text(
            text = label,
            color = if (enabled) Color.White else Color(0xFF7F91A5),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private val ChannelSettingsBlue = Color(0xFF3399FF)
private val ChannelSettingsBackground = Color(0xFF080A0D)
private val ChannelSettingsPanel = Color(0xFF101A28)
private val ChannelSettingsMuted = Color(0xFFA5AFBC)
