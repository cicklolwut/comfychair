# Stable Diffusion Model Prompting Research

A practical guide for LLM-based prompt enhancement across different model families.

---

## 1. SDXL (Stable Diffusion XL)

### Prompting Style
**Natural language sentences preferred.** SDXL understands narrative descriptions better than keyword spam.

- Use clear, descriptive sentences rather than comma-separated tags
- Structure prompts like: Subject → Detailed Imagery → Environment → Mood → Style → Execution
- Word order matters: Most important elements should come first

### Prompt Anatomy (6-Part Structure)

1. **Subject**: The core focus (character, object, scene, action, emotion, position)
2. **Detailed Imagery**: Depth and nuance (clothing, expression, color, texture, proportions, perspective)
3. **Environment**: Setting the stage (indoor/outdoor, landscape, weather, time of day, architecture)
4. **Mood/Atmosphere**: Emotional tone (energy, tension/serenity, warmth/coldness)
5. **Artistic Style**: Visual genre (anime, photographic, comic book, fantasy art, watercolor, pixel art)
6. **Style Execution**: Tools and techniques (rendering engine, camera settings, materials, lighting, color types)

### Quality Tags That Work
- **Photography specifics**: "shot on [camera model], [lens], [settings]" (e.g., "shot on Canon 5D Mark IV, 24-70mm at 35mm, f/2.8")
- **Medium descriptors**: "oil painting", "digital art", "watercolor", "concept art"
- **Lighting**: "cinematic lighting", "soft diffused light", "golden hour", "studio lighting"
- **Composition**: "rule of thirds", "symmetrical composition", "aerial view"

### Things to Avoid
❌ **Don't use SD 1.5 keywords**: "masterpiece", "best quality", "8k", "UHD", "hyper-realistic"
❌ **Don't overload negative prompts**: SDXL works better with minimal negatives
❌ **Don't use excessive weights**: SDXL is sensitive to prompt structure, not emphasis syntax
❌ **Avoid keyword spam**: Natural sentences work better than `tag1, tag2, tag3`

### Model-Specific Quirks
- **Narrative over keywords**: "A bustling marketplace at sunset" beats "marketplace, sunset, busy, people, stalls"
- **Negative prompts should be minimal**: Focus on describing what you want, not what you don't
- **Style selection**: SDXL has 90+ built-in styles (photographic, cartoon, line art, etc.) - specify the style explicitly
- **Resolution flexibility**: SDXL handles various aspect ratios well (1:1, 16:9, 9:16, 4:3, 21:9)

### Example Prompt Structure
```
Subject: A bustling futuristic city filled with towering skyscrapers.
Detailed Imagery: The skyscrapers have sleek, metallic surfaces and neon accents.
Environment: Cars zoom between the buildings.
Mood: The atmosphere is electric and full of innovation and excitement.
Style: Created in Neon Punk style.
Execution: Utilizing vibrant neon colors and sharp contrasts to highlight the futuristic theme.
```

---

## 2. Flux (Black Forest Labs)

### Prompting Style
**Pure natural language.** Flux understands human language better than any other model.

- Write prompts like you're describing a scene to another person
- No need for special syntax, tags, or formatting
- Complex JSON structures supported for advanced control (but optional)

### Prompt Structure (Subject + Action + Style + Context)

- **Subject**: Main focus (person, object, character)
- **Action**: What the subject is doing or their pose
- **Style**: Artistic approach, medium, or aesthetic
- **Context**: Setting, lighting, time, mood, atmospheric conditions

### Quality Enhancement
- **Camera/lens simulation**: "shot on [camera], [lens], [settings]" (e.g., "shot on Hasselblad X2D, 80mm lens, f/2.8")
- **Era-specific styles**: "2000s digicam style", "80s vintage photo", "shot on Kodak Portra 400"
- **Natural descriptors**: "professional studio shot", "editorial raw portrait", "cinematic harsh flash lighting"

### Things to Avoid
❌ **No negative prompts supported**: Flux models don't use negative prompts at all
❌ **Don't use keyword tags**: Natural language only
❌ **Avoid SD-style syntax**: No emphasis weights like `(keyword:1.2)` or `[[nested]]`

