package sh.hnet.comfychair

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sh.hnet.comfychair.db.AssetSeeder
import sh.hnet.comfychair.db.entity.Technique
import sh.hnet.comfychair.db.repository.CivitaiCacheRepository
import sh.hnet.comfychair.service.CivitaiTrpcService
import sh.hnet.comfychair.storage.ModelBrowserSettings

class ComfyChairApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        // Warm up the Room DB singleton on app start so the first read/write
        // doesn't pay the one-time initialisation cost on the UI thread.
        val repo = CivitaiCacheRepository.getInstance(this)

        // Seed hardcoded techniques (8 total, rarely changes)
        CoroutineScope(Dispatchers.IO).launch {
            repo.cacheTechniques(listOf(
                Technique(1, "txt2img", "Image"),
                Technique(2, "img2img", "Image"),
                Technique(3, "inpainting", "Image"),
                Technique(4, "workflow", "Image"),
                Technique(5, "vid2vid", "Video"),
                Technique(6, "txt2vid", "Video"),
                Technique(7, "img2vid", "Video"),
                Technique(8, "controlnet", "Image")
            ))
        }

        // Seed tools + tags from bundled assets on first install, then refresh tools from network
        CoroutineScope(Dispatchers.IO).launch {
            AssetSeeder.seedIfNeeded(this@ComfyChairApplication, repo)
            // Opportunistic network refresh for tools (assets may be stale)
            try {
                val settings = ModelBrowserSettings(this@ComfyChairApplication)
                val civitaiService = CivitaiTrpcService(settings, this@ComfyChairApplication)
                val tools = civitaiService.getTools()
                if (tools.isNotEmpty()) repo.cacheTools(tools)
            } catch (e: Exception) {
                android.util.Log.w("ComfyChairApplication", "Tool network refresh failed: ${e.message}")
            }
        }
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(this@ComfyChairApplication, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100MB
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
