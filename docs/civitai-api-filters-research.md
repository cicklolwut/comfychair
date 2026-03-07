# Civitai tRPC API — Filters Research

> Investigated: 2026-03-07  
> All requests made with `User-Agent: Mozilla/5.0`  
> Base URL: `https://civitai.com/api/trpc/`

---

## Overview

All endpoints follow tRPC's HTTP GET convention:
- URL: `GET /api/trpc/<procedure>`
- Query param: `input=<URL-encoded JSON>`
- Input JSON shape: `{"json": {...}, "meta": {"values": {...}}}`
- Response always wrapped in `{"result": {"data": {"json": ..., "meta": {...}}}}`

The `meta.values` in the response is tRPC super-json type metadata:
- `["Date"]` → field should be parsed as `Date` object
- `["undefined"]` → field is `null` on the wire but semantically `undefined`
- `referentialEqualities` → object identity sharing map (for deduplication)

---

## Endpoint 1: Gallery Settings

### Request

```
GET https://civitai.com/api/trpc/model.getGallerySettings?input={"json":{"id":500352,"authed":true}}
```

- `id` = modelId (integer)
- `authed` = true/false (whether the user is authenticated)

### Response Shape

```json
{
  "result": {
    "data": {
      "json": {
        "hiddenTags": [],            // array<int> — tag IDs the model author has hidden
        "hiddenUsers": [],           // array<int> — user IDs hidden from this gallery
        "hiddenImages": {},          // object — map of hidden image IDs (usually empty)
        "level": 31,                 // int — browsing level (NSFW bitmask)
        "pinnedPosts": {
          "556208": [3307330, 3310380, 3314887, 25232009, 16956788, 15314263, 14887103]
        }
      }
    }
  }
}
```

### Field Details

| Field | Type | Notes |
|-------|------|-------|
| `hiddenTags` | `int[]` | Tags the model creator wants filtered from gallery |
| `hiddenUsers` | `int[]` | User IDs whose content is hidden in gallery |
| `hiddenImages` | `object` | Map of hidden image IDs |
| `level` | `int` | NSFW browsing level bitmask (31 = all content) |
| `pinnedPosts` | `object` | Key = **modelVersionId as string**, value = ordered `postId[]` |

### `pinnedPosts` Structure

**Critical:** `pinnedPosts` is keyed by **modelVersionId** (as a string), not modelId. This means pinned posts are **per-version**.

```json
"pinnedPosts": {
  "556208": [3307330, 3310380, 3314887, 25232009, 16956788, 15314263, 14887103]
}
```

The post IDs are ordered — this order determines their display priority in the gallery. The posts appear first in `getImagesAsPostsInfinite` with `pinned: true`.

For model 500352, version 556208 has 7 pinned posts. If a version has no pinned posts, the key is absent.

---

## Endpoint 2: Tools Filter (`tool.getAll`)

### Request

```
GET https://civitai.com/api/trpc/tool.getAll?input={"json":{"include":["unlisted"],"sort":"AZ","cursor":null,"authed":true},"meta":{"values":{"cursor":["undefined"]}}}
```

- `include: ["unlisted"]` — include tools not shown in main UI
- `sort: "AZ"` — alphabetical sort
- `cursor: null` — pagination cursor (null = first page)

### Response Shape

```json
{
  "result": {
    "data": {
      "json": {
        "items": [ /* Tool[] */ ],
        "nextCursor": null   // null = no more pages
      }
    }
  }
}
```

### Tool Object Shape

```json
{
  "id": 86,
  "name": "ComfyUI",
  "icon": null,                        // null or UUID string (Cloudflare image ID)
  "type": "Image",                     // see Tool Types below
  "priority": 4,                       // null or int (higher = more prominent)
  "domain": "https://github.com/comfyanonymous/ComfyUI",
  "company": "comfyanonymous",
  "description": "A Stable Diffusion UI...",
  "supported": false,                  // bool — Civitai-native support
  "createdAt": "2024-05-16T16:03:14.611Z",
  "alias": null,                       // null or string (short alias for supported tools)
  "bannerUrl": null                    // null or UUID string (Cloudflare image ID)
}
```

