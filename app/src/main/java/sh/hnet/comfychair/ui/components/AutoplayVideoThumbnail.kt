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
 * Layer order: PlayerView (bottom) → Thumbnail (top, removed after first frame)
 * This prevents any black flash — the player renders behind the thumbnail,
 * and the thumbnail is removed only after onRenderedFirstFrame confirms
 * pixels are actually on the surface.
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
    var playerReady by remember { mutableStateOf(false) }
    var firstFrameRendered by remember { mutableStateOf(false) }

    // Watch thumbnailLoaded via snapshotFlow so we don't miss synchronous Coil cache hits
    LaunchedEffect(Unit) {
        snapshotFlow { thumbnailLoaded }
            .collect { loaded ->
                if (loaded && !playerReady) {
                    delay(200) // debounce for fast scrolling
                    val player = VideoPlayerPool.assignPlayer(context, itemKey, Uri.parse(videoUrl))
                    if (player != null) {
                        playerReady = true
                        val listener = object : Player.Listener {
                            override fun onRenderedFirstFrame() {
                                firstFrameRendered = true
                                player.removeListener(this)
                            }
                        }
                        player.addListener(listener)
                    }
                }
            }
    }

    DisposableEffect(itemKey) {
        onDispose {
            VideoPlayerPool.releasePlayer(itemKey)
        }
    }

    Box(modifier = modifier) {
        // Player surface BEHIND the thumbnail
        if (playerReady) {
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

        // Thumbnail on top — removed once first video frame is rendered
        if (!firstFrameRendered) {
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

            // Tiny spinner while buffering
            if (thumbnailLoaded && playerReady) {
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
}
