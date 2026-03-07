# Wave Validation Report — ComfyChair Room Database Migration

**Generated:** 2026-03-07  
**Reviewer:** Opus-validation subagent  
**Project:** `/home/openclaw/.openclaw/workspace/projects/comfychair/`

---

## Summary

| Status | Count |
|--------|-------|
| ✅ PASS | 35 |
| ⚠️ WARNING | 3 |
| ❌ ERROR | 1 |

### Critical Issues (Must Fix Before Compile)

1. **❌ CachedMediaDao column name mismatch** — DAO queries use `snake_case` column names but entity uses `camelCase` without `@ColumnInfo` annotations. Room compiler will fail.

---

## Build Files

### gradle/libs.versions.toml

| Check | Status | Notes |
|-------|--------|-------|
| KSP version matches Kotlin 2.0.21 | ✅ PASS | `ksp = "2.0.21-1.0.28"` correctly pairs with `kotlin = "2.0.21"` |
| Room version correct | ✅ PASS | `room = "2.6.1"` is current stable |
| Room libraries declared | ✅ PASS | `room-runtime`, `room-ktx`, `room-compiler` all present |
| KSP plugin declared | ✅ PASS | `ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }` |

### app/build.gradle.kts

| Check | Status | Notes |
|-------|--------|-------|
| KSP plugin applied | ✅ PASS | `alias(libs.plugins.ksp)` in plugins block |
| Room runtime dep | ✅ PASS | `implementation(libs.room.runtime)` |
| Room ktx dep | ✅ PASS | `implementation(libs.room.ktx)` |
| Room compiler via KSP | ✅ PASS | `ksp(libs.room.compiler)` |
| No conflicting annotation processors | ✅ PASS | No `kapt` blocks present |

### build.gradle.kts (root)

| Check | Status | Notes |
|-------|--------|-------|
| KSP plugin declared apply false | ✅ PASS | `alias(libs.plugins.ksp) apply false` |

---

## Room Schema Correctness

### db/entity/ImageResource.kt

| Check | Status | Notes |
|-------|--------|-------|
| FK to ImageGenerationData | ✅ PASS | `ForeignKey(entity = ImageGenerationData::class, parentColumns = ["imageId"], childColumns = ["imageId"], onDelete = ForeignKey.CASCADE)` |
| Composite PK correct | ✅ PASS | `primaryKeys = ["imageId", "versionId"]` |
| Indices present | ✅ PASS | `Index("versionId")`, `Index("imageId")` |

### db/entity/ImageTag.kt

| Check | Status | Notes |
|-------|--------|-------|
| Composite PK correct | ✅ PASS | `primaryKeys = ["imageId", "tagId"]` |
| Indices present | ✅ PASS | `Index("tagId")`, `Index("imageId")` |

### db/entity/ImageTool.kt

| Check | Status | Notes |
|-------|--------|-------|
| Composite PK correct | ✅ PASS | `primaryKeys = ["imageId", "toolId"]` |
| Indices present | ✅ PASS | `Index("toolId")`, `Index("imageId")` |

### db/entity/ImageTechnique.kt

| Check | Status | Notes |
|-------|--------|-------|
| Composite PK correct | ✅ PASS | `primaryKeys = ["imageId", "techniqueId"]` |
| Indices present | ✅ PASS | `Index("techniqueId")`, `Index("imageId")` |

### db/ComfyChairDatabase.kt

| Check | Status | Notes |
|-------|--------|-------|
| All 10 entities listed | ✅ PASS | `CachedMedia`, `ModelVersion`, `ImageGenerationData`, `ImageResource`, `Tag`, `ImageTag`, `Tool`, `ImageTool`, `Technique`, `ImageTechnique` |
| All 7 DAOs declared | ✅ PASS | `cachedMediaDao()`, `modelVersionDao()`, `imageGenerationDataDao()`, `imageResourceDao()`, `tagDao()`, `toolDao()`, `techniqueDao()` |
| Singleton pattern correct | ✅ PASS | Double-checked locking with `@Volatile` and `synchronized` |

---

## DAO Correctness

### db/dao/ImageResourceDao.kt

| Check | Status | Notes |
|-------|--------|-------|
| `@Transaction` on relation query | ✅ PASS | `@Transaction` present on `getWithVersions()` |
| `ImageResourceWithVersion` location | ✅ PASS | Defined in same file, `@Embedded` + `@Relation` annotations correct |

### db/dao/ModelVersionDao.kt

| Check | Status | Notes |
|-------|--------|-------|
| `insertAllIfAbsent` uses IGNORE | ✅ PASS | `@Insert(onConflict = OnConflictStrategy.IGNORE)` |
| `insertIfAbsent` uses IGNORE | ✅ PASS | `@Insert(onConflict = OnConflictStrategy.IGNORE)` |

### db/dao/CachedMediaDao.kt

| Check | Status | Notes |
|-------|--------|-------|
| SQL column names match entity | ❌ ERROR | **Mismatch: DAO uses `snake_case`, entity uses `camelCase`** |

**Details:**

**File:** `app/src/main/java/sh/hnet/comfychair/db/dao/CachedMediaDao.kt`

