package fr.lc4918.trailog.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * SQL des migrations, au niveau du fichier pour que les tests rejouent EXACTEMENT ce que l'app execute :
 * une copie dans le test validerait une version qui n'est plus celle du code.
 */
internal object MigrationSql {
    /** Gabarit du fond AF3V, fige au moment de la migration 18->19 (cf. Providers.defaults() pour le
     *  detail des couches et des seuils d'echelle). */
    private const val AF3V_URL = "https://sig.af3v.org/index.php/lizmap/service/?repository=rep1" +
        "&project=veloroutes&LAYERS=voie_cyclable,segment_cyclable,poi_travaux&STYLES=&VERSION=1.3.0" +
        "&EXCEPTIONS=application/vnd.ogc.se_inimage&FORMAT=image/png&DPI=96&TRANSPARENT=TRUE" +
        "&SERVICE=WMS&REQUEST=GetMap&CRS=EPSG:3857&WIDTH=256&HEIGHT=256&BBOX={bbox-epsg-3857}"

    const val ADD_VERTICAL_EXAGGERATION =
        "ALTER TABLE settings ADD COLUMN verticalExaggeration INTEGER NOT NULL DEFAULT 0"
    const val ADD_BUBBLE_POSITION =
        "ALTER TABLE settings ADD COLUMN bubblePosition TEXT NOT NULL DEFAULT 'auto'"
    const val ADD_LEGEND_ASSET = "ALTER TABLE providers ADD COLUMN legendAsset TEXT"
    const val ADD_UPDATE_CHECK_MODE =
        "ALTER TABLE settings ADD COLUMN updateCheckMode TEXT NOT NULL DEFAULT 'auto'"
    const val TRANSPARENCY_TO_OPACITY =
        "UPDATE settings SET basemapControlOpacityPct = MAX(30, 100 - basemapControlOpacityPct)"
    const val ADD_BUBBLE_OPACITY =
        "ALTER TABLE settings ADD COLUMN bubbleOpacityPct INTEGER NOT NULL DEFAULT 100"
    // Le titre de l'infobulle passe de 14 à 16 par défaut : on n'ajuste que les bases restées sur
    // l'ancien défaut, pour ne pas écraser une taille choisie exprès.
    const val BUMP_BUBBLE_TITLE_FONT =
        "UPDATE settings SET bubbleTitleFont = 16 WHERE bubbleTitleFont = 14"
    // Defaut 0 volontairement : une base deja en place n'a jamais vu le jeu de demonstration, elle doit
    // donc le recevoir au premier lancement suivant la mise a jour, exactement comme une installation neuve.
    const val ADD_DEMO_SEEDED =
        "ALTER TABLE settings ADD COLUMN demoSeeded INTEGER NOT NULL DEFAULT 0"
    const val ADD_LINE_TAP_TOLERANCE =
        "ALTER TABLE settings ADD COLUMN lineTapToleranceDp INTEGER NOT NULL DEFAULT 16"
    // La tolerance des traces reprend celle deja reglee, jusqu'ici commune aux deux : les traces gardent
    // exactement la portee de tap qu'elles avaient, seuls les marqueurs se resserrent (ci-dessous).
    const val LINE_TAP_TOLERANCE_FROM_TAP_TOLERANCE =
        "UPDATE settings SET lineTapToleranceDp = tapToleranceDp"
    // Nouveau defaut des marqueurs (16 -> 10) : interroges en premier, ils l'emportent sur les traces, et une
    // tolerance large rendait une trace passant a cote inatteignable. N'ajuste que les bases restees a
    // l'ancien defaut, pour ne pas ecraser une valeur choisie expres. A jouer APRES la recopie ci-dessus.
    const val TIGHTEN_TAP_TOLERANCE =
        "UPDATE settings SET tapToleranceDp = 10 WHERE tapToleranceDp = 16"
    // Geocodage : desactive par defaut, y compris sur une base deja en place. C'est la seule fonction qui
    // interroge un service tiers en cours d'usage ; l'activer d'office chez qui ne l'a pas demandee irait
    // contre le parti pris hors-ligne de l'application.
    const val ADD_GEOCODING_ENABLED =
        "ALTER TABLE settings ADD COLUMN geocodingEnabled INTEGER NOT NULL DEFAULT 0"
    // Vide = instance publique par defaut, resolue a l'usage (cf. Photon.DEFAULT_URL) : figer l'URL en base
    // la laisserait perimee ici le jour ou le defaut du code change.
    const val ADD_GEOCODING_URL =
        "ALTER TABLE settings ADD COLUMN geocodingUrl TEXT NOT NULL DEFAULT ''"
    // Meme raison que l'URL du geocodeur : vide = instance publique, resolue a l'usage.
    const val ADD_ROUTING_URL =
        "ALTER TABLE settings ADD COLUMN routingUrl TEXT NOT NULL DEFAULT ''"
    // Defaut VTC : le velo a tout faire, celui qui emprunte le plus de chemins sans en exclure.
    const val ADD_ROUTING_PROFILE =
        "ALTER TABLE settings ADD COLUMN routingProfile TEXT NOT NULL DEFAULT 'hybrid'"
    const val ADD_ROUTE_PLANNER_ENABLED =
        "ALTER TABLE settings ADD COLUMN routePlannerEnabled INTEGER NOT NULL DEFAULT 0"
    // "system" : la bande du planificateur suit le theme de l'application tant qu'on n'a rien impose.
    // Colonne retiree depuis (cf. DROP_PLANNER_BAND_THEME), la bande ayant perdu son theme propre ; l'ajout
    // reste ici, comme toute migration deja jouee sur une base en place.
    const val ADD_PLANNER_BAND_THEME =
        "ALTER TABLE settings ADD COLUMN plannerBandTheme TEXT NOT NULL DEFAULT 'system'"
    const val ADD_CONTROL_BUTTONS_BACKGROUND =
        "ALTER TABLE settings ADD COLUMN controlButtonsBackground INTEGER NOT NULL DEFAULT 0"
    const val ADD_TRACK_MEASURE_ENABLED =
        "ALTER TABLE settings ADD COLUMN trackMeasureEnabled INTEGER NOT NULL DEFAULT 0"
    // Force de l'ombrage du relief, reglable depuis la fiche du fond DEM. Defaut 40 % : l'ombrage etait
    // fige a 50 % et ecrasait le fond sous lui.
    const val ADD_PROVIDER_OPACITY =
        "ALTER TABLE providers ADD COLUMN opacityPct INTEGER NOT NULL DEFAULT $DefaultDemOpacityPct"
    // Trois reglages d'affichage passent a "actif" par defaut. Applique sans condition, contrairement aux
    // ajustements de defaut precedents (cf. BUMP_BUBBLE_TITLE_FONT) : un booleen ne dit pas s'il vaut faux
    // par choix ou par defaut, et aucune version en circulation n'a d'utilisateur dont le choix serait a
    // menager. Les trois se redecochent d'un tap dans les reglages.
    const val SHOW_MAP_CONTROLS_BY_DEFAULT =
        "UPDATE settings SET showGpsButton = 1, routePlannerEnabled = 1, controlButtonsBackground = 1"
    const val ADD_MAP_BUTTON_SIZE =
        "ALTER TABLE settings ADD COLUMN mapButtonSizeDp INTEGER NOT NULL DEFAULT $DefaultMapButtonSizeDp"
    // La taille par defaut passe de la borne basse a 42 : n'ajuste que les bases restees a l'ancienne
    // valeur, comme le report de la taille du titre d'infobulle, pour ne pas ecraser un choix delibere.
    const val BUMP_MAP_BUTTON_SIZE =
        "UPDATE settings SET mapButtonSizeDp = $DefaultMapButtonSizeDp WHERE mapButtonSizeDp = $MinMapButtonSizeDp"
    // L'ombrage du relief quitte le "enabled" du fond DEM pour son propre reglage : celui-la ne dit plus
    // que sa presence dans le gestionnaire de couches, comme pour tout autre fond.
    const val ADD_HILLSHADE_ON =
        "ALTER TABLE settings ADD COLUMN hillshadeOn INTEGER NOT NULL DEFAULT 0"
    // L'etat affiche jusqu'ici par le fond DEM devient l'etat de l'ombrage : l'ecran ne change pas.
    const val HILLSHADE_FROM_DEM_ENABLED =
        "UPDATE settings SET hillshadeOn = COALESCE((SELECT enabled FROM providers WHERE type = 'DEM' LIMIT 1), 0)"
    // ... et le fond DEM reste liste, comme il l'etait toujours jusqu'ici. A jouer APRES la reprise
    // ci-dessus, qui lit ce qu'on ecrase.
    const val LIST_DEM_IN_CONTROL =
        "UPDATE providers SET enabled = 1 WHERE type = 'DEM'"
    // Les fonds d'un pays perdent le pays de leur nom : le drapeau le dit deja. Ne renomme que ceux restes
    // au nom seme - un nom choisi a la main dans les reglages ne doit pas etre ecrase.
    val DROP_COUNTRY_FROM_NAMES = """
        UPDATE providers SET name = CASE id
            WHEN 'ign_fr' THEN 'IGN Scan'
            WHEN 'ign_es' THEN 'IGN MTN'
            WHEN 'hu' THEN 'Turistautak'
            WHEN 'sk' THEN 'Freemap'
            WHEN 'at' THEN 'basemap.at'
            WHEN 'no' THEN 'Statkart'
            WHEN 'be' THEN 'NGI Topo'
            WHEN 'se' THEN 'Lantmäteriet'
            WHEN 'hr' THEN 'DGU TK25'
            WHEN 'de' THEN 'basemap.de'
            WHEN 'fi' THEN 'Maastokartta'
            WHEN 'si' THEN 'GURS DTK50'
            WHEN 'cz' THEN 'CUZK ZTM'
            WHEN 'gb' THEN 'OS Outdoor'
            WHEN 'pl' THEN 'GUGiK Topo'
            WHEN 'pt' THEN 'Carta Militar 25k'
            ELSE name END
        WHERE (id = 'ign_fr' AND name = 'France - IGN Scan') OR (id = 'ign_es' AND name = 'Espagne - IGN MTN') OR (id = 'hu' AND name = 'Hongrie - Turistautak') OR (id = 'sk' AND name = 'Slovaquie - Freemap') OR (id = 'at' AND name = 'Autriche - basemap.at') OR (id = 'no' AND name = 'Norvège - Statkart') OR (id = 'be' AND name = 'Belgique - NGI') OR (id = 'se' AND name = 'Suède - Lantmäteriet') OR (id = 'hr' AND name = 'Croatie - DGU') OR (id = 'de' AND name = 'Allemagne - Web Raster') OR (id = 'fi' AND name = 'Finlande - Maastokartta') OR (id = 'si' AND name = 'Slovénie - GURS DTK50') OR (id = 'cz' AND name = 'Tchéquie - CUZK ZTM') OR (id = 'gb' AND name = 'Royaume-Uni - OS Outdoor') OR (id = 'pl' AND name = 'Pologne - GUGiK Topo') OR (id = 'pt' AND name = 'Portugal - Carta Militar 25k')
    """.trimIndent()
    // Le titre du profil passe de 13 a 16 : n'ajuste que les bases restees a l'ancien defaut, comme le
    // report de la taille du titre d'infobulle.
    const val BUMP_PROFILE_TITLE_FONT =
        "UPDATE settings SET profTitleFont = 16 WHERE profTitleFont = 13"
    // La legende des pentes n'est plus un reglage mais un etat d'affichage, ferme au repos : on l'eteint
    // partout. Sans condition - la valeur precedente etait le defaut "affichee", que plus rien ne porte.
    const val HIDE_SLOPE_LEGEND = "UPDATE settings SET profileSlopeLegend = 0"
    // Le gestionnaire de fonds s'ouvre desormais plus large et plus opaque. N'ajuste que les bases restees
    // aux anciens defauts, comme les autres reports de defaut.
    const val WIDEN_BASEMAP_CONTROL =
        "UPDATE settings SET basemapControlWidthPct = 70 WHERE basemapControlWidthPct = 50"
    const val OPACIFY_BASEMAP_CONTROL =
        "UPDATE settings SET basemapControlOpacityPct = 90 WHERE basemapControlOpacityPct = 80"

