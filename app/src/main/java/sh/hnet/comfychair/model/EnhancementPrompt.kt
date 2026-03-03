package sh.hnet.comfychair.model

import java.util.UUID

/**
 * Tags that categorize enhancement prompts by workflow type.
 * Each prompt can have multiple tags.
 */
enum class PromptTag(val displayName: String) {
    TEXT_TO_IMAGE("Text to Image"),
    IMG2IMG_INPAINTING("Image to Image: Inpainting"),
    IMG2IMG_EDITING("Image to Image: Editing"),
    TEXT_TO_VIDEO("Text to Video"),
    IMAGE_TO_VIDEO("Image to Video");

    companion object {
        /**
         * Map from PromptEnhancementMode to default matching tags.
         */
        fun defaultTagsForMode(mode: PromptEnhancementMode): Set<PromptTag> = when (mode) {
            PromptEnhancementMode.TEXT_TO_IMAGE -> setOf(TEXT_TO_IMAGE)
            PromptEnhancementMode.IMAGE_TO_IMAGE -> setOf(IMG2IMG_INPAINTING, IMG2IMG_EDITING)
            PromptEnhancementMode.TEXT_TO_VIDEO -> setOf(TEXT_TO_VIDEO)
            PromptEnhancementMode.IMAGE_TO_VIDEO -> setOf(IMAGE_TO_VIDEO)
        }
    }
}

/**
 * A named enhancement prompt with tags and content.
 * Can be a built-in default or user-created.
 */
data class EnhancementPrompt(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val systemPrompt: String,
    val tags: Set<PromptTag>,
    val exampleInput: String = "",
    val exampleOutput: String = "",
    val isBuiltIn: Boolean = false,
    val isDeleted: Boolean = false  // soft-delete for built-ins (can be restored)
) {
    /**
     * Build the effective system prompt with JSON output schema and optional examples.
     */
    fun buildSystemPrompt(
        enabledFields: Set<EnhancementOutputField>,
        includeExamples: Boolean
    ): String {
        val sb = StringBuilder(systemPrompt)

        // Add JSON output format instructions
        sb.append("\n\n")
        sb.append("IMPORTANT: You MUST respond with a valid JSON object containing ONLY these fields:\n")
        sb.append("{\n")
        enabledFields.sortedBy { it.ordinal }.forEach { field ->
            val desc = when (field) {
                EnhancementOutputField.PROMPT -> "\"prompt\": \"the enhanced positive prompt\""
                EnhancementOutputField.NEGATIVE_PROMPT -> "\"negative_prompt\": \"things to avoid in generation\""
                EnhancementOutputField.CFG_SCALE -> "\"cfg_scale\": <number, typically 2.0-15.0>"
                EnhancementOutputField.STEPS -> "\"steps\": <integer, typically 20-50>"
                EnhancementOutputField.SAMPLER -> "\"sampler\": \"recommended sampler name\""
                EnhancementOutputField.SCHEDULER -> "\"scheduler\": \"recommended scheduler name\""
            }
            sb.append("  $desc,\n")
        }
        sb.append("}\n")
        sb.append("Do NOT include any text outside the JSON object. No markdown, no explanation.")

        // Add examples if enabled
        if (includeExamples && exampleInput.isNotBlank() && exampleOutput.isNotBlank()) {
            // Build example JSON from the example fields
            val exampleJson = buildExampleJson(enabledFields)
            sb.append("\n\nExample:\nUser: $exampleInput\nOutput: $exampleJson")
        }

        return sb.toString()
    }

    private fun buildExampleJson(enabledFields: Set<EnhancementOutputField>): String {
        val parts = mutableListOf<String>()
        enabledFields.sortedBy { it.ordinal }.forEach { field ->
            when (field) {
                EnhancementOutputField.PROMPT -> parts.add("\"prompt\": \"$exampleOutput\"")
                EnhancementOutputField.NEGATIVE_PROMPT -> parts.add("\"negative_prompt\": \"worst quality, bad quality, blurry, lowres\"")
                EnhancementOutputField.CFG_SCALE -> parts.add("\"cfg_scale\": 7.0")
                EnhancementOutputField.STEPS -> parts.add("\"steps\": 28")
                EnhancementOutputField.SAMPLER -> parts.add("\"sampler\": \"euler\"")
                EnhancementOutputField.SCHEDULER -> parts.add("\"scheduler\": \"normal\"")
            }
        }
        return "{ ${parts.joinToString(", ")} }"
    }
}
