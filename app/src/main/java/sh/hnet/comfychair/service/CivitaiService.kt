package sh.hnet.comfychair.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import sh.hnet.comfychair.model.CommunityImage
import sh.hnet.comfychair.model.GenerationMetadata
import sh.hnet.comfychair.model.GenerationResource
import sh.hnet.comfychair.model.ImageStats
import sh.hnet.comfychair.model.ModelProvider
import sh.hnet.comfychair.model.ModelSearchResult
import sh.hnet.comfychair.model.ModelVersion
import sh.hnet.comfychair.model.ModelVersionImage
import sh.hnet.comfychair.model.ModelFile
import sh.hnet.comfychair.storage.ModelBrowserSettings
import sh.hnet.comfychair.util.DebugLogger
import java.util.concurrent.TimeUnit

/**
 * Service for interacting with the Civitai API.
 * API docs: https://developer.civitai.com/docs/api/public-rest
 */
class CivitaiService(
    private val settings: ModelBrowserSettings
) {
    companion object {
        private const val BASE_URL = "https://civitai.com/api/v1"
        private const val TAG = "CivitaiService"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Search for models on Civitai.
     * @param query Search query
     * @param types Model types to filter (e.g., "Checkpoint", "LORA")
     * @param limit Number of results to return
     * @param nsfw Whether to include NSFW models
     */
    /**
     * Search for models on Civitai.
     * @param query Search query
     * @param types Model types to filter (e.g., "Checkpoint", "LORA")
     * @param limit Number of results to return
     * @param nsfw Whether to include NSFW models
     * @param sort Sort order: "Highest Rated", "Most Downloaded", "Newest"
     * @param period Time period: "AllTime", "Year", "Month", "Week", "Day"
     * @param baseModel Base model filter: "Illustrious", "NoobAI", "SDXL 1.0", etc.
     */
    suspend fun searchModels(
        query: String,
        types: List<String>? = null,
        limit: Int = 20,
        nsfw: Boolean = false,
        sort: String? = null,
        period: String? = null,
        baseModel: String? = null
    ): List<ModelSearchResult> = withContext(Dispatchers.IO) {
        val apiKey = settings.civitaiApiKey
        require(apiKey.isNotBlank()) { "Civitai API key not configured" }

        val url = buildString {
            append("$BASE_URL/models?query=$query&limit=$limit&nsfw=$nsfw")
            types?.forEach { type ->
                append("&types=$type")
            }
            if (!sort.isNullOrBlank()) append("&sort=${java.net.URLEncoder.encode(sort, "UTF-8")}")
            if (!period.isNullOrBlank()) append("&period=$period")
            if (!baseModel.isNullOrBlank()) append("&baseModels=${java.net.URLEncoder.encode(baseModel, "UTF-8")}")
        }

        DebugLogger.d(TAG, "Searching Civitai: $url")

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw RuntimeException("Civitai API returned ${response.code}: $errorBody")
        }

        val body = response.body?.string() ?: throw RuntimeException("Empty response")
        val json = JSONObject(body)
        val items = json.optJSONArray("items") ?: return@withContext emptyList()

        val results = mutableListOf<ModelSearchResult>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            try {
                results.add(parseModelResult(item))
            } catch (e: Exception) {
                DebugLogger.w(TAG, "Failed to parse model result: ${e.message}")
            }
        }

        DebugLogger.d(TAG, "Found ${results.size} models")
        results
    }

    /**
     * Get details for a specific model by ID.
     */
    suspend fun getModelDetails(modelId: String): ModelSearchResult = withContext(Dispatchers.IO) {
        val apiKey = settings.civitaiApiKey
        require(apiKey.isNotBlank()) { "Civitai API key not configured" }

        val url = "$BASE_URL/models/$modelId"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw RuntimeException("Civitai API returned ${response.code}: $errorBody")
        }

        val body = response.body?.string() ?: throw RuntimeException("Empty response")
        val json = JSONObject(body)
        parseModelResult(json)
    }

    private fun parseModelResult(json: JSONObject): ModelSearchResult {
        val id = json.optString("id", "")
        val name = json.optString("name", "Unknown")
        val description = json.optString("description", null)
        val type = json.optString("type", "")

        // Parse tags
        val tagsArray = json.optJSONArray("tags")
        val tags = mutableListOf<String>()
        if (tagsArray != null) {
            for (i in 0 until tagsArray.length()) {
                tags.add(tagsArray.getString(i))
            }
        }

        // Parse creator
        val creatorObj = json.optJSONObject("creator")
        val creator = creatorObj?.optString("username")

        // Parse stats
        val statsObj = json.optJSONObject("stats")
        val downloadCount = statsObj?.optLong("downloadCount")
        val favoriteCount = statsObj?.optLong("favoriteCount")

        // Parse versions
        val versionsArray = json.optJSONArray("modelVersions")
        val versions = mutableListOf<ModelVersion>()
        if (versionsArray != null) {
            for (i in 0 until versionsArray.length()) {
                val versionObj = versionsArray.getJSONObject(i)
                try {
                    versions.add(parseModelVersion(versionObj))
                } catch (e: Exception) {
                    DebugLogger.w(TAG, "Failed to parse version: ${e.message}")
                }
            }
        }

        // Get thumbnail from first version's first image.
        // Use same CDN params as Civitai's site: anim=false, width=450, optimized=true.
        val thumbnailUrl: String?
        val animatedThumbnailUrl: String?
        if (versionsArray != null && versionsArray.length() > 0) {
            val firstVersion = versionsArray.getJSONObject(0)
            val images = firstVersion.optJSONArray("images")
            if (images != null && images.length() > 0) {
                val imgObj = images.getJSONObject(0)
                val originalUrl = imgObj.optString("url", null)
                val imgType = imgObj.optString("type", "image")
                val isVid = imgType == "video"
                val staticP = if (isVid) "anim=false,transcode=true,width=450,original=false,optimized=true"
                              else "anim=false,width=450,optimized=true"
                val animP = if (isVid) staticP else "width=450,optimized=true"
                thumbnailUrl = originalUrl?.let { rewriteCdnUrl(it, staticP) }
                animatedThumbnailUrl = originalUrl?.let { rewriteCdnUrl(it, animP) }
            } else {
                thumbnailUrl = null
                animatedThumbnailUrl = null
            }
        } else {
            thumbnailUrl = null
            animatedThumbnailUrl = null
        }

        // Get baseModel from first version
        val baseModel = if (versionsArray != null && versionsArray.length() > 0) {
            versionsArray.getJSONObject(0).optString("baseModel", null)
        } else null

        return ModelSearchResult(
            id = id,
            name = name,
            description = description,
            thumbnailUrl = thumbnailUrl,
            animatedThumbnailUrl = animatedThumbnailUrl,
            downloadCount = downloadCount,
            favoriteCount = favoriteCount,
            tags = tags,
            creator = creator,
            versions = versions,
            provider = ModelProvider.CIVITAI,
            civitaiType = type,
            baseModel = baseModel
        )
    }

    private fun parseModelVersion(json: JSONObject): ModelVersion {
        val id = json.optString("id", "")
        val name = json.optString("name", "Unknown")
        val baseModel = json.optString("baseModel", null)

        // Parse files
        val filesArray = json.optJSONArray("files")
        val files = mutableListOf<ModelFile>()
        var primaryDownloadUrl = ""
        var primaryFilename = ""
        var primarySizeKB = 0L

        if (filesArray != null && filesArray.length() > 0) {
            for (i in 0 until filesArray.length()) {
                val fileObj = filesArray.getJSONObject(i)
                val filename = fileObj.optString("name", "")
                val sizeKB = fileObj.optLong("sizeKB", 0)
                val downloadUrl = fileObj.optString("downloadUrl", "")
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

        // Parse images
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

        // Parse trained words
        val trainedWordsArray = json.optJSONArray("trainedWords")
        val trainedWords = mutableListOf<String>()
        if (trainedWordsArray != null) {
            for (i in 0 until trainedWordsArray.length()) {
                trainedWords.add(trainedWordsArray.getString(i))
            }
        }

        // Version description
        val description = json.optString("description", null)

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
     * Get community images for a specific model version via Civitai trpc endpoint.
     * Uses image.getInfinite which provides richer stats (AllTime counts) and integer cursors.
     *
     * @param modelVersionId The model version ID
     * @param limit Number of images to return
     * @param cursor Pagination cursor (integer serialized as String, null for first page)
     * @param browsingLevel Kept for API compatibility but unused — trpc uses tag-based filtering
     * @param prioritizedUserIds Optional list of user IDs whose images appear first (e.g. model creator)
     * @return Pair of images list and next cursor (integer serialized as String, or null)
     */
    suspend fun getModelImages(
        modelVersionId: String,
        limit: Int = 20,
        cursor: String? = null,
        browsingLevel: Int? = null,
        prioritizedUserIds: List<Int> = emptyList()
    ): Pair<List<CommunityImage>, String?> = withContext(Dispatchers.IO) {
        val apiKey = settings.civitaiApiKey
        require(apiKey.isNotBlank()) { "Civitai API key not configured" }

        // Build the trpc input JSON.
        // When cursor is null, include meta.values.cursor=["undefined"] as required by trpc.
        // When cursor is an integer, omit meta.values so the server treats it as a real cursor.
        val inputJson = buildString {
            append("{\"json\":{")
            append("\"modelVersionId\":$modelVersionId,")
            if (prioritizedUserIds.isNotEmpty()) {
                append("\"prioritizedUserIds\":[${prioritizedUserIds.joinToString(",")}],")
            } else {
                append("\"prioritizedUserIds\":[],")
            }
            append("\"period\":\"AllTime\",")
            append("\"sort\":\"Most Reactions\",")
            append("\"limit\":$limit,")
            append("\"pending\":true,")
            append("\"include\":[],")
            append("\"withMeta\":true,")
            append("\"excludedTagIds\":[],")
            append("\"disablePoi\":true,")
            append("\"disableMinor\":true,")
            if (cursor != null) {
                // Pass as integer (no quotes)
                append("\"cursor\":$cursor,")
            } else {
                append("\"cursor\":null,")
            }
            append("\"authed\":true")
            append("}")
            if (cursor == null) {
                // Required by trpc when cursor is undefined/null
                append(",\"meta\":{\"values\":{\"cursor\":[\"undefined\"]}}")
            }
            append("}")
        }

        val encodedInput = java.net.URLEncoder.encode(inputJson, "UTF-8")
        val url = "https://civitai.com/api/trpc/image.getInfinite?input=$encodedInput"

        DebugLogger.d(TAG, "Fetching trpc images for version $modelVersionId (cursor=$cursor)")

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            // trpc endpoints may also require cookie-based auth
            .header("Cookie", "__Secure-civitai-token=$apiKey")
            .get()
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw RuntimeException("Civitai trpc returned ${response.code}: $errorBody")
        }

        val body = response.body?.string() ?: throw RuntimeException("Empty response")
        val root = JSONObject(body)
        val resultData = root
            .getJSONObject("result")
            .getJSONObject("data")
            .getJSONObject("json")

        val items = resultData.optJSONArray("items") ?: return@withContext Pair(emptyList(), null)
        val nextCursor = if (resultData.isNull("nextCursor")) null
                         else resultData.optString("nextCursor", null)

        val images = mutableListOf<CommunityImage>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            try {
                images.add(parseCommunityImage(item))
            } catch (e: Exception) {
                DebugLogger.w(TAG, "Failed to parse image: ${e.message}")
            }
        }

        DebugLogger.d(TAG, "Found ${images.size} images (nextCursor=$nextCursor)")
        Pair(images, nextCursor)
    }

    private fun parseCommunityImage(json: JSONObject): CommunityImage {
        val id = json.optLong("id", 0)
        // trpc returns `url` as a UUID only — not a full CDN URL.
        val uuid = json.optString("url", "")
        val name = json.optString("name", "$id.jpg")
        val width = json.optInt("width", 0)
        val height = json.optInt("height", 0)
        // trpc returns nsfwLevel as an integer directly (e.g. 1, 4, 8)
        val nsfwLevel = json.optInt("nsfwLevel", 1)

        // Build CDN URLs from UUID.
        // Videos always need transcode=true; without it the CDN returns nothing (empty placeholder bug).
        val isVideo = json.optString("type", "image") == "video"
        val staticParams = if (isVideo) {
            "anim=false,transcode=true,width=450,original=false,optimized=true"
        } else {
            "anim=false,width=450,optimized=true"
        }
        val animatedParams = if (isVideo) {
            // Videos can't animate in AsyncImage — always use static frame
            staticParams
        } else {
            "width=450,optimized=true"
        }

        val cdnBase = CivitaiMeiliService.CDN_BASE
        val thumbnailUrl = "$cdnBase/$uuid/$staticParams/$name"
        val animatedThumbnailUrl = "$cdnBase/$uuid/$animatedParams/$name"
        // Full-resolution URL for the image viewer
        val fullUrl = "$cdnBase/$uuid/original=true/$name"

        // Parse stats — trpc uses AllTime-suffixed fields for reliable counts.
        val statsObj = json.optJSONObject("stats")
        val stats = if (statsObj != null) {
            ImageStats(
                likeCount = statsObj.optInt("likeCountAllTime", 0),
                heartCount = statsObj.optInt("heartCountAllTime", 0),
                commentCount = statsObj.optInt("commentCountAllTime", 0)
            )
        } else null

        // Parse generation metadata — null when withMeta=false (current default)
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

    /**
     * Rewrite a Civitai CDN URL to use specific transform params.
     * Handles both full URLs (from REST API) and the original=true default.
     */
    private fun rewriteCdnUrl(url: String, params: String): String {
        return url
            .replace("/original=true/", "/$params/")
            .replace(Regex("/width=\\d+[^/]*/"), "/$params/")
            .replace(Regex("/anim=[^/]+/"), "/$params/")
    }

    private fun parseGenerationMetadata(json: JSONObject): GenerationMetadata {
        // Handle nested meta (sometimes meta.meta, sometimes direct)
        val actualMeta = json.optJSONObject("meta") ?: json

        val prompt = actualMeta.optString("prompt", null)
        val negativePrompt = actualMeta.optString("negativePrompt", null)
            ?: actualMeta.optString("negative_prompt", null)
        val sampler = actualMeta.optString("sampler", null)
        val steps = if (actualMeta.has("steps")) actualMeta.optInt("steps") else null
        val cfgScale = if (actualMeta.has("cfgScale")) actualMeta.optDouble("cfgScale") else null
        val seed = if (actualMeta.has("seed")) actualMeta.optLong("seed") else null
        val baseModel = actualMeta.optString("baseModel", null)

        // Parse resources
        val resources = mutableListOf<GenerationResource>()

        // Check for civitaiResources array
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

        // Check for resources array (alternative format)
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
}
