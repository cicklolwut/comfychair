package sh.hnet.comfychair.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Fetches the model list from OpenRouter's /api/v1/models endpoint.
 * Results are cached in memory until the next app restart.
 */
object OpenRouterModels {

    data class Model(
        val id: String,
        val name: String,
        val contextLength: Int = 0,
        val pricingPrompt: Double = 0.0
    )

    @Volatile
    private var cachedModels: List<Model>? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Fetch model list from OpenRouter. Uses in-memory cache after first call.
     * @param forceRefresh Bypass cache and re-fetch
     * @return Sorted list of available models
     */
    suspend fun getModels(forceRefresh: Boolean = false): List<Model> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            cachedModels?.let { return@withContext it }
        }

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/models")
            .header("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("Failed to fetch models: ${response.code}")
        }

        val body = response.body?.string()
            ?: throw RuntimeException("Empty response from OpenRouter")
        val json = JSONObject(body)
        val dataArray = json.getJSONArray("data")

        val models = mutableListOf<Model>()
        for (i in 0 until dataArray.length()) {
            val obj = dataArray.getJSONObject(i)
            val id = obj.getString("id")
            val name = obj.optString("name", id)
            val contextLength = obj.optInt("context_length", 0)
            val pricing = obj.optJSONObject("pricing")
            val promptPrice = pricing?.optString("prompt", "0")?.toDoubleOrNull() ?: 0.0

            models.add(Model(
                id = id,
                name = name,
                contextLength = contextLength,
                pricingPrompt = promptPrice
            ))
        }

        val sorted = models.sortedBy { it.name.lowercase() }
        cachedModels = sorted
        sorted
    }

    fun clearCache() {
        cachedModels = null
    }
}
