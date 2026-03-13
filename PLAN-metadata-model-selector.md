# Plan: Metadata-Based Model Selection + Discrepancy Detection

## Context

Currently, `ModelDropdown` shows models as filesystem paths from `/object_info` (via `ModelCache`). The helper already provides Civitai metadata via organized models. This plan adds:

1. **Settings toggle** — file-path mode (current) vs metadata mode (grouped by base model, enriched display)
2. **Safetensor header parsing** — classify checkpoint vs unet without loading full model
3. **Discrepancy detection** — compare actual file location vs where it should be based on model type
4. **Move API** — relocate misplaced models and update all DB references

## Architecture

### Classification Priority (for determining model node type)

```
1. Safetensor header analysis (most reliable — reads tensor key prefixes)
2. Civitai model type (good for loras, embeddings, controlnets, etc.)
3. Current folder location (fallback)
```

Safetensor headers distinguish checkpoint (has text_encoder/VAE tensors) from unet-only (diffusion tensors only). Civitai can't make this distinction — both are "Checkpoint."

### Target Folder Structure

Models should live at: `{model_type}/{base_model}/filename.safetensors`
- `model_type`: checkpoints, loras, unet, embeddings, controlnet, vae, etc.
- `base_model`: SDXL, Illustrious, Pony, SD 1.5, Flux, etc. (from Civitai metadata)
- Unidentified models stay where they are (no auto-move without metadata)

### Data Flow

```
File Path Mode (current):
  /object_info → ModelCache → ModelDropdown (folder tree)

Metadata Mode (new):
  /object_info → ModelCache (ground truth for dropdown membership)
  /comfychair/models/organized → OrganizedModelCache (display enrichment)
  Dropdown shows: base model groups → model name + extension + weight badges
  Selected value is still a file path (ComfyUI requirement)
```

---

## Part 1: Helper — Safetensor Header Parsing + Classification

### 1A: Safetensor Header Reader (`header_reader.py` — new file)

```python
import struct
import json
from pathlib import Path

# Tensor key prefixes that indicate model components
TEXT_ENCODER_PREFIXES = {
    'cond_stage_model',   # SD 1.x
    'conditioner',        # SDXL
    'text_encoder',       # diffusers layout
    'text_encoders',      # some Flux variants
}
VAE_PREFIXES = {
    'first_stage_model',  # SD 1.x / SDXL ldm format
    'vae',                # diffusers layout
}
UNET_PREFIXES = {
    'model',              # covers model.diffusion_model.*
    'unet',               # diffusers layout
    'input_blocks',       # raw SD1.x UNet keys
    'double_blocks',      # Flux
    'joint_blocks',       # SD3
    'diffusion_model',    # some export formats
}
LORA_INDICATORS = {
    'lora_unet',
    'lora_te',
    'lora_',
}

def read_safetensor_header(path: str) -> dict | None:
    """Read only the JSON header from a safetensors file. No tensor data loaded."""
    try:
        with open(path, 'rb') as f:
            raw = f.read(8)
            if len(raw) < 8:
                return None
            header_len = struct.unpack('<Q', raw)[0]
            if header_len > 100_000_000:  # sanity check: 100MB header = corrupt
                return None
            header_raw = f.read(header_len)
            return json.loads(header_raw)
    except Exception:
        return None

def classify_from_header(header: dict) -> str:
    """Classify model type from safetensor header keys.
    Returns: 'checkpoint', 'unet', 'lora', 'vae', 'text_encoder', 'unknown'
    """
    keys = set(header.keys()) - {'__metadata__'}
    prefixes = {k.split('.')[0] for k in keys}

    # Check for LoRA keys first (they have distinctive patterns)
    if any(any(k.startswith(ind) for ind in LORA_INDICATORS) for k in keys):
        return 'lora'

    has_text_encoder = bool(prefixes & TEXT_ENCODER_PREFIXES)
    has_vae = bool(prefixes & VAE_PREFIXES)
    has_unet = bool(prefixes & UNET_PREFIXES)

    # VAE-only file
    if has_vae and not has_unet and not has_text_encoder:
        return 'vae'

    # Text encoder only
    if has_text_encoder and not has_unet and not has_vae:
        return 'text_encoder'

    # Full checkpoint = has text encoder or VAE alongside unet
    if has_text_encoder or has_vae:
        return 'checkpoint'

    # UNET-only (diffusion model without text encoder/VAE)
    if has_unet:
        return 'unet'

    return 'unknown'

def extract_metadata(header: dict) -> dict:
    """Extract __metadata__ block if present (contains training info, etc.)"""
    return header.get('__metadata__', {})

def get_tensor_stats(header: dict) -> dict:
    """Get summary stats from header: tensor count, dtype, total params estimate."""
    keys = set(header.keys()) - {'__metadata__'}
    dtypes = set()
    for k in keys:
        info = header[k]
        if isinstance(info, dict) and 'dtype' in info:
            dtypes.add(info['dtype'])
    return {
        'tensor_count': len(keys),
        'dtypes': sorted(dtypes),
    }
```

