# Model Browser Code Review

**Date:** 2025-03-05  
**Reviewer:** Roxy (AI Subagent)  
**Scope:** `service/`, `viewmodel/ModelBrowserViewModel.kt`, `ui/screens/ModelBrowserScreen.kt`, `ui/screens/CommunityImagesScreen.kt`, `model/ModelBrowser.kt`, `storage/ModelBrowserSettings.kt`

---

## Executive Summary

The model browser implementation is functional but has several areas that need attention. The most critical issues are around **memory management in the image viewer**, **unstable Compose state causing unnecessary recompositions**, and **missing cancellation handling for network requests**. The architecture is generally sound but could benefit from better separation between data and UI layers.

---

## 1. Performance Optimizations

### 1.1 🔴 CRITICAL: OkHttpClient Instance Per Service

**Location:** `CivitaiService.kt:31`, `CivitaiMeiliService.kt:37`, `HuggingFaceService.kt:27`, `ComfyUIManagerService.kt:26`

Each service creates its own `OkHttpClient` instance:

```kotlin
private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()
```

**Problem:** OkHttpClient manages connection pools and threads. Multiple instances waste resources and prevent connection reuse across services.

**Fix:** Create a single shared OkHttpClient instance (singleton or DI-provided):

```kotlin
object HttpClientProvider {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .build()
    }
}
```

### 1.2 🟡 MEDIUM: Missing Request Cancellation

**Location:** `ModelBrowserViewModel.kt` (all network calls)

Network requests in `searchModels()`, `loadMoreResults()`, `loadCommunityImages()` etc. are not properly cancellable when the user navigates away or starts a new search.

**Current behavior:** When user types fast, multiple debounced searches fire. The `searchDebounceJob?.cancel()` only cancels the delay, not the actual network request.

**Fix:** Store and cancel the network Job:

```kotlin
private var searchJob: Job? = null
private var searchDebounceJob: Job? = null

private fun triggerDebouncedSearch(delayMs: Long = 0L) {
    searchDebounceJob?.cancel()
    searchDebounceJob = viewModelScope.launch {
        if (delayMs > 0) delay(delayMs)
        searchJob?.cancel() // Cancel any in-flight request
        searchJob = launch { searchModels() }
    }
}
```

### 1.3 🟡 MEDIUM: Redundant State Copies in ViewModel

**Location:** `ModelBrowserViewModel.kt:186-199`

In `searchModels()`, the UI state is copied multiple times:

```kotlin
_uiState.value = _uiState.value.copy(isSearching = true, errorMessage = null)
// ...
_uiState.value = _uiState.value.copy(
    searchResults = meiliResult.models,
    searchOffset = meiliResult.models.size,
    // ... 5 more properties
)
```

**Fix:** Use `update {}` for atomic updates (requires `MutableStateFlow.update`):

```kotlin
_uiState.update { it.copy(isSearching = true, errorMessage = null) }
```

This is already safe with StateFlow, but `update {}` is clearer and handles concurrent updates better.

### 1.4 🟢 LOW: Image Prefetching Could Be Smarter

**Location:** `ModelBrowserScreen.kt:136-151`

Current prefetch logic:
```kotlin
for (i in (lastVisible + 1) until prefetchEnd) {
    uiState.searchResults.getOrNull(i)?.thumbnailUrl?.let { url ->
        imageLoader.enqueue(ImageRequest.Builder(context).data(url).build())
    }
}
```

**Improvement:** The prefetch window (+8 items) is reasonable, but consider:
1. Prefetch in both directions (user might scroll up)
2. Use `MemoryCache.Key` to check if already cached before enqueueing
3. Lower priority for prefetch requests: `.priority(Priority.LOW)`

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

### 2.2 🟡 MEDIUM: UI State Class Too Large

**Location:** `ModelBrowserViewModel.kt:33-65`

`ModelBrowserUiState` has **25+ properties**. This is a code smell suggesting the screen is doing too much.

**Suggested Split:**
1. `SearchState` — query, filters, results, pagination
2. `ModelDetailState` — selectedModel, selectedVersion, communityImages
3. `DownloadState` — isDownloading, progress

```kotlin
@Stable
data class ModelBrowserUiState(
    val searchState: SearchState,
    val detailState: ModelDetailState?,
    val downloadState: DownloadState
)
```

### 2.3 🟡 MEDIUM: Settings Read on Every Access

**Location:** `ModelBrowserSettings.kt`

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

### 2.4 🟢 LOW: JSON Parsing Without kotlinx.serialization

**Location:** All service files use `org.json.JSONObject`

Manual JSON parsing is verbose and error-prone:

