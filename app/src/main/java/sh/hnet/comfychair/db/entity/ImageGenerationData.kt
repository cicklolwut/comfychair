package sh.hnet.comfychair.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "image_generation_data")
data class ImageGenerationData(
    @PrimaryKey val imageId: Long,
    val process: String? = null,          // "txt2img", "img2img", null if hideMeta
    val onSite: Boolean = false,
    val prompt: String? = null,
    val negativePrompt: String? = null,
    val cfgScale: Double? = null,
    val steps: Int? = null,
    val sampler: String? = null,
    val seed: Long? = null,
    val size: String? = null,             // "832x1216"
    val model: String? = null,            // checkpoint filename from meta.Model
    val version: String? = null,          // tool name from meta.Version e.g. "ComfyUI"
    val clipSkip: Int? = null,
    val denoisingStrength: Double? = null,
    val canRemix: Boolean = false,
    val hideMeta: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis()
)