The `CachedMedia` entity uses camelCase field names without `@ColumnInfo` annotations:
```kotlin
// Entity fields (Line 12-25 of CachedMedia.kt)
val localPath: String? = null,
val thumbPath: String? = null,
val fileSize: Long = 0L,
val lastViewed: Long = 0L,
val cachedAt: Long = 0L,
val modelVersionId: String? = null
```

But DAO queries reference `snake_case` column names:
```kotlin
// Line 16
@Query("SELECT local_path FROM cached_media WHERE id = :id AND local_path IS NOT NULL")

// Line 19
@Query("UPDATE cached_media SET local_path = :path, file_size = :size, cached_at = :now, last_viewed = :now WHERE id = :id")

// Line 25
@Query("SELECT * FROM cached_media WHERE local_path IS NULL AND (:versionId IS NULL OR model_version_id = :versionId) ...")
```

**Fix Option A (Recommended):** Change DAO queries to use camelCase:
```kotlin
@Query("SELECT localPath FROM cached_media WHERE id = :id AND localPath IS NOT NULL")
suspend fun getLocalPath(id: String): String?

@Query("UPDATE cached_media SET localPath = :path, fileSize = :size, cachedAt = :now, lastViewed = :now WHERE id = :id")
suspend fun markCached(id: String, path: String, size: Long, now: Long = System.currentTimeMillis())

@Query("SELECT * FROM cached_media WHERE localPath IS NULL AND (:versionId IS NULL OR modelVersionId = :versionId) AND (:type IS NULL OR type = :type) ORDER BY lastViewed DESC LIMIT :limit")
suspend fun getUncached(versionId: String? = null, type: String? = null, limit: Int = 20): List<CachedMedia>

@Query("SELECT COALESCE(SUM(fileSize), 0) FROM cached_media WHERE localPath IS NOT NULL")
suspend fun totalCachedBytes(): Long

@Query("SELECT COUNT(*) FROM cached_media WHERE localPath IS NOT NULL")
suspend fun cachedCount(): Int

@Query("SELECT * FROM cached_media WHERE localPath IS NOT NULL ORDER BY lastViewed ASC LIMIT :limit")
suspend fun getOldestCached(limit: Int): List<CachedMedia>

@Query("UPDATE cached_media SET localPath = NULL, fileSize = 0 WHERE id = :id")
suspend fun clearLocalPath(id: String)

@Query("UPDATE cached_media SET localPath = NULL, fileSize = 0 WHERE localPath IS NOT NULL")
suspend fun clearAllLocalPaths()
```

**Fix Option B:** Add `@ColumnInfo` annotations to entity fields:
```kotlin
@ColumnInfo(name = "local_path") val localPath: String? = null,
@ColumnInfo(name = "thumb_path") val thumbPath: String? = null,
@ColumnInfo(name = "file_size") val fileSize: Long = 0L,
@ColumnInfo(name = "last_viewed") val lastViewed: Long = 0L,
@ColumnInfo(name = "cached_at") val cachedAt: Long = 0L,
@ColumnInfo(name = "model_version_id") val modelVersionId: String? = null
```

---

## Repository

### db/repository/CivitaiCacheRepository.kt

| Check | Status | Notes |
|-------|--------|-------|
| `GenerationResource.modelVersionId` reference | ✅ PASS | `r.modelVersionId` matches field in model |
| `GenerationResource.weight` reference | ✅ PASS | `r.weight` matches field in model |
| `GenerationResource.name` reference | ✅ PASS | `r.name` matches field in model |
| `GenerationResource.type` reference | ✅ PASS | `r.type` matches field in model |

---

## Service Changes (CivitaiTrpcService.kt)

### parseCommunityImage

| Check | Status | Notes |
|-------|--------|-------|
| All new CommunityImage fields passed | ✅ PASS | `modelVersionIds`, `modelVersionIdsManual`, `toolIds`, `techniqueIds`, `tagIds`, `baseModel`, `onSite`, `hideMeta` |
| Field names correct | ✅ PASS | All match `CommunityImage` data class |

### getModelImages

| Check | Status | Notes |
|-------|--------|-------|
| Filter params added | ✅ PASS | `types`, `withMeta`, `fromPlatform`, `nonRemixesOnly`, `remixesOnly`, `hideManualResources`, `hideAutoResources` |
| `period` parameterized | ✅ PASS | `period: String = "AllTime"` parameter |
| JSON body conditionally appends | ✅ PASS | Uses `if (param != null) append(...)` pattern |

### getImageMetadata

| Check | Status | Notes |
|-------|--------|-------|
| Calls `image.getGenerationData` | ✅ PASS | `val url = "$TRPC_BASE/image.getGenerationData?input=$encodedInput"` |
| URL correct | ✅ PASS | Uses `TRPC_BASE` constant + correct endpoint |

### parseGetGenerationDataResponse