### 1B: DB Schema Changes

Add to `file_cache` table:
```sql
ALTER TABLE file_cache ADD COLUMN folder_type TEXT;  -- ComfyUI folder: checkpoints, loras, etc.
ALTER TABLE file_cache ADD COLUMN header_class TEXT;  -- From safetensor analysis: checkpoint, unet, lora, etc.
ALTER TABLE file_cache ADD COLUMN header_dtypes TEXT;  -- JSON array of dtypes found in header
ALTER TABLE file_cache ADD COLUMN header_meta TEXT;    -- JSON of __metadata__ block
```

Migration v3 → v4 in `db.py`. Null values = not yet analyzed (triggers analysis on next scan).

### 1C: Scanner Changes

Modify `scanner.py` to:
1. Store `folder_type` (the ComfyUI model type from `_collect_model_files`) in `file_cache`
2. After hashing, also read safetensor headers (only for `.safetensors` files — fast, reads ~10KB)
3. Store classification result in `file_cache.header_class`
4. Store dtypes and metadata in `file_cache.header_dtypes`, `file_cache.header_meta`

Header reading happens during scan, not on API request. It's fast (~1ms per file, reads only the header).

### 1D: Discrepancy Detection

New method in `db.py`:

```python
def get_discrepancies(self) -> list[dict]:
    """
    Compare each file's actual location (folder_type) against its
    classified type (from header analysis + Civitai metadata).
    Returns list of mismatches with current path and suggested path.
    """
```

Logic:
1. For each file in `file_cache` with header_class or Civitai model_type:
   - Determine "expected folder type" from: header_class (priority) → civitai model_type → skip
   - Compare against `folder_type` (actual ComfyUI folder it was found in)
   - If mismatch → it's a discrepancy
2. Map classifications to ComfyUI folder types:
   - `checkpoint` → `checkpoints`
   - `unet` → `diffusion_models`
   - `lora` → `loras`
   - `vae` → `vae`
   - `text_encoder` → `text_encoders`
   - Civitai "LORA"/"LoCon"/"DoRA" → `loras`
   - Civitai "TextualInversion" → `embeddings`
   - Civitai "Controlnet" → `controlnet`
   - Civitai "Upscaler" → `upscale_models`
   - Civitai "Hypernetwork" → `hypernetworks`
3. Build suggested path: `{expected_folder_root}/{base_model}/{filename}`
   - base_model from Civitai metadata (null → keep in folder root)

Each discrepancy entry:
```json
{
    "path": "/models/checkpoints/some_unet.safetensors",
    "filename": "some_unet.safetensors",
    "current_folder_type": "checkpoints",
    "expected_folder_type": "diffusion_models",
    "classification_source": "header",     // or "civitai"
    "header_class": "unet",
    "civitai_type": "Checkpoint",
    "base_model": "SDXL 1.0",
    "suggested_path": "/models/diffusion_models/SDXL 1.0/some_unet.safetensors",
    "hash": "abc123..."
}
```

### 1E: Move API

New endpoint: `POST /comfychair/models/move`

```json
{
    "source_path": "/models/checkpoints/some_unet.safetensors",
    "dest_path": "/models/diffusion_models/SDXL 1.0/some_unet.safetensors"
}
```

Validation:
- Source file must exist
- Source path must be in `file_cache`
- Dest path must be under a known ComfyUI model directory (from `folder_paths`)
- Dest file must NOT exist (no silent overwrite)
- No path traversal (`..` rejected)
- Create intermediate directories if needed

