# Civitai API HAR Analysis

**Source:** HAR capture from Civitai model page (model 500352 "Crazy Girlfriend Mix [XL/PONY]"), version 556208
**Date captured:** 2026-03-07
**Total entries:** 89 | **Civitai API calls:** 68 | **200-OK responses:** 22 unique endpoints

All tRPC calls go to: `https://civitai.com/api/trpc/<endpoint>`
Input is passed as a URL-encoded JSON `input` query param.

> **tRPC response envelope:** All responses are wrapped:
> ```json
> { "result": { "data": { "json": <actual data> } } }
> ```
> The `meta.values` sibling to `json` is tRPC's type annotation (e.g., `"undefined"` means the field was `undefined` in TS, not absent).

---

## Image CDN URL Construction

Image `url` fields are UUIDs (not full URLs). Construct CDN URLs like:
```
https://image.civitai.com/xG1nkqKTMzGDvpLrqFT7WA/<uuid>/width=<w>/original=true
```
Or for thumbnails:
```
https://image.civitai.com/xG1nkqKTMzGDvpLrqFT7WA/<uuid>/width=450/
```

Profile pictures can also be absolute URLs (e.g., Google/Discord CDN links).

---

## 1. `image.getImagesAsPostsInfinite`

**Primary gallery browsing endpoint** — returns posts (groups of images) for a model version's gallery.

### Request

```
GET /api/trpc/image.getImagesAsPostsInfinite?input=<json>
```

**Input parameters (all observed variants):**

```json
{
  "json": {
    "period": "AllTime",
    "periodMode": "published",
    "sort": "Newest",
    "withMeta": false,
    "requiringMeta": false,
    "modelVersionId": 556208,
    "modelId": 500352,
    "hidden": false,
    "limit": 25,
    "browsingLevel": 31,
    "cursor": null,
    "authed": true,

    // Optional filters (observed in later calls):
    "tools": [86],           // filter by tool ID (86 = ComfyUI)
    "techniques": [2],        // filter by technique ID (2 = img2img)
    "techniques": [2, 8]      // multiple techniques
  },
  "meta": {
    "values": { "cursor": ["undefined"] }  // tRPC encoding: cursor starts as undefined
  }
}
```

**Pagination:** Use `nextCursor` from response as `cursor` in next request.
Cursor format: `"<count>|<unixTimestampMs>"` e.g. `"100|1772847939576"`

### Response

```json
{
  "result": {
    "data": {
      "json": {
        "nextCursor": "100|1772847939576",
        "items": [ /* PostItem[] */ ]
      }
    }
  }
}
```

### PostItem Schema (complete)

```json
{
  "postId": 25232009,
  "pinned": true,
  "nsfwLevel": 2,
  "modelVersionId": null,
  "publishedAt": "2025-12-18T19:35:08.960Z",
  "sortAt": "2025-12-18T19:35:08.960Z",
  "createdAt": "2025-12-18T19:35:08.960Z",

  "user": {
    "id": 9077594,
    "username": "pn132",
    "image": "https://cdn.discordapp.com/...",   // absolute URL or UUID
    "deletedAt": null,
    "cosmetics": [],
    "profilePicture": {
      "id": 99696804,
      "name": "filename.jpeg",
      "url": "c70492ab-bbbc-4e00-9803-75e787d0cd87",   // UUID for CDN
      "nsfwLevel": 2,
      "hash": "UUCZhRD%t7j]~qITogt7-=IUofxtxuRjaet6",  // BlurHash
      "userId": 9077594,
      "ingestion": "Scanned",
      "type": "image",
      "width": 832,
      "height": 1216,
      "metadata": {
        "hash": "...",
        "size": 129620,
        "width": 832,
        "height": 1216,
        "userId": 9077594,
        "profilePicture": true
      }
    }
  },

  "images": [ /* ImageItem[] — see below */ ],

  "review": {
    "rating": 5,
    "details": null,
    "recommended": true,
    "id": 53507305
  }
}
```

