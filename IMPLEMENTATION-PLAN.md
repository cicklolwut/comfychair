# Model Browser Implementation Plan

**Generated:** 2025-03-05  
**Source:** REVIEW-COMBINED.md (validated against actual source)

---

## Part 1: Validated Findings

### ✅ CONFIRMED Issues

| ID | Issue | Location | Validated |
|----|-------|----------|-----------|
| 1.1 | OkHttpClient created per service | CivitaiService.kt:35, CivitaiMeiliService.kt:37, HuggingFaceService.kt:27, ComfyUIManagerService.kt:26 | Each creates `OkHttpClient.Builder()...build()` |
| 1.2 | HorizontalPager bitmap memory | CommunityImagesScreen.kt:176-200 (`CommunityImagePage`) | Bitmap held in `remember` state, no cleanup |
| 1.3 | Unstable lambda in grid items | ModelBrowserScreen.kt:~144 | `onClick = { viewModel.selectModel(model) }` creates new lambda per recomposition |
| 1.4 | Model classes missing @Immutable | model/ModelBrowser.kt:1-120 | No stability annotations on any data class |
| 1.6 | Prefetch lacks deduplication | ModelBrowserScreen.kt:~158-170 | No `Set<String>`, includes `uiState.searchResults` in effect key |
| 1.8 | Settings read on every access | storage/ModelBrowserSettings.kt:52-78 | Every getter does `prefs.getString(...)` |
| 1.9 | List accumulation without cap | ModelBrowserViewModel.kt:230-248 | `searchResults + meiliResult.models` with no limit |
| 1.11 | Missing contentType | ModelBrowserScreen.kt:~144 | `items(...)` has no `contentType` parameter |
| 2.1 | Services created in ViewModel | ModelBrowserViewModel.kt:70-73 | Direct instantiation: `private val civitaiService = CivitaiService(...)` |
| 2.3 | ConnectionManager singleton coupling | ComfyUIManagerService.kt:37-43 (`getServerUrl()`) | Direct `ConnectionManager.connectionState.value` access |
| 2.5 | API key saved without validation | ModelBrowserViewModel.kt:~447 (`saveApiKey`) | Saves immediately, no test call |
| 3.1 | AndroidView missing update lambda | ModelBrowserScreen.kt (HtmlText composable, ~line 542) | Factory-only, no `update = { }` |
| 3.2 | Bitmap not explicitly recycled | CommunityImagesScreen.kt:176-200 | No `DisposableEffect` for cleanup |

### ⚠️ OVERSTATED / Already Mitigated

| ID | Issue | Status |
|----|-------|--------|
| 1.5 | ModelBrowserUiState is recomposition bomb | **Partially overstated** — `ModelBrowserUiState` is already marked `@Stable` at ModelBrowserViewModel.kt:31. Splitting is still beneficial for surgical recomposition but not as critical as suggested. |
| 1.7 | Missing request cancellation | **Partially true** — `searchDebounceJob?.cancel()` exists. The guard in `loadMoreResults()` discards stale pages. Network Job isn't separately tracked but race conditions are handled. |
| 1.10 | Tag rows use horizontalScroll | **No action needed** — Review already noted tags are capped to 6, overhead is negligible. |

### ❌ INCORRECT / Already Fixed

None identified — all findings were valid to varying degrees.

---

## Part 2: Implementation Tasks

Tasks are grouped to minimize merge conflicts. Each task includes exact file paths, specific code changes, dependencies, and gotchas.

---

### Task A: Create Shared HTTP Module

**Files:** New file `service/HttpModule.kt`, then modify all 4 services

**Complexity:** Small

**Dependencies:** None (do this first)

**Changes:**

1. **Create `app/src/main/java/sh/hnet/comfychair/service/HttpModule.kt`:**
```kotlin
package sh.hnet.comfychair.service

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP client singleton. All services should use this instead of
 * creating their own OkHttpClient instances.
 */
object HttpModule {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .build()
    }
}
```

