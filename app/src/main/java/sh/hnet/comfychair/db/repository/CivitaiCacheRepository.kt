package sh.hnet.comfychair.db.repository

import android.content.Context
import sh.hnet.comfychair.db.ComfyChairDatabase
import sh.hnet.comfychair.db.dao.ImageResourceWithVersion
import sh.hnet.comfychair.db.entity.ImageGenerationData
import sh.hnet.comfychair.db.entity.ImageResource
import sh.hnet.comfychair.db.entity.ImageTag
import sh.hnet.comfychair.db.entity.ImageTechnique
import sh.hnet.comfychair.db.entity.ImageTool
import sh.hnet.comfychair.db.entity.ModelVersion
import sh.hnet.comfychair.db.entity.Tag
import sh.hnet.comfychair.db.entity.Technique
import sh.hnet.comfychair.db.entity.Tool
import sh.hnet.comfychair.model.GenerationResource

class CivitaiCacheRepository(context: Context) {

    private val db = ComfyChairDatabase.getInstance(context)
    private val mediaDao = db.cachedMediaDao()
    private val modelVersionDao = db.modelVersionDao()
    private val generationDataDao = db.imageGenerationDataDao()
    private val resourceDao = db.imageResourceDao()
    private val tagDao = db.tagDao()
    private val toolDao = db.toolDao()
    private val techniqueDao = db.techniqueDao()

    companion object {
        // Cache is considered fresh for 24 hours
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L

        @Volatile private var instance: CivitaiCacheRepository? = null

        fun getInstance(context: Context) = instance ?: synchronized(this) {
            instance ?: CivitaiCacheRepository(context.applicationContext).also { instance = it }
        }
    }

    // --- ModelVersion ---

    /**
     * Seed partial ModelVersion rows from gallery image's modelVersionIds.
     * Uses IGNORE conflict strategy so existing enriched rows are not overwritten.
     */
    suspend fun seedModelVersionIds(versionIds: List<Long>) {
        val seeds = versionIds.map { ModelVersion(versionId = it) }
        modelVersionDao.insertAllIfAbsent(seeds)
    }

    /** Upsert fully enriched ModelVersion rows from getGenerationData resources. */
    suspend fun cacheModelVersions(versions: List<ModelVersion>) {
        modelVersionDao.upsertAll(versions)
    }

    suspend fun getModelVersion(versionId: Long): ModelVersion? =
        modelVersionDao.getById(versionId)

    suspend fun getModelVersions(versionIds: List<Long>): List<ModelVersion> =
        if (versionIds.isEmpty()) emptyList() else modelVersionDao.getByIds(versionIds)

    /** Returns version IDs from the list that have no name yet (need enrichment). */
    suspend fun getUnenrichedVersionIds(versionIds: List<Long>): List<Long> =
        if (versionIds.isEmpty()) emptyList() else modelVersionDao.getUnenrichedIds(versionIds)

    // --- ImageGenerationData + ImageResource ---

    /**
     * Cache a full getGenerationData response for an image.
     * Stores ImageGenerationData, ImageResource join rows, and enriched ModelVersions.
     */
    suspend fun cacheGenerationData(
        imageId: Long,
        process: String?,
        onSite: Boolean,
        prompt: String?,
        negativePrompt: String?,
        cfgScale: Double?,
        steps: Int?,
        sampler: String?,
        seed: Long?,
        size: String?,
        model: String?,
        version: String?,
        clipSkip: Int?,
        denoisingStrength: Double?,
        canRemix: Boolean,
        hideMeta: Boolean,
        resources: List<GenerationResource>
    ) {
        // 1. Store generation data row
        generationDataDao.upsert(
            ImageGenerationData(
                imageId = imageId,
                process = process,
                onSite = onSite,
                prompt = prompt,
                negativePrompt = negativePrompt,
                cfgScale = cfgScale,
                steps = steps,
                sampler = sampler,
                seed = seed,
                size = size,
                model = model,
                version = version,
                clipSkip = clipSkip,
                denoisingStrength = denoisingStrength,
                canRemix = canRemix,
                hideMeta = hideMeta
            )
        )

        // 2. Store image resource join rows (skip any with no versionId)
        val resourceEntities = resources.mapNotNull { r ->
            val versionId = r.modelVersionId ?: return@mapNotNull null
            if (versionId <= 0L) return@mapNotNull null
            ImageResource(
                imageId = imageId,
                versionId = versionId,
                strength = r.weight
            )
        }
        if (resourceEntities.isNotEmpty()) {
            resourceDao.insertAll(resourceEntities)
        }

        // 3. Upsert enriched ModelVersion rows from resources
        val modelVersions = resources.mapNotNull { r ->
            val vid = r.modelVersionId ?: return@mapNotNull null
            ModelVersion(
                versionId = vid,
                modelId = 0L,       // actual modelId not available in GenerationResource
                modelName = r.name,
                modelType = r.type,
                versionName = null, // not available in GenerationResource
                baseModel = null    // not available in GenerationResource
            )
        }
        if (modelVersions.isNotEmpty()) {
            modelVersionDao.upsertAll(modelVersions)
        }
    }

    /** Returns fresh cached generation data (within TTL) or null. */
    suspend fun getFreshGenerationData(imageId: Long): ImageGenerationData? {
        val threshold = System.currentTimeMillis() - CACHE_TTL_MS
        return generationDataDao.getFreshById(imageId, threshold)
    }

    /** Returns cached resources for an image with their ModelVersion info. */
    suspend fun getResourcesWithVersions(imageId: Long): List<ImageResourceWithVersion> =
        resourceDao.getWithVersions(imageId)

    // --- Tools and Techniques ---

    suspend fun cacheTools(tools: List<Tool>) = toolDao.upsertAll(tools)
    suspend fun cacheTechniques(techniques: List<Technique>) = techniqueDao.upsertAll(techniques)

    suspend fun getToolsById(toolIds: List<Int>): List<Tool> =
        if (toolIds.isEmpty()) emptyList() else toolDao.getByIds(toolIds)

    suspend fun getTechniquesById(techniqueIds: List<Int>): List<Technique> =
        if (techniqueIds.isEmpty()) emptyList() else techniqueDao.getByIds(techniqueIds)

    suspend fun hasTools(): Boolean = toolDao.count() > 0

    suspend fun cacheImageTools(imageId: Long, toolIds: List<Int>) {
        if (toolIds.isEmpty()) return
        toolDao.insertImageTools(toolIds.map { ImageTool(imageId, it) })
    }

    suspend fun cacheImageTechniques(imageId: Long, techniqueIds: List<Int>) {
        if (techniqueIds.isEmpty()) return
        techniqueDao.insertImageTechniques(techniqueIds.map { ImageTechnique(imageId, it) })
    }

    // --- Tags ---

    suspend fun cacheTags(tags: List<Tag>) = tagDao.upsertAll(tags)

    suspend fun cacheImageTags(imageId: Long, tagIds: List<Int>) {
        if (tagIds.isEmpty()) return
        tagDao.insertImageTags(tagIds.map { ImageTag(imageId, it) })
    }
}