### ImageItem Schema (inside `images[]`) — COMPLETE

```json
{
  // Identity
  "id": 114314534,
  "postId": 25232009,
  "index": 1,                         // position within the post

  // Display
  "url": "365e246e-6c37-4b0a-982d-b8594a7609ac",   // ⚠️ UUID, not full URL
  "thumbnailUrl": null,                              // usually null
  "name": null,                                      // original filename or null
  "type": "image",                                   // "image" | "video"
  "mimeType": null,                                  // "image/png" | "image/jpeg" | null
  "width": 832,
  "height": 1216,
  "hash": "U4Dbyf0,0M0119~V0y?F00IU~B9x0057=G-p",  // BlurHash for placeholder

  // Metadata
  "metadata": {
    "width": 832,
    "height": 1216
    // Note: in image.getInfinite this also has: hash, size
  },

  // Content flags
  "nsfwLevel": 2,          // bitmask: 1=None, 2=Soft, 4=Mature, 8=Explicit, 16=Blocked
  "aiNsfwLevel": 0,
  "combinedNsfwLevel": 2,
  "hideMeta": true,        // ⚠️ if true, meta is hidden from public display
  "hasMeta": false,        // ⚠️ true if image has prompt/generation metadata stored
  "hasPositivePrompt": false,
  "onSite": false,         // true = generated on Civitai's own generator
  "minor": false,
  "poi": false,            // person of interest
  "acceptableMinor": false,
  "availability": "Public",
  "blockedFor": null,
  "needsReview": null,
  "ingestion": "Scanned",  // scan status
  "scannedAt": null,
  "remixOfId": null,

  // Generation metadata (IDs only — resolve via tool.getAll / technique.getAll)
  "baseModel": "Pony",                // ⚠️ e.g. "Pony", "SDXL 1.0", "Flux.1 D", null
  "modelVersionId": null,             // which model version this was showcasing
  "modelVersionIds": [],              // auto-detected model version IDs
  "modelVersionIdsManual": [          // ⚠️ manually tagged version IDs
    254162, 262705, 466120, 556208, 717403, 1394486, 1959985, 2334591
  ],
  "toolIds": [78, 86],               // ⚠️ tool IDs (78=Civitai, 86=ComfyUI)
  "techniqueIds": [1, 2],            // ⚠️ technique IDs (1=txt2img, 2=img2img)
  "tagIds": [55, 3084, 3641, ...],   // ⚠️ tag ID list (resolve via tag.getAll etc.)

  // Stats
  "reactionCount": 18,
  "commentCount": 0,
  "collectedCount": 5,
  "stats": {
    "likeCountAllTime": 13,
    "laughCountAllTime": 0,
    "heartCountAllTime": 5,
    "cryCountAllTime": 0,
    "commentCountAllTime": 0,
    "collectedCountAllTime": 5,
    "tippedAmountCountAllTime": 0,
    "dislikeCountAllTime": 0,
    "viewCountAllTime": 0
  },

  // Timestamps
  "createdAt": "2025-12-18T19:35:08.960Z",
  "publishedAt": "2025-12-18T19:35:08.960Z",
  "sortAt": "2025-12-18T19:35:08.960Z",
  "sortAtUnix": 1766086508960,
  "existedAtUnix": 1768428062343,    // when Civitai became aware of this image

  // Post context
  "postTitle": null,                  // sometimes populated
  "postedToId": null,

  // User (duplicated from post-level)
  "userId": 9077594,
  "user": { /* same User schema as post.user */ },

  // Display extras
  "reactions": [],
  "cosmetic": null,
  "tags": [],     // ⚠️ always empty in this endpoint (use tagIds instead)

  // Generation metadata (⚠️ always null here — use image.getGenerationData for full meta)
  "meta": null
}
```

