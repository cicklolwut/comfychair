package sh.hnet.comfychair.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import sh.hnet.comfychair.model.PromptEnhancementMode
import sh.hnet.comfychair.storage.PromptEnhancementSettings
import java.util.concurrent.TimeUnit

/**
 * Calls an OpenAI-compatible chat completions API to enhance prompts.
 * Works with OpenAI, OpenRouter, and any compatible endpoint.
 */
class PromptEnhancementService(
    private val settings: PromptEnhancementSettings
) {
    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Enhance a prompt using the configured LLM provider.
     *
     * @param userPrompt The user's raw prompt
     * @param mode The generation mode (determines system prompt)
     * @return The enhanced prompt text, or throws on error
     */
    suspend fun enhance(
        userPrompt: String,
        mode: PromptEnhancementMode
    ): String = withContext(Dispatchers.IO) {
        val baseUrl = settings.effectiveBaseUrl.trimEnd('/')
        val apiKey = settings.apiKey
        val model = settings.model
        val systemPrompt = settings.getSystemPrompt(mode)

        require(apiKey.isNotBlank()) { "API key not configured" }
        require(baseUrl.isNotBlank()) { "API base URL not configured" }

        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", userPrompt)
            })
        }

        val requestBody = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("temperature", 0.7)
            put("max_tokens", 500)
        }

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw RuntimeException("API returned ${response.code}: $errorBody")
        }

        val responseBody = response.body?.string()
            ?: throw RuntimeException("Empty response from API")
        val json = JSONObject(responseBody)
        val choices = json.getJSONArray("choices")
        if (choices.length() == 0) {
            throw RuntimeException("No choices in API response")
        }

        choices.getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    }
}
