package sh.hnet.comfychair.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import sh.hnet.comfychair.db.entity.ImageTechnique
import sh.hnet.comfychair.db.entity.Technique

@Dao
interface TechniqueDao {
    @Upsert
    suspend fun upsertAll(techniques: List<Technique>)

    @Query("SELECT * FROM techniques WHERE techniqueId = :techniqueId")
    suspend fun getById(techniqueId: Int): Technique?

    @Query("SELECT * FROM techniques WHERE techniqueId IN (:techniqueIds)")
    suspend fun getByIds(techniqueIds: List<Int>): List<Technique>

    @Query("SELECT * FROM techniques WHERE type = :type")
    suspend fun getByType(type: String): List<Technique>

    // ImageTechnique join table
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertImageTechniques(joins: List<ImageTechnique>)

    @Query("SELECT techniqueId FROM image_techniques WHERE imageId = :imageId")
    suspend fun getTechniqueIdsForImage(imageId: Long): List<Int>

    @Query("SELECT imageId FROM image_techniques WHERE techniqueId = :techniqueId")
    suspend fun getImageIdsForTechnique(techniqueId: Int): List<Long>
}
