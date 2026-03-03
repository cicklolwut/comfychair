# Video Generation Model Prompting Guide

This document provides best practices for writing prompt enhancement system prompts for major video generation models. Each model has unique characteristics in how it interprets text prompts, and understanding these nuances is crucial for optimal video generation quality.

---

## 1. WAN / Wan2.1 (Alibaba)

### Model Overview
- **Developer**: Alibaba (Wan-AI)
- **Architecture**: Diffusion Transformer (DiT) with Flow Matching
- **Resolutions**: 480P (832×480), 720P (1280×720)
- **Models**: T2V-1.3B, T2V-14B, I2V-14B, FLF2V-14B, VACE

### Prompting Style
WAN models work best with **detailed, descriptive Chinese or English text**. The model was trained with **prompt extension** as a core feature, meaning it expects enriched, detailed descriptions.

**Key characteristics**:
- Accepts both natural language descriptions and specific keywords
- **Prompt extension is highly recommended** - the model performs better with longer, detailed prompts
- Supports Chinese text natively (trained primarily on Chinese data for some models)
- Two extension modes: Normal (intent comprehension) and Master (cinematic quality)

### Effective Prompt Structure
1. **Start with main subject and action**
2. **Add detailed descriptions of appearance, style, environment**
3. **Include camera movement and angles**
4. **Specify lighting conditions**
5. **Mention composition and atmosphere**

### Motion and Camera Terminology
- Camera movements: "slow dolly forward," "tracking shot," "handheld," "static camera," "360-degree rotation"
- Motion descriptors: "walks slowly," "drifts gently," "rapid movement," "subtle swaying"
- Composition terms: "close-up," "wide shot," "medium shot," "over-the-shoulder"

### Resolution/Duration/FPS Considerations
- **480P**: 832×480, 81 frames (5 seconds @ 16 fps)
- **720P**: 1280×720, 81 frames (5 seconds @ 16 fps)
- Frame number must be: `8N + 1` (e.g., 9, 17, 25, 33, 41, 49, 57, 65, 73, 81)
- Aspect ratios supported but output size represents area with ratio following input

### Things to Avoid
- Short, vague prompts (use prompt extension!)
- Conflicting visual instructions
- Over-reliance on English-only for models trained on Chinese data
- Extremely complex physics or hand movements (current DiT limitation)
- Static descriptions without motion information

### Recommended System Prompt Guidance
```
When enhancing prompts for WAN/Wan2.1:
1. Expand brief descriptions into detailed paragraphs (150-200 words)
2. Include specific visual details: textures, colors, materials, lighting quality
3. Describe camera behavior explicitly (movement, angle, framing)
4. Add atmospheric and environmental details
5. Specify style references when appropriate (e.g., "commercial photography," "cinematic")
6. For Chinese prompts, maintain richness of description
7. Structure: Subject → Action → Appearance → Environment → Camera → Lighting → Style
```

---

## 2. HunyuanVideo (Tencent)

### Model Overview
- **Developer**: Tencent (Hunyuan Team)
- **Architecture**: 13B parameter Diffusion Transformer with dual-stream to single-stream design
- **Text Encoder**: MLLM (Multimodal Large Language Model) - decoder-only with bidirectional refiner
- **VAE**: 3D Causal VAE (4×8×16 compression ratio)

### Prompting Style
HunyuanVideo uses a sophisticated **MLLM text encoder** that expects detailed, chronological descriptions with **strong emphasis on proper prompt rewriting**.

**Key characteristics**:
- Trained with **prompt rewrite model** (Hunyuan-Large fine-tuned)
- Two modes: Normal (intent understanding) and Master (visual quality enhancement)
- Expects long-form prompts (up to 224 tokens)
- Best with detailed, sequential action descriptions
- Supports **English only** (translate other languages first)

### Effective Prompt Structure
HunyuanVideo excels with:
1. **Chronological action sequences** - describe events in order
2. **Detailed character/object descriptions** with specific attributes
3. **Environmental context** - setting, lighting, weather
4. **Camera specifications** - angles, movements, shot types
5. **Style and mood indicators**

### Motion and Camera Terminology
- **Camera angles**: "bird's eye view," "low angle," "eye-level," "dutch angle," "over-the-shoulder"
- **Camera movements**: "push in," "pull back," "tracking shot," "pan left/right," "tilt up/down," "crane shot"
- **Motion quality**: "smooth," "steady," "handheld," "slow motion," "time-lapse"
- **Action descriptors**: Use specific verbs and adverbs (e.g., "walks carefully," "runs energetically")

