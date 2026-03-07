# Wave 4: CivitaiMediaCache → Room Migration

**Branch:** `feat/code-review-fixes`  
**Commit:** `refactor: migrate CivitaiMediaCache from SQLite to Room (CachedMediaDao)`

## What Changed

`CivitaiMediaCache` was migrated from raw `SQLiteOpenHelper` to a Room facade:

| Before | After |
|--------|-------|
| `class CivitaiMediaCache : SQLiteOpenHelper(...)` | `class CivitaiMediaCache(context: Context)` |
| Manual `ContentValues` + `writableDatabase` / `readableDatabase` | `CachedMediaDao` suspend functions |
| Custom schema creation in `onCreate()` / `onUpgrade()` | Room handles schema via `ComfyChairDatabase` |
| Cursor iteration with `cursorToEntry()` helper | Extension functions `toCachedMedia()` / `toMediaCacheEntry()` |
| `override fun close()` (SQLiteOpenHelper contract) | `fun close()` (no superclass) |

## Files Changed

- **Modified:** `app/src/main/java/sh/hnet/comfychair/storage/CivitaiMediaCache.kt`
  - Removed: `SQLiteOpenHelper` inheritance, `onCreate`, `onUpgrade`, `cursorToEntry`, all `ContentValues` + cursor code
  - Added: `CachedMediaDao` reference, extension functions `toCachedMedia` / `toMediaCacheEntry`
  - Preserved: Full public API — all method signatures identical

## Files NOT Changed (zero callers need updates)

- `ModelBrowserViewModel.kt` — uses `mediaCache.cacheLimitMb`, `prefetchEnabled`, `prefetchCount`, etc.
- `MediaPrefetchManager.kt` — takes `CivitaiMediaCache` as constructor parameter
- `ModelBrowserScreen.kt` — reads stats (`totalCachedBytes`, `cachedCount`) from `viewModel.mediaCache`

## Design Decisions

- **Facade pattern:** Public API preserved completely — all callers compile without changes.
- **`runBlocking`** used for methods that were previously synchronous (`getLocalPath`, `getUncachedItems`, stats methods). These are called from ViewModel coroutines on Dispatchers.IO so blocking is safe.
- **`evictionScope`** (CoroutineScope on Dispatchers.IO) reused for fire-and-forget DAO writes (`upsertMetadata`, `upsertBatch`, `touch`, `clearCachedFiles`, `clearAll`).
- **`markCached`** remains `suspend` (was already `suspend` in original, runs eviction check after).
- **Data consolidation:** `civitai_media_cache.db` is replaced by the `cached_media` table in `comfychair.db` (Room's unified database). Old SQLite file will simply go unused on upgrade.

## Stats

- 196 lines removed, 59 lines added (net −137 lines)
- 0 callers modified