    /** Colonnes de settings en version 39, dans l'ordre de SettingsEntity : la recopie ci-dessous les nomme
     *  une a une, un `SELECT *` reprenant la colonne dont on veut se defaire. */
    private const val SETTINGS_COLUMNS_V39 =
        "`id`, `units`, `sideMenuMode`, `tapToleranceDp`, `lineTapToleranceDp`, `terrain3d`, `hillshadeOn`, " +
        "`ambientCacheMb`, `defaultBasemapId`, `mbtilesDir`, `theme`, `profileGrid`, `profileSlope`, " +
        "`profileSlopeLegend`, `bubbleFont`, `profAxisFont`, `profTitleFont`, `profBarFont`, `profLegendFont`, " +
        "`profCursorFont`, `titleInfos`, `cursorInfos`, `statusBarTransparent`, `markerSize`, `importDir`, " +
        "`lastLat`, `lastLon`, `lastZoom`, `hasCamera`, `showScale`, `rotateGesturesEnabled`, `showGpsButton`, " +
        "`bubbleBold`, `profAxisBold`, `profTitleBold`, `profBarBold`, `profLegendBold`, `profCursorBold`, " +
        "`customTitle`, `avatarSource`, `showBasemapControlButton`, `basemapControlWidthPct`, " +
        "`basemapControlOpacityPct`, `bubbleTitleFont`, `bubbleTitleBold`, `simplifyRender`, `profileSmoothingM`, " +
        "`verticalExaggeration`, `bubblePosition`, `bubbleOpacityPct`, `updateCheckMode`, `demoSeeded`, " +
        "`geocodingEnabled`, `geocodingUrl`, `routingUrl`, `routingProfile`, `routePlannerEnabled`, " +
        "`controlButtonsBackground`, `trackMeasureEnabled`, `mapButtonSizeDp`"

