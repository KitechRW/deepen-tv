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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
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
    onRemoveUserChannel: (ChannelSource) -> Unit,
    onBack: () -> Unit,
) {
    var pendingDeleteChannel by remember { mutableStateOf<ChannelSource?>(null) }

    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ChannelSettingsBackground),
    ) {
        SettingsAtmosphere()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 46.dp,
                    end = 46.dp,
                    top = 28.dp,
                    bottom = 30.dp,
                ),
        ) {
            SettingsHeader(
                searchOpen = searchOpen,
                onSearchToggle = onSearchToggle,
                onBack = onBack,
            )

            Spacer(modifier = Modifier.height(18.dp))

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

                Spacer(modifier = Modifier.height(18.dp))
            }

            SelectedChannelPanel(
                channel = selectedChannel,
                isDefault = selectedChannel.id == defaultChannel.id,
                onSetDefault = { onSetDefault(selectedChannel) },
            )

            Spacer(modifier = Modifier.height(22.dp))

            SectionTitle(
                title = "Featured in Rwanda",
                subtitle = "Choose a teaching journey",
            )

            Spacer(modifier = Modifier.height(10.dp))

            FeaturedChannelRow(
                channels = featuredChannels,
                selectedChannelId = selectedChannel.id,
                defaultChannelId = defaultChannel.id,
                onSelect = onSelectChannel,
            )

            Spacer(modifier = Modifier.height(20.dp))

            SectionTitle(
                title = "Your Channels",
                subtitle = if (userChannels.isEmpty()) {
                    "Use Search to add another YouTube channel"
                } else {
                    "Channels you added"
                },
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (userChannels.isEmpty()) {
                EmptyChannelsState()
            } else {
                PersonalChannelRow(
                    channels = userChannels,
                    selectedChannelId = selectedChannel.id,
                    defaultChannelId = defaultChannel.id,
                    onSelect = onSelectChannel,
                    onDeleteRequest = { channel ->
                        pendingDeleteChannel = channel
                    },
                )
            }
        }

        pendingDeleteChannel?.let { channel ->
            DeleteChannelDialog(
                channel = channel,
                onDismiss = {
                    pendingDeleteChannel = null
                },
                onConfirm = {
                    pendingDeleteChannel = null
                    onRemoveUserChannel(channel)
                },
            )
        }
    }
}

@Composable
private fun SettingsAtmosphere() {
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
                        Color(0xFF020711).copy(alpha = 0.88f),
                        Color(0xFF061127).copy(alpha = 0.58f),
                        Color.Transparent,
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
                        Color(0xFF020711).copy(alpha = 0.18f),
                        Color.Transparent,
                        Color(0xFF020711).copy(alpha = 0.60f),
                    ),
                ),
            ),
    )
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
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )

            Spacer(modifier = Modifier.height(1.dp))

            Text(
                text = "Channels",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
private fun SectionTitle(
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = subtitle,
            color = ChannelSettingsMuted.copy(alpha = 0.86f),
            fontSize = 12.sp,
        )
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
            .height(116.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF0A1628).copy(alpha = 0.94f),
                        Color(0xFF0C1B32).copy(alpha = 0.88f),
                        Color(0xFF0A1424).copy(alpha = 0.90f),
                    ),
                ),
                shape = RoundedCornerShape(18.dp),
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.09f),
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelAvatar(
                imageUrl = channel.thumbnailUrl,
                contentDescription = channel.sourceName,
                size = 72,
                ring = true,
            )

            Spacer(modifier = Modifier.width(18.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (isDefault) "DEFAULT CHANNEL" else "SELECTED CHANNEL",
                    color = ChannelSettingsBlue.copy(alpha = 0.90f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp,
                )

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = channel.sourceName,
                    color = Color.White,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = channel.secondaryLabel,
                    color = ChannelSettingsMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.width(20.dp))

        DefaultButton(
            isDefault = isDefault,
            onClick = onSetDefault,
        )
    }
}

