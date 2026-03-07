package sh.hnet.comfychair.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_media",
    indices = [
        Index("last_viewed"),
        Index("model_version_id"),
        Index("local_path")
    ]
)
data class CachedMedia(
    @PrimaryKey val id: String,
    val type: String = "image",
    @ColumnInfo(name = "url_original") val urlOriginal: String,
    @ColumnInfo(name = "url_transcode") val urlTranscode: String? = null,
    @ColumnInfo(name = "url_thumbnail") val urlThumbnail: String? = null,
    @ColumnInfo(name = "local_path") val localPath: String? = null,
    @ColumnInfo(name = "thumb_path") val thumbPath: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    @ColumnInfo(name = "nsfw_level") val nsfwLevel: Int = 1,
    @ColumnInfo(name = "file_size") val fileSize: Long = 0L,
    @ColumnInfo(name = "last_viewed") val lastViewed: Long = 0L,
    @ColumnInfo(name = "cached_at") val cachedAt: Long = 0L,
    @ColumnInfo(name = "model_id") val modelId: String? = null,
    @ColumnInfo(name = "model_version_id") val modelVersionId: String? = null
)