### Tool Types

| Type | Description |
|------|-------------|
| `Image` | AI image generation tools |
| `Video` | AI video generation tools |
| `Editor` | Post-processing / editing software |
| `Compute` | Cloud GPU / compute platforms |
| `Upscalers` | Upscaling/enhancement tools |
| `GameEngines` | Game engines (Unity, Unreal, Blender) |
| `LLM` | Large language models |
| `MotionCapture` | Motion capture tools |

### Complete Tool List (135 tools, as of 2026-03-07)

| ID | Name | Type | Supported | Alias |
|----|------|------|-----------|-------|
| 84 | A1111 | Image | false | — |
| 63 | Adobe AfterEffects | Editor | false | — |
| 4 | Adobe Firefly | Image | false | — |
| 62 | Adobe Photoshop | Editor | false | — |
| 127 | Adobe Premiere | Editor | false | — |
| 129 | AniFusion | Image | false | — |
| 171 | AnimateDiff | Video | false | — |
| 252 | Artflow | Image | false | — |
| 70 | Banodoco | Video | false | — |
| 263 | Blender | GameEngines | false | — |
| 26 | Brev | Compute | false | — |
| 152 | Canva | Image | false | — |
| 81 | CapCut | Editor | false | — |
| 150 | ChatGPT | Editor | false | — |
| 78 | Civitai | Image | **true** | — |
| 184 | CogVideoX | Video | false | — |
| **86** | **ComfyUI** | **Image** | false | — |
| 93 | Craiyon | Image | false | — |
| 111 | Cuebric | Image | false | — |
| 73 | DALL-E 3 | Image | false | — |
| 165 | Davant | Image | false | — |
| 109 | DaVinci | Image | false | — |
| 80 | DaVinci Resolve | Editor | false | — |
| 130 | Deep Dream Generator | Image | false | — |
| 120 | DeepMake | Video | false | — |
| 7 | Deforum Studio | Video | false | — |
| 108 | Diffus | Image | false | — |
| 10 | Domo AI | Video | false | — |
| 139 | Draw Things | Image | false | — |
| 89 | Dream | Image | false | — |
| 72 | DreamStudio | Image | false | — |
| 9 | EBSynth | Video | false | — |
| 5 | Fable | Video | false | — |
| 287 | FaceFusion | Image | false | — |
| 190 | fal | Compute | false | — |
| 241 | Final Cut Pro | Editor | false | — |
| 202 | Flimora | Editor | false | — |
| 97 | Flush | Image | false | — |
| 199 | Flux | Image | false | — |
| 83 | Fooocus | Image | false | — |
| 88 | Forge | Image | false | — |
| 304 | FramePack | Video | false | — |
| 1 | Gemini | Image | false | — |
| 128 | Genmo | Video | false | — |
| 94 | Getty Images Generative AI | Image | false | — |
| 76 | GIMP | Editor | false | — |
| 154 | Gooey AI | Video | false | — |
| 90 | Google ImageFX | Image | false | — |
| 305 | GPT Image 1 | Image | false | — |
| 284 | Grok | LLM | false | — |
| 65 | Haiper | Video | **true** | haiper |
| 140 | Hedra | Video | false | — |
| 177 | Higgsfield | Video | false | — |
| 148 | Hitfilm | Editor | false | — |
| 302 | Hugging Face | Image | false | — |
| 274 | HunYuan | Video | **true** | hunyuan |
| 113 | Ideogram | Image | false | — |
| 69 | iKHOR Labs | Video | false | — |
| 236 | Invideo | Video | false | — |
| 87 | Invoke | Image | false | — |
| 8 | Kaiber | Video | false | — |
| 153 | Kittl AI | Image | false | — |
| 166 | Kling | Video | false | kling |
| 167 | Kolors | Image | false | — |
| 2 | KREA | Image | false | — |
| 77 | Krita | Image | false | — |
| 255 | Lambda Labs | Compute | false | — |
| 151 | Lasco.ai | Image | false | — |
| 41 | LensGo | Video | false | — |
| 170 | Lightricks LTXV | Video | false | — |
| 143 | Live Portrait | MotionCapture | false | — |
| 141 | LTX Studio | Video | false | — |
| 124 | Luma Dream Machine | Video | false | — |
| 125 | Luma Genie | Video | false | — |
| 135 | Magic Animate | Video | false | — |
| 242 | Magix Video | Editor | false | — |
| 47 | MAGNIFIC | Upscalers | false | — |
| 131 | Maze | Image | false | — |
| 96 | Meta AI | Image | false | — |
| 30 | Midjourney | Image | false | — |
| 142 | MimicMotion | MotionCapture | false | — |
| 172 | MiniMax / Hailuo | Video | false | — |
| 169 | Mochi | Video | **true** | mochi |
| 106 | ModelsLab | Image | false | — |
| 66 | Morph Studio | Video | false | — |
| 174 | Nebius | Compute | false | — |
| 121 | neural frames | Video | false | — |
| 82 | Nijijourney | Image | false | — |
| 201 | Nim | Video | false | — |
| 107 | OpenArt | Image | false | — |
| 85 | Parseq | Video | false | — |
| 64 | Photopea | Image | false | — |
| 196 | Picsart | Editor | false | — |
| 138 | PicSo | Image | false | — |
| 67 | Pika | Video | false | — |
| 253 | PixaBay | Image | false | — |
| 155 | Pixverse | Video | false | — |
| 39 | Prism | Video | false | — |
| 185 | Purplesmart | Image | false | — |
| 249 | Recraft | Image | false | — |
| 286 | Rendermind | Image | false | — |
| 126 | Rubbrband | Image | false | — |
| 25 | RunDiffusion | Compute | false | — |
| 24 | RunPod | Compute | false | — |
| 68 | Runway | Video | false | — |
| 137 | SadTalker | Video | false | — |
| 192 | SAGA | Video | false | — |
| 180 | Salad | Compute | false | — |
| 75 | Salt | Image | false | — |
| 195 | Scenario | Image | false | — |
| 285 | SD.Next | Image | false | — |
| 99 | Showrunner AI | Video | false | — |
| 95 | Shutterstock AI Image Generation | Image | false | — |
| 194 | Silmu | Video | false | — |
| 240 | Sora | Video | false | — |
| 71 | Stable Artisan | Video | false | — |
| 168 | SwarmUI | Image | false | — |
| 23 | ThinkDiffusion | Compute | false | — |
| 48 | Topaz Photo AI | Upscalers | false | — |
| 49 | Topaz Video AI | Upscalers | false | — |
| 74 | Touch Designer | Image | false | — |
| 119 | Tripo 3D | Video | false | — |
| 27 | Unity | GameEngines | false | — |
| 61 | Unreal Engine | GameEngines | false | — |
| 204 | Veed.io | Editor | false | — |
| 205 | Veo | Video | false | — |
| 133 | VidProc | Video | false | — |
| 193 | Vidu | Video | false | vidu |
| 79 | Vimeo | Video | false | — |
| 134 | VSDC | Editor | false | — |
| 303 | Wan Video | Video | **true** | wan |
| 98 | Warp Video | Video | false | — |
| 251 | Wondershare | Editor | false | — |
| 92 | Wowzer | Image | false | — |
| 105 | Yodayo | Image | false | — |

