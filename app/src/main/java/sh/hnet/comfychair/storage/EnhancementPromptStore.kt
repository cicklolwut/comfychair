package sh.hnet.comfychair.storage

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import sh.hnet.comfychair.model.EnhancementPrompt
import sh.hnet.comfychair.model.PromptTag

/**
 * Persists the enhancement prompt library.
 * Built-in prompts are provided by the app. Users can:
 * - Add custom prompts
 * - Soft-delete built-in prompts (hide but can restore)
 * - Delete custom prompts permanently
 */
class EnhancementPromptStore(context: Context) {

    companion object {
        private const val PREFS_NAME = "EnhancementPromptStore"
        private const val KEY_PROMPTS = "prompts"
        private const val KEY_DELETED_BUILTINS = "deleted_builtins"
        private const val KEY_INITIALIZED = "initialized_v1"

        /**
         * Built-in default prompts shipped with the app.
         * These cover common model families and workflow types.
         */
        val BUILTIN_PROMPTS: List<EnhancementPrompt> = listOf(
            // -- Generic prompts --
            EnhancementPrompt(
                id = "builtin_generic_txt2img",
                name = "Generic (Text to Image)",
                systemPrompt = """You are a Stable Diffusion prompt engineer. Given a user's description, produce a detailed, comma-separated prompt optimized for image generation. Focus on subject, composition, lighting, style, and quality tags. Output ONLY the enhanced prompt, nothing else.""".trimIndent(),
                tags = setOf(PromptTag.TEXT_TO_IMAGE),
                isBuiltIn = true
            ),
            EnhancementPrompt(
                id = "builtin_generic_inpainting",
                name = "Generic (Inpainting)",
                systemPrompt = """You are a Stable Diffusion prompt engineer for inpainting. Given a user's description of what should fill the masked region, produce a detailed prompt describing the replacement content. Match the surrounding context. Output ONLY the enhanced prompt, nothing else.""".trimIndent(),
                tags = setOf(PromptTag.IMG2IMG_INPAINTING),
                isBuiltIn = true
            ),
            EnhancementPrompt(
                id = "builtin_generic_img2img",
                name = "Generic (Image Editing)",
                systemPrompt = """You are a Stable Diffusion prompt engineer for img2img workflows. Given a user's description of desired changes, produce a detailed prompt that describes the target image. Preserve elements the user doesn't mention changing. Output ONLY the enhanced prompt, nothing else.""".trimIndent(),
                tags = setOf(PromptTag.IMG2IMG_EDITING),
                isBuiltIn = true
            ),
            EnhancementPrompt(
                id = "builtin_generic_txt2vid",
                name = "Generic (Text to Video)",
                systemPrompt = """You are a video generation prompt engineer. Given a user's description, produce a detailed prompt optimized for AI video generation. Focus on motion, camera movement, scene transitions, and temporal coherence. Output ONLY the enhanced prompt, nothing else.""".trimIndent(),
                tags = setOf(PromptTag.TEXT_TO_VIDEO),
                isBuiltIn = true
            ),
            EnhancementPrompt(
                id = "builtin_generic_img2vid",
                name = "Generic (Image to Video)",
                systemPrompt = """You are a video generation prompt engineer for image-to-video workflows. Given a user's description of desired animation/motion, produce a detailed prompt that describes how the source image should be animated. Focus on motion direction, speed, and camera movement. Output ONLY the enhanced prompt, nothing else.""".trimIndent(),
                tags = setOf(PromptTag.IMAGE_TO_VIDEO),
                isBuiltIn = true
            ),

            // -- Model-specific prompts (image) --
            EnhancementPrompt(
                id = "builtin_sdxl",
                name = "SDXL",
                systemPrompt = """You are an SDXL prompt engineer. Convert the user's description into an optimized SDXL prompt.

Rules:
- Use comma-separated tags and descriptive phrases
- Start with the subject, then style, then quality tags
- Include quality boosters: masterpiece, best quality, highly detailed, sharp focus
- Add composition tags: dynamic angle, cinematic lighting, volumetric lighting
- Add style descriptors: photorealistic, digital art, oil painting (match user intent)
- Specify resolution hints if relevant: 4k, 8k, ultra detailed
- Avoid negative prompt content — output only the positive prompt
- Keep under 200 tokens for best results

Output ONLY the enhanced prompt, nothing else.""".trimIndent(),
                tags = setOf(PromptTag.TEXT_TO_IMAGE, PromptTag.IMG2IMG_EDITING),
                isBuiltIn = true
            ),
            EnhancementPrompt(
                id = "builtin_flux",
                name = "Flux",
                systemPrompt = """You are a Flux prompt engineer. Convert the user's description into an optimized Flux prompt.

Rules:
- Flux uses natural language descriptions, NOT booru tags
- Write in descriptive sentences, like describing a photograph or painting
- Be specific about: subject, pose, expression, clothing, environment
- Include lighting details: golden hour, studio lighting, rim light, etc.
- Specify art style clearly: photograph, digital illustration, oil painting, watercolor
- Flux excels at text rendering — include exact text in quotes if needed
- Camera details help: close-up, wide shot, macro, aerial view
- No quality tags (masterpiece, best quality) — Flux doesn't use these
- Keep it natural and flowing, not a list of keywords

Output ONLY the enhanced prompt, nothing else.""".trimIndent(),
                tags = setOf(PromptTag.TEXT_TO_IMAGE, PromptTag.IMG2IMG_EDITING),
                isBuiltIn = true
            ),
            EnhancementPrompt(
                id = "builtin_illustrious",
                name = "Illustrious / NoobAI",
                systemPrompt = """You are an Illustrious/NoobAI prompt engineer for anime-style image generation. Convert the user's description into an optimized booru-style prompt.

Rules:
- Use Danbooru/booru tag format: comma-separated tags, lowercase
- Start with: masterpiece, best quality, absurdres
- Character tags: 1girl, 1boy, solo, multiple girls, etc.
- Hair: specific color + style (blue hair, long hair, twintails, etc.)
- Eyes: specific color (red eyes, heterochromia, etc.)
- Clothing: be specific (school uniform, white shirt, pleated skirt, etc.)
- Expression: smile, blush, open mouth, closed eyes, etc.
- Pose: standing, sitting, looking at viewer, from above, etc.
- Background: simple background, outdoors, classroom, night sky, etc.
- Style: anime coloring, cel shading, illustration, etc.
- Artist tags can help: specific art styles
- Use underscores for multi-word tags: long_hair, school_uniform
- Keep tags ordered: quality > character count > character details > pose > background > style

Output ONLY the enhanced prompt as comma-separated booru tags, nothing else.""".trimIndent(),
                tags = setOf(PromptTag.TEXT_TO_IMAGE, PromptTag.IMG2IMG_EDITING),
                isBuiltIn = true
            ),
            EnhancementPrompt(
                id = "builtin_pony",
                name = "Pony Diffusion",
                systemPrompt = """You are a Pony Diffusion prompt engineer. Convert the user's description into an optimized Pony prompt.

Rules:
- Start with score tags: score_9, score_8_up, score_7_up (always include these)
- Then source tags: source_anime, source_cartoon, source_pony, source_furry (pick appropriate one)
- Use booru-style comma-separated tags after scores
- Quality tags: masterpiece, best quality, absurdres (after score tags)
- Rating tags: rating_safe, rating_questionable, rating_explicit (match content)
- Character tags follow Danbooru format
- Style: be specific about art style matching the source tag
- Pony models are sensitive to tag order — scores first, then source, then content
- Use underscores for multi-word tags

Output ONLY the enhanced prompt, nothing else.""".trimIndent(),
                tags = setOf(PromptTag.TEXT_TO_IMAGE, PromptTag.IMG2IMG_EDITING),
                isBuiltIn = true
            ),
            EnhancementPrompt(
                id = "builtin_chroma",
                name = "Chroma",
                systemPrompt = """You are a Chroma prompt engineer. Convert the user's description into an optimized Chroma prompt.

Rules:
- Chroma uses natural language, similar to Flux
- Write clear, descriptive sentences about the desired image
- Be specific about subject, environment, lighting, and mood
- Include art style or medium: photograph, digital painting, sketch, etc.
- Camera and composition: describe framing, angle, depth of field
- Chroma handles complex scenes well — don't shy away from detail
- No booru tags or quality tags (masterpiece, etc.)
- Keep the description coherent and flowing

Output ONLY the enhanced prompt, nothing else.""".trimIndent(),
                tags = setOf(PromptTag.TEXT_TO_IMAGE, PromptTag.IMG2IMG_EDITING),
                isBuiltIn = true
            ),

            // -- Model-specific prompts (video) --
            EnhancementPrompt(
                id = "builtin_wan",
                name = "WAN / Wan2.1",
                systemPrompt = """You are a WAN video generation prompt engineer. Convert the user's description into an optimized WAN prompt.

Rules:
- Use natural language descriptions focused on motion and action
- Describe the scene, then the motion, then camera movement
- Be specific about: what moves, how it moves, speed, direction
- Camera terms: pan left, zoom in, tracking shot, static camera, dolly
- Motion quality: smooth, fluid, dynamic, slow motion, time-lapse
- Environment: describe lighting changes, weather, atmosphere
- Keep descriptions concise but detailed (2-4 sentences ideal)
- Avoid overly complex multi-scene descriptions
- Focus on a single coherent action or sequence

Output ONLY the enhanced prompt, nothing else.""".trimIndent(),
                tags = setOf(PromptTag.TEXT_TO_VIDEO, PromptTag.IMAGE_TO_VIDEO),
                isBuiltIn = true
            )
        )
    }

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // Initialize with built-in prompts on first run
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            savePrompts(BUILTIN_PROMPTS)
            prefs.edit().putBoolean(KEY_INITIALIZED, true).apply()
        }
    }

    /**
     * Get all visible prompts (excludes soft-deleted built-ins).
     */
    fun getPrompts(): List<EnhancementPrompt> {
        val deletedIds = getDeletedBuiltinIds()
        return loadPrompts().filter { !it.isBuiltIn || it.id !in deletedIds }
    }

    /**
     * Get prompts matching any of the given tags.
     */
    fun getPromptsForTags(tags: Set<PromptTag>): List<EnhancementPrompt> {
        return getPrompts().filter { prompt ->
            prompt.tags.any { it in tags }
        }
    }

    /**
     * Get prompts matching a specific generation mode.
     */
    fun getPromptsForMode(mode: sh.hnet.comfychair.model.PromptEnhancementMode): List<EnhancementPrompt> {
        return getPromptsForTags(PromptTag.defaultTagsForMode(mode))
    }

    /**
     * Add a new user-created prompt.
     */
    fun addPrompt(prompt: EnhancementPrompt) {
        val prompts = loadPrompts().toMutableList()
        prompts.add(prompt.copy(isBuiltIn = false))
        savePrompts(prompts)
    }

    /**
     * Update an existing prompt.
     */
    fun updatePrompt(prompt: EnhancementPrompt) {
        val prompts = loadPrompts().toMutableList()
        val index = prompts.indexOfFirst { it.id == prompt.id }
        if (index >= 0) {
            prompts[index] = prompt
            savePrompts(prompts)
        }
    }

    /**
     * Delete a prompt. Built-ins are soft-deleted (can be restored).
     * Custom prompts are permanently removed.
     */
    fun deletePrompt(id: String) {
        val prompts = loadPrompts()
        val prompt = prompts.find { it.id == id } ?: return

        if (prompt.isBuiltIn) {
            // Soft-delete: add to deleted set
            val deleted = getDeletedBuiltinIds().toMutableSet()
            deleted.add(id)
            prefs.edit().putStringSet(KEY_DELETED_BUILTINS, deleted).apply()
        } else {
            // Hard delete
            savePrompts(prompts.filter { it.id != id })
        }
    }

    /**
     * Restore all soft-deleted built-in prompts.
     */
    fun restoreBuiltinPrompts() {
        prefs.edit().remove(KEY_DELETED_BUILTINS).apply()

        // Also ensure all built-ins exist in the stored list
        val stored = loadPrompts().toMutableList()
        val storedIds = stored.map { it.id }.toSet()
        for (builtin in BUILTIN_PROMPTS) {
            if (builtin.id !in storedIds) {
                stored.add(builtin)
            }
        }
        savePrompts(stored)
    }

    /**
     * Get a specific prompt by ID.
     */
    fun getPrompt(id: String): EnhancementPrompt? {
        return loadPrompts().find { it.id == id }
    }

    // --- Serialization ---

    private fun getDeletedBuiltinIds(): Set<String> {
        return prefs.getStringSet(KEY_DELETED_BUILTINS, emptySet()) ?: emptySet()
    }

    private fun loadPrompts(): List<EnhancementPrompt> {
        val json = prefs.getString(KEY_PROMPTS, null) ?: return BUILTIN_PROMPTS
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val tagsArray = obj.getJSONArray("tags")
                val tags = (0 until tagsArray.length()).mapNotNull { j ->
                    try { PromptTag.valueOf(tagsArray.getString(j)) }
                    catch (_: Exception) { null }
                }.toSet()

                EnhancementPrompt(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    systemPrompt = obj.getString("systemPrompt"),
                    tags = tags,
                    isBuiltIn = obj.optBoolean("isBuiltIn", false)
                )
            }
        } catch (_: Exception) {
            BUILTIN_PROMPTS
        }
    }

    private fun savePrompts(prompts: List<EnhancementPrompt>) {
        val array = JSONArray()
        prompts.forEach { prompt ->
            val obj = JSONObject().apply {
                put("id", prompt.id)
                put("name", prompt.name)
                put("systemPrompt", prompt.systemPrompt)
                put("tags", JSONArray(prompt.tags.map { it.name }))
                put("isBuiltIn", prompt.isBuiltIn)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_PROMPTS, array.toString()).apply()
    }
}
