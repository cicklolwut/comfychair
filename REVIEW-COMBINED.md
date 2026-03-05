# Model Browser — Unified Code Review

**Date:** 2025-03-05  
**Reviewers:** Roxy (AI Subagent) × 2  
**Scope:** `service/`, `viewmodel/`, `ui/screens/`, `model/`, `storage/`

---

## Executive Summary

The model browser is functional but has critical performance issues around **memory management**, **recomposition stability**, and **resource sharing**. The architecture is generally sound but violates single-responsibility in places and lacks dependency injection. Priority fixes focus on preventing OOM conditions and eliminating unnecessary recompositions.

---

## 1. Performance Issues

### 1.1 🔴 CRITICAL: OkHttpClient Created Per Service

**Location:** `CivitaiService.kt:31`, `CivitaiMeiliService.kt:37`, `HuggingFaceService.kt:27`, `ComfyUIManagerService.kt:26`

Each service creates its own `OkHttpClient` instance with dedicated thread pools and connection managers. This wastes memory and prevents connection reuse across API calls.

**Fix:** Create a shared singleton:

```kotlin
object HttpModule {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .build()
    }
}

// In services:
private val client = HttpModule.client
```

---

### 1.2 🔴 CRITICAL: Image Viewer Memory Leak (HorizontalPager)

**Location:** `CommunityImagesScreen.kt:176-186`

`HorizontalPager` keeps neighboring pages composed (default `beyondBoundsPageCount = 1`). Each page loads a full-resolution bitmap:

```kotlin
var bitmap by remember(image.id) { mutableStateOf<Bitmap?>(null) }

LaunchedEffect(image.id) {
    val request = ImageRequest.Builder(context)
        .data(image.url)
        .size(dm.widthPixels, dm.heightPixels) // Full screen size
        .build()
    val result = context.imageLoader.execute(request)
    bitmap = if (result is SuccessResult) {
        result.image.toBitmap() // Holding raw Bitmap in state
    } else null
}
```

**Problem:** With 3 pages active, that's 3 high-res bitmaps (potentially 100-150MB on a 1440p device).

**Fixes:**

1. **Reduce pager scope:**
   ```kotlin
   HorizontalPager(
       state = pagerState,
       beyondBoundsPageCount = 0, // Only keep current page
       // ...
   )
   ```

2. **Let Coil manage memory:**
   ```kotlin
   SubcomposeAsyncImage(
       model = ImageRequest.Builder(LocalContext.current)
           .data(image.url)
           .memoryCachePolicy(CachePolicy.ENABLED)
           .build(),
       loading = {
           AsyncImage(model = image.thumbnailUrl, ...)
       },
       success = { state ->
           ZoomableImage(painter = state.painter, onSingleTap = onTap)
       }
   )
   ```

3. **Explicit cleanup if holding Bitmap:**
   ```kotlin
   DisposableEffect(image.id) {
       onDispose {
           bitmap?.recycle()
           bitmap = null
       }
   }
   ```

---

### 1.3 🔴 CRITICAL: Unstable Lambda Parameters in Grid Items

**Location:** `ModelBrowserScreen.kt` (LazyVerticalGrid items)

```kotlin
items(uiState.searchResults, key = { it.id }) { model ->
    ModelGridCard(
        model = model,
        onClick = { viewModel.selectModel(model) } // NEW LAMBDA EVERY RECOMPOSITION
    )
}
```

**Problem:** `{ viewModel.selectModel(model) }` creates a new lambda instance on every recomposition. Since lambdas aren't stable, every `ModelGridCard` recomposes when *any* part of `uiState` changes.

**Fix:** Use `remember` with explicit keys or method references:

```kotlin
// Option 1: Remember the callback
items(uiState.searchResults, key = { it.id }) { model ->
    val onClick = remember(model.id) { { viewModel.selectModel(model) } }
    ModelGridCard(model = model, onClick = onClick)
}

// Option 2: Pass ID, let ViewModel resolve
items(uiState.searchResults, key = { it.id }) { model ->
    ModelGridCard(
        model = model,
        onModelSelected = viewModel::selectModelById // Method reference = stable
    )
}
```

