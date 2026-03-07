# Code Review: Model Browser & Community Gallery

**Reviewed by:** Roxy (AI Code Review)  
**Date:** 2026-03-06  
**Files reviewed:** 12 core files across UI, ViewModel, Service, Storage layers

---

## 1. Critical Bugs

### 1.1 VideoPlayerPool Thread Safety on Initialization
**File:** `VideoPlayerPool.kt:46-65`  
**Issue:** `ensureInitialized()` is not thread-safe. Concurrent calls from different grid items can race and create duplicate ExoPlayer instances, leading to resource exhaustion.
```kotlin
private fun ensureInitialized(context: Context) {
    if (isInitialized) return  // Race condition: multiple threads can pass this check
    // ... creates MAX_PLAYERS ExoPlayers
    isInitialized = true
}
```
**Why it matters:** Multiple ExoPlayer instances = OOM on low-memory devices, plus orphaned players never get released.  
**Fix:** Use `synchronized(this)` or a `Mutex` to guard initialization:
```kotlin
@Synchronized
private fun ensureInitialized(context: Context) { ... }
```

### 1.2 SharedVideoPlayer Singleton Not Thread-Safe
**File:** `SharedVideoPlayer.kt:35-65`  
**Issue:** `getPlayer()`, `registerConsumer()`, and `unregisterConsumer()` all access and mutate shared state (`exoPlayer`, `consumerCount`) without synchronization.
**Why it matters:** Concurrent navigation between screens (e.g., rapid scrolling + tap) can corrupt counter or create duplicate players.  
**Fix:** Synchronize all state-mutating methods or use `AtomicInteger` for counter and proper lazy init patterns.

### 1.3 LaunchedEffect Listener Never Removed on Configuration Change
**File:** `AutoplayVideoThumbnail.kt:44-60`  
**Issue:** The `Player.Listener` added in `LaunchedEffect` has no cleanup path:
```kotlin
LaunchedEffect(Unit) {
    // ...
    val listener = object : Player.Listener { ... }
    player.addListener(listener)  // Never removed if LaunchedEffect restarts
}
```
**Why it matters:** On configuration changes or recomposition, listeners accumulate. `onRenderedFirstFrame` may be called multiple times.  
**Fix:** Use `DisposableEffect` with `onDispose { player.removeListener(listener) }` or move to existing `DisposableEffect` block.

### 1.4 Database Operations Block Main Thread
**File:** `CivitaiMediaCache.kt:139-147` (`markCached` method)  
**Issue:** `markCached()` calls `writableDatabase.update()` synchronously, then launches `evictIfNeeded()`. If called from main thread (e.g., download complete callback), it blocks UI.
```kotlin
fun markCached(id: String, localPath: String, fileSize: Long) {
    val values = ContentValues().apply { ... }
    writableDatabase.update(TABLE, values, "id = ?", arrayOf(id))  // Blocking!
    evictionScope.launch { evictIfNeeded() }
}
```
**Why it matters:** ANRs if database is slow or under contention.  
**Fix:** Wrap entire method body in `evictionScope.launch { ... }` or make it a `suspend fun`.

---

## 2. Performance Issues

### 2.1 O(n) indexOf on Large Lists
**File:** `CommunityImagesScreen.kt:111-112, 122`  
**Issue:** On card click, `filteredImages.indexOf(image)` or `filteredImages.indexOf(firstImage)` performs O(n) search:
```kotlin
onClick = { selectedImageIndex = filteredImages.indexOf(image) }
```
**Why it matters:** With 200+ images loaded after pagination, this adds measurable lag on tap.  
**Fix:** Pre-compute index map or use item's absolute position from grid state. Alternatively, use `id` to find the index only when the viewer opens.

### 2.2 Eager ExoPlayer Pool Allocation
**File:** `VideoPlayerPool.kt:56-65`  
**Issue:** All 6 ExoPlayer instances are created on first access, regardless of whether all are needed:
```kotlin
for (i in 0 until MAX_PLAYERS) {
    val player = ExoPlayer.Builder(appContext)...build()
    players.add(PooledPlayer(player))
}
```
**Why it matters:** Each ExoPlayer is ~5-15MB depending on decoders. 6 players = 60-90MB upfront, even if only 1-2 videos are visible.  
**Fix:** Lazy instantiation — create players on-demand in `assignPlayer()`, grow pool up to MAX_PLAYERS.

### 2.3 Unstable Lambda Captures Causing Recomposition
**File:** `ModelBrowserScreen.kt:136`  
**Issue:** The onClick callback in `IconButton` captures many variables and recalculates on every recomposition:
```kotlin
IconButton(onClick = { 
    showSettingsSheet = true
    settingsNsfwMax = uiState.nsfwLevels.maxOrNull() ?: 2
    settingsBlurThreshold = uiState.blurThreshold
    // ... 6 more assignments
}) {
```
**Why it matters:** Every time `uiState` changes, this lambda is recreated, causing recomposition of the IconButton.  
**Fix:** Extract to a `remember`'d callback or use `rememberUpdatedState` for values captured in the lambda.

