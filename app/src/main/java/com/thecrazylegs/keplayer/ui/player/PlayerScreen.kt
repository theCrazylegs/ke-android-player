package com.thecrazylegs.keplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thecrazylegs.keplayer.data.socket.SocketEvent
import java.text.SimpleDateFormat
import java.util.*

/**
 * TV-friendly button with focus highlight for D-pad navigation
 */
@Composable
private fun TvButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White,
    content: @Composable RowScope.() -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .then(
                if (isFocused) {
                    Modifier.border(3.dp, Color.Yellow, RoundedCornerShape(4.dp))
                } else {
                    Modifier
                }
            ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (isFocused) Color.Yellow else contentColor
        ),
        content = content
    )
}

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    var isMenuOpen by remember { mutableStateOf(false) }
    val rootFocusRequester = remember { FocusRequester() }

    // Request focus on the root so key events are received
    LaunchedEffect(Unit) {
        rootFocusRequester.requestFocus()
    }

    // Determine if we should show waiting screen
    // Show when: no media loaded OR pending item exists (after NEXT)
    val showWaitingScreen = uiState.mediaUrl == null || uiState.pendingItem != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            if (!isMenuOpen) {
                                isMenuOpen = true
                                true
                            } else false
                        }
                        Key.Back -> {
                            if (isMenuOpen) {
                                isMenuOpen = false
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Video Player always present (to avoid recreation issues)
        if (!uiState.showDebug) {
            VideoPlayer(
                mediaUrl = uiState.mediaUrl,
                isPlaying = uiState.isPlaying,
                token = uiState.token,
                volume = uiState.exoPlayerVolume,
                modifier = Modifier.fillMaxSize(),
                onPlaybackState = { state ->
                    viewModel.updateExoPlayerState(state)
                    // Detect playback end
                    if (state == "ENDED") {
                        viewModel.onPlaybackEnded()
                    }
                },
                onError = { error -> viewModel.updateExoPlayerError(error) },
                onVolumeChange = { volume -> viewModel.updateExoPlayerVolume(volume) },
                onPositionChange = { position -> viewModel.updatePosition(position) }
            )
        }

        // Waiting screen overlay (on top of video when not playing)
        AnimatedVisibility(
            visible = showWaitingScreen && !uiState.showDebug,
            enter = fadeIn(animationSpec = tween(500)),
            exit = fadeOut(animationSpec = tween(400))
        ) {
            WaitingScreen(
                nextItem = viewModel.getWaitingScreenItem(),
                queueRemaining = viewModel.getQueueRemainingCount(),
                serverUrl = uiState.serverUrl,
                token = uiState.token,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Debug panel (if enabled)
        if (uiState.showDebug) {
            DebugPanel(
                uiState = uiState,
                dateFormat = dateFormat,
                onClear = { viewModel.clearEvents() },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Side menu overlay
        SideMenu(
            isVisible = isMenuOpen,
            connectionState = uiState.connectionState,
            username = uiState.username,
            serverUrl = uiState.serverUrl,
            roomId = uiState.roomId,
            isDebugMode = uiState.showDebug,
            onToggleDebug = { viewModel.toggleDebug() },
            onLogout = {
                viewModel.disconnectSocket()
                onLogout()
            },
            onDismiss = { isMenuOpen = false }
        )
    }
}

@Composable
private fun SongOverlay(
    title: String,
    artist: String,
    singer: String,
    coSingers: List<String>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(24.dp),
        color = Color.Black.copy(alpha = 0.7f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = artist,
                color = Color.Gray,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Text(
                    text = "🎤 $singer",
                    color = Color.Cyan,
                    fontSize = 18.sp
                )
                if (coSingers.isNotEmpty()) {
                    Text(
                        text = " + ${coSingers.joinToString(", ")}",
                        color = Color.Cyan.copy(alpha = 0.7f),
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

private val AccentPink = Color(0xFFFD80D8)

@Composable
private fun DebugPanel(
    uiState: PlayerUiState,
    dateFormat: SimpleDateFormat,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xCC1A1A1A))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "KE Player - Debug",
                    style = MaterialTheme.typography.headlineSmall,
                    color = AccentPink
                )
                Text(
                    text = "Server: ${uiState.serverUrl}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            TvButton(
                onClick = onClear,
                contentColor = AccentPink
            ) {
                Text("Clear")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Player state info
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AccentPink.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Player State", color = AccentPink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Playing: ${uiState.isPlaying}", color = Color.White, fontSize = 12.sp)
                Text("Position: ${String.format("%.2f", uiState.position)}s", color = Color.White, fontSize = 12.sp)
                Text("QueueId: ${uiState.currentQueueId ?: "none"}", color = Color.White, fontSize = 12.sp)
                uiState.currentItem?.let {
                    Text("Song: ${it.title} - ${it.artist}", color = Color.Green, fontSize = 12.sp)
                    Text("Singer: ${it.userDisplayName}", color = Color.Yellow, fontSize = 12.sp)
                    Text("MediaId: ${it.mediaId}", color = Color.White, fontSize = 12.sp)
                }
                uiState.mediaUrl?.let {
                    Text("URL: ${it.take(60)}...", color = Color.Gray, fontSize = 10.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                val exoColor = when (uiState.exoPlayerState) {
                    "READY" -> Color.Green
                    "BUFFERING", "LOADING" -> Color.Yellow
                    "ERROR" -> Color.Red
                    else -> Color.Gray
                }
                Text("ExoPlayer: ${uiState.exoPlayerState}", color = exoColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Volume: ${(uiState.exoPlayerVolume * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
                uiState.exoPlayerError?.let {
                    Text("Error: $it", color = Color.Red, fontSize = 10.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Events count badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Events",
                color = AccentPink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = AccentPink.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "${uiState.events.size}",
                    color = AccentPink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Events list
        Card(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, AccentPink.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D0D)),
            shape = RoundedCornerShape(8.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(uiState.events) { event ->
                    EventItem(event = event, dateFormat = dateFormat)
                }
            }
        }
    }
}

@Composable
internal fun ConnectionBadge(state: String) {
    val (color, text) = when (state) {
        "connected" -> Color(0xFF4CAF50) to "Connected"
        "disconnected" -> Color(0xFF9E9E9E) to "Disconnected"
        "error" -> Color(0xFFF44336) to "Error"
        else -> Color(0xFFFF9800) to "Connecting..."
    }

    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EventItem(event: SocketEvent, dateFormat: SimpleDateFormat) {
    val actionType = event.getActionType()
    val displayType = if (actionType != null) {
        "${event.type}: $actionType"
    } else {
        event.type
    }

    val typeColor = when {
        event.type == "CONNECT" -> Color(0xFF4CAF50)
        event.type == "DISCONNECT" -> Color(0xFFF44336)
        event.type == "CONNECT_ERROR" || event.type == "ERROR" -> Color(0xFFF44336)
        event.type == "CONNECTING" -> Color(0xFFFF9800)
        actionType?.contains("PLAYER_STATUS") == true -> Color(0xFF9E9E9E)
        actionType?.contains("CMD_") == true -> Color(0xFF4CAF50)
        actionType?.contains("queue") == true -> Color(0xFFFF9800)
        event.type == "ACTION" -> Color(0xFF2196F3)
        else -> Color(0xFF9C27B0)
    }

    // Focusable for D-pad scrolling + colored left border
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusable()
            .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
    ) {
        // Colored left border indicator
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(typeColor, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayType,
                        color = typeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (event.count > 1) {
                        Surface(
                            color = typeColor.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${event.count}",
                                color = typeColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = dateFormat.format(Date(event.timestamp)),
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            val showData = event.data.isNotBlank() &&
                !(actionType?.contains("PLAYER_STATUS") == true && event.count > 1)

            if (showData) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = event.data.take(500) + if (event.data.length > 500) "..." else "",
                    color = Color(0xFFB0B0B0),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp
                )
            }
        }
    }
}
