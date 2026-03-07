package sh.hnet.comfychair.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey val tagId: Int,
    val name: String,
    val type: String? = null,       // "Label", "Moderation", "UserGenerated"
    val nsfwLevel: Int = 1,
    val cachedAt: Long = System.currentTimeMillis()
)
