package fr.lc4918.trailog.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FolderEntity::class, LayerEntity::class, ProviderEntity::class,
        CompositeEntity::class, SettingsEntity::class, BasemapFolderEntity::class],
    version = 17,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folders(): FolderDao
    abstract fun layers(): LayerDao
    abstract fun providers(): ProviderDao
    abstract fun composites(): CompositeDao
    abstract fun basemapFolders(): BasemapFolderDao
    abstract fun settings(): SettingsDao

    companion object {
        // Ajout de settings.verticalExaggeration : migration explicite (ALTER TABLE) plutôt que destructive,
        // pour ne pas effacer les couches/dossiers importés.
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN verticalExaggeration INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "trailog.db"
            ).addMigrations(MIGRATION_16_17).fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