### Key Notes
- **`meta` is always `null`** in this endpoint. Use `image.getGenerationData` to get prompts/settings.
- **`hasMeta`** indicates whether generation metadata exists at all for this image.
- **`hideMeta`** means the creator set the meta to hidden — `getGenerationData` may still return resources but `meta` will be `null`.
- **`baseModel`** is the most useful quick-lookup field for filtering (e.g. "Pony", "SDXL 1.0", "Flux.1 D").
- **`modelVersionIdsManual`** is the creator-tagged list; `modelVersionIds` is auto-detected.
- **`toolIds`/`techniqueIds`** are integer IDs — resolve via `tool.getAll` and `technique.getAll`.
- Tags come as `tagIds` (integers), not names. The full tag list is not inlined here.

---

## 2. `image.getGenerationData`

**⚠️ Critical metadata endpoint** — fetches complete generation parameters, resources, tools, and techniques for a single image.

### Request

```
GET /api/trpc/image.getGenerationData?input={"json":{"id":122859690,"authed":true}}
```

### Response (Entry 1 — image WITH full meta)

```json
{
  "result": {
    "data": {
      "json": {
        "type": "image",
        "onSite": false,
        "process": "txt2img",

        "meta": {
          "prompt": "ultra photo-realistic, highly detailed, close up.\n1woman, 24, looking at viewer.\nblue eyes...",
          "negativePrompt": "source_pony, source_anime, worst quality, low quality...",
          "cfgScale": 4.5,
          "steps": 30,
          "sampler": "DPM++ SDE Karras",
          "seed": 398648148753014,
          "civitaiResources": [
            {
              "type": "checkpoint",
              "modelVersionId": 1971591
            },
            {
              "type": "lora",
              "weight": 0.7,
              "modelVersionId": 556208
            },
            {
              "type": "lora",
              "weight": 1,
              "modelVersionId": 534952
            }
          ],
          "Size": "896x1152",
          "Model": "lucentxlPonyByKlaabu_b20",
          "Version": "ComfyUI"
        },

        "resources": [
          {
            "imageId": 122859690,
            "modelVersionId": 534952,
            "strength": 1,
            "modelId": 481016,
            "modelName": "Breast Size Slider - Pony / Illustrious",
            "modelType": "LORA",
            "versionId": 534952,
            "versionName": "Pony",
            "baseModel": "Pony"
          },
          {
            "imageId": 122859690,
            "modelVersionId": 556208,
            "strength": 0.7,
            "modelId": 500352,
            "modelName": "Crazy Girlfriend Mix [XL/PONY]",
            "modelType": "LORA",
            "versionId": 556208,
            "versionName": "Insta Baddie[PONY]",
            "baseModel": "Pony"
          },
          {
            "imageId": 122859690,
            "modelVersionId": 1971591,
            "strength": null,
            "modelId": 1559047,
            "modelName": "LucentXL Pony by klaabu",
            "modelType": "Checkpoint",
            "versionId": 1971591,
            "versionName": "b 2.0",
            "baseModel": "Pony"
          }
        ],

        "tools": [
          {
            "id": 86,
            "name": "ComfyUI",
            "icon": null,
            "domain": "https://github.com/comfyanonymous/ComfyUI",
            "priority": 4,
            "notes": null
          }
        ],

        "techniques": [
          {
            "id": 1,
            "name": "txt2img",
            "notes": null
          },
          {
            "id": 2,
            "name": "img2img",
            "notes": null
          }
        ],

        "external": null,
        "canRemix": true,
        "remixOfId": null
      }
    }
  }
}
```

### Response (Entry 1 — image WITHOUT meta, `hideMeta: true`)

```json
{
  "result": {
    "data": {
      "json": {
        "type": "image",
        "onSite": false,
        "process": null,
        "meta": null,
        "resources": [
          {
            "imageId": 122723126,
            "modelVersionId": 254162,
            "strength": null,
            "modelId": 225334,
            "modelName": "Eye catching - sliders / ntcai.xyz",
            "modelType": "LORA",
            "versionId": 254162,
            "versionName": "v1.0",
            "baseModel": "SDXL 1.0"
          }
          // ... more resources without strength values
        ],
        "tools": [ /* same structure */ ],
        "techniques": [ /* same structure */ ],
        "external": null,
        "canRemix": false,
        "remixOfId": null
      }
    }
  }
}
```