2. **Modify `CivitaiService.kt`:**
   - Remove lines 35-38 (the `private val client = OkHttpClient.Builder()...`)
   - Add: `private val client = HttpModule.client`

3. **Modify `CivitaiMeiliService.kt`:**
   - Remove lines 37-40 (the `private val client = OkHttpClient.Builder()...`)
   - Add: `private val client = HttpModule.client`

4. **Modify `HuggingFaceService.kt`:**
   - Remove lines 27-30 (the `private val client = OkHttpClient.Builder()...`)
   - Add: `private val client = HttpModule.client`

5. **Modify `ComfyUIManagerService.kt`:**
   - Remove lines 26-31 (the `private val client = OkHttpClient.Builder()...`)
   - Add: `private val client = HttpModule.client`

**Gotchas:**
- ComfyUIManagerService has `writeTimeout(15)` which others don't. The shared client includes it.
- Make sure imports are correct: `import sh.hnet.comfychair.service.HttpModule`

---

### Task B: Add Stability Annotations to Model Classes

**Files:** `model/ModelBrowser.kt`

**Complexity:** Small

**Dependencies:** None

**Changes:**

Add `@Immutable` annotation to all data classes. At the top of the file, add import:
```kotlin
import androidx.compose.runtime.Immutable
```

Then annotate each data class:

```kotlin
@Immutable
data class ModelSearchResult(...)

@Immutable
data class ModelVersion(...)

@Immutable
data class ModelVersionImage(...)

@Immutable
data class ModelFile(...)

@Immutable
data class CommunityImage(...)

@Immutable
data class ImageStats(...)

@Immutable
data class GenerationMetadata(...)

@Immutable
data class GenerationResource(...)
```

**Do NOT annotate:**
- `ModelProvider` (enum, already stable)
- `ModelType` (enum, already stable)
- `CivitaiTypeMapper` (object singleton)

**Gotchas:**
- `@Immutable` requires all properties to be `val` (they already are)
- Lists must be immutable conceptually — these are read-only `List<T>` so that's fine

---

### Task C: Stabilize Grid Item Lambdas

**Files:** `ui/screens/ModelBrowserScreen.kt`

**Complexity:** Small

**Dependencies:** None (can run parallel with A, B)

**Changes:**

Find the `items()` call in the LazyVerticalGrid (around line 144):

**Current code:**
```kotlin
items(uiState.searchResults, key = { it.id }) { model ->
    ModelGridCard(
        model = model,
        filterType = uiState.filterModelType,
        filterBaseModel = uiState.filterBaseModel,
        showAnimations = uiState.showAnimations,
        onClick = { viewModel.selectModel(model) }
    )
}
```

**Replace with:**
```kotlin
items(
    items = uiState.searchResults,
    key = { it.id },
    contentType = { "model_card" }
) { model ->
    val onClick = remember(model.id) { { viewModel.selectModel(model) } }
    ModelGridCard(
        model = model,
        filterType = uiState.filterModelType,
        filterBaseModel = uiState.filterBaseModel,
        showAnimations = uiState.showAnimations,
        onClick = onClick
    )
}
```

**Gotchas:**
- The `remember` must be keyed on `model.id`, not `model` (object identity changes)
- This also adds `contentType` which was another finding (1.11)

---

### Task D: Fix Image Viewer Memory Management

**Files:** `ui/screens/CommunityImagesScreen.kt`

**Complexity:** Medium

**Dependencies:** None

**Changes:**

1. **Add DisposableEffect for bitmap cleanup** in `CommunityImagePage` composable (around line 176):

**Current code:**
```kotlin
@Composable
private fun CommunityImagePage(
    image: CommunityImage,
    onTap: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(image.id) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(image.id) { mutableStateOf(true) }
    
    LaunchedEffect(image.id) {
        isLoading = true
        val dm = context.resources.displayMetrics
        val request = ImageRequest.Builder(context)
            .data(image.url)
            .size(dm.widthPixels, dm.heightPixels)
            .build()
        val result = context.imageLoader.execute(request)
        bitmap = if (result is SuccessResult) {
            result.image.toBitmap()
        } else null
        isLoading = false
    }
    // ... rest of composable
}
```