```kotlin
val id = json.optString("id", "")
val name = json.optString("name", "Unknown")
// ... 30+ more lines
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

## 3. Memory Usage

### 3.1 🔴 CRITICAL: Full-Resolution Images in HorizontalPager

**Location:** `CommunityImagesScreen.kt:176-186`

```kotlin
LaunchedEffect(image.id) {
    isLoading = true
    val dm = context.resources.displayMetrics
    val request = ImageRequest.Builder(context)
        .data(image.url)
        .size(dm.widthPixels, dm.heightPixels)
        .build()
    // ...
}
```

**Problem:** The `HorizontalPager` keeps 3 pages in memory by default (current ± 1). Each page loads a full-res bitmap constrained to screen size. On a 1440p device with 3 high-res images, that's potentially **100-150MB** of bitmaps.

**Fixes:**
1. Reduce pager's `beyondBoundsPageCount`:
   ```kotlin
   HorizontalPager(
       state = pagerState,
       beyondBoundsPageCount = 0, // Only keep current page
       // ...
   )
   ```

2. Use Coil's `memoryCachePolicy(CachePolicy.DISABLED)` for full-res images since they're already paged.

3. Better: Keep thumbnails in memory, load full-res only for current page:
   ```kotlin
   val isCurrentPage = page == pagerState.currentPage
   if (isCurrentPage) { loadFullRes() } else { showThumbnail() }
   ```

### 3.2 🟡 MEDIUM: List Accumulation Without Cleanup

**Location:** `ModelBrowserViewModel.kt:230-248` (loadMoreResults)

```kotlin
_uiState.value = _uiState.value.copy(
    searchResults = _uiState.value.searchResults + meiliResult.models,
    // ...
)
```

**Problem:** Results accumulate indefinitely. A user browsing 500+ models keeps all in memory.

**Fix:** Implement a sliding window or limit max items:

```kotlin
val maxItems = 200
val newResults = (existingResults + newModels).takeLast(maxItems)
```

### 3.3 🟢 LOW: Bitmap Not Recycled

**Location:** `CommunityImagesScreen.kt:179`

```kotlin
var bitmap by remember(image.id) { mutableStateOf<Bitmap?>(null) }
```

When scrolling away, the bitmap reference is cleared but not explicitly recycled. On Android 8+, this is usually fine (GC handles it), but for large bitmaps, explicit recycling can help:

```kotlin
DisposableEffect(image.id) {
    onDispose { bitmap?.recycle() }
}
```

---

## 4. Compose-Specific Issues

### 4.1 🔴 CRITICAL: Unstable Lambda Captures Causing Recomposition

**Location:** `ModelBrowserScreen.kt:137-151`

```kotlin
LaunchedEffect(gridState, uiState.searchResults) {
    snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
        .distinctUntilChanged()
        .collect { lastVisible ->
            val prefetchEnd = minOf(lastVisible + 8, uiState.searchResults.size)
            for (i in (lastVisible + 1) until prefetchEnd) {
                uiState.searchResults.getOrNull(i)?.thumbnailUrl?.let { url ->
                    imageLoader.enqueue(...)
                }
            }
        }
}
```

**Problem:** `uiState.searchResults` in the key means this effect restarts on every search result change, even though the logic only needs the list for prefetching.

**Fix:** Remove `uiState.searchResults` from key, use `rememberUpdatedState`:

```kotlin
val searchResults by rememberUpdatedState(uiState.searchResults)

