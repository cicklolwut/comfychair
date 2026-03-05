# Model Browser UX Plan

## Phase 1: Search Grid Polish (ViewModel + Screen)
**Goal:** Infinite scroll, image prefetching, disk cache, filters bottom sheet

### 1A. Infinite scroll pagination
**Files:** `ModelBrowserViewModel.kt`, `ModelBrowserScreen.kt`

Add to `ModelBrowserUiState`:
```kotlin
val searchOffset: Int = 0,
val hasMoreResults: Boolean = true,
val isLoadingMore: Boolean = false
```

ViewModel:
- `searchModels()` resets offset to 0, replaces results
- New `loadMoreResults()` increments offset by 20, appends to `searchResults`
- Track `hasMoreResults` from `meiliResult.totalHits > current results size`

Screen — detect near-bottom in `LazyVerticalGrid`:
```kotlin
val gridState = rememberLazyGridState()
LaunchedEffect(gridState) {
    snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
        .collect { lastIndex ->
            if (lastIndex != null && lastIndex >= uiState.searchResults.size - 6
                && uiState.hasMoreResults && !uiState.isLoadingMore) {
                viewModel.loadMoreResults()
            }
        }
}
```

### 1B. Image prefetching + disk cache
**Files:** New `ComfyChairApplication.kt` (or existing Application class), `build.gradle.kts`

Coil 3 disk cache — configure in the Application class:
```kotlin
ImageLoader.Builder(this)
    .memoryCache {
        MemoryCache.Builder()
            .maxSizePercent(this@App, 0.25) // 25% of app memory
            .build()
    }
    .diskCache {
        DiskCache.Builder()
            .directory(cacheDir.resolve("image_cache"))
            .maxSizeBytes(100 * 1024 * 1024) // 100MB
            .build()
    }
    .build()
```

Prefetch next ~6 off-screen items in `LazyVerticalGrid` using `LaunchedEffect`:
```kotlin
LaunchedEffect(gridState) {
    snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
        .collect { lastVisible ->
            // Prefetch next 6 items beyond visible
            val prefetchEnd = minOf(lastVisible + 8, uiState.searchResults.size)
            for (i in (lastVisible + 1) until prefetchEnd) {
                uiState.searchResults.getOrNull(i)?.thumbnailUrl?.let { url ->
                    context.imageLoader.enqueue(
                        ImageRequest.Builder(context).data(url).build()
                    )
                }
            }
        }
}
```

### 1C. Filters bottom sheet
**Files:** `ModelBrowserScreen.kt`

Replace the inline filters/NSFW/change key row with:
- FAB (bottom-right, similar to download button) with filter icon
- Clicking opens `ModalBottomSheet` containing:
  - All current `SearchFilters` content (sort, period, type, base model)
  - NSFW toggle
  - "Change API Key" button
- Remove the old inline row entirely

The FAB sits above the grid, doesn't scroll with it.

---

## Phase 2: Model Detail Sheet (Screen)
**Goal:** Full-height sheet, clickable description images, NSFW filtering

### 2A. Full-height bottom sheet
**Files:** `ModelBrowserScreen.kt`

Change `ModalBottomSheet` to expand fully:
```kotlin
val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState
) { ... }
```

### 2B. Clickable images in HTML description
**Files:** `ModelBrowserScreen.kt` (`HtmlText` composable)

Current `HtmlText` uses `TextView` with `Html.fromHtml()`. Images in HTML are ignored.

Options:
1. Use `Html.ImageGetter` to load images via Coil into the `TextView`
2. Replace with a Compose-native HTML renderer that supports image tags

Option 1 is simpler — implement `Html.ImageGetter` that fetches images and sets drawables:
```kotlin
val imageGetter = Html.ImageGetter { source ->
    // Return placeholder, then async load and invalidate
}
```

This is complex to get right in a TextView. Better approach: intercept image URLs from HTML, render them as separate `AsyncImage` composables between text blocks. Parse HTML, split on `<img>` tags, render text and images alternately.

### 2C. NSFW filter on description images + community images
**Files:** `CommunityImagesScreen.kt`, `ModelBrowserScreen.kt`

Community images already have `nsfwLevel`. When `showNsfw` is false:
- Filter community images to `nsfwLevel <= 4` before displaying
- For model version images in the detail sheet gallery, apply same filter
- Pass `showNsfw` through as a parameter

---

## Phase 3: Community Images Overhaul (Screen + ViewModel)
**Goal:** Reliable pagination, fullscreen viewer with swipe, compact layout

### 3A. Fix pagination consistency
**Files:** `ModelBrowserViewModel.kt`, `CommunityImagesScreen.kt`

Current issue: pagination sometimes stops working. Root causes:
- `communityImagesCursor` can be null even when more images exist (API quirk)
- `hasMoreCommunityImages` set to false prematurely when `nextCursor` is null

