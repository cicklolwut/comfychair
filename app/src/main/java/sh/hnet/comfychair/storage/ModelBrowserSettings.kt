package sh.hnet.comfychair.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Persists model browser configuration: API keys for Civitai and HuggingFace.
 * API keys are stored in EncryptedSharedPreferences for security.
 */
class ModelBrowserSettings(context: Context) {

    companion object {
        private const val PREFS_NAME = "ModelBrowserPrefs"
        private const val SECURE_PREFS_NAME = "ModelBrowserSecurePrefs"

        private const val KEY_CIVITAI_API_KEY = "civitai_api_key"
        private const val KEY_HUGGINGFACE_API_KEY = "huggingface_api_key"
        private const val KEY_PREFERRED_PROVIDER = "preferred_provider"
        private const val KEY_SHOW_NSFW = "show_nsfw"
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

    /** Civitai API key. */
    var civitaiApiKey: String
        get() = securePrefs.getString(KEY_CIVITAI_API_KEY, "") ?: ""
        set(value) = securePrefs.edit().putString(KEY_CIVITAI_API_KEY, value).apply()

    /** HuggingFace API key. */
    var huggingfaceApiKey: String
        get() = securePrefs.getString(KEY_HUGGINGFACE_API_KEY, "") ?: ""
        set(value) = securePrefs.edit().putString(KEY_HUGGINGFACE_API_KEY, value).apply()

    /** Preferred provider (civitai or huggingface). */
    var preferredProvider: String
        get() = prefs.getString(KEY_PREFERRED_PROVIDER, "civitai") ?: "civitai"
        set(value) = prefs.edit().putString(KEY_PREFERRED_PROVIDER, value).apply()

    /** Whether to show NSFW content in search results (Civitai only). */
    var showNsfw: Boolean
        get() = prefs.getBoolean(KEY_SHOW_NSFW, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_NSFW, value).apply()

    /** Whether Civitai is configured (has API key). */
    val isCivitaiConfigured: Boolean
        get() = civitaiApiKey.isNotBlank()

    /** Whether HuggingFace is configured (has API key). */
    val isHuggingFaceConfigured: Boolean
        get() = huggingfaceApiKey.isNotBlank()
}
