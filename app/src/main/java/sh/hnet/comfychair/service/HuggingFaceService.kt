package sh.hnet.comfychair.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import sh.hnet.comfychair.model.ModelProvider
import sh.hnet.comfychair.model.ModelSearchResult
import sh.hnet.comfychair.model.ModelVersion
import sh.hnet.comfychair.model.ModelFile
import sh.hnet.comfychair.storage.ModelBrowserSettings
import sh.hnet.comfychair.util.DebugLogger
import java.util.concurrent.TimeUnit

/**
 * Service for interacting with the HuggingFace API.
 */
class HuggingFaceService(
    private val settings: ModelBrowserSettings
) {
    companion object {
        private const val BASE_URL = "https://huggingface.co/api"
        private const val TAG = "HuggingFaceService"
    }

    private val client = HttpModule.client

    /**
     * Search for models on HuggingFace.
     * @param query Search query
     * @param filter Filter by model type (e.g., "diffusers", "safetensors")
     * @param limit Number of results to return
     */
    suspend fun searchModels(
        query: String,
        filter: String? = null,
        limit: Int = 20
    ): List<ModelSearchResult> = withContext(Dispatchers.IO) {
        val apiKey = settings.huggingfaceApiKey
        require(apiKey.isNotBlank()) { "HuggingFace API key not configured" }

        val url = buildString {
            append("$BASE_URL/models?search=$query&limit=$limit")
            if (filter != null) {
                append("&filter=$filter")
            }
        }

        DebugLogger.d(TAG, "Searching HuggingFace: $url")

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw RuntimeException("HuggingFace API returned ${response.code}: $errorBody")
        }

        val body = response.body?.string() ?: throw RuntimeException("Empty response")
        val jsonArray = JSONArray(body)

        val results = mutableListOf<ModelSearchResult>()
        for (i in 0 until minOf(jsonArray.length(), limit)) {
            val item = jsonArray.getJSONObject(i)
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
     * Get file list for a specific model repo.
     */
    suspend fun getModelFiles(modelId: String): List<ModelFile> = withContext(Dispatchers.IO) {
        val apiKey = settings.huggingfaceApiKey
        require(apiKey.isNotBlank()) { "HuggingFace API key not configured" }

        val url = "$BASE_URL/models/$modelId/tree/main"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw RuntimeException("HuggingFace API returned ${response.code}: $errorBody")
        }

        val body = response.body?.string() ?: throw RuntimeException("Empty response")
        val jsonArray = JSONArray(body)

        val files = mutableListOf<ModelFile>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            if (item.optString("type") == "file") {
                val filename = item.optString("path", "")
                val sizeBytes = item.optLong("size", 0)
                val downloadUrl = "https://huggingface.co/$modelId/resolve/main/$filename"

                files.add(
                    ModelFile(
                        filename = filename,
                        downloadUrl = downloadUrl,
                        sizeBytes = sizeBytes
                    )
                )
            }
        }

        files
    }

    private fun parseModelResult(json: JSONObject): ModelSearchResult {
        val modelId = json.optString("modelId", json.optString("id", ""))
        val name = modelId.split("/").lastOrNull() ?: modelId
        val description = json.optString("description", null)
        val downloads = json.optLong("downloads", 0)
        val likes = json.optLong("likes", 0)

        // Parse tags
        val tagsArray = json.optJSONArray("tags")
        val tags = mutableListOf<String>()
        if (tagsArray != null) {
            for (i in 0 until tagsArray.length()) {
                tags.add(tagsArray.getString(i))
            }
        }

        // Parse author
        val author = json.optString("author", null)

        // Create a single "main" version pointing to the repo
        // Users will select specific files after selecting the model
        val version = ModelVersion(
            id = "main",
            name = "main",
            baseModel = null,
            downloadUrl = "https://huggingface.co/$modelId/resolve/main",
            filename = "<huggingface>", // Special marker for HF repos
            sizeKB = null,
            files = emptyList() // Will be populated on demand
        )

        return ModelSearchResult(
            id = modelId,
            name = name,
            description = description,
            thumbnailUrl = null, // HF API doesn't provide thumbnails directly
            downloadCount = downloads,
            favoriteCount = likes,
            tags = tags,
            creator = author,
            versions = listOf(version),
            provider = ModelProvider.HUGGINGFACE
        )
    }
}
