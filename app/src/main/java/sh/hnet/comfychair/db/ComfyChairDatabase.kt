package sh.hnet.comfychair.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import sh.hnet.comfychair.db.dao.CachedMediaDao
import sh.hnet.comfychair.db.dao.ImageGenerationDataDao
import sh.hnet.comfychair.db.dao.ImageResourceDao
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
        ImageTechnique::class
    ],
    version = 1,
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

    companion object {
        private const val DB_NAME = "comfychair.db"

        @Volatile
        private var instance: ComfyChairDatabase? = null

        fun getInstance(context: Context): ComfyChairDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ComfyChairDatabase::class.java,
                    DB_NAME
                ).build().also { instance = it }
            }
        }
    }
}