### 2.4 derivedStateOf Recalculation on Every Scroll Frame
**File:** `ModelBrowserScreen.kt:164-180`, `CommunityImagesScreen.kt:48-63`  
**Issue:** While `derivedStateOf` is used (good!), the calculation inside iterates all `visibleItemsInfo` and creates a new `HashSet` on every layout info change:
```kotlin
val autoplayKeys by remember {
    derivedStateOf {
        // ... iterates visibleItemsInfo, filters, maps, toHashSet()
    }
}
```
**Why it matters:** For 20+ visible items, this runs 60x/second during scroll. HashSet allocation per frame = GC pressure.  
**Fix:** Cache previous result and only recalculate when first/last visible indices actually change. Use `snapshotFlow` with `distinctUntilChanged` on indices, then compute set.

### 2.5 String Concatenation in Hot Path
**File:** `CivitaiTrpcService.kt:70-97` (`searchModels`)  
**Issue:** JSON input is built via `StringBuilder.append()` with inline string interpolation. For frequent searches this creates many intermediate strings.
**Why it matters:** Minor — GC pressure during rapid search.  
**Fix:** Use a JSON library (org.json) to build the object, which handles escaping correctly too.

### 2.6 Full Image Prefetch Without Cancellation
**File:** `ModelBrowserScreen.kt:229-243`  
**Issue:** Image prefetch loop runs on every scroll and enqueues Coil requests without checking if already prefetched:
```kotlin
LaunchedEffect(gridState) {
    val prefetched = mutableSetOf<String>()  // Reset on every LaunchedEffect restart!
    snapshotFlow { ... }.collect { ... }
}
```
**Why it matters:** On config change, `prefetched` set is cleared, causing duplicate prefetch requests.  
**Fix:** Move `prefetched` outside `LaunchedEffect` using `remember { mutableSetOf() }`.

---

## 3. Medium Bugs

### 3.1 Stale Closure in Settings Sheet Click Handler
**File:** `ModelBrowserScreen.kt:136`  
**Issue:** Variables like `viewModel.mediaCache.cacheLimitMb` are read at click time but assigned to local state. If `mediaCache` settings change externally, the sheet shows stale data.
**Why it matters:** Confusion if cache cleared elsewhere while sheet is preparing to open.  
**Fix:** Read values inside `LaunchedEffect(showSettingsSheet)` when sheet opens, not in onClick.

### 3.2 Missing Key in LazyVerticalGrid items()
**File:** `CommunityImagesScreen.kt:106-122`  
**Issue:** In grouped mode, items use `post.postId` as key, but in flat mode, `image.id`. If user toggles `groupByPost`, Compose may reuse wrong items.
```kotlin
if (groupByPost) {
    items(filteredPosts, key = { it.postId }) { ... }
} else {
    items(filteredImages, key = { it.id }) { ... }
}
```
**Why it matters:** Visual glitches, wrong thumbnails shown briefly during mode switch.  
**Fix:** Use distinct key namespaces: `key = { "post_${it.postId}" }` and `key = { "img_${it.id}" }`.

### 3.3 Cursor Capture Race in loadMoreResults()
**File:** `ModelBrowserViewModel.kt:205-206, 217`  
**Issue:** Cursor is captured at the function start but re-read inside the coroutine:
```kotlin
fun loadMoreResults() {
    val cursor = state.searchCursor ?: return  // Captured here
    viewModelScope.launch {
        val currentCursor = _uiState.value.searchCursor  // Re-read here (may differ!)
        // ...
        if (_uiState.value.searchCursor == currentCursor) { ... }  // Race check
    }
}
```
**Why it matters:** The guard at line 232 helps, but the initial `cursor` variable passed to the API call (line 223) uses the outer capture, not `currentCursor`. This is inconsistent.  
**Fix:** Use `currentCursor` consistently throughout the coroutine block.

### 3.4 evictionScope Never Cancelled
**File:** `CivitaiMediaCache.kt:41`  
**Issue:** `evictionScope` is created with `SupervisorJob()` but never cancelled when the cache is closed or app terminates.
```kotlin
private val evictionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
```
**Why it matters:** Potential for eviction to run after app is paused, wasting CPU.  
**Fix:** Add a `close()` method that cancels the scope, call it from Application.onTerminate or process lifecycle.