Fix: Track total loaded vs estimated total. Continue loading until the API returns an empty page (not just null cursor). Add a minimum threshold — if we got `limit` results, assume more exist.

### 3B. Layout changes — move button, remove header
**Files:** `ModelBrowserScreen.kt`, `CommunityImagesScreen.kt`

Model detail sheet:
- Move "Community Images" button above the download FAB (more vertical space)
- When viewing community images: remove the header row ("Community Images" title + back button)
- Add floating back button (bottom-right) — small FAB with back arrow
- This gives the grid almost full height

### 3C. Fullscreen image viewer with swipe
**Files:** `CommunityImagesScreen.kt`, possibly new `CommunityImageViewerScreen.kt`

Replace `ImageDetailSheet` (bottom sheet with small image + metadata below) with:
- Fullscreen `HorizontalPager` using images list + clicked index
- Reuse existing `ImageViewer` component (pinch-zoom, pan, double-tap)
- Load full-resolution image (not width=600)
- Semi-transparent bottom bar showing truncated prompt (1-2 lines)
- Tap image to toggle prompt bar visibility
- "Details" FAB (bottom-right) opens a sheet with full generation metadata (prompt, neg prompt, sampler, steps, CFG, seed, resources)
- Swipe left/right to navigate between community images
- Prefetch adjacent images (i-1, i+1)

This pattern matches the existing `MediaViewerScreen` which already has `HorizontalPager` + `ImageViewer` + metadata sheet + floating toolbar.

Key difference: `MediaViewerScreen` loads from local files via `MediaViewerViewModel`. Community images load from URLs. We need to either:
1. Adapt `MediaViewerScreen` to accept URL-based items (preferred — less duplication)
2. Build a parallel viewer (more code but no risk of breaking gallery)

Recommendation: Option 2 for now — build `CommunityImageViewer` composable that follows the same pattern but is self-contained. Merge later once both stabilize.

### 3D. Prevent re-fetching on image click
**Files:** `CommunityImagesScreen.kt`

Current `ImageDetailSheet` loads a new `AsyncImage` with `width=600`. The grid already loaded `width=200`. When clicking:
- The width=200 version is in Coil's cache
- The width=600 version is a new URL → new fetch

Fix: Use `placeholder` with the cached thumbnail while the larger version loads:
```kotlin
AsyncImage(
    model = ImageRequest.Builder(context)
        .data(image.url.replace("/original=true/", "/width=1024/"))
        .placeholderMemoryCacheKey(thumbnailCacheKey)
        .crossfade(true)
        .build(),
    ...
)
```

With the fullscreen viewer (3C), this becomes: load original resolution, show thumbnail as placeholder during load.

---

## Phase 4: Description Images (nice-to-have)
**Goal:** Make images in model HTML descriptions viewable

### 4A. Rich HTML renderer with image support

This is the most complex item. Civitai descriptions contain `<img>` tags with full URLs. Current `HtmlText` ignores them.

Approach: Parse HTML into segments (text blocks + image URLs), render as a `Column` of `Text` and `AsyncImage` composables. Clicking an image opens it in a simple viewer dialog.

This is lower priority since it's cosmetic — the description is readable without images.

---

## Execution Summary

| Phase | Items | Complexity | Dependencies |
|-------|-------|-----------|-------------|
| 1 | 1A, 1B, 1C | Medium | None (all independent within phase) |
| 2 | 2A, 2B, 2C | 2A: Easy, 2B: Hard, 2C: Easy | None |
| 3 | 3A, 3B, 3C, 3D | 3A-3B: Easy, 3C: Medium, 3D: Easy | 1B (disk cache helps 3D) |
| 4 | 4A | Hard | None |

**Parallelizable:**
- Phase 1 (1A, 1B, 1C) — all three are independent, can be 3 parallel agents
- Phase 2A + 2C — independent of each other
- Phase 3A + 3B — independent of each other
- Phase 3C depends on understanding existing MediaViewer pattern
- Phase 3D is a small fix, can bundle with 3C

**Sequential dependencies:**
- 1B (disk cache) should land before 3C/3D (viewer benefits from cache)
- 3B (layout changes) should land before 3C (viewer replaces the detail sheet)
- 2B and 4A are the hardest items — defer if needed

**Suggested agent deployment:**
- **Wave 1 (3 parallel):** 1A (pagination) + 1B (cache) + 1C (filter sheet)
- **Wave 2 (2 parallel):** 2A+2C (sheet+nsfw, small) + 3A+3B (community fixes+layout)
- **Wave 3 (1 agent):** 3C+3D (fullscreen viewer — needs careful integration)
- **Wave 4 (if desired):** 2B + 4A (HTML images — complex, defer)
