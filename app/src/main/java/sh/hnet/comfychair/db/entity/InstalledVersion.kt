package sh.hnet.comfychair.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "installed_versions",
    primaryKeys = ["versionId", "serverId"],
    indices = [Index("serverId")]
)
data class InstalledVersion(
    val versionId: Long,
    val serverId: String,
    val installedAt: Long = System.currentTimeMillis()
)
