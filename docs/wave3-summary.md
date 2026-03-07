# Wave 3 Implementation Summary

## Overview

Wave 3 wired up gallery seeding, expanded the `CommunityImage` model with fields available at gallery time, switched `getImageMetadata` to the richer `getGenerationData` trpc endpoint, and added startup seeding for tools and techniques.

---

## Tasks Completed

### Task 1 — Expand `CommunityImage` model (`model/ModelBrowser.kt`)
Added 8 new fields to `CommunityImage` (all with defaults, fully backward-compatible):
- `modelVersionIds: List<Long>` — primary version ID list from gallery JSON
- `modelVersionIdsManual: List<Long>` — manually-tagged version IDs
- `toolIds: List<Int>` — tool join IDs (ComfyUI, Automatic1111, etc.)
- `techniqueIds: List<Int>` — technique join IDs (txt2img, img2img, etc.)
- `tagIds: List<Int>` — tag join IDs for image-level tagging
- `baseModel: String?` — base model string from gallery (no round-trip needed)
- `onSite: Boolean` — whether generated on Civitai
- `hideMeta: Boolean` — whether metadata is hidden by the owner

`hasMeta` was already present and left unchanged.

### Task 2 — Update `parseCommunityImage` (`service/CivitaiTrpcService.kt`)
Parses all 8 new fields from the `image.getImagesAsPostsInfinite` JSON using `optJSONArray` / `optBoolean` / `optString` with safe fallbacks. All fields map directly to camelCase JSON keys.

### Task 3 — Fix gallery seeding (`service/CivitaiTrpcService.kt`)
Replaced the fallback `img.meta?.resources` seeding block (from Wave 2 placeholder comment) with proper seeding:
- Seeds `ModelVersion` partial rows from `img.modelVersionIds + img.modelVersionIdsManual`
- Caches image-level join rows for tools, techniques, and tags via `cacheImageTools` / `cacheImageTechniques` / `cacheImageTags` in the repository

### Task 4 — Add filter params to `getModelImages` (`service/CivitaiTrpcService.kt`)
Extended signature with 7 new optional params (all nullable/defaulted, existing callers unaffected):
- `period: String = "AllTime"` (was hardcoded `"AllTime"`, now parameterized)
- `withMeta: Boolean?`
- `fromPlatform: Boolean?`
- `nonRemixesOnly: Boolean?`
- `remixesOnly: Boolean?`
- `hideManualResources: Boolean?`
- `hideAutoResources: Boolean?`

The hardcoded `"withMeta":false` in the JSON body was removed — callers now pass it explicitly if needed (or omit it to let the server default apply).

### Task 5 — Replace `getImageMetadata` with trpc (`service/CivitaiTrpcService.kt`)
Replaced the old REST `v1/images?postId=` approach with `image.getGenerationData` trpc. Key changes:
- `postId` param retained in signature (kept for ViewModel compatibility) but ignored
- Added `parseGetGenerationDataResponse()` — a separate parser for the trpc shape
- **Why separate parser:** The existing `parseGenerationMetadata()` handles the REST/inline-meta format where `resources` and `civitaiResources` live inside `meta`. In `getGenerationData`, they're at the ROOT level while `meta` is a nested sub-object. Using the existing parser would have caused it to look for resources inside the nested `meta` object (missing them).
- `getGenerationData` `resources[]` uses `versionId` (not `modelVersionId`) as the primary key — handled with fallback check
- `versionName` or `modelName` is preferred as the display name for resources (richer than the REST response)

### Task 6 — Seed hardcoded techniques (`ComfyChairApplication.kt`)
Added fire-and-forget `CoroutineScope(Dispatchers.IO)` coroutine seeding 8 techniques on every app start (upsert is idempotent via Room REPLACE strategy).

### Task 7 — Seed `tool.getAll` at startup (`service/CivitaiTrpcService.kt`, `ComfyChairApplication.kt`)
- Added `getTools()` to `CivitaiTrpcService` — hits `tool.getAll` trpc endpoint, parses `items[]` array
- `ComfyChairApplication.onCreate()` creates a local `CivitaiTrpcService` instance (with `ModelBrowserSettings`) and calls `getTools()` only if `repo.hasTools()` returns false
- No singleton needed for the service here — local instance is fine for one-time startup fetch

### Task 8 — `ImageMetadataSheet` baseModel from gallery data (`ui/screens/CommunityImagesScreen.kt`)
- Added `selectedImage: CommunityImage? = null` param to `ImageMetadataSheet`
- Updated call site to pass `currentImage`
- Base model now shows `selectedImage?.baseModel ?: meta?.baseModel` — gallery value takes priority, visible immediately without waiting for `getGenerationData` to complete
- Updated resource name fallback: `name ?: "Version #${modelVersionId}" ?: type ?: "Unknown"` — version ID shown when name is null (useful before Room enrichment fills in names)

---

## Issues & Adaptations

### `getGenerationData` key naming
The `resources[]` entries use `versionId` as the primary key (not `modelVersionId` as in civitaiResources and the REST format). Added dual-key detection (`versionId` preferred, `modelVersionId` as fallback) to handle both shapes gracefully.

### `withMeta` hardcoded default removed
Wave 2 had `"withMeta":false` hardcoded in the JSON body. This was replaced with the optional param. Callers that relied on the server defaulting to `withMeta=false` will still get that behavior (server default), but if the server default changes, callers can now opt-in explicitly.

### `ComfyChairApplication` — no service singleton needed
The task noted that `civitaiService` needs to be accessible from `Application`. Rather than adding a companion singleton to `CivitaiTrpcService` (which would complicate the constructor and lifecycle), a local instance is created in `Application.onCreate()` scoped to the startup coroutine. `ModelBrowserViewModel` continues to create its own instance as before — no breaking change.

### Technique seeding is always-on
Techniques are upserted every app launch (not gated on `repo.hasTechniques()`). Since there are only 8 rows and Room's upsert is O(1) per row, this is harmless. Could add a `hasTechniques()` guard later if startup latency becomes a concern.

---

## Files Modified

| File | Changes |
|------|---------|
| `model/ModelBrowser.kt` | Added 8 fields to `CommunityImage` |
| `service/CivitaiTrpcService.kt` | `parseCommunityImage` new fields, seeding block fix, `getModelImages` filter params, `getImageMetadata` trpc rewrite, `parseGetGenerationDataResponse`, `getTools()` |
| `ComfyChairApplication.kt` | Technique seeding, tool seeding on first launch |
| `ui/screens/CommunityImagesScreen.kt` | `ImageMetadataSheet` `selectedImage` param, baseModel fallback, resource name fallback |

## Files NOT Modified (per constraints)
- All DAO/entity files (Wave 1) — untouched
- `CivitaiCacheRepository.kt` (Wave 2) — untouched