@Composable
private fun FeaturedChannelRow(
    channels: List<ChannelSource>,
    selectedChannelId: String,
    defaultChannelId: String,
    onSelect: (ChannelSource) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        channels.take(5).forEach { channel ->
            FeaturedChannelCard(
                channel = channel,
                selected = channel.id == selectedChannelId,
                isDefault = channel.id == defaultChannelId,
                onClick = { onSelect(channel) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PersonalChannelRow(
    channels: List<ChannelSource>,
    selectedChannelId: String,
    defaultChannelId: String,
    onSelect: (ChannelSource) -> Unit,
    onDeleteRequest: (ChannelSource) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(end = 12.dp),
    ) {
        items(
            items = channels,
            key = { channel -> channel.id },
        ) { channel ->
            PersonalChannelCard(
                channel = channel,
                selected = channel.id == selectedChannelId,
                isDefault = channel.id == defaultChannelId,
                onClick = {
                    if (channel.id == selectedChannelId) {
                        onDeleteRequest(channel)
                    } else {
                        onSelect(channel)
                    }
                },
            )
        }
    }
}

@Composable
private fun FeaturedChannelCard(
    channel: ChannelSource,
    selected: Boolean,
    isDefault: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(15.dp)
    val temporarySelection = selected && !isDefault

    Button(
        onClick = onClick,
        modifier = modifier
            .height(122.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = when {
                    focused -> 2.dp
                    temporarySelection -> 1.5.dp
                    else -> 1.dp
                },
                color = when {
                    focused -> Color(0xFFBDEBFF)
                    temporarySelection -> ChannelSettingsBlue.copy(alpha = 0.95f)
                    else -> Color.White.copy(alpha = 0.055f)
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
            focusedScale = 1.045f,
            pressedScale = 0.985f,
        ),
        colors = ButtonDefaults.colors(
            containerColor = ChannelSettingsPanel.copy(alpha = 0.88f),
            contentColor = Color.White,
            focusedContainerColor = Color(0xFF153354),
            focusedContentColor = Color.White,
            pressedContainerColor = Color(0xFF102844),
            pressedContentColor = Color.White,
        ),
        contentPadding = PaddingValues(
            horizontal = 14.dp,
            vertical = 13.dp,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                ChannelAvatar(
                    imageUrl = channel.thumbnailUrl,
                    contentDescription = channel.sourceName,
                    size = 42,
                    ring = false,
                )

                ChannelStateIcon(
                    selected = temporarySelection,
                    isDefault = isDefault,
                )
            }

            Spacer(modifier = Modifier.height(9.dp))

            Text(
                text = channel.sourceName,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PersonalChannelCard(
    channel: ChannelSource,
    selected: Boolean,
    isDefault: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(15.dp)
    val temporarySelection = selected && !isDefault

    Button(
        onClick = onClick,
        modifier = Modifier
            .width(230.dp)
            .height(142.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = when {
                    focused -> 2.dp
                    temporarySelection -> 1.5.dp
                    else -> 1.dp
                },
                color = when {
                    focused -> Color(0xFFBDEBFF)
                    temporarySelection -> ChannelSettingsBlue.copy(alpha = 0.95f)
                    else -> Color.White.copy(alpha = 0.055f)
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
            containerColor = ChannelSettingsPanel.copy(alpha = 0.88f),
            contentColor = Color.White,
            focusedContainerColor = Color(0xFF153354),
            focusedContentColor = Color.White,
            pressedContainerColor = Color(0xFF102844),
            pressedContentColor = Color.White,
        ),
        contentPadding = PaddingValues(
            horizontal = 14.dp,
            vertical = 13.dp,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                ChannelAvatar(
                    imageUrl = channel.thumbnailUrl,
                    contentDescription = channel.sourceName,
                    size = 42,
                    ring = false,
                )

                ChannelStateIcon(
                    selected = temporarySelection,
                    isDefault = isDefault,
                )
            }

            Spacer(modifier = Modifier.height(9.dp))

            Text(
                text = channel.sourceName,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = channel.secondaryLabel,
                color = ChannelSettingsMuted.copy(alpha = 0.90f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DeleteChannelDialog(
    channel: ChannelSource,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .width(430.dp)
                .background(
                    color = Color(0xFF07111F),
                    shape = RoundedCornerShape(20.dp),
                )
                .border(
                    width = 1.dp,
                    color = ChannelSettingsBlue.copy(alpha = 0.42f),
                    shape = RoundedCornerShape(20.dp),
                )
                .padding(26.dp),
        ) {
            Text(
                text = "REMOVE CHANNEL?",
                color = ChannelSettingsBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = channel.sourceName,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "This channel and its saved Deepen journey will be removed.",
                color = ChannelSettingsMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )

            Spacer(modifier = Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsPillButton(
                    label = "CANCEL",
                    onClick = onDismiss,
                )

                Spacer(modifier = Modifier.width(10.dp))

                DangerPillButton(
                    label = "REMOVE",
                    onClick = onConfirm,
                )
            }
        }
    }
}

@Composable
private fun DangerPillButton(
    label: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)

    Button(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) {
                    Color(0xFFFFB4AB)
                } else {
                    Color(0xFFFF8A80).copy(alpha = 0.28f)
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
            containerColor = Color(0xFF2B1518),
            contentColor = Color.White,
            focusedContainerColor = Color(0xFF5A282D),
            focusedContentColor = Color.White,
            pressedContainerColor = Color(0xFF3C1B1F),
            pressedContentColor = Color.White,
        ),
        contentPadding = PaddingValues(
            horizontal = 18.dp,
            vertical = 9.dp,
        ),
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ChannelStateIcon(
    selected: Boolean,
    isDefault: Boolean,
) {
    when {
        isDefault -> {
            Text(
                text = "★",
                color = ChannelSettingsBlue,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        selected -> {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(ChannelSettingsBlue),
            )
        }
    }
}

@Composable
private fun EmptyChannelsState() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(
                color = Color(0xFF0B1422).copy(alpha = 0.58f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "No personal channels yet",
            color = Color.White.copy(alpha = 0.76f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = "•",
            color = ChannelSettingsBlue,
            fontSize = 12.sp,
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = "Search for a preacher, ministry or YouTube channel to add one.",
            color = ChannelSettingsMuted,
            fontSize = 12.sp,
        )
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
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF081422).copy(alpha = 0.94f),
                        Color(0xFF0B1A2D).copy(alpha = 0.92f),
                    ),
                ),
                shape = RoundedCornerShape(15.dp),
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.07f),
                shape = RoundedCornerShape(15.dp),
            )
            .padding(14.dp),
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

            Spacer(modifier = Modifier.width(10.dp))

            SettingsPillButton(
                label = if (searching) "SEARCHING…" else "SEARCH",
                onClick = onSearch,
                enabled = !searching && query.isNotBlank(),
                emphasized = true,
            )
        }

        error?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = Color(0xFFFF9A91),
                fontSize = 12.sp,
            )
        }

        if (results.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
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
            .height(44.dp)
            .background(
                color = Color(0xFF0B1421).copy(alpha = 0.92f),
                shape = shape,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) {
                    Color(0xFF8FD6FF)
                } else {
                    Color.White.copy(alpha = 0.10f)
                },
                shape = shape,
            )
            .padding(horizontal = 17.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isBlank()) {
            Text(
                text = "Search preacher, ministry or YouTube channel",
                color = ChannelSettingsMuted,
                fontSize = 13.sp,
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
                fontSize = 14.sp,
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
    val shape = RoundedCornerShape(13.dp)

    Button(
        onClick = onAdd,
        modifier = Modifier
            .width(225.dp)
            .height(72.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) {
                    Color(0xFFBDEBFF)
                } else {
                    Color.White.copy(alpha = 0.06f)
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
            focusedScale = 1.035f,
            pressedScale = 0.985f,
        ),
        colors = ButtonDefaults.colors(
            containerColor = ChannelSettingsPanel.copy(alpha = 0.90f),
            contentColor = Color.White,
            focusedContainerColor = Color(0xFF153354),
            focusedContentColor = Color.White,
            pressedContainerColor = Color(0xFF102844),
            pressedContentColor = Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelAvatar(
                imageUrl = result.thumbnailUrl,
                contentDescription = result.displayName,
                size = 36,
                ring = false,
            )

            Spacer(modifier = Modifier.width(9.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = result.displayName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = result.handle
                        ?.let { handle -> if (handle.startsWith("@")) handle else "@$handle" }
                        ?: "ADD CHANNEL",
                    color = if (result.handle == null) {
                        ChannelSettingsBlue
                    } else {
                        ChannelSettingsMuted
                    },
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ChannelAvatar(
    imageUrl: String?,
    contentDescription: String?,
    size: Int,
    ring: Boolean,
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(0xFF111D2F))
            .then(
                if (ring) {
                    Modifier.border(
                        width = 2.dp,
                        color = ChannelSettingsBlue.copy(alpha = 0.34f),
                        shape = CircleShape,
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl.isNullOrBlank()) {
            Image(
                painter = painterResource(R.drawable.ic_youtube_white),
                contentDescription = contentDescription,
                modifier = Modifier.size((size * 0.50f).dp),
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
                    emphasized -> ChannelSettingsBlue.copy(alpha = 0.35f)
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
                Color(0xFF103357).copy(alpha = 0.92f)
            } else {
                Color(0xFF081321).copy(alpha = 0.80f)
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
        contentPadding = PaddingValues(
            horizontal = 17.dp,
            vertical = 9.dp,
        ),
    ) {
        Text(
            text = label,
            color = if (enabled) Color.White else Color(0xFF8092A4),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private val ChannelSettingsBlue = Color(0xFF3399FF)
private val ChannelSettingsBackground = Color(0xFF020711)
private val ChannelSettingsPanel = Color(0xFF0D1828)
private val ChannelSettingsMuted = Color(0xFFA8B4C2)