| Check | Status | Notes |
|-------|--------|-------|
| Handles `resources[]` array | ✅ PASS | Primary loop at line ~700 |
| Handles `civitaiResources[]` fallback | ✅ PASS | Secondary loop at line ~720 |
| Handles null `meta` | ✅ PASS | `val metaObj = if (!json.isNull("meta")) json.optJSONObject("meta") else null` |
| Handles `versionId` vs `modelVersionId` | ✅ PASS | `val versionId = when { res.has("versionId") -> ... res.has("modelVersionId") -> ... }` |
| Skips duplicate versionIds | ✅ PASS | Uses `seenVersionIds` set |

---

## Model Changes (ModelBrowser.kt)

### CommunityImage

| Check | Status | Notes |
|-------|--------|-------|
| `modelVersionIds: List<Long>` added | ✅ PASS | Default `emptyList()` |
| `modelVersionIdsManual: List<Long>` added | ✅ PASS | Default `emptyList()` |
| `toolIds: List<Int>` added | ✅ PASS | Default `emptyList()` |
| `techniqueIds: List<Int>` added | ✅ PASS | Default `emptyList()` |
| `tagIds: List<Int>` added | ✅ PASS | Default `emptyList()` |
| `baseModel: String?` added | ✅ PASS | Default `null` |
| `onSite: Boolean` added | ✅ PASS | Default `false` |
| `hideMeta: Boolean` added | ✅ PASS | Default `false` |
| Constructor compiles | ✅ PASS | Data class with all defaults |

---

## Application Changes (ComfyChairApplication.kt)

### Technique Seeding

| Check | Status | Notes |
|-------|--------|-------|
| 8 entries seeded | ✅ PASS | IDs 1-8 |
| Correct IDs/names/types | ✅ PASS | txt2img, img2img, inpainting, workflow, vid2vid, txt2vid, img2vid, controlnet |

### Tool Fetch

| Check | Status | Notes |
|-------|--------|-------|
| `hasTools()` guard present | ✅ PASS | `if (!repo.hasTools()) { ... }` |
| Correct `CivitaiTrpcService` instantiation | ✅ PASS | `CivitaiTrpcService(settings, this@ComfyChairApplication)` |
| Fire-and-forget coroutine | ✅ PASS | `CoroutineScope(Dispatchers.IO).launch { ... }` |

---

## UI Changes (CommunityImagesScreen.kt)

### ImageMetadataSheet

| Check | Status | Notes |
|-------|--------|-------|
| `selectedImage: CommunityImage?` param added | ✅ PASS | Line ~660 |
| Call sites updated | ✅ PASS | Single call site at line ~545 passes `currentImage` |
| `baseModel` chip uses gallery-first fallback | ✅ PASS | `val displayBaseModel = selectedImage?.baseModel ?: meta.baseModel` at line ~720 |

---

## Cross-Cutting Concerns

| Check | Status | Notes |
|-------|--------|-------|
| Missing imports | ✅ PASS | All imports present |
| Suspend from non-coroutine | ✅ PASS | All suspend calls inside `launch {}` or `withContext {}` |
| Hardcoded strings | ⚠️ WARNING | Technique names in `ComfyChairApplication.kt` could be constants |
| Null-pointer risks | ⚠️ WARNING | See notes below |
| Old SQLite cache coexistence | ⚠️ WARNING | See notes below |

### Warning: Hardcoded Technique Strings

**File:** `ComfyChairApplication.kt` (Line 21-28)

Consider extracting to constants for maintainability:
```kotlin
// In companion object or separate file
object TechniqueIds {
    const val TXT2IMG = 1
    const val IMG2IMG = 2
    // ...
}
```

**Impact:** Low — nice-to-have, not blocking.

### Warning: Potential Null Safety

**File:** `CommunityImagesScreen.kt` (Line ~487)

```kotlin
val firstImage = post.images.first()  // Could throw if images is empty
```

The code filters `post.images` earlier, so in practice this should be safe. However, a defensive `firstOrNull()` with early return would be more robust.

**Impact:** Low — the filtering logic ensures `images` is non-empty before this point.

### Warning: Old SQLite Cache Coexistence

**Files:** `CivitaiMediaCache.kt` + `CachedMediaDao.kt`

The app has TWO database implementations for media caching:
1. Old SQLite helper: `CivitaiMediaCache.kt` (DB name: `civitai_media_cache.db`)
2. New Room entity: `CachedMedia.kt` + `CachedMediaDao.kt` (part of `comfychair.db`)

Both are used in `ModelBrowserViewModel.kt`:
- `mediaCache` → old `CivitaiMediaCache`
- `civitaiCacheRepository` → new Room repo

This dual-database approach may be intentional (gradual migration), but should be documented or consolidated eventually.

**Impact:** Medium — works fine but adds complexity. Consider migrating fully to Room in a future wave.

---

## Conclusion

The Wave 1-3 implementation is **mostly correct** with one blocking issue:

### Must Fix Before Compile

1. **CachedMediaDao column names** — Change all DAO queries from `snake_case` to `camelCase` column names (see detailed fix above)

### Nice-to-Have Improvements

1. Extract technique constants
2. Add `firstOrNull()` defensive check for empty post images
3. Document or consolidate dual-cache architecture

After fixing the CachedMediaDao column names, the code should compile and function correctly.