**Add after the `LaunchedEffect` block:**
```kotlin
// Clean up bitmap when page is disposed (scrolled away)
DisposableEffect(image.id) {
    onDispose {
        bitmap?.recycle()
        bitmap = null
    }
}
```

2. **Set beyondBoundsPageCount = 0** on the HorizontalPager in `CommunityImageViewer` (around line 160):

**Find:**
```kotlin
HorizontalPager(
    state = pagerState,
    modifier = Modifier.fillMaxSize(),
    key = { images[it].id }
) { page ->
```

**Change to:**
```kotlin
HorizontalPager(
    state = pagerState,
    modifier = Modifier.fillMaxSize(),
    beyondBoundsPageCount = 0,
    key = { images[it].id }
) { page ->
```

**Gotchas:**
- Must import `DisposableEffect` from `androidx.compose.runtime`
- `bitmap?.recycle()` is safe to call even if the bitmap was already recycled
- Setting `beyondBoundsPageCount = 0` may cause brief flicker when swiping fast — acceptable tradeoff for memory

---

### Task E: Improve Prefetch Logic

**Files:** `ui/screens/ModelBrowserScreen.kt`

**Complexity:** Small

**Dependencies:** None (can run parallel)

**Changes:**

Find the prefetch `LaunchedEffect` block (around line 158-170):

**Current code:**
```kotlin
val imageLoader = context.imageLoader
LaunchedEffect(gridState, uiState.searchResults) {
    snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
        .distinctUntilChanged()
        .collect { lastVisible ->
            val prefetchEnd = minOf(lastVisible + 8, uiState.searchResults.size)
            for (i in (lastVisible + 1) until prefetchEnd) {
                uiState.searchResults.getOrNull(i)?.thumbnailUrl?.let { url ->
                    imageLoader.enqueue(
                        ImageRequest.Builder(context).data(url).build()
                    )
                }
            }
        }
}
```

**Replace with:**
```kotlin
val imageLoader = context.imageLoader
val searchResults by rememberUpdatedState(uiState.searchResults)

LaunchedEffect(gridState) {
    val prefetched = mutableSetOf<String>()
    
    snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
        .distinctUntilChanged()
        .collect { lastVisible ->
            val currentResults = searchResults
            val prefetchEnd = minOf(lastVisible + 6, currentResults.size)
            for (i in (lastVisible + 1) until prefetchEnd) {
                currentResults.getOrNull(i)?.thumbnailUrl?.let { url ->
                    if (url !in prefetched) {
                        prefetched.add(url)
                        imageLoader.enqueue(
                            ImageRequest.Builder(context)
                                .data(url)
                                .size(450, 675)
                                .build()
                        )
                    }
                }
            }
        }
}
```

**Required import:**
```kotlin
import androidx.compose.runtime.rememberUpdatedState
```

**Gotchas:**
- `rememberUpdatedState` captures the latest value without restarting the effect
- Removing `uiState.searchResults` from LaunchedEffect key prevents effect restart on every search
- The `prefetched` set lives inside the effect, so it resets when `gridState` changes (fine for our use case)

---

### Task F: Fix AndroidView HTML Rendering

**Files:** `ui/screens/ModelBrowserScreen.kt`

**Complexity:** Trivial

**Dependencies:** None

**Changes:**

Find the `HtmlText` composable (around line 542):

**Current code:**
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

**Replace with:**
```kotlin
@Composable
fun HtmlText(html: String) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                textSize = 14f
                setTextColor(textColor)
            }
        },
        update = { textView ->
            textView.text = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
        },
        modifier = Modifier.fillMaxWidth()
    )
}
```

**Required imports:**
```kotlin
import androidx.compose.ui.graphics.toArgb
```

