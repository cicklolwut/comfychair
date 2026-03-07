package sh.hnet.comfychair.model

import androidx.compose.runtime.Immutable

/**
 * Model search result from Civitai or HuggingFace.
 */
@Immutable
data class ModelSearchResult(
    val id: String,
    val name: String,
    val description: String?, // HTML from Civitai
    val thumbnailUrl: String?, // Cover image URL (static, anim=false)
    val animatedThumbnailUrl: String? = null, // Animated cover (no anim=false)
    val coverImageNsfwLevel: Int = 1, // NSFW level of the cover image (for browseLevel filtering)
    val coverImageType: String = "image", // "image" or "video"
    val coverVideoUrl: String? = null, // transcode URL for video covers (for inline playback)
    val downloadCount: Long?,
    val favoriteCount: Long?,
    val tags: List<String>,
    val creator: String?,
    val creatorId: Int? = null, // Civitai user ID (for prioritizedUserIds in community images)
    val versions: List<ModelVersion>,
    val provider: ModelProvider,
    val civitaiType: String? = null, // "LORA", "Checkpoint", "TextualInversion", etc.
    val baseModel: String? = null // "Illustrious", "NoobAI", "SDXL", "Pony", etc.
)

/**
 * A specific version of a model.
 */
@Immutable
data class ModelVersion(
    val id: String,
    val name: String,
    val baseModel: String?,
    val downloadUrl: String,
    val filename: String,
    val sizeKB: Long?,
    val files: List<ModelFile>,
    val images: List<ModelVersionImage> = emptyList(),
    val trainedWords: List<String> = emptyList(),
    val description: String? = null // HTML, version-specific notes
)

/**
 * Image associated with a model version (from Civitai).
 */
@Immutable
data class ModelVersionImage(
    val url: String,
    val nsfwLevel: Int = 1,
    val width: Int = 0,
    val height: Int = 0,
    val type: String = "image"    // "image" or "video"
)

/**
 * A specific file in a model version (for HuggingFace repos with multiple files).
 */
@Immutable
data class ModelFile(
    val filename: String,
    val downloadUrl: String,
    val sizeBytes: Long?
)

/**
 * Model provider (Civitai or HuggingFace).
 */
enum class ModelProvider {
    CIVITAI,
    HUGGINGFACE
}

/**
 * Model type for ComfyUI Manager download.
 */
enum class ModelType(val value: String, val displayName: String) {
    CHECKPOINT("checkpoint", "Checkpoint"),
    LORA("lora", "LoRA"),
    VAE("vae", "VAE"),
    CONTROLNET("controlnet", "ControlNet"),
    CLIP_VISION("clip_vision", "CLIP Vision"),
    UPSCALE("upscale", "Upscale Model"),
    EMBEDDING("embedding", "Embedding"),
    DIFFUSION_MODEL("diffusion_model", "Diffusion Model"),
    TEXT_ENCODER("text_encoders", "Text Encoder"),
    GLIGEN("gligen", "GLIGEN"),
    T2I_ADAPTER("t2i-adapter", "T2I Adapter");

    companion object {
        fun fromDisplayName(name: String): ModelType? =
            values().find { it.displayName == name }
    }
}

/**
 * Community image from Civitai.
 */
@Immutable
data class CommunityImage(
    val id: Long,
    val url: String,
    val thumbnailUrl: String,         // static thumbnail (anim=false)
    val animatedThumbnailUrl: String, // animated thumbnail (no anim=false) — only differs for non-video
    val width: Int,
    val height: Int,
    val nsfwLevel: Int,
    val type: String = "image",       // "image" or "video"
    val hasMeta: Boolean = false,     // whether generation metadata exists (from API)
    val postId: Long? = null,         // parent post ID for grouping
    val stats: ImageStats?,
    val meta: GenerationMetadata?,
    // Gallery-level fields (seeded directly from image.getImagesAsPostsInfinite)
    val modelVersionIds: List<Long> = emptyList(),
    val modelVersionIdsManual: List<Long> = emptyList(),
    val toolIds: List<Int> = emptyList(),
    val techniqueIds: List<Int> = emptyList(),
    val tagIds: List<Int> = emptyList(),
    val baseModel: String? = null,    // base model from gallery response (no need to wait for getGenerationData)
    val onSite: Boolean = false,      // generated on Civitai
    val hideMeta: Boolean = false     // metadata hidden by owner
)

/**
 * A community post containing one or more images/videos.
 */
@Immutable
data class CommunityPost(
    val postId: Long,
    val pinned: Boolean = false,
    val nsfwLevel: Int = 1,
    val username: String? = null,
    val publishedAt: String? = null,
    val images: List<CommunityImage>
)

/**
 * Image statistics.
 */
@Immutable
data class ImageStats(
    val likeCount: Int = 0,
    val heartCount: Int = 0,
    val commentCount: Int = 0
)

/**
 * Generation metadata for an image.
 */
@Immutable
data class GenerationMetadata(
    val prompt: String?,
    val negativePrompt: String?,
    val sampler: String?,
    val steps: Int?,
    val cfgScale: Double?,
    val seed: Long?,
    val baseModel: String?,
    val resources: List<GenerationResource>
)

/**
 * Resource used in generation (LoRA, checkpoint, etc.).
 */
@Immutable
data class GenerationResource(
    val name: String?,
    val type: String?, // "lora", "checkpoint"
    val weight: Double?,
    val modelVersionId: Long?
)

/**
 * Mapper for Civitai model types to ComfyUI Manager types.
 */
object CivitaiTypeMapper {
    /** Map Civitai model type to ComfyUI Manager model type */
    fun toComfyUIType(civitaiType: String?): ModelType? = when (civitaiType?.uppercase()) {
        "CHECKPOINT" -> ModelType.CHECKPOINT
        "LORA", "LOCON" -> ModelType.LORA
        "TEXTUALINVERSION" -> ModelType.EMBEDDING
        "VAE" -> ModelType.VAE
        "CONTROLNET" -> ModelType.CONTROLNET
        "UPSCALER" -> ModelType.UPSCALE
        "HYPERNETWORK" -> null // not in our enum
        else -> null
    }
}
