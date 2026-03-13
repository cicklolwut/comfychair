package sh.hnet.comfychair.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import sh.hnet.comfychair.AuthInterceptor
import sh.hnet.comfychair.model.AuthCredentials
import sh.hnet.comfychair.util.DebugLogger
import java.util.concurrent.TimeUnit

/**
 * Data classes for the organized models response.
 */
data class OrganizedModel(
    val modelId: Long?,
    val modelName: String?,
    val modelType: String?,
    val versions: List<OrganizedVersion>
)

data class OrganizedVersion(
    val versionId: Long?,
    val versionName: String?,
    val baseModel: String?,
    val hash: String?,
    val trainedWords: List<String>,
    val paths: List<String>
)

data class HelperVersionInfo(
    val version: String,
    val commit: String?,
    val behind: Int?,
    val updateAvailable: Boolean?
)

data class HelperUpdateResult(
    val ok: Boolean,
    val message: String?,
    val error: String?,
    val restartRequired: Boolean
)

enum class InstallResult {
    SUCCESS,
    ALREADY_INSTALLED,
    SECURITY_BLOCKED,
    FAILED
}

data class ScanStatus(
    val running: Boolean,
    val lastScan: Long?,
    val filesTotal: Int,
    val filesDone: Int,
    val currentFile: String?
)

/**
 * Represents a model file that is in the wrong folder according to its classification.
 */
data class ModelDiscrepancy(
    val path: String,
    val filename: String,
    val currentFolderType: String,
    val expectedFolderType: String,
    val classificationSource: String,  // "header" or "civitai"
    val headerClass: String?,
    val civitaiType: String?,
    val baseModel: String?,
    val suggestedPath: String,
    val hash: String
)

