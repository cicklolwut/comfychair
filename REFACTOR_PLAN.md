# ComfyChair Refactor/Bugfix Plan

**Created:** 2026-03-06  
**Based on:** CODE_REVIEW.md + code validation  
**Scope:** Stability and performance improvements for Model Browser & Community Gallery

---

## Validation Summary

Each finding from CODE_REVIEW.md was verified against the actual code:

| Finding | Status | Reason |
|---------|--------|--------|
| 1.1 VideoPlayerPool Thread Safety | ✅ CONFIRMED | `ensureInitialized()` has no synchronization |
| 1.2 SharedVideoPlayer Not Thread-Safe | ✅ CONFIRMED | `consumerCount++` not atomic |
| 1.3 LaunchedEffect Listener Leak | ❌ INVALID | Listener self-removes in `onRenderedFirstFrame()` |
| 1.4 Database Blocks Main Thread | ⚠️ LOW PRIORITY | Currently called from IO dispatcher |
| 2.1 O(n) indexOf | ✅ CONFIRMED | Real issue with 200+ images |
| 2.2 Eager ExoPlayer Pool | ✅ CONFIRMED | All 6 players created upfront |
| 2.3 Unstable Lambda | ⚠️ LOW PRIORITY | Single button, not in a list |
| 2.4 derivedStateOf per-frame | ✅ CONFIRMED | HashSet allocation every frame |
| 2.5 String Concatenation | ❌ INVALID | Already uses StringBuilder |
| 2.6 Prefetch Set Reset | ✅ CONFIRMED | Set inside LaunchedEffect |
| 3.1 Stale Closure | ❌ INVALID | Intended behavior (snapshot on click) |
| 3.2 Key Namespace Collision | ✅ CONFIRMED | Post/Image IDs could collide |
| 3.3 Cursor Capture Race | ✅ CONFIRMED | Inconsistent cursor variable usage |
| 3.4 evictionScope Leak | ✅ CONFIRMED | CoroutineScope never cancelled |
| 3.5 Community Images Persist | ⚠️ LOW PRIORITY | Cleared on new model selection |
| 3.6 Tag Cache Threading | ✅ CONFIRMED | MutableMap without synchronization |

---

## Phase 1: Thread Safety (Critical)

**Priority:** 🔴 CRITICAL — Race conditions can cause crashes  
**Estimated Time:** 1 session  
**Dependencies:** None

### Fix 1.1: VideoPlayerPool Synchronization
**File:** `app/src/main/java/sh/hnet/comfychair/ui/components/VideoPlayerPool.kt`  
**Complexity:** S

**What to do:**
1. Add `@Synchronized` annotation to `ensureInitialized()`:
```kotlin
@Synchronized
private fun ensureInitialized(context: Context) {
    if (isInitialized) return
    // ... existing code
}
```

2. Add `@Synchronized` to `assignPlayer()` and `releasePlayer()` since they access `players` list which is populated in `ensureInitialized()`.

### Fix 1.2: SharedVideoPlayer Atomic Counter
**File:** `app/src/main/java/sh/hnet/comfychair/ui/components/SharedVideoPlayer.kt`  
**Complexity:** S

**What to do:**
1. Replace `private var consumerCount = 0` with:
```kotlin
private val consumerCount = AtomicInteger(0)
```

2. Update `registerConsumer()`:
```kotlin
fun registerConsumer(context: Context): ExoPlayer {
    consumerCount.incrementAndGet()
    // ... rest unchanged
}
```

3. Update `unregisterConsumer()`:
```kotlin
fun unregisterConsumer() {
    if (consumerCount.decrementAndGet() <= 0) {
        consumerCount.set(0)
        // ... schedule stop
    }
}
```

4. Add `@Synchronized` to `getPlayer()` for lazy init safety.

### Fix 1.3: Tag Cache Thread Safety
**File:** `app/src/main/java/sh/hnet/comfychair/storage/ModelBrowserSettings.kt`  
**Complexity:** S

