package sh.hnet.comfychair.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import sh.hnet.comfychair.db.entity.InstalledVersion

@Dao
interface InstalledVersionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(versions: List<InstalledVersion>)

    @Query("SELECT versionId FROM installed_versions WHERE serverId = :serverId")
    suspend fun getVersionIds(serverId: String): List<Long>

    @Query("DELETE FROM installed_versions WHERE serverId = :serverId")
    suspend fun clearForServer(serverId: String)
}
