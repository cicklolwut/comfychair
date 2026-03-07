package sh.hnet.comfychair.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.*
import java.io.File

/**
 * SQLite-backed media cache for Civitai community images and videos.
 *
 * Stores metadata + local file paths for cached media. Actual pixel caching
 * is handled by the image loader (Coil); this manages video files and provides
 * a metadata layer for prefetch decisions and LRU eviction.
 *
 * Schema:
 * - media_cache: metadata + file paths for cached media
 * - cache_settings: persisted in SharedPreferences (not DB)
 *
 * Eviction: LRU by last_viewed timestamp when total cache size exceeds limit.
 */
class CivitaiMediaCache(context: Context) : SQLiteOpenHelper(
    context.applicationContext, DB_NAME, null, DB_VERSION
) {
    companion object {
        private const val DB_NAME = "civitai_media_cache.db"
        private const val DB_VERSION = 1
        private const val TABLE = "media_cache"

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
                instance ?: CivitaiMediaCache(context).also { instance = it }
            }
        }
    }

    private val appContext = context.applicationContext
    private val cacheDir = File(appContext.cacheDir, "civitai_media").apply { mkdirs() }
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val evictionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

    // --- Schema ---

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL DEFAULT 'image',
                url_original TEXT NOT NULL,
                url_transcode TEXT,
                url_thumbnail TEXT,
                local_path TEXT,
                thumb_path TEXT,
                width INTEGER NOT NULL DEFAULT 0,
                height INTEGER NOT NULL DEFAULT 0,
                nsfw_level INTEGER NOT NULL DEFAULT 1,
                file_size INTEGER NOT NULL DEFAULT 0,
                last_viewed INTEGER NOT NULL DEFAULT 0,
                cached_at INTEGER NOT NULL DEFAULT 0,
                model_id TEXT,
                model_version_id TEXT
            )
        """)
        db.execSQL("CREATE INDEX idx_last_viewed ON $TABLE (last_viewed)")
        db.execSQL("CREATE INDEX idx_model_version ON $TABLE (model_version_id)")
        db.execSQL("CREATE INDEX idx_cached ON $TABLE (local_path)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future migrations go here
    }

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
        val values = ContentValues().apply {
            put("id", id)
            put("type", type)
            put("url_original", urlOriginal)
            put("url_transcode", urlTranscode)
            put("url_thumbnail", urlThumbnail)
            put("width", width)
            put("height", height)
            put("nsfw_level", nsfwLevel)
            put("model_id", modelId)
            put("model_version_id", modelVersionId)
        }
        writableDatabase.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    /**
     * Batch upsert metadata for a page of results.
     */
    fun upsertBatch(items: List<MediaCacheEntry>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (item in items) {
                val values = ContentValues().apply {
                    put("id", item.id)
                    put("type", item.type)
                    put("url_original", item.urlOriginal)
                    put("url_transcode", item.urlTranscode)
                    put("url_thumbnail", item.urlThumbnail)
                    put("width", item.width)
                    put("height", item.height)
                    put("nsfw_level", item.nsfwLevel)
                    put("model_id", item.modelId)
                    put("model_version_id", item.modelVersionId)
                }
                db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Mark a media item as cached (file downloaded to local storage).
     * Runs on Dispatchers.IO to avoid blocking the calling thread.
     */
    suspend fun markCached(id: String, localPath: String, fileSize: Long) {
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("local_path", localPath)
                put("file_size", fileSize)
                put("cached_at", System.currentTimeMillis())
                put("last_viewed", System.currentTimeMillis())
            }
            writableDatabase.update(TABLE, values, "id = ?", arrayOf(id))
            evictionScope.launch { evictIfNeeded() }
        }
    }

    /**
     * Update last_viewed timestamp (for LRU tracking).
     */
    fun touch(id: String) {
        val values = ContentValues().apply {
            put("last_viewed", System.currentTimeMillis())
        }
        writableDatabase.update(TABLE, values, "id = ?", arrayOf(id))
    }

    /**
     * Get local file path if cached, or null.
     */
    fun getLocalPath(id: String): String? {
        val cursor = readableDatabase.query(
            TABLE, arrayOf("local_path"),
            "id = ? AND local_path IS NOT NULL", arrayOf(id),
            null, null, null
        )
        return cursor.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
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
        val where = mutableListOf("local_path IS NULL")
        val args = mutableListOf<String>()
        if (modelVersionId != null) {
            where.add("model_version_id = ?")
            args.add(modelVersionId)
        }
        if (type != null) {
            where.add("type = ?")
            args.add(type)
        }
        val cursor = readableDatabase.query(
            TABLE, null,
            where.joinToString(" AND "), args.toTypedArray(),
            null, null, "last_viewed DESC", limit.toString()
        )
        return cursor.use { c ->
            val results = mutableListOf<MediaCacheEntry>()
            while (c.moveToNext()) results.add(cursorToEntry(c))
            results
        }
    }

    // --- Cache Stats ---

    /**
     * Total size of all cached files in bytes.
     */
    fun totalCachedBytes(): Long {
        val cursor = readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(file_size), 0) FROM $TABLE WHERE local_path IS NOT NULL", null
        )
        return cursor.use { if (it.moveToFirst()) it.getLong(0) else 0L }
    }

    /**
     * Number of cached items.
     */
    fun cachedCount(): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE WHERE local_path IS NOT NULL", null
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /**
     * Total metadata entries (cached + uncached).
     */
    fun totalEntries(): Int {
        val cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null)
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    // --- Eviction ---

    /**
     * LRU eviction: delete oldest cached files until under the size limit.
     */
    fun evictIfNeeded() {
        val limitBytes = cacheLimitMb * 1024 * 1024
        var totalBytes = totalCachedBytes()
        if (totalBytes <= limitBytes) return

        // Get cached items ordered by last_viewed ASC (oldest first)
        val cursor = readableDatabase.query(
            TABLE, arrayOf("id", "local_path", "file_size"),
            "local_path IS NOT NULL", null,
            null, null, "last_viewed ASC"
        )
        cursor.use { c ->
            while (c.moveToNext() && totalBytes > limitBytes) {
                val id = c.getString(0)
                val path = c.getString(1)
                val size = c.getLong(2)

                // Delete file
                try { File(path).delete() } catch (_: Exception) {}

                // Clear local_path in DB (keep metadata)
                val values = ContentValues().apply {
                    putNull("local_path")
                    put("file_size", 0)
                }
                writableDatabase.update(TABLE, values, "id = ?", arrayOf(id))
                totalBytes -= size
            }
        }
    }

    /**
     * Clear ALL cached files. Keeps metadata.
     */
    fun clearCachedFiles() {
        // Delete all files in cache dir
        cacheDir.listFiles()?.forEach { it.delete() }

        // Clear local_path and file_size in DB
        val values = ContentValues().apply {
            putNull("local_path")
            put("file_size", 0)
        }
        writableDatabase.update(TABLE, values, "local_path IS NOT NULL", null)
    }

    /**
     * Nuclear option: drop all metadata and files.
     */
    fun clearAll() {
        cacheDir.listFiles()?.forEach { it.delete() }
        writableDatabase.delete(TABLE, null, null)
    }

    /**
     * Get the cache directory for storing downloaded files.
     */
    fun getCacheDir(): File = cacheDir

    // --- Helpers ---

    private fun cursorToEntry(c: android.database.Cursor): MediaCacheEntry {
        return MediaCacheEntry(
            id = c.getString(c.getColumnIndexOrThrow("id")),
            type = c.getString(c.getColumnIndexOrThrow("type")),
            urlOriginal = c.getString(c.getColumnIndexOrThrow("url_original")),
            urlTranscode = c.getString(c.getColumnIndexOrThrow("url_transcode")),
            urlThumbnail = c.getString(c.getColumnIndexOrThrow("url_thumbnail")),
            localPath = c.getString(c.getColumnIndexOrThrow("local_path")),
            thumbPath = c.getString(c.getColumnIndexOrThrow("thumb_path")),
            width = c.getInt(c.getColumnIndexOrThrow("width")),
            height = c.getInt(c.getColumnIndexOrThrow("height")),
            nsfwLevel = c.getInt(c.getColumnIndexOrThrow("nsfw_level")),
            fileSize = c.getLong(c.getColumnIndexOrThrow("file_size")),
            lastViewed = c.getLong(c.getColumnIndexOrThrow("last_viewed")),
            cachedAt = c.getLong(c.getColumnIndexOrThrow("cached_at")),
            modelId = c.getString(c.getColumnIndexOrThrow("model_id")),
            modelVersionId = c.getString(c.getColumnIndexOrThrow("model_version_id"))
        )
    }

    /**
     * Release resources held by this cache instance.
     * Cancels the eviction coroutine scope and closes the underlying database.
     */
    fun close() {
        evictionScope.cancel()
        db.close()
    }
}

/**
 * Data class for media cache entries.
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