    /**
     * Retrait de settings.plannerBandTheme : la bande du planificateur prend le theme de l'application.
     *
     * Par recopie dans une table neuve, et non par un `ALTER TABLE ... DROP COLUMN` que SQLite ne connait
     * qu'a partir de sa version 3.35 - Android 14 -, la ou l'application descend jusqu'a Android 7.
     *
     * Le schema et la liste de colonnes sont FIGES a ce qu'ils valent ici, comme les valeurs d'un fond
     * insere par migration : ils decrivent la base en version 39, et une colonne ajoutee plus tard le sera
     * par sa propre migration, pas en retouchant celle-ci.
     */
    val DROP_PLANNER_BAND_THEME: List<String> = listOf(
        settingsTableV39("settings_new"),
        "INSERT INTO `settings_new` ($SETTINGS_COLUMNS_V39) SELECT $SETTINGS_COLUMNS_V39 FROM `settings`",
        "DROP TABLE `settings`",
        "ALTER TABLE `settings_new` RENAME TO `settings`",
    )

    /** Le schema de settings en version 39, sous le nom demande : la migration s'en sert pour la table de
     *  destination, et le test pour se donner la table d'origine. */
    fun settingsTableV39(table: String): String =
        """
        CREATE TABLE `$table` (`id` INTEGER NOT NULL, `units` TEXT NOT NULL, `sideMenuMode` TEXT NOT
        NULL, `tapToleranceDp` INTEGER NOT NULL, `lineTapToleranceDp` INTEGER NOT NULL, `terrain3d` INTEGER
        NOT NULL, `hillshadeOn` INTEGER NOT NULL, `ambientCacheMb` INTEGER NOT NULL, `defaultBasemapId` TEXT
        NOT NULL, `mbtilesDir` TEXT NOT NULL, `theme` TEXT NOT NULL, `profileGrid` INTEGER NOT NULL,
        `profileSlope` INTEGER NOT NULL, `profileSlopeLegend` INTEGER NOT NULL, `bubbleFont` INTEGER NOT NULL,
        `profAxisFont` INTEGER NOT NULL, `profTitleFont` INTEGER NOT NULL, `profBarFont` INTEGER NOT NULL,
        `profLegendFont` INTEGER NOT NULL, `profCursorFont` INTEGER NOT NULL, `titleInfos` TEXT NOT NULL,
        `cursorInfos` TEXT NOT NULL, `statusBarTransparent` INTEGER NOT NULL, `markerSize` INTEGER NOT NULL,
        `importDir` TEXT NOT NULL, `lastLat` REAL NOT NULL, `lastLon` REAL NOT NULL, `lastZoom` REAL NOT NULL,
        `hasCamera` INTEGER NOT NULL, `showScale` INTEGER NOT NULL, `rotateGesturesEnabled` INTEGER NOT NULL,
        `showGpsButton` INTEGER NOT NULL, `bubbleBold` INTEGER NOT NULL, `profAxisBold` INTEGER NOT NULL,
        `profTitleBold` INTEGER NOT NULL, `profBarBold` INTEGER NOT NULL, `profLegendBold` INTEGER NOT NULL,
        `profCursorBold` INTEGER NOT NULL, `customTitle` TEXT NOT NULL, `avatarSource` TEXT NOT NULL,
        `showBasemapControlButton` INTEGER NOT NULL, `basemapControlWidthPct` INTEGER NOT NULL,
        `basemapControlOpacityPct` INTEGER NOT NULL, `bubbleTitleFont` INTEGER NOT NULL, `bubbleTitleBold`
        INTEGER NOT NULL, `simplifyRender` INTEGER NOT NULL, `profileSmoothingM` INTEGER NOT NULL,
        `verticalExaggeration` INTEGER NOT NULL, `bubblePosition` TEXT NOT NULL, `bubbleOpacityPct` INTEGER
        NOT NULL, `updateCheckMode` TEXT NOT NULL, `demoSeeded` INTEGER NOT NULL, `geocodingEnabled` INTEGER
        NOT NULL, `geocodingUrl` TEXT NOT NULL, `routingUrl` TEXT NOT NULL, `routingProfile` TEXT NOT NULL,
        `routePlannerEnabled` INTEGER NOT NULL, `controlButtonsBackground` INTEGER NOT NULL,
        `trackMeasureEnabled` INTEGER NOT NULL, `mapButtonSizeDp` INTEGER NOT NULL, PRIMARY KEY(`id`))
        """.trimIndent().replace('\n', ' ')

