package sh.hnet.comfychair.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sh.hnet.comfychair.model.EnhancementOutputField
import sh.hnet.comfychair.model.EnhancementPrompt
import sh.hnet.comfychair.model.EnhancementResult
import sh.hnet.comfychair.model.PreEnhancementState
import sh.hnet.comfychair.model.PromptEnhancementMode
import sh.hnet.comfychair.model.PromptEnhancementProvider
import sh.hnet.comfychair.service.OpenRouterModels
import sh.hnet.comfychair.service.PromptEnhancementService
import sh.hnet.comfychair.storage.EnhancementPromptStore
import sh.hnet.comfychair.storage.PromptEnhancementSettings

data class PromptEnhancementUiState(
    val isEnhancing: Boolean = false,
    val error: String? = null,
    val isValidated: Boolean = false,
    val isTesting: Boolean = false,
    val testResult: String? = null,
    // Settings fields
    val provider: PromptEnhancementProvider = PromptEnhancementProvider.OPENAI,
    val apiKey: String = "",
    val model: String = "",
    val customBaseUrl: String = "",
    // Prompt library
    val availablePrompts: List<EnhancementPrompt> = emptyList(),
    val selectedPromptId: String? = null,
    val includeExamples: Boolean = false,
    val enabledOutputFields: Set<EnhancementOutputField> = EnhancementOutputField.DEFAULTS,
    // Post-enhancement state
    val hasEnhanced: Boolean = false,
    val preEnhancementState: PreEnhancementState? = null,
    val lastResult: EnhancementResult? = null,
    // OpenRouter model list
    val openRouterModels: List<OpenRouterModels.Model> = emptyList(),
    val isLoadingModels: Boolean = false,
    val modelFilter: String = ""
)

class PromptEnhancementViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = PromptEnhancementSettings(application)
    private val promptStore = EnhancementPromptStore(application)
    private val service = PromptEnhancementService(settings)

    private val _uiState = MutableStateFlow(loadState())
    val uiState: StateFlow<PromptEnhancementUiState> = _uiState.asStateFlow()

    private fun loadState(): PromptEnhancementUiState {
        return PromptEnhancementUiState(
            isValidated = settings.validated,
            provider = settings.provider,
            apiKey = settings.apiKey,
            model = settings.model,
            customBaseUrl = settings.customBaseUrl,
            availablePrompts = promptStore.getPrompts(),
            includeExamples = settings.includeExamples,
            enabledOutputFields = settings.enabledOutputFields
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
        _uiState.value = _uiState.value.copy(apiKey = key, isValidated = false, testResult = null)
    }

    fun setModel(model: String) {
        settings.model = model
        _uiState.value = _uiState.value.copy(model = model)
    }

    fun setCustomBaseUrl(url: String) {
        settings.customBaseUrl = url
        _uiState.value = _uiState.value.copy(customBaseUrl = url)
    }

    // --- Prompt Library ---

    fun addPrompt(prompt: EnhancementPrompt) {
        promptStore.addPrompt(prompt)
        _uiState.value = _uiState.value.copy(availablePrompts = promptStore.getPrompts())
    }

    fun updatePrompt(prompt: EnhancementPrompt) {
        promptStore.updatePrompt(prompt)
        _uiState.value = _uiState.value.copy(availablePrompts = promptStore.getPrompts())
    }

    fun deletePrompt(id: String) {
        promptStore.deletePrompt(id)
        val newPrompts = promptStore.getPrompts()
        _uiState.value = _uiState.value.copy(
            availablePrompts = newPrompts,
            selectedPromptId = if (_uiState.value.selectedPromptId == id) null
                else _uiState.value.selectedPromptId
        )
    }

    fun restoreBuiltinPrompts() {
        promptStore.restoreBuiltinPrompts()
        _uiState.value = _uiState.value.copy(availablePrompts = promptStore.getPrompts())
    }

    fun selectPrompt(id: String?) {
        _uiState.value = _uiState.value.copy(selectedPromptId = id)
    }

    fun setIncludeExamples(include: Boolean) {
        settings.includeExamples = include
        _uiState.value = _uiState.value.copy(includeExamples = include)
    }

    /**
     * Get prompts filtered for a specific generation mode.
     */
    fun getPromptsForMode(mode: PromptEnhancementMode): List<EnhancementPrompt> {
        return promptStore.getPromptsForMode(mode)
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
     * Enhance a prompt using the selected enhancement prompt (or first available for mode).
     * Stores pre-enhancement state for revert. Returns structured result via the callback.
     *
     * @param preState snapshot of current generation params (for revert)
     */
    fun enhance(
        prompt: String,
        mode: PromptEnhancementMode,
        promptId: String? = _uiState.value.selectedPromptId,
        preState: PreEnhancementState? = null,
        onResult: (EnhancementResult?) -> Unit
    ) {
        // Resolve the system prompt to use
        val enhancementPrompt = promptId?.let { promptStore.getPrompt(it) }
            ?: getPromptsForMode(mode).firstOrNull()

        if (enhancementPrompt == null) {
            onResult(null)
            return
        }

        val enabledFields = settings.enabledOutputFields
        // Store pre-enhancement state (only on first enhance, not reroll)
        val stateToStore = preState?.copy(enhancementPromptId = promptId)
            ?: _uiState.value.preEnhancementState

        _uiState.value = _uiState.value.copy(
            isEnhancing = true,
            error = null,
            preEnhancementState = stateToStore
        )

        viewModelScope.launch {
            try {
                val systemPrompt = enhancementPrompt.buildSystemPrompt(
                    enabledFields,
                    settings.includeExamples
                )
                val result = service.enhanceStructured(prompt, systemPrompt)
                _uiState.value = _uiState.value.copy(
                    isEnhancing = false,
                    hasEnhanced = true,
                    lastResult = result
                )
                onResult(result)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isEnhancing = false,
                    error = e.message
                )
                onResult(null)
            }
        }
    }

    /**
     * Clear the post-enhancement state (called after revert).
     */
    fun clearEnhancementState() {
        _uiState.value = _uiState.value.copy(
            hasEnhanced = false,
            preEnhancementState = null,
            lastResult = null
        )
    }

    fun testConnection() {
        _uiState.value = _uiState.value.copy(isTesting = true, testResult = null, error = null)
        viewModelScope.launch {
            try {
                val modelCount = service.testConnection()
                settings.validated = true
                _uiState.value = _uiState.value.copy(
                    isTesting = false,
                    isValidated = true,
                    testResult = "Connected ($modelCount models available)"
                )
            } catch (e: Exception) {
                settings.validated = false
                _uiState.value = _uiState.value.copy(
                    isTesting = false,
                    isValidated = false,
                    testResult = "Failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Refresh state from persisted settings. Call on screen resume
     * to pick up changes made in the settings screen.
     */
    fun refreshState() {
        val current = _uiState.value
        _uiState.value = current.copy(
            isValidated = settings.validated,
            provider = settings.provider,
            apiKey = settings.apiKey,
            model = settings.model,
            customBaseUrl = settings.customBaseUrl,
            availablePrompts = promptStore.getPrompts(),
            includeExamples = settings.includeExamples,
            enabledOutputFields = settings.enabledOutputFields
        )
    }

    fun setOutputFieldEnabled(field: EnhancementOutputField, enabled: Boolean) {
        val current = settings.enabledOutputFields.toMutableSet()
        if (enabled) current.add(field) else current.remove(field)
        // PROMPT is always enabled
        current.add(EnhancementOutputField.PROMPT)
        settings.enabledOutputFields = current
        _uiState.value = _uiState.value.copy(enabledOutputFields = current)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
