# MVVM Alignment Plan — ComfyChair

## Priority 1: ViewModel lifecycle (STATE LOSS BUG)

### 1a. ModelBrowserViewModel → AndroidViewModel

**Problem:** Created as `ModelBrowserViewModel(context)` inline in composable. Every recomposition creates a new instance — search results, filters, scroll position all lost.

**Current:**
```kotlin
// SettingsNavHost.kt
composable(SettingsRoute.ModelBrowser.route) {
    ModelBrowserScreen(
        viewModel = ModelBrowserViewModel(context = LocalContext.current),
        ...
    )
}

// ModelBrowserViewModel.kt
class ModelBrowserViewModel(context: Context) : ViewModel() {
    private val modelBrowserSettings = ModelBrowserSettings(context)
```

**Fix:**
```kotlin
// ModelBrowserViewModel.kt
class ModelBrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val modelBrowserSettings = ModelBrowserSettings(getApplication())
    private val civitaiService = CivitaiService(modelBrowserSettings)
    private val civitaiMeiliService = CivitaiMeiliService()
    // ... rest unchanged

// SettingsNavHost.kt
composable(SettingsRoute.ModelBrowser.route) {
    val viewModel: ModelBrowserViewModel = viewModel()
    ModelBrowserScreen(
        viewModel = viewModel,
        onNavigateBack = onNavigateToGeneration
    )
}
```

**Files:** `ModelBrowserViewModel.kt`, `SettingsNavHost.kt`
**Risk:** Low — just changes inheritance and instantiation

---

## Priority 2: Decouple child composables from ViewModel

### 2a. SearchFilters — replace viewModel with callbacks

**Problem:** Takes `viewModel: ModelBrowserViewModel` directly. Can't preview, can't test, tightly coupled.

**Current:**
```kotlin
fun SearchFilters(
    uiState: ModelBrowserUiState,
    viewModel: ModelBrowserViewModel
)
```

**Fix:**
```kotlin
fun SearchFilters(
    uiState: ModelBrowserUiState,
    onFilterTypeChanged: (String?) -> Unit,
    onFilterBaseModelChanged: (String?) -> Unit,
    onFilterSortChanged: (String) -> Unit,
    onFilterPeriodChanged: (String) -> Unit
)
```

**Caller changes:**
```kotlin
SearchFilters(
    uiState = uiState,
    onFilterTypeChanged = viewModel::setFilterModelType,
    onFilterBaseModelChanged = viewModel::setFilterBaseModel,
    onFilterSortChanged = viewModel::setFilterSort,
    onFilterPeriodChanged = viewModel::setFilterPeriod
)
```

**Files:** `ModelBrowserScreen.kt`
**Risk:** None — purely structural

### 2b. ModelDetailBottomSheet — replace viewModel with state + callbacks

**Problem:** Takes `viewModel: ModelBrowserViewModel`, calls multiple methods directly.

**Current calls from the sheet:**
- `viewModel.uiState.collectAsState()` (reads state)
- `viewModel.selectVersion(version)`
- `viewModel.toggleCommunityImages()`
- `viewModel.loadMoreCommunityImages()`
- `viewModel.selectFile(file)`

**Fix:**
```kotlin
fun ModelDetailBottomSheet(
    model: ModelSearchResult,
    uiState: ModelBrowserUiState,
    onDismiss: () -> Unit,
    onSelectVersion: (ModelVersion) -> Unit,
    onToggleCommunityImages: () -> Unit,
    onLoadMoreCommunityImages: () -> Unit,
    onSelectFile: (ModelFile) -> Unit,
    onDownloadRequested: () -> Unit
)
```

**Files:** `ModelBrowserScreen.kt`
**Risk:** Low — more parameters but cleaner contract

### 2c. DownloadConfigDialog — replace viewModel with state + callbacks

**Current calls:**
- `viewModel.uiState.collectAsState()`
- `viewModel.selectModelType(type)`
- `viewModel.selectFile(file)`
- `viewModel.downloadModel(subfolder)`

**Fix:**
```kotlin
fun DownloadConfigDialog(
    model: ModelSearchResult,
    uiState: ModelBrowserUiState,
    onDismiss: () -> Unit,
    onSelectModelType: (ModelType) -> Unit,
    onSelectFile: (ModelFile) -> Unit,
    onDownload: (subfolder: String) -> Unit
)
```

**Files:** `ModelBrowserScreen.kt`
**Risk:** None

---

## Priority 3: Semantic ViewModel API

### 3a. Replace `setCivitaiApiKey("")` with `resetApiKey()`

**Problem:** Screen calls `setCivitaiApiKey("")` to trigger "Change Key" UI. The Screen shouldn't know that empty string = show setup card.

