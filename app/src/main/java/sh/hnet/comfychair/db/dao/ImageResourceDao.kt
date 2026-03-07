package sh.hnet.comfychair.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import sh.hnet.comfychair.db.entity.ImageResource
import sh.hnet.comfychair.db.entity.ModelVersion

data class ImageResourceWithVersion(
    @Embedded val resource: ImageResource,
    @Relation(
        parentColumn = "versionId",
        entityColumn = "versionId"
    )
    val version: ModelVersion?
)

@Dao
interface ImageResourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(resources: List<ImageResource>)

    @Transaction
    @Query("SELECT * FROM image_resources WHERE imageId = :imageId")
    suspend fun getWithVersions(imageId: Long): List<ImageResourceWithVersion>

    @Query("SELECT versionId FROM image_resources WHERE imageId = :imageId")
    suspend fun getVersionIds(imageId: Long): List<Long>

    @Query("SELECT imageId FROM image_resources WHERE versionId = :versionId")
    suspend fun getImageIdsByVersion(versionId: Long): List<Long>

    @Query("DELETE FROM image_resources WHERE imageId = :imageId")
    suspend fun deleteByImage(imageId: Long)
}
