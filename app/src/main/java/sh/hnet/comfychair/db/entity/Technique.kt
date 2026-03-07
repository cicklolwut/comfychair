package sh.hnet.comfychair.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "techniques")
data class Technique(
    @PrimaryKey val techniqueId: Int,
    val name: String,
    val type: String  // "Image" or "Video"
)
