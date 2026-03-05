# Model Browser Code Review — Agent 1 (Truncated, Reconstructed)

**Date:** 2026-03-05
**Reviewer:** Roxy (AI Subagent, Opus)
**Scope:** `service/`, `viewmodel/`, `ui/screens/`, `model/`, `storage/`

---

## 1. Performance Issues

### 1.1 🔴 CRITICAL: ModelBrowserUiState is a Recomposition Bomb

**Location:** `ModelBrowserViewModel.kt`

`ModelBrowserUiState` has ~25 fields. Any mutation triggers a full state copy and recomposition of all observers.

```kotlin
// Current: every change creates a full copy
_uiState.value = _uiState.value.copy(searchQuery = query)
```

**Problem:** When pagination loads, `searchResults` changes, causing every composable observing `uiState` to recompose — even those only using `selectedModel`.

**Fix:** Split into domain-specific state objects:

```kotlin
data class SearchState(
    val query: String = "",
    val results: List<ModelSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val offset: Int = 0,
    val hasMore: Boolean = true
)

data class FilterState(
    val modelType: String? = null,
    val baseModel: String? = null,
    val sort: String = "Most Downloaded",
    val nsfwLevels: Set<Int> = setOf(1, 2, 4)
)

data class SelectionState(
    val model: ModelSearchResult? = null,
    val version: ModelVersion? = null,
    val file: ModelFile? = null,
    val modelType: ModelType? = null
)
```

Composables can observe only what they need. Changing a filter won't recompose the grid items.

---

### 1.2 🔴 CRITICAL: Image Viewer Memory Leak

**Location:** `CommunityImagesScreen.kt` — `CommunityImagePage`

```kotlin
var bitmap by remember(image.id) { mutableStateOf<Bitmap?>(null) }

LaunchedEffect(image.id) {
    val result = context.imageLoader.execute(request)
    bitmap = if (result is SuccessResult) {
        result.image.toBitmap()  // ← Holding raw Bitmap in state
    } else null
}
```

**Problem:** `HorizontalPager` keeps neighboring pages composed. Each page holds a `Bitmap` in Compose state. With 3 pages active, that's 3 full-resolution bitmaps (potentially 48MB+ each).

**Fix:** Don't hold Bitmap in state; let Coil manage memory via its cache. Or use `SubcomposeAsyncImage`:

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

Or if Bitmap is needed for gesture handling, dispose explicitly:

```kotlin
DisposableEffect(image.id) {
    onDispose {
        bitmap?.recycle()
        bitmap = null
    }
}
```

---

### 1.3 🟡 MEDIUM: Scroll Prefetching Creates Render Pressure

**Location:** `ModelBrowserScreen.kt`

**Problems:**
1. No size constraint on prefetch requests — downloading full-size images
2. No deduplication — scrolling back and forth re-enqueues same URLs
3. Fires on every distinct scroll position change (still noisy)

**Fix:**

```kotlin
LaunchedEffect(gridState, uiState.searchResults) {
    val prefetched = mutableSetOf<String>()

    snapshotFlow {
        gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    }
    .distinctUntilChanged()
    .debounce(100) // Don't fire on every frame
    .collect { lastVisible ->
        val prefetchRange = (lastVisible + 1) until minOf(lastVisible + 6, uiState.searchResults.size)
        prefetchRange.forEach { i ->
            uiState.searchResults.getOrNull(i)?.thumbnailUrl?.let { url ->
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

### 1.4 🔴 CRITICAL: OkHttpClient Per Service

**Location:** `CivitaiService.kt`, `CivitaiMeiliService.kt`, `HuggingFaceService.kt`, `ComfyUIManagerService.kt`

Each service creates its own `OkHttpClient` instance — multiple thread pools, no connection reuse.

**Fix:** Shared singleton:

```kotlin
object HttpModule {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .build()
}
```

---

## 2. Architecture Concerns

### 2.1 🟡 MEDIUM: ViewModel Does Too Much

`ModelBrowserViewModel` handles search, filters, selection, community images, downloads, AND API key management. Violates single responsibility.

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
```

---

### 2.2 🟡 MEDIUM: Manual JSON Parsing is Error-Prone

All services use `org.json.JSONObject` with verbose `optString`/`optInt`/`optJSONArray` chains.

**Recommendation:** kotlinx.serialization with data classes:

```kotlin
@Serializable
data class CivitaiModelResponse(
    val id: String,
    val name: String,
    val description: String? = null,
)

val model = Json.decodeFromString<CivitaiModelResponse>(body)
```

---

### 2.3 🟡 MEDIUM: Tight Coupling to ConnectionManager Singleton

**Location:** `ComfyUIManagerService.kt`

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

## 3. Compose-Specific Issues

### 3.1 🔴 CRITICAL: Unstable Lambda Parameters Cause Recomposition

**Location:** `ModelBrowserScreen.kt`

```kotlin
items(uiState.searchResults, key = { it.id }) { model ->
    ModelGridCard(
        model = model,
        onClick = { viewModel.selectModel(model) }  // ← NEW LAMBDA EVERY RECOMPOSITION
    )
}
```

**Problem:** `{ viewModel.selectModel(model) }` creates a new lambda instance every recomposition. Since lambdas aren't stable, every `ModelGridCard` recomposes when *any* part of `uiState` changes.

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
        onModelSelected = viewModel::selectModelById  // Method reference = stable
    )
}
```

---

### 3.2 🔴 CRITICAL: ModelSearchResult Should Be @Immutable

**Location:** `ModelBrowser.kt`

`ModelSearchResult` uses `List<String>` and `List<ModelVersion>` which Compose can't prove are immutable. Forces defensive recomposition on every item.

**Fix:**

```kotlin
@Immutable
data class ModelSearchResult(
    val id: String,
    val name: String,
    val tags: List<String>,
    val versions: List<ModelVersion>,
    // ...
)
```

Also mark `ModelVersion`, `ModelFile`, `CommunityImage`, `ImageStats`, etc.

---

## Summary — Unique Findings (Not in Agent 2)

| Issue | Description |
|-------|-------------|
| ConnectionManager coupling | Singleton access in ComfyUIManagerService, inject instead |
| Lambda instability in grid items | Per-item onClick lambda recreation, use remember/method refs |
| Use case extraction | SearchModelsUseCase, DownloadModelUseCase pattern |
| Prefetch deduplication | Track prefetched URLs in Set + debounce(100) |
