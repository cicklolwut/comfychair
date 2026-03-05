# Task: Unified trpc Service for Model Browser

## Overview

Replace both `CivitaiMeiliService` (search) and REST `CivitaiService.getModelDetails()` with a single trpc-based service. All Civitai communication goes through trpc endpoints. No API key required (works unauthenticated), but pass key if configured for potential rate limit benefits.

## Endpoints

### 1. `model.getAll` — Search & Browse

**URL:** `GET https://civitai.com/api/trpc/model.getAll?input={urlEncoded}`

**Auth:** Optional. Works without any auth headers. If API key available, pass:
- `Authorization: Bearer {key}`
- `Cookie: __Secure-civitai-token={key}`

**Input:**
```json
{
  "json": {
    "query": "",                     // optional, text search (empty = browse mode)
    "limit": 20,                     // page size
    "sort": "Most Downloaded",       // see sort options below
    "period": "AllTime",             // see period options below
    "periodMode": "stats",           // "stats" for metric sorts, "published" for Newest
    "types": ["LORA"],               // optional array, omit for all
    "baseModels": ["Illustrious"],   // optional array, omit for all
    "browsingLevel": 7,              // bitmask sum: 1+2+4=7 (PG+PG13+R)
    "cursor": null,                  // string cursor, null for first page
    "authed": true                   // set true if API key present, false otherwise
  },
  "meta": {
    "values": {
      "cursor": ["undefined"]        // ONLY when cursor is null
    }
  }
}
```

**Sort options:** `"Most Downloaded"`, `"Highest Rated"`, `"Most Liked"`, `"Most Discussed"`, `"Most Collected"`, `"Most Buzz"`, `"Newest"`

**Period options:** `"AllTime"`, `"Year"`, `"Month"`, `"Week"`, `"Day"`

**periodMode logic:**
- Sort is `"Newest"` → `periodMode: "published"` (filter by publish date)
- All other sorts → `periodMode: "stats"` (sort by period-scoped metrics)

**Response fields we use from `items[]`:**

| Field | Type | Notes |
|-------|------|-------|
| `id` | Int | Model ID |
| `name` | String | Model name |
| `type` | String | "Checkpoint", "LORA", "LoCon", etc. |
| `nsfwLevel` | Int | Bitmask |
| `user.id` | Int | Creator user ID |
| `user.username` | String | Creator username |
| `version.id` | Int | Latest version ID |
| `version.name` | String | Version name |
| `version.baseModel` | String | "SD 1.5", "Illustrious", "Pony", etc. |
| `version.trainedWords` | String[] | Trigger words |
| `tags` | Int[] | Tag IDs only — resolve via tag cache |
| `rank.downloadCount` | Int | |
| `rank.thumbsUpCount` | Int | |
| `rank.commentCount` | Int | |
| `rank.collectedCount` | Int | |
| `images[]` | Array | Cover images for this model |
| `images[].url` | String | CDN UUID (not full URL) |
| `images[].name` | String? | Can be null |
| `images[].type` | String | "image" or "video" |
| `images[].nsfwLevel` | Int | Per-image NSFW level |
| `images[].width` | Int | |
| `images[].height` | Int | |
| `images[].hash` | String | BlurHash for placeholder |

**Pagination:** `nextCursor` is a pipe-delimited string like `"1188720|76618|827184"`. Pass as `"cursor": "1188720|76618|827184"` (quoted string in JSON). When null, no more pages.

**Cover image selection:** `images[]` contains multiple images. Pick the first one whose `nsfwLevel` is within the user's `browsingLevel`. Build CDN URL: `{CDN_BASE}/{uuid}/anim=false,width=450,optimized=true/{name || id}.jpg`

---

### 2. `model.getById` — Model Detail (Bottom Sheet)

**URL:** `GET https://civitai.com/api/trpc/model.getById?input={urlEncoded}`

**Input:**
```json
{
  "json": {
    "id": 443821
  }
}
```

**Response fields we use:**

