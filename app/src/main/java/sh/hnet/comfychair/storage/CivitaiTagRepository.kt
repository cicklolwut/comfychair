package sh.hnet.comfychair.storage

import android.content.Context
import org.json.JSONObject

/**
 * Loads the bundled Civitai tag dictionary from assets exactly once.
 *
 * The JSON file (civitai_tags.json) is a flat map of string ID → tag name scraped
 * from Civitai's tag.getAll API at build time (~16 k entries). Because tag IDs on
 * Civitai are immutable, no persistence is needed — the bundled map covers 99%+ of
 * all IDs seen in model search results.
 *
 * Thread safety: double-checked locking; [getTags] is safe to call from any thread.
 */
object CivitaiTagRepository {

    @Volatile
    private var tags: Map<Int, String>? = null

    /**
     * Return the full tag map, loading from assets on the first call.
     */
    fun getTags(context: Context): Map<Int, String> {
        return tags ?: synchronized(this) {
            tags ?: loadTags(context.applicationContext).also { tags = it }
        }
    }

    /**
     * Resolve a single tag ID to a name, or null if not found in the bundled map.
     */
    fun getTagName(context: Context, id: Int): String? = getTags(context)[id]

    private fun loadTags(context: Context): Map<Int, String> {
        return try {
            val json = context.assets.open("civitai_tags.json").bufferedReader().readText()
            val obj = JSONObject(json)
            buildMap {
                obj.keys().forEach { key -> put(key.toInt(), obj.getString(key)) }
            }
        } catch (e: Exception) {
            android.util.Log.e("CivitaiTagRepository", "Failed to load bundled tags: ${e.message}")
            emptyMap()
        }
    }
}
