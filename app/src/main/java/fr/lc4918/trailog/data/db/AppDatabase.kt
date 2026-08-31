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

    // Le fond des boutons quitte l'interrupteur pour un curseur d'opacite (0 a 100), dans la meme colonne
    // (cf. SettingsEntity.controlButtonsOpacityPct et son @ColumnInfo). Vrai devient l'opacite qu'il
    // dessinait jusqu'ici a taux fixe (cf. l'ancien ControlButtonBgAlpha = 0,9f), faux reste 0 - les deux
    // bornes du curseur disent exactement ce que disaient les deux etats de l'interrupteur.
    const val CONTROL_BUTTONS_BG_TO_OPACITY =
        "UPDATE settings SET controlButtonsBackground = " +
            "CASE controlButtonsBackground WHEN 1 THEN $DefaultControlButtonsOpacityPct ELSE 0 END"

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

    /**
     * Les quatre boutons de la carte qui s'affichent par defaut - burger, GPS, gestionnaire de fonds,
     * planificateur - ramenes a cet etat sur une base deja en place.
     *
     * La migration 30->31 en avait deja rallume deux ; le gestionnaire de fonds, lui, n'a jamais ete
     * repris par aucune, si bien qu'une base ou il etait eteint le restait indefiniment. Applique sans
     * condition, pour la meme raison qu'alors : un booleen ne dit pas s'il vaut faux par choix ou par
     * defaut, et les trois se redecochent d'un tap dans les reglages.
     *
     * Le burger n'y figure pas : il ne tient pas a un interrupteur mais au mode d'ouverture du menu
     * lateral, dont le defaut ("both") l'affiche deja. Le forcer effacerait le choix de qui ouvre son menu
     * au seul balayage - un geste, pas un bouton absent.
     */
    const val SHOW_DEFAULT_MAP_BUTTONS =
        "UPDATE settings SET showGpsButton = 1, showBasemapControlButton = 1, routePlannerEnabled = 1"

    // Ligne du restant sur la trace : allumee sur une base deja en place, comme sur une installation
    // neuve. Elle n'apparait que capteur allume et profil ouvert, elle n'encombre donc rien le reste du
    // temps - et c'est exactement la situation ou l'on veut la voir.
    const val ADD_PROFILE_REMAINING =
        "ALTER TABLE settings ADD COLUMN profileRemaining INTEGER NOT NULL DEFAULT 1"

    // Barre de retouche des traces : eteinte sur une base deja en place, comme les autres commandes qui ne
    // servent qu'a qui les demande. Elle modifie des traces importees, elle n'apparait pas d'elle-meme.
    const val ADD_TRACK_EDIT_ENABLED =
        "ALTER TABLE settings ADD COLUMN trackEditEnabled INTEGER NOT NULL DEFAULT 0"

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

    // Symbole de la position GPS : la puce sur une base deja en place, comme sur une installation neuve -
    // c'est le repere que l'utilisateur y voit deja, changer de dessin sous ses yeux n'aurait aucun sens.
    // La couleur reste vide, donc celle propre au symbole (cf. GpsMarkerStyle.defaultColor).
    const val ADD_GPS_MARKER_STYLE =
        "ALTER TABLE settings ADD COLUMN gpsMarkerStyle TEXT NOT NULL DEFAULT 'dot'"
    const val ADD_GPS_MARKER_COLOR =
        "ALTER TABLE settings ADD COLUMN gpsMarkerColor TEXT NOT NULL DEFAULT ''"
    const val ADD_GPS_MARKER_SIZE =
        "ALTER TABLE settings ADD COLUMN gpsMarkerSizeDp INTEGER NOT NULL DEFAULT $DefaultGpsMarkerSizeDp"

    // Alerte d'eloignement : eteinte sur une base deja en place, comme toute commande qui ne sert qu'a qui
    // la demande. Elle ne se contente pas d'occuper un bouton - elle projette la position sur une trace a
    // chaque mesure du capteur, et peut sonner. Rien de tout cela ne doit arriver sans l'avoir demande.
    const val ADD_OFF_TRACK_ALERT_ENABLED =
        "ALTER TABLE settings ADD COLUMN offTrackAlertEnabled INTEGER NOT NULL DEFAULT 0"
    const val ADD_OFF_TRACK_ALERT_DISTANCE =
        "ALTER TABLE settings ADD COLUMN offTrackAlertDistanceM INTEGER NOT NULL DEFAULT $DefaultOffTrackAlertM"
    const val ADD_OFF_TRACK_ALERT_SOUND =
        "ALTER TABLE settings ADD COLUMN offTrackAlertSound INTEGER NOT NULL DEFAULT 0"
    // Vide = le son de notification du telephone, resolu a l'usage : figer son URI en base la laisserait
    // pointer un fichier que l'utilisateur peut changer dans les reglages du systeme.
    const val ADD_OFF_TRACK_ALERT_SOUND_URI =
        "ALTER TABLE settings ADD COLUMN offTrackAlertSoundUri TEXT NOT NULL DEFAULT ''"

    /**
     * Preferences de trace, une colonne par discipline (cf. RoutingPrefs).
     *
     * Trois valeurs dans une seule colonne, comme les chips d'infos du profil : ce sont trois reponses a la
     * meme question - "comment tracer, pour cette discipline" - qui se lisent, s'ecrivent et se remettent a
     * zero ensemble. Quinze colonnes diraient la meme chose en quinze fois.
     *
     * Posees d'office sur une base en place, a la difference de l'alerte d'eloignement : celle-ci ajoutait
     * un comportement qu'on n'avait pas demande, celles-la corrigent un calcul qui ignorait les voies
     * vertes. Le defaut est le seul cas ou l'on preferait deja l'autre valeur sans pouvoir le dire.
     *
     * Les valeurs ci-dessous ont ete refaites une fois, la premiere mouture laissant encore quatre
     * disciplines sur cinq passer a cote des voies vertes (cf. RoutingPrefs.defaultFor). Elles sont
     * modifiees sur place, sans nouvelle migration : le defaut d'une colonne ajoutee ne sert qu'a remplir
     * les lignes deja la au moment de l'ajout, et il doit dire la meme chose que le defaut Kotlin - c'est
     * ce que verrouille MigrationsTest, colonne par colonne.
     */
    private const val PREFS_ROAD = "soft,balanced,paved"
    private const val PREFS_GRAVEL = "soft,seek,rough"
    private const val PREFS_HYBRID = "soft,balanced,rough"
    private const val PREFS_MTB = "soft,seek,rough"
    private const val PREFS_FOOT = "soft,seek,rough"
    const val ADD_ROUTE_PREFS_ROAD =
        "ALTER TABLE settings ADD COLUMN routePrefsRoad TEXT NOT NULL DEFAULT '$PREFS_ROAD'"
    const val ADD_ROUTE_PREFS_GRAVEL =
        "ALTER TABLE settings ADD COLUMN routePrefsGravel TEXT NOT NULL DEFAULT '$PREFS_GRAVEL'"
    const val ADD_ROUTE_PREFS_HYBRID =
        "ALTER TABLE settings ADD COLUMN routePrefsHybrid TEXT NOT NULL DEFAULT '$PREFS_HYBRID'"
    const val ADD_ROUTE_PREFS_MTB =
        "ALTER TABLE settings ADD COLUMN routePrefsMtb TEXT NOT NULL DEFAULT '$PREFS_MTB'"
    const val ADD_ROUTE_PREFS_FOOT =
        "ALTER TABLE settings ADD COLUMN routePrefsFoot TEXT NOT NULL DEFAULT '$PREFS_FOOT'"

    /**
     * Reprise des preferences de trace restees sur LEUR PREMIER DEFAUT.
     *
     * Les defauts par discipline ont ete refaits une fois (cf. RoutingPrefs.defaultFor), la premiere
     * mouture laissant quatre disciplines sur cinq passer a cote des voies vertes. Changer la valeur par
     * defaut ne suffisait pas : la colonne existe deja et porte l'ancienne, si bien qu'une installation
     * mise a jour continuait de calculer comme avant, en silence. C'est l'appareil qui l'a montre - le
     * velo de route y restait sans exigence de revetement, le gravel et la marche sans denivele accepte.
     *
     * Conditionnel, comme la taille du titre d'infobulle et celle des boutons : on ne reprend que ce qui
     * est reste sur l'ancien defaut. Une discipline reglee a la main garde ce qu'on lui a demande, et le
     * VTC de l'appareil en est l'exemple vivant - son revetement avait ete change avant que ce SQL
     * n'existe, et il n'a pas bouge.
     */
    private const val PREFS_ROAD_V1 = "soft,balanced,balanced"
    private const val PREFS_GRAVEL_V1 = "soft,balanced,rough"
    private const val PREFS_HYBRID_V1 = "soft,balanced,balanced"
    private const val PREFS_FOOT_V1 = "soft,balanced,rough"
    val RESET_ROUTE_PREFS = """
        UPDATE settings SET
          routePrefsRoad   = CASE routePrefsRoad   WHEN '$PREFS_ROAD_V1'   THEN '$PREFS_ROAD'   ELSE routePrefsRoad   END,
          routePrefsGravel = CASE routePrefsGravel WHEN '$PREFS_GRAVEL_V1' THEN '$PREFS_GRAVEL' ELSE routePrefsGravel END,
          routePrefsHybrid = CASE routePrefsHybrid WHEN '$PREFS_HYBRID_V1' THEN '$PREFS_HYBRID' ELSE routePrefsHybrid END,
          routePrefsFoot   = CASE routePrefsFoot   WHEN '$PREFS_FOOT_V1'   THEN '$PREFS_FOOT'   ELSE routePrefsFoot   END
    """.trimIndent()

    /**
     * Moteur d'itineraire (cf. RouteEngine), BRouter pour tout le monde - installations en place
     * comprises, ce que ne fait aucune commande ajoutee recemment.
     *
     * La raison est la meme que pour les preferences ci-dessus, et c'est la seule qui vaille ici : ce
     * n'est pas une fonction en plus, c'est le meme calcul rendu meilleur. Mesure sur les quatre trajets
     * de reference : a pied, Moulin-Neuf - Mirepoix passe de 15 a 87 % de voies douces, la voie verte
     * etant enfin empruntee ; sur Revel - Soreze, de 24 a 57 % de chemins. Laisser une base en place sur
     * l'ancien moteur, c'est lui garder un calcul dont on sait qu'il passe a cote.
     *
     * L'URL du service ne bouge pas de colonne : vide, elle designe l'instance publique DU MOTEUR RETENU
     * (cf. Router.baseOf).
     */
    const val ADD_ROUTE_ENGINE =
        "ALTER TABLE settings ADD COLUMN routeEngine TEXT NOT NULL DEFAULT 'brouter'"

    /**
     * Seconde URL de service : le moteur d'itineraire en a desormais UNE PAR MOTEUR.
     *
     * Une seule adresse pour deux moteurs se paie a l'usage. Reglee sur une instance Valhalla personnelle
     * puis le moteur change, l'application deposerait un profil BRouter chez Valhalla - et l'echec est
     * MUET, "Aucun itineraire" etant indiscernable de deux points non relies.
     *
     * Vide, comme toute URL de service ajoutee ici : figer l'instance publique en base la laisserait
     * perimee le jour ou le defaut du code change. Et c'est routingUrl qui reste celle de Valhalla, sans
     * renommage : ce qu'elle contient sur une base en place EST une URL Valhalla, le second moteur
     * n'existant pas quand elle a ete reglee.
     */
    const val ADD_ROUTING_URL_BROUTER =
        "ALTER TABLE settings ADD COLUMN routingUrlBrouter TEXT NOT NULL DEFAULT ''"

    /**
     * Bouton des points d'interet sur la carte. Eteint par defaut, comme la recherche de lieu, la mesure
     * et la retouche : ce sont les commandes qui s'ajoutent volontairement. Celle-ci interroge en plus un
     * service tiers a chaque deplacement de carte, ce qui vaut bien d'etre demande.
     */
    const val ADD_POI_ENABLED =
        "ALTER TABLE settings ADD COLUMN poiEnabled INTEGER NOT NULL DEFAULT 0"

    /**
     * Filtres des points d'interet : les categories DECOCHEES, et les groupes limites au theme velo.
     *
     * Les decochees et non les cochees, pour que le defaut vide veuille dire "tout afficher" : une
     * categorie ajoutee plus tard apparait alors d'elle-meme, la ou une liste de cochees l'aurait laissee
     * invisible jusqu'a ce que l'utilisateur aille la chercher.
     *
     * Deux colonnes de texte plutot que trente et une colonnes de booleens, comme les preferences de trace
     * : ce sont des reponses a la meme question, qui se lisent, s'ecrivent et se remettent a zero ensemble.
     */
    const val ADD_POI_HIDDEN =
        "ALTER TABLE settings ADD COLUMN poiHiddenCategories TEXT NOT NULL DEFAULT ''"
    const val ADD_POI_BIKE_GROUPS =
        "ALTER TABLE settings ADD COLUMN poiBikeGroups TEXT NOT NULL DEFAULT ''"

    // Suivi de la position par la carte, allume d'office y compris sur une base en place : il ne se
    // declenche que le capteur en marche, donc a un moment ou l'on a deja demande a etre localise, et il
    // n'ajoute alors aucune consommation - il deplace la carte avec des positions qui arrivaient de toute
    // facon. Qui prefere une carte immobile l'eteint ici (cf. MapFollow).
    const val ADD_MAP_FOLLOW_POSITION =
        "ALTER TABLE settings ADD COLUMN mapFollowPosition INTEGER NOT NULL DEFAULT 1"

    /**
     * Cache des points d'interet (cf. PoiCacheEntity). Une TABLE et non des colonnes : c'est une liste
     * dont la taille suit le terrain parcouru, pas un reglage.
     *
     * Vide a la creation, evidemment : un cache se remplit a l'usage, et une base migree n'a jamais vu ces
     * lieux. La seule chose qui compte ici est que la table existe avant la premiere requete.
     */
    /**
     * Huit derniers lieux retenus pour le planificateur (cf. PlannerHistory).
     *
     * Vide au depart : un historique se remplit a l'usage, et proposer quoi que ce soit avant le premier
     * trajet n'aurait aucun sens.
     */
    const val ADD_PLANNER_HISTORY =
        "ALTER TABLE settings ADD COLUMN plannerHistory TEXT NOT NULL DEFAULT ''"

    /**
     * Le fond par defaut passe d'OSM a Mapbox Outdoors - un fond de randonnee la ou la tuile OSM standard
     * est une carte de ville.
     *
     * Conditionnel, comme la taille du titre d'infobulle et celle des boutons avant lui : n'ajuste que les
     * bases restees sur l'ancien defaut. Qui a choisi son fond garde le sien - c'est le premier reglage
     * qu'on touche dans cette application, et l'ecraser serait la pire des surprises.
     */
    const val DEFAULT_BASEMAP_TO_MAPBOX =
        "UPDATE settings SET defaultBasemapId = 'mapbox_outdoors' WHERE defaultBasemapId = 'osm'"

    /**
     * La table du cache AVANT la colonne [ADD_POI_PINNED], gardee pour que le test de cette migration
     * parte d'une base telle qu'elle existait vraiment. Inutilisee par l'application.
     */
    val CREATE_POI_CACHE_V55 = """
        CREATE TABLE IF NOT EXISTS poi_cache (
            uuid TEXT NOT NULL PRIMARY KEY,
            label TEXT NOT NULL,
            lat REAL NOT NULL,
            lon REAL NOT NULL,
            categoryKey TEXT NOT NULL,
            city TEXT,
            imageUrl TEXT,
            webUrl TEXT,
            bikeTheme INTEGER NOT NULL,
            fetchedAt INTEGER NOT NULL
        )
    """.trimIndent()

    /**
     * Les points d'interet emportes avec une zone hors ligne echappent au menage du cache (cf.
     * PoiCacheEntity.pinned).
     *
     * A zero pour l'existant, et c'est juste : tout ce qui est deja en base y a ete mis en passant, au fil
     * des deplacements de carte, et rien de cela n'a ete demande.
     */
    const val ADD_POI_PINNED =
        "ALTER TABLE poi_cache ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0"

    /** Eteint sur une base deja en place comme sur une neuve : allumer l'ecran de quelqu'un sans qu'il
     *  l'ait demande est le genre de surprise qu'une mise a jour ne doit pas faire. */
    const val ADD_KEEP_SCREEN_ON =
        "ALTER TABLE settings ADD COLUMN keepScreenOn INTEGER NOT NULL DEFAULT 0"

    /** Allume sur une base deja en place comme sur une neuve : c'est le comportement qu'on vient de
     *  corriger, et le retirer par surprise ferait disparaitre les restaurants une seconde fois. */
    const val ADD_POI_OSM_COMPLEMENT =
        "ALTER TABLE settings ADD COLUMN poiOsmComplement INTEGER NOT NULL DEFAULT 1"

    /** Couloir des traces pour les points d'interet, en metres. 0 sur une base deja en place comme sur une
     *  neuve : c'est un filtre qui RETIRE de la carte, et l'allumer sans qu'on l'ait demande ferait
     *  disparaitre des lieux qu'on voyait la veille (cf. SettingsEntity.poiTrackCorridorM). */
    const val ADD_POI_TRACK_CORRIDOR =
        "ALTER TABLE settings ADD COLUMN poiTrackCorridorM INTEGER NOT NULL DEFAULT 0"

    /** L'instance Overpass devient reglable, comme le geocodeur et le moteur d'itineraire. Vide = celle
     *  qui etait en dur jusqu'ici, donc aucun changement de comportement a la mise a jour. */
    const val ADD_POI_OSM_URL =
        "ALTER TABLE settings ADD COLUMN poiOsmUrl TEXT NOT NULL DEFAULT ''"

    /** Le nouveau defaut de repere, pousse aux bases restees sur l'ancien (cf. MIGRATION_58_59). */
    const val DEFAULT_MARKER_ARROW =
        "UPDATE settings SET gpsMarkerStyle = 'arrow_filled', gpsMarkerSizeDp = 30, " +
            "gpsMarkerColor = '' WHERE gpsMarkerStyle = 'dot'"

    val CREATE_POI_CACHE = """
        CREATE TABLE IF NOT EXISTS poi_cache (
            uuid TEXT NOT NULL PRIMARY KEY,
            label TEXT NOT NULL,
            lat REAL NOT NULL,
            lon REAL NOT NULL,
            categoryKey TEXT NOT NULL,
            city TEXT,
            imageUrl TEXT,
            webUrl TEXT,
            bikeTheme INTEGER NOT NULL,
            fetchedAt INTEGER NOT NULL,
            pinned INTEGER NOT NULL DEFAULT 0
        )
    """.trimIndent()

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

