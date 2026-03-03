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
    val isBuiltIn: Boolean = false,
    val isDeleted: Boolean = false  // soft-delete for built-ins (can be restored)
)
