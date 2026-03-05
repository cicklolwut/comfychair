package sh.hnet.comfychair.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import sh.hnet.comfychair.util.DebugLogger
import java.util.concurrent.TimeUnit

/**
 * Service for interacting with ComfyUI-Manager's model download API.
 * Triggers server-side downloads via the /manager/queue/install_model endpoint.
 *
 * Security requirements (from manager_server.py):
 * - Requires security_level of 'middle' or lower
 * - Non-safetensors formats require security_level of 'high' or lower + default channel whitelist
 */
class ComfyUIManagerService(
    private val serverUrlProvider: () -> String
) {
    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val TAG = "ComfyUIManagerService"
    }

    private val client = HttpModule.client

    /** Get the server base URL from the injected provider. */
    private fun getServerUrl(): String = serverUrlProvider()

    /**
     * Trigger model download on the ComfyUI server.
     */
    suspend fun queueModelDownload(
        url: String,
        filename: String,
        modelType: String,
        modelName: String,
        savePath: String = "default"
    ): Boolean = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl()
        val endpoint = "${serverUrl.trimEnd('/')}/manager/queue/install_model"

        val requestBody = JSONObject().apply {
            put("url", url)
            put("filename", filename)
            put("type", modelType)
            put("name", modelName)
            put("save_path", savePath)
            put("ui_id", System.currentTimeMillis().toString())
        }.toString()

        DebugLogger.d(TAG, "Queueing model download: $modelName ($modelType)")
        DebugLogger.d(TAG, "Request: $requestBody")

        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            DebugLogger.w(TAG, "Download queue failed: ${response.code} - $errorBody")
            throw RuntimeException("Failed to queue download: ${response.code} - $errorBody")
        }

        DebugLogger.d(TAG, "Model download queued successfully")
        true
    }

    /**
     * Check the current queue status.
     */
    suspend fun getQueueStatus(): JSONObject = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl()
        val endpoint = "${serverUrl.trimEnd('/')}/manager/queue/status"

        val request = Request.Builder()
            .url(endpoint)
            .get()
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw RuntimeException("Failed to get queue status: ${response.code}")
        }

        val body = response.body?.string() ?: throw RuntimeException("Empty response")
        JSONObject(body)
    }

    /**
     * Start processing the download queue.
     */
    suspend fun startQueue(): Boolean = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl()
        val endpoint = "${serverUrl.trimEnd('/')}/manager/queue/start"

        val request = Request.Builder()
            .url(endpoint)
            .get()
            .build()

        val response = client.newCall(request).execute()
        response.isSuccessful
    }
}