/**
 * Version du schema, nommee plutot qu'ecrite dans l'annotation.
 *
 * `@Database` a une retention binaire : sa valeur n'est pas lisible a l'execution, donc pas verifiable par
 * un test. La constante l'est, et c'est ce qui permet a [MigrationChainTest] de comparer la chaine des
 * migrations a la version qu'elle est censee atteindre.
 *
 * **A incrementer avec toute evolution de schema**, et jamais seule : une migration doit l'accompagner
 * (cf. `ALL_MIGRATIONS`).
 */
internal const val DB_VERSION = 62

@Database(
    entities = [FolderEntity::class, LayerEntity::class, ProviderEntity::class,
        CompositeEntity::class, SettingsEntity::class, BasemapFolderEntity::class,
        PoiCacheEntity::class],
    version = DB_VERSION,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folders(): FolderDao
    abstract fun layers(): LayerDao
    abstract fun providers(): ProviderDao
    abstract fun composites(): CompositeDao
    abstract fun basemapFolders(): BasemapFolderDao
    abstract fun settings(): SettingsDao
    abstract fun pois(): PoiDao

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

        private val MIGRATION_40_41 = object : Migration(40, 41) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.SHOW_DEFAULT_MAP_BUTTONS)
            }
        }

        private val MIGRATION_41_42 = object : Migration(41, 42) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_TRACK_EDIT_ENABLED)
            }
        }

        private val MIGRATION_42_43 = object : Migration(42, 43) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_PROFILE_REMAINING)
            }
        }

        private val MIGRATION_43_44 = object : Migration(43, 44) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_GPS_MARKER_STYLE)
                db.execSQL(MigrationSql.ADD_GPS_MARKER_COLOR)
                db.execSQL(MigrationSql.ADD_GPS_MARKER_SIZE)
            }
        }

        private val MIGRATION_44_45 = object : Migration(44, 45) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_OFF_TRACK_ALERT_ENABLED)
                db.execSQL(MigrationSql.ADD_OFF_TRACK_ALERT_DISTANCE)
                db.execSQL(MigrationSql.ADD_OFF_TRACK_ALERT_SOUND)
                db.execSQL(MigrationSql.ADD_OFF_TRACK_ALERT_SOUND_URI)
            }
        }

        private val MIGRATION_45_46 = object : Migration(45, 46) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_ROUTE_PREFS_ROAD)
                db.execSQL(MigrationSql.ADD_ROUTE_PREFS_GRAVEL)
                db.execSQL(MigrationSql.ADD_ROUTE_PREFS_HYBRID)
                db.execSQL(MigrationSql.ADD_ROUTE_PREFS_MTB)
                db.execSQL(MigrationSql.ADD_ROUTE_PREFS_FOOT)
            }
        }

        private val MIGRATION_46_47 = object : Migration(46, 47) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_MAP_FOLLOW_POSITION)
            }
        }

        private val MIGRATION_47_48 = object : Migration(47, 48) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.RESET_ROUTE_PREFS)
            }
        }

        private val MIGRATION_48_49 = object : Migration(48, 49) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_ROUTE_ENGINE)
            }
        }

        private val MIGRATION_49_50 = object : Migration(49, 50) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_ROUTING_URL_BROUTER)
            }
        }

        private val MIGRATION_50_51 = object : Migration(50, 51) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_POI_ENABLED)
            }
        }

        private val MIGRATION_51_52 = object : Migration(51, 52) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_POI_HIDDEN)
                db.execSQL(MigrationSql.ADD_POI_BIKE_GROUPS)
            }
        }

        private val MIGRATION_52_53 = object : Migration(52, 53) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.CREATE_POI_CACHE)
            }
        }

        private val MIGRATION_53_54 = object : Migration(53, 54) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_PLANNER_HISTORY)
            }
        }

        private val MIGRATION_54_55 = object : Migration(54, 55) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.DEFAULT_BASEMAP_TO_MAPBOX)
            }
        }

        private val MIGRATION_55_56 = object : Migration(55, 56) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_POI_PINNED)
            }
        }

        private val MIGRATION_56_57 = object : Migration(56, 57) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_KEEP_SCREEN_ON)
            }
        }

        private val MIGRATION_57_58 = object : Migration(57, 58) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_POI_OSM_COMPLEMENT)
            }
        }

        /**
         * La fleche pleine devient le repere de position par defaut.
         *
         * Un changement de defaut ne touche qu'une installation NEUVE : la ligne de reglages existe deja
         * chez qui a l'application, avec la puce qu'elle portait a l'installation. Sans cette migration,
         * le nouveau defaut ne serait vu que par ceux qui n'ont jamais lance l'application.
         *
         * `WHERE gpsMarkerStyle = 'dot'` : seules les bases restees sur l'ancien defaut sont reprises.
         * Qui a choisi la fleche creuse ou le reticule garde son choix. Le prix est connu et assume - qui
         * a DELIBEREMENT choisi la puce se retrouve avec la fleche, ce reglage ne gardant pas trace de la
         * difference entre un defaut subi et un choix pose.
         *
         * La taille suit le symbole : une fleche a 20 dp, taille de la puce, se lit mal.
         */
        private val MIGRATION_58_59 = object : Migration(58, 59) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.DEFAULT_MARKER_ARROW)
            }
        }

        // Le fond des boutons de carte quitte l'interrupteur pour un curseur d'opacite. Meme colonne,
        // meme type SQL (INTEGER) des deux cotes : une conversion de valeur suffit, pas de recreation de
        // table (cf. le commentaire de CONTROL_BUTTONS_BG_TO_OPACITY).
        private val MIGRATION_59_60 = object : Migration(59, 60) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.CONTROL_BUTTONS_BG_TO_OPACITY)
            }
        }

        // Couloir des traces pour les points d'interet : une colonne de plus, eteinte par defaut.
        private val MIGRATION_60_61 = object : Migration(60, 61) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_POI_TRACK_CORRIDOR)
            }
        }

        // L'instance Overpass devient reglable : une colonne de plus, vide par defaut.
        private val MIGRATION_61_62 = object : Migration(61, 62) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MigrationSql.ADD_POI_OSM_URL)
            }
        }

        /**
         * Toutes les migrations, dans l'ordre, et **nommees** plutot qu'ecrites a la volee dans le
         * constructeur.
         *
         * C'est ce qui permet a un test de verifier la CHAINE et non seulement chaque maillon : qu'aucune
         * version ne manque entre la plus ancienne et celle que declare `@Database`. Le defaut vise est
         * precis - on ajoute une colonne, on ecrit sa migration, on incremente la version, et l'on oublie
         * de l'enregistrer ici. Rien ne le signale a la compilation, et Room se rabat alors sur ce qu'il
         * sait faire d'autre (cf. [OLDEST_SUPPORTED]).
         */
        internal val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36, MIGRATION_36_37, MIGRATION_37_38, MIGRATION_38_39, MIGRATION_39_40, MIGRATION_40_41, MIGRATION_41_42, MIGRATION_42_43, MIGRATION_43_44, MIGRATION_44_45, MIGRATION_45_46, MIGRATION_46_47, MIGRATION_47_48, MIGRATION_48_49, MIGRATION_49_50, MIGRATION_50_51, MIGRATION_51_52, MIGRATION_52_53, MIGRATION_53_54, MIGRATION_54_55, MIGRATION_55_56, MIGRATION_56_57, MIGRATION_57_58, MIGRATION_58_59, MIGRATION_59_60, MIGRATION_60_61, MIGRATION_61_62)

        /**
         * La plus ancienne version depuis laquelle on sait migrer.
         *
         * En dessous, aucun chemin n'existe et aucun ne sera ecrit : ces bases datent des toutes premieres
         * versions et plus personne n'en porte. C'est le SEUL cas ou l'on accepte de repartir a vide, et
         * `fallbackToDestructiveMigrationFrom` le borne a ces versions-la.
         *
         * Partout ailleurs, un chemin manquant fait desormais **echouer l'ouverture** au lieu d'effacer :
         * un plantage au demarrage se corrige par une mise a jour, des couches effacees ne reviennent pas.
         */
        internal const val OLDEST_SUPPORTED = 16

        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "trailog.db"
            ).addMigrations(*ALL_MIGRATIONS)
                // Destructeur pour les seules versions anterieures a la premiere migration ecrite.
                // Un trou dans la chaine entretenue leve desormais, au lieu d'effacer en silence.
                .fallbackToDestructiveMigrationFrom(*(1 until OLDEST_SUPPORTED).toList().toIntArray())
                .build().also { INSTANCE = it }
        }
    }
}
