package sh.hnet.comfychair.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "image_resources",
    primaryKeys = ["imageId", "versionId"],
    foreignKeys = [
        ForeignKey(
            entity = ImageGenerationData::class,
            parentColumns = ["imageId"],
            childColumns = ["imageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("versionId"), Index("imageId")]
)
data class ImageResource(
    val imageId: Long,
    val versionId: Long,
    val strength: Double? = null    // null when hideMeta=true
)