| Field | Type | Notes |
|-------|------|-------|
| `id` | Int | |
| `name` | String | |
| `type` | String | |
| `description` | String | HTML content |
| `user.id` | Int | |
| `user.username` | String | |
| `tagsOnModels[].tag.id` | Int | Tag ID |
| `tagsOnModels[].tag.name` | String | Tag name — **names included here!** |
| `modelVersions[]` | Array | ALL versions |
| `modelVersions[].id` | Int | Version ID |
| `modelVersions[].name` | String | Version name |
| `modelVersions[].baseModel` | String | |
| `modelVersions[].trainedWords` | String[] | |
| `modelVersions[].files[]` | Array | Files for this version |
| `modelVersions[].files[].id` | Int | |
| `modelVersions[].files[].name` | String | e.g. "model.safetensors" |
| `modelVersions[].files[].type` | String | "Model", "Config", etc. |
| `modelVersions[].files[].sizeKB` | Double | Size in KB |
| `modelVersions[].files[].metadata.fp` | String? | "fp16", "fp32" |
| `modelVersions[].files[].metadata.size` | String? | "full", "pruned" |
| `modelVersions[].files[].metadata.format` | String? | "SafeTensor", "PickleTensor" |
| `modelVersions[].files[].hashes[]` | Array | Hash objects |
| `modelVersions[].files[].hashes[].type` | String | "SHA256", "AutoV2", etc. |
| `modelVersions[].files[].hashes[].hash` | String | Hash value |

**Note:** `modelVersions[].images` is empty/null in this endpoint. Cover images come from `model.getAll`, community images from `image.getImagesAsPostsInfinite`.

---

### 3. `image.getImagesAsPostsInfinite` — Community Images (Already Implemented)

**URL:** `GET https://civitai.com/api/trpc/image.getImagesAsPostsInfinite?input={urlEncoded}`

**Input:**
```json
{
  "json": {
    "modelVersionId": 2727742,
    "modelId": 443821,
    "period": "AllTime",
    "periodMode": "published",
    "sort": "Most Reactions",
    "withMeta": true,
    "requiringMeta": false,
    "hidden": false,
    "limit": 20,
    "browsingLevel": 31,
    "cursor": null,
    "authed": true
  },
  "meta": {
    "values": {
      "cursor": ["undefined"]
    }
  }
}
```

**Sort options:** `"Most Reactions"`, `"Most Comments"`, `"Newest"`

**Response:** Nested `items[].images[]` — flatten to image list. Already implemented, no changes needed.

---

### 4. `tag.getAll` — Tag Name Resolution

**URL:** `GET https://civitai.com/api/trpc/tag.getAll?input={urlEncoded}`

**Input:**
```json
{
  "json": {
    "limit": 200,
    "entityType": ["Model"]
  }
}
```

**Response:** `items[]: { id: Int, name: String, isCategory: Boolean }`

**Caching strategy:**
- Store in SharedPreferences as JSON string: `{"172":"photorealistic","266":"portrait",...}`
- On first search, bulk-fetch top 200 model tags
- When search results contain unknown tag IDs, batch-fetch and update cache
- Cache is append-only (tag ID→name mappings are immutable)
- Read cache into memory on service init (avoid repeated SharedPrefs reads)

**Max per request:** 200 items. Paginate with `offset` if needed (unlikely — top 200 covers most models).

---

## Data Class Mapping

### `model.getAll` item → `ModelSearchResult`

```kotlin
@Immutable
data class ModelSearchResult(
    val id: String,              // from item.id (Int→String)
    val name: String,            // from item.name
    val description: String?,    // null from getAll (populated from getById)
    val thumbnailUrl: String?,   // built from first suitable image CDN URL
    val animatedThumbnailUrl: String?, // same but without anim=false
    val downloadCount: Long?,    // from item.rank.downloadCount
    val favoriteCount: Long?,    // from item.rank.thumbsUpCount
    val tags: List<String>,      // resolved from item.tags via cache (empty if unresolved)
    val creator: String?,        // from item.user.username
    val creatorId: Int?,         // from item.user.id
    val versions: List<ModelVersion>, // single item from item.version (latest only)
    val provider: ModelProvider,  // ModelProvider.CIVITAI
    val civitaiType: String?,    // from item.type
    val baseModel: String?       // from item.version.baseModel
)
```

### `model.getById` → populates detail bottom sheet

When user taps a model card, call `model.getById` with the model's integer ID. This gives:
- Full description (HTML)
- ALL versions (not just latest) — populate version selector
- Files per version — populate file selector / download
- Tags with names — can update the tag cache as a side effect

### Cover image → thumbnail URL

