package sh.hnet.comfychair.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import sh.hnet.comfychair.model.CommunityImage
import sh.hnet.comfychair.model.GenerationMetadata
import sh.hnet.comfychair.model.GenerationResource
import sh.hnet.comfychair.model.ImageStats
import sh.hnet.comfychair.model.ModelFile
import sh.hnet.comfychair.model.ModelProvider
import sh.hnet.comfychair.model.ModelSearchResult
import sh.hnet.comfychair.model.ModelVersion
import sh.hnet.comfychair.model.ModelVersionImage
import sh.hnet.comfychair.storage.ModelBrowserSettings
import sh.hnet.comfychair.util.DebugLogger
import java.net.URLEncoder

/**
 * Unified service for all Civitai API communication via trpc endpoints.
 * Replaces both CivitaiMeiliService (search) and CivitaiService (REST details + trpc images).
 *
 * All endpoints work without authentication. If an API key is available, it's passed
 * for potential rate limit benefits.
 */
class CivitaiTrpcService(
    private val settings: ModelBrowserSettings
) {
    companion object {
        private const val TAG = "CivitaiTrpcService"
        private const val TRPC_BASE = "https://civitai.com/api/trpc"
        const val CDN_BASE = "https://image.civitai.com/xG1nkqKTMzGDvpLrqFT7WA"
    }

    private val client = HttpModule.client

    /**
     * Result from model.getAll search/browse.
     */
    data class TrpcSearchResult(
        val models: List<ModelSearchResult>,
        val nextCursor: String?,
        val totalHits: Int = 0  // trpc doesn't provide total count
    )

    /**
     * Result from model.getById for full model details.
     */
    data class TrpcModelDetail(
        val id: Int,
        val name: String,
        val type: String,
        val description: String?,
        val creator: String,
        val creatorId: Int,
        val tags: List<String>,
        val versions: List<ModelVersion>,
        val files: Map<String, List<ModelFile>>  // versionId → files
    )

    // ========== PUBLIC API ==========

    /**
     * Search/browse models via model.getAll.
     * Works without API key — no auth required.
     *
     * @param query Search text (empty string = browse mode)
     * @param types Model types to filter (null = all)
     * @param baseModels Base models to filter (null = all)
     * @param sort Sort order
     * @param period Time period for metrics
     * @param browsingLevel Bitmask of allowed NSFW levels (1+2+4=7 for PG+PG13+R)
     * @param limit Results per page
     * @param cursor Pagination cursor from previous response
     */
    suspend fun searchModels(
        query: String = "",
        types: List<String>? = null,
        baseModels: List<String>? = null,
        sort: String = "Most Downloaded",
        period: String = "AllTime",
        browsingLevel: Int = 7,
        limit: Int = 20,
        cursor: String? = null
    ): TrpcSearchResult = withContext(Dispatchers.IO) {
        val apiKey = settings.civitaiApiKey.takeIf { it.isNotBlank() }
        
        // Build JSON input
        // Civitai always uses "published" — filters models by lastVersionAt within period,
        // then sorts by the chosen metric. "stats" periodMode is dead code on their backend.
        val periodMode = "published"
        
        val jsonBuilder = StringBuilder().apply {
            append("{\"json\":{")
            if (query.isNotBlank()) {
                append("\"query\":\"${escapeJson(query)}\",")
            }
            append("\"limit\":$limit,")
            append("\"sort\":\"$sort\",")
            append("\"period\":\"$period\",")
            append("\"periodMode\":\"$periodMode\",")
            
            // types and baseModels are arrays — only include if filtering
            if (!types.isNullOrEmpty()) {
                append("\"types\":[${types.joinToString(",") { "\"$it\"" }}],")
            }
            if (!baseModels.isNullOrEmpty()) {
                append("\"baseModels\":[${baseModels.joinToString(",") { "\"$it\"" }}],")
            }
            
            append("\"browsingLevel\":$browsingLevel,")
            
            if (cursor != null) {
                append("\"cursor\":\"$cursor\",")
            } else {
                append("\"cursor\":null,")
            }
            
            append("\"authed\":${apiKey != null}")
            append("}")
            
            // meta.values.cursor only when cursor is null
            if (cursor == null) {
                append(",\"meta\":{\"values\":{\"cursor\":[\"undefined\"]}}")
            }
            append("}")
        }
        
        val inputJson = jsonBuilder.toString()
        val encodedInput = URLEncoder.encode(inputJson, "UTF-8")
        val url = "$TRPC_BASE/model.getAll?input=$encodedInput"
        
        DebugLogger.d(TAG, "searchModels: query='$query' types=$types baseModels=$baseModels sort=$sort cursor=$cursor")
        
        val request = buildRequest(url, apiKey)
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw RuntimeException("model.getAll returned ${response.code}: $errorBody")
        }
        
        val body = response.body?.string() ?: throw RuntimeException("Empty response")
        val root = JSONObject(body)
        val resultData = root
            .getJSONObject("result")
            .getJSONObject("data")
            .getJSONObject("json")
        
        val items = resultData.optJSONArray("items") ?: JSONArray()
        val nextCursor = if (resultData.isNull("nextCursor")) null 
                         else resultData.optString("nextCursor", null)
        
        val models = mutableListOf<ModelSearchResult>()
        for (i in 0 until items.length()) {
            try {
                models.add(parseModelFromGetAll(items.getJSONObject(i), browsingLevel))
            } catch (e: Exception) {
                DebugLogger.w(TAG, "Failed to parse model item: ${e.message}")
            }
        }
        
        DebugLogger.d(TAG, "searchModels: found ${models.size} models, nextCursor=$nextCursor")
        
        TrpcSearchResult(
            models = models,
            nextCursor = nextCursor
        )
    }

    /**
     * Get full model details via model.getById.
     * Returns all versions, files, and tags with names.
     */
    suspend fun getModelDetails(modelId: Int): ModelSearchResult = withContext(Dispatchers.IO) {
        val apiKey = settings.civitaiApiKey.takeIf { it.isNotBlank() }
        
        val inputJson = "{\"json\":{\"id\":$modelId}}"
        val encodedInput = URLEncoder.encode(inputJson, "UTF-8")
        val url = "$TRPC_BASE/model.getById?input=$encodedInput"
        
        DebugLogger.d(TAG, "getModelDetails: id=$modelId")
        
        val request = buildRequest(url, apiKey)
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw RuntimeException("model.getById returned ${response.code}: $errorBody")
        }
        
        val body = response.body?.string() ?: throw RuntimeException("Empty response")
        val root = JSONObject(body)
        val json = root
            .getJSONObject("result")
            .getJSONObject("data")
            .getJSONObject("json")
        
        parseModelFromGetById(json)
    }

    /**
     * Get community images for a model version via image.getImagesAsPostsInfinite.
     * Already implemented in old CivitaiService — moved here.
     */
    suspend fun getModelImages(
        modelVersionId: String,
        modelId: String? = null,
        limit: Int = 20,
        cursor: String? = null,
        browsingLevel: Int? = null,
        sort: String = "Most Reactions"
    ): Pair<List<CommunityImage>, String?> = withContext(Dispatchers.IO) {
        val apiKey = settings.civitaiApiKey.takeIf { it.isNotBlank() }

        val inputJson = buildString {
            append("{\"json\":{")
            append("\"period\":\"AllTime\",")
            append("\"periodMode\":\"published\",")
            append("\"sort\":\"$sort\",")
            append("\"withMeta\":true,")
            append("\"requiringMeta\":false,")
            append("\"modelVersionId\":$modelVersionId,")
            if (modelId != null) {
                append("\"modelId\":$modelId,")
            }
            append("\"hidden\":false,")
            append("\"limit\":$limit,")
            append("\"browsingLevel\":${browsingLevel ?: 31},")
            if (cursor != null) {
                append("\"cursor\":\"$cursor\",")
            } else {
                append("\"cursor\":null,")
            }
            append("\"authed\":${apiKey != null}")
            append("}")
            if (cursor == null) {
                append(",\"meta\":{\"values\":{\"cursor\":[\"undefined\"]}}")
            }
            append("}")
        }

        val encodedInput = URLEncoder.encode(inputJson, "UTF-8")
        val url = "$TRPC_BASE/image.getImagesAsPostsInfinite?input=$encodedInput"

        DebugLogger.d(TAG, "getModelImages: versionId=$modelVersionId sort=$sort cursor=$cursor")

        val request = buildRequest(url, apiKey)
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw RuntimeException("image.getImagesAsPostsInfinite returned ${response.code}: $errorBody")
        }

        val body = response.body?.string() ?: throw RuntimeException("Empty response")
        val root = JSONObject(body)
        val resultData = root
            .getJSONObject("result")
            .getJSONObject("data")
            .getJSONObject("json")

        val posts = resultData.optJSONArray("items") ?: return@withContext Pair(emptyList(), null)
        val nextCursor = if (resultData.isNull("nextCursor")) null
                         else resultData.optString("nextCursor", null)

        // Flatten posts → images (each post can contain multiple images)
        val images = mutableListOf<CommunityImage>()
        for (i in 0 until posts.length()) {
            val post = posts.getJSONObject(i)
            val postImages = post.optJSONArray("images") ?: continue
            for (j in 0 until postImages.length()) {
                try {
                    images.add(parseCommunityImage(postImages.getJSONObject(j)))
                } catch (e: Exception) {
                    DebugLogger.w(TAG, "Failed to parse community image: ${e.message}")
                }
            }
        }

        DebugLogger.d(TAG, "getModelImages: found ${images.size} images from ${posts.length()} posts")
        Pair(images, nextCursor)
    }

    /**
     * Resolve tag IDs to names. Fetches from API if not cached.
     * Tag cache is append-only (mappings are immutable).
     */
    suspend fun resolveTagNames(ids: List<Int>): Map<Int, String> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyMap()
        
        val cached = settings.tagCache
        val missing = ids.filter { it !in cached }
        
        if (missing.isEmpty()) {
            return@withContext ids.associateWith { cached[it]!! }
        }
        
        // Fetch top model tags and merge into cache
        try {
            val fetched = fetchTags()
            settings.updateTagCache(fetched)
            
            // Return what we can resolve
            val updated = settings.tagCache
            ids.associateWith { updated[it] ?: "tag:$it" }
        } catch (e: Exception) {
            DebugLogger.w(TAG, "Failed to fetch tags: ${e.message}")
            // Return what we have, placeholders for missing
            ids.associateWith { cached[it] ?: "tag:$it" }
        }
    }

    // ========== INTERNAL HELPERS ==========

    /**
     * Fetch top model tags via tag.getAll.
     */
    private suspend fun fetchTags(): Map<Int, String> {
        val apiKey = settings.civitaiApiKey.takeIf { it.isNotBlank() }
        
        val inputJson = "{\"json\":{\"limit\":200,\"entityType\":[\"Model\"]}}"
        val encodedInput = URLEncoder.encode(inputJson, "UTF-8")
        val url = "$TRPC_BASE/tag.getAll?input=$encodedInput"
        
        val request = buildRequest(url, apiKey)
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw RuntimeException("tag.getAll returned ${response.code}")
        }
        
        val body = response.body?.string() ?: throw RuntimeException("Empty response")
        val root = JSONObject(body)
        val items = root
            .getJSONObject("result")
            .getJSONObject("data")
            .getJSONObject("json")
            .optJSONArray("items") ?: return emptyMap()
        
        val result = mutableMapOf<Int, String>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val id = item.optInt("id", 0)
            val name = item.optString("name", null)
            if (id > 0 && name != null) {
                result[id] = name
            }
        }
        
        DebugLogger.d(TAG, "fetchTags: fetched ${result.size} tags")
        return result
    }

    /**
     * Build an HTTP request with optional auth headers.
     */
    private fun buildRequest(url: String, apiKey: String?): Request {
        return Request.Builder()
            .url(url)
            .apply {
                if (apiKey != null) {
                    header("Authorization", "Bearer $apiKey")
                    header("Cookie", "__Secure-civitai-token=$apiKey")
                }
            }
            .get()
            .build()
    }

    /**
     * Parse a model from model.getAll response.
     * Returns latest version only (in `version` field, singular).
     */
    private fun parseModelFromGetAll(json: JSONObject, browsingLevel: Int): ModelSearchResult {
        val id = json.optInt("id", 0)
        val name = json.optString("name", "Unknown")
        val type = json.optString("type", "")
        val nsfwLevel = json.optInt("nsfwLevel", 1)
        
        // User info
        val userObj = json.optJSONObject("user")
        val creator = userObj?.optString("username")
        val creatorId = userObj?.optInt("id", 0)?.takeIf { it > 0 }
        
        // Latest version (singular `version` field)
        val versionObj = json.optJSONObject("version")
        val versions = if (versionObj != null) {
            listOf(parseVersionFromGetAll(versionObj))
        } else {
            emptyList()
        }
        
        // Rank stats
        val rankObj = json.optJSONObject("rank")
        val downloadCount = rankObj?.optLong("downloadCount")
        val favoriteCount = rankObj?.optLong("thumbsUpCount")
        
        // Tags — IDs only, resolve later
        val tagsArray = json.optJSONArray("tags")
        val tagIds = mutableListOf<Int>()
        if (tagsArray != null) {
            for (i in 0 until tagsArray.length()) {
                tagIds.add(tagsArray.getInt(i))
            }
        }
        // Resolve tags from cache (synchronously — cache is in memory)
        val tagCache = settings.tagCache
        val tags = tagIds.mapNotNull { tagCache[it] }
        
        // Cover image — pick first matching browsingLevel
        val imagesArray = json.optJSONArray("images")
        val coverImage = selectCoverImage(imagesArray, browsingLevel)
        val (thumbnailUrl, animatedThumbnailUrl) = if (coverImage != null) {
            buildCdnUrls(coverImage)
        } else {
            Pair(null, null)
        }
        
        // Base model from version
        val baseModel = versionObj?.optString("baseModel", null)
        
        return ModelSearchResult(
            id = id.toString(),
            name = name,
            description = null,  // Not in getAll — load via getById
            thumbnailUrl = thumbnailUrl,
            animatedThumbnailUrl = animatedThumbnailUrl,
            downloadCount = downloadCount,
            favoriteCount = favoriteCount,
            tags = tags,
            creator = creator,
            creatorId = creatorId,
            versions = versions,
            provider = ModelProvider.CIVITAI,
            civitaiType = type,
            baseModel = baseModel
        )
    }

    /**
     * Parse version from getAll response (minimal info).
     */
    private fun parseVersionFromGetAll(json: JSONObject): ModelVersion {
        val id = json.optInt("id", 0).toString()
        val name = json.optString("name", "Unknown")
        val baseModel = json.optString("baseModel", null)
        
        // Trained words
        val trainedWordsArray = json.optJSONArray("trainedWords")
        val trainedWords = mutableListOf<String>()
        if (trainedWordsArray != null) {
            for (i in 0 until trainedWordsArray.length()) {
                trainedWords.add(trainedWordsArray.getString(i))
            }
        }
        
        // getAll doesn't include files — that comes from getById
        return ModelVersion(
            id = id,
            name = name,
            baseModel = baseModel,
            downloadUrl = "https://civitai.com/api/download/models/$id?type=Model&format=SafeTensor",
            filename = "",
            sizeKB = null,
            files = emptyList(),
            images = emptyList(),
            trainedWords = trainedWords,
            description = null
        )
    }

    /**
     * Parse a model from model.getById response.
     * Returns full details with all versions.
     */
    private fun parseModelFromGetById(json: JSONObject): ModelSearchResult {
        val id = json.optInt("id", 0)
        val name = json.optString("name", "Unknown")
        val type = json.optString("type", "")
        val description = json.optString("description", null)
        
        // User info
        val userObj = json.optJSONObject("user")
        val creator = userObj?.optString("username")
        val creatorId = userObj?.optInt("id", 0)?.takeIf { it > 0 }
        
        // Tags with names from tagsOnModels
        val tagsOnModels = json.optJSONArray("tagsOnModels")
        val tags = mutableListOf<String>()
        if (tagsOnModels != null) {
            for (i in 0 until tagsOnModels.length()) {
                val tagWrapper = tagsOnModels.getJSONObject(i)
                val tagObj = tagWrapper.optJSONObject("tag")
                tagObj?.optString("name", null)?.let { tags.add(it) }
                
                // Update cache as side effect
                val tagId = tagObj?.optInt("id", 0) ?: 0
                val tagName = tagObj?.optString("name", null)
                if (tagId > 0 && tagName != null) {
                    settings.updateTagCache(mapOf(tagId to tagName))
                }
            }
        }
        
        // All versions
        val versionsArray = json.optJSONArray("modelVersions")
        val versions = mutableListOf<ModelVersion>()
        if (versionsArray != null) {
            for (i in 0 until versionsArray.length()) {
                try {
                    versions.add(parseVersionFromGetById(versionsArray.getJSONObject(i)))
                } catch (e: Exception) {
                    DebugLogger.w(TAG, "Failed to parse version: ${e.message}")
                }
            }
        }
        
        // Stats from first version's stats or model-level
        val statsObj = json.optJSONObject("stats")
        val downloadCount = statsObj?.optLong("downloadCount")
        val favoriteCount = statsObj?.optLong("thumbsUpCount") 
            ?: statsObj?.optLong("favoriteCount")
        
        // Base model from first version
        val baseModel = versions.firstOrNull()?.baseModel
        
        // Thumbnail from first version's first image (if available)
        // Note: getById's modelVersions[].images is often empty — fallback to null
        val thumbnailUrl: String? = null
        val animatedThumbnailUrl: String? = null
        
        return ModelSearchResult(
            id = id.toString(),
            name = name,
            description = description,
            thumbnailUrl = thumbnailUrl,
            animatedThumbnailUrl = animatedThumbnailUrl,
            downloadCount = downloadCount,
            favoriteCount = favoriteCount,
            tags = tags,
            creator = creator,
            creatorId = creatorId,
            versions = versions,
            provider = ModelProvider.CIVITAI,
            civitaiType = type,
            baseModel = baseModel
        )
    }

    /**
     * Parse version from getById response (full info with files).
     */
    private fun parseVersionFromGetById(json: JSONObject): ModelVersion {
        val id = json.optInt("id", 0).toString()
        val name = json.optString("name", "Unknown")
        val baseModel = json.optString("baseModel", null)
        val description = json.optString("description", null)
        
        // Files
        val filesArray = json.optJSONArray("files")
        val files = mutableListOf<ModelFile>()
        var primaryDownloadUrl = "https://civitai.com/api/download/models/$id?type=Model&format=SafeTensor"
        var primaryFilename = ""
        var primarySizeKB = 0L
        
        if (filesArray != null) {
            for (i in 0 until filesArray.length()) {
                val fileObj = filesArray.getJSONObject(i)
                val filename = fileObj.optString("name", "")
                val sizeKB = fileObj.optDouble("sizeKB", 0.0).toLong()
                val downloadUrl = fileObj.optString("downloadUrl", "")
                    .ifBlank { "https://civitai.com/api/download/models/$id?type=Model&format=SafeTensor" }
                val isPrimary = fileObj.optBoolean("primary", false)
                
                files.add(
                    ModelFile(
                        filename = filename,
                        downloadUrl = downloadUrl,
                        sizeBytes = sizeKB * 1024
                    )
                )
                
                if (isPrimary || i == 0) {
                    primaryDownloadUrl = downloadUrl
                    primaryFilename = filename
                    primarySizeKB = sizeKB
                }
            }
        }
        
        // Images — often empty in getById, but parse if present
        val imagesArray = json.optJSONArray("images")
        val images = mutableListOf<ModelVersionImage>()
        if (imagesArray != null) {
            for (i in 0 until imagesArray.length()) {
                val imgObj = imagesArray.getJSONObject(i)
                images.add(
                    ModelVersionImage(
                        url = imgObj.optString("url", ""),
                        nsfwLevel = imgObj.optInt("nsfwLevel", 1),
                        width = imgObj.optInt("width", 0),
                        height = imgObj.optInt("height", 0)
                    )
                )
            }
        }
        
        // Trained words
        val trainedWordsArray = json.optJSONArray("trainedWords")
        val trainedWords = mutableListOf<String>()
        if (trainedWordsArray != null) {
            for (i in 0 until trainedWordsArray.length()) {
                trainedWords.add(trainedWordsArray.getString(i))
            }
        }
        
        return ModelVersion(
            id = id,
            name = name,
            baseModel = baseModel,
            downloadUrl = primaryDownloadUrl,
            filename = primaryFilename,
            sizeKB = primarySizeKB,
            files = files,
            images = images,
            trainedWords = trainedWords,
            description = description
        )
    }

    /**
     * Select the first cover image matching the user's browsingLevel.
     */
    private fun selectCoverImage(images: JSONArray?, browsingLevel: Int): JSONObject? {
        if (images == null || images.length() == 0) return null
        
        for (i in 0 until images.length()) {
            val img = images.getJSONObject(i)
            val imgNsfw = img.optInt("nsfwLevel", 1)
            // Check if image's NSFW level is within allowed levels
            if ((imgNsfw and browsingLevel) == imgNsfw) {
                return img
            }
        }
        
        // No image matches the user's NSFW level — return null (no thumbnail)
        return null
    }

    /**
     * Build CDN URLs from an image object.
     * Returns (staticUrl, animatedUrl).
     */
    private fun buildCdnUrls(img: JSONObject): Pair<String?, String?> {
        val uuid = img.optString("url", "").takeIf { it.isNotBlank() } ?: return Pair(null, null)
        val imgId = img.optInt("id", 0)
        val name = if (img.isNull("name")) "$imgId.jpg" else img.optString("name", "$imgId.jpg")
        val imgType = img.optString("type", "image")
        val isVideo = imgType == "video"
        
        val staticParams = if (isVideo) {
            "anim=false,transcode=true,width=450,original=false,optimized=true"
        } else {
            "anim=false,width=450,optimized=true"
        }
        val animatedParams = if (isVideo) staticParams else "width=450,optimized=true"
        
        val staticUrl = "$CDN_BASE/$uuid/$staticParams/$name"
        val animatedUrl = "$CDN_BASE/$uuid/$animatedParams/$name"
        
        return Pair(staticUrl, animatedUrl)
    }

    /**
     * Parse a community image from getImagesAsPostsInfinite response.
     */
    private fun parseCommunityImage(json: JSONObject): CommunityImage {
        val id = json.optLong("id", 0)
        val uuid = json.optString("url", "")
        val name = if (json.isNull("name")) "$id.jpg" else json.optString("name", "$id.jpg")
        val width = json.optInt("width", 0)
        val height = json.optInt("height", 0)
        val nsfwLevel = json.optInt("nsfwLevel", 1)
        
        val isVideo = json.optString("type", "image") == "video"
        val staticParams = if (isVideo) {
            "anim=false,transcode=true,width=450,original=false,optimized=true"
        } else {
            "anim=false,width=450,optimized=true"
        }
        val animatedParams = if (isVideo) staticParams else "width=450,optimized=true"
        
        val thumbnailUrl = "$CDN_BASE/$uuid/$staticParams/$name"
        val animatedThumbnailUrl = "$CDN_BASE/$uuid/$animatedParams/$name"
        val fullUrl = "$CDN_BASE/$uuid/original=true/$name"
        
        // Parse stats
        val statsObj = json.optJSONObject("stats")
        val stats = if (statsObj != null) {
            ImageStats(
                likeCount = statsObj.optInt("likeCountAllTime", 0),
                heartCount = statsObj.optInt("heartCountAllTime", 0),
                commentCount = statsObj.optInt("commentCountAllTime", 0)
            )
        } else null
        
        // Parse generation metadata
        val metaObj = if (!json.isNull("meta")) json.optJSONObject("meta") else null
        val meta = if (metaObj != null) parseGenerationMetadata(metaObj) else null
        
        return CommunityImage(
            id = id,
            url = fullUrl,
            thumbnailUrl = thumbnailUrl,
            animatedThumbnailUrl = animatedThumbnailUrl,
            width = width,
            height = height,
            nsfwLevel = nsfwLevel,
            type = if (isVideo) "video" else "image",
            stats = stats,
            meta = meta
        )
    }

    private fun parseGenerationMetadata(json: JSONObject): GenerationMetadata {
        val actualMeta = json.optJSONObject("meta") ?: json
        
        val prompt = actualMeta.optString("prompt", null)
        val negativePrompt = actualMeta.optString("negativePrompt", null)
            ?: actualMeta.optString("negative_prompt", null)
        val sampler = actualMeta.optString("sampler", null)
        val steps = if (actualMeta.has("steps")) actualMeta.optInt("steps") else null
        val cfgScale = if (actualMeta.has("cfgScale")) actualMeta.optDouble("cfgScale") else null
        val seed = if (actualMeta.has("seed")) actualMeta.optLong("seed") else null
        val baseModel = actualMeta.optString("baseModel", null)
        
        val resources = mutableListOf<GenerationResource>()
        
        val civitaiResourcesArray = actualMeta.optJSONArray("civitaiResources")
        if (civitaiResourcesArray != null) {
            for (i in 0 until civitaiResourcesArray.length()) {
                val resObj = civitaiResourcesArray.getJSONObject(i)
                resources.add(
                    GenerationResource(
                        name = null,
                        type = resObj.optString("type", null),
                        weight = if (resObj.has("weight")) resObj.optDouble("weight") else null,
                        modelVersionId = if (resObj.has("modelVersionId")) resObj.optLong("modelVersionId") else null
                    )
                )
            }
        }
        
        val resourcesArray = actualMeta.optJSONArray("resources")
        if (resourcesArray != null) {
            for (i in 0 until resourcesArray.length()) {
                val resObj = resourcesArray.getJSONObject(i)
                resources.add(
                    GenerationResource(
                        name = resObj.optString("name", null),
                        type = resObj.optString("type", null),
                        weight = if (resObj.has("weight")) resObj.optDouble("weight") else null,
                        modelVersionId = null
                    )
                )
            }
        }
        
        return GenerationMetadata(
            prompt = prompt,
            negativePrompt = negativePrompt,
            sampler = sampler,
            steps = steps,
            cfgScale = cfgScale,
            seed = seed,
            baseModel = baseModel,
            resources = resources
        )
    }

    /**
     * Escape JSON string content.
     */
    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
