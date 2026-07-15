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
    version = 19,
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
        /** Gabarit du fond AF3V, figé au moment de la migration 18->19 (cf. Providers.defaults() pour le
         *  détail des couches et des seuils d'échelle). */
        private const val AF3V_URL = "https://sig.af3v.org/index.php/lizmap/service/?repository=rep1" +
            "&project=veloroutes&LAYERS=voie_cyclable,segment_cyclable,poi_travaux&STYLES=&VERSION=1.3.0" +
            "&EXCEPTIONS=application/vnd.ogc.se_inimage&FORMAT=image/png&DPI=96&TRANSPARENT=TRUE" +
            "&SERVICE=WMS&REQUEST=GetMap&CRS=EPSG:3857&WIDTH=256&HEIGHT=256&BBOX={bbox-epsg-3857}"

        // Ajout de settings.verticalExaggeration : migration explicite (ALTER TABLE) plutôt que destructive,
        // pour ne pas effacer les couches/dossiers importés.
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN verticalExaggeration INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Ajout de settings.bubblePosition : migration explicite, même raison que la 16->17.
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN bubblePosition TEXT NOT NULL DEFAULT 'auto'")
            }
        }

        // Ajout de providers.legendAsset, et du fond AF3V qui l'inaugure. La table n'est semée qu'à vide
        // (cf. TrailogRepository), donc sans cet INSERT le nouveau fond n'apparaîtrait que sur une
        // installation neuve, jamais sur une base déjà en place. Valeurs figées volontairement, à l'image
        // d'un fond semé : une modification ultérieure de Providers.defaults() ne réécrit pas l'existant.
        // sortOrder = le dernier + 1, pour ne pas heurter ceux déjà en base.
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE providers ADD COLUMN legendAsset TEXT")
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO providers
                      (id, name, groupName, type, urlTemplate, apiKey, subdomains, minZoom, maxZoom,
                       tileSize, attribution, transparent, enabled, builtin, sortOrder, folderId, legendAsset)
                    SELECT 'af3v', 'Af3v Voies cyclables', 'Overlays', 'WMS',
                           '$AF3V_URL',
                           NULL, NULL, 0, 20, 256, NULL, 1, 1, 1,
                           COALESCE((SELECT MAX(sortOrder) + 1 FROM providers), 0), NULL, 'legends/af3v.png'
                    """.trimIndent()
                )
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "trailog.db"
            ).addMigrations(MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19)
                .fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
