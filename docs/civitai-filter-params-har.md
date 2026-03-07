# Civitai `image.getImagesAsPostsInfinite` — Filter Parameter Analysis

> **HAR source:** `/tmp/civitai_filters.har` (Zen browser, captured 2026-03-07)  
> **Page context:** Model page — modelId `500352`, modelVersionId `556208`  
> **Total matching requests:** 23 of 24 entries  
> **Other trpc calls:** 1 — `comment.getAll` (not filter-related)

---

## Parameter Reference Table

All 23 requests share these **static** fields (never change):

| Parameter | Value | Notes |
|-----------|-------|-------|
| `modelId` | `500352` | Model being browsed |
| `modelVersionId` | `556208` | Specific version |
| `hidden` | `false` | Always false |
| `limit` | `50` | Page size |
| `cursor` | `null` | Pagination cursor (null = first page) |
| `authed` | `true` | Logged-in user |
| `periodMode` | `"published"` | Always `published` (vs `created`?) |
| `browsingLevel` | `31` | Content rating bitmask — see below |

These **vary** between calls:

| Parameter | Values Observed | UI Toggle |
|-----------|----------------|-----------|
| `period` | `"Year"`, `"Month"` | Time period selector |
| `sort` | `"Newest"`, `"Oldest"`, `"Most Reactions"`, `"Most Comments"`, `"Most Collected"` | Sort dropdown |
| `types` | absent, `["image"]`, `["video"]`, `["video","image"]` | Media type filter |
| `withMeta` | `false`, `true` | "Has prompt" / metadata filter |
| `requiringMeta` | `false`, `true` | "Missing metadata" — inverse of withMeta; shows images that LACK gen metadata (moderation/admin tool) |
| `fromPlatform` | absent, `false`, `true` | "Generated on-site" / Civitai platform toggle |
| `nonRemixesOnly` | absent, `false`, `true` | "No remixes" filter |
| `remixesOnly` | absent, `false`, `true` | "Remixes only" filter |
| `hideManualResources` | `false`, `true` | "Hide manually-tagged resources" |
| `hideAutoResources` | `false`, `true` | "Hide auto-detected resources" |

---

## All 23 Requests — Per-Call Params

Fields shown only when they differ from baseline or are absent.

| # | period | sort | types | withMeta | requiringMeta | fromPlatform | nonRemixesOnly | remixesOnly | hideManualRes | hideAutoRes | Notes |
|---|--------|------|-------|----------|---------------|--------------|----------------|-------------|---------------|-------------|-------|
| 1 | Year | Newest | — | false | false | — | — | — | false | false | **Baseline / initial load** |
| 2 | Month | Newest | — | false | false | — | — | — | false | false | Period changed to Month |
| 3 | Month | Newest | `["video"]` | false | false | — | — | — | false | false | Types = video only |
| 4 | Month | Newest | `["video","image"]` | false | false | — | — | — | false | false | Types = both |
| 5 | Month | Newest | `["image"]` | false | false | — | — | — | false | false | Types = image only |
| 6 | Month | Newest | `["image"]` | **true** | false | — | — | — | false | false | withMeta toggled ON |
| 7 | Month | Newest | `["image"]` | true | false | **true** | — | — | false | false | fromPlatform ON |
| 8 | Month | Newest | `["image"]` | true | false | true | **true** | **false** | false | false | nonRemixesOnly ON |
| 9 | Month | Newest | `["image"]` | **false** | false | true | true | false | false | false | withMeta back OFF |
| 10 | Month | Newest | `["image"]` | false | false | **false** | true | false | false | false | fromPlatform OFF |
| 11 | Month | Newest | `["image"]` | false | false | false | **false** | — | false | false | nonRemixesOnly OFF (field dropped) |
| 12 | Month | Newest | `["image"]` | false | false | false | — | **true** | false | false | remixesOnly ON |
| 13 | Month | Newest | `["image"]` | false | false | false | — | **false** | false | false | remixesOnly OFF |
| 14 | Month | Newest | `["image"]` | false | **true** | false | — | false | false | false | requiringMeta ON |
| 15 | Month | Newest | `["image"]` | false | false | false | — | false | **true** | false | hideManualResources ON |
| 16 | Month | Newest | `["image"]` | false | false | false | — | false | false | **true** | hideAutoResources ON |
| 17 | Month | Newest | `["image"]` | false | false | false | — | false | **true** | **true** | Both hide flags ON |
| 18 | Month | **Most Reactions** | `["image"]` | false | false | false | — | false | false | false | Sort changed |
| 19 | Month | Most Reactions | `["image"]` | false | false | **true** | — | false | false | false | fromPlatform ON again |
| 20 | Month | **Most Comments** | `["image"]` | false | false | true | — | false | false | false | Sort = Most Comments |
| 21 | Month | **Most Collected** | `["image"]` | false | false | true | — | false | false | false | Sort = Most Collected |
| 22 | Month | **Newest** | `["image"]` | false | false | true | — | false | false | false | Sort back to Newest |
| 23 | Month | **Oldest** | `["image"]` | false | false | true | — | false | false | false | Sort = Oldest |

