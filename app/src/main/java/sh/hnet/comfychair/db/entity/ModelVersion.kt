package sh.hnet.comfychair.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "model_versions")
data class ModelVersion(
    @PrimaryKey val versionId: Long,
    val modelId: Long = 0L,
    val modelName: String? = null,   // null = seeded from gallery, name not yet fetched
    val modelType: String? = null,   // "LORA", "Checkpoint", "LyCORIS", etc.
    val versionName: String? = null,
    val baseModel: String? = null,
    val cachedAt: Long = System.currentTimeMillis()
)
