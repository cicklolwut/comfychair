package sh.hnet.comfychair.storage

import android.content.Context
import kotlinx.coroutines.*
import sh.hnet.comfychair.db.ComfyChairDatabase
import sh.hnet.comfychair.db.entity.CachedMedia
import java.io.File

/**
 * Room-backed media cache for Civitai community images and videos.
 *
 * Stores metadata + local file paths for cached media. Actual pixel caching
 * is handled by the image loader (Coil); this manages video files and provides
 * a metadata layer for prefetch decisions and LRU eviction.
 *
 * Eviction: LRU by last_viewed timestamp when total cache size exceeds limit.
 * Settings: persisted in SharedPreferences.
 */
class CivitaiMediaCache(context: Context) {
    companion object {
        // Settings keys (SharedPreferences)
        private const val PREFS_NAME = "MediaCachePrefs"
        private const val KEY_CACHE_LIMIT_MB = "cache_limit_mb"
        private const val KEY_PREFETCH_ENABLED = "prefetch_enabled"
        private const val KEY_PREFETCH_COUNT = "prefetch_count"

        const val DEFAULT_CACHE_LIMIT_MB = 250L
        const val DEFAULT_PREFETCH_COUNT = 5

        @Volatile
        private var instance: CivitaiMediaCache? = null

        fun getInstance(context: Context): CivitaiMediaCache {
            return instance ?: synchronized(this) {
                instance ?: CivitaiMediaCache(context.applicationContext).also { instance = it }
            }
        }
    }

    private val appContext = context.applicationContext
    val cacheDir = File(appContext.cacheDir, "civitai_media").apply { mkdirs() }
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val evictionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dao = ComfyChairDatabase.getInstance(appContext).cachedMediaDao()

    // --- Settings ---

    var cacheLimitMb: Long
        get() = prefs.getLong(KEY_CACHE_LIMIT_MB, DEFAULT_CACHE_LIMIT_MB)
        set(value) = prefs.edit().putLong(KEY_CACHE_LIMIT_MB, value).apply()

    var prefetchEnabled: Boolean
        get() = prefs.getBoolean(KEY_PREFETCH_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_PREFETCH_ENABLED, value).apply()

    var prefetchCount: Int
        get() = prefs.getInt(KEY_PREFETCH_COUNT, DEFAULT_PREFETCH_COUNT)
        set(value) = prefs.edit().putInt(KEY_PREFETCH_COUNT, value).apply()

    // --- CRUD ---

    /**
     * Insert or update media metadata. Does NOT download the file.
     */
    fun upsertMetadata(
        id: String,
        type: String = "image",
        urlOriginal: String,
        urlTranscode: String? = null,
        urlThumbnail: String? = null,
        width: Int = 0,
        height: Int = 0,
        nsfwLevel: Int = 1,
        modelId: String? = null,
        modelVersionId: String? = null
    ) {
        evictionScope.launch {
            dao.insert(CachedMedia(
                id = id, type = type, urlOriginal = urlOriginal,
                urlTranscode = urlTranscode, urlThumbnail = urlThumbnail,
                width = width, height = height, nsfwLevel = nsfwLevel,
                modelId = modelId, modelVersionId = modelVersionId
            ))
        }
    }

    /**
     * Batch upsert metadata for a page of results.
     */
    fun upsertBatch(items: List<MediaCacheEntry>) {
        evictionScope.launch {
            dao.insertAll(items.map { it.toCachedMedia() })
        }
    }

    /**
     * Mark a media item as cached (file downloaded to local storage).
     */
    suspend fun markCached(id: String, localPath: String, fileSize: Long) {
        dao.markCached(id, localPath, fileSize)
        evictionScope.launch { evictIfNeeded() }
    }

    /**
     * Update last_viewed timestamp (for LRU tracking).
     */
    fun touch(id: String) {
        evictionScope.launch { dao.touch(id) }
    }

    /**
     * Get local file path if cached, or null.
     */
    fun getLocalPath(id: String): String? {
        // blocking call — called from non-suspend contexts
        return runBlocking { dao.getLocalPath(id) }
    }