**What to do:**
1. Change `_tagCache` type from `MutableMap<Int, String>?` to use `ConcurrentHashMap`:
```kotlin
private val _tagCache: ConcurrentHashMap<Int, String> by lazy {
    ConcurrentHashMap(loadTagCache())
}
```

2. Simplify `tagCache` getter and `updateTagCache()` to use the thread-safe map directly.

---

## Phase 2: Performance - Grid Scrolling (High Impact)

**Priority:** 🟠 HIGH — Directly affects scroll smoothness  
**Estimated Time:** 1 session  
**Dependencies:** None

### Fix 2.1: Replace indexOf with Index Parameter
**File:** `app/src/main/java/sh/hnet/comfychair/ui/screens/CommunityImagesScreen.kt`  
**Complexity:** M

**What to do:**
1. Change `items()` call to use `itemsIndexed()`:
```kotlin
// Before
items(filteredImages, key = { it.id }) { image ->
    CommunityImageCard(
        ...
        onClick = { selectedImageIndex = filteredImages.indexOf(image) }
    )
}

// After
itemsIndexed(filteredImages, key = { _, image -> image.id }) { index, image ->
    CommunityImageCard(
        ...
        onClick = { selectedImageIndex = index }
    )
}
```

2. Same change for `filteredPosts` in grouped mode — use `itemsIndexed()` and pass index directly.

3. For the grouped mode case (`firstImage`), precompute a `Map<Long, Int>` of imageId → index:
```kotlin
val imageIndexMap = remember(filteredImages) {
    filteredImages.withIndex().associate { it.value.id to it.index }
}
```
Then use `imageIndexMap[firstImage.id] ?: 0` instead of `indexOf`.

### Fix 2.2: derivedStateOf Optimization
**Files:** 
- `app/src/main/java/sh/hnet/comfychair/ui/screens/ModelBrowserScreen.kt`
- `app/src/main/java/sh/hnet/comfychair/ui/screens/CommunityImagesScreen.kt`  
**Complexity:** M

**What to do:**
1. Replace `derivedStateOf` with `snapshotFlow` + `distinctUntilChanged`:
```kotlin
// Before
val autoplayKeys by remember {
    derivedStateOf {
        gridState.layoutInfo.visibleItemsInfo
            .filter { ... }
            .map { it.key }
            .toHashSet()
    }
}

// After
var autoplayKeys by remember { mutableStateOf(emptySet<Any>()) }

LaunchedEffect(gridState) {
    snapshotFlow { 
        gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.index to 
        gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index 
    }
    .distinctUntilChanged()
    .collect { (first, last) ->
        if (first == null || last == null) return@collect
        autoplayKeys = gridState.layoutInfo.visibleItemsInfo
            .filter { item ->
                // ... visibility check
            }
            .map { it.key }
            .toSet()
    }
}
```

This only recalculates when first/last visible indices change, not on every scroll pixel.

### Fix 2.3: Prefetch Set Persistence
**File:** `app/src/main/java/sh/hnet/comfychair/ui/screens/ModelBrowserScreen.kt`  
**Complexity:** S

**What to do:**
1. Move `prefetched` set outside `LaunchedEffect`:
```kotlin
// Before (inside LaunchedEffect)
LaunchedEffect(gridState) {
    val prefetched = mutableSetOf<String>()
    snapshotFlow { ... }.collect { ... }
}

// After
val prefetched = remember { mutableSetOf<String>() }
LaunchedEffect(gridState) {
    snapshotFlow { ... }.collect { ... }
}
```

---

## Phase 3: Performance - Video Player Pool (Memory)

**Priority:** 🟠 HIGH — Reduces memory by ~60MB on app start  
**Estimated Time:** 1 session  
**Dependencies:** Phase 1 (thread safety)

### Fix 3.1: Lazy ExoPlayer Instantiation
**File:** `app/src/main/java/sh/hnet/comfychair/ui/components/VideoPlayerPool.kt`  
**Complexity:** M