On success:
- `os.rename()` (or `shutil.move()` if cross-filesystem)
- Update `file_cache.path` for the old path
- Update `file_cache.folder_type` to the new ComfyUI folder type
- Return `{"ok": true, "new_path": "..."}`

### 1F: New API Endpoints

```
GET  /comfychair/models/discrepancies   → list of misplaced models
POST /comfychair/models/move            → move a model file + update DB
GET  /comfychair/models/{hash}/header   → raw safetensor header info for a file
```

---

## Part 2: App — Metadata Model Selector

### 2A: Settings Toggle + OrganizedModelCache

**Files:**
- `storage/AppSettings.kt` — add `KEY_MODEL_SELECTOR_MODE` (`"file_path"` | `"metadata"`)
- `connection/OrganizedModelCache.kt` — **new** singleton, fetches + indexes organized models

```kotlin
data class MetadataModelEntry(
    val displayName: String,       // "Illustrious XL 2.0"
    val versionName: String?,      // "v2.0"
    val baseModel: String?,        // "Illustrious", "SDXL 1.0", "Pony", etc.
    val filePath: String,          // actual path for ComfyUI (the VALUE)
    val extension: String,         // "safetensors", "pt", "gguf"
    val weight: String?,           // "fp16", "fp8", "bf16"
    val modelType: String?,        // Civitai type: "Checkpoint", "LORA", etc.
    val headerClass: String?,      // From safetensor analysis: "checkpoint", "unet", "lora"
    val trainedWords: List<String>
)
```

`OrganizedModelCache` is a `Map<filePath, MetadataModelEntry>` indexed by path for O(1) lookup.

### 2B: MetadataModelDropdown Component

**New file:** `ui/components/shared/MetadataModelDropdown.kt`

Display:
```
▼ Illustrious                    ← base model group header
   Illustrious XL 2.0              .safetensors · fp16
   NoobAI-XL v1.0                  .safetensors · bf16
▼ SDXL 1.0
   SD XL Base 1.0                  .safetensors · fp16
▼ Unknown                        ← models without base model metadata
   custom_merge_v3.safetensors
```

- Groups by `baseModel` (null → "Unknown")
- Model name on left, extension + weight badge on right
- Selected value highlighted, auto-scroll to selected item's group
- Text field shows display name when matching entry exists

### 2C: Integration into ModelDropdown

Augment existing `ModelDropdown` with optional `metadataEntries` parameter:
```kotlin
fun ModelDropdown(
    label: String,
    selectedValue: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    metadataEntries: List<MetadataModelEntry>? = null  // null = file path mode
)
```

When building entries for a dropdown:
1. Get file paths from `ModelCache` (e.g. `ModelCache.checkpoints`) — **ground truth for what's installed**
2. Cross-reference with `OrganizedModelCache` for display enrichment
3. Paths with metadata → full `MetadataModelEntry`
4. Paths without metadata → filename-only fallback entry

### 2D: Settings UI

In `ApplicationSettingsScreen.kt`, Generation card:
```
Model Selection
  ○ File Path — Show models by filesystem folder structure
  ● Metadata — Group by base model with rich display (requires helper)
```

Grey out metadata option if helper unavailable.

### 2E: Fetch on Connect

In `ConnectionManager.kt`, after `fetchServerData()`:
- If metadata mode enabled AND helper available → call `OrganizedModelCache.refresh()`
- If not available → fall back to file path mode silently

### 2F: Weight Parsing Utility

```kotlin
fun parseWeight(versionName: String?, filename: String): String? {
    val combined = "${versionName.orEmpty()} $filename".lowercase()
    return when {
        "fp8" in combined -> "fp8"
        "bf16" in combined -> "bf16"
        "fp16" in combined || "half" in combined -> "fp16"
        "fp32" in combined || "full" in combined -> "fp32"
        "q4_k" in combined || "q5_k" in combined || "q8" in combined ->
            combined.substringAfter("q").take(4).trim()
        else -> null
    }
}
```

---

## Part 3: App — Discrepancy UI

### 3A: Helper Service Extensions

Add to `ComfyChairHelperService.kt`:

```kotlin
data class ModelDiscrepancy(
    val path: String,
    val filename: String,
    val currentFolderType: String,
    val expectedFolderType: String,
    val classificationSource: String,  // "header" or "civitai"
    val headerClass: String?,
    val civitaiType: String?,
    val baseModel: String?,
    val suggestedPath: String,
    val hash: String
)

suspend fun getDiscrepancies(): List<ModelDiscrepancy>
suspend fun moveModel(sourcePath: String, destPath: String): Boolean
```

