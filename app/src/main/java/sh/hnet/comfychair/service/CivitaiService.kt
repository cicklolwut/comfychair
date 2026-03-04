package sh.hnet.comfychair.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import sh.hnet.comfychair.model.ModelProvider
import sh.hnet.comfychair.model.ModelSearchResult
import sh.hnet.comfychair.model.ModelVersion
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
    suspend fun searchModels(
        query: String,
        types: List<String>? = null,
        limit: Int = 20,
        nsfw: Boolean = false
    ): List<ModelSearchResult> = withContext(Dispatchers.IO) {
        val apiKey = settings.civitaiApiKey
        require(apiKey.isNotBlank()) { "Civitai API key not configured" }

        val url = buildString {
            append("$BASE_URL/models?query=$query&limit=$limit&nsfw=$nsfw")
            types?.forEach { type ->
                append("&types=$type")
            }
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

        // Get thumbnail from first version's first image
        val thumbnailUrl = if (versionsArray != null && versionsArray.length() > 0) {
            val firstVersion = versionsArray.getJSONObject(0)
            val images = firstVersion.optJSONArray("images")
            if (images != null && images.length() > 0) {
                images.getJSONObject(0).optString("url", null)
            } else null
        } else null

        return ModelSearchResult(
            id = id,
            name = name,
            description = description,
            thumbnailUrl = thumbnailUrl,
            downloadCount = downloadCount,
            favoriteCount = favoriteCount,
            tags = tags,
            creator = creator,
            versions = versions,
            provider = ModelProvider.CIVITAI
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

        return ModelVersion(
            id = id,
            name = name,
            baseModel = baseModel,
            downloadUrl = primaryDownloadUrl,
            filename = primaryFilename,
            sizeKB = primarySizeKB,
            files = files
        )
    }
}
