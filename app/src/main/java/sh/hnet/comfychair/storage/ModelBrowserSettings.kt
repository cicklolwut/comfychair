package sh.hnet.comfychair.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONObject

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
        private const val KEY_NSFW_LEVELS = "nsfw_levels"
        private const val KEY_SHOW_ANIMATIONS = "show_animations"
        private const val KEY_TAG_CACHE = "tag_cache"
        // Civitai NSFW level mapping:
        // 1=PG, 2=PG-13, 4=R, 8=X, 16=XXX, 32=Blocked
        val DEFAULT_NSFW_LEVELS = setOf(1, 2, 4) // PG, PG-13, R
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

    // In-memory cache for frequently accessed values
    private var _nsfwLevels: Set<Int>? = null
    private var _showAnimations: Boolean? = null
    private var _tagCache: MutableMap<Int, String>? = null

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
    @Deprecated("Use nsfwLevels instead")
    var showNsfw: Boolean
        get() = prefs.getBoolean(KEY_SHOW_NSFW, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_NSFW, value).apply()

    /** Selected NSFW content levels (Civitai only). Set of level ints: 1=PG, 2=PG-13, 4=R, 8=X, 16=XXX. */
    var nsfwLevels: Set<Int>
        get() = _nsfwLevels ?: run {
            val stored = prefs.getString(KEY_NSFW_LEVELS, null)
            val parsed = if (stored != null) {
                stored.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
            } else {
                DEFAULT_NSFW_LEVELS
            }
            _nsfwLevels = parsed
            parsed
        }
        set(value) {
            _nsfwLevels = value
            prefs.edit().putString(KEY_NSFW_LEVELS, value.joinToString(",")).apply()
        }

    /** Whether to show animated thumbnails (GIF/WebP) in grids. Off by default (bandwidth). */
    var showAnimations: Boolean
        get() = _showAnimations ?: prefs.getBoolean(KEY_SHOW_ANIMATIONS, false).also { _showAnimations = it }
        set(value) {
            _showAnimations = value
            prefs.edit().putBoolean(KEY_SHOW_ANIMATIONS, value).apply()
        }

    /** Whether Civitai is configured (has API key). */
    val isCivitaiConfigured: Boolean
        get() = civitaiApiKey.isNotBlank()

    /** Whether HuggingFace is configured (has API key). */
    val isHuggingFaceConfigured: Boolean
        get() = huggingfaceApiKey.isNotBlank()

    /**
     * Tag ID → name cache. Loaded from SharedPrefs on first access.
     * Tag mappings are immutable on Civitai, so this is append-only.
     */
    val tagCache: Map<Int, String>
        get() {
            if (_tagCache == null) {
                _tagCache = loadTagCache()
            }
            return _tagCache!!
        }

    /**
     * Add new tag mappings to the cache. Merges with existing.
     */
    fun updateTagCache(newTags: Map<Int, String>) {
        if (newTags.isEmpty()) return
        
        val current = _tagCache ?: loadTagCache()
        current.putAll(newTags)
        _tagCache = current
        
        // Persist to SharedPrefs
        val json = JSONObject()
        current.forEach { (id, name) -> json.put(id.toString(), name) }
        prefs.edit().putString(KEY_TAG_CACHE, json.toString()).apply()
    }

    private fun loadTagCache(): MutableMap<Int, String> {
        val stored = prefs.getString(KEY_TAG_CACHE, null) ?: return mutableMapOf()
        return try {
            val json = JSONObject(stored)
            val result = mutableMapOf<Int, String>()
            json.keys().forEach { key ->
                result[key.toInt()] = json.getString(key)
            }
            result
        } catch (e: Exception) {
            mutableMapOf()
        }
    }
}