    // Completement des altitudes manquantes : desactive sur une base deja en place, comme le geocodage
    // avant lui. Les trois champs de service restent vides, donc aux defauts du code (cf. ElevationServices).
    const val ADD_FILL_MISSING_ELEVATION =
        "ALTER TABLE settings ADD COLUMN fillMissingElevation INTEGER NOT NULL DEFAULT 0"
    const val ADD_ELEVATION_IGN_URL =
        "ALTER TABLE settings ADD COLUMN elevationIgnUrl TEXT NOT NULL DEFAULT ''"
    const val ADD_ELEVATION_WORLD_URL =
        "ALTER TABLE settings ADD COLUMN elevationWorldUrl TEXT NOT NULL DEFAULT ''"
    const val ADD_ELEVATION_WORLD_KEY =
        "ALTER TABLE settings ADD COLUMN elevationWorldKey TEXT NOT NULL DEFAULT ''"

    val INSERT_AF3V = """
        INSERT OR IGNORE INTO providers
          (id, name, groupName, type, urlTemplate, apiKey, subdomains, minZoom, maxZoom,
           tileSize, attribution, transparent, enabled, builtin, sortOrder, folderId, legendAsset)
        SELECT 'af3v', 'Af3v Voies cyclables', 'Overlays', 'WMS',
               '$AF3V_URL',
               NULL, NULL, 0, 20, 256, NULL, 1, 1, 1,
               COALESCE((SELECT MAX(sortOrder) + 1 FROM providers), 0), NULL, 'legends/af3v.png'
    """.trimIndent()
}

