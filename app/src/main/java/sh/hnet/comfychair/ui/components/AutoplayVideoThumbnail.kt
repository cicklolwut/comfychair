package sh.hnet.comfychair.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.delay

/**
 * Video thumbnail that auto-plays when visible in a grid.
 *
 * Layers: thumbnail (always) → player surface (on top, hidden until playing)
 * Shows a small spinner on the thumbnail while buffering.
 * Player surface only becomes visible once ExoPlayer reaches STATE_READY.
 */
@OptIn(UnstableApi::class)
@Composable
fun AutoplayVideoThumbnail(
    videoUrl: String,
    thumbnailUrl: String,
    itemKey: String,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var playerAssigned by remember { mutableStateOf(false) }
    var isRendering by remember { mutableStateOf(false) }

    // Debounced pool assignment — wait 200ms before assigning a player
    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(200)
            val player = VideoPlayerPool.assignPlayer(context, itemKey, Uri.parse(videoUrl))
            if (player != null) {
                playerAssigned = true
                // Listen for STATE_READY to know when first frame is available
                isRendering = player.playbackState == Player.STATE_READY
                if (!isRendering) {
                    val listener = object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY) {
                                isRendering = true
                                player.removeListener(this)
                            }
                        }
                    }
                    player.addListener(listener)
                }
            }
        } else {
            VideoPlayerPool.releasePlayer(itemKey)
            playerAssigned = false
            isRendering = false
        }
    }

    DisposableEffect(itemKey) {
        onDispose {
            VideoPlayerPool.releasePlayer(itemKey)
            playerAssigned = false
            isRendering = false
        }
    }

    Box(modifier = modifier) {
        // Thumbnail is ALWAYS rendered as the base layer
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Player surface layered on top — only visible once rendering
        if (playerAssigned && isRendering) {
            val player = VideoPlayerPool.getPlayer(itemKey)
            if (player != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        }
                    },
                    update = { view ->
                        view.player = player
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Small spinner while buffering (player assigned but not yet rendering)
        if (playerAssigned && !isRendering) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.Center),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}
