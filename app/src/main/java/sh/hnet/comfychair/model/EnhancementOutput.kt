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
) {
    /** Returns list of field names that were populated. */
    fun updatedFieldNames(): List<String> {
        val fields = mutableListOf<String>()
        if (prompt != null) fields.add("Prompt")
        if (negativePrompt != null) fields.add("Negative Prompt")
        if (cfgScale != null) fields.add("CFG Scale → $cfgScale")
        if (steps != null) fields.add("Steps → $steps")
        if (sampler != null) fields.add("Sampler → $sampler")
        if (scheduler != null) fields.add("Scheduler → $scheduler")
        return fields
    }
}

/**
 * Snapshot of generation parameters before enhancement, for revert.
 */
data class PreEnhancementState(
    val prompt: String = "",
    val negativePrompt: String = "",
    val cfgScale: String = "",
    val steps: String = "",
    val sampler: String = "",
    val scheduler: String = "",
    val enhancementPromptId: String? = null  // for reroll
)
