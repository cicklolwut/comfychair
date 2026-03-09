package sh.hnet.comfychair.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import sh.hnet.comfychair.AuthInterceptor
import sh.hnet.comfychair.model.AuthCredentials
import java.util.concurrent.TimeUnit

class ComfyChairHelperService(
    private val serverUrlProvider: () -> String,
    private val credentialsProvider: () -> AuthCredentials = { AuthCredentials.None }
) {
    private val authInterceptor = AuthInterceptor(credentialsProvider())

    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            // Refresh credentials on each request so changes (e.g. re-auth) take effect
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