### Model-Specific Quirks
- **Text rendering capability**: Flux excels at rendering readable text - use quotation marks: *"The text 'OPEN' appears in red neon letters"*
- **HEX color support**: Can use precise colors like "color #FF5733" or "hex #0047AB"
- **Multi-language prompting**: Understands prompts in multiple languages (French, German, Thai, Japanese, etc.)
- **JSON structured prompts**: Supports complex JSON for precise control (but natural language works just as well)
- **Gradient support**: Can create color gradients - "gradient from #02eb3c to #edfa3c"

### Example Prompt
```
Dog wrapped in white towel after bath, photographed with direct flash and high exposure, 
fur wet details sharply visible, editorial raw portrait, cinematic harsh flash lighting, 
intimate humorous documentary style
```

### Advanced Features
- **Typography**: Excellent for posters, magazine covers, product ads with text
- **Multi-reference editing**: Can combine up to 8 reference images (fashion, design composites)
- **Prompt upsampling**: Built-in feature to automatically enhance simple prompts

---

## 3. Illustrious / NoobAI (Anime-Focused)

### Prompting Style
**Danbooru-style booru tags.** Comma-separated tags in a specific order.

- Use underscores for multi-word tags: `blue_eyes`, `long_hair`, `school_uniform`
- Order matters: General tags → Character → Style → Quality → Meta
- Mix of character features, clothing, pose, background, and quality tags

### Tag Structure (Recommended Order)

1. **Quality/Rating tags** (often first): `masterpiece`, `best quality`, `absurdres`, `highres`
2. **Character count**: `1girl`, `2boys`, `multiple_girls`
3. **Character features**: `blue_eyes`, `long_hair`, `blonde_hair`, `large_breasts`
4. **Clothing**: `school_uniform`, `white_shirt`, `pleated_skirt`
5. **Pose/Action**: `sitting`, `looking_at_viewer`, `smile`, `hands_on_hips`
6. **Background/Setting**: `indoors`, `classroom`, `cherry_blossoms`, `sky`
7. **Style/Medium**: `anime_style`, `manga_style`, `official_art`

### Quality Tags That Work
- `masterpiece`, `best quality`, `absurdres`, `highres`
- `official_art`, `production_art`, `anime_screencap`
- `detailed`, `intricate`, `highly_detailed`
- `vibrant_colors`, `dynamic_pose`, `cinematic_lighting`

### Things to Avoid
❌ **Natural language sentences**: Illustrious expects tags, not prose
❌ **Missing underscores**: Use `blue_eyes` not "blue eyes"
❌ **Western art terms**: Stick to booru conventions (anime/manga terminology)
❌ **Vague quality terms**: Be specific with character features and clothing

### Model-Specific Quirks
- **Booru tag database**: Trained on Danbooru-style annotations
- **Anatomy focus**: Good at anime-style anatomy and proportions
- **Character consistency**: Works well with character LoRAs using booru tags
- **NSFW capability**: Uncensored for NSFW content (unlike base SDXL)
- **Artist tags**: Can reference specific anime artists (`artist:wlop`, `artist:makoto_shinkai`)

### Example Prompt
```
masterpiece, best quality, absurdres, 
1girl, blue_eyes, long_silver_hair, twin_braids, large_breasts,
mage_robe, blue_robes, staff, wizard_hat, 
standing, casting_spell, magic_circle, glowing_hands,
fantasy_background, magical_library, floating_books, arcane_symbols,
detailed, vibrant_colors, dramatic_lighting
```

### Negative Prompt Example
```
lowres, bad anatomy, bad hands, text, error, missing fingers, extra digit, 
fewer digits, cropped, worst quality, low quality, normal quality, jpeg artifacts, 
signature, watermark, username, blurry
```

---

## 4. Pony Diffusion (Score-Based Prompting)

### Prompting Style
**Score tags + Danbooru tags + Natural language mix.**

- **MUST start with score tags**: `score_9, score_8_up, score_7_up, score_6_up, score_5_up, score_4_up`
- Then add source tags (optional): `source_anime`, `source_furry`, `source_pony`, `source_cartoon`
- Then add rating (optional): `rating_safe`, `rating_questionable`, `rating_explicit`
- Follow with standard booru-style tags and/or natural descriptions

### The Score System (Critical)

**The full string is required**: `score_9, score_8_up, score_7_up, score_6_up, score_5_up, score_4_up`

This is NOT optional - the model was trained with this exact format due to a training quirk.

- `score_9` = highest quality images (90-100% quality)
- `score_8_up` = includes 80%+ quality
- Can use shorter versions for looser quality control: `score_9, score_8_up, score_7_up` (70%+ quality)

