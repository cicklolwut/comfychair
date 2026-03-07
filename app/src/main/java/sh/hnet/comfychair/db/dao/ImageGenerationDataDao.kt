package sh.hnet.comfychair.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import sh.hnet.comfychair.db.entity.ImageGenerationData

@Dao
interface ImageGenerationDataDao {
    @Upsert
    suspend fun upsert(data: ImageGenerationData)

    @Query("SELECT * FROM image_generation_data WHERE imageId = :imageId")
    suspend fun getById(imageId: Long): ImageGenerationData?

    @Query("SELECT * FROM image_generation_data WHERE imageId = :imageId AND cachedAt > :freshThreshold")
    suspend fun getFreshById(imageId: Long, freshThreshold: Long): ImageGenerationData?

    @Query("DELETE FROM image_generation_data WHERE cachedAt < :before")
    suspend fun evictOlderThan(before: Long)
}
