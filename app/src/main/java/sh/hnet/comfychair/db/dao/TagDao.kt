package sh.hnet.comfychair.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import sh.hnet.comfychair.db.entity.ImageTag
import sh.hnet.comfychair.db.entity.Tag

@Dao
interface TagDao {
    @Upsert
    suspend fun upsertAll(tags: List<Tag>)

    @Query("SELECT * FROM tags WHERE tagId = :tagId")
    suspend fun getById(tagId: Int): Tag?

    @Query("SELECT * FROM tags WHERE tagId IN (:tagIds)")
    suspend fun getByIds(tagIds: List<Int>): List<Tag>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertImageTags(joins: List<ImageTag>)

    @Query("SELECT tagId FROM image_tags WHERE imageId = :imageId")
    suspend fun getTagIdsForImage(imageId: Long): List<Int>

    @Query("SELECT imageId FROM image_tags WHERE tagId = :tagId")
    suspend fun getImageIdsForTag(tagId: Int): List<Long>

    @Query("SELECT COUNT(*) FROM tags")
    suspend fun count(): Int
}
