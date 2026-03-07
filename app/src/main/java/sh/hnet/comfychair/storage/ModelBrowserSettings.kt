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
        private const val KEY_NSFW_LEVELS = "nsfw_levels"
        private const val KEY_SHOW_ANIMATIONS = "show_animations"
        private const val KEY_BLUR_THRESHOLD = "blur_threshold"
        private const val KEY_BROWSE_LEVEL = "browse_level"
        private const val KEY_AUTOPLAY_VIDEOS = "autoplay_videos"
        // Civitai NSFW level mapping:
        // 1=PG, 2=PG-13, 4=R, 8=X, 16=XXX, 32=Blocked
        val DEFAULT_NSFW_LEVELS = setOf(1, 2, 4) // PG, PG-13, R
    }

    // Retain application context for tag resolution via CivitaiTagRepository.
    private val appContext: Context = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val securePrefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            SECURE_PREFS_NAME,
            masterKeyAlias,
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // In-memory cache for frequently accessed values
    private var _nsfwLevels: Set<Int>? = null
    private var _showAnimations: Boolean? = null
    private var _blurThreshold: Int? = null
    private var _browseLevel: Int? = null
    private var _autoplayVideos: Boolean? = null

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

    /** Blur threshold for NSFW images. Images with nsfwLevel > this value are blurred. Default: 2 (PG-13). */
    var blurThreshold: Int
        get() = _blurThreshold ?: prefs.getInt(KEY_BLUR_THRESHOLD, 2).also { _blurThreshold = it }
        set(value) {
            _blurThreshold = value
            prefs.edit().putInt(KEY_BLUR_THRESHOLD, value).apply()
        }

    /** Browse level for community images. Bitmask: 1=PG, 3=PG-13, 7=R, 15=X, 31=XXX. Default: 31 (all). */
    var browseLevel: Int
        get() = _browseLevel ?: prefs.getInt(KEY_BROWSE_LEVEL, 31).also { _browseLevel = it }
        set(value) {
            _browseLevel = value
            prefs.edit().putInt(KEY_BROWSE_LEVEL, value).apply()
        }

    /** Whether to autoplay video clips in grids. Off by default (data usage). */
    var autoplayVideos: Boolean
        get() = _autoplayVideos ?: prefs.getBoolean(KEY_AUTOPLAY_VIDEOS, false).also { _autoplayVideos = it }
        set(value) {
            _autoplayVideos = value
            prefs.edit().putBoolean(KEY_AUTOPLAY_VIDEOS, value).apply()
        }

    /** Whether Civitai is configured (has API key). */
    val isCivitaiConfigured: Boolean
        get() = civitaiApiKey.isNotBlank()

    /** Whether HuggingFace is configured (has API key). */
    val isHuggingFaceConfigured: Boolean
        get() = huggingfaceApiKey.isNotBlank()

    // ── Tag resolution (bundled dictionary) ────────────────────────────────────

    /**
     * Return the full bundled tag map (ID → name). Loaded from assets on first call.
     * Delegates to [CivitaiTagRepository]; no SharedPreferences I/O involved.
     */
    fun getAllTags(): Map<Int, String> = CivitaiTagRepository.getTags(appContext)

    /**
     * Resolve a single tag ID to a name, or null if the ID isn't in the bundled map.
     */
    fun getTagName(id: Int): String? = CivitaiTagRepository.getTagName(appContext, id)
}
