package sh.hnet.comfychair.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import sh.hnet.comfychair.model.ModelProvider
import sh.hnet.comfychair.model.ModelSearchResult
import sh.hnet.comfychair.util.DebugLogger
import java.util.concurrent.TimeUnit

/**
 * Service for searching Civitai models via their public Meilisearch endpoint.
 * No API key needed for search — uses a public bearer token.
 * 
 * Note: Full model details (versions, descriptions, community images) still require
 * the Civitai REST API with user API key.
 */
class CivitaiMeiliService {
    companion object {
        private const val MEILI_ENDPOINT = "https://search.civitai.com/multi-search"
        private const val PUBLIC_TOKEN = "8c46eb2508e21db1e9828a97968d91ab1ca1caa5f70a00e88a2ba1e286603b61"
        private const val INDEX_UID = "models_v9"
        private const val TAG = "CivitaiMeiliService"
    }

    data class MeiliSearchResult(
        val models: List<ModelSearchResult>,
        val totalHits: Int,
        val facets: FacetDistribution?
    )

    data class FacetDistribution(
        val types: Map<String, Int>,        // e.g. "Checkpoint" -> 16696
        val baseModels: Map<String, Int>    // e.g. "Illustrious" -> 242976
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Search models via Civitai's Meilisearch endpoint.
     * 
     * @param query Search query (can be empty for browsing)
     * @param type Model type filter ("Checkpoint", "LORA", etc.)
     * @param baseModel Base model filter ("Illustrious", "NoobAI", etc.)
     * @param sort Sort order (see sortMapping below)
     * @param nsfw Whether to include NSFW models (nsfwLevel > 4)
     * @param limit Number of results
     * @param offset Pagination offset
     */
    suspend fun searchModels(
        query: String,
        type: String? = null,
        baseModel: String? = null,
        sort: String = "Most Downloaded",
        nsfw: Boolean = false,
        limit: Int = 20,
        offset: Int = 0
    ): MeiliSearchResult = withContext(Dispatchers.IO) {
        // Build filter array
        val filters = mutableListOf<Any>()
        
        // Type filter (wrapped in array for OR group)
        if (type != null) {
            filters.add(listOf("\"type\"=\"$type\""))
        }
        
        // Base model filter (wrapped in array for OR group)
        if (baseModel != null) {
            filters.add(listOf("\"version.baseModel\"=\"$baseModel\""))
        }
        
        // NSFW filter (top-level AND)
        if (!nsfw) {
            filters.add("nsfwLevel IN [1, 2, 4]")
        }
        
        // Always require public availability
        filters.add("availability = Public")
        
        // Map sort option to Meili sort field
        val sortField = when (sort) {
            "Most Downloaded" -> "metrics.downloadCount:desc"
            "Highest Rated" -> "metrics.thumbsUpCount:desc"
            "Most Liked" -> "metrics.favoriteCount:desc"
            "Most Discussed" -> "metrics.commentCount:desc"
            "Most Collected" -> "metrics.collectedCount:desc"
            "Most Buzz" -> "metrics.tippedAmountCount:desc"
            "Newest" -> "createdAt:desc"
            else -> "metrics.downloadCount:desc"
        }
        
        // Build request JSON
        val requestJson = JSONObject().apply {
            put("queries", JSONArray().apply {
                put(JSONObject().apply {
                    put("q", query)
                    put("indexUid", INDEX_UID)
                    put("limit", limit)
                    put("offset", offset)
                    put("filter", JSONArray(filters))
                    put("sort", JSONArray().apply { put(sortField) })
                    put("facets", JSONArray().apply {
                        put("type")
                        put("version.baseModel")
                    })
                })
            })
        }
        
        DebugLogger.d(TAG, "Searching Meili: query='$query', type=$type, baseModel=$baseModel, nsfw=$nsfw")
        
        val requestBody = requestJson.toString()
            .toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url(MEILI_ENDPOINT)
            .header("Authorization", "Bearer $PUBLIC_TOKEN")
            .post(requestBody)
            .build()
        
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw RuntimeException("Meilisearch returned ${response.code}: $errorBody")
        }
        
        val body = response.body?.string() ?: throw RuntimeException("Empty response")
        val json = JSONObject(body)
        val results = json.optJSONArray("results")?.optJSONObject(0)
            ?: throw RuntimeException("Invalid response format")
        
        val hits = results.optJSONArray("hits") ?: JSONArray()
        val totalHits = results.optInt("estimatedTotalHits", 0)
        
        // Parse facets
        val facetDistribution = results.optJSONObject("facetDistribution")
        val facets = if (facetDistribution != null) {
            val typesObj = facetDistribution.optJSONObject("type")
            val baseModelsObj = facetDistribution.optJSONObject("version.baseModel")
            
            val typesMap = mutableMapOf<String, Int>()
            typesObj?.keys()?.forEach { key ->
                typesMap[key] = typesObj.getInt(key)
            }
            
            val baseModelsMap = mutableMapOf<String, Int>()
            baseModelsObj?.keys()?.forEach { key ->
                baseModelsMap[key] = baseModelsObj.getInt(key)
            }
            
            FacetDistribution(typesMap, baseModelsMap)
        } else null
        
        // Parse hits into ModelSearchResult
        val models = mutableListOf<ModelSearchResult>()
        for (i in 0 until hits.length()) {
            val hit = hits.getJSONObject(i)
            try {
                models.add(parseHit(hit))
            } catch (e: Exception) {
                DebugLogger.w(TAG, "Failed to parse hit: ${e.message}")
            }
        }
        
        DebugLogger.d(TAG, "Found ${models.size} models (total: $totalHits)")
        
        MeiliSearchResult(models, totalHits, facets)
    }
    
