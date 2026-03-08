package sh.hnet.comfychair.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import sh.hnet.comfychair.db.dao.CachedMediaDao
import sh.hnet.comfychair.db.dao.ImageGenerationDataDao
import sh.hnet.comfychair.db.dao.ImageResourceDao
import sh.hnet.comfychair.db.dao.InstalledVersionDao
import sh.hnet.comfychair.db.dao.ModelVersionDao
import sh.hnet.comfychair.db.dao.TagDao
import sh.hnet.comfychair.db.dao.TechniqueDao
import sh.hnet.comfychair.db.dao.ToolDao
import sh.hnet.comfychair.db.entity.CachedMedia
import sh.hnet.comfychair.db.entity.ImageGenerationData
import sh.hnet.comfychair.db.entity.ImageResource
import sh.hnet.comfychair.db.entity.ImageTag
import sh.hnet.comfychair.db.entity.ImageTechnique
import sh.hnet.comfychair.db.entity.ImageTool
import sh.hnet.comfychair.db.entity.InstalledVersion
import sh.hnet.comfychair.db.entity.ModelVersion
import sh.hnet.comfychair.db.entity.Tag
import sh.hnet.comfychair.db.entity.Technique
import sh.hnet.comfychair.db.entity.Tool

@Database(
    entities = [
        CachedMedia::class,
        ModelVersion::class,
        ImageGenerationData::class,
        ImageResource::class,
        Tag::class,
        ImageTag::class,
        Tool::class,
        ImageTool::class,
        Technique::class,
        ImageTechnique::class,
        InstalledVersion::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ComfyChairDatabase : RoomDatabase() {
    abstract fun cachedMediaDao(): CachedMediaDao
    abstract fun modelVersionDao(): ModelVersionDao
    abstract fun imageGenerationDataDao(): ImageGenerationDataDao
    abstract fun imageResourceDao(): ImageResourceDao
    abstract fun tagDao(): TagDao
    abstract fun toolDao(): ToolDao
    abstract fun techniqueDao(): TechniqueDao
    abstract fun installedVersionDao(): InstalledVersionDao

    companion object {
        private const val DB_NAME = "comfychair.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS installed_versions (
                        versionId INTEGER NOT NULL,
                        serverId TEXT NOT NULL,
                        installedAt INTEGER NOT NULL,
                        PRIMARY KEY (versionId, serverId)
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_installed_versions_serverId ON installed_versions (serverId)")
            }
        }

        @Volatile
        private var instance: ComfyChairDatabase? = null

        fun getInstance(context: Context): ComfyChairDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ComfyChairDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { instance = it }
            }
        }
    }
}