**What to do:**
1. Remove eager player creation in `ensureInitialized()`. Instead, just initialize the list:
```kotlin
@Synchronized
private fun ensureInitialized(context: Context) {
    if (isInitialized) return
    appContext = context.applicationContext
    players = mutableListOf()  // Empty list
    isInitialized = true
}
```

2. Modify `assignPlayer()` to create players on-demand:
```kotlin
@Synchronized
fun assignPlayer(context: Context, key: String, uri: Uri): ExoPlayer? {
    ensureInitialized(context)
    
    // Check for existing assignment
    val existing = players.find { it.assignedKey == key }
    if (existing != null) { ... }
    
    // Find free player OR create new if under limit
    var pooled = players.find { it.assignedKey == null }
    
    if (pooled == null && players.size < MAX_PLAYERS) {
        // Create new player
        val player = createPlayer(context)
        pooled = PooledPlayer(player)
        players.add(pooled)
        Log.d(TAG, "Created player ${players.size}/$MAX_PLAYERS")
    }
    
    if (pooled == null) {
        // Pool full — reclaim oldest
        pooled = players.minByOrNull { it.lastAssignedAt }!!
        pooled.player.stop()
    }
    
    // Assign
    ...
}

private fun createPlayer(context: Context): ExoPlayer {
    val httpFactory = DefaultHttpDataSource.Factory()
        .setConnectTimeoutMs(8_000)
        .setReadTimeoutMs(8_000)
        .setAllowCrossProtocolRedirects(true)
    
    return ExoPlayer.Builder(context.applicationContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
        .build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = false
        }
}
```

---

## Phase 4: Data Consistency (Medium Bugs)

**Priority:** 🟡 MEDIUM — Incorrect behavior in edge cases  
**Estimated Time:** 1 session  
**Dependencies:** None

### Fix 4.1: Key Namespace Separation
**File:** `app/src/main/java/sh/hnet/comfychair/ui/screens/CommunityImagesScreen.kt`  
**Complexity:** S

**What to do:**
1. Prefix keys with type identifier:
```kotlin
// Before
items(filteredPosts, key = { it.postId }) { post -> ... }
items(filteredImages, key = { it.id }) { image -> ... }

// After
items(filteredPosts, key = { "post_${it.postId}" }) { post -> ... }
items(filteredImages, key = { "img_${it.id}" }) { image -> ... }
```

### Fix 4.2: Consistent Cursor Usage
**File:** `app/src/main/java/sh/hnet/comfychair/viewmodel/ModelBrowserViewModel.kt`  
**Complexity:** S

**What to do:**
1. In `loadMoreResults()`, use `currentCursor` consistently:
```kotlin
fun loadMoreResults() {
    // Remove this line:
    // val cursor = state.searchCursor ?: return
    
    viewModelScope.launch {
        // Keep using currentCursor for everything
        val currentCursor = _uiState.value.searchCursor ?: return@launch
        
        val trpcResult = civitaiTrpcService.searchModels(
            ...
            cursor = currentCursor  // Use currentCursor, not outer cursor
        )
        
        if (_uiState.value.searchCursor == currentCursor) { ... }
    }
}
```

### Fix 4.3: Cancel evictionScope
**File:** `app/src/main/java/sh/hnet/comfychair/storage/CivitaiMediaCache.kt`  
**Complexity:** S

**What to do:**
1. Add a `close()` method:
```kotlin
fun close() {
    evictionScope.cancel()
    super.close()  // SQLiteOpenHelper.close()
}
```

2. Call from Application.onTerminate or ProcessLifecycleOwner ON_DESTROY (latter is more reliable).

---

## Phase 5: Defensive Improvements (Optional)

**Priority:** ⚪ LOW — Nice-to-have for robustness  
**Estimated Time:** 0.5 session  
**Dependencies:** None

### Fix 5.1: Wrap markCached in IO
**File:** `app/src/main/java/sh/hnet/comfychair/storage/CivitaiMediaCache.kt`  
**Complexity:** S

