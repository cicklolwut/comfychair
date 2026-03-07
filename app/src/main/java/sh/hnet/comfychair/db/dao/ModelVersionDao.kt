package sh.hnet.comfychair.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import sh.hnet.comfychair.db.entity.ModelVersion

@Dao
interface ModelVersionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(version: ModelVersion)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfAbsent(versions: List<ModelVersion>)

    @Upsert
    suspend fun upsert(version: ModelVersion)

    @Upsert
    suspend fun upsertAll(versions: List<ModelVersion>)

    @Query("SELECT * FROM model_versions WHERE versionId = :versionId")
    suspend fun getById(versionId: Long): ModelVersion?

    @Query("SELECT * FROM model_versions WHERE versionId IN (:versionIds)")
    suspend fun getByIds(versionIds: List<Long>): List<ModelVersion>

    // Returns only versions that have modelName populated (fully enriched)
    @Query("SELECT * FROM model_versions WHERE versionId IN (:versionIds) AND modelName IS NOT NULL")
    suspend fun getEnrichedByIds(versionIds: List<Long>): List<ModelVersion>

    @Query("SELECT versionId FROM model_versions WHERE versionId IN (:versionIds) AND modelName IS NULL")
    suspend fun getUnenrichedIds(versionIds: List<Long>): List<Long>
}