From `model.getAll` `images[]` array:
1. Filter images where `nsfwLevel` fits within user's `browsingLevel` bitmask: `(imageNsfwLevel AND browsingLevel) == imageNsfwLevel`
2. Pick the first matching image
3. Build URLs:
   - Static: `{CDN_BASE}/{uuid}/anim=false,width=450,optimized=true/{name || "{id}.jpg"}`
   - Animated: `{CDN_BASE}/{uuid}/width=450,optimized=true/{name || "{id}.jpg"}`
   - For videos: add `transcode=true,original=false` to static params

**CDN_BASE:** `https://image.civitai.com/xG1nkqKTMzGDvpLrqFT7WA`

---

## Filter Chips (Static Lists)

Without Meili facets, use hardcoded lists (already the fallback in SearchFilters composable):

**Types:** LORA, Checkpoint, LoCon, TextualInversion, Workflows, Wildcards, DoRA, Poses, Hypernetwork, VAE, Controlnet, AestheticGradient, Detection, MotionModule, Upscaler, Other

**Base Models:** Illustrious, SD 1.5, Pony, Flux.1 D, SDXL 1.0, NoobAI, Other, ZImageTurbo, Qwen, Hunyuan Video, Wan Video 2.2 I2V-A14B, Wan Video 2.2 T2V-A14B, Wan Video 14B I2v, Flux.1 S, SD 2.1 768, Chroma, ZImageBase, Flux.1 Kontext

**Sort:** Most Downloaded, Highest Rated, Most Liked, Most Discussed, Most Collected, Most Buzz, Newest

**Period:** AllTime, Year, Month, Week, Day

**NSFW Levels:** PG (1), PG-13 (2), R (4), X (8), XXX (16)

---

## Service Architecture

### Before (3 services)
```
CivitaiMeiliService  → search/browse (Meili direct)
CivitaiService       → model details (REST), community images (trpc)
HuggingFaceService   → HF search (separate provider)
```

### After (2 services)
```
CivitaiTrpcService   → ALL Civitai operations (search, details, images, tags)
HuggingFaceService   → HF search (unchanged)
```

### `CivitaiTrpcService` methods:

```kotlin
class CivitaiTrpcService(
    private val settings: ModelBrowserSettings
) {
    private val client = HttpModule.client

    /**
     * Search/browse models. Primary entry point for the grid.
     */
    suspend fun searchModels(
        query: String = "",
        types: List<String>? = null,
        baseModels: List<String>? = null,
        sort: String = "Most Downloaded",
        period: String = "AllTime",
        browsingLevel: Int = 7,
        limit: Int = 20,
        cursor: String? = null
    ): TrpcSearchResult

    /**
     * Get full model details (all versions, files, tags with names).
     */
    suspend fun getModelDetails(modelId: Int): TrpcModelDetail

    /**
     * Get community images for a model version. Already implemented,
     * move from CivitaiService.
     */
    suspend fun getModelImages(
        modelVersionId: String,
        modelId: String? = null,
        limit: Int = 20,
        cursor: String? = null,
        browsingLevel: Int? = null,
        sort: String = "Most Reactions"
    ): Pair<List<CommunityImage>, String?>

    /**
     * Resolve tag IDs to names. Checks cache first, fetches missing.
     */
    suspend fun resolveTagNames(ids: List<Int>): Map<Int, String>

    // --- Internal helpers ---

    /**
     * Build trpc GET request with URL-encoded input.
     */
    private fun buildTrpcRequest(endpoint: String, input: String): Request

    /**
     * Build CDN thumbnail URL from image UUID.
     */
    private fun buildCdnUrl(uuid: String, name: String?, isVideo: Boolean, animated: Boolean): String

    /**
     * Select best cover image from images array based on browsingLevel.
     */
    private fun selectCoverImage(images: JSONArray, browsingLevel: Int): JSONObject?
}
```

### Result types:

```kotlin
data class TrpcSearchResult(
    val models: List<ModelSearchResult>,
    val nextCursor: String?,        // null when no more pages
    val totalHits: Int = 0          // not available from trpc, set to 0
)

data class TrpcModelDetail(
    val id: Int,
    val name: String,
    val type: String,
    val description: String?,       // HTML
    val creator: String,
    val creatorId: Int,
    val tags: List<String>,         // names from tagsOnModels
    val versions: List<ModelVersion>,
    val files: Map<String, List<ModelFile>> // versionId → files
)
```