**Why?** The model learned to associate the entire string with "good looking images" rather than individual components (Clever Hans effect during training).

### Source Tags (Dataset Filters)
- `source_anime` - pulls from anime dataset
- `source_furry` - pulls from furry/anthro dataset
- `source_pony` - pulls from My Little Pony dataset
- `source_cartoon` - pulls from cartoon/western animation dataset

Use in **negative prompt** to avoid unwanted styles (e.g., negative `source_pony` if character keeps becoming a pony).

### Rating Tags
- `rating_safe` - SFW content
- `rating_questionable` - Suggestive content
- `rating_explicit` - NSFW content

### Quality Tags That Work
- Score tags (mandatory)
- Standard booru quality tags: `masterpiece`, `best quality`, `highres`
- Artist references work well

### Things to Avoid
❌ **Omitting the score tags**: Will produce poor quality results
❌ **Using only `score_9`**: Won't work as well - use the full string
❌ **Score tags in negative**: Less effective (only goes down to score_4, not lower quality ranges)
❌ **Wrong score syntax**: Must be exactly `score_9, score_8_up, score_7_up...` with underscores and commas

### Model-Specific Quirks
- **Training mistake became feature**: The verbose score format is due to a Clever Hans effect
- **Source filtering powerful**: Can strongly influence output style
- **NSFW-friendly**: Designed for furry/pony/anime communities
- **Anime bias override**: Use `source_cartoon, source_furry, source_pony` in negative for more anime results
- **LoRA compatibility**: When using LoRAs, start with author's recommended settings

### Example Prompt
```
score_9, score_8_up, score_7_up, score_6_up, score_5_up, score_4_up,
source_anime, rating_safe,
1girl, blue_hair, long_hair, pointy_ears, mage_outfit, wizard_hat,
casting_spell, glowing_magic, library_background, books_floating,
masterpiece, best quality, detailed
```

### Tricks for Anime Style
If output is too "western" or "furry":
- Add to **negative**: `source_cartoon, source_furry, source_pony, sketch, painting, monochrome`
- Use shorter score string: `score_9, score_6_up, score_5_up, score_4_up` (skips some western-biased datasets)

---

## 5. Chroma (Flux-Based, Uncensored)

### Prompting Style
**Natural language (Flux-style) with optional aesthetic tags.**

- Based on Flux.1-Schnell architecture (8.9B parameters)
- Understands plain English / natural language
- Supports negative prompts (unlike base Flux)
- Similar to Flux prompting, but with Chroma-specific enhancements

### The "Aesthetic" Tag System

**Format**: `aesthetic [number]` (6-10 scale)

- `aesthetic 6` - Amateur style, flat/pale colors
- `aesthetic 7-8` - Balanced quality
- `aesthetic 9-10` - Professional artist style, high contrast, vibrant colors

**When to use**: Primarily for stylized/artistic images. Skip for realism unless quality is poor.

### Realism Prompts (Stop Using SD 1.5 Keywords!)

❌ **Don't use**: `hyper-realistic`, `8k`, `UHD`, `photorealistic`, `masterpiece`

✅ **Do use**:
1. **Source context**: "Posted on Reddit", "Instagram photo", "Dashboard cam", "Amateur photo"
2. **Lighting specifics**: "Hard flash", "Natural morning light", "Soft golden light"
3. **Style descriptors**: "Candid amateur photograph", "Retro snapshot", "2010s-era photograph"
4. **Medium**: "digital_media_(artwork)", "retro_snapshot"

### Stylized/Artistic Prompts

Define the art direction clearly:
1. **Genre**: Concept art, comic panel, surrealism, dark fantasy
2. **Medium**: Oil painting, watercolor, pixel art, digital painting
3. **Texture**: "Rough brush strokes", "bold ink lines", "painterly"
4. **Mood**: `aesthetic [number]`, style-specific terms

### Quality Enhancement
- `painterly`, `digital_media_(artwork)`, `absurd_res` for artistic
- Context/source for realism: "candid", "amateur", "posted on [platform]"
- Lighting descriptions: critical for both realism and artistic

### Things to Avoid
❌ **Verbose prompts**: Short and structured beats long rambling
❌ **SD 1.5 keywords**: No "8k", "hyper-realistic", "masterpiece" for realism
❌ **Confusing atmosphere descriptions**: "aristocratic atmosphere" → too vague
❌ **Over-reliance on aesthetic tag**: Use only when needed for quality boost