### 3B: Discrepancy Screen

New section in **Server Settings/Status** screen:

```
Model Organization
  ⚠ 3 models in unexpected locations

  ┌────────────────────────────────────────────┐
  │ some_unet_model.safetensors                │
  │ checkpoints/ → diffusion_models/SDXL 1.0/  │
  │ Detected: UNET (header analysis)           │
  │                              [Move]        │
  ├────────────────────────────────────────────┤
  │ my_lora.safetensors                        │
  │ checkpoints/ → loras/Illustrious/          │
  │ Detected: LoRA (Civitai)                   │
  │                              [Move]        │
  └────────────────────────────────────────────┘
```

Each card shows:
- Filename
- Current location → suggested location
- Classification source (header analysis vs Civitai metadata)
- **Move** button — calls `POST /comfychair/models/move`, then refreshes the list

After move:
- Update `ModelCache` (trigger `/object_info` refresh)
- Update `OrganizedModelCache`
- Show success snackbar
- ComfyUI needs restart for `/object_info` to reflect the change (show note about this)

---

## Edge Cases

1. **Helper not available** — metadata toggle grayed out, discrepancy section hidden
2. **Model on disk but not in organized models** — shown with filename-only in "Unknown" group, no discrepancy generated (need metadata to detect mismatch)
3. **Model in organized but not in ModelCache** — filtered out (not installed on this server)
4. **Cross-filesystem move** — `shutil.move()` fallback (models on NFS, local storage, etc.)
5. **Checkpoint vs UNET ambiguity** — safetensor header is authoritative. "All-in-one" UNET merges that work in checkpoint loader will classify as `unet` if they lack text encoder/VAE tensors. User can choose not to move.
6. **GGUF files** — no safetensor header. Fall back to Civitai type only.
7. **Switching modes mid-session** — selected value (file path) persists; display changes
8. **Move changes /object_info results** — ComfyUI caches folder contents. Note in UI that restart may be needed.

---

## Execution Plan

### Agent A: Helper Backend (Python)
- Task 1A: `header_reader.py` — safetensor header parser + classifier
- Task 1B: DB migration v3→v4 (new columns on file_cache)
- Task 1C: Scanner integration (store folder_type, read headers during scan)
- Task 1D: Discrepancy detection in `db.py`
- Task 1E: Move API logic
- Task 1F: New API endpoints in `api.py`

### Agent B: App Frontend (Kotlin)
- Task 2A: Settings toggle + OrganizedModelCache
- Task 2B: MetadataModelDropdown composable
- Task 2C: ModelDropdown integration
- Task 2D: Settings UI toggle
- Task 2E: Fetch on connect
- Task 2F: Weight parsing utility
- Task 3A: Helper service extensions
- Task 3B: Discrepancy UI in server settings

### Dependencies
- Agent B's discrepancy UI (3A/3B) depends on Agent A's endpoints being defined (1F)
- Agent B can build against the API contract before Agent A finishes implementation
- Both agents can run in parallel

---

## File Summary

| File | Action | Task |
|---|---|---|
| **Helper** | | |
| `header_reader.py` | **Create** | 1A |
| `db.py` | Modify | 1B, 1D |
| `scanner.py` | Modify | 1C |
| `api.py` | Modify | 1E, 1F |
| **App** | | |
| `storage/AppSettings.kt` | Modify | 2A |
| `connection/OrganizedModelCache.kt` | **Create** | 2A |
| `viewmodel/SettingsViewModel.kt` | Modify | 2A |
| `ui/components/shared/MetadataModelDropdown.kt` | **Create** | 2B |
| `ui/components/shared/ModelDropdown.kt` | Modify | 2C |
| `ui/components/config/BottomSheetConfig.kt` | Modify | 2C |
| `ui/components/config/CommonGenerationState.kt` | Modify | 2C |
| `ui/screens/ApplicationSettingsScreen.kt` | Modify | 2D |
| `connection/ConnectionManager.kt` | Modify | 2E |
| `service/ComfyChairHelperService.kt` | Modify | 3A |
| `ui/screens/HelperIntegrationComposables.kt` | Modify | 3B |
