package sh.hnet.comfychair.model

/**
 * Model search result from Civitai or HuggingFace.
 */
data class ModelSearchResult(
    val id: String,
    val name: String,
    val description: String?, // HTML from Civitai
    val thumbnailUrl: String?, // Cover image URL (static, anim=false)
    val animatedThumbnailUrl: String? = null, // Animated cover (no anim=false)
    val downloadCount: Long?,
    val favoriteCount: Long?,
    val tags: List<String>,
    val creator: String?,
    val versions: List<ModelVersion>,
    val provider: ModelProvider,
    val civitaiType: String? = null, // "LORA", "Checkpoint", "TextualInversion", etc.
    val baseModel: String? = null // "Illustrious", "NoobAI", "SDXL", "Pony", etc.
)

/**
 * A specific version of a model.
 */
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
data class ModelVersionImage(
    val url: String,
    val nsfwLevel: Int = 1,
    val width: Int = 0,
    val height: Int = 0
)

/**
 * A specific file in a model version (for HuggingFace repos with multiple files).
 */
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
data class CommunityImage(
    val id: Long,
    val url: String,
    val thumbnailUrl: String,         // static thumbnail (anim=false)
    val animatedThumbnailUrl: String, // animated thumbnail (no anim=false) — only differs for non-video
    val width: Int,
    val height: Int,
    val nsfwLevel: Int,
    val type: String = "image",       // "image" or "video"
    val stats: ImageStats?,
    val meta: GenerationMetadata?
)

/**
 * Image statistics.
 */
data class ImageStats(
    val likeCount: Int = 0,
    val heartCount: Int = 0,
    val commentCount: Int = 0
)

/**
 * Generation metadata for an image.
 */
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
