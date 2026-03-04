package sh.hnet.comfychair.model

/**
 * Model search result from Civitai or HuggingFace.
 */
data class ModelSearchResult(
    val id: String,
    val name: String,
    val description: String?,
    val thumbnailUrl: String?,
    val downloadCount: Long?,
    val favoriteCount: Long?,
    val tags: List<String>,
    val creator: String?,
    val versions: List<ModelVersion>,
    val provider: ModelProvider
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
    val files: List<ModelFile>
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