**Gotchas:**
- `toArgb()` converts Compose Color to Android int color
- Moving `Html.fromHtml` to `update` ensures it re-runs when `html` changes
- `textColor` is captured once at composition time — if theme changes dynamically, may need `rememberUpdatedState`

---

### Task G: Cache Settings Values

**Files:** `storage/ModelBrowserSettings.kt`

**Complexity:** Small

**Dependencies:** None

**Changes:**

Add private backing fields with lazy initialization. Modify the class as follows:

**After the `securePrefs` lazy init (around line 48), add:**
```kotlin
// In-memory cache for frequently accessed values
private var _nsfwLevels: Set<Int>? = null
private var _showAnimations: Boolean? = null
```

**Replace the `nsfwLevels` property (around line 68-76):**

**Current:**
```kotlin
var nsfwLevels: Set<Int>
    get() {
        val stored = prefs.getString(KEY_NSFW_LEVELS, null)
        return if (stored != null) {
            stored.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        } else {
            DEFAULT_NSFW_LEVELS
        }
    }
    set(value) = prefs.edit().putString(KEY_NSFW_LEVELS, value.joinToString(",")).apply()
```

**Replace with:**
```kotlin
var nsfwLevels: Set<Int>
    get() = _nsfwLevels ?: run {
        val stored = prefs.getString(KEY_NSFW_LEVELS, null)
        val parsed = if (stored != null) {
            stored.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        } else {
            DEFAULT_NSFW_LEVELS
        }
        _nsfwLevels = parsed
        parsed
    }
    set(value) {
        _nsfwLevels = value
        prefs.edit().putString(KEY_NSFW_LEVELS, value.joinToString(",")).apply()
    }
```

**Replace the `showAnimations` property (around line 79-81):**

**Current:**
```kotlin
var showAnimations: Boolean
    get() = prefs.getBoolean(KEY_SHOW_ANIMATIONS, false)
    set(value) = prefs.edit().putBoolean(KEY_SHOW_ANIMATIONS, value).apply()
```

**Replace with:**
```kotlin
var showAnimations: Boolean
    get() = _showAnimations ?: prefs.getBoolean(KEY_SHOW_ANIMATIONS, false).also { _showAnimations = it }
    set(value) {
        _showAnimations = value
        prefs.edit().putBoolean(KEY_SHOW_ANIMATIONS, value).apply()
    }
```

**Gotchas:**
- Only caching the two most frequently accessed properties (called per grid item)
- API keys are read infrequently, no need to cache
- Cache is invalidated on write (setter updates both cache and SharedPrefs)

---

### Task H: Cap Search Results Accumulation

**Files:** `viewmodel/ModelBrowserViewModel.kt`

**Complexity:** Small

**Dependencies:** None

**Changes:**

Find the `loadMoreResults()` function (around line 210-248).

**In the success branch, find:**
```kotlin
_uiState.value = _uiState.value.copy(
    searchResults = _uiState.value.searchResults + meiliResult.models,
    // ...
)
```

**Replace with:**
```kotlin
val maxResults = 300
val combined = _uiState.value.searchResults + meiliResult.models
val capped = if (combined.size > maxResults) combined.takeLast(maxResults) else combined

_uiState.value = _uiState.value.copy(
    searchResults = capped,
    // ... rest stays the same
)
```

**Gotchas:**
- Using `takeLast()` keeps the most recent results (newer pagination pages)
- 300 is generous — user would have to scroll through 15+ pages to hit it
- This prevents unbounded memory growth but may cause confusion if user scrolls back up

---

### Task I: Inject Connection Info into ComfyUIManagerService

**Files:** `service/ComfyUIManagerService.kt`, `viewmodel/ModelBrowserViewModel.kt`

**Complexity:** Small

**Dependencies:** Task A (both touch services, coordinate)

**Changes:**

1. **Modify `ComfyUIManagerService.kt`:**

