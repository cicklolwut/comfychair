package sh.hnet.comfychair.storage

import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Background prefetch manager for Civitai media.
 *
 * Downloads media files (primarily videos, since Coil handles image caching)
 * to local storage for instant playback. Runs as fire-and-forget coroutines
 * with configurable concurrency.
 *
 * Also handles background HEAD requests to warm Cloudflare's transcode cache
 * for videos (avoids 6s on-the-fly transcode delay on first view).
 */
class MediaPrefetchManager(
    private val cache: CivitaiMediaCache,
    private val concurrency: Int = 3
) {
    companion object {
        private const val TAG = "MediaPrefetch"
        private const val CONNECT_TIMEOUT = 8_000
        private const val READ_TIMEOUT = 30_000
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val semaphore = kotlinx.coroutines.sync.Semaphore(concurrency)

    /**
     * Warm Cloudflare CDN cache for a batch of video transcode URLs.
     * Fire-and-forget HEAD requests — no files downloaded, minimal bandwidth.
     */
    fun warmTranscodeCache(transcodeUrls: List<String>) {
        for (url in transcodeUrls) {
            scope.launch {
                try {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.requestMethod = "HEAD"
                    conn.connectTimeout = CONNECT_TIMEOUT
                    conn.readTimeout = 5_000
                    conn.instanceFollowRedirects = true
                    conn.responseCode // triggers the request
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.d(TAG, "CDN warm failed for ${url.take(60)}: ${e.message}")
                }
            }
        }
    }

    /**
     * Prefetch media files for a batch of cache entries.
     * Downloads transcoded videos (or originals as fallback) to local cache.
     * Respects the cache size limit — skips if already over budget.
     */
    fun prefetch(items: List<MediaCacheEntry>) {
        if (!cache.prefetchEnabled) return

        val limitBytes = cache.cacheLimitMb * 1024 * 1024
        if (cache.totalCachedBytes() >= limitBytes) {
            Log.d(TAG, "Cache full, skipping prefetch")
            return
        }

        val count = minOf(items.size, cache.prefetchCount)
        for (entry in items.take(count)) {
            if (cache.isCached(entry.id)) continue

            scope.launch {
                semaphore.acquire()
                try {
                    downloadAndCache(entry)
                } finally {
                    semaphore.release()
                }
            }
        }
    }

    /**
     * Download a single media file to local cache.
     */
    private suspend fun downloadAndCache(entry: MediaCacheEntry) {
        // Prefer transcode URL for videos (smaller), original for images
        val downloadUrl = if (entry.type == "video" && entry.urlTranscode != null) {
            entry.urlTranscode
        } else {
            entry.urlOriginal
        }

        val ext = if (entry.type == "video") "mp4" else "webp"
        val localFile = File(cache.getCacheDir(), "${entry.id}.$ext")

        try {
            val conn = URL(downloadUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT
            conn.readTimeout = READ_TIMEOUT
            conn.instanceFollowRedirects = true

            if (conn.responseCode != 200) {
                Log.w(TAG, "Prefetch ${entry.id}: HTTP ${conn.responseCode}")
                conn.disconnect()
                return
            }

            conn.inputStream.use { input ->
                localFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }
            conn.disconnect()

            val fileSize = localFile.length()
            cache.markCached(entry.id, localFile.absolutePath, fileSize)
            Log.d(TAG, "Prefetched ${entry.id} (${fileSize / 1024}KB)")
        } catch (e: Exception) {
            localFile.delete()
            Log.w(TAG, "Prefetch ${entry.id} failed: ${e.message}")
        }
    }

    /**
     * Cancel all running prefetch jobs.
     */
    fun cancelAll() {
        scope.coroutineContext.cancelChildren()
    }
}