**What to do:**
1. Make `markCached` suspend or wrap in coroutine:
```kotlin
fun markCached(id: String, localPath: String, fileSize: Long) {
    evictionScope.launch {
        val values = ContentValues().apply { ... }
        writableDatabase.update(TABLE, values, "id = ?", arrayOf(id))
        evictIfNeeded()
    }
}
```

Note: This changes the semantics slightly (fire-and-forget), but callers don't depend on synchronous completion.

### Fix 5.2: Clear Community Images on Deselect
**File:** `app/src/main/java/sh/hnet/comfychair/viewmodel/ModelBrowserViewModel.kt`  
**Complexity:** S

**What to do:**
1. Add call to `clearCommunityImages()` in `clearSelection()`:
```kotlin
fun clearSelection() {
    clearCommunityImages()  // Add this
    _uiState.value = _uiState.value.copy(
        selectedModel = null,
        selectedVersion = null,
        selectedFile = null,
        selectedModelType = null
    )
}
```

---

## NOT DOING (with reasoning)

### ❌ 1.3 LaunchedEffect Listener Leak
**Reason:** The listener self-removes in `onRenderedFirstFrame()`. When `DisposableEffect` runs `releasePlayer()`, the player is stopped, making the listener effectively dead. No actual leak observed.

### ❌ 2.3 Unstable Lambda (Settings Button)
**Reason:** This is a single IconButton in the screen header, not inside a LazyColumn/LazyGrid. Recomposing one button has negligible cost. Fixing would add complexity for no user-visible benefit.

### ❌ 2.5 String Concatenation in CivitaiTrpcService
**Reason:** The code already uses `StringBuilder.append()`. While string interpolation inside `append()` creates intermediate strings, this runs once per search, not in a hot loop. Switching to org.json would be a larger change with minimal benefit.

### ❌ 3.1 Stale Closure (Settings Sheet)
**Reason:** This is **intended behavior**. When opening a settings sheet, you want to snapshot the current state so users can edit and Cancel without affecting the live state. Reading values at click time is correct.

### ❌ Paging 3 Migration
**Reason:** Too large for this sprint. Would require rewriting pagination in both screens, adding new dependencies, and changing data flow. The current manual pagination works — defer to a future milestone.

### ❌ ViewModel Split
**Reason:** The ViewModel is ~650 lines and functional. Splitting into 3-4 ViewModels adds navigation/coordination complexity. Defer unless we're adding major new features.

### ❌ @Stable/@Immutable Annotations
**Reason:** The data classes use `emptyList()` defaults which are stable. Adding kotlinx.collections.immutable would require a new dependency. Low ROI for now.

---

## Implementation Order

```
Phase 1: Thread Safety (blocking issues, do first)
    ↓
Phase 2: Grid Performance (user-visible lag)
    ↓
Phase 3: Memory (reduces baseline RAM)
    ↓
Phase 4: Data Consistency (edge cases)
    ↓
Phase 5: Optional hardening
```

Each phase is self-contained and can be done in one session. Phases 1-4 are recommended; Phase 5 is nice-to-have.

---

## Testing Checklist

After each phase, verify:

- [ ] App launches without crash
- [ ] Model search returns results
- [ ] Scrolling through 100+ results is smooth (no jank)
- [ ] Video autoplay works in grids (if enabled)
- [ ] Tapping a card opens detail sheet
- [ ] Community images load and paginate
- [ ] Fullscreen image viewer works with swipe
- [ ] Settings save and persist across app restart
- [ ] No ANR when switching between model/community views rapidly

---

## Estimates

| Phase | Complexity | Time |
|-------|------------|------|
| Phase 1 | 3 × S = S-M | ~45 min |
| Phase 2 | S + M + S = M | ~1 hour |
| Phase 3 | M | ~45 min |
| Phase 4 | 3 × S = S-M | ~45 min |
| Phase 5 | 2 × S = S | ~30 min |
| **Total** | | **~4 hours** |
