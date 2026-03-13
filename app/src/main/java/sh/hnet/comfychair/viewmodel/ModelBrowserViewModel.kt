package sh.hnet.comfychair.viewmodel

import android.app.Application
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.imageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sh.hnet.comfychair.model.CommunityImage
import sh.hnet.comfychair.model.CommunityPost
import sh.hnet.comfychair.model.ModelProvider
import sh.hnet.comfychair.model.ModelSearchResult
import sh.hnet.comfychair.model.ModelType
import sh.hnet.comfychair.model.ModelVersion
import sh.hnet.comfychair.model.ModelFile
import sh.hnet.comfychair.connection.ConnectionManager
import sh.hnet.comfychair.connection.ConnectionState
import sh.hnet.comfychair.db.dao.ImageResourceWithVersion
import sh.hnet.comfychair.db.entity.ImageGenerationData
import sh.hnet.comfychair.db.repository.CivitaiCacheRepository
import sh.hnet.comfychair.model.GenerationMetadata
import sh.hnet.comfychair.model.GenerationResource
import sh.hnet.comfychair.service.CivitaiTrpcService
import sh.hnet.comfychair.service.HuggingFaceService
import sh.hnet.comfychair.service.ComfyChairHelperService
import sh.hnet.comfychair.service.HelperVersionInfo
import sh.hnet.comfychair.service.InstallResult
import sh.hnet.comfychair.service.OrganizedModel
import sh.hnet.comfychair.service.ScanStatus
import sh.hnet.comfychair.service.ComfyUIManagerService
// AppSettings is an object singleton, not instantiated
import sh.hnet.comfychair.storage.ModelBrowserSettings
import sh.hnet.comfychair.storage.CivitaiMediaCache
import sh.hnet.comfychair.storage.MediaCacheEntry
import sh.hnet.comfychair.storage.MediaPrefetchManager
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
    val downloadPercent: Float = 0f, // 0..1 for determinate progress bar
    val errorMessage: String? = null,
    val communityImages: List<CommunityImage> = emptyList(),
    val communityPosts: List<CommunityPost> = emptyList(),
    val isLoadingCommunityImages: Boolean = false,
    val showCommunityImages: Boolean = false,
    val communityImagesCursor: String? = null,
    val hasMoreCommunityImages: Boolean = true,
    val communityImagesSort: String = "Most Reactions",
    // Community image filters
    val communityTypeFilter: String? = null,          // null=all, "image", "video"
    val communityMetaOnly: Boolean = false,           // only show entries with generation metadata
    val communityFeaturedFirst: Boolean = true,        // pinned posts at top
    val communityGroupByPost: Boolean = false,         // group images by post vs flat grid
    // Search filters
    val filterModelType: String? = null, // "Checkpoint", "LORA", etc.
    val filterBaseModel: String? = null, // "Illustrious", "NoobAI", etc.
    val filterSort: String = "Most Downloaded", // "Highest Rated", "Most Downloaded", "Newest"
    val filterPeriod: String = "AllTime", // "AllTime", "Year", "Month", "Week", "Day"
    val showFilters: Boolean = false,
    val nsfwLevels: Set<Int> = ModelBrowserSettings.DEFAULT_NSFW_LEVELS,
    val showAnimations: Boolean = false,
    val autoplayVideos: Boolean = false,
    val blurThreshold: Int = 2, // Default: blur images above PG-13
    val apiKey: String = "", // API key for current provider
    val browseLevel: Int = 31, // Default: show all images (XXX)

    // Pagination (cursor-based for trpc)
    val searchCursor: String? = null,
    val hasMoreResults: Boolean = true,
    val isLoadingMore: Boolean = false,

    // ComfyChair Helper node integration
    val installedVersionIds: Set<Long> = emptySet(),
    val helperAvailable: Boolean? = null, // null = checking, true/false = result
    val helperVersion: HelperVersionInfo? = null,
    val helperUpdateAvailable: Boolean = false,
    val helperInstalling: Boolean = false,
    val helperUpdating: Boolean = false,
    val managerAvailable: Boolean? = null, // null = not checked, for install path
    val organizedModels: List<OrganizedModel> = emptyList(),
    val isLoadingOrganized: Boolean = false,
    val scanStatus: ScanStatus? = null
)