---

### 1.4 🔴 CRITICAL: Model Data Classes Not Marked Immutable

**Location:** `model/ModelBrowser.kt`

`ModelSearchResult`, `ModelVersion`, `ModelFile`, etc. are **not marked `@Stable`** or `@Immutable`. Compose can't prove stability, so it recomposes every item defensively.

**Fix:**

```kotlin
@Immutable
data class ModelSearchResult(
    val id: String,
    val name: String,
    val description: String?,
    val thumbnailUrl: String?,
    val animatedThumbnailUrl: String? = null,
    val downloadCount: Long?,
    val favoriteCount: Long?,
    val tags: List<String>,
    val creator: String?,
    val creatorId: Int? = null,
    val versions: List<ModelVersion>,
    val provider: ModelProvider,
    val civitaiType: String? = null,
    val baseModel: String? = null
)

@Immutable
data class ModelVersion(/* ... */)

@Immutable
data class ModelFile(/* ... */)

@Immutable
data class CommunityImage(/* ... */)
```

---

### 1.5 🔴 CRITICAL: ModelBrowserUiState is a Recomposition Bomb

**Location:** `ModelBrowserViewModel.kt:33-65`

`ModelBrowserUiState` has **25+ properties**. Any mutation triggers a full state copy and recomposition of all observers, even those only using a single field.

```kotlin
// Current: every change creates a full copy
_uiState.value = _uiState.value.copy(searchQuery = query)
```

**Problem:** When pagination loads, `searchResults` changes, causing every composable observing `uiState` to recompose — even those only using `selectedModel`.

**Fix:** Split into domain-specific state objects:

```kotlin
@Stable
data class ModelBrowserUiState(
    val searchState: SearchState,
    val detailState: ModelDetailState?,
    val downloadState: DownloadState
)

@Stable
data class SearchState(
    val query: String = "",
    val results: List<ModelSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val offset: Int = 0,
    val hasMore: Boolean = true
)

@Stable
data class FilterState(
    val modelType: String? = null,
    val baseModel: String? = null,
    val sort: String = "Most Downloaded",
    val nsfwLevels: Set<Int> = setOf(1, 2, 4)
)

@Stable
data class ModelDetailState(
    val selectedModel: ModelSearchResult?,
    val selectedVersion: ModelVersion?,
    val selectedFile: ModelFile?,
    val modelType: ModelType?,
    val communityImages: List<CommunityImage> = emptyList()
)

@Stable
data class DownloadState(
    val isDownloading: Boolean = false,
    val progress: Float? = null
)
```

Composables can observe only what they need. Changing a filter won't recompose the grid items.

---

### 1.6 🟡 MEDIUM: Scroll Prefetching Lacks Deduplication

**Location:** `ModelBrowserScreen.kt:136-151`

```kotlin
LaunchedEffect(gridState, uiState.searchResults) {
    snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
        .distinctUntilChanged()
        .collect { lastVisible ->
            val prefetchEnd = minOf(lastVisible + 8, uiState.searchResults.size)
            for (i in (lastVisible + 1) until prefetchEnd) {
                uiState.searchResults.getOrNull(i)?.thumbnailUrl?.let { url ->
                    imageLoader.enqueue(ImageRequest.Builder(context).data(url).build())
                }
            }
        }
}
```

**Problems:**
1. No size constraint on prefetch requests — downloading full-size images
2. No deduplication — scrolling back and forth re-enqueues same URLs
3. Including `uiState.searchResults` in key causes effect restart on every search result change

**Fix:**

