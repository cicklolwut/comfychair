package sh.hnet.comfychair.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import sh.hnet.comfychair.model.EnhancementResult
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
     * Test the connection by hitting the /models endpoint.
     * Returns the model count on success, throws on failure.
     */
    suspend fun testConnection(): Int = withContext(Dispatchers.IO) {
        val baseUrl = settings.effectiveBaseUrl.trimEnd('/')
        val apiKey = settings.apiKey

        require(apiKey.isNotBlank()) { "API key not configured" }
        require(baseUrl.isNotBlank()) { "API base URL not configured" }

        val request = Request.Builder()
            .url("$baseUrl/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw RuntimeException("API returned ${response.code}: $errorBody")
        }

        val body = response.body?.string() ?: throw RuntimeException("Empty response")
        val json = JSONObject(body)
        val data = json.optJSONArray("data")
        data?.length() ?: 0
    }

    /**
     * Enhance a prompt using a specific system prompt. Returns raw text.
     */
    suspend fun enhanceWithSystemPrompt(
        userPrompt: String,
        systemPrompt: String
    ): String = enhanceInternal(userPrompt, systemPrompt)

    /**
     * Enhance a prompt and parse the structured JSON result.
     */
    suspend fun enhanceStructured(
        userPrompt: String,
        systemPrompt: String
    ): EnhancementResult = withContext(Dispatchers.IO) {
        val rawResponse = enhanceInternal(userPrompt, systemPrompt)
        parseEnhancementResult(rawResponse)
    }

    /**
     * Parse LLM response into EnhancementResult.
     * Handles both clean JSON and JSON wrapped in markdown code blocks.
     */
    private fun parseEnhancementResult(raw: String): EnhancementResult {
        // Strip markdown code blocks if present
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            val json = JSONObject(cleaned)
            EnhancementResult(
                prompt = json.optString("prompt", null),
                negativePrompt = json.optString("negative_prompt", null),
                cfgScale = if (json.has("cfg_scale")) json.optDouble("cfg_scale") else null,
                steps = if (json.has("steps")) json.optInt("steps") else null,
                sampler = json.optString("sampler", null),
                scheduler = json.optString("scheduler", null)
            )
        } catch (_: Exception) {
            // Fallback: treat entire response as prompt text
            EnhancementResult(prompt = cleaned)
        }
    }

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
    ): String = enhanceInternal(userPrompt, settings.getSystemPrompt(mode))

    private suspend fun enhanceInternal(
        userPrompt: String,
        systemPrompt: String
    ): String = withContext(Dispatchers.IO) {
        val baseUrl = settings.effectiveBaseUrl.trimEnd('/')
        val apiKey = settings.apiKey
        val model = settings.model

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