**Current constructor and getServerUrl (lines 22-43):**
```kotlin
class ComfyUIManagerService {
    // ... companion object ...

    private val client = OkHttpClient.Builder()...

    private fun getServerUrl(): String {
        val connState = ConnectionManager.connectionState.value
        if (connState is ConnectionState.Connected) {
            return "${connState.protocol}://${connState.hostname}:${connState.port}"
        }
        throw IllegalStateException("Not connected to ComfyUI server")
    }
```

**Replace with:**
```kotlin
class ComfyUIManagerService(
    private val serverUrlProvider: () -> String
) {
    // ... companion object stays the same ...

    private val client = HttpModule.client  // From Task A

    private fun getServerUrl(): String = serverUrlProvider()
```

2. **Modify `ModelBrowserViewModel.kt`:**

**Find the service instantiation (around line 73):**
```kotlin
private val comfyUIManagerService = ComfyUIManagerService()
```

**Replace with:**
```kotlin
private val comfyUIManagerService = ComfyUIManagerService {
    val connState = ConnectionManager.connectionState.value
    if (connState is ConnectionState.Connected) {
        "${connState.protocol}://${connState.hostname}:${connState.port}"
    } else {
        throw IllegalStateException("Not connected to ComfyUI server")
    }
}
```

**Gotchas:**
- The lambda is evaluated each time `getServerUrl()` is called, so it picks up connection changes
- This allows unit testing ComfyUIManagerService by passing a mock URL provider
- Don't forget to update the import in ModelBrowserViewModel if ConnectionState isn't already imported

---

## Part 3: Task Dependency Graph

```
[A] HttpModule ──────────────────────────────┐
                                             │
[B] @Immutable annotations ─────────────────┐│
                                            ││
[C] Stabilize lambdas ─────────────────────┐││
                                           │││
[D] Image viewer memory ───────────────────┼┼┼──→ Can all run in parallel
                                           │││
[E] Prefetch deduplication ────────────────┼┼┘
                                           ││
[F] AndroidView update ────────────────────┼┘
                                           │
[G] Cache settings ────────────────────────┘

[H] Cap results ────────────────────────────→ Independent

[I] Inject ConnectionManager ───────────────→ Depends on [A] (both touch services)
```

**Recommended execution order:**
1. **Wave 1 (parallel):** A, B, D, F, G, H
2. **Wave 2 (parallel, after A):** C, E, I

Tasks B, D, F, G, H touch completely different files — safe to merge in any order.

Tasks C and E both touch `ModelBrowserScreen.kt` but different sections — can run parallel if agents are aware of each other's changes.

Task I depends on A being merged first (both modify service files).

---

## Part 4: Summary

| Task | Priority | Complexity | Files | Impact |
|------|----------|------------|-------|--------|
| A | 🔴 Critical | Small | 5 files (new + 4 services) | Memory, connection reuse |
| B | 🔴 Critical | Small | 1 file | Recomposition |
| C | 🔴 Critical | Small | 1 file | Recomposition |
| D | 🔴 Critical | Medium | 1 file | OOM prevention |
| E | 🟡 Medium | Small | 1 file | Bandwidth |
| F | 🟡 Medium | Trivial | 1 file | UI correctness |
| G | 🟡 Medium | Small | 1 file | Main thread I/O |
| H | 🟡 Medium | Small | 1 file | Memory creep |
| I | 🟡 Medium | Small | 2 files | Testability |

**Total estimated effort:** ~4-6 hours for a senior developer, or can be parallelized across multiple agents in ~1-2 hours.

---

## Part 5: Deferred (Not in This Plan)

The following items from the review are valid but deferred due to high effort or architectural scope:

1. **Dependency Injection (Hilt/Koin)** — Requires gradle changes, module setup, significant refactoring
2. **Split UI State** — `ModelBrowserUiState` is already `@Stable`; splitting is nice-to-have
3. **Extract Use Cases** — Clean architecture refactor, high effort
4. **kotlinx.serialization migration** — 500+ lines of JSON parsing to migrate
5. **API key validation** — Low priority, small UX improvement
