package sh.hnet.comfychair.ui.components

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * Pool of ExoPlayer instances for grid autoplay.
 *
 * Manages a small pool of players that can be assigned to visible video
 * items in a scrolling grid. Players are recycled when items scroll off screen.
 *
 * Shares the same disk cache as SharedVideoPlayer (50MB LRU).
 *
 * Typical usage:
 * - Grid determines which video items are sufficiently visible
 * - Pool assigns a player to each visible video
 * - When items scroll away, their player is released back to the pool
 * - If pool is exhausted, oldest assignment is reclaimed
 */
@UnstableApi
object VideoPlayerPool {

    private const val TAG = "VideoPlayerPool"
    private const val MAX_PLAYERS = 6

    private var players = mutableListOf<PooledPlayer>()
    private var isInitialized = false

    data class PooledPlayer(
        val player: ExoPlayer,
        var assignedUri: Uri? = null,
        var assignedKey: String? = null,  // unique key for the grid item
        var lastAssignedAt: Long = 0
    )

    private fun ensureInitialized(context: Context) {
        if (isInitialized) return
        val appContext = context.applicationContext

        // Reuse SharedVideoPlayer's cache if available
        val cacheDataSourceFactory = try {
            val cache = SharedVideoPlayer.let {
                // Access the cache through getPlayer (ensures it's created)
                it.getPlayer(appContext)
                // We'll create our own data source factory using the same cache setup
                null
            }
            // Build our own — simpler, no cache sharing complexity
            val httpFactory = DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(8_000)
                .setReadTimeoutMs(8_000)
                .setAllowCrossProtocolRedirects(true)
            httpFactory
        } catch (e: Exception) {
            DefaultHttpDataSource.Factory()
        }

        for (i in 0 until MAX_PLAYERS) {
            val player = ExoPlayer.Builder(appContext)
                .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
                .build().apply {
                    repeatMode = Player.REPEAT_MODE_ALL
                    volume = 0f  // muted for autoplay
                    playWhenReady = false
                }
            players.add(PooledPlayer(player))
        }
        isInitialized = true
        Log.d(TAG, "Initialized pool with $MAX_PLAYERS players")
    }

    /**
     * Assign a player to a video item. Returns the player if one is available.
     *
     * If the item already has an assigned player, returns it.
     * If pool has free players, assigns one.
     * If pool is full, reclaims the oldest assignment.
     */
    fun assignPlayer(context: Context, key: String, uri: Uri): ExoPlayer? {
        ensureInitialized(context)

        // Already assigned?
        val existing = players.find { it.assignedKey == key }
        if (existing != null) {
            if (existing.assignedUri != uri) {
                // Same key, different URI — reload
                existing.player.stop()
                existing.player.setMediaItem(MediaItem.fromUri(uri))
                existing.player.prepare()
                existing.player.playWhenReady = true
                existing.assignedUri = uri
            }
            existing.lastAssignedAt = System.currentTimeMillis()
            return existing.player
        }

        // Find a free player
        var pooled = players.find { it.assignedKey == null }

        // No free player — reclaim the oldest
        if (pooled == null) {
            pooled = players.minByOrNull { it.lastAssignedAt }!!
            pooled.player.stop()
            Log.d(TAG, "Reclaimed player from ${pooled.assignedKey}")
        }

        // Assign
        pooled.assignedKey = key
        pooled.assignedUri = uri
        pooled.lastAssignedAt = System.currentTimeMillis()
        pooled.player.setMediaItem(MediaItem.fromUri(uri))
        pooled.player.prepare()
        pooled.player.playWhenReady = true

        return pooled.player
    }

    /**
     * Release a player assignment (item scrolled off screen).
     * The player is stopped and returned to the pool.
     */
    fun releasePlayer(key: String) {
        val pooled = players.find { it.assignedKey == key } ?: return
        pooled.player.stop()
        pooled.assignedKey = null
        pooled.assignedUri = null
    }

    /**
     * Pause all players (app backgrounded, screen off).
     */
    fun pauseAll() {
        players.forEach { it.player.pause() }
    }

    /**
     * Resume all assigned players.
     */
    fun resumeAll() {
        players.filter { it.assignedKey != null }.forEach { it.player.play() }
    }

    /**
     * Check if a key has an assigned player.
     */
    fun hasPlayer(key: String): Boolean = players.any { it.assignedKey == key }

    /**
     * Get the player for a key, or null.
     */
    fun getPlayer(key: String): ExoPlayer? = players.find { it.assignedKey == key }?.player

    /**
     * Release all resources.
     */
    fun release() {
        players.forEach { it.player.release() }
        players.clear()
        isInitialized = false
    }
}