```kotlin
val searchResults by rememberUpdatedState(uiState.searchResults)

LaunchedEffect(gridState) {
    val prefetched = mutableSetOf<String>()

    snapshotFlow {
        gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    }
    .distinctUntilChanged()
    .debounce(100) // Don't fire on every frame
    .collect { lastVisible ->
        val currentResults = searchResults
        val prefetchRange = (lastVisible + 1) until minOf(lastVisible + 6, currentResults.size)
        prefetchRange.forEach { i ->
            currentResults.getOrNull(i)?.thumbnailUrl?.let { url ->
                if (url !in prefetched) {
                    prefetched.add(url)
                    imageLoader.enqueue(
                        ImageRequest.Builder(context)
                            .data(url)
                            .size(450, 675) // Match grid cell size
                            .priority(Priority.LOW)
                            .build()
                    )
                }
            }
        }
    }
}
```

---

### 1.7 🟡 MEDIUM: Missing Request Cancellation

**Location:** `ModelBrowserViewModel.kt` (all network calls)

Network requests in `searchModels()`, `loadMoreResults()`, `loadCommunityImages()` are not properly cancellable when the user navigates away or starts a new search.

**Current behavior:** `searchDebounceJob?.cancel()` only cancels the delay, not the actual network request.

**Fix:**

```kotlin
private var searchJob: Job? = null
private var searchDebounceJob: Job? = null

private fun triggerDebouncedSearch(delayMs: Long = 0L) {
    searchDebounceJob?.cancel()
    searchDebounceJob = viewModelScope.launch {
        if (delayMs > 0) delay(delayMs)
        searchJob?.cancel() // Cancel in-flight request
        searchJob = launch { searchModels() }
    }
}
```

---

### 1.8 🟡 MEDIUM: Settings Read on Every Access

**Location:** `storage/ModelBrowserSettings.kt`

Every property getter reads from SharedPreferences synchronously:

```kotlin
var nsfwLevels: Set<Int>
    get() {
        val stored = prefs.getString(KEY_NSFW_LEVELS, null)
        // ... parsing on every read
    }
```

**Fix:** Cache values in memory, invalidate on writes:

```kotlin
private var _nsfwLevels: Set<Int>? = null

var nsfwLevels: Set<Int>
    get() = _nsfwLevels ?: loadNsfwLevels().also { _nsfwLevels = it }
    set(value) {
        _nsfwLevels = value
        prefs.edit().putString(KEY_NSFW_LEVELS, value.joinToString(",")).apply()
    }
```

---

### 1.9 🟡 MEDIUM: List Accumulation Without Cleanup

**Location:** `ModelBrowserViewModel.kt:230-248` (`loadMoreResults`)

```kotlin
_uiState.value = _uiState.value.copy(
    searchResults = _uiState.value.searchResults + meiliResult.models,
    // ...
)
```

**Problem:** Results accumulate indefinitely. A user browsing 500+ models keeps all in memory.

**Fix:** Implement a sliding window or cap max items:

```kotlin
val maxItems = 200
val newResults = (existingResults + newModels).takeLast(maxItems)
```

---

### 1.10 🟢 LOW: Tag Rows Use horizontalScroll (Already Addressed)

