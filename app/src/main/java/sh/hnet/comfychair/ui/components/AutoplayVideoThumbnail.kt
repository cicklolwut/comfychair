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
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.delay

/**
 * Video thumbnail that auto-plays in a lazy grid.
 *
 * Lifecycle:
 * 1. Thumbnail loads via Coil (always visible as base layer)
 * 2. Once thumbnail is loaded (Success), starts 200ms debounce
 * 3. After debounce, assigns a player from VideoPlayerPool
 * 4. Player surface stays hidden until ExoPlayer hits STATE_READY
 * 5. Once ready, player surface appears over thumbnail (seamless transition)
 * 6. On dispose (scroll off screen), releases player back to pool
 *
 * A small spinner shows between steps 3-4 (player assigned, buffering).
 */
@OptIn(UnstableApi::class)
@Composable
fun AutoplayVideoThumbnail(
    videoUrl: String,
    thumbnailUrl: String,
    itemKey: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var thumbnailLoaded by remember { mutableStateOf(false) }
    var playerAssigned by remember { mutableStateOf(false) }
    var isRendering by remember { mutableStateOf(false) }

    // Once thumbnail is loaded, debounce then assign player
    LaunchedEffect(thumbnailLoaded) {
        if (thumbnailLoaded) {
            delay(200) // don't assign during fast scroll
            val player = VideoPlayerPool.assignPlayer(context, itemKey, Uri.parse(videoUrl))
            if (player != null) {
                playerAssigned = true
                // Check if already ready (cached video)
                if (player.playbackState == Player.STATE_READY) {
                    isRendering = true
                } else {
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
        }
    }

    // Cleanup on dispose
    DisposableEffect(itemKey) {
        onDispose {
            VideoPlayerPool.releasePlayer(itemKey)
        }
    }

    Box(modifier = modifier) {
        // Thumbnail — always the base layer
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onState = { state ->
                if (state is AsyncImagePainter.State.Success) {
                    thumbnailLoaded = true
                }
            }
        )

        // Player surface — only visible once first frame is decoded
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

        // Tiny spinner while buffering
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