---

## ViewModel Changes

### `ModelBrowserViewModel.kt`

1. Replace `civitaiMeiliService` with `civitaiTrpcService`
2. Replace `civitaiService` with `civitaiTrpcService` (for community images + details)
3. `searchModels()`: call `civitaiTrpcService.searchModels()` instead of `civitaiMeiliService.searchModels()`
4. `loadMoreResults()`: same, use trpc cursor
5. `selectModel()` / model detail loading: call `civitaiTrpcService.getModelDetails()` instead of REST `getModelDetails()`
6. Remove `filterPeriod` workaround (lastVersionAtUnix filter) — period is handled properly by trpc
7. Pass `period` and compute `periodMode` from sort
8. After search results arrive, fire background tag resolution for any uncached IDs
9. Remove `availableTypes` and `availableBaseModels` from UI state (no more facets)
10. Remove `isCivitaiConfigured` gate — trpc works without API key

### Auto-load on init:

Add an `init` block to the ViewModel that triggers an initial browse (empty query) immediately:

```kotlin
init {
    // trpc works without API key — load browse results on screen open
    if (_uiState.value.selectedProvider == ModelProvider.CIVITAI) {
        triggerDebouncedSearch()
    }
}
```

This means the user sees "Most Downloaded" models immediately when they open the browser, before typing anything. The `providerConfigured` gate is no longer needed for Civitai (only HuggingFace still requires a key).

### State changes:

Remove from `ModelBrowserUiState`:
- `availableTypes: List<String>` (use hardcoded list)
- `availableBaseModels: List<String>` (use hardcoded list)

Keep:
- `filterModelType: String?`
- `filterBaseModel: String?`
- `filterSort: String`
- `filterPeriod: String`
- `nsfwLevels: Set<Int>`

---

## Files to Create/Modify/Delete

### Create:
- `service/CivitaiTrpcService.kt` — new unified service

### Modify:
- `viewmodel/ModelBrowserViewModel.kt` — swap service calls
- `storage/ModelBrowserSettings.kt` — add tag cache
- `ui/screens/ModelBrowserScreen.kt` — remove facet-dependent code, use static lists only

### Delete (or deprecate):
- `service/CivitaiMeiliService.kt` — replaced entirely
- `service/CivitaiService.kt` — all functionality moved to CivitaiTrpcService

### Unchanged:
- `service/HuggingFaceService.kt`
- `service/ComfyUIManagerService.kt`
- `service/HttpModule.kt`
- `ui/screens/CommunityImagesScreen.kt`
- `model/ModelBrowser.kt` (data classes stay the same)

---

## Gotchas

1. **cursor format:** Always pass as quoted string in JSON: `"cursor": "1188720|76618|827184"`. When null, include `meta.values.cursor: ["undefined"]`. When non-null, OMIT the meta block entirely.

2. **periodMode:** Must match the sort. Metric sorts → "stats", Newest → "published". Getting this wrong silently returns wrong data.

3. **types/baseModels are arrays** in trpc, not single strings. `["LORA"]` not `"LORA"`. Omit the key entirely (don't send empty array) when "All" is selected.

4. **images[].name can be null** — fallback to `"{id}.jpg"` for CDN URL construction.

5. **model.getById returns tags with names** (`tagsOnModels[].tag.name`) but model.getAll returns tag IDs only (`tags: [172, 266]`). Use getAll tags to populate cache, getById tags for detail view.

6. **browsingLevel for cover image selection:** The `browsingLevel` param filters which MODELS are returned, but each model can have images at different NSFW levels. Must filter `images[]` client-side by comparing each image's `nsfwLevel` against the user's levels.

7. **No totalHits from trpc** — unlike Meili, trpc doesn't return total count. Use `nextCursor != null` to determine if there are more pages. The `hasMoreResults` state field should be set based on cursor, not count.

8. **Empty query is fine** — `model.getAll` with `query: ""` or omitted query returns browse results (sorted by sort+period).

9. **Download URLs:** Not in trpc response. Keep using the existing pattern: `https://civitai.com/api/download/models/{versionId}?type=Model&format=SafeTensor`

10. **model.getAll `version` is singular** (latest only). model.getById `modelVersions` is the full array. The version selector in the bottom sheet needs getById data.
