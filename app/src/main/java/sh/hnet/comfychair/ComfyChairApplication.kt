package sh.hnet.comfychair

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade

class ComfyChairApplication : Application(), SingletonImageLoader.Factory {
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
