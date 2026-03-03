package sh.hnet.comfychair.model

/**
 * Fields the LLM can output during prompt enhancement.
 * Each can be toggled on/off in settings.
 */
enum class EnhancementOutputField(val jsonKey: String, val displayName: String) {
    PROMPT("prompt", "Positive Prompt"),
    NEGATIVE_PROMPT("negative_prompt", "Negative Prompt"),
    CFG_SCALE("cfg_scale", "CFG Scale"),
    STEPS("steps", "Steps"),
    SAMPLER("sampler", "Sampler"),
    SCHEDULER("scheduler", "Scheduler");

    companion object {
        /** Default enabled fields — just prompt and negative. */
        val DEFAULTS = setOf(PROMPT, NEGATIVE_PROMPT)
    }
}

/**
 * Parsed structured output from the LLM enhancement.
 */
data class EnhancementResult(
    val prompt: String? = null,
    val negativePrompt: String? = null,
    val cfgScale: Double? = null,
    val steps: Int? = null,
    val sampler: String? = null,
    val scheduler: String? = null
)