**Pagination:** `nextCursor: null` — all 135 tools returned in one page.

---

## Endpoint 3: Techniques Filter (`technique.getAll`)

### Request

```
GET https://civitai.com/api/trpc/technique.getAll?input={"json":{"authed":true}}
```

### Response Shape

```json
{
  "result": {
    "data": {
      "json": [
        { "id": 1, "name": "txt2img", "type": "Image" },
        { "id": 2, "name": "img2img", "type": "Image" },
        ...
      ]
    }
  }
}
```

Response is a **flat array** (not paginated, no cursor).

### Complete Technique List

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

**8 total techniques.** No pagination needed.

---

## Endpoint 4: Gallery with Filters (`image.getImagesAsPostsInfinite`)

### Request (filtered)

```
GET https://civitai.com/api/trpc/image.getImagesAsPostsInfinite?input={
  "json": {
    "period": "AllTime",
    "periodMode": "published",
    "sort": "Newest",
    "withMeta": false,
    "requiringMeta": false,
    "tools": [86],
    "techniques": [2, 8],
    "modelVersionId": 556208,
    "modelId": 500352,
    "hidden": false,
    "limit": 5,
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

### Response Shape

```json
{
  "result": {
    "data": {
      "json": {
        "nextCursor": "18|1772411400000",   // string | null — pagination cursor
        "items": [ /* Post[] */ ]
      },
      "meta": {
        "values": { /* tRPC super-json type hints */ },
        "referentialEqualities": { /* object identity sharing map */ }
      }
    }
  }
}
```

### Post Object Shape

```json
{
  "postId": 26932568,
  "pinned": false,                         // bool — true for pinnedPosts entries
  "nsfwLevel": 4,                          // int — NSFW bitmask for the post
  "modelVersionId": null,                  // null in gallery context
  "publishedAt": "2025-12-18T19:35:08.960Z",
  "sortAt": "2025-12-18T19:35:08.960Z",
  "createdAt": "2025-12-18T19:35:08.960Z",
  "user": { /* User object */ },
  "images": [ /* Image[] */ ],
  "review": null                           // ReviewObject | null
}
```

### Image Object Shape (inside `post.images`)

```json
{
  "id": 114314534,
  "reactionCount": 18,
  "commentCount": 0,
  "collectedCount": 5,
  "index": 1,                              // int — position within post
  "postId": 25232009,
  "url": "365e246e-6c37-4b0a-982d-b8594a7609ac",  // Cloudflare Image UUID
  "nsfwLevel": 2,
  "aiNsfwLevel": 0,
  "width": 832,
  "height": 1216,
  "hash": "U4Dbyf0,0M0119~V0y?F00IU~B9x0057=G-p",  // BlurHash
  "hideMeta": true,                        // bool — if true, prompt is hidden
  "sortAt": "2025-12-18T19:35:08.960Z",
  "type": "image",
  "userId": 9077594,
  "needsReview": null,
  "hasMeta": false,                        // bool — has generation metadata
  "onSite": true,                          // bool — generated on civitai.com
  "postedToId": null,
  "combinedNsfwLevel": 2,
  "baseModel": "Pony",                     // string — e.g. "Pony", "SDXL", "Flux.1 D"
  "modelVersionIds": [382152, 534756, 556208],  // array<int> — all models used
  "toolIds": [78, 86],                     // ← FILTER FIELD: tool IDs used
  "techniqueIds": [1, 2],                  // ← FILTER FIELD: technique IDs used
  "existedAtUnix": 1768428062343,          // Unix timestamp ms — when image was found
  "sortAtUnix": 1766086508960,             // Unix timestamp ms — sort key
  "tagIds": [55, 3084, 3641, ...],         // int[] — content tag IDs
  "modelVersionIdsManual": [],
  "minor": false,
  "blockedFor": null,
  "remixOfId": null,                       // int | null — source image if remixed
  "hasPositivePrompt": false,
  "availability": "Public",
  "poi": false,
  "acceptableMinor": false,
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
  "modelVersionId": null,
  "createdAt": "2025-12-18T19:35:08.960Z",
  "metadata": { "width": 832, "height": 1216 },
  "publishedAt": "2025-12-18T19:35:08.960Z",
  "user": { /* same reference as post.user */ },
  "reactions": [],                         // empty when not authed or no reactions
  "cosmetic": null,
  "tags": [],                              // always empty — use tagIds instead
  "name": null,
  "scannedAt": null,
  "mimeType": null,
  "ingestion": "Scanned",
  "postTitle": null,
  "meta": null,                            // PromptMeta | null (null when hideMeta=true)
  "thumbnailUrl": null                     // undefined in response meta
}
```

### User Object Shape

```json
{
  "id": 9077594,
  "username": "pn132",
  "image": "https://cdn.discordapp.com/avatars/...",  // string | null
  "deletedAt": null,
  "cosmetics": [
    {
      "cosmeticId": 298,
      "data": null,
      "cosmetic": {
        "id": 298,
        "name": "Avatar Cosmetic 2",
        "type": "ProfileDecoration",
        "data": { "url": "...", "offset": "30%" },
        "source": "Purchase"
      }
    }
  ],
  "profilePicture": {
    "id": 99696804,
    "name": "8f15c042-5cce-42eb-bc40-51b3ce8f5932.jpeg",
    "url": "c70492ab-bbbc-4e00-9803-75e787d0cd87",   // Cloudflare Image UUID
    "nsfwLevel": 2,
    "hash": "UUCZhRD%t7j]~qITogt7-=IUofxtxuRjaet6",
    "userId": 9077594,
    "ingestion": "Scanned",
    "type": "image",
    "width": 832,
    "height": 1216,
    "metadata": { ... }
  }
}
```

### Review Object Shape

```json
{
  "rating": 5,          // int — star rating
  "details": null,      // string | null — review text
  "recommended": true,  // bool
  "id": 53507305        // int — review ID
}
```

---

## Filter Behavior Analysis

### Filter Logic: OR within, AND between

Tested with `tools=[86]` (ComfyUI) and `techniques=[2,8]` (img2img, controlnet):

**Results from filtered call:**
```
postId=26932568  toolIds=[78, 86]  techniqueIds=[1, 2]
postId=26957855  toolIds=[86]      techniqueIds=[1, 2]
postId=26952233  toolIds=[86]      techniqueIds=[1, 2]
```

**Conclusions:**
- All returned images have `toolId 86` (ComfyUI) ✓
- Techniques match `2` (img2img) but NOT necessarily `8` (controlnet) — confirms **OR within techniques** 
- Both filters must be satisfied simultaneously — confirms **AND between tools and techniques**
- Summary: `(toolIds ∩ tools != ∅) AND (techniqueIds ∩ techniques != ∅)`

### Pinned Posts Behavior with Filters

- **Unfiltered gallery:** first N posts have `pinned: true`, matching the pinnedPosts IDs from gallery settings. These appear first regardless of sort order.
- **Filtered gallery:** `pinned: false` on ALL results. Pinned posts are excluded when tool/technique filters are active (because pinned posts typically have `toolIds: []` and `techniqueIds: []`).
- **Implication for UI:** When tools/techniques filters are active, don't expect pinned posts to appear. Show them only when no filters are applied.

### `toolIds` / `techniqueIds` on Images

Most images uploaded externally (outside Civitai's generator) have `toolIds: []` and `techniqueIds: []`. Only images where the uploader explicitly tagged the tool/technique (or images generated on-site) populate these arrays.

Key indicator: `onSite: true` means generated on Civitai's built-in generator (most likely has toolIds set). `onSite: false` means uploaded from an external tool (may or may not have toolIds).

---

## Comparing Filtered vs Unfiltered Gallery

| Aspect | Unfiltered | Filtered (tools=[86], techniques=[2,8]) |
|--------|------------|----------------------------------------|
| First results | Pinned posts (pinned=true) | Non-pinned only |
| `nextCursor` format | `"6\|1772899466320"` | `"18\|1772411400000"` |
| Items per page | Respects `limit` param | Respects `limit` param |
| `toolIds` on images | Usually `[]` | Contains the filtered tool ID |
| `techniqueIds` on images | Usually `[]` | Contains at least one filtered technique ID |
| Response shape | Identical | Identical |

The **response structure is identical** between filtered and unfiltered. Only the contents differ. No extra fields are added/removed by filtering.

---

## Pagination

Both filtered and unfiltered use the same pagination mechanism:

- `nextCursor: null` → no more pages
- `nextCursor: "18|1772411400000"` → pass as `cursor` in the next request, e.g.:
  ```json
  {"json": {..., "cursor": "18|1772411400000"}, "meta": {"values": {"cursor": ["undefined"]}}}
  ```
  **Note:** Despite sending `cursor: null` initially, the `meta.values.cursor: ["undefined"]` is always required to signal the null is intentional undefined.

---

## Image CDN URL Construction

Images use Cloudflare Images. The `url` field is a UUID. To construct a displayable URL:

```
https://image.civitai.com/xG1nkqKTMzGDvpLrqFT7WA/<uuid>/original=true/<filename>
```

Or for resized:
```
https://image.civitai.com/xG1nkqKTMzGDvpLrqFT7WA/<uuid>/width=<w>/
```

The CDN prefix `xG1nkqKTMzGDvpLrqFT7WA` is the Civitai account hash (consistent across all images).

---

## Request Parameters Reference

### `image.getImagesAsPostsInfinite` Parameters

| Parameter | Type | Notes |
|-----------|------|-------|
| `modelId` | `int` | Filter to a specific model |
| `modelVersionId` | `int` | Filter to a specific model version |
| `period` | `"AllTime" \| "Year" \| "Month" \| "Week" \| "Day"` | Time filter |
| `periodMode` | `"published" \| "created"` | Which date field to use |
| `sort` | `"Newest" \| "MostReactions" \| "MostComments" \| "MostCollected"` | Sort order |
| `withMeta` | `bool` | Include generation metadata in response |
| `requiringMeta` | `bool` | Only return images that have metadata |
| `tools` | `int[]` | Filter by tool IDs (OR within list) |
| `techniques` | `int[]` | Filter by technique IDs (OR within list) |
| `hidden` | `bool` | Include hidden images |
| `limit` | `int` | Page size (default ~20, tested with 3-5) |
| `browsingLevel` | `int` | NSFW bitmask (31 = all content) |
| `cursor` | `string \| null` | Pagination cursor |
| `authed` | `bool` | Whether user is authenticated |

### `browsingLevel` Bitmask Values

| Bit | Value | Meaning |
|-----|-------|---------|
| 0 | 1 | PG (safe) |
| 1 | 2 | PG-13 |
| 2 | 4 | R |
| 3 | 8 | X (explicit) |
| 4 | 16 | XXX (extreme) |
| 5 | 32 | Blocked content |

`browsingLevel: 31` = bits 0-4 = PG+PG13+R+X+XXX (everything except blocked).

---

## Implementation Notes for ComfyChair

1. **Gallery Settings call:** Make one call to `model.getGallerySettings` when loading a model page. Cache `pinnedPosts` to know which postIds to highlight/pin in UI.

2. **Tool/Technique filters:** Load once, cache aggressively (these change rarely). Use `tool.getAll` and `technique.getAll` at app startup or first gallery open.

3. **Filter UI design:**
   - For tools: show only `type: "Image"` tools in Image gallery filter (type="Video" tools aren't relevant)
   - Consider grouping by type or showing most-used first (use `priority` field: 1=highest)
   - Techniques with `type: "Video"` (5,6,7) should only show for video galleries
   - Currently only 8 techniques — a simple chip/checkbox group works fine

4. **Pinned posts:** When `tools`/`techniques` filters are not applied, the first items have `pinned: true`. You can visually distinguish them (pin icon, reorder badge). When filters are active, don't show a "pinned posts" section since they won't appear.

5. **toolIds/techniqueIds sparsity:** Most community-uploaded images have empty `toolIds` and `techniqueIds`. The filter will return far fewer results than the unfiltered gallery — set user expectations accordingly (maybe show result count or "few results" messaging).

6. **Cursor pagination:** The cursor format is `"index|timestamp"`. Pass it back verbatim in the `cursor` field; always include `meta.values.cursor: ["undefined"]` in the request regardless.