### `meta` Object Fields (full set observed)

| Field | Type | Description |
|-------|------|-------------|
| `prompt` | string | Positive prompt text |
| `negativePrompt` | string | Negative prompt text |
| `cfgScale` | number | CFG/guidance scale |
| `steps` | number | Sampling steps |
| `sampler` | string | Sampler name e.g. "DPM++ SDE Karras" |
| `seed` | number | Generation seed |
| `Size` | string | Resolution e.g. "896x1152" |
| `Model` | string | Checkpoint filename (without extension) |
| `Version` | string | Tool used e.g. "ComfyUI", "A1111" |
| `civitaiResources` | array | Resources with type/weight/modelVersionId |

### `resources[]` Item Fields

| Field | Type | Description |
|-------|------|-------------|
| `imageId` | number | Parent image ID |
| `modelVersionId` | number | Version ID (use for API lookups) |
| `modelId` | number | Model ID |
| `modelName` | string | Human-readable model name |
| `modelType` | string | "LORA", "LoCon", "Checkpoint", etc. |
| `versionId` | number | Same as modelVersionId |
| `versionName` | string | Version name |
| `baseModel` | string | "Pony", "SDXL 1.0", "Flux.1 D", etc. |
| `strength` | number\|null | LoRA weight (null when meta is hidden) |

