# Room Migration Plan

**Goal:** Replace raw SQLite (`CivitaiMediaCache`) with Room, add new tables for Civitai model/resource/tag caching, and wire up a cache-first fetch strategy for generation metadata.

---

## Schema

### Entities

| Entity | Table | Purpose |
|--------|-------|---------|
| `CachedMedia` | `cached_media` | Port of existing `media_cache` — video file paths + LRU eviction |
| `ModelVersion` | `model_versions` | Hash→name map: `versionId → modelName, modelType, etc.` |
| `ImageGenerationData` | `image_generation_data` | Cached `getGenerationData` responses (prompt, sampler, etc.) |
| `ImageResource` | `image_resources` | Normalized join: `imageId + versionId + strength` |
| `Tag` | `tags` | Tag ID → name/type map |
| `ImageTag` | `image_tags` | Join table: imageId ↔ tagId (enables tag filtering) |
| `Tool` | `tools` | Tool ID → name/type (from `tool.getAll`, cache rarely) |

### Normalized Resource Design
`ImageResource` stores only `(imageId, versionId, strength)`.
Names/types come from `ModelVersion` via JOIN.
`ModelVersion` can be seeded from gallery `modelVersionIds[]` (partial, no name) before `getGenerationData` fires, then enriched later. JOIN gracefully handles partial rows.

---

## Wave 1 — Build Setup + Entities + DAOs

**Files to create/modify:**

1. `gradle/libs.versions.toml` — add `ksp`, `room` versions + libraries + KSP plugin
2. `build.gradle.kts` (root) — add KSP plugin alias (apply false)
3. `app/build.gradle.kts` — add KSP plugin + Room dependencies
4. `app/src/main/java/sh/hnet/comfychair/db/entity/CachedMedia.kt`
5. `app/src/main/java/sh/hnet/comfychair/db/entity/ModelVersion.kt`
6. `app/src/main/java/sh/hnet/comfychair/db/entity/ImageGenerationData.kt`
7. `app/src/main/java/sh/hnet/comfychair/db/entity/ImageResource.kt`
8. `app/src/main/java/sh/hnet/comfychair/db/entity/Tag.kt`
9. `app/src/main/java/sh/hnet/comfychair/db/entity/ImageTag.kt`
10. `app/src/main/java/sh/hnet/comfychair/db/entity/Tool.kt`
11. `app/src/main/java/sh/hnet/comfychair/db/dao/CachedMediaDao.kt`
12. `app/src/main/java/sh/hnet/comfychair/db/dao/ModelVersionDao.kt`
13. `app/src/main/java/sh/hnet/comfychair/db/dao/ImageGenerationDataDao.kt`
14. `app/src/main/java/sh/hnet/comfychair/db/dao/ImageResourceDao.kt`
15. `app/src/main/java/sh/hnet/comfychair/db/dao/TagDao.kt`
16. `app/src/main/java/sh/hnet/comfychair/db/dao/ToolDao.kt`
17. `app/src/main/java/sh/hnet/comfychair/db/ComfyChairDatabase.kt`

**Validation:** Code review for correct Room annotations, types, DAO method signatures. No Android SDK so we can't compile — review manually.

---

## Wave 2 — Repository + Service Integration

**Files to create/modify:**

1. `db/repository/CivitaiCacheRepository.kt` — unified repo wrapping all DAOs
2. `storage/CivitaiMediaCache.kt` — delegate file ops to Room `CachedMediaDao`; keep eviction logic
3. `service/CivitaiTrpcService.kt` — populate `ModelVersion` + `Tool` tables on gallery/tool fetches
4. `viewmodel/ModelBrowserViewModel.kt` — cache `ImageGenerationData` + `ImageResource` after `getGenerationData` calls; check Room cache first before hitting network

**Validation:** Verify repository API is complete, service integration doesn't break existing call flow, viewmodel cache-check logic is correct.

---

## Wave 3 — Gallery Seeding + UI Wiring

**Files to create/modify:**

1. `service/CivitaiTrpcService.kt` — seed `ModelVersion` (partial rows) from `modelVersionIds[]` in gallery responses
2. `ui/screens/CommunityImagesScreen.kt` — resolve model names from Room in resources display
3. `viewmodel/ModelBrowserViewModel.kt` — `getGenerationData` cache-first (skip network if fresh)
4. `db/dao/TagDao.kt` / `ImageTagDao` — populate tags from `tag.getVotableTags` responses
5. `service/CivitaiTrpcService.kt` — cache `tool.getAll` into `Tool` table on first load

**Validation:** Full end-to-end review — cache hit paths, partial-row handling, UI data flow.

---

## Migration Strategy

- Old `civitai_media_cache.db` (raw SQLite) is pure cache — acceptable to lose on upgrade
- Room DB file: `comfychair.db` (new, separate from old file)
- `CivitaiMediaCache` class is refactored to use Room DAO internally; external API stays the same to minimize diff
- No Room schema migration needed (v1 → fresh start)

---

## Room Version
- Room: `2.6.1` (stable)
- KSP: `2.0.21-1.0.28` (matches Kotlin `2.0.21` already in project)