**Location:** `ModelBrowserScreen.kt` → `ModelGridCard`

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
    // ...
)
```

**Note:** Both reviewers initially suggested replacing with `LazyRow` to avoid creating a `ScrollState` per item. However, **tags are already capped at 6 per card** (via `.take(6)`), and a non-scrolling `Row` with 6 chips is perfectly fine. The `ScrollState` overhead is negligible for this use case.

**Status:** No action needed — already optimized.

---

### 1.11 🟢 LOW: Missing contentType in LazyVerticalGrid

**Location:** `ModelBrowserScreen.kt:107-128`

```kotlin
items(uiState.searchResults, key = { it.id }) { model ->
    ModelGridCard(...)
}
```

**Improvement:** Add `contentType` for better recycling:

```kotlin
items(
    items = uiState.searchResults,
    key = { it.id },
    contentType = { "model_card" }
) { model ->
    ModelGridCard(...)
}
```

---

## 2. Architecture Concerns

### 2.1 🔴 CRITICAL: Services Created in ViewModel Constructor

**Location:** `ModelBrowserViewModel.kt:70-73`

```kotlin
private val civitaiService = CivitaiService(modelBrowserSettings)
private val civitaiMeiliService = CivitaiMeiliService()
private val huggingFaceService = HuggingFaceService(modelBrowserSettings)
private val comfyUIManagerService = ComfyUIManagerService()
```

**Problems:**
1. No dependency injection — hard to test
2. Services recreated on ViewModel recreation (config changes)
3. No lifecycle awareness

**Fix:** Use Hilt/Koin for DI:

```kotlin
@HiltViewModel
class ModelBrowserViewModel @Inject constructor(
    private val civitaiService: CivitaiService,
    private val civitaiMeiliService: CivitaiMeiliService,
    private val huggingFaceService: HuggingFaceService,
    private val comfyUIManagerService: ComfyUIManagerService,
    private val modelBrowserSettings: ModelBrowserSettings
) : ViewModel() { ... }
```

---

### 2.2 🟡 MEDIUM: ViewModel Violates Single Responsibility

**Location:** `ModelBrowserViewModel.kt`

The ViewModel handles search, filters, selection, community images, downloads, **and** API key management. This violates single responsibility and makes testing difficult.

**Fix:** Extract use cases:

```kotlin
class SearchModelsUseCase(
    private val civitaiMeiliService: CivitaiMeiliService,
    private val huggingFaceService: HuggingFaceService
) {
    suspend fun execute(params: SearchParams): Result<SearchResult>
}

class DownloadModelUseCase(
    private val comfyUIManagerService: ComfyUIManagerService
) {
    suspend fun execute(params: DownloadParams): Result<Unit>
}

class ValidateApiKeyUseCase(
    private val civitaiService: CivitaiService,
    private val huggingFaceService: HuggingFaceService
) {
    suspend fun execute(provider: ModelProvider, key: String): Result<Unit>
}
```

Then inject use cases into the ViewModel instead of services directly.

---

### 2.3 🟡 MEDIUM: ConnectionManager Singleton Coupling

**Location:** `service/ComfyUIManagerService.kt`

```kotlin
private fun getServerUrl(): String {
    val connState = ConnectionManager.connectionState.value
    if (connState is ConnectionState.Connected) {
        return "${connState.protocol}://${connState.hostname}:${connState.port}"
    }
    throw IllegalStateException("Not connected to ComfyUI server")
}
```

**Problem:** Direct singleton access makes testing impossible without mocking statics.

**Fix:** Inject connection info:

```kotlin
class ComfyUIManagerService(
    private val serverUrlProvider: () -> String,
    private val client: OkHttpClient = HttpModule.client
)
```

---

### 2.4 🟡 MEDIUM: Manual JSON Parsing is Error-Prone

**Location:** All service files use `org.json.JSONObject`

Manual JSON parsing is verbose and error-prone:

```kotlin
val id = json.optString("id", "")
val name = json.optString("name", "Unknown")
// ... 30+ more lines per object
```

**Recommendation:** Migrate to kotlinx.serialization with data classes:

```kotlin
@Serializable
data class CivitaiModelResponse(
    val id: String,
    val name: String,
    val description: String? = null,
    // ...
)