> **"—" / absent** means the key was not present in that request's JSON (treated as default/falsy by backend).

---

## Parameter Deep Dives

### `browsingLevel` — Content Rating Bitmask

Value is always `31` in this capture. This is a bitmask across rating tiers:

| Bit | Value | Label |
|-----|-------|-------|
| 0 | 1 | Safe |
| 1 | 2 | Soft nudity |
| 2 | 4 | Mature |
| 3 | 8 | Explicit |
| 4 | 16 | XXX |

`31` = `0b11111` = all bits set = show everything (fully unlocked account).  
For SFW-only browsing you'd send `browsingLevel: 1` (only Safe).

---

### `types` — Media Type Filter

| Value | Meaning |
|-------|---------|
| absent | All types (image + video) |
| `["image"]` | Images only |
| `["video"]` | Videos only |
| `["video","image"]` | Both (explicit — same as absent but overriding a previous filter) |

---

### `sort` — Sort Order

| Value | UI Label |
|-------|----------|
| `"Newest"` | Newest |
| `"Oldest"` | Oldest |
| `"Most Reactions"` | Most Reactions |
| `"Most Comments"` | Most Comments |
| `"Most Collected"` | Most Collected |

(No `"Most Buzz"` or `"Random"` appeared in this session — may exist.)

---

### `period` — Time Window

| Value | Meaning |
|-------|---------|
| `"AllTime"` | All time (not seen here but likely exists) |
| `"Year"` | Past year |
| `"Month"` | Past month |
| `"Week"` | Past week (not seen) |
| `"Day"` | Past day / Today (not seen) |

---

### `withMeta` vs `requiringMeta`

**Verified via HAR response analysis** (call #6 vs #14):

| Param | Behavior | HAR result |
|-------|----------|------------|
| `withMeta: true` | **Positive filter** — only images that HAVE gen metadata (`hasMeta=true`). 200+ images returned, all `hasMeta=true`, zero `hasMeta=false`. | ✅ Confirmed |
| `requiringMeta: true` | **Inverse/moderation filter** — only images that are MISSING gen metadata. Returns images that lack metadata (need it added). On a popular model page where most images have metadata, this returns 0. | ✅ Confirmed (0 results) |
| Both false | No metadata filtering — show all images regardless of metadata state | Default |

> **Note:** Civitai likely enforces metadata as a requirement at upload time for R+ models, meaning non-compliant images never enter the system. `requiringMeta: true` would return 0 on those models too — not because everything is compliant, but because the gate is at upload. May only surface results for grandfathered uploads predating the policy. **Low signal — likely not worth exposing in the app filter UI.**

---

### Remix Filters — Mutual Exclusion

| `nonRemixesOnly` | `remixesOnly` | Result |
|-----------------|---------------|--------|
| absent/false | absent/false | All images |
| `true` | `false` | Original images only (no remixes) |
| absent/false | `true` | Remixes only |

These appear mutually exclusive in the UI. The backend likely handles contradictory values by ignoring one.

---

### `fromPlatform` — On-Site Generation Filter

| Value | Meaning |
|-------|---------|
| absent | No filter — all images regardless of source |
| `true` | Only images generated on Civitai (on-site) |
| `false` | Explicitly "not filtered" — same as absent but overriding a prior `true` |

---

### `hideManualResources` / `hideAutoResources`

These control whether images tagged with the model via **manual** curation vs **automatic detection** are included.

| Param | Effect |
|-------|--------|
| `hideManualResources: true` | Exclude images where a user manually tagged this model |
| `hideAutoResources: true` | Exclude images where the model was auto-detected from metadata |
| Both true | Only show images with no resource tagging (unusual edge case) |
| Both false (default) | Show all |

---

## Other Interesting tRPC Calls

Only one non-`image.getImagesAsPostsInfinite` call was captured:

| Endpoint | Params | Notes |
|----------|--------|-------|
| `comment.getAll` | `modelId: 500352, limit: 8, sort: "newest", cursor: 592079` | Comment pagination — cursor-based, `sort: "newest"` or likely `"oldest"` too. Response includes `nextCursor` for pagination, comment objects with `id`, `createdAt`, `nsfw`, `content` (HTML), nested replies. |

> **Not captured in this session:** `technique.getAll`, `tool.getAll`, `tag.getVotableTags`, `image.getInfinite`. These would appear if the user navigated to the Tools/Techniques filter panels or the main image feed (not a model-specific page).

---

## API Call Template

For programmatic use, here's the minimal call structure:

```
GET https://civitai.com/api/trpc/image.getImagesAsPostsInfinite?input=<URL-encoded JSON>
```

```json
{
  "json": {
    "period": "Month",
    "periodMode": "published",
    "sort": "Newest",
    "types": ["image"],
    "withMeta": false,
    "requiringMeta": false,
    "fromPlatform": false,
    "remixesOnly": false,
    "hideManualResources": false,
    "hideAutoResources": false,
    "modelVersionId": 556208,
    "modelId": 500352,
    "hidden": false,
    "limit": 50,
    "browsingLevel": 31,
    "cursor": null,
    "authed": true
  }
}
```

Pagination: pass `cursor` value from the previous response's `nextCursor` field.
