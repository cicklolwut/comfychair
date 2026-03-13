package sh.hnet.comfychair.connection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sh.hnet.comfychair.service.ComfyChairHelperService
import sh.hnet.comfychair.service.OrganizedModel
import sh.hnet.comfychair.service.OrganizedVersion
import sh.hnet.comfychair.util.DebugLogger

/**
 * Metadata-enriched model entry with display information from Civitai.
 */
data class MetadataModelEntry(
    val displayName: String,         // Model name (e.g. "Illustrious XL 2.0")
    val versionName: String?,        // Version name (e.g. "v2.0")
    val baseModel: String?,          // Base model (e.g. "Illustrious", "SDXL 1.0")
    val filePath: String,            // Actual file path for ComfyUI
    val extension: String,           // File extension (safetensors, pt, gguf)
    val weight: String?,             // Weight precision (fp16, fp8, bf16, etc.)
    val modelType: String?,          // Civitai model type (Checkpoint, LORA, etc.)
    val headerClass: String?,        // From safetensor header analysis (checkpoint, unet, lora)
    val trainedWords: List<String>   // Trigger words
)

/**
 * Centralized cache for organized models from the ComfyChair Helper.
 * Provides O(1) lookup by file path to enrich ModelDropdown with metadata.
 */
object OrganizedModelCache {
    private const val TAG = "OrganizedModelCache"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _entryMap = MutableStateFlow<Map<String, MetadataModelEntry>>(emptyMap())
    val entryMap: StateFlow<Map<String, MetadataModelEntry>> = _entryMap.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Refresh the organized model cache from the helper.
     * Call after connection or when models are added/moved.
     *
     * @param helperService The helper service to fetch organized models from
     */
    fun refresh(helperService: ComfyChairHelperService) {
        if (_isLoading.value) {
            DebugLogger.d(TAG, "Refresh already in progress, skipping")
            return
        }

        DebugLogger.i(TAG, "Refreshing organized model cache")
        _isLoading.value = true

        scope.launch {
            try {
                val organizedModels = helperService.getOrganizedModels()
                val map = buildEntryMap(organizedModels)
                _entryMap.value = map
                DebugLogger.i(TAG, "Organized model cache refreshed: ${map.size} entries")
            } catch (e: Exception) {
                DebugLogger.w(TAG, "Failed to refresh organized models: ${e.message}")
                _entryMap.value = emptyMap()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Clear the cache (on disconnect or server change).
     */
    fun clear() {
        _entryMap.value = emptyMap()
        DebugLogger.d(TAG, "Organized model cache cleared")
    }

    /**
     * Build the file path → metadata entry map from organized models.
     */
    private fun buildEntryMap(organizedModels: List<OrganizedModel>): Map<String, MetadataModelEntry> {
        val map = mutableMapOf<String, MetadataModelEntry>()

        for (model in organizedModels) {
            val displayName = model.modelName ?: "Unknown Model"
            val modelType = model.modelType

            for (version in model.versions) {
                val versionName = version.versionName
                val baseModel = version.baseModel
                val trainedWords = version.trainedWords

                for (path in version.paths) {
                    val extension = path.substringAfterLast('.', "")
                    val weight = parseWeight(versionName, path)

                    map[path] = MetadataModelEntry(
                        displayName = displayName,
                        versionName = versionName,
                        baseModel = baseModel,
                        filePath = path,
                        extension = extension,
                        weight = weight,
                        modelType = modelType,
                        headerClass = null,  // Could be populated if helper provides it
                        trainedWords = trainedWords
                    )
                }
            }
        }

        return map
    }
}

/**
 * Parse weight precision from version name or filename.
 * Detects: fp8, bf16, fp16/half, fp32/full, GGUF quants (q4_k, q5_k, q8).
 *
 * @param versionName Civitai version name (may contain precision hints)
 * @param filename File name (may contain precision hints)
 * @return Weight string (e.g. "fp16", "q4_k") or null if not detected
 */
fun parseWeight(versionName: String?, filename: String): String? {
    val combined = "${versionName.orEmpty()} $filename".lowercase()

    return when {
        "fp8" in combined -> "fp8"
        "bf16" in combined -> "bf16"
        "fp16" in combined || "half" in combined -> "fp16"
        "fp32" in combined || "full" in combined -> "fp32"
        Regex("q[0-9]_[kms]").containsMatchIn(combined) -> {
            // Extract GGUF quant pattern (e.g. q4_k, q5_k_m, q8_0)
            Regex("q[0-9]_[kms]").find(combined)?.value
        }
        else -> null
    }
}

/**
 * Build a list of metadata entries from file paths and the organized model cache.
 * For paths with metadata → full MetadataModelEntry.
 * For paths without metadata → filename-only fallback entry.
 *
 * @param filePaths List of file paths from ModelCache (ground truth)
 * @param organizedEntryMap Map of file path → MetadataModelEntry from OrganizedModelCache
 * @return List of metadata entries enriched where possible, with fallbacks for unmatched paths
 */
fun buildMetadataEntries(
    filePaths: List<String>,
    organizedEntryMap: Map<String, MetadataModelEntry>
): List<MetadataModelEntry> {
    return filePaths.map { path ->
        organizedEntryMap[path] ?: run {
            // Fallback for paths without metadata: use filename only
            val filename = path.substringAfterLast('/', path).substringAfterLast('\\', path)
            val extension = filename.substringAfterLast('.', "")
            MetadataModelEntry(
                displayName = filename,
                versionName = null,
                baseModel = null,  // Will go to "Unknown" group
                filePath = path,
                extension = extension,
                weight = parseWeight(null, filename),
                modelType = null,
                headerClass = null,
                trainedWords = emptyList()
            )
        }
    }
}
