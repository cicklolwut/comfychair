package sh.hnet.comfychair.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import sh.hnet.comfychair.model.PromptEnhancementMode
import sh.hnet.comfychair.model.PromptEnhancementProvider

/**
 * Persists prompt enhancement configuration: provider, API key, model, and system prompts.
 * API keys are stored in EncryptedSharedPreferences.
 */
class PromptEnhancementSettings(context: Context) {

    companion object {
        private const val PREFS_NAME = "PromptEnhancementPrefs"
        private const val SECURE_PREFS_NAME = "PromptEnhancementSecurePrefs"

        private const val KEY_PROVIDER = "provider"
        private const val KEY_MODEL = "model"
        private const val KEY_CUSTOM_BASE_URL = "custom_base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_SYSTEM_PROMPT_PREFIX = "system_prompt_"

        val DEFAULT_PROMPTS = mapOf(
            PromptEnhancementMode.TEXT_TO_IMAGE to """
                You are a Stable Diffusion prompt engineer. Given a user's description, 
                produce a detailed, comma-separated prompt optimized for image generation.
                Focus on subject, composition, lighting, style, and quality tags.
                Output ONLY the enhanced prompt, nothing else.
            """.trimIndent(),

            PromptEnhancementMode.IMAGE_TO_IMAGE to """
                You are a Stable Diffusion prompt engineer for img2img workflows. Given a 
                user's description of desired changes, produce a detailed prompt that 
                describes the target image. Preserve elements the user doesn't mention.
                Output ONLY the enhanced prompt, nothing else.
            """.trimIndent(),

            PromptEnhancementMode.TEXT_TO_VIDEO to """
                You are a video generation prompt engineer. Given a user's description, 
                produce a detailed prompt optimized for AI video generation. Focus on 
                motion, camera movement, scene transitions, and temporal coherence.
                Output ONLY the enhanced prompt, nothing else.
            """.trimIndent(),

            PromptEnhancementMode.IMAGE_TO_VIDEO to """
                You are a video generation prompt engineer for image-to-video workflows.
                Given a user's description of desired animation/motion, produce a detailed
                prompt that describes the target video. Focus on how the source image
                should be animated.
                Output ONLY the enhanced prompt, nothing else.
            """.trimIndent()
        )
    }

    private val prefs: SharedPreferences by lazy {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val securePrefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            SECURE_PREFS_NAME,
            masterKeyAlias,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var provider: PromptEnhancementProvider
        get() {
            val name = prefs.getString(KEY_PROVIDER, null)
            return name?.let {
                try { PromptEnhancementProvider.valueOf(it) }
                catch (_: Exception) { PromptEnhancementProvider.OPENAI }
            } ?: PromptEnhancementProvider.OPENAI
        }
        set(value) = prefs.edit().putString(KEY_PROVIDER, value.name).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, null)
            ?: PromptEnhancementProvider.defaultModel(provider)
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    var customBaseUrl: String
        get() = prefs.getString(KEY_CUSTOM_BASE_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_BASE_URL, value).apply()

    var apiKey: String
        get() = securePrefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = securePrefs.edit().putString(KEY_API_KEY, value).apply()

    fun getSystemPrompt(mode: PromptEnhancementMode): String {
        return prefs.getString(
            "$KEY_SYSTEM_PROMPT_PREFIX${mode.name}",
            DEFAULT_PROMPTS[mode]
        ) ?: DEFAULT_PROMPTS[mode] ?: ""
    }

    fun setSystemPrompt(mode: PromptEnhancementMode, prompt: String) {
        prefs.edit().putString("$KEY_SYSTEM_PROMPT_PREFIX${mode.name}", prompt).apply()
    }

    fun resetSystemPrompt(mode: PromptEnhancementMode) {
        prefs.edit().remove("$KEY_SYSTEM_PROMPT_PREFIX${mode.name}").apply()
    }

    fun resetAllSystemPrompts() {
        val editor = prefs.edit()
        PromptEnhancementMode.entries.forEach { mode ->
            editor.remove("$KEY_SYSTEM_PROMPT_PREFIX${mode.name}")
        }
        editor.apply()
    }

    /** Whether prompt enhancement is configured (has API key). */
    val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    /** Effective base URL for the current provider. */
    val effectiveBaseUrl: String
        get() = when (provider) {
            PromptEnhancementProvider.CUSTOM -> customBaseUrl
            else -> provider.baseUrl
        }
}
