package com.thecrazylegs.keplayer.ui.player

import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val TAG = "VideoPlayer"

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    mediaUrl: String?,
    isPlaying: Boolean,
    token: String?,
    modifier: Modifier = Modifier,
    onPlaybackState: (String) -> Unit = {},
    onError: (String) -> Unit = {},
    onVolumeChange: (Float) -> Unit = {},
    onPositionChange: (Double) -> Unit = {}
) {
    val context = LocalContext.current

    // Create ExoPlayer with cookie header for auth - recreate if token changes
    val exoPlayer = remember(token) {
        Log.d(TAG, "Creating ExoPlayer with token: ${token?.take(20)}...")

        val dataSourceFactory = DefaultHttpDataSource.Factory().apply {
            if (token != null) {
                setDefaultRequestProperties(mapOf("Cookie" to "keToken=$token"))
                Log.d(TAG, "Cookie header set")
            } else {
                Log.w(TAG, "No token available for auth!")
            }
        }

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                playWhenReady = false
                repeatMode = Player.REPEAT_MODE_OFF
                volume = 1.0f  // Ensure volume is at max
                Log.d(TAG, "ExoPlayer created, volume: $volume, deviceVolume: $deviceVolume")
            }
    }

    // Add listeners in separate effect to update UI
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val errorMsg = "Error ${error.errorCode}: ${error.message}"
                Log.e(TAG, errorMsg, error)
                onError(errorMsg)
                onPlaybackState("ERROR")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val state = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                Log.d(TAG, "Playback state: $state, volume: ${exoPlayer.volume}, deviceVolume: ${exoPlayer.deviceVolume}")
                onPlaybackState(state)
                onVolumeChange(exoPlayer.volume)
            }

            override fun onVolumeChanged(volume: Float) {
                Log.d(TAG, "Volume changed: $volume")
                onVolumeChange(volume)
            }
        }
        exoPlayer.addListener(listener)
        // Report initial volume
        onVolumeChange(exoPlayer.volume)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Track playback position periodically
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            if (exoPlayer.playbackState == Player.STATE_READY && exoPlayer.isPlaying) {
                val positionSeconds = exoPlayer.currentPosition / 1000.0
                onPositionChange(positionSeconds)
            }
            delay(250) // Update 4 times per second for smooth progress
        }
    }

    // Handle media URL changes
    LaunchedEffect(mediaUrl, token) {
        if (mediaUrl != null && token != null) {
            Log.d(TAG, "Loading media: $mediaUrl")
            onPlaybackState("LOADING")
            val mediaItem = MediaItem.fromUri(mediaUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        } else {
            if (mediaUrl == null) Log.d(TAG, "No media URL")
            if (token == null) Log.w(TAG, "No token for media request")
            exoPlayer.clearMediaItems()
            onPlaybackState("IDLE")
        }
    }

    // Handle play/pause
    LaunchedEffect(isPlaying) {
        Log.d(TAG, "Play state changed: $isPlaying, player state: ${exoPlayer.playbackState}")
        exoPlayer.playWhenReady = isPlaying
    }

    // Cleanup
    DisposableEffect(token) {
        onDispose {
            Log.d(TAG, "Releasing ExoPlayer")
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // PlayerView always present - use update to ensure player attachment
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false // No controls, KE web controls the playback
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                // Always ensure player is attached
                if (playerView.player !== exoPlayer) {
                    Log.d(TAG, "Attaching ExoPlayer to PlayerView")
                    playerView.player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
