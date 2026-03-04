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
import sh.hnet.comfychair.service.CivitaiService
import sh.hnet.comfychair.service.CivitaiMeiliService
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
    val providerConfigured: Boolean = false, // tracks if current provider has API key
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
    // Search filters
    val filterModelType: String? = null, // "Checkpoint", "LORA", etc.
    val filterBaseModel: String? = null, // "Illustrious", "NoobAI", etc.
    val filterSort: String = "Most Downloaded", // "Highest Rated", "Most Downloaded", "Newest"
    val filterPeriod: String = "AllTime", // "AllTime", "Year", "Month", "Week", "Day"
    val showFilters: Boolean = false,
    val showNsfw: Boolean = false,
    val availableTypes: List<String> = emptyList(),      // from Meili facets
    val availableBaseModels: List<String> = emptyList()  // from Meili facets
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
    private val civitaiService = CivitaiService(modelBrowserSettings)
    private val civitaiMeiliService = CivitaiMeiliService()
    private val huggingFaceService = HuggingFaceService(modelBrowserSettings)
    private val comfyUIManagerService = ComfyUIManagerService()

    private val _uiState = MutableStateFlow(ModelBrowserUiState(
        providerConfigured = modelBrowserSettings.isCivitaiConfigured,
        showNsfw = modelBrowserSettings.showNsfw
    ))
    val uiState: StateFlow<ModelBrowserUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ModelBrowserEvent>()
    val events: SharedFlow<ModelBrowserEvent> = _events.asSharedFlow()

    private var searchDebounceJob: Job? = null

    /**
     * Trigger a debounced search. Called automatically on query/filter/NSFW changes.
     * @param delayMs debounce delay — 0 for immediate (filter changes), 800 for typing
     */
    private fun triggerDebouncedSearch(delayMs: Long = 0L) {
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            val query = _uiState.value.searchQuery.trim()
            if (query.isNotEmpty()) {
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
        val configured = when (provider) {
            ModelProvider.CIVITAI -> modelBrowserSettings.isCivitaiConfigured
            ModelProvider.HUGGINGFACE -> modelBrowserSettings.isHuggingFaceConfigured
        }
        _uiState.value = _uiState.value.copy(
            selectedProvider = provider,
            providerConfigured = configured,
            searchResults = emptyList(),
            selectedModel = null
        )
        modelBrowserSettings.preferredProvider = provider.name.lowercase()
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
            _uiState.value = _uiState.value.copy(isSearching = true, errorMessage = null)
            try {
                when (_uiState.value.selectedProvider) {
                    ModelProvider.CIVITAI -> {
                        if (!modelBrowserSettings.isCivitaiConfigured) {
                            throw IllegalStateException("Civitai API key not configured")
                        }
                        val state = _uiState.value
                        
                        // Use Meili for search
                        val meiliResult = civitaiMeiliService.searchModels(
                            query = query,
                            type = state.filterModelType,
                            baseModel = state.filterBaseModel,
                            sort = state.filterSort,
                            nsfw = state.showNsfw,
                            limit = 20
                        )
                        
                        _uiState.value = _uiState.value.copy(
                            searchResults = meiliResult.models,
                            isSearching = false,
                            availableTypes = meiliResult.facets?.types?.keys?.sortedByDescending { 
                                meiliResult.facets.types[it] 
                            } ?: emptyList(),
                            availableBaseModels = meiliResult.facets?.baseModels?.keys?.sortedByDescending { 
                                meiliResult.facets.baseModels[it] 
                            }?.take(20) ?: emptyList()
                        )
                        
                        if (meiliResult.models.isEmpty()) {
                            _events.emit(ModelBrowserEvent.ShowToast("No results found"))
                        }
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
     * Select a model to view details.
     */
    fun selectModel(model: ModelSearchResult) {
        val autoType = sh.hnet.comfychair.model.CivitaiTypeMapper.toComfyUIType(model.civitaiType)
        
        // Show immediately with what we have
        _uiState.value = _uiState.value.copy(
            selectedModel = model,
            selectedVersion = null,
            selectedFile = null,
            selectedModelType = autoType
        )
        // Clear community images when selecting a new model
        clearCommunityImages()
        
        // Load full details if Civitai (versions, description, etc.)
        if (model.provider == ModelProvider.CIVITAI) {
            viewModelScope.launch {
                try {
                    val fullModel = civitaiService.getModelDetails(model.id)
                    _uiState.value = _uiState.value.copy(
                        selectedModel = fullModel,
                        selectedVersion = fullModel.versions.firstOrNull()
                    )
                } catch (e: Exception) {
                    DebugLogger.w(TAG, "Failed to load model details: ${e.message}")
                    // Keep showing the basic model info from Meili
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
            selectedFile = version.files.firstOrNull()
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
        _uiState.value = _uiState.value.copy(showCommunityImages = newShowState)
        
        // Load images if showing for the first time
        if (newShowState && _uiState.value.communityImages.isEmpty()) {
            loadCommunityImages()
        }
    }

    /**
     * Load community images for the selected model version.
     */
    fun loadCommunityImages() {
        val version = _uiState.value.selectedVersion ?: return
        
        // Only Civitai has community images
        if (_uiState.value.selectedProvider != ModelProvider.CIVITAI) {
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingCommunityImages = true)
            try {
                val (images, nextCursor) = civitaiService.getModelImages(
                    modelVersionId = version.id,
                    limit = 20,
                    cursor = null
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
                val (newImages, nextCursor) = civitaiService.getModelImages(
                    modelVersionId = version.id,
                    limit = 20,
                    cursor = cursor
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
     * Set show NSFW setting.
     */
    fun setShowNsfw(value: Boolean) {
        modelBrowserSettings.showNsfw = value
        _uiState.value = _uiState.value.copy(showNsfw = value)
        triggerDebouncedSearch()
    }
}
