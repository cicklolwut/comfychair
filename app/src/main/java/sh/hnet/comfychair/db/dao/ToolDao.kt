package sh.hnet.comfychair.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import sh.hnet.comfychair.db.entity.ImageTool
import sh.hnet.comfychair.db.entity.Tool

@Dao
interface ToolDao {
    @Upsert
    suspend fun upsertAll(tools: List<Tool>)

    @Query("SELECT * FROM tools WHERE toolId = :toolId")
    suspend fun getById(toolId: Int): Tool?

    @Query("SELECT * FROM tools WHERE toolId IN (:toolIds)")
    suspend fun getByIds(toolIds: List<Int>): List<Tool>

    @Query("SELECT * FROM tools WHERE type = :type ORDER BY priority ASC")
    suspend fun getByType(type: String): List<Tool>

    @Query("SELECT COUNT(*) FROM tools")
    suspend fun count(): Int

    // ImageTool join table
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertImageTools(joins: List<ImageTool>)

    @Query("SELECT toolId FROM image_tools WHERE imageId = :imageId")
    suspend fun getToolIdsForImage(imageId: Long): List<Int>

    @Query("SELECT imageId FROM image_tools WHERE toolId = :toolId")
    suspend fun getImageIdsForTool(toolId: Int): List<Long>
}
