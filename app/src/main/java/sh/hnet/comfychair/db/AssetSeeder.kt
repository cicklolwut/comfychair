package sh.hnet.comfychair.db

import android.content.Context
import org.json.JSONArray
import sh.hnet.comfychair.db.entity.Tag
import sh.hnet.comfychair.db.entity.Tool
import sh.hnet.comfychair.db.repository.CivitaiCacheRepository

object AssetSeeder {

    suspend fun seedIfNeeded(context: Context, repo: CivitaiCacheRepository) {
        seedTools(context, repo)
        seedTags(context, repo)
    }

    private suspend fun seedTools(context: Context, repo: CivitaiCacheRepository) {
        if (repo.hasTools()) return  // already seeded (network fetch or prior asset seed)
        try {
            val json = context.assets.open("civitai_tools.json")
                .bufferedReader().use { it.readText() }
            val arr = JSONArray(json)
            val tools = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Tool(
                    toolId = obj.getInt("toolId"),
                    name = obj.getString("name"),
                    type = obj.optString("type", ""),
                    icon = obj.optString("icon").takeIf { it.isNotEmpty() },
                    domain = obj.optString("domain").takeIf { it.isNotEmpty() },
                    priority = obj.optInt("priority", 0),
                    supported = obj.optBoolean("supported", false)
                )
            }
            if (tools.isNotEmpty()) repo.cacheTools(tools)
        } catch (e: Exception) {
            android.util.Log.w("AssetSeeder", "Failed to seed tools from assets: ${e.message}")
        }
    }

    private suspend fun seedTags(context: Context, repo: CivitaiCacheRepository) {
        if (repo.hasTags()) return  // already seeded
        try {
            val json = context.assets.open("civitai_tags.json")
                .bufferedReader().use { it.readText() }
            val arr = JSONArray(json)
            val tags = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Tag(
                    tagId = obj.getInt("tagId"),
                    name = obj.getString("name"),
                    type = obj.optString("type").takeIf { it.isNotEmpty() },
                    nsfwLevel = obj.optInt("nsfwLevel", 1)
                )
            }
            if (tags.isNotEmpty()) repo.cacheTags(tags)
        } catch (e: Exception) {
            android.util.Log.w("AssetSeeder", "Failed to seed tags from assets: ${e.message}")
        }
    }
}
