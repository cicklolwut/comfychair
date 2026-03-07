package sh.hnet.comfychair.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tools")
data class Tool(
    @PrimaryKey val toolId: Int,
    val name: String,
    val type: String,               // "Image", "Video", "Editor", etc.
    val icon: String? = null,
    val domain: String? = null,
    val priority: Int? = null,
    val supported: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis()
)