// One line instead of 30:
val model = Json.decodeFromString<CivitaiModelResponse>(body)
```

---

### 2.5 🟢 LOW: API Key Stored Without Validation

**Location:** `ModelBrowserViewModel.kt:421`

```kotlin
fun saveApiKey(key: String) {
    when (_uiState.value.selectedProvider) {
        ModelProvider.CIVITAI -> modelBrowserSettings.civitaiApiKey = key
        // ...
    }
    _uiState.value = _uiState.value.copy(providerConfigured = key.isNotBlank())
}
```

**Problem:** No validation that the key actually works before marking configured.

**Fix:** Validate with a test API call:

```kotlin
fun saveApiKey(key: String) {
    viewModelScope.launch {
        _uiState.update { it.copy(isValidatingKey = true) }
        try {
            when (_uiState.value.selectedProvider) {
                ModelProvider.CIVITAI -> civitaiService.validateKey(key)
                ModelProvider.HUGGING_FACE -> huggingFaceService.validateKey(key)
            }
            modelBrowserSettings.civitaiApiKey = key
            _uiState.update { it.copy(providerConfigured = true, isValidatingKey = false) }
        } catch (e: Exception) {
            _events.emit(ModelBrowserEvent.ShowError("Invalid API key"))
            _uiState.update { it.copy(isValidatingKey = false) }
        }
    }
}
```

---

## 3. Compose-Specific Issues

### 3.1 🟡 MEDIUM: AndroidView for HTML Rendering Missing Update

**Location:** `ModelBrowserScreen.kt:642-651`

```kotlin
@Composable
fun HtmlText(html: String) {
    AndroidView(
        factory = { 
            TextView(it).apply {
                text = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
                textSize = 14f
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}
```

**Problems:**
1. No `update` lambda — if `html` changes, the view won't update
2. Creating new TextView on every composition if not properly remembered

**Fix:**

```kotlin
@Composable
fun HtmlText(html: String) {
    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                textSize = 14f
                setTextColor(/* use theme color */)
            }
        },
        update = { textView ->
            textView.text = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
        },
        modifier = Modifier.fillMaxWidth()
    )
}
```

---

### 3.2 🟢 LOW: Bitmap Not Explicitly Recycled

**Location:** `CommunityImagesScreen.kt:179`

```kotlin
var bitmap by remember(image.id) { mutableStateOf<Bitmap?>(null) }
```

When scrolling away, the bitmap reference is cleared but not explicitly recycled. On Android 8+, GC handles this, but explicit recycling helps with large bitmaps:

```kotlin
DisposableEffect(image.id) {
    onDispose { 
        bitmap?.recycle()
        bitmap = null
    }
}
```

---

## 4. Summary of Priorities

| Priority | Issue | Impact | Effort |
|----------|-------|--------|--------|
| 🔴 Critical | Shared OkHttpClient | High memory, no connection reuse | Low |
| 🔴 Critical | HorizontalPager memory | OOM on image viewer | Medium |
| 🔴 Critical | Unstable data classes (`@Immutable`) | Excessive recomposition | Low |
| 🔴 Critical | Unstable lambda parameters | Excessive recomposition | Low |
| 🔴 Critical | Monolithic UI state | Excessive recomposition | Medium |
| 🔴 Critical | Services in ViewModel | No DI, hard to test | Medium |
| 🟡 Medium | Request cancellation | Wasted network/battery | Medium |
| 🟡 Medium | Settings cache | Disk I/O on main thread | Low |
| 🟡 Medium | Prefetch deduplication | Redundant network requests | Low |
| 🟡 Medium | List accumulation cap | Memory creep | Low |
| 🟡 Medium | ViewModel SRP violation | Maintainability | High |
| 🟡 Medium | ConnectionManager coupling | Testability | Low |
| 🟡 Medium | AndroidView update | UI not reactive | Trivial |
| 🟢 Low | contentType | Minor perf | Trivial |
| 🟢 Low | Bitmap recycle | Minor memory | Trivial |
| 🟢 Low | API key validation | UX | Low |
| 🟢 Low | kotlinx.serialization | Code quality | High |

---

## 5. Action Plan (Sorted by Priority, then Effort)

### Phase 1: Critical Fixes (This Sprint)

1. **Share OkHttpClient** (🔴 Low effort)
   - Create `HttpModule` singleton
   - Update all service constructors
   - **Impact:** Immediate memory reduction, connection reuse

2. **Mark model classes `@Immutable`** (🔴 Low effort)
   - Add annotation to `ModelSearchResult`, `ModelVersion`, `ModelFile`, `CommunityImage`, `ImageStats`
   - **Impact:** Eliminates unnecessary recompositions

3. **Stabilize grid item lambdas** (🔴 Low effort)
   - Use `remember(model.id)` or method references
   - **Impact:** Prevents recomposition of all cards on any state change

4. **Reduce pager memory** (🔴 Medium effort)
   - Set `beyondBoundsPageCount = 0`
   - Replace bitmap state with Coil's `SubcomposeAsyncImage`
   - Add `DisposableEffect` for cleanup
   - **Impact:** Prevents OOM in image viewer

5. **Split UI state** (🔴 Medium effort)
   - Create `SearchState`, `FilterState`, `DetailState`, `DownloadState`
   - Update ViewModel to use nested states
   - **Impact:** Surgical recompositions

6. **Introduce DI** (🔴 Medium effort)
   - Add Hilt dependencies
   - Annotate ViewModel with `@HiltViewModel`
   - Provide service instances via modules
   - **Impact:** Testability, lifecycle management

### Phase 2: Medium Fixes (Next Sprint)

7. **Add request cancellation** (🟡 Medium effort)
   - Store network `Job` instances
   - Cancel on new request or navigation
   - **Impact:** Battery savings, prevents stale results

8. **Cache settings values** (🟡 Low effort)
   - Store parsed values in memory
   - Invalidate on writes
   - **Impact:** Eliminates main-thread I/O

9. **Prefetch deduplication** (🟡 Low effort)
   - Track prefetched URLs in `Set`
   - Add `.debounce(100)` and `.priority(LOW)`
   - Remove `uiState.searchResults` from effect key
   - **Impact:** Reduces redundant downloads

10. **Cap accumulated results** (🟡 Low effort)
    - Limit search results to 200 items
    - Use sliding window on pagination
    - **Impact:** Prevents memory creep

11. **Inject ConnectionManager** (🟡 Low effort)
    - Pass `serverUrlProvider` lambda
    - **Impact:** Testability

12. **Fix AndroidView update** (🟡 Trivial effort)
    - Add `update` lambda to `HtmlText`
    - **Impact:** Reactive HTML rendering

### Phase 3: Refactoring (Backlog)

13. **Extract use cases** (🟡 High effort)
    - Create `SearchModelsUseCase`, `DownloadModelUseCase`, `ValidateApiKeyUseCase`
    - Inject into ViewModel
    - **Impact:** Single responsibility, testability

14. **Migrate to kotlinx.serialization** (🟢 High effort)
    - Define data classes for API responses
    - Replace `org.json` parsing
    - **Impact:** Code quality, type safety

15. **Add API key validation** (🟢 Low effort)
    - Test key before saving
    - Show validation feedback
    - **Impact:** Better UX

16. **Add contentType** (🟢 Trivial effort)
    - Annotate LazyGrid items
    - **Impact:** Minor perf boost

17. **Explicit bitmap recycle** (🟢 Trivial effort)
    - Add `DisposableEffect` where bitmaps are held
    - **Impact:** Minor memory optimization

---

## Estimated Impact

| Metric | Before | After Phase 1 | After Phase 2 |
|--------|--------|---------------|---------------|
| Peak memory (image viewer) | ~150MB | ~50MB | ~50MB |
| Recompositions (grid scroll) | 100+ per scroll | <10 per scroll | <5 per scroll |
| Network connections | 4 pools | 1 pool | 1 pool |
| Settings I/O | Every access | Cached | Cached |
| Stale network requests | Common | None | None |

---

## Conclusion

The most urgent fixes are **shared HTTP client**, **image viewer memory management**, and **Compose stability annotations**. These three alone will eliminate the majority of performance issues. Dependency injection and use case extraction are medium-term goals that improve maintainability without immediate user-facing impact.
