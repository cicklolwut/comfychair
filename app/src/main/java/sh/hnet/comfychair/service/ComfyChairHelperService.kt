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

data class ScanStatus(
    val running: Boolean,
    val lastScan: Long?,
    val filesTotal: Int,
    val filesDone: Int,
    val currentFile: String?
)

class ComfyChairHelperService(
    private val serverUrlProvider: () -> String,
    private val credentialsProvider: () -> AuthCredentials = { AuthCredentials.None }
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

    companion object {
        private const val TAG = "ComfyChairHelperService"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private fun baseUrl(): String = serverUrlProvider().trimEnd('/')

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

    // ---- Installation via ComfyUI-Manager ----

    /**
     * Install the helper node via ComfyUI-Manager's git URL install endpoint.
     * Requires ComfyUI-Manager to be installed with security_level of 'middle' or lower.
     */
    suspend fun installViaManager(
        gitUrl: String = "https://git.bun.cafe/cinnabrad/comfyui-comfychair-helper"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${baseUrl()}/customnode/install/git_url")
                .post(gitUrl.toRequestBody("text/plain".toMediaType()))
                .build()
            val resp = client.newCall(req).execute()
            resp.use { it.isSuccessful }
        } catch (e: Exception) {
            DebugLogger.w(TAG, "Failed to install via Manager: ${e.message}")
            false
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
}