### Resolution/Duration/FPS Considerations
- **Supported resolutions**:
  - 540p: Various aspect ratios (544×960, 960×544, 624×832, 832×624, 720×720)
  - **720p (recommended)**: 720×1280, 1280×720, 1104×832, 832×1104, 960×960
- **Video length**: 129 frames (5 seconds @ ~26 fps)
- **Aspect ratios**: 9:16, 16:9, 4:3, 3:4, 1:1 all supported
- Resolution format: `height width` (e.g., `--video-size 720 1280`)

### Things to Avoid
- Non-English prompts (must translate first)
- Overly brief descriptions (model trained on detailed prompts)
- Ignoring prompt rewrite suggestions
- Static, single-moment descriptions
- Exceeding 224 token limit
- Requesting text generation in videos (limited capability)

### Recommended System Prompt Guidance
```
When enhancing prompts for HunyuanVideo:
1. Write in detailed, flowing paragraphs describing action chronologically
2. Expand prompts to utilize most of the 224 token limit
3. Include specific subject descriptions (age, clothing, appearance)
4. Detail the environment comprehensively (lighting, setting, atmosphere)
5. Specify camera work explicitly (angle, movement, framing)
6. Add style references (e.g., "documentary style," "cinematic")
7. Use vivid, specific language rather than generic terms
8. Prioritize visual quality descriptors in Master mode
9. Focus on semantic accuracy in Normal mode
10. Structure: Main action → Subject details → Setting → Camera → Lighting → Style
```

---

## 3. CogVideoX (Tsinghua/Zhipu AI)

### Model Overview
- **Developer**: Tsinghua University / Zhipu AI
- **Architecture**: 3D Causal VAE + Expert Transformer
- **Models**: CogVideoX-2B, CogVideoX-5B, CogVideoX1.5-5B, I2V variants
- **Text Encoder**: T5-XXL

### Prompting Style
CogVideoX requires **detailed, literal, chronological descriptions** written like a cinematographer's shot list. The model was trained on long, descriptive prompts.

**Key characteristics**:
- **Long prompts strongly preferred** (up to 226 tokens)
- Direct, literal descriptions work best
- Chronological flow is important
- **Prompt optimization/rewrite highly recommended** using LLMs
- Supports English only (translate if needed)

### Effective Prompt Structure
The recommended structure is a **single flowing paragraph** containing:

