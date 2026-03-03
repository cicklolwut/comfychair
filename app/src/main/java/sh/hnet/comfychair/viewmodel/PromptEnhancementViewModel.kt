package sh.hnet.comfychair.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sh.hnet.comfychair.model.PromptEnhancementMode
import sh.hnet.comfychair.model.PromptEnhancementProvider
import sh.hnet.comfychair.service.OpenRouterModels
import sh.hnet.comfychair.service.PromptEnhancementService
import sh.hnet.comfychair.storage.PromptEnhancementSettings

data class PromptEnhancementUiState(
    val isEnhancing: Boolean = false,
    val error: String? = null,
    val isConfigured: Boolean = false,
    // Settings fields
    val provider: PromptEnhancementProvider = PromptEnhancementProvider.OPENAI,
    val apiKey: String = "",
    val model: String = "",
    val customBaseUrl: String = "",
    // System prompts per mode
    val systemPrompts: Map<PromptEnhancementMode, String> = emptyMap(),
    // OpenRouter model list
    val openRouterModels: List<OpenRouterModels.Model> = emptyList(),
    val isLoadingModels: Boolean = false,
    val modelFilter: String = ""
)

class PromptEnhancementViewModel(application: Application) : AndroidViewModel(application) {
    val settings = PromptEnhancementSettings(application)
    private val service = PromptEnhancementService(settings)

    private val _uiState = MutableStateFlow(loadState())
    val uiState: StateFlow<PromptEnhancementUiState> = _uiState.asStateFlow()

    private fun loadState(): PromptEnhancementUiState {
        val prompts = PromptEnhancementMode.entries.associateWith { settings.getSystemPrompt(it) }
        return PromptEnhancementUiState(
            isConfigured = settings.isConfigured,
            provider = settings.provider,
            apiKey = settings.apiKey,
            model = settings.model,
            customBaseUrl = settings.customBaseUrl,
            systemPrompts = prompts
        )
    }

    fun setProvider(provider: PromptEnhancementProvider) {
        settings.provider = provider
        settings.model = PromptEnhancementProvider.defaultModel(provider)
        _uiState.value = loadState()
        if (provider == PromptEnhancementProvider.OPENROUTER) {
            fetchOpenRouterModels()
        }
    }

    fun setApiKey(key: String) {
        settings.apiKey = key
        _uiState.value = _uiState.value.copy(apiKey = key, isConfigured = key.isNotBlank())
    }

    fun setModel(model: String) {
        settings.model = model
        _uiState.value = _uiState.value.copy(model = model)
    }

    fun setCustomBaseUrl(url: String) {
        settings.customBaseUrl = url
        _uiState.value = _uiState.value.copy(customBaseUrl = url)
    }

    fun setSystemPrompt(mode: PromptEnhancementMode, prompt: String) {
        settings.setSystemPrompt(mode, prompt)
        _uiState.value = _uiState.value.copy(
            systemPrompts = _uiState.value.systemPrompts + (mode to prompt)
        )
    }

    fun resetSystemPrompt(mode: PromptEnhancementMode) {
        settings.resetSystemPrompt(mode)
        val defaultPrompt = PromptEnhancementSettings.DEFAULT_PROMPTS[mode] ?: ""
        _uiState.value = _uiState.value.copy(
            systemPrompts = _uiState.value.systemPrompts + (mode to defaultPrompt)
        )
    }

    fun resetAllSystemPrompts() {
        settings.resetAllSystemPrompts()
        _uiState.value = loadState()
    }

    fun setModelFilter(filter: String) {
        _uiState.value = _uiState.value.copy(modelFilter = filter)
    }

    fun fetchOpenRouterModels(forceRefresh: Boolean = false) {
        _uiState.value = _uiState.value.copy(isLoadingModels = true)
        viewModelScope.launch {
            try {
                val models = OpenRouterModels.getModels(forceRefresh)
                _uiState.value = _uiState.value.copy(
                    openRouterModels = models,
                    isLoadingModels = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingModels = false,
                    error = "Failed to load models: ${e.message}"
                )
            }
        }
    }

    /**
     * Enhance a prompt. Returns the enhanced text via the callback.
     */
    fun enhance(
        prompt: String,
        mode: PromptEnhancementMode,
        onResult: (String?) -> Unit
    ) {
        _uiState.value = _uiState.value.copy(isEnhancing = true, error = null)
        viewModelScope.launch {
            try {
                val enhanced = service.enhance(prompt, mode)
                _uiState.value = _uiState.value.copy(isEnhancing = false)
                onResult(enhanced)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isEnhancing = false,
                    error = e.message
                )
                onResult(null)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
