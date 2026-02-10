package com.thecrazylegs.keplayer.ui.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.thecrazylegs.keplayer.R
import com.thecrazylegs.keplayer.data.model.QueueItem
import java.util.Locale

/**
 * Waiting screen displayed between songs or when queue is empty.
 * Matches the Kodi addon's waiting_screen design.
 */
@Composable
fun WaitingScreen(
    nextItem: QueueItem?,
    queueRemaining: Int,
    serverUrl: String?,
    token: String?,
    modifier: Modifier = Modifier
) {
    val isQueueEmpty = nextItem == null
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        // Background image (waiting_screen.png)
        Image(
            painter = painterResource(id = R.drawable.waiting_screen),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Full-width bottom banner (like Kodi addon)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            if (isQueueEmpty) {
                // Empty queue mode: centered message in banner
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "WAITING FOR A SINGER...",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Normal mode: show next singer info
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 30.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar (left side) with cookie auth - 110dp like Kodi
                    val avatarUrl = if (serverUrl != null) {
                        buildAvatarUrl(serverUrl, nextItem.userId, token)
                    } else null

                    val imageRequest = if (avatarUrl != null && token != null) {
                        ImageRequest.Builder(context)
                            .data(avatarUrl)
                            .addHeader("Cookie", "keToken=$token")
                            .crossfade(true)
                            .build()
                    } else {
                        avatarUrl
                    }

                    AsyncImage(
                        model = imageRequest,
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(Color.Gray.copy(alpha = 0.3f))
                    )

                    Spacer(modifier = Modifier.width(24.dp))

                    // Singer info (center)
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        // "Up next:" label (gray, smaller)
                        Text(
                            text = "Up next:",
                            color = Color(0xFFAAAAAA),
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Singer name (UPPERCASE - white, bold)
                        val singerDisplay = buildString {
                            append(nextItem.userDisplayName.uppercase(Locale.getDefault()))
                            if (nextItem.coSingers.isNotEmpty()) {
                                append(" + ")
                                append(nextItem.coSingers.joinToString(", "))
                            }
                        }
                        Text(
                            text = singerDisplay,
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Title - ARTIST (purple #FD80D8)
                        val titleLine = buildString {
                            if (nextItem.title.isNotBlank()) {
                                append(nextItem.title.replaceFirstChar {
                                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                                })
                            }
                            if (nextItem.artist.isNotBlank()) {
                                if (isNotBlank()) append(" - ")
                                append(nextItem.artist.uppercase(Locale.getDefault()))
                            }
                        }
                        Text(
                            text = titleLine,
                            color = Color(0xFFFD80D8),
                            fontSize = 20.sp
                        )
                    }

                    // Queue counter (right side)
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Text(
                            text = queueRemaining.toString(),
                            color = Color(0xFFFD80D8),
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (queueRemaining > 0) "waiting" else "",
                            color = Color(0xFFE0E0E0),
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Build the avatar URL for a user.
 * Uses the standard /api/user/{userId}/image endpoint with cookie auth.
 */
private fun buildAvatarUrl(serverUrl: String, userId: Int, token: String?): String {
    var baseUrl = serverUrl.trim()
    if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
        baseUrl = "http://$baseUrl"
    }
    if (!baseUrl.endsWith("/")) {
        baseUrl = "$baseUrl/"
    }
    // Use the standard user image endpoint
    return "${baseUrl}api/user/$userId/image"
}