    /**
     * Check if a media item has a local cached file.
     */
    fun isCached(id: String): Boolean = getLocalPath(id) != null

    /**
     * Get items that have metadata but no local cache (candidates for prefetch).
     * Returns items ordered by last_viewed DESC (most recently seen first).
     */
    fun getUncachedItems(
        modelVersionId: String? = null,
        type: String? = null,
        limit: Int = 20
    ): List<MediaCacheEntry> {
        return runBlocking {
            dao.getUncached(versionId = modelVersionId, type = type, limit = limit)
                .map { it.toMediaCacheEntry() }
        }
    }

    // --- Cache Stats ---

    /**
     * Total size of all cached files in bytes.
     */
    fun totalCachedBytes(): Long = runBlocking { dao.totalCachedBytes() }

    /**
     * Number of cached items.
     */
    fun cachedCount(): Int = runBlocking { dao.cachedCount() }

    /**
     * Total metadata entries (cached + uncached).
     */
    fun totalEntries(): Int = runBlocking { dao.totalCount() }

    // --- Eviction ---

    /**
     * LRU eviction: delete oldest cached files until under the size limit.
     */
    fun evictIfNeeded() {
        val limitBytes = cacheLimitMb * 1024 * 1024
        var totalBytes = runBlocking { dao.totalCachedBytes() }
        if (totalBytes <= limitBytes) return

        val oldest = runBlocking { dao.getOldestCached(limit = 100) }
        for (entry in oldest) {
            if (totalBytes <= limitBytes) break
            entry.localPath?.let { path ->
                try { File(path).delete() } catch (_: Exception) {}
            }
            runBlocking { dao.clearLocalPath(entry.id) }
            totalBytes -= entry.fileSize
        }
    }

    /**
     * Clear ALL cached files. Keeps metadata.
     */
    fun clearCachedFiles() {
        cacheDir.listFiles()?.forEach { it.delete() }
        evictionScope.launch { dao.clearAllLocalPaths() }
    }

    /**
     * Nuclear option: drop all metadata and files.
     */
    fun clearAll() {
        cacheDir.listFiles()?.forEach { it.delete() }
        evictionScope.launch { dao.deleteAll() }
    }

    /**
     * Get the cache directory for storing downloaded files.
     */
    fun getCacheDir(): File = cacheDir

    /**
     * Release resources held by this cache instance.
     */
    fun close() {
        evictionScope.cancel()
    }
}

// --- Conversion helpers ---

private fun MediaCacheEntry.toCachedMedia() = CachedMedia(
    id = id, type = type, urlOriginal = urlOriginal,
    urlTranscode = urlTranscode, urlThumbnail = urlThumbnail,
    localPath = localPath, thumbPath = thumbPath,
    width = width, height = height, nsfwLevel = nsfwLevel,
    fileSize = fileSize, lastViewed = lastViewed, cachedAt = cachedAt,
    modelId = modelId, modelVersionId = modelVersionId
)

private fun CachedMedia.toMediaCacheEntry() = MediaCacheEntry(
    id = id, type = type, urlOriginal = urlOriginal,
    urlTranscode = urlTranscode, urlThumbnail = urlThumbnail,
    localPath = localPath, thumbPath = thumbPath,
    width = width, height = height, nsfwLevel = nsfwLevel,
    fileSize = fileSize, lastViewed = lastViewed, cachedAt = cachedAt,
    modelId = modelId, modelVersionId = modelVersionId
)

/**
 * Data class for media cache entries (public API — do not change).
 */
data class MediaCacheEntry(
    val id: String,
    val type: String = "image",
    val urlOriginal: String,
    val urlTranscode: String? = null,
    val urlThumbnail: String? = null,
    val localPath: String? = null,
    val thumbPath: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val nsfwLevel: Int = 1,
    val fileSize: Long = 0,
    val lastViewed: Long = 0,
    val cachedAt: Long = 0,
    val modelId: String? = null,
    val modelVersionId: String? = null
)