### Model-Specific Quirks
- **Fully uncensored**: No NSFW/gore restrictions
- **Apache 2.0 license**: Free commercial use
- **Strong prompt adherence**: Less random than SDXL
- **Negative prompts work**: Unlike base Flux (use sparingly)
- **Flux LoRA compatibility**: Some Flux LoRAs work with Chroma
- **Aesthetic tag is optional**: Only use if quality issues arise

### Sampler/Scheduler Recommendations
- **Deis_2M – Beta57**: Improved anatomy and clarity (recommended)
- **Euler – Normal/Beta**: Safe versatile choice
- **DPM++ 2M – sgm_uniform**: Good speed/quality balance
- **CFG**: 3.0-4.0 (higher = stricter prompt following but risk of "burning")
- **Steps**: 25-35 (20 risks pixelation, 50 rarely needed)

### Example Realism Prompt
```
This is a candid amateur 2010s-era photograph, posted on Instagram. 
Close-up headshot of a young woman with long dark hair, wearing a black choker, 
holding a vintage Canon PowerShot digital camera. Natural bedroom lighting, 
white wall background, nostalgic lo-fi aesthetic, indie internet culture vibe.
```

### Example Stylized Prompt
```
aesthetic 11, painterly, digital_media_(artwork), absurd_res, dark_fantasy. 
A close-up portrait of a young androgynous figure with messy dark hair, 
pale skin, and piercing eyes glowing faintly in hues of pink and crimson. 
Loose painterly brushstrokes, layered textures evoking oil-on-canvas feel. 
Background dissolves into swirling abstract shadows and golden flecks. 
Raw beauty and haunting surrealism.
```

---

## Quick Reference Table

| Model Family | Prompting Style | Quality Approach | Key Quirk |
|--------------|-----------------|------------------|-----------|
| **SDXL** | Natural language sentences | Narrative structure (6-part anatomy) | No SD1.5 keywords |
| **Flux** | Pure natural language | Camera/lens specifics, era styles | No negative prompts, HEX colors |
| **Illustrious** | Danbooru booru tags | `masterpiece, best quality` at start | Underscores in tags |
| **Pony** | Score tags + booru tags | Full score string REQUIRED | `score_9, score_8_up...` mandatory |
| **Chroma** | Natural language + aesthetic | Context over keywords, aesthetic tag | Stop using SD1.5 terms |

---

## LLM System Prompt Guidelines

When enhancing prompts for each model, an LLM should:

### For SDXL:
- Rewrite into natural sentences with 6-part structure
- Add camera/lens details for photography
- Specify artistic medium for non-photo
- Keep negative prompts minimal
- Avoid SD1.5 keywords

### For Flux:
- Use pure conversational English
- Add camera model/settings for realism
- Include era/style descriptors ("2000s digicam", "80s vintage")
- Never add negative prompts
- For text in image, use quotation marks
- Can add HEX color codes if precise colors needed

### For Illustrious/NoobAI:
- Convert to comma-separated booru tags with underscores
- Start with quality tags
- Follow tag order: quality → character → clothing → pose → background → style
- Add character count (`1girl`, `2boys`)
- Build comprehensive negative prompt

### For Pony Diffusion:
- **ALWAYS** start with: `score_9, score_8_up, score_7_up, score_6_up, score_5_up, score_4_up`
- Add source tag if style is known (`source_anime`, `source_furry`)
- Add rating tag (`rating_safe`, `rating_questionable`, `rating_explicit`)
- Follow with booru-style tags
- Can mix natural language after score/source/rating

### For Chroma:
- Use natural language (Flux-style)
- For realism: add source context ("Instagram photo", "amateur snapshot")
- For artistic: optionally add `aesthetic 9` or `aesthetic 10`
- Avoid SD1.5 keywords for realism
- Specify lighting and medium clearly
- Negative prompts OK but use sparingly

---

## Notes

- All research compiled from official documentation, CivitAI guides, and community best practices
- Model capabilities evolve with updates - verify with model-specific documentation
- These are guidelines, not strict rules - experimentation encouraged
- LoRAs and fine-tunes may have their own specific prompting requirements

**Last Updated**: 2026-03-03
**Research Sources**: Black Forest Labs docs, CivitAI guides, Segmind blog, Reddit communities