class ComfyChairHelperService(
    private val serverUrlProvider: () -> String,
    private val credentialsProvider: () -> AuthCredentials = { AuthCredentials.None },
    /** Called when auth session appears expired. Wire to ConnectionManager's refresh flow. */
    var onSessionExpired: (() -> Unit)? = null
) {
    private val authInterceptor = AuthInterceptor(credentialsProvider())

    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            authInterceptor.setCredentials(credentialsProvider())
            chain.proceed(chain.request())
        }
        .addInterceptor(authInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    init {
        authInterceptor.onSessionExpired = { onSessionExpired?.invoke() }
    }

    companion object {
        private const val TAG = "ComfyChairHelperService"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private fun baseUrl(): String = serverUrlProvider().trimEnd('/')

    /**
     * Check if a response is actually an auth redirect (HTML login page instead of JSON).
     * OkHttp follows redirects automatically, so we check the content type.
     * Throws [SessionExpiredException] if auth appears expired.
     */
    private fun requireJsonResponse(resp: okhttp3.Response, context: String) {
        val contentType = resp.header("Content-Type") ?: ""
        if (contentType.contains("text/html", ignoreCase = true)) {
            throw SessionExpiredException("Authentication session expired during $context. Please re-authenticate.")
        }
    }

    /** Thrown when a request gets an auth redirect instead of the expected API response. */
    class SessionExpiredException(message: String) : RuntimeException(message)

    // ---- Health / availability ----

    /** Returns true if the helper node is installed and responding. */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("${baseUrl()}/comfychair/ping").get().build()
            val resp = client.newCall(req).execute()
            resp.use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    // ---- Installed versions ----

    /** Returns the set of Civitai version IDs installed on the server. Empty set if unavailable. */
    suspend fun getInstalledVersionIds(): Set<Long> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("${baseUrl()}/comfychair/installed").get().build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptySet()
            val body = resp.body?.string() ?: return@withContext emptySet()
            val arr = JSONObject(body).getJSONArray("version_ids")
            (0 until arr.length()).map { arr.getLong(it) }.toSet()
        } catch (e: Exception) {
            DebugLogger.w(TAG, "Failed to get installed versions: ${e.message}")
            emptySet()
        }
    }

    // ---- Organized models ----

    /** Returns models grouped by Civitai model, with file paths. */
    suspend fun getOrganizedModels(): List<OrganizedModel> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${baseUrl()}/comfychair/models/organized")
                .get().build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()
            val body = resp.body?.string() ?: return@withContext emptyList()
            val arr = JSONArray(body)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val versions = obj.getJSONArray("versions")
                OrganizedModel(
                    modelId = if (obj.isNull("model_id")) null else obj.getLong("model_id"),
                    modelName = if (obj.isNull("model_name")) null else obj.getString("model_name"),
                    modelType = if (obj.isNull("model_type")) null else obj.getString("model_type"),
                    versions = (0 until versions.length()).map { j ->
                        val v = versions.getJSONObject(j)
                        val trainedWords = try {
                            val tw = v.optString("trained_words", "[]")
                            val twArr = JSONArray(tw)
                            (0 until twArr.length()).map { twArr.getString(it) }
                        } catch (e: Exception) { emptyList() }
                        val paths = v.getJSONArray("paths")
                        OrganizedVersion(
                            versionId = if (v.isNull("version_id")) null else v.getLong("version_id"),
                            versionName = if (v.isNull("version_name")) null else v.getString("version_name"),
                            baseModel = if (v.isNull("base_model")) null else v.getString("base_model"),
                            hash = if (v.isNull("hash")) null else v.getString("hash"),
                            trainedWords = trainedWords,
                            paths = (0 until paths.length()).map { paths.getString(it) }
                        )
                    }
                )
            }
        } catch (e: Exception) {
            DebugLogger.w(TAG, "Failed to get organized models: ${e.message}")
            emptyList()
        }
    }

    // ---- Version / update ----

    /** Check helper node version and update availability. */
    suspend fun getVersionInfo(): HelperVersionInfo? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("${baseUrl()}/comfychair/version").get().build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body?.string() ?: return@withContext null
            val obj = JSONObject(body)
            HelperVersionInfo(
                version = obj.getString("version"),
                commit = obj.optString("commit", null),
                behind = if (obj.isNull("behind")) null else obj.getInt("behind"),
                updateAvailable = if (obj.isNull("update_available")) null else obj.getBoolean("update_available")
            )
        } catch (e: Exception) {
            DebugLogger.w(TAG, "Failed to get version info: ${e.message}")
            null
        }
    }

    /** Trigger a self-update (git pull). */
    suspend fun update(): HelperUpdateResult = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${baseUrl()}/comfychair/update")
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return@withContext HelperUpdateResult(
                ok = false, message = null, error = "Empty response", restartRequired = false
            )
            val obj = JSONObject(body)
            HelperUpdateResult(
                ok = obj.optBoolean("ok", false),
                message = obj.optString("message", null),
                error = obj.optString("error", null),
                restartRequired = obj.optBoolean("restart_required", false)
            )
        } catch (e: Exception) {
            DebugLogger.w(TAG, "Failed to update helper: ${e.message}")
            HelperUpdateResult(ok = false, message = null, error = e.message, restartRequired = false)
        }
    }

    // ---- Scan ----

    /** Get current scan status. */
    suspend fun getScanStatus(): ScanStatus? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("${baseUrl()}/comfychair/scan/status").get().build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body?.string() ?: return@withContext null
            val obj = JSONObject(body)
            ScanStatus(
                running = obj.getBoolean("running"),
                lastScan = if (obj.isNull("last_scan")) null else obj.getLong("last_scan"),
                filesTotal = obj.optInt("files_total", 0),
                filesDone = obj.optInt("files_done", 0),
                currentFile = obj.optString("current_file", null)
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Trigger a model rescan. */
    suspend fun triggerScan(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply { put("force", force) }
            val req = Request.Builder()
                .url("${baseUrl()}/comfychair/scan")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val resp = client.newCall(req).execute()
            resp.use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    // ---- Config ----

    /** Set the Civitai API key on the helper node. */
    suspend fun setCivitaiApiKey(key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply { put("civitai_api_key", key) }
            val req = Request.Builder()
                .url("${baseUrl()}/comfychair/config")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val resp = client.newCall(req).execute()
            resp.use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    // ---- Model downloads ----

    data class DownloadJob(
        val id: String,
        val status: String,       // queued | downloading | done | error
        val progress: Double,
        val bytesDone: Long,
        val bytesTotal: Long,
        val error: String?,
        val keyStored: Boolean    // true if the helper stored the API key
    )

    /**
     * Check whether the helper has a stored Civitai API key.
     */
    suspend fun hasStoredApiKey(): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${baseUrl()}/comfychair/config/apikey")
                .get().build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext false
            val body = resp.body?.string() ?: return@withContext false
            JSONObject(body).optBoolean("has_key", false)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Download a model via the helper node.
     *
     * @param url            Download URL (e.g. Civitai download link)
     * @param filename       Target filename (e.g. "model_v10.safetensors")
     * @param modelType      Model type for directory resolution (lora, checkpoint, etc.)
     * @param savePath       Sub-path under models dir, or "default" for type-based
     * @param versionId      Optional Civitai version ID for metadata tracking
     * @param apiKey         Optional API key to pass for this download
     * @param storeKey       If true and apiKey provided, helper will persist it
     */
    suspend fun downloadModel(
        url: String,
        filename: String,
        modelType: String,
        savePath: String = "default",
        versionId: Long? = null,
        apiKey: String? = null,
        storeKey: Boolean = false
    ): DownloadJob = withContext(Dispatchers.IO) {
        DebugLogger.d(TAG, "downloadModel: POST url=${url.take(80)}, filename=$filename, type=$modelType, versionId=$versionId")
        val body = JSONObject().apply {
            put("url", url)
            put("filename", filename)
            put("model_type", modelType)
            put("save_path", savePath)
            if (versionId != null) put("civitai_version_id", versionId)
            if (!apiKey.isNullOrBlank()) {
                put("api_key", apiKey)
                put("store_key", storeKey)
            }
        }

        val req = Request.Builder()
            .url("${baseUrl()}/comfychair/download")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val resp = client.newCall(req).execute()
        DebugLogger.d(TAG, "downloadModel: response code=${resp.code}, contentType=${resp.header("Content-Type")}")
        requireJsonResponse(resp, "model download")

        val respBody = resp.body?.string()
            ?: throw RuntimeException("Empty response from helper")

        val obj = try {
            JSONObject(respBody)
        } catch (e: org.json.JSONException) {
            DebugLogger.e(TAG, "downloadModel: invalid JSON response: ${respBody.take(200)}")
            throw RuntimeException("Server returned invalid response (expected JSON, got: ${respBody.take(100)})")
        }
        if (!resp.isSuccessful) {
            val errMsg = if (obj.isNull("error")) "Download request failed (HTTP ${resp.code})"
                         else obj.optString("error", "Download request failed")
            DebugLogger.e(TAG, "downloadModel: server error ${resp.code}: $errMsg")
            throw RuntimeException(errMsg)
        }

        DebugLogger.d(TAG, "downloadModel: job created id=${obj.optString("id")}, status=${obj.optString("status")}")
        DownloadJob(
            id = obj.getString("id"),
            status = obj.getString("status"),
            progress = obj.optDouble("progress", 0.0),
            bytesDone = obj.optLong("bytes_done", 0),
            bytesTotal = obj.optLong("bytes_total", 0),
            error = if (obj.isNull("error")) null else obj.optString("error", null),
            keyStored = obj.optBoolean("key_stored", false)
        )
    }

    /**
     * Poll download progress.
     */
    suspend fun getDownloadStatus(jobId: String): DownloadJob? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${baseUrl()}/comfychair/download/$jobId")
                .get().build()
            val resp = client.newCall(req).execute()
            requireJsonResponse(resp, "download status poll")
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body?.string() ?: return@withContext null
            val obj = JSONObject(body)
            DownloadJob(
                id = obj.getString("id"),
                status = obj.getString("status"),
                progress = obj.optDouble("progress", 0.0),
                bytesDone = obj.optLong("bytes_done", 0),
                bytesTotal = obj.optLong("bytes_total", 0),
                error = if (obj.isNull("error")) null else obj.optString("error", null),
                keyStored = false
            )
        } catch (e: SessionExpiredException) {
            throw e // Don't swallow auth errors — let them propagate
        } catch (e: Exception) {
            null
        }
    }

    // ---- Installation via ComfyUI-Manager ----

    /**
     * Install the helper node via ComfyUI-Manager's git URL install endpoint.
     * The clone runs synchronously server-side — this call blocks until complete.
     *
     * Returns:
     * - SUCCESS: clone completed, restart needed
     * - ALREADY_INSTALLED: node already exists (Manager returned 200 with skip action)
     * - SECURITY_BLOCKED: Manager security level too restrictive (403)
     * - FAILED: clone failed (400) or network error
     */
    suspend fun installViaManager(
        gitUrl: String = "https://git.bun.cafe/cinnabrad/comfyui-comfychair-helper"
    ): InstallResult = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${baseUrl()}/customnode/install/git_url")
                .post(gitUrl.toRequestBody("text/plain".toMediaType()))
                .build()
            val resp = client.newCall(req).execute()
            resp.use {
                when (it.code) {
                    200 -> {
                        // Manager returns 200 for both fresh install and "already installed"
                        // We can't distinguish from status alone, but both mean restart is valid
                        InstallResult.SUCCESS
                    }
                    403 -> InstallResult.SECURITY_BLOCKED
                    else -> InstallResult.FAILED
                }
            }
        } catch (e: Exception) {
            DebugLogger.w(TAG, "Failed to install via Manager: ${e.message}")
            InstallResult.FAILED
        }
    }

    /**
     * Restart the ComfyUI server via ComfyUI-Manager's reboot endpoint.
     * The server will go down and come back up — the app should reconnect via WebSocket.
     */
    suspend fun restartComfyUI(): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${baseUrl()}/manager/reboot")
                .get().build()
            val resp = client.newCall(req).execute()
            resp.use { it.isSuccessful }
        } catch (e: Exception) {
            // Connection reset is expected — server is shutting down
            DebugLogger.d(TAG, "Restart request sent (connection closed as expected)")
            true
        }
    }

    /** Check if ComfyUI-Manager is available (for the install path). */
    suspend fun isManagerAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${baseUrl()}/manager/queue/status")
                .get().build()
            val resp = client.newCall(req).execute()
            resp.use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    // ---- Model organization (discrepancy detection) ----

    /**
     * Get list of models that are in the wrong folder based on their classification.
     */
    suspend fun getDiscrepancies(): List<ModelDiscrepancy> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${baseUrl()}/comfychair/models/discrepancies")
                .get().build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()
            val body = resp.body?.string() ?: return@withContext emptyList()
            val arr = JSONArray(body)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ModelDiscrepancy(
                    path = obj.getString("path"),
                    filename = obj.getString("filename"),
                    currentFolderType = obj.getString("current_folder_type"),
                    expectedFolderType = obj.getString("expected_folder_type"),
                    classificationSource = obj.getString("classification_source"),
                    headerClass = if (obj.isNull("header_class")) null else obj.getString("header_class"),
                    civitaiType = if (obj.isNull("civitai_type")) null else obj.getString("civitai_type"),
                    baseModel = if (obj.isNull("base_model")) null else obj.getString("base_model"),
                    suggestedPath = obj.getString("suggested_path"),
                    hash = obj.getString("hash")
                )
            }
        } catch (e: Exception) {
            DebugLogger.w(TAG, "Failed to get discrepancies: ${e.message}")
            emptyList()
        }
    }

    /**
     * Move a model file from one location to another.
     * Updates the database and creates intermediate directories if needed.
     *
     * @param sourcePath Current file path
     * @param destPath Target file path
     * @return true if move succeeded, false otherwise
     */
    suspend fun moveModel(sourcePath: String, destPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("source_path", sourcePath)
                put("dest_path", destPath)
            }
            val req = Request.Builder()
                .url("${baseUrl()}/comfychair/models/move")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val resp = client.newCall(req).execute()
            resp.use { it.isSuccessful && resp.body?.string()?.contains("\"ok\":true") == true }
        } catch (e: Exception) {
            DebugLogger.w(TAG, "Failed to move model: ${e.message}")
            false
        }
    }
}