    private fun parseHit(json: JSONObject): ModelSearchResult {
        val id = json.optLong("id", 0).toString()
        val name = json.optString("name", "Unknown")
        val type = json.optString("type", "")
        
        // Parse stats
        val statsObj = json.optJSONObject("stats")
        val downloadCount = statsObj?.optLong("downloadCount")
        val favoriteCount = statsObj?.optLong("favoriteCount")
        
        // Parse tags (Meili returns [{id, name}] objects, not plain strings)
        val tagsArray = json.optJSONArray("tags")
        val tags = mutableListOf<String>()
        if (tagsArray != null) {
            for (i in 0 until tagsArray.length()) {
                val tag = tagsArray.get(i)
                when (tag) {
                    is JSONObject -> tag.optString("name", null)?.let { tags.add(it) }
                    is String -> tags.add(tag)
                    else -> {} // skip numeric tag IDs
                }
            }
        }
        
        // Parse creator
        val userObj = json.optJSONObject("user")
        val creator = userObj?.optString("username")
        
        // Parse version info
        val versionObj = json.optJSONObject("version")
        val baseModel = versionObj?.optString("baseModel")
        
        // Parse thumbnail from Meili image data
        // Meili returns image URL as just a UUID — need to construct the full CDN URL
        // Format: https://image.civitai.com/xG1nkqKTMzfpXDLw6IMLCjbA7Bm7MVHJ/{uuid}/width=200/{filename}
        val imagesArray = json.optJSONArray("images")
        val thumbnailUrl = if (imagesArray != null && imagesArray.length() > 0) {
            val imgObj = imagesArray.getJSONObject(0)
            val urlOrUuid = imgObj.optString("url", null)
            val imgName = imgObj.optString("name", "image.jpeg")
            if (urlOrUuid != null) {
                if (urlOrUuid.startsWith("http")) {
                    // Full URL (REST API format) — resize
                    urlOrUuid.replace("/original=true/", "/width=200/")
                } else {
                    // UUID (Meili format) — construct CDN URL
                    "https://image.civitai.com/xG1nkqKTMzfpXDLw6IMLCjbA7Bm7MVHJ/$urlOrUuid/width=200/$imgName"
                }
            } else null
        } else null
        
        return ModelSearchResult(
            id = id,
            name = name,
            description = null, // Not in Meili index — load via REST API
            thumbnailUrl = thumbnailUrl,
            downloadCount = downloadCount,
            favoriteCount = favoriteCount,
            tags = tags,
            creator = creator,
            versions = emptyList(), // Full versions loaded via REST API on selectModel()
            provider = ModelProvider.CIVITAI,
            civitaiType = type,
            baseModel = baseModel
        )
    }
}
