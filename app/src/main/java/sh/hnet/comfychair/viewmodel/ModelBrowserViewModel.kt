package sh.hnet.comfychair.viewmodel

import android.app.Application
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sh.hnet.comfychair.model.CommunityImage
import sh.hnet.comfychair.model.ModelProvider
import sh.hnet.comfychair.model.ModelSearchResult
import sh.hnet.comfychair.model.ModelType
import sh.hnet.comfychair.model.ModelVersion
import sh.hnet.comfychair.model.ModelFile
import sh.hnet.comfychair.connection.ConnectionManager
import sh.hnet.comfychair.connection.ConnectionState
import sh.hnet.comfychair.service.CivitaiTrpcService
import sh.hnet.comfychair.service.HuggingFaceService
import sh.hnet.comfychair.service.ComfyUIManagerService
// AppSettings is an object singleton, not instantiated
import sh.hnet.comfychair.storage.ModelBrowserSettings
import sh.hnet.comfychair.util.DebugLogger

/**
 * UI state for model browser screen.
 */
@Stable
data class ModelBrowserUiState(
    val searchQuery: String = "",
    val selectedProvider: ModelProvider = ModelProvider.CIVITAI,
    val providerConfigured: Boolean = false, // tracks if current provider has API key (HF only — Civitai works without)
    val searchResults: List<ModelSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val selectedModel: ModelSearchResult? = null,
    val selectedVersion: ModelVersion? = null,
    val selectedFile: ModelFile? = null,
    val selectedModelType: ModelType? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: String? = null,
    val errorMessage: String? = null,
    val communityImages: List<CommunityImage> = emptyList(),
    val isLoadingCommunityImages: Boolean = false,
    val showCommunityImages: Boolean = false,
    val communityImagesCursor: String? = null,
    val hasMoreCommunityImages: Boolean = true,
    val communityImagesSort: String = "Most Reactions",
    // Search filters
    val filterModelType: String? = null, // "Checkpoint", "LORA", etc.
    val filterBaseModel: String? = null, // "Illustrious", "NoobAI", etc.
    val filterSort: String = "Most Downloaded", // "Highest Rated", "Most Downloaded", "Newest"
    val filterPeriod: String = "AllTime", // "AllTime", "Year", "Month", "Week", "Day"
    val showFilters: Boolean = false,
    val nsfwLevels: Set<Int> = ModelBrowserSettings.DEFAULT_NSFW_LEVELS,
    val showAnimations: Boolean = false,
    val blurThreshold: Int = 2, // Default: blur images above PG-13
    val apiKey: String = "", // API key for current provider
    val browseLevel: Int = 31, // Default: show all images (XXX)

    // Pagination (cursor-based for trpc)
    val searchCursor: String? = null,
    val hasMoreResults: Boolean = true,
    val isLoadingMore: Boolean = false
)

/**
 * Events emitted by model browser operations.
 */
sealed class ModelBrowserEvent {
    data class ShowToast(val message: String) : ModelBrowserEvent()
    data class ShowError(val message: String) : ModelBrowserEvent()
}

/**
 * ViewModel for model browser screen.
 */
class ModelBrowserViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "ModelBrowserViewModel"
    }

    private val modelBrowserSettings = ModelBrowserSettings(getApplication())
    private val civitaiTrpcService = CivitaiTrpcService(modelBrowserSettings)
    private val huggingFaceService = HuggingFaceService(modelBrowserSettings)
    private val comfyUIManagerService = ComfyUIManagerService {
        val connState = ConnectionManager.connectionState.value
        if (connState is ConnectionState.Connected) {
            "${connState.protocol}://${connState.hostname}:${connState.port}"
        } else {
            throw IllegalStateException("Not connected to ComfyUI server")
        }
    }

    private val _uiState = MutableStateFlow(ModelBrowserUiState(
        // Civitai trpc works without API key, so it's always "configured"
        // Only HuggingFace still requires a key
        providerConfigured = true,
        nsfwLevels = modelBrowserSettings.nsfwLevels,
        showAnimations = modelBrowserSettings.showAnimations,
        blurThreshold = modelBrowserSettings.blurThreshold,
        browseLevel = modelBrowserSettings.browseLevel,
        apiKey = modelBrowserSettings.civitaiApiKey // Initial load, updated on provider switch
    ))
    val uiState: StateFlow<ModelBrowserUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ModelBrowserEvent>()
    val events: SharedFlow<ModelBrowserEvent> = _events.asSharedFlow()

    private var searchDebounceJob: Job? = null

    init {
        // trpc works without API key — load browse results immediately on screen open
        if (_uiState.value.selectedProvider == ModelProvider.CIVITAI) {
            triggerDebouncedSearch()
        }
    }

    /**
     * Trigger a debounced search. Called automatically on query/filter/NSFW changes.
     * @param delayMs debounce delay — 0 for immediate (filter changes), 800 for typing
     */
    private fun triggerDebouncedSearch(delayMs: Long = 0L) {
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            // Civitai (Meili) supports empty query for browsing by filters
            // HuggingFace still requires a query
            val query = _uiState.value.searchQuery.trim()
            val canSearch = query.isNotEmpty() ||
                _uiState.value.selectedProvider == ModelProvider.CIVITAI
            if (canSearch) {
                searchModels()
            }
        }
    }

    /**
     * Update the search query with debounced auto-search.
     */
    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        triggerDebouncedSearch(delayMs = 800L)
    }

    fun toggleFilters() {
        _uiState.value = _uiState.value.copy(showFilters = !_uiState.value.showFilters)
    }

    fun setFilterModelType(type: String?) {
        _uiState.value = _uiState.value.copy(filterModelType = type)
        triggerDebouncedSearch()
    }

    fun setFilterBaseModel(baseModel: String?) {
        _uiState.value = _uiState.value.copy(filterBaseModel = baseModel)
        triggerDebouncedSearch()
    }

    fun setFilterSort(sort: String) {
        _uiState.value = _uiState.value.copy(filterSort = sort)
        triggerDebouncedSearch()
    }

    fun setFilterPeriod(period: String) {
        _uiState.value = _uiState.value.copy(filterPeriod = period)
        triggerDebouncedSearch()
    }

    /**
     * Switch between Civitai and HuggingFace providers.
     */
    fun selectProvider(provider: ModelProvider) {
        // Civitai trpc works without API key, HuggingFace still requires one
        val configured = when (provider) {
            ModelProvider.CIVITAI -> true  // trpc works without auth
            ModelProvider.HUGGINGFACE -> modelBrowserSettings.isHuggingFaceConfigured
        }
        val apiKey = when (provider) {
            ModelProvider.CIVITAI -> modelBrowserSettings.civitaiApiKey
            ModelProvider.HUGGINGFACE -> modelBrowserSettings.huggingfaceApiKey
        }
        _uiState.value = _uiState.value.copy(
            selectedProvider = provider,
            providerConfigured = configured,
            apiKey = apiKey,
            searchResults = emptyList(),
            selectedModel = null,
            searchCursor = null,
            hasMoreResults = true
        )
        modelBrowserSettings.preferredProvider = provider.name.lowercase()

        // Auto-browse when switching to Civitai
        if (provider == ModelProvider.CIVITAI) {
            triggerDebouncedSearch()
        }
    }

    /**
     * Search for models using the selected provider.
     */
    fun searchModels() {
        val query = _uiState.value.searchQuery.trim()
        
        // HuggingFace still requires non-empty query
        if (query.isEmpty() && _uiState.value.selectedProvider == ModelProvider.HUGGINGFACE) {
            viewModelScope.launch {
                _events.emit(ModelBrowserEvent.ShowToast("Enter a search query"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearching = true,
                errorMessage = null,
                searchResults = emptyList(), // Clear stale results immediately
                searchCursor = null
            )
            try {
                when (_uiState.value.selectedProvider) {
                    ModelProvider.CIVITAI -> {
                        val state = _uiState.value
                        
                        // Compute browsingLevel bitmask from selected NSFW levels
                        val browsingLevel = state.nsfwLevels.fold(0) { acc, level -> acc or level }
                        
                        // Use trpc for search
                        val trpcResult = civitaiTrpcService.searchModels(
                            query = query,
                            types = state.filterModelType?.let { listOf(it) },
                            baseModels = state.filterBaseModel?.let { listOf(it) },
                            sort = state.filterSort,
                            period = state.filterPeriod,
                            browsingLevel = browsingLevel,
                            limit = 20,
                            cursor = null
                        )
                        
                        _uiState.value = _uiState.value.copy(
                            searchResults = trpcResult.models,
                            searchCursor = trpcResult.nextCursor,
                            hasMoreResults = trpcResult.nextCursor != null,
                            isSearching = false
                        )
                        
                        if (trpcResult.models.isEmpty()) {
                            _events.emit(ModelBrowserEvent.ShowToast("No results found"))
                        }
                        
                        // Background: resolve any uncached tag IDs
                        resolveTagsInBackground(trpcResult.models)
                    }
                    ModelProvider.HUGGINGFACE -> {
                        if (!modelBrowserSettings.isHuggingFaceConfigured) {
                            throw IllegalStateException("HuggingFace API key not configured")
                        }
                        val results = huggingFaceService.searchModels(query = query)
                        
                        _uiState.value = _uiState.value.copy(
                            searchResults = results,
                            isSearching = false
                        )
                        
                        if (results.isEmpty()) {
                            _events.emit(ModelBrowserEvent.ShowToast("No results found"))
                        }
                    }
                }
            } catch (e: Exception) {
                DebugLogger.w(TAG, "Search failed: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    errorMessage = e.message ?: "Search failed"
                )
                _events.emit(ModelBrowserEvent.ShowError(e.message ?: "Search failed"))
            }
        }
    }
    
    /**
     * Trigger background tag resolution for models with unresolved tags.
     * Tags are resolved from cache in the service, but if many are missing
     * we should fetch them once and they'll be available for future searches.
     */
    private fun resolveTagsInBackground(models: List<ModelSearchResult>) {
        // If any model has empty tags but likely had tag IDs, trigger a cache population
        // This is fire-and-forget — tags will be available on next search
        viewModelScope.launch {
            try {
                // Just fetch top tags to populate cache
                civitaiTrpcService.resolveTagNames(emptyList())
            } catch (e: Exception) {
                DebugLogger.d(TAG, "Background tag fetch failed (non-critical): ${e.message}")
            }
        }
    }

    /**
     * Load more search results (pagination).
     */
    fun loadMoreResults() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMoreResults) return
        if (state.selectedProvider != ModelProvider.CIVITAI) return
        
        val cursor = state.searchCursor ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            try {
                // Re-read state inside the coroutine so we always use the latest NSFW levels and filters
                val currentState = _uiState.value
                val currentCursor = currentState.searchCursor
                
                // Compute browsingLevel bitmask from selected NSFW levels
                val browsingLevel = currentState.nsfwLevels.fold(0) { acc, level -> acc or level }
                
                val trpcResult = civitaiTrpcService.searchModels(
                    query = currentState.searchQuery.trim(),
                    types = currentState.filterModelType?.let { listOf(it) },
                    baseModels = currentState.filterBaseModel?.let { listOf(it) },
                    sort = currentState.filterSort,
                    period = currentState.filterPeriod,
                    browsingLevel = browsingLevel,
                    limit = 20,
                    cursor = cursor
                )
                
                // Guard against stale append: if cursor changed (new search), discard
                if (_uiState.value.searchCursor == currentCursor) {
                    val maxResults = 300
                    val combined = _uiState.value.searchResults + trpcResult.models
                    val capped = if (combined.size > maxResults) combined.takeLast(maxResults) else combined
                    
                    _uiState.value = _uiState.value.copy(
                        searchResults = capped,
                        searchCursor = trpcResult.nextCursor,
                        hasMoreResults = trpcResult.nextCursor != null,
                        isLoadingMore = false
                    )
                } else {
                    // New search started — just clear the loading flag
                    _uiState.value = _uiState.value.copy(isLoadingMore = false)
                    DebugLogger.d(TAG, "loadMoreResults: cursor changed while in-flight, discarding stale page")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
                DebugLogger.w(TAG, "Failed to load more results: ${e.message}")
            }
        }
    }

    /**
     * Select a model to view details.
     */
    fun selectModel(model: ModelSearchResult) {
        DebugLogger.d(TAG, "selectModel: ${model.id} - ${model.name}")
        val autoType = sh.hnet.comfychair.model.CivitaiTypeMapper.toComfyUIType(model.civitaiType)
        
        // Show immediately with what we have
        _uiState.value = _uiState.value.copy(
            selectedModel = model,
            selectedVersion = model.versions.firstOrNull(),
            selectedFile = null,
            selectedModelType = autoType
        )
        // Clear community images when selecting a new model
        clearCommunityImages()
        
        // Load full details if Civitai (versions, description, etc.)
        if (model.provider == ModelProvider.CIVITAI) {
            val selectedId = model.id
            viewModelScope.launch {
                try {
                    val fullModel = civitaiTrpcService.getModelDetails(model.id.toInt())
                    // Guard against race: user may have selected a different model
                    if (_uiState.value.selectedModel?.id == selectedId) {
                        // Merge: keep thumbnails from getAll (getById doesn't include them)
                        val merged = fullModel.copy(
                            thumbnailUrl = fullModel.thumbnailUrl ?: model.thumbnailUrl,
                            animatedThumbnailUrl = fullModel.animatedThumbnailUrl ?: model.animatedThumbnailUrl
                        )
                        _uiState.value = _uiState.value.copy(
                            selectedModel = merged,
                            selectedVersion = merged.versions.firstOrNull()
                        )
                    }
                } catch (e: Exception) {
                    DebugLogger.w(TAG, "Failed to load model details: ${e.message}")
                    // Keep showing the basic model info from getAll
                }
            }
        } else {
            // HuggingFace already has full data from search
            _uiState.value = _uiState.value.copy(
                selectedVersion = model.versions.firstOrNull()
            )
        }
    }

    /**
     * Select a specific version of the model.
     */
    fun selectVersion(version: ModelVersion) {
        _uiState.value = _uiState.value.copy(
            selectedVersion = version,
            selectedFile = version.files.firstOrNull(),
            // Clear stale community images when switching versions
            communityImages = emptyList(),
            communityImagesCursor = null,
            hasMoreCommunityImages = true,
            showCommunityImages = false
        )

        // Load files for HuggingFace models if not already loaded
        if (_uiState.value.selectedProvider == ModelProvider.HUGGINGFACE && version.files.isEmpty()) {
            loadHuggingFaceFiles()
        }
    }

    /**
     * Select a specific file (for HuggingFace models with multiple files).
     */
    fun selectFile(file: ModelFile) {
        _uiState.value = _uiState.value.copy(selectedFile = file)
    }

    /**
     * Select the model type for download.
     */
    fun selectModelType(type: ModelType) {
        _uiState.value = _uiState.value.copy(selectedModelType = type)
    }

    /**
     * Load files for a HuggingFace model.
     */
    private fun loadHuggingFaceFiles() {
        val model = _uiState.value.selectedModel ?: return
        val version = _uiState.value.selectedVersion ?: return

        viewModelScope.launch {
            try {
                val files = huggingFaceService.getModelFiles(model.id)
                val updatedVersion = version.copy(files = files)
                
                // Update the selected version with files
                _uiState.value = _uiState.value.copy(
                    selectedVersion = updatedVersion,
                    selectedFile = files.firstOrNull()
                )
            } catch (e: Exception) {
                DebugLogger.w(TAG, "Failed to load files: ${e.message}")
                _events.emit(ModelBrowserEvent.ShowError("Failed to load files: ${e.message}"))
            }
        }
    }

    /**
     * Download the selected model via ComfyUI Manager.
     */
    fun downloadModel(subfolder: String = "") {
        val model = _uiState.value.selectedModel ?: return
        val version = _uiState.value.selectedVersion ?: return
        val modelType = _uiState.value.selectedModelType

        if (modelType == null) {
            viewModelScope.launch {
                _events.emit(ModelBrowserEvent.ShowToast("Select a model type first"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloading = true,
                downloadProgress = "Queueing download...",
                errorMessage = null
            )

            try {
                val downloadUrl: String
                val filename: String

                when (_uiState.value.selectedProvider) {
                    ModelProvider.CIVITAI -> {
                        downloadUrl = version.downloadUrl
                        filename = version.filename
                    }
                    ModelProvider.HUGGINGFACE -> {
                        val file = _uiState.value.selectedFile
                        if (file == null) {
                            throw IllegalStateException("No file selected")
                        }
                        downloadUrl = file.downloadUrl
                        filename = file.filename
                    }
                }

                // Determine save path from subfolder
                val savePath = if (subfolder.isBlank()) "default" else subfolder

                // Queue the download via ComfyUI Manager
                comfyUIManagerService.queueModelDownload(
                    url = downloadUrl,
                    filename = filename,
                    modelType = modelType.value,
                    modelName = model.name,
                    savePath = savePath
                )

                // Start the queue processing
                comfyUIManagerService.startQueue()

                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    downloadProgress = null
                )

                _events.emit(ModelBrowserEvent.ShowToast("Download queued! Check ComfyUI Manager for progress."))
                
                // Clear selection
                _uiState.value = _uiState.value.copy(
                    selectedModel = null,
                    selectedVersion = null,
                    selectedFile = null,
                    selectedModelType = null
                )

            } catch (e: Exception) {
                DebugLogger.w(TAG, "Download failed: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    downloadProgress = null,
                    errorMessage = e.message ?: "Download failed"
                )
                _events.emit(ModelBrowserEvent.ShowError(e.message ?: "Download failed"))
            }
        }
    }

    /**
     * Clear the selected model.
     */
    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedModel = null,
            selectedVersion = null,
            selectedFile = null,
            selectedModelType = null
        )
    }

    /**
     * Toggle community images view.
     */
    fun toggleCommunityImages() {
        val newShowState = !_uiState.value.showCommunityImages
        DebugLogger.d(TAG, "toggleCommunityImages: newShowState=$newShowState")
        _uiState.value = _uiState.value.copy(showCommunityImages = newShowState)
        
        // Load images if showing for the first time
        if (newShowState && _uiState.value.communityImages.isEmpty()) {
            loadCommunityImages()
        }
    }

    /**
     * Load community images for the selected model version.
     */
    /**
     * Change community images sort order. Clears and reloads.
     */
    fun setCommunityImagesSort(sort: String) {
        _uiState.value = _uiState.value.copy(
            communityImagesSort = sort,
            communityImages = emptyList(),
            communityImagesCursor = null,
            hasMoreCommunityImages = true
        )
        loadCommunityImages()
    }

    fun loadCommunityImages() {
        DebugLogger.d(TAG, "loadCommunityImages: called")
        val version = _uiState.value.selectedVersion ?: run {
            DebugLogger.d(TAG, "loadCommunityImages: no selected version, returning")
            return
        }
        
        // Only Civitai has community images
        if (_uiState.value.selectedProvider != ModelProvider.CIVITAI) {
            DebugLogger.d(TAG, "loadCommunityImages: not Civitai provider, returning")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingCommunityImages = true)
            try {
                val modelId = _uiState.value.selectedModel?.id
                val browsingLevel = _uiState.value.nsfwLevels.fold(0) { acc, level -> acc or level }
                
                val (images, nextCursor) = civitaiTrpcService.getModelImages(
                    modelVersionId = version.id,
                    modelId = modelId,
                    limit = 20,
                    cursor = null,
                    browsingLevel = browsingLevel,
                    sort = _uiState.value.communityImagesSort
                )

                _uiState.value = _uiState.value.copy(
                    communityImages = images,
                    isLoadingCommunityImages = false,
                    communityImagesCursor = nextCursor,
                    hasMoreCommunityImages = nextCursor != null
                )
            } catch (e: Exception) {
                DebugLogger.w(TAG, "Failed to load community images: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoadingCommunityImages = false)
                _events.emit(ModelBrowserEvent.ShowError("Failed to load community images: ${e.message}"))
            }
        }
    }

    /**
     * Load more community images (pagination).
     */
    fun loadMoreCommunityImages() {
        val version = _uiState.value.selectedVersion ?: return
        val cursor = _uiState.value.communityImagesCursor ?: return
        
        if (_uiState.value.isLoadingCommunityImages || !_uiState.value.hasMoreCommunityImages) {
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingCommunityImages = true)
            try {
                val modelId = _uiState.value.selectedModel?.id
                val browsingLevel = _uiState.value.nsfwLevels.fold(0) { acc, level -> acc or level }
                
                val (newImages, nextCursor) = civitaiTrpcService.getModelImages(
                    modelVersionId = version.id,
                    modelId = modelId,
                    limit = 20,
                    cursor = cursor,
                    browsingLevel = browsingLevel,
                    sort = _uiState.value.communityImagesSort
                )

                _uiState.value = _uiState.value.copy(
                    communityImages = _uiState.value.communityImages + newImages,
                    isLoadingCommunityImages = false,
                    communityImagesCursor = nextCursor,
                    hasMoreCommunityImages = nextCursor != null
                )
            } catch (e: Exception) {
                DebugLogger.w(TAG, "Failed to load more community images: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoadingCommunityImages = false)
            }
        }
    }

    /**
     * Clear community images state.
     */
    fun clearCommunityImages() {
        _uiState.value = _uiState.value.copy(
            communityImages = emptyList(),
            isLoadingCommunityImages = false,
            showCommunityImages = false,
            communityImagesCursor = null,
            hasMoreCommunityImages = true
        )
    }

    /**
     * Save API key for the currently selected provider.
     */
    fun saveApiKey(key: String) {
        when (_uiState.value.selectedProvider) {
            ModelProvider.CIVITAI -> modelBrowserSettings.civitaiApiKey = key
            ModelProvider.HUGGINGFACE -> modelBrowserSettings.huggingfaceApiKey = key
        }
        _uiState.value = _uiState.value.copy(providerConfigured = key.isNotBlank())
    }

    /**
     * Reset API key for the currently selected provider (shows setup card).
     */
    fun resetProviderApiKey() {
        when (_uiState.value.selectedProvider) {
            ModelProvider.CIVITAI -> modelBrowserSettings.civitaiApiKey = ""
            ModelProvider.HUGGINGFACE -> modelBrowserSettings.huggingfaceApiKey = ""
        }
        _uiState.value = _uiState.value.copy(providerConfigured = false)
    }

    /**
     * Import workflow from community image metadata.
     */
    fun importWorkflow(json: String) {
        viewModelScope.launch {
            // TODO: Implement actual workflow import
            _events.emit(ModelBrowserEvent.ShowToast("Generation params copied to clipboard"))
        }
    }

    /**
     * Enable or disable animated thumbnails. Called after user confirms the dialog.
     */
    fun setShowAnimations(enabled: Boolean) {
        modelBrowserSettings.showAnimations = enabled
        _uiState.value = _uiState.value.copy(showAnimations = enabled)
    }

    /**
     * Set show NSFW setting.
     */
    fun toggleNsfwLevel(level: Int) {
        val current = _uiState.value.nsfwLevels.toMutableSet()
        if (level in current) {
            current.remove(level)
            // Don't allow empty — keep at least PG
            if (current.isEmpty()) current.add(1)
        } else {
            current.add(level)
        }
        modelBrowserSettings.nsfwLevels = current
        // Clear results immediately so stale thumbnails (selected with old NSFW levels)
        // don't stay visible while the new search is in flight.
        _uiState.value = _uiState.value.copy(
            nsfwLevels = current,
            searchResults = emptyList(),
            searchCursor = null,
            hasMoreResults = true
        )
        triggerDebouncedSearch()

        // If community images are currently visible, clear and reload with new browsingLevel.
        // Without this, already-loaded pages used the old level and new pages use the new one,
        // giving an inconsistent mixed feed.
        if (_uiState.value.showCommunityImages) {
            _uiState.value = _uiState.value.copy(
                communityImages = emptyList(),
                communityImagesCursor = null,
                hasMoreCommunityImages = true
            )
            loadCommunityImages()
        }
    }

    /**
     * Set blur threshold for NSFW images.
     */
    fun setBlurThreshold(threshold: Int) {
        modelBrowserSettings.blurThreshold = threshold
        _uiState.value = _uiState.value.copy(blurThreshold = threshold)
    }

    /**
     * Set browse level for image filtering (what images to show).
     * This is different from nsfwLevels which controls model fetching.
     */
    fun setBrowseLevel(level: Int) {
        modelBrowserSettings.browseLevel = level
        _uiState.value = _uiState.value.copy(browseLevel = level)
    }

    /**
     * Update API key for current provider (without saving yet).
     */
    fun updateApiKey(key: String) {
        _uiState.value = _uiState.value.copy(apiKey = key)
    }

    /**
     * Apply all settings at once (from settings sheet save).
     * Converts max NSFW level to bitmask.
     */
    fun applySettings(
        nsfwMax: Int,
        blurThreshold: Int,
        showAnimations: Boolean,
        apiKey: String
    ) {
        // Convert max level to bitmask (include all levels <= max)
        // Bitmask values: PG=1, PG-13=2, R=4, X=8, XXX=16
        val nsfwBitmask = when {
            nsfwMax >= 16 -> 1 or 2 or 4 or 8 or 16  // 31: PG + PG13 + R + X + XXX
            nsfwMax >= 8 -> 1 or 2 or 4 or 8           // 15: PG + PG13 + R + X
            nsfwMax >= 4 -> 1 or 2 or 4                // 7: PG + PG13 + R
            nsfwMax >= 2 -> 1 or 2                     // 3: PG + PG13
            nsfwMax >= 1 -> 1                          // 1: PG only
            else -> 1
        }

        // Convert bitmask to set of individual levels for storage
        val nsfwLevelSet = mutableSetOf<Int>()
        if (nsfwBitmask and 1 != 0) nsfwLevelSet.add(1)
        if (nsfwBitmask and 2 != 0) nsfwLevelSet.add(2)
        if (nsfwBitmask and 4 != 0) nsfwLevelSet.add(4)
        if (nsfwBitmask and 8 != 0) nsfwLevelSet.add(8)
        if (nsfwBitmask and 16 != 0) nsfwLevelSet.add(16)

        modelBrowserSettings.nsfwLevels = nsfwLevelSet
        modelBrowserSettings.blurThreshold = blurThreshold
        modelBrowserSettings.showAnimations = showAnimations

        when (_uiState.value.selectedProvider) {
            ModelProvider.CIVITAI -> modelBrowserSettings.civitaiApiKey = apiKey
            ModelProvider.HUGGINGFACE -> modelBrowserSettings.huggingfaceApiKey = apiKey
        }

        _uiState.value = _uiState.value.copy(
            nsfwLevels = nsfwLevelSet,
            blurThreshold = blurThreshold,
            showAnimations = showAnimations,
            apiKey = apiKey
        )

        // Re-search if search query is non-empty
        if (_uiState.value.searchQuery.isNotBlank()) {
            triggerDebouncedSearch()
        }
    }
}
