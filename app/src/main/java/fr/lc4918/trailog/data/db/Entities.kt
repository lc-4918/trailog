package fr.lc4918.trailog.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Dossier de l'arborescence. parentId null = dossier principal (racine). */
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val sortOrder: Int = 0,
)

/** Couche importée : peut contenir des points et/ou des traces (lignes), dans un même fichier GeoJSON.
 *  hasLine/hasPoints indiquent ce que contient réellement geometryFile, sans avoir à le reparser. */
@Entity(tableName = "layers")
data class LayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val folderId: Long?,
    val link: String? = null,
    val description: String? = null,
    val source: String = "import",         // import | bundled
    val importedAt: Long = System.currentTimeMillis(),
    val geometryFile: String,              // nom de fichier GeoJSON dans le stockage de l'app
    val color: String = "#1F6FB2",
    val schemaJson: String = "[]",         // schéma des propriétés des points (vide si pas de points)
    val distance: Double = 0.0,
    val ascent: Double = 0.0,
    val descent: Double = 0.0,
    val minEle: Double = 0.0,
    val maxEle: Double = 0.0,
    val movingTime: Double? = null,
    val hasZ: Boolean = false,
    val hasTime: Boolean = false,
    val hasLine: Boolean = false,
    val hasPoints: Boolean = false,
    val visible: Boolean = true,
    val sortOrder: Int = 0,
    val west: Double = 0.0, val south: Double = 0.0, val east: Double = 0.0, val north: Double = 0.0,
)

/** Dossier de l'arborescence du gestionnaire de fonds de plan (Basemap Control), distinct de `folders`. */
@Entity(tableName = "basemap_folders")
data class BasemapFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val sortOrder: Int = 0,
)

/** Fond de carte / overlay. URL et apiKey personnalisables (cf. SPEC section 4). */
@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val groupName: String,                 // Monde | Pays | Overlays | Local | Relief
    val type: String,                      // XYZ | WMS | WMTS | VECTOR | MBTILES | PMTILES | DEM
    val urlTemplate: String,
    val apiKey: String? = null,
    val subdomains: String? = null,        // ex "0,1,2,3"
    val minZoom: Int = 0,
    val maxZoom: Int = 19,
    val tileSize: Int = 256,
    val attribution: String? = null,
    val transparent: Boolean = false,
    val enabled: Boolean = true,
    val builtin: Boolean = true,
    val sortOrder: Int = 0,
    val folderId: Long? = null,            // dossier dans le Basemap Control (basemap_folders)
    // Legende de ce fond, chemin d'un asset bundle (ex. "legends/af3v.png"), ou null s'il n'en a pas.
    // Non nul : un bouton "info" apparait sur la carte des que ce fond est affiche, seul ou comme couche
    // d'un composite, et montre l'image par-dessus la carte (cf. legendAssetModel, MainViewModel.activeLegends).
    val legendAsset: String? = null,
    // Force du rendu, en %. Ne concerne aujourd'hui que le RELIEF : c'est l'exageration de son ombrage
    // (cf. StyleBuilder), le seul reglage qu'un fond DEM expose. Les autres types l'ignorent, leur
    // opacite se reglant la ou elle a un sens - sur le composite, pour une couche de premier plan.
    val opacityPct: Int = DefaultDemOpacityPct,
)

/** Force par defaut de l'ombrage du relief (%). Le bouton de remise a zero de son reglage y revient. */
const val DefaultDemOpacityPct = 30

/**
 * Rangs de depart des deux familles de fonds qui n'apparaissent pas dans le semis : un .mbtiles importe
 * ou telecharge, et un composite.
 *
 * Ils sont pris hors de portee des fonds semes (numerotes de 0 a une quarantaine) pour que le
 * gestionnaire les pose dans cet ordre - fonds distants, relief, .mbtiles, composites - sans avoir a
 * ranger les elements par type : le tri s'y fait sur le seul `sortOrder`, que le glisser-deposer
 * reecrit ensuite librement.
 */
