# Wave 1 Summary — Room Database Setup

## What Was Done

### Build System Changes

| File | Change |
|------|--------|
| `gradle/libs.versions.toml` | Added `ksp = "2.0.21-1.0.28"`, `room = "2.6.1"` versions; `room-runtime`, `room-ktx`, `room-compiler` library refs; `ksp` plugin alias |
| `build.gradle.kts` (root) | Added `alias(libs.plugins.ksp) apply false` |
| `app/build.gradle.kts` | Added `alias(libs.plugins.ksp)` to plugins block; `room-runtime`, `room-ktx` implementations + `ksp(room-compiler)` to dependencies |

### Entities (10 total)

| Entity | Table | Notes |
|--------|-------|-------|
| `CachedMedia` | `cached_media` | Mirrors existing SQLite `media_cache` schema exactly; indexes on `lastViewed`, `modelVersionId`, `localPath` |
| `ModelVersion` | `model_versions` | `modelName = null` signals seed-only rows not yet enriched |
| `ImageGenerationData` | `image_generation_data` | Full generation meta; `hideMeta=true` rows have most fields null |
| `ImageResource` | `image_resources` | Composite PK `(imageId, versionId)`; FK cascade-deletes with `ImageGenerationData` |
| `Tag` | `tags` | |
| `ImageTag` | `image_tags` | Composite PK join table; managed via `TagDao` |
| `Tool` | `tools` | |
| `ImageTool` | `image_tools` | Composite PK join table; managed via `ToolDao` |
| `Technique` | `techniques` | `type` = "Image" or "Video" |
| `ImageTechnique` | `image_techniques` | Composite PK join table; managed via `TechniqueDao` |

### DAOs (7 total)

| DAO | Highlights |
|-----|------------|
| `CachedMediaDao` | `markCached`, `touch`, `getUncached`, `getOldestCached`, eviction helpers |
| `ModelVersionDao` | `insertIfAbsent` vs `upsert` separation; `getEnrichedByIds`/`getUnenrichedIds` for lazy enrichment |
| `ImageGenerationDataDao` | `getFreshById(threshold)` for TTL-aware reads; `evictOlderThan` |
| `ImageResourceDao` | `@Relation`-based `ImageResourceWithVersion`; `@Transaction` on join query |
| `TagDao` | Owns `ImageTag` inserts + reverse-lookup queries |
| `ToolDao` | Owns `ImageTool` inserts + reverse-lookup queries |
| `TechniqueDao` | Owns `ImageTechnique` inserts + reverse-lookup queries |

### Database Class

`ComfyChairDatabase` — singleton pattern (`@Volatile` + double-checked lock), `version = 1`, `exportSchema = false`. All 10 entities registered; 7 abstract DAO accessors.

## Design Decisions

- **Join table ownership:** Each join table's `@Insert` lives in the parent entity's DAO (`ImageTag` → `TagDao`, `ImageTool` → `ToolDao`, `ImageTechnique` → `TechniqueDao`). No dedicated join DAOs — keeps related queries co-located and avoids DAO sprawl.
- **`@Upsert` vs `@Insert(IGNORE)`:** `insertIfAbsent`-style methods use `IGNORE` to avoid overwriting enriched data with stale stubs. `upsert` available separately for intentional overwrites.
- **`ImageResourceWithVersion`:** Kept in `ImageResourceDao.kt` (not a separate file) since it's only consumed there. Avoids a 1-use file.
- **No `exportSchema`:** Set `false` for now. Flip to `true` and add schema dir to VCS before shipping migrations.

## Next Steps (Wave 2)

- Wire `ComfyChairDatabase.getInstance()` into the app (Application class or DI)
- Migrate `CivitaiMediaCache` reads/writes to `CachedMediaDao`
- Populate `ModelVersion`, `Tag`, `Tool`, `Technique` from API responses
- Add `exportSchema = true` + schema directory before first production migration
