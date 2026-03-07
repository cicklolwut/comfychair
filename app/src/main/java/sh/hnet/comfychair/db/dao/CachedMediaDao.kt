package sh.hnet.comfychair.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import sh.hnet.comfychair.db.entity.CachedMedia

@Dao
interface CachedMediaDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(media: CachedMedia)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(media: List<CachedMedia>)

    @Query("SELECT * FROM cached_media WHERE id = :id")
    suspend fun getById(id: String): CachedMedia?

    @Query("SELECT local_path FROM cached_media WHERE id = :id AND local_path IS NOT NULL")
    suspend fun getLocalPath(id: String): String?

    @Query("UPDATE cached_media SET local_path = :path, file_size = :size, cached_at = :now, last_viewed = :now WHERE id = :id")
    suspend fun markCached(id: String, path: String, size: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE cached_media SET last_viewed = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM cached_media WHERE local_path IS NULL AND (:versionId IS NULL OR model_version_id = :versionId) AND (:type IS NULL OR type = :type) ORDER BY last_viewed DESC LIMIT :limit")
    suspend fun getUncached(versionId: String? = null, type: String? = null, limit: Int = 20): List<CachedMedia>

    @Query("SELECT COALESCE(SUM(file_size), 0) FROM cached_media WHERE local_path IS NOT NULL")
    suspend fun totalCachedBytes(): Long

    @Query("SELECT COUNT(*) FROM cached_media WHERE local_path IS NOT NULL")
    suspend fun cachedCount(): Int

    @Query("SELECT COUNT(*) FROM cached_media")
    suspend fun totalCount(): Int

    @Query("SELECT * FROM cached_media WHERE local_path IS NOT NULL ORDER BY last_viewed ASC LIMIT :limit")
    suspend fun getOldestCached(limit: Int): List<CachedMedia>

    @Query("UPDATE cached_media SET local_path = NULL, file_size = 0 WHERE id = :id")
    suspend fun clearLocalPath(id: String)

    @Query("UPDATE cached_media SET local_path = NULL, file_size = 0 WHERE local_path IS NOT NULL")
    suspend fun clearAllLocalPaths()

    @Query("DELETE FROM cached_media")
    suspend fun deleteAll()
}