const val MbtilesSortOrder = 1000
const val CompositeSortOrder = 2000

/** Fond composite = couche arrière-plan (opaque) + couche premier plan (avec transparence réglable). */
@Entity(tableName = "composites")
data class CompositeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val backgroundProviderId: String,
    val foregroundProviderId: String,
    val foregroundOpacity: Float = 0.5f,   // 0f..1f
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val folderId: Long? = null,            // dossier dans le Basemap Control (basemap_folders)
)

/** Réglages (une seule ligne, id = 0). */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 0,
    val units: String = "meters",          // meters | imperial
    val sideMenuMode: String = "both",     // burger | swipe | both
    val tapToleranceDp: Int = 10,      // marqueurs
    val lineTapToleranceDp: Int = 16,  // traces
    val terrain3d: Boolean = false,
    // Ombrage du relief affiche sur la carte. Distinct du "enabled" du fond DEM, qui dit seulement s'il
    // figure dans le gestionnaire de couches, comme pour tout autre fond : l'un se regle dans les
    // parametres, l'autre d'un tap sur la carte. Les confondre faisait disparaitre le relief du
    // gestionnaire au tap qui l'eteignait, sans plus aucun moyen de le rallumer.
    val hillshadeOn: Boolean = false,
    val ambientCacheMb: Int = 200,
    val defaultBasemapId: String = "osm",
    val mbtilesDir: String = "",           // chemin réel ; vide = dossier privé de l'app
    val theme: String = "system",          // system | light | dark
    val profileGrid: Boolean = true,       // grille du profil
    val profileSlope: Boolean = true,      // colorer l'aire par pente
    // Legende des pentes : masquee par defaut, et montree d'un tap sur le "i" du bandeau de profil. Ce
    // n'est plus une preference mais un etat d'affichage, qui se referme du meme geste.
    val profileSlopeLegend: Boolean = false,
    // Ligne du restant sous les totaux du profil : distance et D+ jusqu'au bout, depuis la position GPS
    // projetee sur la trace. Active par defaut - elle ne s'affiche que capteur allume ET profil ouvert,
    // c'est-a-dire dans la seule situation ou on la cherche.
    val profileRemaining: Boolean = true,
    val bubbleFont: Int = 14,              // taille police infobulle (sp)
    val profAxisFont: Int = 9,             // axes du profil
    val profTitleFont: Int = 16,           // titre (nom)
    val profBarFont: Int = 11,             // infos barre de titre
    val profLegendFont: Int = 9,           // légende des pentes
    val profCursorFont: Int = 11,          // infos du point courant
    val titleInfos: String = "dist,asc,desc",       // infos de la ligne de titre du profil
    val cursorInfos: String = "dist,ele,slope",     // infos du point courant
    val statusBarTransparent: Boolean = false,      // barre de statut transparente (carte dessous)
    val markerSize: Int = 36,                       // taille des marqueurs sur la carte (dp)
    val importDir: String = "",                     // dossier de départ pour l'import de fichiers (tree uri)
    val lastLat: Double = 46.6,                     // dernière caméra (défaut : centre France)
    val lastLon: Double = 2.4,
    val lastZoom: Double = 4.8,
    val hasCamera: Boolean = false,                 // true dès qu'une caméra a été enregistrée
    val showScale: Boolean = true,                  // échelle graphique sur la carte
    val rotateGesturesEnabled: Boolean = false,     // rotation de la carte (geste à 2 doigts)
    val showGpsButton: Boolean = true,              // bouton de localisation GPS sur la carte
    val bubbleBold: Boolean = false,
    val profAxisBold: Boolean = false,
    val profTitleBold: Boolean = true,
    val profBarBold: Boolean = false,
    val profLegendBold: Boolean = false,
    val profCursorBold: Boolean = false,
    val customTitle: String = "",                   // titre du menu latéral ; vide = titre par défaut traduit
    val avatarSource: String = "",                  // chemin fichier local ou URL ; vide = icône par défaut
    val showBasemapControlButton: Boolean = true,   // bouton du gestionnaire de fonds de plan sur la carte
    val basemapControlWidthPct: Int = 70,           // largeur du panneau (% de la largeur d'écran)
    // Opacité du panneau (%), appliquée telle quelle en alpha. A porté la transparence jusqu'à la
    // migration 20->21, qui a inversé les valeurs en base pour coller enfin au nom de la colonne.
    val basemapControlOpacityPct: Int = 90,
    val bubbleTitleFont: Int = 16,                  // taille police du titre de l'infobulle ("Marqueur")
    val bubbleTitleBold: Boolean = true,
    val simplifyRender: Boolean = true,             // simplifier la géométrie des traces dans le rendu de carte
    val profileSmoothingM: Int = 5,                 // lissage de l'altitude (m) avant calcul du profil affiché
    // Échelle verticale du profil : mètres d'altitude par centimètre physique ; 0 = Auto (remplit la hauteur).
    // Colonne DB nommée "verticalExaggeration" (le réglage était d'abord une exagération, remplacé par une
    // échelle absolue) : on garde le nom de colonne pour éviter une migration supplémentaire.
    @ColumnInfo(name = "verticalExaggeration") val profileVerticalScaleMPerCm: Int = 0,
    // Placement de l'infobulle par rapport au marqueur tapé (cf. BubblePosition) : "auto" = sous le marqueur,
    // basculée au-dessus si ça ne tient pas, bornée à l'écran, sans jamais bouger la carte. Les 9 autres
    // valeurs imposent un placement fixe autour du point et peuvent recentrer la carte pour tenir à l'écran.
    // Défaut "bottom_left" et non "auto" : l'infobulle se pose alors en bas à gauche du marqueur, qui reste
    // ainsi dégagé vers le haut et la droite, là où se lit la suite de la trace.
    val bubblePosition: String = "bottom_left",
    // Opacité du fond de l'infobulle (30..100 %), le contenu (texte, images) restant opaque. 100 = fond plein.
    val bubbleOpacityPct: Int = 100,
    // Vérification des mises à jour : "auto" au démarrage, ou "manual" (bouton dans les réglages).
    // Sans effet en build debug, qui a son propre applicationId et ne peut pas se remplacer par une
    // release signée d'une autre clé (cf. UpdateManager.isSupported).
    val updateCheckMode: String = "auto",
    // Le dossier de demonstration a-t-il deja ete seme ? Un drapeau, et non un test sur la presence du
    // dossier : celui qui le supprime ne doit pas le voir revenir a la mise a jour suivante. Passe a vrai
    // apres le semis, qu'il ait reussi ou non (cf. DemoData.seed) - un jeu d'exemple qui n'a pas pu
    // s'installer ne vaut pas de reessayer a chaque lancement.
    val demoSeeded: Boolean = false,
    // Recherche de lieu/adresse (bouton loupe sur la carte). Desactivee par defaut : c'est la seule
    // fonction qui interroge un service tiers en cours d'usage, elle n'a donc pas a s'imposer.
    val geocodingEnabled: Boolean = false,
    // Service d'autocompletion interroge (API Photon). Vide = instance publique (Photon.DEFAULT_URL) ;
    // renseignable pour viser sa propre instance, l'interet d'avoir choisi un geocodeur auto-hebergeable.
    val geocodingUrl: String = "",
    // Service d'itineraire (API Valhalla). Vide = instance publique (Valhalla.DEFAULT_URL).
    val routingUrl: String = "",
    // Discipline retenue par defaut a l'ouverture du planificateur d'itineraire (cf. RoutingProfile).
    val routingProfile: String = "hybrid",
    // Completer a l'import les altitudes que le fichier ne porte pas (cf. elevation/ElevationFiller).
    // Desactive par defaut, comme le geocodage : c'est la seule autre fonction qui fait partir des requetes
    // vers un service tiers sans qu'on l'ait demandee sur le moment.
    val fillMissingElevation: Boolean = false,
    // Service altimetrique francais (API Geoplateforme). Vide = instance publique (IgnElevation.DEFAULT_URL).
    val elevationIgnUrl: String = "",
    // Service altimetrique mondial (API OpenTopography). Vide = instance publique (OpenTopo.DEFAULT_URL).
    val elevationWorldUrl: String = "",
    // Cle du service mondial. Vide = celle livree avec l'application (OpenTopo.DEFAULT_KEY).
    val elevationWorldKey: String = "",
    // Bouton du planificateur d'itineraire sur la carte. Affiche par defaut : preparer un trajet est
    // l'une des raisons d'ouvrir l'application, et le bouton ne lance rien tout seul - ce sont les
    // etapes saisies qui font partir une requete, geste explicite s'il en est.
    val routePlannerEnabled: Boolean = true,
    // Fond blanc translucide derriere les boutons poses sur la carte. Actif par defaut : sans lui les
    // boutons flottent nus au-dessus de la carte, ce qui est plus leger mais devient illisible sur une
    // orthophoto ou un relief clair - et l'on ne choisit pas son fond de carte pour ses boutons.
    val controlButtonsBackground: Boolean = true,
    // Bouton de mesure sur trace. Desactive par defaut comme les deux boutons voisins, mais pour une autre
    // raison : il n'interroge aucun service, il ne sert simplement qu'a qui mesure ses parcours.
    val trackMeasureEnabled: Boolean = false,
    // Bouton de la barre de retouche des traces. Desactive par defaut, et pour la raison la plus forte de
    // toutes : ses outils MODIFIENT une trace importee, et son mode detourne les taps de la carte. Qui ne
    // retouche pas ses traces n'a aucune raison de croiser ce bouton.
    val trackEditEnabled: Boolean = false,
    // Cote du carre dessine derriere un bouton de carte (dp). La zone tactile, elle, ne bouge pas : elle
    // reste aux 48 dp que Material impose, quelle que soit la taille choisie. Les bornes vont donc du plus
    // discret (le carre ne fait alors que la taille de l'icone) au bouton Material plein, qui occupe toute
    // sa zone tactile.
    val mapButtonSizeDp: Int = DefaultMapButtonSizeDp,
    // Symbole de la position GPS sur la carte (cf. GpsMarkerStyle) : la puce par defaut, deux fleches qui
    // suivent l'orientation du telephone, ou une croix de visee.
    val gpsMarkerStyle: String = "dot",
    // Couleur du symbole. Vide = celle propre au symbole choisi (cf. GpsMarkerStyle.defaultColor) : la
    // couleur suit alors le symbole quand on en change, au lieu de figer le bleu de la puce sur une fleche.
    val gpsMarkerColor: String = "",
    // Cote du symbole de position (dp) - diametre de la puce, hauteur de la fleche ou de la croix.
    val gpsMarkerSizeDp: Int = DefaultGpsMarkerSizeDp,
)

/** Bornes du carre des boutons de carte (dp) : icone seule, ou bouton Material plein. */
const val MinMapButtonSizeDp = 36
const val MaxMapButtonSizeDp = 48

/** Taille par defaut : entre les deux bornes, assez large pour se voir sans etaler un aplat de 48 dp. */
const val DefaultMapButtonSizeDp = 42

/** Bornes du symbole de position (dp) : du point discret au repere qu'on retrouve d'un coup d'oeil. */
const val MinGpsMarkerSizeDp = 12
const val MaxGpsMarkerSizeDp = 48

/** Taille par defaut : celle de la puce d'origine (rayon 7 + contour 2,5), inchangee pour qui n'y touche pas. */
const val DefaultGpsMarkerSizeDp = 20
