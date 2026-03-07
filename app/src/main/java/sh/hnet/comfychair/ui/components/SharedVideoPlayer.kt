package sh.hnet.comfychair.ui.components

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Singleton manager for a shared ExoPlayer instance.
 *
 * Since only one video plays at a time in the app, we can reuse a single
 * ExoPlayer instance across all video playback locations. This eliminates
 * the initialization delay and surface setup issues that occur when creating
 * new ExoPlayer instances.
 *
 * Uses reference counting to track active consumers. Playback is only paused
 * when all consumers have released the player.
 *
 * Features:
 * - Single ExoPlayer instance with reference counting
 * - 50MB disk cache for video segments (LRU eviction)
 * - Smooth transitions between preview and fullscreen modes
 */
@UnstableApi
object SharedVideoPlayer {

    private var exoPlayer: ExoPlayer? = null
    private var videoCache: SimpleCache? = null
    private var currentUri: Uri? = null
    private var isInitialized = false
    private val consumerCount = AtomicInteger(0)
    private val handler = Handler(Looper.getMainLooper())
    private var pendingStopRunnable: Runnable? = null

    private fun getCache(context: Context): SimpleCache {
        if (videoCache == null) {
            val cacheDir = File(context.cacheDir, "video_cache")
            videoCache = SimpleCache(
                cacheDir,
                LeastRecentlyUsedCacheEvictor(50L * 1024 * 1024), // 50MB
                androidx.media3.database.StandaloneDatabaseProvider(context)
            )
        }
        return videoCache!!
    }

    /**
     * Get or create the shared ExoPlayer instance with caching.
     */
    @Synchronized
    fun getPlayer(context: Context): ExoPlayer {
        if (exoPlayer == null) {
            val appContext = context.applicationContext
            val cache = getCache(appContext)
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(8_000)
                .setReadTimeoutMs(8_000)
                .setAllowCrossProtocolRedirects(true)
            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(httpDataSourceFactory)
            exoPlayer = ExoPlayer.Builder(appContext)
                .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
                .build().apply {
                    repeatMode = Player.REPEAT_MODE_ALL
                    playWhenReady = false
                }
            isInitialized = true
        }
        return exoPlayer!!
    }

    /**
     * Register a consumer (VideoPlayer component) as active.
     * Call this when a VideoPlayer enters composition.
     * Cancels any pending stop since there's an active consumer.
     */
    fun registerConsumer(context: Context): ExoPlayer {
        consumerCount.incrementAndGet()
        // Cancel any pending stop since someone is using the player
        pendingStopRunnable?.let { handler.removeCallbacks(it) }
        pendingStopRunnable = null
        return getPlayer(context)
    }

    /**
     * Unregister a consumer. When count reaches 0, schedules a delayed stop
     * to allow for transitions (e.g., switching from preview to fullscreen).
     * Video is stopped (not paused) so it resets to the beginning on next play.
     */
    fun unregisterConsumer() {
        if (consumerCount.decrementAndGet() <= 0) {
            consumerCount.set(0)
            // Schedule delayed stop to allow for transitions
            pendingStopRunnable?.let { handler.removeCallbacks(it) }
            pendingStopRunnable = Runnable {
                if (consumerCount.get() == 0) {
                    // Stop (not pause) so video resets to beginning on next play
                    exoPlayer?.stop()
                    currentUri = null  // Clear URI so prepareVideo will reload
                }
                pendingStopRunnable = null
            }
            handler.postDelayed(pendingStopRunnable!!, 100)
        }
    }

    /**
     * Prepare a video from the given URI without starting playback.
     * Call startPlayback() after the surface is ready to begin from frame 0.
     * Always resets to the beginning, even if the same URI is already loaded.
     */
    fun prepareVideo(context: Context, uri: Uri) {
        // Cancel any pending stop
        pendingStopRunnable?.let { handler.removeCallbacks(it) }
        pendingStopRunnable = null

        val player = getPlayer(context)

        if (currentUri != uri) {
            // New video - stop current, load new
            player.stop()
            player.setMediaItem(MediaItem.fromUri(uri))
            player.playWhenReady = false  // Don't auto-play
            player.prepare()
            currentUri = uri
        } else {
            // Same video - just reset to beginning and ensure it's ready
            player.playWhenReady = false
            player.seekTo(0)
        }
    }

    /**
     * Start playback from the beginning.
     * Call this after the video surface is ready to ensure playback starts from frame 0.
     */
    fun startPlayback() {
        exoPlayer?.let { player ->
            player.seekTo(0)
            player.playWhenReady = true
            player.play()
        }
    }

    /**
     * Pause playback without releasing resources.
     * Called when a consumer is done but video might be reused.
     */
    fun pause() {
        exoPlayer?.pause()
    }

    /**
     * Resume playback if a video is loaded and there are active consumers.
     */
    fun resume() {
        exoPlayer?.let { player ->
            if (currentUri != null && consumerCount.get() > 0) {
                player.play()
            }
        }
    }

    /**
     * Check if a specific URI is currently loaded.
     */
    fun isCurrentUri(uri: Uri?): Boolean {
        return uri != null && currentUri == uri
    }

    /**
     * Get the currently loaded URI.
     */
    fun getCurrentUri(): Uri? = currentUri

    /**
     * Release all resources. Call when app is being destroyed.
     */
    fun release() {
        pendingStopRunnable?.let { handler.removeCallbacks(it) }
        pendingStopRunnable = null
        exoPlayer?.release()
        exoPlayer = null
        videoCache?.release()
        videoCache = null
        currentUri = null
        isInitialized = false
        consumerCount.set(0)
    }

    /**
     * Check if player is initialized.
     */
    fun isReady(): Boolean = isInitialized && exoPlayer != null
}
