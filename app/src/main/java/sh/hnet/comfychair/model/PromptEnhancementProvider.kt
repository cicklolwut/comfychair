package sh.hnet.comfychair.model

/**
 * LLM API providers for prompt enhancement.
 * All providers use the OpenAI-compatible chat completions API.
 */
enum class PromptEnhancementProvider(
    val displayName: String,
    val baseUrl: String,
    val supportsModelList: Boolean = false
) {
    OPENAI("OpenAI", "https://api.openai.com/v1"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1", supportsModelList = true),
    CUSTOM("Custom", "", supportsModelList = false);

    companion object {
        /** Default model for each provider when none is specified. */
        fun defaultModel(provider: PromptEnhancementProvider): String {
            return when (provider) {
                OPENAI -> "gpt-4o-mini"
                OPENROUTER -> "openai/gpt-4o-mini"
                CUSTOM -> ""
            }
        }
    }
}
