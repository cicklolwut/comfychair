# Illustrious Model Family: Comprehensive Prompting Research

## Table of Contents

1. [Introduction](#introduction)
2. [Base Model: Illustrious XL](#base-model-illustrious-xl)
3. [NoobAI XL](#noobai-xl)
4. [WAI-ANI-Illustrious](#wai-ani-illustrious)
5. [Hassaku XL](#hassaku-xl)
6. [Other Notable Variants](#other-notable-variants)
7. [Illustrious vs Pony Diffusion](#illustrious-vs-pony-diffusion)
8. [Illustrious vs Standard SDXL](#illustrious-vs-standard-sdxl)
9. [Common Mistakes](#common-mistakes)
10. [Model-Specific System Prompts](#model-specific-system-prompts)

---

## Introduction

The Illustrious model family represents a new generation of anime-focused SDXL models trained on the complete Danbooru dataset (up to 2023-2024 depending on variant). Unlike Pony Diffusion which requires structured Danbooru tags exclusively, Illustrious models support **hybrid prompting**: both natural language descriptions AND Danbooru tags, though tags remain more precise.

**Key Philosophy**: Illustrious models aim to combine SDXL's natural language understanding with the precision of Danbooru tag-based prompting, while addressing anatomical accuracy issues (especially hands) that plague other anime models.

---

## Base Model: Illustrious XL

**Developer**: OnomaAI Research  
**Base**: Kohaku XL (itself an SDXL finetune)  
**Dataset**: Danbooru (up to 2023)  
**Versions**: v0.1 (September 2024), v1.0 (March 2025)

### Prompt Format

**Tag Format**: Danbooru-style tags, comma-separated. **Remove underscores** from Danbooru tags and **escape parentheses** with backslashes.

**Example Tag Conversion**:
- Danbooru: `lucy_(cyberpunk)`
- Illustrious: `lucy \(cyberpunk\)`

**Hybrid Prompting** (v1.0 improved this):
```
A beautiful anime girl with long blonde hair, 1girl, solo, blue eyes, 
sailor school uniform, standing outdoors, cherry blossoms, looking at viewer, 
detailed background, depth of field
```

OR pure tags:
```
1girl, solo, long hair, blonde hair, blue eyes, school uniform, serafuku, 
sailor collar, pleated skirt, standing, looking at viewer, smile, outdoors, 
cherry blossoms, detailed background, depth of field
```

### Quality Tags

Illustrious does NOT use Pony-style `score_9, score_8_up` tags. Instead:

**Positive Quality Tags**:
- `masterpiece`
- `best quality`
- `highres`
- `absurdres` (for high-resolution generations)
- `newest` (steers toward 2021-2024 art styles)
- `year 2024` (specific year targeting)
- `very awa` (aesthetic quality indicator)

**Negative Quality Tags**:
- `worst quality`
- `low quality`
- `bad quality`
- `lowres`
- `normal quality`

### Tag Ordering

Recommended order:
1. **Quality tags first**: `masterpiece, best quality, newest, absurdres, highres`
2. **Character count**: `1girl`, `2girls`, `1boy`, etc.
3. **Character name** (if applicable): `character_name \(series\)`
4. **Physical attributes**: Hair, eyes, body type
5. **Clothing**: Detailed clothing tags
6. **Pose/action**: `standing`, `sitting`, `looking at viewer`
7. **Expression**: `smile`, `blush`, etc.
8. **Environment**: `outdoors`, `indoors`, `detailed background`
9. **Art style/artist**: `artist:artist_name` or style descriptors

### Artist Tags

- **Format**: `artist:artist_name` (colon, not underscore)
- Replace underscores in artist names with spaces
- Illustrious knows thousands of artists from Danbooru
- Artist tags work but are **less reliable** than in Pony Diffusion
- Can mix multiple artists: `artist:wlop, artist:ask \(askzy\)`

### NSFW Tags

- **Safety Ratings**: `safe`, `sensitive`, `nsfw`, `explicit`
- Add `nsfw` to negative prompt to avoid NSFW content
- Add `explicit` to negative to avoid explicit content
- Illustrious v1.0 has **weaker NSFW capabilities** than v0.1 or NoobAI

### Negative Prompts

**Recommended negative prompt**:
```
worst quality, low quality, bad quality, lowres, bad anatomy, bad hands, 
watermark, signature, username, text, error, blurry, jpeg artifacts, 
cropped, sketch, monochrome (if not desired)
```

**Important**: Illustrious v1.0 greatly benefits from adding `sketch, monochrome, blurry, blurry background` to negatives.

### Resolution

**Native resolutions** (all variants):
- 768×1344, 832×1216, 896×1152, 1024×1024, 1152×896, 1216×832, 1344×768, 1536×1024
- **v1.0 claims 1536×1536 native support** but community testing shows mixed results
- Resolutions below 512×512 cause severe errors
- **Recommendation**: Use 1024×1024 base or standard SDXL resolutions, upscale with hi-res fix

### Model Settings

**Sampler**: 
- Euler, Euler A (most common)
- DDPM, DPM++ also work well
- 25-40 steps recommended

**CFG Scale**: 5-7 (higher values may over-saturate)

**VAE**: Use SDXL default VAE (usually baked into model)

**CLIP Skip**: No need to set (leave at default/1)

### Version Differences

**v0.1** (September 2024):
- Initial release, somewhat rough/unbalanced
- Better NSFW capabilities
- More variation but less consistency
- Strong community support, many finetunes built on this

**v1.0** (March 2025):
- Improved anatomy and consistency
- Better high-resolution support (claimed)
- Weaker NSFW by default
- **Closed-source/paid initially**, caused community backlash
- Better natural language understanding (claimed, but limited NLP training documented)

### Known Issues

- **Catastrophic forgetting**: Lost some SDXL concepts (e.g., "pirate ship" becomes pirate girls)
- **Scenery tag behavior**: Tends to generate landscape-only shots without character tags
- **Bias toward female characters**: Even neutral prompts generate girls unless explicitly prompted otherwise
- **Background quality**: Can be sloppy/blurry without careful prompting
- **Needs more prompt "massaging"** than some competitors (Hassaku, NoobAI)

---

## NoobAI XL

**Developer**: Laxhar Dream Lab  
**Base**: Illustrious XL v0.1  
**Dataset**: Complete Danbooru + e621 (furry content), up to October 2024  
**Versions**: eps-prediction (1.1), v-prediction (1.0)  
**Philosophy**: "Noob-friendly" with extensive documentation and expanded training

### Major Differences from Base Illustrious

1. **Dual-dataset training**: Danbooru + e621 (includes furry/anthro content)
2. **V-prediction variant**: Revolutionary lighting and rendering quality
3. **Rating prefix system**: Unique NSFW handling
4. **Better documentation**: Extensive official guides and tag lists
5. **More consistent anatomy** than base Illustrious v0.1

### Prompt Format

**Identical tag syntax to Illustrious**: Remove underscores, escape parentheses.

**Rating prefix tags** (NoobAI innovation):
- Tags can use `rating_` prefix for filtering
- Example: `rating_safe`, `rating_questionable`, `rating_explicit`
- These act as dataset filters, not quality indicators

### Quality Tags

NoobAI uses **both Illustrious-style AND custom quality tags**:

**Quality tag system** (based on image popularity with time-decay):

| Percentile | Tag |
|------------|-----|
| > 95th | `masterpiece` |
| > 85th, ≤ 95th | `best quality` |
| > 60th, ≤ 85th | `good quality` |
| > 30th, ≤ 60th | `normal quality` |
| ≤ 30th | `worst quality` |

**Date tags** (temporal steering):

| Year Range | Tag |
|------------|-----|
| 2005-2010 | `old` |
| 2011-2014 | `early` |
| 2014-2017 | `mid` |
| 2018-2020 | `recent` |
| 2021-2024 | `newest` |

**Recommended positive prefix**:
```
masterpiece, best quality, newest, absurdres, highres
```

### Tag Ordering

Same as Illustrious, but NoobAI is **more forgiving** of tag order variations.

### Artist Tags

- **Prefix required**: `artist:artist_name`
- Example: `artist:wlop`, `artist:ask \(askzy\)`
- More reliable artist reproduction than base Illustrious
- Can mix: `[artist:wlop],[artist:ciloranko],[artist:sho \(sho_lwlw\)]`

### Character Tags

**NoobAI provides CSV lookup tables**:
- [Danbooru Characters](https://huggingface.co/datasets/Laxhar/noob-wiki/blob/main/danbooru_character_webui.csv)
- [Danbooru Artists](https://huggingface.co/datasets/Laxhar/noob-wiki/blob/main/danbooru_artist_webui.csv)
- [e621 Characters](https://huggingface.co/datasets/Laxhar/noob-wiki/blob/main/e621_character_webui.csv)
- [e621 Artists](https://huggingface.co/datasets/Laxhar/noob-wiki/blob/main/e621_artist_webui.csv)

**CSV columns**:
- `trigger`: The tag to use
- `core_tags`: Supplementary tags to improve character accuracy
- `url`: Visual reference link

### NSFW Handling

**Safety rating tags**:
- Add to prompt: `general`, `sensitive`, `questionable`, `explicit`
- Add to negative: `nsfw`, `explicit` (to avoid NSFW)
- e621 dataset provides **better furry/anthro NSFW** than pure Danbooru models

**Negative prompt for SFW**:
```
nsfw, worst quality, old, early, low quality, lowres, signature, username, 
logo, bad hands, mutated hands, mammal, anthro, furry, ambiguous form, 
feral, semi-anthro
```

### Resolution

Same as Illustrious base:
- **Recommended**: 768×1344, 832×1216, 896×1152, 1024×1024, 1152×896, 1216×832, 1344×768, 1536×1024
- **Do not use below 512×512** (causes severe artifacts)

### Model Settings

**EPS-prediction version**:
- Sampler: Euler, Euler A (28 steps)
- CFG: 5-6
- Works with most SDXL samplers

**V-prediction version** (IMPORTANT):
- Sampler: **Euler** (NOT Euler A) — other samplers cause over-saturation
- CFG: 3.5-5.5
- May require LatentModifier adjustments in some UIs
- **Revolutionary lighting quality** — dramatic improvement over eps

### V-Prediction Advantages

- **Significantly better lighting**: Moody, cinematic, natural
- **Improved color balance**: Less saturated, more realistic
- **Better shadow rendering**: Depth and atmosphere
- **Trade-off**: Slightly worse hands/feet than eps version

### Known Issues

- **e621 dataset artifacts**: Can introduce furry characteristics unintentionally
- **V-pred hand quality**: Slightly worse than eps for complex hand poses
- **Complex scenes**: Still struggles with 3+ characters, can duplicate
- **Sampler sensitivity** (v-pred): Must use correct sampler or images break

### Unique Features

- **Best documentation** in the Illustrious family
- **ControlNet models** specifically trained for NoobAI
- **Active community** with extensive guides
- **Temporal control** via date tags is unique
- **Dual-dataset** (Danbooru + e621) broadest concept coverage

---

## WAI-ANI-Illustrious

**Developer**: Community creator (WAI series)  
**Base**: Illustrious XL v0.1  
**Versions**: v11, v12, v13, v15, v16 (frequent updates)  
**Philosophy**: Finetuned for enhanced visual quality and "cleanliness"

### Major Differences from Base Illustrious

1. **Enhanced default aesthetics**: Cleaner, more polished outputs
2. **Better character accuracy** out-of-box
3. **Frequent iterations**: Rapid development cycle
4. **Integrated VAE**: Pre-baked into checkpoint
5. **Simplified quality tags**: Less verbose requirements

### Prompt Format

**Identical syntax to Illustrious**: Danbooru tags, remove underscores, escape parentheses.

**WAI-specific simplification**: You can use **shorter quality prompts** without losing quality.

### Quality Tags

**Minimal recommended set**:
```
masterpiece, best quality, amazing quality
```

**WAI explicitly warns**: "Do not add too many quality and aesthetic-related tags, nor overly long negative prompts, as this will actually reduce image quality and make it more blurry."

This is **opposite** of most Illustrious models — WAI is tuned to work well with minimal prompting.

### Tag Ordering

Same as base Illustrious, but **more flexible** due to finetuning.

### Artist Tags

- Same format as Illustrious: `artist:name`
- Improved artist tag adherence compared to base model
- More consistent style transfer

### NSFW Handling

**Safety rating tags**: `general`, `sensitive`, `nsfw`, `explicit`

**Important**: "Users are expected to consciously add 'nsfw' to negative prompts to filter inappropriate content."

WAI is **NSFW-capable by default**, requires explicit negative filtering for SFW content.

### Negative Prompts

**Recommended minimal set**:
```
bad quality, worst quality, worst detail, sketch, censor
```

Again, WAI emphasizes **brevity** over extensive negatives.

### Resolution

- Same SDXL resolutions as base Illustrious
- **Recommended base**: Larger than 1024×1024
- **Hi-res fix**: 1.5× upscale, 20 steps, R-ESRGAN 4x+ Anime6B, denoise 0.35-0.5

### Model Settings

- **Steps**: 15-30 (v16), 25-40 (earlier versions)
- **CFG**: 5-7
- **Sampler**: Euler A
- **VAE**: Pre-integrated (do not load external VAE)

### Version-Specific Notes

**v12**: Highly praised, stable
**v13**: Community reported "borked" — worse than v12 with same prompts
**v15**: Improved character accuracy
**v16**: "Adjusted overall default style, enhancing visual cleanliness and improving character accuracy"

### Known Issues

- **Version instability**: Some versions perform worse than predecessors
- **Beta scheduler**: Works significantly better with beta scheduler vs. normal at low step counts
- **Natural language**: Still limited, primarily tag-based despite base NLP capability

### Unique Features

- **Low-step efficiency**: Works well at 12-15 steps with beta scheduler
- **Quality through simplicity**: Achieves high quality with minimal prompting
- **Rapid iteration**: Frequent version releases mean constant improvements
- **Character Select SAA**: Companion tool with 5000+ character database

---

## Hassaku XL

**Developer**: Hassaku (community creator)  
**Base**: Illustrious XL v0.1  
**Versions**: v1.3, v2.2, v3.4 (latest)  
**Philosophy**: "Bright and distinct anime style" with strong color vibrancy

### Major Differences from Base Illustrious

1. **Bright, vibrant aesthetic**: Saturated colors, distinct anime style
2. **Quality tag training**: Trained with quality tags from v0.2M onwards
3. **Trained on clean dataset**: Minimal text, logos, signatures, speech bubbles
4. **Better prompt adherence** than base Illustrious
5. **Excellent at high resolution**: Fewer artifacts than base model

### Prompt Format

**Standard Illustrious syntax**: Danbooru tags, remove underscores, escape parentheses.

### Quality Tags

Hassaku **requires quality tags** (unlike base v0.1):

**Positive**:
```
masterpiece, best quality, newest
```

**Negative** (REQUIRED):
```
worst quality, low quality
```

OR

```
worst quality, bad quality
```

Later versions (v2.0+) specifically trained on these quality tags, so they're **essential** for good results.

### Tag Ordering

Same as Illustrious base — quality tags first.

### Artist Tags

- Format: `artist:name`
- **Excellent artist tag application** without interference from model's base style
- One of the best Illustrious derivatives for artist style mimicry

### NSFW Handling

- Standard Illustrious safety tags: `safe`, `sensitive`, `nsfw`, `explicit`
- No special NSFW handling — works like base model

### Negative Prompts

**Recommended** (from model page):
```
worst quality, low quality, signature
```

**Note**: Model was trained on clean data without signatures, so `signature` in negative is important.

### Resolution

- Same SDXL resolutions as base
- **Recommended**: 832×1216 for portrait
- **Excellent performance** at higher resolutions (1248×1824) in community testing

### Model Settings

- **Steps**: 20-30
- **CFG**: 7
- **Sampler**: Euler A, 28 steps commonly used
- **VAE**: SDXL default

### Known Issues

- **Very saturated colors**: May need desaturation in post-processing for some styles
- **Strong base aesthetic**: Can be hard to override completely with artist tags
- **Bright bias**: Low-light/dark scenes can be challenging

### Unique Features

- **Best high-resolution performance** among Illustrious derivatives (community consensus)
- **Minimal artifact generation**: Cleaner outputs than base Illustrious
- **Artist style control**: Excellent for style transfer without base model interference
- **Bright anime aesthetic**: If you want vibrant, punchy colors, this is the top choice

---

## Other Notable Variants

### Obsession (Illustrious XL v-pred)

**Base**: Illustrious XL  
**Type**: V-prediction finetune

- Similar to NoobAI v-pred but different aesthetic
- Better lighting than eps models
- Slightly worse finger quality than NoobAI v-pred
- Less emphasis on furry content (no e621 dataset)

### Animagine XL 4.0

**Not technically Illustrious-based**, but often compared:
- Separate SDXL finetune on Danbooru
- Older architecture, less prompt adherence than Illustrious
- More surreal/artistic outputs
- Lower anatomy accuracy
- Still popular for certain aesthetics

### Personal Merges

Countless community merges exist on CivitAI combining:
- Different Illustrious versions
- Hassaku + NoobAI + WAI combinations
- Style-specific blends
- NSFW-focused merges

**Note**: Most merges maintain Illustrious prompting conventions.

---

## Illustrious vs Pony Diffusion

Users frequently confuse these models. Here are the critical differences:

### Dataset Differences

**Pony Diffusion**:
- Trained on Danbooru + Derpibooru (MLP) + e621 + custom curation
- Heavy curation and filtering
- Includes Western animation styles

**Illustrious**:
- Trained on complete Danbooru (less filtered)
- Later variants (NoobAI) added e621
- Focuses purely on anime/manga styles

### Prompting Philosophy

**Pony Diffusion**: **Pure tag-based only**
- Requires structured Danbooru tags exclusively
- Natural language mostly fails
- Specific tag vocabulary (some differs from standard Danbooru)

**Illustrious**: **Hybrid prompting**
- Supports both natural language AND tags
- Tags are more precise, NL is more flexible
- Closer to SDXL's original behavior

### Quality Tags

**Pony Diffusion**:
```
score_9, score_8_up, score_7_up, source_anime
```
Uses numerical scoring system from e621.

**Illustrious**:
```
masterpiece, best quality, newest, absurdres
```
Uses quality descriptors, not numerical scores.

### Anatomy

**Illustrious**: **Better hands and anatomy by default**
- Less negative prompting needed for hands
- More consistent joint placement
- Fewer regenerations needed

**Pony Diffusion**: **Requires extensive negative prompts for anatomy**
- Common to need 10+ anatomy-related negative tags
- More generations to get clean hands

### Style Range

**Pony Diffusion**: **Broader style range**
- Vintage anime (80s/90s)
- Western cartoon styles
- Pixel art
- Various illustration styles

**Illustrious**: **Narrower, more cohesive style**
- Modern anime (2010s-2020s)
- Consistent rendering approach
- Less style variety

### Color and Rendering

**Pony Diffusion**: Bold, saturated colors
**Illustrious**: More balanced, naturalistic color (except Hassaku)

### Consistency

**Pony Diffusion**: High variation between seeds
**Illustrious**: More consistent outputs per prompt

### LoRA Ecosystem

**Pony Diffusion**: **Massive LoRA library** (thousands)
**Illustrious**: Growing but smaller (hundreds)

**Note**: Pony and Illustrious LoRAs are **NOT interchangeable**.

### NSFW Capability

**Pony Diffusion**: Excellent NSFW, extensive tag vocabulary
**Illustrious base**: Weaker NSFW, but NoobAI/WAI/Hassaku improve this

### Character Knowledge

**Illustrious**: **Better character accuracy**
- More up-to-date (2023-2024 data vs. Pony's 2023 cutoff)
- More complete Danbooru coverage
- NoobAI includes e621 for furry characters

**Pony Diffusion**: Strong character knowledge but less current

### When to Use Pony vs Illustrious

**Choose Pony when**:
- You need specific character LoRAs (larger ecosystem)
- You want precise style control with tags
- You're comfortable with pure tag prompting
- You need broad style range (vintage, western, etc.)

**Choose Illustrious when**:
- You want better default anatomy
- You prefer natural language prompting
- You need consistent outputs
- You want modern anime aesthetics
- You want more up-to-date character knowledge

---

## Illustrious vs Standard SDXL

Illustrious is a **heavily finetuned SDXL model**, not a ground-up architecture.

### What Illustrious Retains from SDXL

- Same architecture (U-Net, VAE, CLIP encoders)
- Same resolution requirements
- Same hardware requirements (VRAM, speed)
- Some natural language understanding (inherited from CLIP training)
- Basic composition and lighting knowledge

### What Illustrious Changes/Loses

**Gains**:
- Danbooru tag vocabulary (10,000+ tags)
- Anime/manga-specific knowledge
- Character accuracy (thousands of anime characters)
- Artist style knowledge (thousands of artists)
- Better anime anatomy (hands, proportions)

**Losses** (catastrophic forgetting):
- Realistic human faces
- Photorealistic rendering
- Real-world objects (cars, buildings, etc.)
- Western celebrities/people
- General "real world" knowledge

**Example**: Prompt "pirate ship" in SDXL gives actual ships. In Illustrious, it gives pirate girls (because "pirate" is an anime costume tag).

### Prompting Differences

**Standard SDXL**:
- Pure natural language
- No tag structure required
- Artist names as natural text: "in the style of Greg Rutkowski"
- Quality via descriptors: "highly detailed, 8k, photorealistic"

**Illustrious**:
- Hybrid: tags + natural language
- Tag structure is more precise: `artist:greg_rutkowski`
- Quality via tags: `masterpiece, best quality, absurdres`
- Character counts required: `1girl`, `2girls`, `1boy`

### Concept Coverage

**Standard SDXL**: Broad coverage of real-world concepts
**Illustrious**: Deep coverage of anime/manga concepts, poor real-world coverage

### Style

**Standard SDXL**: Leans photorealistic
**Illustrious**: Locked into anime/manga aesthetic

---

## Common Mistakes

### 1. Using Pony-style score tags

**Wrong**:
```
score_9, score_8_up, score_7_up, 1girl, blonde hair...
```

**Right**:
```
masterpiece, best quality, newest, absurdres, 1girl, blonde hair...
```

Illustrious doesn't understand Pony's `score_X` tags.

### 2. Keeping underscores in character tags

**Wrong**:
```
lucy_(cyberpunk), klee_(genshin_impact)
```

**Right**:
```
lucy \(cyberpunk\), klee \(genshin impact\)
```

Remove underscores, escape parentheses with backslashes.

### 3. Forgetting character count tags

**Wrong**:
```
masterpiece, blonde hair, blue eyes, school uniform, outdoors
```

**Right**:
```
masterpiece, best quality, 1girl, solo, blonde hair, blue eyes, school uniform, outdoors
```

Without `1girl`, model may generate scenery-only or multiple characters.

### 4. Over-prompting quality tags (WAI-specific)

**Wrong** (for WAI):
```
masterpiece, best quality, amazing quality, very awa, high score, great score, 
perfect, flawless, ultra detailed, 8k, absurdres, highres...
```

**Right** (for WAI):
```
masterpiece, best quality, amazing quality
```

WAI specifically warns too many quality tags reduce quality.

### 5. Using wrong sampler for v-prediction models

**Wrong** (NoobAI v-pred):
```
Sampler: DPM++ 2M Karras
```

**Right**:
```
Sampler: Euler (NOT Euler A)
```

V-pred models are sampler-sensitive. Wrong sampler = oversaturated/broken images.

### 6. Artist tags without prefix

**Wrong**:
```
wlop, ciloranko, ask (askzy)
```

**Right**:
```
artist:wlop, artist:ciloranko, artist:ask \(askzy\)
```

NoobAI and some Illustrious variants require `artist:` prefix.

### 7. Expecting SDXL concepts to work

**Wrong**:
```
masterpiece, best quality, 1girl, standing next to a Tesla Model 3, urban parking lot
```

**Result**: Anime girl, vague car-like shapes, probably broken

**Why**: Illustrious has catastrophic forgetting of real-world objects. Prompt anime-appropriate elements instead.

### 8. Not using negative prompts

**Wrong**:
```
Positive: masterpiece, best quality, 1girl...
Negative: (empty)
```

**Right**:
```
Positive: masterpiece, best quality, 1girl...
Negative: worst quality, low quality, bad anatomy, bad hands, signature, watermark
```

Illustrious benefits significantly from negative prompts to filter training noise.

### 9. Using too low resolution

**Wrong**:
```
Resolution: 512×512
```

**Result**: Severe artifacts, duplications, broken anatomy

**Right**:
```
Resolution: 1024×1024 (minimum), or standard SDXL resolutions
```

Illustrious is tuned for SDXL resolutions, breaks below 512×512.

### 10. Mixing incompatible LoRAs

**Wrong**:
```
Base: Illustrious XL
LoRA: Pony-trained character LoRA
```

**Result**: Degraded/broken output

**Why**: Pony and Illustrious LoRAs are trained on different models and aren't compatible. Always check LoRA's base model.

### 11. Ignoring model-specific requirements

**Wrong**: Using Hassaku without quality tags in negative
**Wrong**: Using NoobAI v-pred with Euler A sampler
**Wrong**: Using WAI with extensive negative prompts

Each variant has specific quirks — read the model card!

### 12. Expecting perfect NLP

**Wrong**:
```
A girl dressed in a Victorian-era maid costume cleaning a luxurious 
living room with an upright Dyson vacuum cleaner, afternoon sunlight 
streaming through tall windows
```

**Result**: Sloppy vacuum, weird hoses, "Victorian" misinterpreted

**Why**: Illustrious has limited NLP training. Use tags for precision:

**Right**:
```
1girl, maid, maid headdress, maid apron, frills, black dress, white apron,
holding vacuum cleaner, indoors, living room, detailed background, sunlight, 
window, masterpiece, best quality
```

Tags are more reliable than full natural language descriptions.

---

## Model-Specific System Prompts

The following are suggested LLM system prompts for each model variant. These tell an LLM how to convert user descriptions into optimal prompts.

### Illustrious XL Base (v0.1 or v1.0)

```
You are an expert at creating prompts for Illustrious XL, an anime-focused SDXL model.

CORE PRINCIPLES:
- Use Danbooru tags primarily, natural language secondarily
- Remove underscores from tags, escape parentheses: lucy_(cyberpunk) → lucy \(cyberpunk\)
- Always include quality tags first
- Always specify character count (1girl, 2girls, 1boy, etc.)

PROMPT STRUCTURE:
1. Quality tags: masterpiece, best quality, newest, absurdres, highres
2. Character count: 1girl, solo (or 2girls, 1boy, etc.)
3. Character name (if any): character_name \(series\)
4. Physical attributes: hair (color, length, style), eyes, body type
5. Clothing: detailed tags (serafuku, blazer, pleated_skirt, etc.)
6. Pose/action: standing, sitting, looking_at_viewer, from_above
7. Expression: smile, blush, closed_eyes
8. Environment: outdoors, indoors, detailed_background, cherry_blossoms
9. Style: artist:artist_name (optional)

NEGATIVE PROMPT TEMPLATE:
worst quality, low quality, bad quality, lowres, bad anatomy, bad hands, 
watermark, signature, username, text, error, blurry, jpeg artifacts, 
sketch, monochrome

CRITICAL RULES:
- NO score_9, score_8_up tags (that's Pony, not Illustrious)
- NO underscores in tags unless part of the tag name
- ALWAYS escape parentheses: \(name\)
- Artist tags optional but can help: artist:wlop, artist:ask \(askzy\)
- For NSFW avoidance, add "nsfw, explicit" to negative
- Use absurdres for high-resolution generations

EXAMPLE:
User: "A smiling blonde anime girl in a school uniform outdoors"
Your output:
Positive: masterpiece, best quality, newest, absurdres, 1girl, solo, long hair, blonde hair, blue eyes, school uniform, serafuku, sailor collar, pleated skirt, standing, looking at viewer, smile, outdoors, cherry blossoms, detailed background
Negative: worst quality, low quality, bad anatomy, bad hands, watermark, signature, text, blurry
```

### NoobAI XL (eps-prediction)

```
You are an expert at creating prompts for NoobAI XL, an Illustrious-based model with expanded Danbooru + e621 training.

CORE PRINCIPLES:
- Use Danbooru tags (remove underscores, escape parentheses)
- Include quality AND date tags
- Artist tags REQUIRE "artist:" prefix
- Supports furry/anthro content via e621 dataset

PROMPT STRUCTURE:
1. Quality + date tags: masterpiece, best quality, newest, absurdres, highres
2. Character count: 1girl, solo
3. Character name (if any): character_name \(series\)
4. Core character tags (from NoobAI CSV lookup if available)
5. Physical attributes: hair, eyes, body
6. Clothing: detailed Danbooru tags
7. Pose/action: standing, sitting, etc.
8. Expression: smile, blush
9. Environment: outdoors, detailed_background
10. Artist: artist:artist_name (with prefix!)

QUALITY TAGS (use appropriate tier):
- masterpiece (>95th percentile popularity)
- best quality (>85th percentile)
- good quality (>60th percentile)
- Avoid: normal quality, worst quality

DATE TAGS (for style control):
- newest (2021-2024 art)
- recent (2018-2020)
- mid (2014-2017)
- early (2011-2014)
- old (2005-2010)

NEGATIVE PROMPT TEMPLATE:
nsfw, worst quality, old, early, low quality, lowres, signature, username, 
logo, bad hands, mutated hands, mammal, anthro, furry (if not desired)

ARTIST TAG FORMAT:
- MUST use prefix: artist:wlop
- Can stack: [artist:wlop],[artist:ciloranko],[artist:ask \(askzy\)]

MODEL SETTINGS:
- Sampler: Euler or Euler A
- Steps: 28
- CFG: 5-6

EXAMPLE:
User: "A cute anime girl by artist Wlop, modern style"
Your output:
Positive: masterpiece, best quality, newest, absurdres, highres, 1girl, solo, long hair, [artist:wlop], detailed background
Negative: nsfw, worst quality, old, low quality, lowres, bad hands, signature
```

### NoobAI XL (v-prediction)

```
You are an expert at creating prompts for NoobAI XL v-prediction, a revolutionary lighting-focused anime model.

CORE PRINCIPLES:
- Same tag format as NoobAI eps, but CRITICAL sampler requirement
- V-pred has dramatically better lighting than eps models
- Slightly worse hands/feet than eps version
- MUST use specific sampler settings

PROMPT STRUCTURE:
(Same as NoobAI eps above)

CRITICAL SAMPLER SETTINGS:
- Sampler: Euler (NOT Euler A, NOT DPM++, NOT others)
- CFG: 3.5-5.5 (lower than eps)
- Wrong sampler = oversaturated/broken images

NEGATIVE PROMPT TEMPLATE:
nsfw, worst quality, old, low quality, lowres, bad hands, signature, 
username, logo

LIGHTING STRENGTH:
V-pred excels at dramatic lighting, moody atmospheres, and natural shadows.
When user wants cinematic/dramatic lighting, this model is ideal.

EXAMPLE:
User: "Dramatic lighting, girl in shadows"
Your output:
Positive: masterpiece, best quality, newest, absurdres, 1girl, solo, dramatic lighting, dark, shadows, cinematic lighting, detailed background
Negative: nsfw, worst quality, low quality, lowres, bad hands
Settings: Euler sampler, CFG 4.0, 28 steps
```

### WAI-ANI-Illustrious

```
You are an expert at creating prompts for WAI-illustrious-SDXL, a finetuned Illustrious model optimized for quality with minimal prompting.

CORE PRINCIPLES:
- LESS IS MORE with WAI
- Do NOT over-prompt quality tags
- Do NOT use extensive negative prompts
- Model is tuned for cleanliness by default

PROMPT STRUCTURE (MINIMAL):
1. Quality (SHORT): masterpiece, best quality, amazing quality
2. Character: 1girl, solo
3. Character name (if any)
4. Attributes: hair, eyes, clothing
5. Pose/environment: standing, outdoors, etc.
6. (Optional) Artist: artist:name

NEGATIVE PROMPT (MINIMAL):
bad quality, worst quality, worst detail, sketch, censor

CRITICAL RULES:
- DO NOT use long lists of quality tags
- DO NOT use long negative prompts
- Model creator explicitly warns: "too many quality tags reduce quality and make it more blurry"
- Keep prompts concise

NSFW HANDLING:
- Add "nsfw" to negative for SFW content
- Safety tags: general, sensitive, nsfw, explicit

MODEL SETTINGS:
- Steps: 15-30 (WAI works at low steps)
- CFG: 5-7
- Sampler: Euler A
- Beta scheduler works better than normal at low steps

EXAMPLE:
User: "High quality detailed anime girl, perfect face, flawless skin, 8k, ultra detailed..."
Your output (corrected):
Positive: masterpiece, best quality, amazing quality, 1girl, solo, detailed
Negative: bad quality, worst quality, sketch

NOTE: Resist user's urge to over-prompt. WAI's strength is simplicity.
```

### Hassaku XL

```
You are an expert at creating prompts for Hassaku XL, a bright and vibrant anime model with strong color saturation.

CORE PRINCIPLES:
- Hassaku REQUIRES quality tags (unlike base Illustrious v0.1)
- Trained on clean data (minimal signatures/logos)
- Excels at bright, saturated anime aesthetics
- Excellent high-resolution performance

PROMPT STRUCTURE:
1. Quality tags (REQUIRED): masterpiece, best quality, newest
2. Character count: 1girl, solo
3. Character/attributes: standard Danbooru tags
4. Pose/action: standing, sitting, etc.
5. Environment: outdoors, detailed_background
6. Artist: artist:name (Hassaku has excellent artist tag support)

NEGATIVE PROMPT (REQUIRED):
worst quality, low quality, signature

OR

worst quality, bad quality, signature

CRITICAL RULES:
- Quality tags are REQUIRED (trained from v0.2M onwards)
- "signature" in negative is important (trained on clean data)
- Expect bright, saturated colors (may need desaturation in post)
- Excellent for artist style mimicry

MODEL SETTINGS:
- Steps: 20-30
- CFG: 7
- Sampler: Euler A (28 steps common)
- Excellent at high resolutions (1248×1824)

AESTHETIC:
Hassaku produces vibrant, punchy anime colors. If user wants pastel/muted tones, 
consider suggesting a different model or post-processing.

EXAMPLE:
User: "Vibrant anime girl by artist Wlop"
Your output:
Positive: masterpiece, best quality, newest, 1girl, solo, long hair, artist:wlop, vibrant colors, detailed background
Negative: worst quality, low quality, signature
```

### General Conversion Tips

When converting user descriptions to model-specific prompts:

1. **Identify key elements**: Subject, action, setting, style
2. **Convert to Danbooru tags**: Use tag databases when possible
3. **Add quality tags** appropriate to model
4. **Add character count** (1girl, 2girls, etc.)
5. **Format special tags** (artist:name, character \(series\))
6. **Include appropriate negatives**
7. **Note model-specific settings** if critical (v-pred sampler, etc.)

**Example conversion**:

User input: "A beautiful sorceress with flowing purple hair casting a spell in a magical forest, fantasy art style by Yoshitaka Amano"

Illustrious XL output:
```
Positive: masterpiece, best quality, newest, absurdres, 1girl, solo, long hair, purple hair, flowing hair, wizard, robe, casting spell, magic circle, glowing, forest, fantasy, detailed background, artist:yoshitaka_amano
Negative: worst quality, low quality, bad anatomy, bad hands, signature, watermark
```

NoobAI XL output:
```
Positive: masterpiece, best quality, newest, absurdres, highres, 1girl, solo, long hair, purple hair, wizard, magic, casting spell, magic circle, glowing, forest, fantasy, detailed background, [artist:yoshitaka_amano]
Negative: nsfw, worst quality, old, low quality, lowres, bad hands, signature
```

WAI output:
```
Positive: masterpiece, best quality, amazing quality, 1girl, long hair, purple hair, wizard, magic, forest, artist:yoshitaka_amano
Negative: bad quality, worst quality, sketch
```

---

## Conclusion

The Illustrious model family represents a significant evolution in anime-focused SDXL models, offering hybrid prompting (tags + natural language), better anatomy than predecessors, and a rapidly growing ecosystem.

**Key Takeaways**:

1. **Base Illustrious**: Good foundation, hybrid prompting, better hands than Pony
2. **NoobAI**: Best documentation, v-pred lighting breakthrough, dual dataset (Danbooru + e621)
3. **WAI**: Quality through simplicity, minimal prompting, frequent updates
4. **Hassaku**: Bright vibrant aesthetic, excellent artist tags, high-res performance

**Model Selection Guide**:
- **General use**: NoobAI XL eps or Illustrious XL v1.0
- **Dramatic lighting**: NoobAI XL v-pred or Obsession v-pred
- **Simplicity**: WAI-illustrious
- **Vibrant colors**: Hassaku XL
- **Best documentation**: NoobAI XL
- **Furry/anthro**: NoobAI XL (has e621 dataset)

**Universal Prompting Tips**:
- Always include quality tags first
- Always specify character count (1girl, 2girls, etc.)
- Remove underscores from Danbooru tags
- Escape parentheses in character names: \(series\)
- Use detailed Danbooru tags for precision
- Natural language works but is less precise
- Artist tags help but require proper format
- Read model-specific documentation — variants have quirks!

**vs Pony Diffusion**:
- Illustrious: Hybrid prompting, better anatomy, modern anime
- Pony: Pure tags, broader styles, larger LoRA ecosystem

**vs Standard SDXL**:
- Illustrious: Anime-locked, tag-based, catastrophic forgetting of real-world concepts
- SDXL: Photorealistic, natural language, broad concept coverage

The Illustrious family is rapidly evolving. Expect more variants, better documentation, and continued community innovation. When in doubt, start with NoobAI XL eps (best docs) or Hassaku (easiest to get good results).
