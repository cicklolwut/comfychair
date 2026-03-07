package sh.hnet.comfychair.ui.components

import android.net.Uri
import android.util.Log
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

private const val TAG = "AutoplayVideo"

/**
 * Video thumbnail that auto-plays in a lazy grid.
 *
 * Layer order: PlayerView (bottom) → Thumbnail (top)
 * Thumbnail is removed only after onRenderedFirstFrame AND the player
 * is still assigned to this item (guards against pool reclamation).
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

    // Check if our player was reclaimed by another card
    // If so, reset state so thumbnail reappears
    val stillOwnsPlayer = playerReady && VideoPlayerPool.hasPlayer(itemKey)
    val showThumbnail = !firstFrameRendered || !stillOwnsPlayer

    // Reset state if player was reclaimed
    LaunchedEffect(stillOwnsPlayer) {
        if (!stillOwnsPlayer && playerReady) {
            Log.d(TAG, "[$itemKey] player reclaimed, resetting")
            playerReady = false
            firstFrameRendered = false
        }
    }

    // Assign player after thumbnail loads + debounce
    LaunchedEffect(Unit) {
        snapshotFlow { thumbnailLoaded }
            .collect { loaded ->
                if (loaded && !playerReady) {
                    delay(200)
                    val player = VideoPlayerPool.assignPlayer(context, itemKey, Uri.parse(videoUrl))
                    if (player != null) {
                        playerReady = true
                        Log.d(TAG, "[$itemKey] player assigned, state=${player.playbackState}")
                        val listener = object : Player.Listener {
                            override fun onRenderedFirstFrame() {
                                Log.d(TAG, "[$itemKey] first frame rendered")
                                firstFrameRendered = true
                                player.removeListener(this)
                            }
                        }
                        player.addListener(listener)
                    }
                }
            }
    }

    // Fallback: if thumbnail doesn't trigger onState within 1s, proceed anyway
    LaunchedEffect(Unit) {
        delay(1000)
        if (!thumbnailLoaded) {
            Log.d(TAG, "[$itemKey] thumbnail fallback triggered")
            thumbnailLoaded = true
        }
    }

    DisposableEffect(itemKey) {
        onDispose {
            VideoPlayerPool.releasePlayer(itemKey)
        }
    }

    Box(modifier = modifier) {
        // Player surface BEHIND the thumbnail
        if (stillOwnsPlayer) {
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

        // Thumbnail on top — shown until first frame renders AND player still ours
        if (showThumbnail) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Success -> {
                            if (!thumbnailLoaded) {
                                Log.d(TAG, "[$itemKey] thumbnail loaded")
                                thumbnailLoaded = true
                            }
                        }
                        is AsyncImagePainter.State.Error -> {
                            Log.e(TAG, "[$itemKey] thumbnail FAILED: $thumbnailUrl")
                            thumbnailLoaded = true
                        }
                        else -> {}
                    }
                }
            )

            // Spinner while buffering
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