1. **Main action** (start directly with what's happening)
2. **Specific movements and gestures** (detailed descriptions)
3. **Character/object appearance** (precise visual details)
4. **Background and environment** (setting details)
5. **Camera angles and movements** (technical cinematography terms)
6. **Lighting and colors** (atmosphere and mood)
7. **Changes or events** (progression within the scene)

**Example structure**: "A cat walks through a garden, its fluffy white fur catching the golden afternoon sunlight. The camera follows from a low angle as the cat stops to sniff a red rose, then continues along a cobblestone path. Dappled light filters through tree leaves overhead, creating moving shadows on the ground. The scene has a peaceful, serene atmosphere with soft natural colors."

### Motion and Camera Terminology
- **Camera angles**: "low angle," "high angle," "bird's eye view," "eye-level," "Dutch angle," "over-the-shoulder"
- **Camera movements**: "dolly forward," "dolly back," "tracking shot," "pan left/right," "tilt up/down," "static," "handheld," "crane up/down"
- **Shot types**: "close-up," "medium shot," "wide shot," "extreme close-up," "establishing shot"
- **Motion descriptors**: "slowly," "quickly," "smoothly," "suddenly," "gradually," "energetically"

### Resolution/Duration/FPS Considerations

**CogVideoX-2B & 5B**:
- Resolution: 720×480
- Frames: 49 frames (should be 8N + 1, where N ≤ 6)
- Duration: ~6 seconds
- Frame rate: 8 fps

**CogVideoX1.5-5B** (Latest):
- Resolution: 1360×768 (or any resolution where min(W,H) ≥ 768 and max(W,H) ≤ 1360)
- Frames: Up to 81 frames (should be 16N + 1, where N ≤ 10)
- Duration: 5-10 seconds
- Frame rate: 16 fps
- **Max(W, H) % 16 = 0** (must be divisible by 16)

### Things to Avoid
- Starting with "Let me explain..." or similar preamble
- Generic descriptors like "nice," "good," "interesting"
- Step-by-step numbered lists
- Abstract concepts without visual grounding
- Non-English prompts
- Static descriptions without motion
- Overly complex multi-action sequences in short clips
- Text generation requests (limited capability)

### Recommended System Prompt Guidance
```
When enhancing prompts for CogVideoX:
1. Write in a single flowing paragraph (no bullet points)
2. Start directly with the main action
3. Use literal, precise descriptions (think like a cinematographer)
4. Include specific movements, appearances, camera angles, lighting, and colors
5. Keep chronological flow - describe what happens in sequence
6. Aim for 150-200 words
7. Use cinematography vocabulary for camera work
8. Specify lighting conditions and environmental details
9. Avoid abstract concepts - stay visual and concrete
10. Structure: Action → Movement → Appearance → Environment → Camera → Lighting → Changes
```

---

## 4. Mochi (Genmo)

### Model Overview
- **Developer**: Genmo
- **Architecture**: 10B parameter Asymmetric Diffusion Transformer (AsymmDiT)
- **Text Encoder**: T5-XXL (single encoder, no CLIP)
- **VAE**: AsymmVAE (362M params, 8×8 spatial, 6× temporal compression)

### Prompting Style
Mochi uses a **single T5-XXL encoder** and expects **natural language descriptions** without excessive complexity. The model is optimized for photorealistic styles.

**Key characteristics**:
- Simple, clear natural language works best
- Photorealistic style optimization
- Good prompt adherence
- High-fidelity motion generation
- Does **not** work well with animated/cartoon styles

### Effective Prompt Structure
Mochi prefers straightforward descriptions:
1. **Subject and main action**
2. **Visual details** (appearance, clothing, props)
3. **Setting and environment**
4. **Camera information** (optional but helpful)
5. **Lighting and atmosphere**

Unlike other models, Mochi doesn't require extremely long prompts - clarity over length.

### Motion and Camera Terminology
- **Camera terms**: Standard cinematography vocabulary works
- **Motion descriptors**: Natural language descriptions of movement
- **Style references**: "photorealistic," "cinematic," "documentary," "commercial"
- Best to keep technical terms simple and clear

### Resolution/Duration/FPS Considerations
- **Native resolution**: 480p (exact specs not specified in public docs)
- **Frame rate**: Generates high-quality motion
- Model can generate videos with strong motion fidelity
- Optimized for realistic motion, not stylized animation

### Things to Avoid
- **Animated/cartoon style requests** (model optimized for photorealism)
- Overly complex nested descriptions
- Extreme motion scenarios (can cause warping)
- Multiple language models' prompt styles (uses single T5-XXL)
- Over-engineering prompts (simpler is often better)

### Recommended System Prompt Guidance
```
When enhancing prompts for Mochi:
1. Use clear, natural language descriptions
2. Focus on photorealistic scenarios
3. Avoid cartoon/animated style requests
4. Keep descriptions concise but detailed (100-150 words ideal)
5. Specify subject, action, setting, and atmosphere
6. Use standard cinematography terms if describing camera work
7. Emphasize realistic motion and natural physics
8. Structure: Subject → Action → Appearance → Setting → Camera (optional) → Atmosphere
9. Simpler prompts often work better than over-complicated ones
```

---

## 5. LTX-Video (Lightricks)

### Model Overview
- **Developer**: Lightricks
- **Architecture**: DiT-based foundation model
- **Models**: LTX-Video (2B), LTXV-13B, distilled variants
- **Key Feature**: Frame-level control with Shot Control system

### Prompting Style
LTX-Video excels with **detailed cinematographer-style descriptions** structured like shot lists. The platform emphasizes the combination of text prompts with visual references.

**Key characteristics**:
- **Detailed, chronological descriptions preferred**
- Works with both text-only and text + visual reference combinations
- Strong focus on camera angles and movements
- Supports frame-by-frame direction via Shot Control
- Resolution flexibility (works across multiple resolutions)

### Effective Prompt Structure (Recommended by LTX)
Build prompts with this **chronological layered structure**:

1. **Main action** (single sentence, direct start)
2. **Specific movements and gestures**
3. **Character/object appearances** (precise details)
4. **Background and environment details**
5. **Camera angles and movements** (technical terms)
6. **Lighting and colors**
7. **Changes or sudden events**

**Format**: Single flowing paragraph, 150-200 words

### Motion and Camera Terminology
Extensive cinematography vocabulary is well-supported:

- **Camera angles**: "low angle," "high angle," "bird's eye view," "eye-level," "Dutch angle," "over-the-shoulder"
- **Camera movements**: "slow dolly forward," "tracking shot," "pan," "tilt," "crane," "zoom," "static," "handheld"
- **Shot types**: "close-up," "medium shot," "wide shot," "extreme close-up," "establishing shot"
- **Motion terms**: "drifting slowly," "rapid," "gentle swaying," "sudden," "smooth"
- **Lighting**: "golden hour," "overcast," "harsh midday," "neon-lit," "rim-lit silhouette," "soft diffused"

### Resolution/Duration/FPS Considerations

**LTX-Video models support flexible resolutions**:
- Works on resolutions divisible by 32
- Default: 1216×704 at 30 FPS (as of model updates)
- Can generate up to 720p and beyond
- Frames divisible by 8 + 1 (e.g., 257 frames)
- Model works best on resolutions under 720×1280

**LTXV-13B specifics**:
- Supports up to **60 seconds** of video (long shot generation)
- Frame rate: Up to 50 FPS
- Native 4K support mentioned for future versions

### Things to Avoid
- Starting with preambles or explanations
- Generic descriptors ("nice," "good," "interesting")
- Overly abstract concepts
- Numbered step-by-step lists
- Missing motion information (always specify how things move)
- Conflicting instructions (e.g., "fast-paced" + "slow contemplative")
- Too many actions in short clips

### Recommended System Prompt Guidance
```
When enhancing prompts for LTX-Video:
1. Write as a single flowing paragraph (cinematographer's shot list style)
2. Start directly with the main action
3. Layer details chronologically: action → movement → appearance → environment → camera → lighting
4. Use precise cinematography vocabulary (angles, movements, lighting terms)
5. Aim for 150-200 words
6. Specify camera behavior explicitly (movement, angle, framing)
7. Include lighting descriptors (quality, direction, mood)
8. Reference artistic styles when appropriate ("Wes Anderson symmetry," "noir cinematography")
9. Describe motion direction and speed clearly
10. Keep vision coherent - avoid conflicting instructions
11. Structure: Main action → Movements → Character/object details → Environment → Camera work → Lighting → Style
```

---

## Cross-Model Best Practices Summary

### Universal Principles

1. **Detailed > Vague**: All models perform better with specific, detailed descriptions
2. **Chronological flow**: Describe action sequences in order
3. **Cinematography language**: Use standard film terminology for camera work
4. **Lighting matters**: Specify lighting conditions for better atmosphere
5. **Motion is key**: Always describe how things move, not just what they look like

### Model Selection Guide

- **Best for photorealism**: Mochi, HunyuanVideo
- **Best for Chinese prompts**: WAN, HunyuanVideo (with translation)
- **Best for control**: LTX-Video (Shot Control), HunyuanVideo (detailed MLLM)
- **Best for long videos**: LTXV-13B (60s), CogVideoX1.5-5B (10s)
- **Best for consumer GPUs**: CogVideoX-2B, WAN-1.3B

### Prompt Length Guidelines

- **CogVideoX**: 150-226 tokens (long preferred)
- **HunyuanVideo**: Up to 224 tokens (long preferred)
- **WAN**: 150-200 words with extension
- **LTX-Video**: 150-200 words
- **Mochi**: 100-150 words (simpler is better)

---

## Appendix: Common Cinematography Terms

### Camera Angles
- **Eye-level**: Camera at subject's eye height
- **Low angle**: Camera below subject, looking up (heroic, powerful)
- **High angle**: Camera above subject, looking down (vulnerable, small)
- **Bird's eye view**: Directly overhead
- **Dutch angle**: Tilted/canted camera (unease, tension)
- **Over-the-shoulder**: Behind one subject, looking at another

### Camera Movements
- **Pan**: Horizontal rotation (left/right)
- **Tilt**: Vertical rotation (up/down)
- **Dolly**: Camera moves toward (dolly in) or away (dolly out)
- **Tracking**: Camera follows subject's movement
- **Crane**: Camera moves up or down on crane
- **Zoom**: Lens focal length change (not physical movement)
- **Handheld**: Unstabilized, natural camera shake
- **Steadicam**: Smooth, stabilized movement while operator walks

### Shot Types
- **Extreme close-up (ECU)**: Very tight on detail
- **Close-up (CU)**: Face or object fills frame
- **Medium close-up (MCU)**: Head and shoulders
- **Medium shot (MS)**: Waist up
- **Medium wide shot (MWS)**: Knees up
- **Wide shot (WS)**: Full body in environment
- **Establishing shot**: Shows location/setting

### Lighting Terms
- **Golden hour**: Warm, soft light during sunrise/sunset
- **Blue hour**: Cool light just before sunrise/after sunset
- **Hard light**: Direct, creates sharp shadows
- **Soft light**: Diffused, gentle shadows
- **Rim light**: Backlight creating outline around subject
- **Key light**: Main light source
- **Fill light**: Secondary light, softens shadows
- **Practical light**: Light source visible in frame

---

*Last updated: 2026-03-03*