### 3.5 Community Images Not Cleared on Model Deselection
**File:** `ModelBrowserViewModel.kt:313-322`  
**Issue:** `clearSelection()` resets model/version/file but not community images:
```kotlin
fun clearSelection() {
    _uiState.value = _uiState.value.copy(
        selectedModel = null,
        selectedVersion = null,
        // Missing: communityImages, communityPosts, showCommunityImages
    )
}
```
**Why it matters:** If user dismisses detail sheet, old community images persist in memory and may flash briefly on next model selection.  
**Fix:** Call `clearCommunityImages()` inside `clearSelection()`.

### 3.6 Tag Cache Potential Threading Issue
**File:** `ModelBrowserSettings.kt:102-113`  
**Issue:** `_tagCache` is a mutable map that can be read and written from multiple threads (main UI reads, IO coroutine writes via `updateTagCache`):
```kotlin
val tagCache: Map<Int, String>
    get() {
        if (_tagCache == null) {
            _tagCache = loadTagCache()  // Not synchronized
        }
        return _tagCache!!
    }

fun updateTagCache(newTags: Map<Int, String>) {
    val current = _tagCache ?: loadTagCache()
    current.putAll(newTags)  // Concurrent modification risk
    _tagCache = current
}
```
**Why it matters:** ConcurrentModificationException or lost updates.  
**Fix:** Use `ConcurrentHashMap` or synchronize access.

---

## 4. Tech Debt

### 4.1 God-Object ViewModel
**File:** `ModelBrowserViewModel.kt` (650+ lines)  
**Issue:** This ViewModel handles: search, pagination, model selection, version selection, file selection, downloads, community images (load, filter, sort, paginate), NSFW settings, API keys, media cache, prefetch, and workflow import.
**Why it matters:** Hard to test, hard to modify. Changes to community images risk breaking search logic.  
**Fix:** Split into:
- `ModelSearchViewModel` — search, filters, pagination
- `ModelDetailViewModel` — selection, versions, downloads  
- `CommunityImagesViewModel` — community images, filters, viewer state
- Keep `ModelBrowserSettings` (or inject via Hilt/Koin)

### 4.2 Duplicated Video Thumbnail Logic
**Files:** `ModelGridCard` (ModelBrowserScreen.kt:467-545), `CommunityImageCard` (CommunityImagesScreen.kt:227-301)  
**Issue:** Both composables have nearly identical video handling: `isVideoCover`, `inlinePlay`, `AutoplayVideoThumbnail`, spinner overlay, play icon. ~70 lines of duplicated logic.
**Why it matters:** Bug fixes must be applied twice. Easy to diverge.  
**Fix:** Extract `VideoThumbnailCard` composable with parameters for video/thumbnail URLs, autoplay state, etc.

### 4.3 Magic Numbers Scattered Throughout
**Locations:**
- `VideoPlayerPool.kt:22` — `MAX_PLAYERS = 6`
- `ModelBrowserScreen.kt:175` — `visibleHeight >= 0.33f`
- `CivitaiMediaCache.kt:27` — `DEFAULT_CACHE_LIMIT_MB = 250L`
- `MediaPrefetchManager.kt:24` — `CONNECT_TIMEOUT = 8_000`
- `ModelBrowserViewModel.kt:227` — `maxResults = 300`
- `ModelBrowserViewModel.kt:251` — `delayMs = 800L` (debounce)

**Fix:** Create `object Constants` or companion objects in each class with named constants.

### 4.4 Inconsistent Error Handling
**File:** `CivitaiTrpcService.kt:116-117`  
**Issue:** Search errors throw `RuntimeException` with raw HTTP body:
```kotlin
throw RuntimeException("model.getAll returned ${response.code}: $errorBody")
```
Meanwhile, `getImageMetadata` (line 417) returns `null` on error silently.
**Why it matters:** Inconsistent UX — sometimes user sees error toast, sometimes silent failure.  
**Fix:** Define sealed class for API errors, handle consistently in ViewModel.

### 4.5 Hardcoded Civitai CDN URL
**File:** `CivitaiTrpcService.kt:21`  
```kotlin
const val CDN_BASE = "https://image.civitai.com/xG1nkqKTMzGDvpLrqFT7WA"
```
**Why it matters:** If CDN path changes, requires code change and release.  
**Fix:** Consider making configurable or at least documenting the source.

### 4.6 Missing @Stable/@Immutable Annotations on UI State
**File:** `ModelBrowserViewModel.kt:26-54`  
**Issue:** `ModelBrowserUiState` is a data class but contains mutable collections:
```kotlin
data class ModelBrowserUiState(
    val searchResults: List<ModelSearchResult> = emptyList(),  // List is stable? depends
    val communityImages: List<CommunityImage> = emptyList(),
    val nsfwLevels: Set<Int> = ...
)
```
**Why it matters:** Compose may over-recompose because it can't prove stability.  
**Fix:** Use `kotlinx.collections.immutable.ImmutableList`/`ImmutableSet` or add `@Stable` with care.

---