**Fix:** Add explicit method:
```kotlin
// ModelBrowserViewModel.kt
fun resetProviderApiKey() {
    when (_uiState.value.selectedProvider) {
        ModelProvider.CIVITAI -> {
            modelBrowserSettings.civitaiApiKey = ""
            _uiState.value = _uiState.value.copy(providerConfigured = false)
        }
        ModelProvider.HUGGINGFACE -> {
            modelBrowserSettings.huggingfaceApiKey = ""
            _uiState.value = _uiState.value.copy(providerConfigured = false)
        }
    }
}
```

**Screen change:**
```kotlin
// Before:
TextButton(onClick = { viewModel.setCivitaiApiKey("") }) { Text("Change Key") }
// After:
TextButton(onClick = { viewModel.resetProviderApiKey() }) { Text("Change Key") }
```

Remove `setCivitaiApiKey()` and `setHuggingFaceApiKey()` from public API. Make them `private` — only called internally from `saveApiKey(key)` and `resetProviderApiKey()`.

Add:
```kotlin
fun saveApiKey(key: String) {
    when (_uiState.value.selectedProvider) {
        ModelProvider.CIVITAI -> {
            modelBrowserSettings.civitaiApiKey = key
            _uiState.value = _uiState.value.copy(providerConfigured = key.isNotBlank())
        }
        ModelProvider.HUGGINGFACE -> {
            modelBrowserSettings.huggingfaceApiKey = key
            _uiState.value = _uiState.value.copy(providerConfigured = key.isNotBlank())
        }
    }
}
```

**Files:** `ModelBrowserViewModel.kt`, `ModelBrowserScreen.kt`
**Risk:** None

### 3b. Workflow import — move to ViewModel

**Problem:** `onImportWorkflow` in `ModelDetailBottomSheet` is currently a Toast no-op.

**Fix:** Add a ViewModel method that handles the import (even if it just emits a toast event for now), so the logic is in the right layer:
```kotlin
// ModelBrowserViewModel.kt
fun importWorkflow(json: String) {
    viewModelScope.launch {
        // TODO: Implement actual workflow import
        _events.emit(ModelBrowserEvent.ShowToast("Workflow copied to clipboard"))
    }
}
```

**Files:** `ModelBrowserViewModel.kt`, `ModelBrowserScreen.kt`
**Risk:** None

---

## Priority 4: PromptEnhancementViewModel cleanup

### 4a. Make `settings` and `promptStore` private

**Problem:** Public vals allow Screen to read settings directly, bypassing state.

**Current:**
```kotlin
val settings = PromptEnhancementSettings(application)
val promptStore = EnhancementPromptStore(application)
```

**Audit:** Checked all Screens — neither `viewModel.settings` nor `viewModel.promptStore` are accessed from any Screen. The generation screens only use `viewModel.uiState`, `viewModel.enhance()`, `viewModel.getPromptsForMode()`, etc. So this is safe to just make `private`.

**Fix:**
```kotlin
private val settings = PromptEnhancementSettings(application)
private val promptStore = EnhancementPromptStore(application)
```

**Files:** `PromptEnhancementViewModel.kt`
**Risk:** None — verified no external callers

### 4b. `getPromptsForMode()` → move to state

**Problem:** `getPromptsForMode(mode)` is called from generation Screens to get the prompt list. This is a synchronous getter that reads from `promptStore` — should be reactive state.

**Current usage in TextToImageScreen:**
```kotlin
enhancementPrompts = if (enhancementState.isValidated)
    enhancementViewModel.getPromptsForMode(PromptEnhancementMode.TEXT_TO_IMAGE)
```

**Fix:** Add filtered prompts per mode to the state. When prompts change, update the derived lists:
```kotlin
// PromptEnhancementUiState
val textToImagePrompts: List<EnhancementPrompt> = emptyList(),
val imageToImagePrompts: List<EnhancementPrompt> = emptyList(),
val textToVideoPrompts: List<EnhancementPrompt> = emptyList(),
val imageToVideoPrompts: List<EnhancementPrompt> = emptyList(),
```

Update these whenever prompts are modified (add/delete/update/restore).

**Files:** `PromptEnhancementViewModel.kt`, `TextToImageScreen.kt`, `ImageToImageScreen.kt`, `TextToVideoScreen.kt`, `ImageToVideoScreen.kt`
**Risk:** Medium — touches multiple screens. Test prompt lists after each mode change.

---

## Execution Order

1. **1a** — AndroidViewModel (fixes actual bug, standalone change)
2. **3a** — Semantic API (small, clean, no dependencies)
3. **3b** — Workflow import (trivial)
4. **2a** — SearchFilters callbacks (easy win)
5. **2b** — ModelDetailBottomSheet callbacks (more params but straightforward)
6. **2c** — DownloadConfigDialog callbacks (same pattern as 2b)
7. **4a** — Private settings/promptStore (one-liner, safe)
8. **4b** — getPromptsForMode → state (last — most complex, touches most files)

**Estimated scope:** ~200-300 lines changed across 6 files. No new dependencies. No behavior changes — purely structural.
