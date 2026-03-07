package sh.hnet.comfychair.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "image_techniques",
    primaryKeys = ["imageId", "techniqueId"],
    indices = [Index("techniqueId"), Index("imageId")]
)
data class ImageTechnique(
    val imageId: Long,
    val techniqueId: Int
)