LaunchedEffect(gridState) {
    snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
        .distinctUntilChanged()
        .collect { lastVisible ->
            val currentResults = searchResults // captures latest
            // ...
        }
}
```

### 4.2 🔴 CRITICAL: Non-Stable Data Classes in LazyGrid Items

**Location:** `ModelBrowserScreen.kt:114`

```kotlin
items(uiState.searchResults, key = { it.id }) { model ->
    ModelGridCard(model = model, ...)
}
```

`ModelSearchResult` is **not marked `@Stable`** despite being a data class. Lists (`tags`, `versions`) are mutable by default in Kotlin.

**Problem:** Compose can't prove stability, so it recomposes every item on any state change.

**Fix in `ModelBrowser.kt`:**

```kotlin
@Immutable // Even stronger than @Stable
data class ModelSearchResult(
    val id: String,
    val name: String,
    val description: String?,
    val thumbnailUrl: String?,
    val animatedThumbnailUrl: String? = null,
    val downloadCount: Long?,
    val favoriteCount: Long?,
    val tags: List<String>, // Already immutable due to data class copy
    val creator: String?,
    val creatorId: Int? = null,
    val versions: List<ModelVersion>,
    val provider: ModelProvider,
    val civitaiType: String? = null,
    val baseModel: String? = null
)
```

Also mark `ModelVersion`, `ModelVersionImage`, `ModelFile`, `CommunityImage`, etc.

### 4.3 🟡 MEDIUM: Remember with Mutable Default

**Location:** `ModelBrowserScreen.kt:65`

```kotlin
var showFilterSheet by remember { mutableStateOf(false) }
```

This is fine, but similar patterns exist with more complex objects that aren't properly remembered:

**Location:** `CommunityImagesScreen.kt:40`
```kotlin
val gridState = rememberLazyGridState()
```

This is correct. However, in `ModelBrowserScreen.kt:109`:

```kotlin
val gridState = rememberLazyGridState()
```

**Potential Issue:** If `ModelBrowserScreen` recomposes with a new `viewModel` instance (unlikely but possible), grid state is lost.

### 4.4 🟡 MEDIUM: Heavy Composable Inside LazyColumn Item

**Location:** `ModelBrowserScreen.kt` → `ModelGridCard`

```kotlin
@Composable
fun ModelGridCard(
    model: ModelSearchResult,
    filterType: String?,
    filterBaseModel: String?,
    showAnimations: Boolean = false,
    onClick: () -> Unit
) {
    // ...
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()), // NEW SCROLL STATE PER ITEM
        // ...
    )
}
```

**Problem:** Each card creates its own `ScrollState` for the tags row. With 100+ cards, that's 100+ scroll states.

**Fix:** Use `LazyRow` for tags (only renders visible):

```kotlin
if (model.tags.isNotEmpty()) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(model.tags.take(6)) { tag ->
            MiniChip(text = tag, ...)
        }
    }
}
```

### 4.5 🟢 LOW: Missing `contentType` in LazyVerticalGrid

**Location:** `ModelBrowserScreen.kt:107-128`

```kotlin
LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    state = gridState,
    // ...
) {
    items(uiState.searchResults, key = { it.id }) { model ->
        ModelGridCard(...)
    }
    
    if (uiState.isLoadingMore) {
        item(span = { GridItemSpan(2) }) {
            // Loading indicator
        }
    }
}
```

**Improvement:** Add `contentType` for better recycling:

```kotlin
items(
    items = uiState.searchResults,
    key = { it.id },
    contentType = { "model_card" } // Helps Compose reuse compositions
) { model ->
    ModelGridCard(...)
}

item(
    span = { GridItemSpan(2) },
    contentType = "loading"
) {
    // ...
}
```

---

## 5. Additional Issues

### 5.1 API Key Stored Even When Invalid

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

**Fix:** Validate with a test API call before saving:

```kotlin
fun saveApiKey(key: String) {
    viewModelScope.launch {
        _uiState.update { it.copy(isValidatingKey = true) }
        try {
            // Quick validation call
            when (_uiState.value.selectedProvider) {
                ModelProvider.CIVITAI -> civitaiService.validateKey(key)
                // ...
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

### 5.2 Race Condition in `loadMoreResults`

**Location:** `ModelBrowserViewModel.kt:234-248`

The existing guard against stale appends is good:

```kotlin
if (_uiState.value.searchOffset == currentState.searchOffset) {
    // Safe to append
}
```

However, there's still a window between the check and the update where another `loadMoreResults` could slip through. Use a mutex or `isLoadingMore` check at the start (which you do — good).

### 5.3 AndroidView for HTML Rendering

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
2. Creating new TextView on every composition

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

## 6. Summary of Priorities

| Priority | Issue | Impact | Effort |
|----------|-------|--------|--------|
| 🔴 Critical | Shared OkHttpClient | High memory, no connection reuse | Low |
| 🔴 Critical | HorizontalPager memory | OOM on image viewer | Medium |
| 🔴 Critical | Unstable data classes | Excessive recomposition | Low |
| 🟡 Medium | Request cancellation | Wasted network/battery | Medium |
| 🟡 Medium | UI state too large | Maintainability | Medium |
| 🟡 Medium | Settings cache | Disk I/O on main thread | Low |
| 🟡 Medium | ScrollState per item | Memory waste | Low |
| 🟢 Low | Missing contentType | Minor perf | Trivial |
| 🟢 Low | kotlinx.serialization | Code quality | High |

---

## Recommended Action Plan

1. **Immediate (this sprint):**
   - Share OkHttpClient across services
   - Mark model data classes `@Immutable`
   - Reduce pager's `beyondBoundsPageCount` to 0

2. **Near-term (next sprint):**
   - Add request cancellation to ViewModel
   - Cache ModelBrowserSettings values
   - Replace `horizontalScroll` with `LazyRow` in cards

3. **Technical debt (backlog):**
   - Introduce dependency injection (Hilt)
   - Migrate JSON parsing to kotlinx.serialization
   - Split UI state into sub-states
