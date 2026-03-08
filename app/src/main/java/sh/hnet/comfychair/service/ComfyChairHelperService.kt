package sh.hnet.comfychair.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ComfyChairHelperService(
    private val serverUrlProvider: () -> String,
    private val client: OkHttpClient
) {
    companion object {
        private const val TAG = "ComfyChairHelperService"
    }

    /** Returns true if the helper node is installed and responding. */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "${serverUrlProvider().trimEnd('/')}/comfychair/ping"
            val req = Request.Builder().url(url).get().build()
            val resp = client.newCall(req).execute()
            resp.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /** Returns the set of Civitai version IDs installed on the server. Empty set if unavailable. */
    suspend fun getInstalledVersionIds(): Set<Long> = withContext(Dispatchers.IO) {
        try {
            val url = "${serverUrlProvider().trimEnd('/')}/comfychair/installed"
            val req = Request.Builder().url(url).get().build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptySet()
            val body = resp.body?.string() ?: return@withContext emptySet()
            val arr = JSONObject(body).getJSONArray("version_ids")
            (0 until arr.length()).map { arr.getLong(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }
}
