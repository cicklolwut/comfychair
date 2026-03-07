package sh.hnet.comfychair.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "image_tags",
    primaryKeys = ["imageId", "tagId"],
    indices = [Index("tagId"), Index("imageId")]
)
data class ImageTag(
    val imageId: Long,
    val tagId: Int
)
