package sh.hnet.comfychair.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
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
 * Uses VideoPlayerPool to manage a small pool of ExoPlayer instances.
 * Videos play muted, looping, with no controls. Falls back to static
 * thumbnail when autoplay is disabled or player is unavailable.
 *
 * @param videoUrl Transcode URL for playback (450px width, ~700KB)
 * @param thumbnailUrl Static thumbnail URL (anim=false WebP)
 * @param itemKey Unique key for pool assignment (e.g., "cover_${modelId}" or "community_${imageId}")
 * @param isVisible Whether this item should be actively playing
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
    var isPlaying by remember { mutableStateOf(false) }

    // Debounced pool assignment — wait 200ms before assigning a player
    // so fast scrolling doesn't thrash the pool
    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(200)
            VideoPlayerPool.assignPlayer(context, itemKey, Uri.parse(videoUrl))
            isPlaying = true
        } else {
            VideoPlayerPool.releasePlayer(itemKey)
            isPlaying = false
        }
    }

    // Cleanup on dispose
    DisposableEffect(itemKey) {
        onDispose {
            VideoPlayerPool.releasePlayer(itemKey)
        }
    }

    Box(modifier = modifier) {
        if (isPlaying && VideoPlayerPool.hasPlayer(itemKey)) {
            // ExoPlayer surface
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
        } else {
            // Static thumbnail fallback
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