@Database(
    entities = [FolderEntity::class, LayerEntity::class, ProviderEntity::class,
        CompositeEntity::class, SettingsEntity::class, BasemapFolderEntity::class],
    version = 40,
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
                db.execSQL(MigrationSql.ADD_VERTICAL_EXAGGERATION)
            }
        }

        // Ajout de settings.bubblePosition : migration explicite, même raison que la 16->17.
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_BUBBLE_POSITION)
            }
        }

        // Ajout de providers.legendAsset, et du fond AF3V qui l'inaugure. La table n'est semée qu'à vide
        // (cf. TrailogRepository), donc sans cet INSERT le nouveau fond n'apparaîtrait que sur une
        // installation neuve, jamais sur une base déjà en place. Valeurs figées volontairement, à l'image
        // d'un fond semé : une modification ultérieure de Providers.defaults() ne réécrit pas l'existant.
        // sortOrder = le dernier + 1, pour ne pas heurter ceux déjà en base.
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_LEGEND_ASSET)
                db.execSQL(MigrationSql.INSERT_AF3V)
            }
        }

        // Ajout de settings.updateCheckMode : migration explicite, même raison que la 16->17.
        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_UPDATE_CHECK_MODE)
            }
        }

        // settings.basemapControlOpacityPct portait une transparence malgré son nom (appliquée en
        // "1 - valeur"), et devient l'opacité réelle. Sans conversion, un panneau réglé à 20 %
        // deviendrait presque invisible au lieu de rester à 80 % d'opacité. La borne basse suit le
        // nouveau minimum du réglage : une transparence de 90 % donnerait 10 %, hors plage.
        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.TRANSPARENCY_TO_OPACITY)
            }
        }

        // Ajout de settings.bubbleOpacityPct : migration explicite, même raison que la 16->17.
        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_BUBBLE_OPACITY)
            }
        }

        // Nouveau défaut de bubbleTitleFont (14 -> 16) répercuté sur les bases restées à l'ancien défaut.
        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.BUMP_BUBBLE_TITLE_FONT)
            }
        }

        // Ajout de settings.demoSeeded : migration explicite, même raison que la 16->17. Le jeu de
        // démonstration lui-même n'est pas inséré ici mais au semis (cf. DemoData) : il passe par le même
        // import qu'un fichier choisi par l'utilisateur, ce qu'un INSERT SQL ne saurait pas reproduire.
        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_DEMO_SEEDED)
            }
        }

        // Ajout de settings.lineTapToleranceDp : migration explicite, même raison que la 16->17. La valeur
        // déjà réglée est recopiée sur les traces (sans quoi une tolérance choisie exprès serait ramenée au
        // défaut), puis les marqueurs passent au nouveau défaut plus serré. L'ordre compte : recopier après
        // le resserrement donnerait 10 aux traces.
        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_LINE_TAP_TOLERANCE)
                db.execSQL(MigrationSql.LINE_TAP_TOLERANCE_FROM_TAP_TOLERANCE)
                db.execSQL(MigrationSql.TIGHTEN_TAP_TOLERANCE)
            }
        }

        // Ajout de settings.geocodingEnabled / geocodingUrl : migration explicite, même raison que la 16->17.
        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_GEOCODING_ENABLED)
                db.execSQL(MigrationSql.ADD_GEOCODING_URL)
            }
        }

        // Ajout de settings.routingUrl / routingProfile : migration explicite, meme raison que la 16->17.
        private val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_ROUTING_URL)
                db.execSQL(MigrationSql.ADD_ROUTING_PROFILE)
            }
        }

        private val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_ROUTE_PLANNER_ENABLED)
                db.execSQL(MigrationSql.ADD_PLANNER_BAND_THEME)
            }
        }

        private val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_CONTROL_BUTTONS_BACKGROUND)
            }
        }

        private val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_TRACK_MEASURE_ENABLED)
            }
        }

        private val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_PROVIDER_OPACITY)
                db.execSQL(MigrationSql.SHOW_MAP_CONTROLS_BY_DEFAULT)
            }
        }

        private val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_MAP_BUTTON_SIZE)
            }
        }

        private val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.BUMP_MAP_BUTTON_SIZE)
            }
        }

        private val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_HILLSHADE_ON)
                db.execSQL(MigrationSql.HILLSHADE_FROM_DEM_ENABLED)
                db.execSQL(MigrationSql.LIST_DEM_IN_CONTROL)
            }
        }

        private val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.DROP_COUNTRY_FROM_NAMES)
            }
        }

        private val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.BUMP_PROFILE_TITLE_FONT)
            }
        }

        private val MIGRATION_36_37 = object : Migration(36, 37) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.HIDE_SLOPE_LEGEND)
            }
        }

        private val MIGRATION_37_38 = object : Migration(37, 38) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.WIDEN_BASEMAP_CONTROL)
                db.execSQL(MigrationSql.OPACIFY_BASEMAP_CONTROL)
            }
        }

        // La bande du planificateur perd son theme propre, donc la colonne qui le retenait. Migration
        // explicite plutot que destructive, comme la 16->17 : une base en place garde ses couches.
        private val MIGRATION_38_39 = object : Migration(38, 39) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.DROP_PLANNER_BAND_THEME.forEach { db.execSQL(it) }
            }
        }

        private val MIGRATION_39_40 = object : Migration(39, 40) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_FILL_MISSING_ELEVATION)
                db.execSQL(MigrationSql.ADD_ELEVATION_IGN_URL)
                db.execSQL(MigrationSql.ADD_ELEVATION_WORLD_URL)
                db.execSQL(MigrationSql.ADD_ELEVATION_WORLD_KEY)
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "trailog.db"
            ).addMigrations(MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36, MIGRATION_36_37, MIGRATION_37_38, MIGRATION_38_39, MIGRATION_39_40)
                .fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
