# Wave 2 Implementation Summary — Room DB Repository Layer

**Date:** 2026-03-07  
**Scope:** Repository layer creation + service/viewmodel wiring for Civitai image cache

---

## Files Created

### `app/.../db/repository/CivitaiCacheRepository.kt` (new)

Single entry point for all cache read/write operations. Wraps all 7 DAOs and exposes:

- **ModelVersion seeding/enrichment** — `seedModelVersionIds` (INSERT IGNORE), `cacheModelVersions` (upsert)
- **Generation data** — `cacheGenerationData`, `getFreshGenerationData` (24h TTL via `cachedAt > threshold`)
- **Resources** — `getResourcesWithVersions` (Room `@Relation` join)
- **Tools/Techniques/Tags** — `cacheImageTools`, `cacheImageTechniques`, `cacheImageTags`, `hasTools`
- **Singleton pattern** with double-checked locking (`getInstance(context)`)

**Type fix vs template:** In `cacheGenerationData`, `modelId` is set to `0L` (not `r.modelVersionId`)
since `GenerationResource` only exposes `modelVersionId`, not the parent model ID. This avoids
storing incorrect data in the `model_versions` table.

---

## Files Modified

### `CivitaiTrpcService.kt`

**Changes:**
- Added `context: Context? = null` optional parameter (default null = backward-compatible)
- Lazy `repository: CivitaiCacheRepository?` initialized from context's applicationContext
- After `getModelImages` builds `allImages`: fire-and-forget `CoroutineScope(Dispatchers.IO).launch`
  seeds ModelVersion IDs from `img.meta?.resources?.mapNotNull { it.modelVersionId }`

**Adaptation from task spec:** `CommunityImage` does NOT have `modelVersionIds`, `modelVersionIdsManual`,
`toolIds`, `techniqueIds`, or `tagIds` — those fields don't exist in the current model class.
The seeding code was adapted to use `img.meta?.resources` instead. Since `getModelImages` sends
`withMeta:false`, most gallery calls will seed an empty list (no-op), which is correct —
enrichment happens via `fetchImageMetadata` (Task 3). The infrastructure is in place for when
`withMeta=true` is used.

**Imports added:** `android.content.Context`, `kotlinx.coroutines.CoroutineScope`,
`kotlinx.coroutines.launch`, `CivitaiCacheRepository`

---

### `ModelBrowserViewModel.kt`

**Changes:**
- Added `civitaiCacheRepository = CivitaiCacheRepository.getInstance(getApplication())` field
- Updated `civitaiTrpcService` instantiation to pass `getApplication()` as context (enables Task 2 seeding)
- Replaced `fetchImageMetadata` body with cache-first logic:
  1. `withContext(Dispatchers.IO)` check `getFreshGenerationData(imageId)` — **awaited** (critical path)
  2. Cache hit → `getResourcesWithVersions` + `buildMetadataFromCache` → return early
  3. Cache miss → existing `civitaiTrpcService.getImageMetadata(imageId, postId)` call
  4. On network success → `withContext(Dispatchers.IO)` `cacheGenerationData(...)` then `onResult`
- Added `private fun buildMetadataFromCache(cached, resources): GenerationMetadata` — maps
  `ImageGenerationData` + `List<ImageResourceWithVersion>` → `GenerationMetadata`

**Signature unchanged:** `fetchImageMetadata(imageId: Long, postId: Long? = null, onResult: (GenerationMetadata?) -> Unit)` — identical public API.

**Imports added:** `ImageResourceWithVersion`, `ImageGenerationData`, `CivitaiCacheRepository`,
`GenerationMetadata`, `GenerationResource`

---

### `ComfyChairApplication.kt`

**Changes:**
- Added `override fun onCreate()` calling `super.onCreate()` + `CivitaiCacheRepository.getInstance(this)`
- Warms up the Room DB singleton at app start to avoid first-access latency on the UI thread

**Import added:** `CivitaiCacheRepository`

---

## Constraints Compliance

| Constraint | Status |
|---|---|
| No method signature changes | ✅ All public signatures preserved; service constructor adds optional param with default |
| No removed functionality | ✅ All existing code intact |
| New coroutine work on Dispatchers.IO | ✅ Service uses `CoroutineScope(Dispatchers.IO).launch`; VM uses `withContext(Dispatchers.IO)` |
| Service cache ops fire-and-forget | ✅ `CoroutineScope(Dispatchers.IO).launch` — doesn't block `getModelImages` return |
| Viewmodel cache check on critical path | ✅ `withContext` is awaited before network decision |

---

## Known Gaps / Future Work

- `CommunityImage` model doesn't expose `toolIds`, `techniqueIds`, `tagIds` — tool/technique/tag
  seeding from gallery is not yet wired (requires model class update to expose these fields from API)
- `modelId` in cached `ModelVersion` rows created from `GenerationResource` is set to `0L`
  (no parent model ID available from that source — needs enrichment from a separate model lookup)
- Wave 3 (planned): enrich unenriched ModelVersion rows via batch API calls using `getUnenrichedVersionIds`