/**
 * Events emitted by model browser operations.
 */
sealed class ModelBrowserEvent {
    data class ShowToast(val message: String) : ModelBrowserEvent()
    data class ShowError(val message: String) : ModelBrowserEvent()
    /** Prompt user to restart ComfyUI (after install/update). */
    data class PromptRestart(val reason: String) : ModelBrowserEvent()
    /** Prompt user to store their Civitai API key on the helper for future downloads. */
    data class PromptStoreApiKey(val jobId: String) : ModelBrowserEvent()
}

/**
 * ViewModel for model browser screen.
 */
class ModelBrowserViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "ModelBrowserViewModel"
    }

    private val modelBrowserSettings = ModelBrowserSettings(getApplication())
    private val civitaiCacheRepository = CivitaiCacheRepository.getInstance(getApplication())
    private val civitaiTrpcService = CivitaiTrpcService(modelBrowserSettings, getApplication())
    private val huggingFaceService = HuggingFaceService(modelBrowserSettings)
    val mediaCache = CivitaiMediaCache.getInstance(getApplication())
    private val prefetchManager = MediaPrefetchManager(mediaCache)
    private val serverUrlProvider: () -> String = {
        val connState = ConnectionManager.connectionState.value
        if (connState is ConnectionState.Connected) {
            "${connState.protocol}://${connState.hostname}:${connState.port}"
        } else {
            throw IllegalStateException("Not connected to ComfyUI server")
        }
    }

    private val comfyUIManagerService = ComfyUIManagerService(
        serverUrlProvider = serverUrlProvider,
        credentialsProvider = { ConnectionManager.client.getCredentials() }
    )

    private val helperService = ComfyChairHelperService(
        serverUrlProvider = serverUrlProvider,
        credentialsProvider = { ConnectionManager.client.getCredentials() }
    )

    private val _uiState = MutableStateFlow(ModelBrowserUiState(
        // Civitai trpc works without API key, so it's always "configured"
        // Only HuggingFace still requires a key
        providerConfigured = true,
        nsfwLevels = modelBrowserSettings.nsfwLevels,
        showAnimations = modelBrowserSettings.showAnimations,
        autoplayVideos = modelBrowserSettings.autoplayVideos,
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
        checkHelperStatus()
        refreshInstalledVersions()
    }

    /**
     * Check if the helper node is installed and get version info.
     * Called on init and after install/update.
     */
    fun checkHelperStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val available = helperService.isAvailable()
            _uiState.value = _uiState.value.copy(helperAvailable = available)

            if (available) {
                // Get version info for update check
                val versionInfo = helperService.getVersionInfo()
                _uiState.value = _uiState.value.copy(
                    helperVersion = versionInfo,
                    helperUpdateAvailable = versionInfo?.updateAvailable == true
                )
                // Load organized models
                loadOrganizedModels()
            } else {
                // Check if ComfyUI-Manager is available (for install path)
                val managerAvailable = helperService.isManagerAvailable()
                _uiState.value = _uiState.value.copy(managerAvailable = managerAvailable)
            }
        }
    }

    /**
     * Install the helper node via ComfyUI-Manager.
     */
    fun installHelper() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(helperInstalling = true)
            try {
                val result = helperService.installViaManager()
                when (result) {
                    InstallResult.SUCCESS -> {
                        _events.emit(ModelBrowserEvent.PromptRestart(
                            "ComfyChair Helper installed successfully."
                        ))
                    }
                    InstallResult.ALREADY_INSTALLED -> {
                        _events.emit(ModelBrowserEvent.PromptRestart(
                            "ComfyChair Helper is already installed."
                        ))
                    }
                    InstallResult.SECURITY_BLOCKED -> {
                        _events.emit(ModelBrowserEvent.ShowError(
                            "Blocked by ComfyUI-Manager security policy. " +
                            "Security level must be 'normal-' or lower."
                        ))
                    }
                    InstallResult.FAILED -> {
                        _events.emit(ModelBrowserEvent.ShowError(
                            "Installation failed. Check ComfyUI logs for details."
                        ))
                    }
                }
            } catch (e: Exception) {
                _events.emit(ModelBrowserEvent.ShowError("Install failed: ${e.message}"))
            } finally {
                _uiState.value = _uiState.value.copy(helperInstalling = false)
            }
        }
    }

    /**
     * Update the helper node (git pull).
     */
    fun updateHelper() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(helperUpdating = true)
            try {
                val result = helperService.update()
                if (result.ok) {
                    _uiState.value = _uiState.value.copy(helperUpdateAvailable = false)
                    if (result.restartRequired) {
                        _events.emit(ModelBrowserEvent.PromptRestart(
                            "Helper updated successfully."
                        ))
                    } else {
                        _events.emit(ModelBrowserEvent.ShowToast("Helper updated!"))
                    }
                } else {
                    _events.emit(ModelBrowserEvent.ShowError(
                        "Update failed: ${result.error ?: "Unknown error"}"
                    ))
                }
            } catch (e: Exception) {
                _events.emit(ModelBrowserEvent.ShowError("Update failed: ${e.message}"))
            } finally {
                _uiState.value = _uiState.value.copy(helperUpdating = false)
            }
        }
    }

    /**
     * Load organized models from the helper node.
     */
    fun loadOrganizedModels() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoadingOrganized = true)
            try {
                val models = helperService.getOrganizedModels()
                _uiState.value = _uiState.value.copy(
                    organizedModels = models,
                    isLoadingOrganized = false
                )
            } catch (e: Exception) {
                DebugLogger.w(TAG, "Failed to load organized models: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoadingOrganized = false)
            }
        }
    }

    /**
     * Trigger a model rescan on the server.
     */
    fun triggerRescan(force: Boolean = false) {
        viewModelScope.launch {
            val success = helperService.triggerScan(force)
            if (success) {
                _events.emit(ModelBrowserEvent.ShowToast("Scan started..."))
                // Poll scan status for a bit
                pollScanStatus()
            } else {
                _events.emit(ModelBrowserEvent.ShowError("Failed to start scan"))
            }
        }
    }

    /**
     * Restart ComfyUI via Manager's reboot endpoint.
     * After restart, the WebSocket reconnect loop will re-establish connection
     * and checkHelperStatus() will detect the newly installed/updated helper.
     */
    fun restartComfyUI() {
        viewModelScope.launch {
            _events.emit(ModelBrowserEvent.ShowToast("Restarting ComfyUI..."))
            helperService.restartComfyUI()
            // The WebSocket disconnect handler in ConnectionManager will
            // trigger reconnection attempts automatically
        }
    }

    private fun pollScanStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            repeat(60) { // poll for up to 2 minutes
                val status = helperService.getScanStatus() ?: return@launch
                _uiState.value = _uiState.value.copy(scanStatus = status)
                if (!status.running) {
                    // Scan finished — reload organized models and installed versions
                    loadOrganizedModels()
                    refreshInstalledVersions()
                    _uiState.value = _uiState.value.copy(scanStatus = null)
                    return@launch
                }
                delay(2000)
            }
            _uiState.value = _uiState.value.copy(scanStatus = null)
        }
    }

    fun refreshInstalledVersions() {
        viewModelScope.launch(Dispatchers.IO) {
            val serverId = try {
                serverUrlProvider().trimEnd('/')
            } catch (e: IllegalStateException) {
                // Not connected — load whatever is cached in Room
                val connState = ConnectionManager.connectionState.value
                if (connState is ConnectionState.Connected) {
                    "${connState.protocol}://${connState.hostname}:${connState.port}"
                } else {
                    return@launch
                }
            }

            // Fetch from the node if available, then persist to Room
            if (helperService.isAvailable()) {
                val ids = helperService.getInstalledVersionIds()
                civitaiCacheRepository.upsertInstalledVersions(serverId, ids)
            }

            // Always read back from Room so UiState reflects persisted state
            val persisted = civitaiCacheRepository.getInstalledVersionIds(serverId)
            _uiState.value = _uiState.value.copy(installedVersionIds = persisted)
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
                        
                        // Combine nsfwLevels (model filter) with browseLevel (image filter)
                        // nsfwLevels controls which models appear; browseLevel controls cover images
                        val nsfwBitmask = state.nsfwLevels.fold(0) { acc, level -> acc or level }
                        val browsingLevel = nsfwBitmask and state.browseLevel
                        
                        // Use trpc for search
                        val trpcResult = civitaiTrpcService.searchModels(
                            query = query,
                            types = state.filterModelType?.let { listOf(it) },
                            baseModels = state.filterBaseModel?.let { listOf(it) },
                            sort = state.filterSort,
                            period = state.filterPeriod,
                            browsingLevel = browsingLevel,
                            limit = 100,
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
                        
                        // Prefetch cover thumbnails so they're ready before the user scrolls
                        prefetchCoverImages(trpcResult.models)
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
     * Load more search results (pagination).
     */
    fun loadMoreResults() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMoreResults) return
        if (state.selectedProvider != ModelProvider.CIVITAI) return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            try {
                // Re-read state inside the coroutine so we always use the latest NSFW levels and filters
                val currentState = _uiState.value
                val currentCursor = currentState.searchCursor
                
                // If the cursor was cleared by a new search before this coroutine started, bail out.
                if (currentCursor == null) {
                    _uiState.value = _uiState.value.copy(isLoadingMore = false)
                    return@launch
                }

                // Combine nsfwLevels with browseLevel for API call
                val nsfwBitmask = currentState.nsfwLevels.fold(0) { acc, level -> acc or level }
                val browsingLevel = nsfwBitmask and currentState.browseLevel
                
                val trpcResult = civitaiTrpcService.searchModels(
                    query = currentState.searchQuery.trim(),
                    types = currentState.filterModelType?.let { listOf(it) },
                    baseModels = currentState.filterBaseModel?.let { listOf(it) },
                    sort = currentState.filterSort,
                    period = currentState.filterPeriod,
                    browsingLevel = browsingLevel,
                    limit = 100,
                    cursor = currentCursor
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
                    // Prefetch cover thumbnails for the new page
                    prefetchCoverImages(trpcResult.models)
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
            communityPosts = emptyList(),
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
     * Download the selected model.
     *
     * Fallback chain:
     * 1. Helper node installed → use /comfychair/download (supports any model)
     * 2. No helper → show error directing user to install the helper node
     *
     * The ComfyUI Manager /manager/queue/install_model endpoint is NOT used
     * because it validates against a whitelist that excludes most Civitai models.
     */
    fun downloadModel(subfolder: String = "", storeApiKey: Boolean = false) {
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
                        filename = version.filename.ifBlank {
                            // getAll doesn't include files — derive filename
                            // from the download URL or use a fallback
                            version.files.firstOrNull()?.filename
                                ?: "model_v${version.id}.safetensors"
                        }
                    }
                    ModelProvider.HUGGINGFACE -> {
                        val file = _uiState.value.selectedFile
                            ?: throw IllegalStateException("No file selected")
                        downloadUrl = file.downloadUrl
                        filename = file.filename
                    }
                }

                val savePath = if (subfolder.isBlank()) "default" else subfolder

                // Check if helper node is available
                if (!helperService.isAvailable()) {
                    throw IllegalStateException(
                        "ComfyChair Helper node is required for model downloads. " +
                        "Install it from Server Management in Settings."
                    )
                }

                // Get API key from app settings (for Civitai downloads)
                val apiKey = modelBrowserSettings.civitaiApiKey.takeIf { it.isNotBlank() }

                // Check if helper already has a stored key
                val helperHasKey = helperService.hasStoredApiKey()

                val job = helperService.downloadModel(
                    url = downloadUrl,
                    filename = filename,
                    modelType = modelType.value,
                    savePath = savePath,
                    versionId = version.id?.toLongOrNull(),
                    apiKey = if (!helperHasKey) apiKey else null,
                    storeKey = storeApiKey && !helperHasKey
                )

                // If we sent an API key but didn't store it, prompt user
                if (apiKey != null && !helperHasKey && !storeApiKey) {
                    _events.emit(ModelBrowserEvent.PromptStoreApiKey(job.id))
                }

                // Poll progress until done or error
                _uiState.value = _uiState.value.copy(
                    downloadProgress = "Starting download...",
                    downloadPercent = 0f
                )

                var jobId = job.id
                while (true) {
                    delay(500) // poll every 500ms
                    val status = helperService.getDownloadStatus(jobId) ?: break

                    when (status.status) {
                        "downloading" -> {
                            _uiState.value = _uiState.value.copy(
                                downloadProgress = formatDownloadProgress(status),
                                downloadPercent = (status.progress / 100.0).toFloat()
                                    .coerceIn(0f, 1f)
                            )
                        }
                        "done" -> {
                            _uiState.value = _uiState.value.copy(
                                isDownloading = false,
                                downloadProgress = null,
                                downloadPercent = 0f
                            )
                            _events.emit(ModelBrowserEvent.ShowToast("Download complete!"))
                            break
                        }
                        "error" -> {
                            throw RuntimeException(status.error ?: "Download failed on server")
                        }
                        else -> { /* queued — keep polling */ }
                    }
                }

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
                    downloadPercent = 0f,
                    errorMessage = e.message ?: "Download failed"
                )
                _events.emit(ModelBrowserEvent.ShowError(e.message ?: "Download failed"))
            }
        }
    }

    /**
     * Store the Civitai API key on the helper node for future downloads.
     * Called when user confirms the PromptStoreApiKey dialog.
     */
    fun storeApiKeyOnHelper() {
        viewModelScope.launch {
            try {
                val apiKey = modelBrowserSettings.civitaiApiKey
                if (apiKey.isBlank()) return@launch
                helperService.setCivitaiApiKey(apiKey)
                _events.emit(ModelBrowserEvent.ShowToast("API key saved on server"))
            } catch (e: Exception) {
                _events.emit(ModelBrowserEvent.ShowError("Failed to save API key: ${e.message}"))
            }
        }
    }

    /**
     * Clear the selected model and free community image memory.
     */
    private fun formatDownloadProgress(job: ComfyChairHelperService.DownloadJob): String {
        val percent = job.progress.toInt()
        val doneMB = job.bytesDone / (1024.0 * 1024.0)
        val totalMB = job.bytesTotal / (1024.0 * 1024.0)
        return if (job.bytesTotal > 0) {
            "$percent% — ${"%.1f".format(doneMB)} / ${"%.1f".format(totalMB)} MB"
        } else {
            "$percent% — ${"%.1f".format(doneMB)} MB"
        }
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedModel = null,
            selectedVersion = null,
            selectedFile = null,
            selectedModelType = null,
            communityImages = emptyList(),
            communityPosts = emptyList(),
            isLoadingCommunityImages = false,
            showCommunityImages = false,
            communityImagesCursor = null,
            hasMoreCommunityImages = true
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
            communityPosts = emptyList(),
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
                val nsfwBitmask = _uiState.value.nsfwLevels.fold(0) { acc, level -> acc or level }
                val browsingLevel = nsfwBitmask and _uiState.value.browseLevel
                
                val typeFilter = _uiState.value.communityTypeFilter?.let { listOf(it) }
                val result = civitaiTrpcService.getModelImages(
                    modelVersionId = version.id,
                    modelId = modelId,
                    limit = 20,
                    cursor = null,
                    browsingLevel = browsingLevel,
                    sort = _uiState.value.communityImagesSort,
                    types = typeFilter
                )

                _uiState.value = _uiState.value.copy(
                    communityImages = result.images,
                    communityPosts = result.posts,
                    isLoadingCommunityImages = false,
                    communityImagesCursor = result.nextCursor,
                    hasMoreCommunityImages = result.nextCursor != null
                )
                // Cache metadata + trigger prefetch
                cacheAndPrefetch(result.images, modelId, version.id)
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
                val nsfwBitmask = _uiState.value.nsfwLevels.fold(0) { acc, level -> acc or level }
                val browsingLevel = nsfwBitmask and _uiState.value.browseLevel
                
                val typeFilter = _uiState.value.communityTypeFilter?.let { listOf(it) }
                val result = civitaiTrpcService.getModelImages(
                    modelVersionId = version.id,
                    modelId = modelId,
                    limit = 20,
                    cursor = cursor,
                    browsingLevel = browsingLevel,
                    sort = _uiState.value.communityImagesSort,
                    types = typeFilter
                )

                _uiState.value = _uiState.value.copy(
                    communityImages = _uiState.value.communityImages + result.images,
                    communityPosts = _uiState.value.communityPosts + result.posts,
                    isLoadingCommunityImages = false,
                    communityImagesCursor = result.nextCursor,
                    hasMoreCommunityImages = result.nextCursor != null
                )
                // Cache metadata + trigger prefetch
                cacheAndPrefetch(result.images, modelId, version.id)
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
            communityPosts = emptyList(),
            isLoadingCommunityImages = false,
            showCommunityImages = false,
            communityImagesCursor = null,
            hasMoreCommunityImages = true
        )
    }

    /**
     * Set community image type filter (null=all, "image", "video").
     * Triggers reload since this is a server-side filter.
     */
    fun setCommunityTypeFilter(type: String?) {
        _uiState.value = _uiState.value.copy(
            communityTypeFilter = type,
            communityImages = emptyList(),
            communityPosts = emptyList(),
            communityImagesCursor = null,
            hasMoreCommunityImages = true
        )
        loadCommunityImages()
    }

    /** Toggle metadata-only filter (client-side). */
    fun setCommunityMetaOnly(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(communityMetaOnly = enabled)
    }

    /** Toggle featured/pinned posts first (client-side). */
    fun setCommunityFeaturedFirst(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(communityFeaturedFirst = enabled)
    }

    /** Toggle group-by-post view mode (client-side). */
    fun setCommunityGroupByPost(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(communityGroupByPost = enabled)
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
                communityPosts = emptyList(),
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
     * Triggers a model re-search AND a community image reload if currently shown.
     */
    fun setBrowseLevel(level: Int) {
        modelBrowserSettings.browseLevel = level
        _uiState.value = _uiState.value.copy(browseLevel = level)
        // Re-search with new browseLevel (affects cover image selection via combined browsingLevel)
        if (_uiState.value.searchQuery.isNotBlank() || _uiState.value.searchResults.isNotEmpty()) {
            searchModels()
        }
        // Reload community images if currently shown — browseLevel is passed to the API as browsingLevel
        if (_uiState.value.showCommunityImages) {
            _uiState.value = _uiState.value.copy(
                communityImages = emptyList(),
                communityPosts = emptyList(),
                communityImagesCursor = null,
                hasMoreCommunityImages = true
            )
            loadCommunityImages()
        }
    }

    /**
     * Update community images sort without triggering a reload.
     * Used by the community filter sheet — reload happens on sheet dismiss (Fix 3).
     */
    fun updateCommunitySort(sort: String) {
        _uiState.value = _uiState.value.copy(communityImagesSort = sort)
    }

    /**
     * Update community type filter without triggering a reload.
     * Used by the community filter sheet — reload happens on sheet dismiss (Fix 3).
     */
    fun updateCommunityTypeFilter(type: String?) {
        _uiState.value = _uiState.value.copy(communityTypeFilter = type)
    }

    /**
     * Update browse level state only (persists to settings, no API reload).
     * Used by the community filter sheet — reload happens on sheet dismiss (Fix 3).
     */
    fun updateBrowseLevel(level: Int) {
        modelBrowserSettings.browseLevel = level
        _uiState.value = _uiState.value.copy(browseLevel = level)
    }

    /**
     * Reload community images from scratch if they are currently shown.
     * Called after the community filter sheet is dismissed with changed filters.
     */
    fun reloadCommunityImages() {
        if (_uiState.value.showCommunityImages) {
            _uiState.value = _uiState.value.copy(
                communityImages = emptyList(),
                communityPosts = emptyList(),
                communityImagesCursor = null,
                hasMoreCommunityImages = true
            )
            loadCommunityImages()
        }
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
        autoplayVideos: Boolean = false,
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
        modelBrowserSettings.autoplayVideos = autoplayVideos

        when (_uiState.value.selectedProvider) {
            ModelProvider.CIVITAI -> modelBrowserSettings.civitaiApiKey = apiKey
            ModelProvider.HUGGINGFACE -> modelBrowserSettings.huggingfaceApiKey = apiKey
        }

        _uiState.value = _uiState.value.copy(
            nsfwLevels = nsfwLevelSet,
            blurThreshold = blurThreshold,
            showAnimations = showAnimations,
            autoplayVideos = autoplayVideos,
            apiKey = apiKey
        )

        // Re-search if search query is non-empty
        if (_uiState.value.searchQuery.isNotBlank()) {
            triggerDebouncedSearch()
        }
    }

    // --- Media Cache ---

    /**
     * Prefetch cover image thumbnails for a list of model search results.
     * Enqueues Coil image loads so thumbnails are warm in the disk/memory cache
     * before the user scrolls to them. For video covers also fires HEAD requests
     * to warm the CDN transcode cache.
     */
    private fun prefetchCoverImages(models: List<ModelSearchResult>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val context = getApplication<Application>()
            val imageLoader = context.imageLoader

            // Only prefetch first 10 — visible cards + one row ahead.
            // Rest load on-demand via AsyncImage (which naturally prioritizes visible items).
            val prefetchBatch = models.take(10)

            // Enqueue Coil prefetch for each non-null thumbnail URL.
            // Use the same target size (450x675) as the LaunchedEffect prefetch in the UI so
            // the decoded bitmap lands in the correct size bucket — avoiding a second decode
            // at display time (which would be a cache miss on the sized variant).
            prefetchBatch.mapNotNull { it.thumbnailUrl }.forEach { url ->
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(450, 675)
                    .build()
                imageLoader.enqueue(request)
            }

            // Warm CDN transcode cache for video covers (first 10 only)
            val videoTranscodeUrls = prefetchBatch
                .filter { it.coverImageType == "video" }
                .mapNotNull { it.coverVideoUrl }
            if (videoTranscodeUrls.isNotEmpty()) {
                prefetchManager.warmTranscodeCache(videoTranscodeUrls)
            }
        }
    }

    /**
     * Store community image metadata in cache DB and trigger prefetch if enabled.
     * Also fires background HEAD requests to warm CDN transcode cache for videos.
     */
    private fun cacheAndPrefetch(
        images: List<CommunityImage>,
        modelId: String?,
        modelVersionId: String
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Batch upsert metadata
            val entries = images.map { img ->
                val transcodeUrl = if (img.type == "video") {
                    img.url.replace("/original=true/", "/transcode=true,optimized=true/")
                } else null

                MediaCacheEntry(
                    id = img.id.toString(),
                    type = img.type,
                    urlOriginal = img.url,
                    urlTranscode = transcodeUrl,
                    urlThumbnail = img.thumbnailUrl,
                    width = img.width,
                    height = img.height,
                    nsfwLevel = img.nsfwLevel,
                    modelId = modelId,
                    modelVersionId = modelVersionId
                )
            }
            mediaCache.upsertBatch(entries)

            // Warm CDN transcode cache for videos (fire-and-forget HEAD requests)
            val videoTranscodeUrls = entries
                .filter { it.type == "video" && it.urlTranscode != null }
                .mapNotNull { it.urlTranscode }
            if (videoTranscodeUrls.isNotEmpty()) {
                prefetchManager.warmTranscodeCache(videoTranscodeUrls)
            }

            // Prefetch files if enabled
            if (mediaCache.prefetchEnabled) {
                val uncached = mediaCache.getUncachedItems(
                    modelVersionId = modelVersionId,
                    type = "video",  // prioritize videos (images handled by Coil)
                    limit = mediaCache.prefetchCount
                )
                prefetchManager.prefetch(uncached)
            }
        }
    }

    /**
     * Lazy-load generation metadata for a community image.
     * Cache-first: checks Room DB for a fresh (< 24h) cached result before hitting the network.
     * Called when user opens fullscreen viewer — the trpc list endpoint
     * doesn't include meta, so we fetch it via REST on demand.
     */
    fun fetchImageMetadata(imageId: Long, postId: Long? = null, onResult: (GenerationMetadata?) -> Unit) {
        viewModelScope.launch {
            // 1. Check cache on IO dispatcher (awaited — on the critical path)
            val cached = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                civitaiCacheRepository.getFreshGenerationData(imageId)
            }

            if (cached != null) {
                val resources = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    civitaiCacheRepository.getResourcesWithVersions(imageId)
                }
                val meta = buildMetadataFromCache(cached, resources)
                // Update the image in state with the reconstructed metadata
                val updated = _uiState.value.communityImages.map { img ->
                    if (img.id == imageId) img.copy(meta = meta) else img
                }
                _uiState.value = _uiState.value.copy(communityImages = updated)
                onResult(meta)
                return@launch
            }

            // 2. Cache miss — fetch from network
            val meta = civitaiTrpcService.getImageMetadata(imageId, postId)

            // Update the image in state with the fetched metadata
            if (meta != null) {
                val updated = _uiState.value.communityImages.map { img ->
                    if (img.id == imageId) img.copy(meta = meta) else img
                }
                _uiState.value = _uiState.value.copy(communityImages = updated)

                // 3. Cache the fetched result (fire-and-forget on IO)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    civitaiCacheRepository.cacheGenerationData(
                        imageId = imageId,
                        process = null,          // not available via REST metadata endpoint
                        onSite = false,
                        prompt = meta.prompt,
                        negativePrompt = meta.negativePrompt,
                        cfgScale = meta.cfgScale,
                        steps = meta.steps,
                        sampler = meta.sampler,
                        seed = meta.seed,
                        size = null,             // not available via REST metadata endpoint
                        model = null,
                        version = null,
                        clipSkip = null,
                        denoisingStrength = null,
                        canRemix = false,
                        hideMeta = false,
                        resources = meta.resources
                    )
                }
            }

            onResult(meta)
        }
    }

    /**
     * Reconstruct a [GenerationMetadata] from cached Room data.
     * Resources are populated from the stored [ImageResourceWithVersion] join rows.
     */
    private fun buildMetadataFromCache(
        cached: ImageGenerationData,
        resources: List<ImageResourceWithVersion>
    ): GenerationMetadata {
        val genResources = resources.mapNotNull { rWithV ->
            val v = rWithV.version ?: return@mapNotNull null
            GenerationResource(
                name = v.modelName,
                type = v.modelType,
                weight = rWithV.resource.strength,
                modelVersionId = rWithV.resource.versionId
            )
        }
        return GenerationMetadata(
            prompt = cached.prompt,
            negativePrompt = cached.negativePrompt,
            sampler = cached.sampler,
            steps = cached.steps,
            cfgScale = cached.cfgScale,
            seed = cached.seed,
            baseModel = null, // not stored on ImageGenerationData
            resources = genResources
        )
    }

    /**
     * Clear all cached media files (keeps metadata).
     */
    fun clearMediaCache() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            mediaCache.clearCachedFiles()
        }
    }
}
