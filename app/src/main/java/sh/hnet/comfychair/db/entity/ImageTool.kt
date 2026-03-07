package sh.hnet.comfychair.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "image_tools",
    primaryKeys = ["imageId", "toolId"],
    indices = [Index("toolId"), Index("imageId")]
)
data class ImageTool(
    val imageId: Long,
    val toolId: Int
)
