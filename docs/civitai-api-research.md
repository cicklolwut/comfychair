# Civitai tRPC API Research

**Date:** 2026-03-07  
**Model:** Crazy Girlfriend Mix [XL/PONY] (`modelId=500352`, `modelVersionId=556208`)  
**Purpose:** Document exact response shapes for ComfyChair Android app integration

---

## CDN URL Construction

Images use Cloudflare-proxied CDN. The `url` field in image objects is a UUID path segment:

```
https://image.civitai.com/xG1nkqKTMzGDvpLrqFT7WA/{url}/width={n}/{filename}
```

**Example:**
```
https://image.civitai.com/xG1nkqKTMzGDvpLrqFT7WA/f8e79643-c92a-4337-bd19-8e4935af2f54/width=832/image.jpg
```

Common widths: 128, 256, 450, 640, 1024, original. Use `original=true` for full res:
```
https://image.civitai.com/xG1nkqKTMzGDvpLrqFT7WA/{url}/original=true/{filename}
```

The `hash` field is a [BlurHash](https://blurha.sh/) string for placeholder rendering.

---

## Endpoint 1: `image.getImagesAsPostsInfinite`

**Purpose:** Gallery images grouped by post (the main model gallery tab)

**Request:**
```
GET https://civitai.com/api/trpc/image.getImagesAsPostsInfinite?input=<url-encoded-json>
```

**Input schema:**
```json
{
  "json": {
    "period": "AllTime",           // "AllTime" | "Year" | "Month" | "Week" | "Day"
    "periodMode": "published",     // "published" | "stats"
    "sort": "Newest",              // "Newest" | "Most Reactions" | "Most Comments" | "Most Collected"
    "withMeta": false,             // include generation metadata inline (false = don't)
    "requiringMeta": false,        // INVERSE of withMeta — shows images MISSING meta; most relevant on explicit models where Civitai requires metadata for uploads
    "modelVersionId": 556208,
    "modelId": 500352,
    "hidden": false,
    "limit": 5,
    "browsingLevel": 31,           // bitmask: 1=Safe 2=Soft 4=Mature 8=X 16=XXX; 31=all
    "cursor": null,                // pagination: pass nextCursor value
    "authed": true
  },
  "meta": {
    "values": { "cursor": ["undefined"] }  // required to signal null cursor
  }
}
```

### Top-Level Response Shape

```
result.data.json
├── nextCursor: string           // e.g. "10|1772897392941" — pass as cursor for next page
└── items: PostItem[]
```

### PostItem Object

```typescript
{
  postId: number;                 // unique post ID
  pinned: boolean;                // pinned by model author
  nsfwLevel: number;              // bitmask aggregate of images in post
  modelVersionId: number | null;  // set if post is directly associated with a version
  publishedAt: string;            // ISO date
  sortAt: string;                 // ISO date (used for sort order)
  createdAt: string;              // ISO date
  user: UserObject;
  images: ImageObject[];          // all images in this post
  review: ReviewObject | null;
}
```

### ImageObject (in getImagesAsPostsInfinite)

```typescript
{
  id: number;                     // image ID — use for getGenerationData
  url: string;                    // UUID segment for CDN URL construction
  nsfwLevel: number;              // 1=None 2=Soft 4=Mature 8=X 16=XXX
  aiNsfwLevel: number;            // AI-detected nsfw level
  combinedNsfwLevel: number;      // max(nsfwLevel, aiNsfwLevel)
  width: number;
  height: number;
  hash: string;                   // BlurHash
  hideMeta: boolean;              // user hid their meta
  hasMeta: boolean;               // generation metadata exists (use for getGenerationData)
  hasPositivePrompt: boolean;     // has a positive prompt
  onSite: boolean;                // generated on Civitai (vs uploaded)
  postedToId: number | null;      // modelVersionId if posted as showcase
  baseModel: string | null;       // "Pony", "SDXL", "SD 1.5", etc.
  modelVersionIds: number[];      // all LoRA/checkpoint version IDs detected in image
  modelVersionIdsManual: number[]; // manually tagged version IDs
  type: "image" | "video";
  index: number;                  // position within post
  postId: number;
  sortAt: string;                 // ISO date
  sortAtUnix: number;             // unix ms timestamp
  createdAt: string;
  existedAtUnix: number;          // unix ms timestamp
  publishedAt: string;
  userId: number;
  needsReview: null | string;
  minor: boolean;                 // flagged as minor
  poi: boolean;                   // person of interest
  acceptableMinor: boolean;
  blockedFor: null | string;
  remixOfId: number | null;       // ID of source image if remixed
  availability: "Public" | "Private" | "Unsearchable";
  stats: {
    likeCountAllTime: number;
    laughCountAllTime: number;
    heartCountAllTime: number;
    cryCountAllTime: number;
    commentCountAllTime: number;
    collectedCountAllTime: number;
    tippedAmountCountAllTime: number;
    dislikeCountAllTime: number;
    viewCountAllTime: number;
  };
  reactionCount: number;          // total reactions
  commentCount: number;
  collectedCount: number;
  tagIds: number[];               // tag IDs (resolve with separate tag lookup)
  toolIds: number[];
  techniqueIds: number[];
  user: UserObject;               // same as post user (deduplicated via referentialEqualities)
  reactions: any[];               // empty unless authed with session
  cosmetic: null;
  collections?: number[];         // collection IDs (appears on some images)
  // NOTE: meta is ALWAYS null here — must call getGenerationData separately
  meta: null;
  thumbnailUrl: null;             // always undefined/null
  mimeType: null;
  scannedAt: null;
  name: null;
  ingestion: "Scanned" | "Pending";
  postTitle: null;
  modelVersionId: null;           // different from post-level modelVersionId
}
```

### UserObject

```typescript
{
  id: number;
  username: string;
  image: string | null;           // UUID for profile picture CDN URL
  deletedAt: null | string;
  cosmetics: CosmeticEntry[];
  profilePicture: ProfilePicture | null;
}
```

### ReviewObject

```typescript
{
  rating: number;                 // 1-5
  details: string | null;        // HTML string
  recommended: boolean;
  id: number;
}
```

### Pagination

- `nextCursor` is a string like `"10|1772897392941"` (appears to be `postId|sortAtUnix`)
- Pass as `cursor` in next request
- No `nextCursor` = end of results
- The `meta.values` notes which fields were `undefined` (tRPC serialization hint)
- The `meta.referentialEqualities` notes where objects are shared (de-duplicated)

### Raw JSON Snippet (single post example)

```json
{
  "postId": 16956788,
  "pinned": true,
  "nsfwLevel": 4,
  "modelVersionId": null,
  "publishedAt": "2025-05-18T08:54:39.803Z",
  "sortAt": "2025-05-18T08:54:39.803Z",
  "createdAt": "2025-05-17T13:09:49.788Z",
  "user": {
    "id": 4702209,
    "username": "Wadufuk1",
    "image": null,
    "deletedAt": null,
    "cosmetics": [...]
  },
  "images": [
    {
      "id": 76353065,
      "reactionCount": 7345,
      "commentCount": 4,
      "collectedCount": 665,
      "index": 2,
      "postId": 16956788,
      "url": "f8e79643-c92a-4337-bd19-8e4935af2f54",
      "nsfwLevel": 4,
      "aiNsfwLevel": 0,
      "width": 832,
      "height": 1216,
      "hash": "UTF$Lt=_.mn2XoI@M|Mx.9xtxuM_XoemS6Vr",
      "hideMeta": false,
      "hasMeta": true,
      "hasPositivePrompt": true,
      "onSite": true,
      "postedToId": null,
      "combinedNsfwLevel": 4,
      "baseModel": "Pony",
      "modelVersionIds": [382152, 534756, 556208, 596040, 1142665, 1242203, 1500353, 1764228],
      "toolIds": [],
      "techniqueIds": [],
      "existedAtUnix": 1766002119495,
      "sortAtUnix": 1747558479803,
      "tagIds": [666, 2392, 3247, 3507, ...],
      "modelVersionIdsManual": [],
      "minor": false,
      "blockedFor": null,
      "remixOfId": 75923318,
      "hasPositivePrompt": true,
      "availability": "Public",
      "poi": false,
      "acceptableMinor": false,
      "stats": {
        "likeCountAllTime": 4293,
        "laughCountAllTime": 531,
        "heartCountAllTime": 2242,
        "cryCountAllTime": 277,
        "commentCountAllTime": 4,
        "collectedCountAllTime": 664,
        "tippedAmountCountAllTime": 110,
        "dislikeCountAllTime": 0,
        "viewCountAllTime": 0
      },
      "meta": null,
      "thumbnailUrl": null
    }
  ],
  "review": null
}
```

---

## Endpoint 2: `image.getGenerationData`

**Purpose:** Full generation metadata for a single image — **THE metadata endpoint**

**Request:**
```
GET https://civitai.com/api/trpc/image.getGenerationData?input=<url-encoded-json>
```

**Input schema:**
```json
{
  "json": {
    "id": 76353065,
    "authed": true
  }
}
```

**Only works on images where `hasMeta: true`. Returns null/error if meta is hidden.**

### Top-Level Response Shape

```
result.data.json
├── type: string                  // "image"
├── onSite: boolean               // generated on Civitai
├── process: string               // "txt2img" | "img2img" | "inpainting"
├── meta: GenerationMeta
├── resources: ResourceEntry[]    // FULL named resource list
├── tools: any[]
├── techniques: any[]
├── external: null
├── canRemix: boolean
└── remixOfId: number | null
```

### GenerationMeta Object

```typescript
{
  baseModel: string;             // "Pony", "SDXL", "SD 1.5", "Flux.1 D", etc.
  prompt: string;                // full positive prompt
  negativePrompt: string;        // full negative prompt
  cfgScale: number;              // guidance scale
  steps: number;
  sampler: string;               // "DDIM", "Euler a", "DPM++ 2M Karras", etc.
  seed: number;
  workflow: string;              // "txt2img", "img2img"
  civitaiResources: CivitaiResource[];  // lightweight resource list (IDs only)
  extra: {
    remixOfId?: number;
  };
  Size: string;                  // "832x1216" format
  nsfw: boolean;
  draft: boolean;
  width: number;
  height: number;
  quantity: number;              // number of images in batch
  "Created Date": string;        // ISO-like date string
  clipSkip: number;
  // May also contain:
  hashes?: Record<string, string>; // model hashes keyed by model name
  denoisingStrength?: number;    // for img2img
  initImage?: string;            // for img2img
}
```

### CivitaiResource (in meta.civitaiResources)

Lightweight — IDs only, no names:

```typescript
{
  type: "checkpoint" | "lora" | "lycoris" | "textualInversion";
  modelVersionId: number;
  weight?: number;               // lora strength (absent for checkpoints)
}
```

### ResourceEntry (in resources array)

**This is the key object** — fully named with model info:

```typescript
{
  imageId: number;
  modelVersionId: number;
  strength: number | null;       // null for checkpoints, float for LoRAs
  modelId: number;               // parent model ID
  modelName: string;             // human-readable model name
  modelType: "Checkpoint" | "LORA" | "LyCORIS" | "TextualInversion" | "Controlnet";
  versionId: number;             // same as modelVersionId
  versionName: string;           // version label like "v1.0", "Insta Baddie[PONY]"
  baseModel: string;             // "Pony", "SDXL", etc.
}
```

### Complete Raw JSON Response (image 76353065)

```json
{
  "type": "image",
  "onSite": true,
  "process": "txt2img",
  "meta": {
    "baseModel": "Pony",
    "prompt": "(score_9, score_8_up, score_7_up, score_6_up,\nsource_anime, masterpiece), 1girl, solo, curvy, large breasts, beautiful eyes, brown eyes, eyelashes, eyeshadow, large ass, thick thighs, wide hips, hourglass figure, plump, lusty, piercings, bikini, sideboob, underboob, choker, necklace, gorgeous face, thighs, tattoos, neck tattoos, shoulder tattoos, belly tattoos, seductive, skindentation, dynamic angle, closeup, outside, pool, swimming pool, smirk, smile, slutty, cocky, flirty, \nboth hands on breasts, squeezing breasts, hair up, standing, \nClose up,",
    "negativePrompt": "(3d, render, cgi, doll, painting, fake, 3d modeling:1.4), (worst quality, low quality:1.4), old, deformed, malformed, bad hands, bad fingers, bad eyes, bad teeth, long body, blurry, duplicated, cloned, duplicate body parts, disfigured, extra limbs, fused fingers, extra fingers, twisted, distorted, malformed hands, malformed fingers, mutated hands and fingers, conjoined, missing limbs, bad anatomy, bad proportions, logo, watermark, text, lowres, mutated, mutilated, blend, artifacts, gross, ugly, depth of field, asian, face defects), milf, elderly, selfie, jeans, (car), (car interior), (transport), selfie, nipples,",
    "cfgScale": 4,
    "steps": 40,
    "sampler": "DDIM",
    "seed": 2047301005,
    "workflow": "txt2img",
    "civitaiResources": [
      { "type": "checkpoint", "modelVersionId": 1764228 },
      { "type": "lora", "weight": 0.45, "modelVersionId": 382152 },
      { "type": "lora", "weight": 0.3,  "modelVersionId": 534756 },
      { "type": "lora", "weight": 1,    "modelVersionId": 556208 },
      { "type": "lora", "weight": 0.6,  "modelVersionId": 596040 },
      { "type": "lora", "weight": 1.65, "modelVersionId": 1242203 },
      { "type": "lora", "weight": 0.1,  "modelVersionId": 1500353 },
      { "type": "lora", "weight": 0.15, "modelVersionId": 1142665 }
    ],
    "extra": { "remixOfId": 75923318 },
    "Size": "832x1216",
    "nsfw": false,
    "draft": false,
    "width": 832,
    "height": 1216,
    "quantity": 2,
    "Created Date": "2025-05-13T0251:55.1620775Z",
    "clipSkip": 2
  },
  "resources": [
    {
      "imageId": 76353065,
      "modelVersionId": 382152,
      "strength": 0.45,
      "modelId": 341353,
      "modelName": "ExpressiveH (Hentai LoRa Style) エロアニメ",
      "modelType": "LORA",
      "versionId": 382152,
      "versionName": "ExpressiveH",
      "baseModel": "Pony"
    },
    {
      "imageId": 76353065,
      "modelVersionId": 534756,
      "strength": 0.3,
      "modelId": 480835,
      "modelName": "Pony Amateur ✨",
      "modelType": "LORA",
      "versionId": 534756,
      "versionName": "Rough (V1)",
      "baseModel": "Pony"
    },
    {
      "imageId": 76353065,
      "modelVersionId": 556208,
      "strength": 1,
      "modelId": 500352,
      "modelName": "Crazy Girlfriend Mix [XL/PONY]",
      "modelType": "LORA",
      "versionId": 556208,
      "versionName": "Insta Baddie[PONY]",
      "baseModel": "Pony"
    },
    {
      "imageId": 76353065,
      "modelVersionId": 596040,
      "strength": 0.6,
      "modelId": 536170,
      "modelName": "Real Beauty",
      "modelType": "LORA",
      "versionId": 596040,
      "versionName": "V1",
      "baseModel": "Pony"
    },
    {
      "imageId": 76353065,
      "modelVersionId": 1142665,
      "strength": 0.15,
      "modelId": 1019079,
      "modelName": "WaduStyle",
      "modelType": "LORA",
      "versionId": 1142665,
      "versionName": "V1",
      "baseModel": "Pony"
    },
    {
      "imageId": 76353065,
      "modelVersionId": 1242203,
      "strength": 1.65,
      "modelId": 1105685,
      "modelName": "Dramatic Lighting Slider",
      "modelType": "LORA",
      "versionId": 1242203,
      "versionName": "v1.0",
      "baseModel": "Pony"
    },
    {
      "imageId": 76353065,
      "modelVersionId": 1500353,
      "strength": 0.1,
      "modelId": 1323231,
      "modelName": "Thick curvy thighs - Kinklover",
      "modelType": "LORA",
      "versionId": 1500353,
      "versionName": "v1.1",
      "baseModel": "Pony"
    },
    {
      "imageId": 76353065,
      "modelVersionId": 1764228,
      "strength": null,
      "modelId": 1559047,
      "modelName": "LucentXL Pony by klaabu",
      "modelType": "Checkpoint",
      "versionId": 1764228,
      "versionName": "b 1.0",
      "baseModel": "Pony"
    }
  ],
  "tools": [],
  "techniques": [],
  "external": null,
  "canRemix": true,
  "remixOfId": 75923318
}
```

### Key Notes for ComfyChair

- **`resources[]` is the authoritative source** — has full names, modelId, modelType, strength
- **`meta.civitaiResources[]`** is a lightweight duplicate (IDs only, no names) — skip it, use `resources[]`
- **No hashes** in this response. To get file hashes for downloading, you need `model.getModelVersionById` endpoint (returns SHA256 etc.)
- The checkpoint has `strength: null` (not a float, needs null check)
- `canRemix` indicates if the user set this image to be remixable
- `onSite: true` means generated via Civitai's generator (not uploaded), so parameters are trustworthy

---

## Endpoint 3: `image.getInfinite`

**Purpose:** Flat list of images — used for cover/showcase images, prioritizing model author

**Request:**
```
GET https://civitai.com/api/trpc/image.getInfinite?input=<url-encoded-json>
```

**Input schema:**
```json
{
  "json": {
    "modelVersionId": 556208,
    "prioritizedUserIds": [2086307],  // show these users' images first
    "period": "AllTime",
    "sort": "Most Reactions",
    "limit": 5,
    "pending": true,                  // include pending review images
    "include": [],
    "withMeta": false,
    "excludedTagIds": [415792],
    "disablePoi": true,               // exclude real person images
    "disableMinor": true,             // exclude minor-flagged images
    "cursor": null,
    "authed": true
  },
  "meta": {
    "values": { "cursor": ["undefined"] }
  }
}
```

### Top-Level Response Shape

```
result.data.json
├── nextCursor: number            // numeric ID (different from endpoint 1's string cursor!)
└── items: FlatImageObject[]
```

### FlatImageObject (differences from endpoint 1's ImageObject)

This is a **flat image** (not nested inside a post). Key differences:

```typescript
{
  id: number;
  name: string;                   // filename like "crazy2.png" — NOT in endpoint 1
  url: string;                    // same UUID format
  nsfwLevel: number;
  width: number;
  height: number;
  hash: string;
  hideMeta: boolean;
  hasMeta: boolean;
  hasPositivePrompt: boolean;
  onSite: boolean;
  remixOfId: number | null;
  createdAt: string;
  sortAt: string;
  mimeType: string;               // "image/png", "image/jpeg" — present here, null in ep1
  type: "image" | "video";
  metadata: {
    hash: string;
    size: number;                 // file size in bytes — only in ep3
    width: number;
    height: number;
  };
  ingestion: string;
  blockedFor: null | string;
  scannedAt: string | null;       // scan timestamp — null in ep1
  needsReview: null | string;
  postId: number;
  postTitle: string | null;       // "Model Name - Version Showcase" — present here, null in ep1
  index: number;
  publishedAt: string;
  modelVersionId: number | null;  // set if image is tagged to this version
  availability: string;
  minor: boolean;
  poi: boolean;
  acceptableMinor: boolean;
  meta: null;                     // still null, call getGenerationData separately
  modelVersionIds: never[];       // always empty in this endpoint
  modelVersionIdsManual: never[]; // always empty
  baseModel: null;                // always null/undefined in this endpoint
  user: UserObject;
  stats: StatsObject;
  reactions: any[];
  tags: null;                     // null here (not tagIds array)
  tagIds: number[];
  cosmetic: null;
  thumbnailUrl: null;
  judgeScore: null | number;      // algorithmic quality score — only in ep3
}
```

### Pagination

- `nextCursor` is a **number** (image ID), unlike endpoint 1's string cursor
- Pass as `cursor` in next request

### Raw JSON Snippet (single image example)

```json
{
  "id": 17269828,
  "name": "crazy2.png",
  "url": "6013a50e-a3d0-45ab-a16f-b27d4fd00955",
  "nsfwLevel": 4,
  "width": 2396,
  "height": 3600,
  "hash": "UGJtSN.7wa$+_NIBt5t3IAt7XBtP-;x]s;i$",
  "hideMeta": false,
  "hasMeta": false,
  "hasPositivePrompt": false,
  "onSite": false,
  "remixOfId": null,
  "createdAt": "2024-06-26T09:20:18.910Z",
  "sortAt": "2024-06-26T09:20:28.806Z",
  "mimeType": "image/png",
  "type": "image",
  "metadata": {
    "hash": "UGJtSN.7wa$+_NIBt5t3IAt7XBtP-;x]s;i$",
    "size": 9069996,
    "width": 2396,
    "height": 3600
  },
  "ingestion": "Scanned",
  "blockedFor": null,
  "scannedAt": "2024-06-26T09:20:28.806Z",
  "needsReview": null,
  "postId": 3307330,
  "postTitle": "Crazy Girlfriend Mix [XL/PONY] - Insta Baddie[PONY] Showcase",
  "index": 0,
  "publishedAt": "2024-06-26T09:20:28.806Z",
  "modelVersionId": 556208,
  "availability": "Public",
  "minor": false,
  "poi": false,
  "acceptableMinor": false,
  "meta": null,
  "modelVersionIds": [],
  "modelVersionIdsManual": [],
  "baseModel": null,
  "user": {
    "id": 2086307,
    "username": "Ggrue",
    "image": "35195f3d-e8f8-443f-84d1-1f5a1c1dd5f6",
    "deletedAt": null,
    "cosmetics": [],
    "profilePicture": null
  },
  "stats": {
    "likeCountAllTime": 74,
    "laughCountAllTime": 8,
    "heartCountAllTime": 37,
    "cryCountAllTime": 0,
    "commentCountAllTime": 0,
    "collectedCountAllTime": 8,
    "tippedAmountCountAllTime": 0,
    "dislikeCountAllTime": 0,
    "viewCountAllTime": 0
  },
  "reactions": [],
  "tags": null,
  "tagIds": [208, 1465, 2193, ...],
  "cosmetic": null,
  "thumbnailUrl": null,
  "judgeScore": null
}
```

---

## Endpoint Comparison Summary

| Field | getImagesAsPostsInfinite | getInfinite |
|-------|--------------------------|-------------|
| Structure | Posts containing image arrays | Flat image list |
| `nextCursor` type | string (`"10\|timestamp"`) | number (image ID) |
| `name` (filename) | absent | present |
| `postTitle` | absent | present |
| `mimeType` | null | "image/png" etc. |
| `metadata.size` | absent | present |
| `scannedAt` | null | present |
| `judgeScore` | absent | present |
| `baseModel` | present | null/absent |
| `modelVersionIds` | populated | empty |
| `tags` | empty array | null |
| Grouped by author | via post | `prioritizedUserIds` param |

---

## NSFW Level Bitmask

The `nsfwLevel`, `aiNsfwLevel`, and `combinedNsfwLevel` fields are power-of-2 bitmasks:

| Value | Label | Description |
|-------|-------|-------------|
| 1 | None | Safe for all ages |
| 2 | Soft | Suggestive but not explicit |
| 4 | Mature | Mature themes |
| 8 | X | Explicit sexual content |
| 16 | XXX | Very explicit |
| 24 | Explicit | Combination |
| 28 | Blocked | (combination) |
| 31 | All | browsingLevel to show everything |

The `browsingLevel` parameter uses the same bitmask — set to 31 to show all content.

---

## Key Implementation Notes

### Fetching Generation Metadata

1. Use endpoint 1 or 3 to list images
2. Check `hasMeta: true` and `hideMeta: false` before attempting metadata fetch
3. Call `getGenerationData` with the `id` from the image object
4. Parse `resources[]` for named LoRAs/checkpoints with weights
5. Use `meta.civitaiResources[]` only if `resources[]` is empty (they overlap)

### Getting File Hashes for Downloads

The `getGenerationData` endpoint does **NOT** return file hashes (SHA256/AutoV2). To get hashes for local model matching, call:
```
GET https://civitai.com/api/trpc/model.getModelVersionById?input={"json":{"id":<modelVersionId>}}
```
That endpoint returns `files[].hashes` with SHA256, CRC32, AutoV2, etc.

### Alternatively: REST API for hashes

```
GET https://civitai.com/api/v1/model-versions/<modelVersionId>
```
Returns `files[].hashes.SHA256`, `files[].hashes.AutoV2`, `files[].downloadUrl`, etc. — no auth needed.

### Image CDN URL Pattern

```kotlin
fun buildImageUrl(uuid: String, width: Int? = null): String {
    val base = "https://image.civitai.com/xG1nkqKTMzGDvpLrqFT7WA/$uuid"
    return if (width != null) "$base/width=$width/image.jpg" else "$base/original=true/image.jpg"
}
```

### tRPC Response Wrapper

All responses are wrapped:
```json
{
  "result": {
    "data": {
      "json": { /* actual data */ },
      "meta": {
        "values": { /* tRPC type hints (undefined fields, Date fields) */ },
        "referentialEqualities": { /* shared object paths */ }
      }
    }
  }
}
```

The `meta.values` entries like `["Date"]` mean that field should be parsed as a Date object. Entries like `["undefined"]` mean the field is `undefined` (not null) in the original TypeScript.
