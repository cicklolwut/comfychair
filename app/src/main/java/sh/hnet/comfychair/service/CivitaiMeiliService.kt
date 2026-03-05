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
        /** Civitai image CDN base path (the key after /). */
        const val CDN_BASE = "https://image.civitai.com/xG1nkqKTMzGDvpLrqFT7WA"
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
     * @param nsfwLevels Set of allowed NSFW levels (1=PG, 2=PG-13, 4=R, 8=X, 16=XXX)
     * @param limit Number of results
     * @param offset Pagination offset
     */
    suspend fun searchModels(
        query: String,
        type: String? = null,
        baseModel: String? = null,
        sort: String = "Most Downloaded",
        nsfwLevels: Set<Int> = setOf(1, 2, 4),
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
        
        // NSFW level filter (top-level AND)
        if (nsfwLevels.isNotEmpty()) {
            filters.add("nsfwLevel IN [${nsfwLevels.sorted().joinToString(", ")}]")
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
        
        DebugLogger.d(TAG, "Searching Meili: query='$query', type=$type, baseModel=$baseModel, nsfwLevels=$nsfwLevels")
        
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
                models.add(parseHit(hit, nsfwLevels))
            } catch (e: Exception) {
                DebugLogger.w(TAG, "Failed to parse hit: ${e.message}")
            }
        }
        
        DebugLogger.d(TAG, "Found ${models.size} models (total: $totalHits)")
        
        MeiliSearchResult(models, totalHits, facets)
    }
    
    private fun parseHit(json: JSONObject, nsfwLevels: Set<Int>): ModelSearchResult {
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
        val creatorId = userObj?.optInt("id", 0)?.takeIf { it > 0 }
        
        // Parse version info
        val versionObj = json.optJSONObject("version")
        val baseModel = versionObj?.optString("baseModel")
        
        // Parse thumbnail from Meili image data.
        // Pick the first static image matching the user's NSFW levels (like Civitai's site does).
        // Meili returns image URL as just a UUID — construct the full CDN URL.
        // Build both static (anim=false) and animated (no anim=false) URLs.
        val imagesArray = json.optJSONArray("images")
        val thumbnailUrl: String?
        val animatedThumbnailUrl: String?
        if (imagesArray != null && imagesArray.length() > 0) {
            // Find first image matching NSFW levels, fall back to first image
            var selectedImg: JSONObject? = null
            for (i in 0 until imagesArray.length()) {
                val img = imagesArray.getJSONObject(i)
                if (img.optInt("nsfwLevel", 1) in nsfwLevels) {
                    selectedImg = img
                    break
                }
            }
            if (selectedImg == null) selectedImg = imagesArray.getJSONObject(0)
            
            val urlOrUuid = selectedImg.optString("url", null)
            val imgName = selectedImg.optString("name", "image.jpeg")
            val imgType = selectedImg.optString("type", "image")
            val isVideo = imgType == "video"
            val staticParams = if (isVideo) {
                "anim=false,transcode=true,width=450,original=false,optimized=true"
            } else {
                "anim=false,width=450,optimized=true"
            }
            val animatedParams = if (isVideo) staticParams else "width=450,optimized=true"
            
            if (urlOrUuid != null) {
                if (urlOrUuid.startsWith("http")) {
                    thumbnailUrl = urlOrUuid
                        .replace("/original=true/", "/$staticParams/")
                        .replace(Regex("/width=\\d+[^/]*/"), "/$staticParams/")
                    animatedThumbnailUrl = urlOrUuid
                        .replace("/original=true/", "/$animatedParams/")
                        .replace(Regex("/width=\\d+[^/]*/"), "/$animatedParams/")
                } else {
                    thumbnailUrl = "$CDN_BASE/$urlOrUuid/$staticParams/$imgName"
                    animatedThumbnailUrl = "$CDN_BASE/$urlOrUuid/$animatedParams/$imgName"
                }
            } else {
                thumbnailUrl = null
                animatedThumbnailUrl = null
            }
        } else {
            thumbnailUrl = null
            animatedThumbnailUrl = null
        }
        
        return ModelSearchResult(
            id = id,
            name = name,
            description = null, // Not in Meili index — load via REST API
            thumbnailUrl = thumbnailUrl,
            animatedThumbnailUrl = animatedThumbnailUrl,
            downloadCount = downloadCount,
            favoriteCount = favoriteCount,
            tags = tags,
            creator = creator,
            creatorId = creatorId,
            versions = emptyList(), // Full versions loaded via REST API on selectModel()
            provider = ModelProvider.CIVITAI,
            civitaiType = type,
            baseModel = baseModel
        )
    }
}