## 5. Refactor Opportunities

### 5.1 Extract Image/Video URL Builder
**File:** `CivitaiTrpcService.kt:325-355` (`buildCdnUrls`), plus similar logic in `parseCommunityImage`  
**Opportunity:** URL construction for thumbnails, animated, video transcode appears in multiple places with subtle variations.  
**Benefit:** Single source of truth for CDN URL parameters.
```kotlin
object CivitaiCdnUrls {
    fun thumbnail(uuid: String, name: String, isVideo: Boolean): String
    fun animated(uuid: String, name: String, isVideo: Boolean): String
    fun transcode(uuid: String, name: String): String
    fun original(uuid: String, name: String): String
}
```

### 5.2 Create UseCase Classes
**Opportunity:** Extract business logic from ViewModel:
- `SearchModelsUseCase(civitaiService, huggingFaceService)`
- `LoadCommunityImagesUseCase(civitaiService, mediaCache, prefetchManager)`
- `DownloadModelUseCase(comfyUIManagerService)`

**Benefit:** Testable without ViewModel, reusable across screens.

### 5.3 Unify Video Player Components
**Files:** `VideoPlayer.kt`, `SharedVideoPlayer.kt`, `VideoPlayerPool.kt`, `AutoplayVideoThumbnail.kt`  
**Opportunity:** These are tightly coupled but spread across 4 files. Consider:
- `VideoPlaybackManager` — owns pool + shared player, handles lifecycle
- `VideoThumbnail` — single composable for all video thumbnail needs
- `FullscreenVideoPlayer` — stays separate for modal playback

### 5.4 Implement Repository Pattern
**Opportunity:** Create `CivitaiRepository` that:
- Coordinates `CivitaiTrpcService` + `CivitaiMediaCache`
- Exposes `Flow<PagingData<Model>>` for search results
- Handles caching decisions internally

**Benefit:** ViewModel doesn't manage cache + API coordination.

### 5.5 Use Paging 3 for Infinite Scroll
**Files:** `ModelBrowserScreen.kt`, `CommunityImagesScreen.kt`  
**Opportunity:** Current manual pagination with cursors duplicates what Paging 3 provides. Using `Pager` + `PagingDataAdapter` would:
- Automatically handle load states
- Provide built-in deduplication
- Integrate cleanly with LazyVerticalGrid via `LazyPagingItems`

---

## 6. Nice-to-Haves

### 6.1 Add kdoc Comments
**Files:** All service/ViewModel methods  
**Issue:** Many public methods lack documentation. `toggleCommunityImages()`, `applySettings()`, etc. would benefit from explaining parameters and side effects.

### 6.2 Use Named Parameters for Clarity
**File:** `ModelBrowserViewModel.kt:460`  
```kotlin
viewModel.applySettings(
    nsfwMax = settingsNsfwMax,
    blurThreshold = settingsBlurThreshold,
    showAnimations = settingsShowAnimations,
    autoplayVideos = settingsAutoplayVideos,
    apiKey = settingsApiKey
)
```
Already does this — good! But some call sites could be cleaner.

### 6.3 Extract Filter Sheet to Separate Composable
**File:** `ModelBrowserScreen.kt:284-399`  
**Issue:** Settings sheet is 115 lines inline. Extract to `ModelBrowserSettingsSheet.kt` for cleaner main screen.

### 6.4 Add Unit Tests for Service Layer
**File:** `CivitaiTrpcService.kt`  
**Opportunity:** JSON parsing and URL construction have many edge cases. Unit tests with mock HTTP responses would catch regressions.

### 6.5 Implement Content Transition Animations
**File:** `CommunityImagesScreen.kt`  
**Opportunity:** When opening fullscreen viewer, use `SharedTransitionLayout` (Compose 1.7+) for hero animation from grid to fullscreen.

### 6.6 Add Skeleton Loading States
**Files:** `ModelBrowserScreen.kt`, `CommunityImagesScreen.kt`  
**Issue:** Currently shows `CircularProgressIndicator` during load. Skeleton cards matching the grid layout would feel faster.

---

## Summary

| Category | Count | Severity |
|----------|-------|----------|
| Critical Bugs | 4 | 🔴 Fix immediately |
| Performance Issues | 6 | 🟠 Degrades UX on scroll |
| Medium Bugs | 6 | 🟡 Incorrect behavior |
| Tech Debt | 6 | ⚪ Maintainability |
| Refactor Opportunities | 5 | 🔵 Architecture improvements |
| Nice-to-haves | 6 | ⚪ Polish |

**Priority order:**
1. Fix thread safety in `VideoPlayerPool` and `SharedVideoPlayer`
2. Move database writes off main thread
3. Fix listener leak in `AutoplayVideoThumbnail`
4. Address `indexOf` performance
5. Split ViewModel (longer-term)
