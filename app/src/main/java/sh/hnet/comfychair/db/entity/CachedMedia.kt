package sh.hnet.comfychair.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_media",
    indices = [
        Index("lastViewed"),
        Index("modelVersionId"),
        Index("localPath")
    ]
)
data class CachedMedia(
    @PrimaryKey val id: String,
    val type: String = "image",
    val urlOriginal: String,
    val urlTranscode: String? = null,
    val urlThumbnail: String? = null,
    val localPath: String? = null,
    val thumbPath: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val nsfwLevel: Int = 1,
    val fileSize: Long = 0L,
    val lastViewed: Long = 0L,
    val cachedAt: Long = 0L,
    val modelId: String? = null,
    val modelVersionId: String? = null
)