### Key Notes
- `process` = `null` when `hideMeta: true`, otherwise e.g. `"txt2img"`
- `meta` = `null` when hidden. Resources may still be populated (extracted from EXIF / Civitai's own data).
- `strength` = `null` on all resources when meta is hidden (can't determine weights without prompt).
- `canRemix` = `false` when meta hidden, `true` when meta available.
- `tools` and `techniques` objects here are **full objects** (name, domain), unlike the ID-only arrays in `getImagesAsPostsInfinite`.

---

## 3. `image.getInfinite`

**Flat image list** for a specific model version — no post grouping. Used on model version pages.

### Request

```
GET /api/trpc/image.getInfinite?input={"json":{
  "modelVersionId": 556208,
  "prioritizedUserIds": [2086307],
  "period": "AllTime",
  "sort": "MostReactions",
  "limit": 20,
  "browsingLevel": 31,
  "authed": true
}}
```

### ImageItem Schema (flat, slightly different from getImagesAsPostsInfinite)

```json
{
  "id": 17269828,
  "name": "crazy2.png",              // ⚠️ filename present here
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
  "mimeType": "image/png",           // ⚠️ mimeType present here
  "type": "image",
  "metadata": {
    "hash": "...",
    "size": 9069996,                 // ⚠️ file size in bytes present here
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
  "modelVersionId": 556208,          // ⚠️ populated here (the version being showcased)
  "availability": "Public",
  "minor": false,
  "poi": false,
  "acceptableMinor": false,
  "meta": null,                      // still null — use getGenerationData
  "modelVersionIds": [],
  "modelVersionIdsManual": [],
  "baseModel": null,                 // ⚠️ can be null here even when set in getImagesAsPostsInfinite
  "user": { /* User schema */ },
  "stats": { /* Stats schema */ },
  "reactions": [],
  "tags": null,                      // null (not empty array)
  "tagIds": [208, 1465, ...]
}
```

### Key Differences vs `getImagesAsPostsInfinite`

| Field | `getImagesAsPostsInfinite` | `getInfinite` |
|-------|---------------------------|---------------|
| Structure | Grouped by post | Flat list |
| `name` | null | filename string |
| `mimeType` | null | "image/png" etc. |
| `metadata.size` | absent | file size in bytes |
| `baseModel` | populated | can be null |
| `tags` | `[]` | `null` |

---

## 4. `model.getGallerySettings`

Returns per-model gallery settings including hidden content and pinned posts.

### Request

```
GET /api/trpc/model.getGallerySettings?input={"json":{"id":500352,"authed":true}}
```

### Response (complete)

```json
{
  "result": {
    "data": {
      "json": {
        "hiddenTags": [],
        "hiddenUsers": [],
        "hiddenImages": {},
        "level": 31,
        "pinnedPosts": {
          "556208": [3307330, 3310380, 3314887, 25232009, 16956788, 15314263, 14887103]
        }
      }
    }
  }
}
```

### Fields

| Field | Description |
|-------|-------------|
| `hiddenTags` | Tag IDs hidden from this gallery |
| `hiddenUsers` | User IDs hidden from this gallery |
| `hiddenImages` | Map of hidden image IDs |
| `level` | Browsing level (31 = show all) |
| `pinnedPosts` | Map of `versionId → [postId, ...]` — pinned post IDs per version |

---

## 5. `tool.getAll`

Returns all known AI tools registered on Civitai. Useful for building a local lookup table.

### Request

```
GET /api/trpc/tool.getAll?input={"json":{
  "include": ["unlisted"],
  "sort": "AZ",
  "cursor": null,
  "authed": true
}}
```

### Response Structure

```json
{
  "result": {
    "data": {
      "json": {
        "items": [
          {
            "id": 84,
            "name": "A1111",
            "icon": null,
            "type": "Image",
            "priority": null,
            "domain": "https://github.com/AUTOMATIC1111/stable-diffusion-webui",
            "company": "AUTOMATIC1111",
            "description": "AUTOMATIC1111, or A1111 is...",
            "supported": false,
            "createdAt": "2024-05-16T16:03:14.611Z",
            "alias": null,
            "bannerUrl": null
          }
        ]
      }
    }
  }
}
```

### Notable Tools (IDs)

| ID | Name | Type |
|----|------|------|
| 78 | Civitai | Image |
| 84 | A1111 | Image |
| 86 | ComfyUI | Image |
| 4 | Adobe Firefly | Image |
| 62 | Adobe Photoshop | Editor |
| 63 | Adobe AfterEffects | Editor |

**Total tools:** ~100+ entries (response is 59KB)

### Tool Item Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | number | Unique tool ID |
| `name` | string | Tool name |
| `icon` | string\|null | Icon UUID or null |
| `type` | string | "Image", "Editor", "Video", etc. |
| `priority` | number\|null | Sort priority |
| `domain` | string | Website URL |
| `company` | string | Creator company |
| `description` | string | Short description |
| `supported` | boolean | Whether Civitai officially supports this tool |
| `alias` | string\|null | Alternative name |
| `bannerUrl` | string\|null | Banner image URL |

---

## 6. `technique.getAll`

Returns all generation techniques. Small static list — cache locally.

### Request

```
GET /api/trpc/technique.getAll?input={"json":{"authed":true}}
```

### Response (complete)

```json
{
  "result": {
    "data": {
      "json": [
        { "id": 8, "name": "controlnet", "type": "Image" },
        { "id": 2, "name": "img2img",    "type": "Image" },
        { "id": 3, "name": "inpainting", "type": "Image" },
        { "id": 7, "name": "img2vid",    "type": "Video" },
        { "id": 5, "name": "vid2vid",    "type": "Video" },
        { "id": 4, "name": "workflow",   "type": "Image" },
        { "id": 6, "name": "txt2vid",    "type": "Video" },
        { "id": 1, "name": "txt2img",    "type": "Image" }
      ]
    }
  }
}
```

### All Technique IDs

| ID | Name | Type |
|----|------|------|
| 1 | txt2img | Image |
| 2 | img2img | Image |
| 3 | inpainting | Image |
| 4 | workflow | Image |
| 5 | vid2vid | Video |
| 6 | txt2vid | Video |
| 7 | img2vid | Video |
| 8 | controlnet | Image |

---

## 7. Comment Endpoints

### 7a. `comment.getAll`

Model-level comments (legacy v1 comment system).

#### Request

```
GET /api/trpc/comment.getAll?input={"json":{
  "modelId": 500352,
  "limit": 4,
  "sort": "newest",
  "hidden": null,
  "cursor": null,
  "authed": true
}}
```

#### Response

```json
{
  "result": {
    "data": {
      "json": {
        "nextCursor": 667368,
        "comments": [
          {
            "id": 1038029,
            "createdAt": "2025-12-09T01:44:13.347Z",
            "nsfw": false,
            "content": "<p>hmm doesn't look like the girl...</p>",
            "modelId": 500352,
            "parentId": null,
            "locked": false,
            "tosViolation": false,
            "hidden": false,
            "user": {
              "id": 10609681,
              "username": "hmoobguyhero",
              "deletedAt": null,
              "image": "https://lh3.googleusercontent.com/...",
              "profilePicture": null,
              "cosmetics": []
            },
            "reactions": [],
            "model": {
              "name": "Crazy Girlfriend Mix [XL/PONY]"
            },
            "_count": {
              "comments": 0
            }
          }
        ]
      }
    }
  }
}
```

### 7b. `comment.getById`

Single comment with full detail including replies.

#### Request

```
GET /api/trpc/comment.getById?input={"json":{"id":788016,"authed":true}}
```

#### Response (excerpt)

```json
{
  "result": {
    "data": {
      "json": {
        "id": 788016,
        "createdAt": "2025-04-25T21:53:39.060Z",
        "nsfw": false,
        "content": "<p>Is this safe for publishing it on social media?</p>",
        "modelId": 500352,
        "parentId": null,
        "locked": false,
        "tosViolation": false,
        "hidden": false,
        "user": { /* User with profilePicture */ },
        "reactions": [
          {
            "id": 1096930,
            "reaction": "Like",
            "user": { /* User */ }
          }
        ],
        "model": {
          "name": "Crazy Girlfriend Mix [XL/PONY]"
        },
        "_count": {
          "comments": 1
        }
      }
    }
  }
}
```

### 7c. `comment.getCommentsById`

Replies/children of a specific comment.

#### Request

```
GET /api/trpc/comment.getCommentsById?input={"json":{"id":788016,"authed":true}}
```

#### Response

```json
{
  "result": {
    "data": {
      "json": [
        {
          "id": 788398,
          "createdAt": "2025-04-26T08:04:51.026Z",
          "nsfw": false,
          "content": "<p>It just gives your images an influencer look...</p>",
          "modelId": 500352,
          "parentId": 788016,
          "locked": false,
          "tosViolation": false,
          "hidden": false,
          "user": { /* User with cosmetics */ },
          "reactions": [],
          "_count": { "comments": 0 }
        }
      ]
    }
  }
}
```

### 7d. `comment.getCommentCountByModel`

Returns a single integer count.

```json
{ "result": { "data": { "json": 0 } } }
```

### 7e. `commentv2.getInfinite` and `commentv2.getThreadDetails`

Image-level v2 comment system. Both returned `null` in this HAR (image had no comments):

```json
{ "result": { "data": { "json": null } } }
```

Request params:
```json
{
  "entityId": 122723126,
  "entityType": "image",
  "limit": 3,
  "sort": "Oldest",
  "hidden": false,
  "authed": true
}
```

---

## 8. `tag.getVotableTags`

Votable tag list for a specific image — includes scores and moderation types.

### Request

```
GET /api/trpc/tag.getVotableTags?input={"json":{"id":122723126,"type":"image","authed":true}}
```

### Response (excerpt)

```json
{
  "result": {
    "data": {
      "json": [
        {
          "score": 10,
          "upVotes": 0,
          "downVotes": 0,
          "automated": true,
          "needsReview": false,
          "concrete": true,
          "lastUpvote": null,
          "id": 5133,
          "type": "Label",
          "nsfwLevel": 1,
          "name": "woman"
        },
        {
          "score": 9,
          "upVotes": 0,
          "downVotes": 0,
          "automated": true,
          "concrete": true,
          "id": 5146,
          "type": "Moderation",
          "nsfwLevel": 16,
          "name": "sex"
        }
      ]
    }
  }
}
```

### Tag Types

- `"Label"` — content labels (automated)
- `"Moderation"` — NSFW moderation flags
- `"UserGenerated"` — user-added tags

---

## 9. `model.getById`

Full model detail. Large response (~41KB).

### Request

```
GET /api/trpc/model.getById?input={"json":{"id":500352,"authed":true}}
```

### Key Fields in Response

```json
{
  "id": 500352,
  "name": "Crazy Girlfriend Mix [XL/PONY]",
  "description": "<p>HTML content...</p>",
  "type": "LORA",
  "uploadType": "Created",
  "status": "Published",
  "nsfwLevel": 15,
  "nsfw": false,
  "poi": false,
  "minor": false,
  "sfwOnly": false,
  "availability": "Public",
  "locked": false,
  "publishedAt": "2024-06-07T06:49:51.266Z",
  "updatedAt": "2025-12-19T02:36:03.380Z",
  "allowNoCredit": true,
  "allowCommercialUse": ["Image", "RentCivit", "Rent"],
  "allowDerivatives": true,
  "allowDifferentLicense": false,
  "meta": {
    "imageNsfw": "Mature",
    "commentsLocked": false
  },
  "user": {
    "id": 2086307,
    "username": "Ggrue",
    "rank": { "leaderboardRank": 90 }
  }
}
```

---

## 10. `modelVersion.getById`

Version-specific details.

### Request

```
GET /api/trpc/modelVersion.getById?input={"json":{"id":556208,"authed":true}}
```

### Response (complete)

```json
{
  "result": {
    "data": {
      "json": {
        "id": 556208,
        "name": "Insta Baddie[PONY]",
        "description": null,
        "baseModel": "Pony",
        "baseModelType": "Standard",
        "earlyAccessConfig": {},
        "earlyAccessEndsAt": null,
        "trainedWords": ["igbaddie"],
        "epochs": null,
        "steps": null,
        "clipSkip": null,
        "status": "Published",
        "createdAt": "2024-06-07T06:41:19.382Z",
        "vaeId": null,
        "trainingDetails": null,
        "trainingStatus": null,
        "uploadType": "Created",
        "usageControl": "Download",
        "model": {
          "id": 500352,
          "name": "Crazy Girlfriend Mix [XL/PONY]",
          "type": "LORA",
          "status": "Published",
          "publishedAt": "2024-06-07T06:49:51.266Z",
          "nsfw": false,
          "uploadType": "Created",
          "user": { "id": 2086307 },
          "availability": "Public"
        },
        "requireAuth": false,
        "settings": {},
        "recommendedResources": [],
        "monetization": null,
        "generationCoverage": { "covered": true },
        "canGenerate": true,
        "files": null
      }
    }
  }
}
```

---

## 11. `user.getCreator`

Public creator profile for a user.

### Request

```
GET /api/trpc/user.getCreator?input={"json":{"id":792293,"authed":true}}
```

### Response (complete)

```json
{
  "result": {
    "data": {
      "json": {
        "id": 792293,
        "username": "Dangas_GorgeousSluts",
        "image": "https://lh3.googleusercontent.com/...",
        "muted": false,
        "bannedAt": null,
        "deletedAt": null,
        "createdAt": "2023-03-14T13:57:40.592Z",
        "publicSettings": {
          "creatorCardStatsPreferences": ["followers", "reactions", "likes"]
        },
        "excludeFromLeaderboards": false,
        "links": [],
        "stats": {
          "downloadCountAllTime": 0,
          "thumbsUpCountAllTime": 0,
          "followerCountAllTime": 652,
          "reactionCountAllTime": 18936,
          "uploadCountAllTime": 0,
          "generationCountAllTime": 0
        },
        "rank": null,
        "cosmetics": [ /* badge objects */ ],
        "profilePicture": { /* image object */ },
        "_count": { "models": 0 }
      }
    }
  }
}
```

---

## 12. `image.getContestCollectionDetails`

Checks if an image is part of any contest collections.

### Request

```
GET /api/trpc/image.getContestCollectionDetails?input={"json":{"id":122723126,"authed":true}}
```

### Response

```json
{
  "result": {
    "data": {
      "json": {
        "collectionItems": [],
        "post": null
      }
    }
  }
}
```

---

## 13. `entityCollaborator.get`

Collaborators on a post.

### Request

```
GET /api/trpc/entityCollaborator.get?input={"json":{"entityId":26932568,"entityType":"Post","authed":true}}
```

### Response

```json
{ "result": { "data": { "json": [] } } }
```

---

## 14. Other Observed Endpoints

| Endpoint | Description | Notes |
|----------|-------------|-------|
| `tag.getAll` | All tags (possibly paginated) | Large |
| `modelVersion.donationGoals` | Donation goal data | Empty in captures |
| `common.getEntityAccess` | Access permissions check | |
| `system.getLiveNow` | Live stream status | Polled frequently |
| `track.addView` | POST — analytics view tracking | Returns 204 |
| `auction.getAll` | Auction listings | Status 0 (blocked/timeout) |

---

## Meta Field Comparison Across Endpoints

| Field | `getImagesAsPostsInfinite` | `getInfinite` | `getGenerationData` |
|-------|--------------------------|---------------|---------------------|
| `meta` (prompts) | ❌ always null | ❌ always null | ✅ when available |
| `hasMeta` | ✅ boolean flag | ✅ boolean flag | N/A |
| `hideMeta` | ✅ visibility flag | ✅ visibility flag | N/A |
| `baseModel` | ✅ populated | ⚠️ can be null | ✅ per resource |
| `resources` | ❌ absent | ❌ absent | ✅ full objects |
| `toolIds` | ✅ int array | ❌ absent | ✅ full objects |
| `techniqueIds` | ✅ int array | ❌ absent | ✅ full objects |
| `modelVersionIds` | ✅ auto-detected | ✅ | N/A |
| `modelVersionIdsManual` | ✅ creator-tagged | ✅ | N/A |
| `tagIds` | ✅ int array | ✅ int array | ❌ absent |
| `stats` | ✅ | ✅ | ❌ absent |
| `process` | ❌ absent | ❌ absent | ✅ e.g. "txt2img" |
| `canRemix` | ❌ absent | ❌ absent | ✅ |

---

## Recommended Fetch Strategy for ComfyChair

### Gallery Feed

1. Call `model.getGallerySettings` → get `pinnedPosts` and content filters
2. Call `image.getImagesAsPostsInfinite` with `modelId` + `modelVersionId` → post groups
3. Optionally filter by `tools=[86]` (ComfyUI only)
4. Display `baseModel`, `hasMeta`, tool/technique icons from the pre-fetched lookup tables

### Image Detail

1. When user taps an image: call `image.getGenerationData` with the image ID
2. Check `meta` for prompt/settings (null if hidden)
3. `resources[]` gives full model list with names even when meta is null
4. `tools[]` and `techniques[]` give full objects (not just IDs)
5. `canRemix` tells you whether remix is possible

### Lookup Tables (cache locally, refresh rarely)

- `tool.getAll` → `Map<id, Tool>` (~100 entries)
- `technique.getAll` → `Map<id, Technique>` (8 entries — hardcode this)

### NSFW Level Bitmask

```
1  = None (SFW)
2  = Soft
4  = Mature
8  = Explicit
16 = Blocked (very explicit / requires specific unlock)
31 = All (1+2+4+8+16)
```

`browsingLevel: 31` in requests = show everything.

### Cursor-based Pagination

nextCursor format: `"<offset>|<unixTimestampMs>"`
Pass back as `cursor` in next request. When `nextCursor` is `null`, no more pages.
