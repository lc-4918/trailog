package fr.lc4918.trailog.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import fr.lc4918.trailog.domain.model.RouteEngine
import fr.lc4918.trailog.domain.model.RoutingPrefs
import fr.lc4918.trailog.domain.model.RoutingProfile

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
    // Moteur d'itineraire : "valhalla" ou "brouter" (cf. RouteEngine). Les deux repondent aux memes cinq
    // disciplines et aux memes trois preferences, chacun dans sa langue - c'est ce qui rend la comparaison
    // honnete, et c'est pour comparer que ce reglage existe.
    val routeEngine: String = "brouter",
    // Service d'itineraire, UNE URL PAR MOTEUR (cf. routeUrl/withRouteUrl plus bas). Vide = instance
    // publique du moteur (cf. Router.baseOf). Deux colonnes et non une : une seule adresse pour deux
    // moteurs se paie a l'usage - reglee sur une instance Valhalla puis le moteur change, l'application
    // deposerait un profil BRouter chez Valhalla, ce qui echoue en SILENCE, "Aucun itineraire" etant
    // indiscernable de deux points non relies.
    // routingUrl est celle de Valhalla, et garde son nom d'avant le second moteur : ce qu'elle contient
    // sur une base en place EST une URL Valhalla, la renommer ne dirait rien de plus et couterait une
    // refonte de table.
    val routingUrl: String = "",
    val routingUrlBrouter: String = "",
    // Discipline retenue par defaut a l'ouverture du planificateur d'itineraire (cf. RoutingProfile).
    val routingProfile: String = "hybrid",
    // Preferences de trace, une colonne par discipline : "voies,relief,revetement" (cf. RoutingPrefs, et
    // routePrefs/withRoutePrefs plus bas pour y acceder sans repandre un when). Elles ne se partagent pas
    // entre disciplines - on ne demande pas la meme chose a un velo de route et a un VTT - et c'est la
    // raison des cinq colonnes.
    val routePrefsRoad: String = "soft,balanced,paved",
    val routePrefsGravel: String = "soft,seek,rough",
    val routePrefsHybrid: String = "soft,balanced,rough",
    val routePrefsMtb: String = "soft,seek,rough",
    val routePrefsFoot: String = "soft,seek,rough",
    // Bouton des points d'interet sur la carte (cf. poi/Datatourisme). Eteint par defaut : c'est une
    // commande qui s'ajoute volontairement, et qui interroge un service tiers a chaque deplacement.
    val poiEnabled: Boolean = false,
    // Filtres des points d'interet : categories DECOCHEES et groupes limites au theme velo, en CSV de
    // cles (cf. PoiFilters). Les decochees, pour qu'un reglage vide veuille dire "tout afficher".
    val poiHiddenCategories: String = "",
    val poiBikeGroups: String = "",
    // La carte suit la position tant que le capteur tourne, et rend la main cinq secondes apres chaque
    // geste (cf. MapFollow). Actif par defaut : en sortie, c'est ce qu'on attend d'une carte allumee.
    val mapFollowPosition: Boolean = true,
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
    // Bouton de l'alerte d'eloignement (la cloche, sous le planificateur). Eteint par defaut, comme les
    // autres commandes qui ne servent qu'a qui les demande.
    //
    // Il ne va JAMAIS sans le bouton de localisation : une alerte a la position pour seule matiere, et
    // l'allumer sans que rien ne montre ni ne coupe le capteur serait une position qui tourne en cachette.
    // Les reglages tiennent le lien dans les deux sens (cf. SettingsScreen.MapTab).
    val offTrackAlertEnabled: Boolean = false,
    // Ecart a la trace suivie (m) au-dela duquel l'alerte s'affiche en bas de l'ecran.
    val offTrackAlertDistanceM: Int = DefaultOffTrackAlertM,
    // Emettre un son en plus de la banniere.
    val offTrackAlertSound: Boolean = false,
    // Son retenu, parmi les notifications du telephone (URI du selecteur systeme). Vide = celui que le
    // telephone donne pour ses notifications : un son n'a pas a etre choisi pour etre entendu.
    val offTrackAlertSoundUri: String = "",
)

/** Bornes du carre des boutons de carte (dp) : icone seule, ou bouton Material plein. */
const val MinMapButtonSizeDp = 36
const val MaxMapButtonSizeDp = 48

/** Taille par defaut : entre les deux bornes, assez large pour se voir sans etaler un aplat de 48 dp. */
const val DefaultMapButtonSizeDp = 42

/** Bornes du symbole de position (dp) : du point discret au repere qu'on retrouve d'un coup d'oeil. */
const val MinGpsMarkerSizeDp = 12
const val MaxGpsMarkerSizeDp = 48

/**
 * Taille par defaut d'une installation neuve : celle de la puce d'origine (rayon 7 + contour 2,5),
 * inchangee pour qui n'y touche pas. Les autres symboles ont la leur (cf. GpsMarkerStyle.defaultSizeDp),
 * appliquee quand on en change ; la constante ne peut pas la citer, un `const` exigeant une valeur
 * calculable a la compilation.
 */
const val DefaultGpsMarkerSizeDp = 20

/**
 * La cloche de l'alerte d'eloignement s'affiche-t-elle sur la carte ?
 *
 * Les deux reglages, et non le seul interrupteur de l'alerte : elle n'existe pas sans le bouton de
 * localisation (cf. [SettingsEntity.offTrackAlertEnabled]). L'ecran des reglages tient deja le lien dans
 * les deux sens, mais la carte ne s'y fie pas - une base restauree, ou ecrite par une version anterieure,
 * peut porter la combinaison interdite, et c'est la carte qui aurait tort de l'afficher.
 */
val SettingsEntity.offTrackAlertVisible: Boolean
    get() = offTrackAlertEnabled && showGpsButton

/**
 * Bornes de l'ecart declenchant l'alerte d'eloignement (m), et son pas de reglage.
 *
 * Vingt metres est le plancher utile : en dessous, l'imprecision d'un GPS de telephone declencherait
 * l'alerte alors qu'on marche sur la trace. Cinq cents metres est le plafond : au-dela, on ne s'est plus
 * ecarte, on est ailleurs, et l'alerte arriverait trop tard pour servir.
 */
const val MinOffTrackAlertM = 20
const val MaxOffTrackAlertM = 500
const val OffTrackAlertStepM = 10

/** Ecart par defaut : celui a partir duquel le profil dit deja l'ecart a la trace, sous les totaux. */
const val DefaultOffTrackAlertM = 50

/**
 * Preferences de trace de la discipline [profile], relues de leur colonne.
 *
 * Ici et non chez l'appelant : la correspondance discipline -> colonne est la seule chose qui sache ou
 * chacune est rangee, et trois lecteurs (le planificateur, la mesure depuis un point, le pont de jonction)
 * la recopieraient sinon a l'identique - jusqu'au jour ou l'un d'eux oublierait une discipline.
 */
fun SettingsEntity.routePrefs(profile: RoutingProfile): RoutingPrefs = RoutingPrefs.of(
    when (profile) {
        RoutingProfile.ROAD_BIKE -> routePrefsRoad
        RoutingProfile.GRAVEL -> routePrefsGravel
        RoutingProfile.HYBRID_BIKE -> routePrefsHybrid
        RoutingProfile.MOUNTAIN_BIKE -> routePrefsMtb
        RoutingProfile.FOOT -> routePrefsFoot
    },
    profile,
)

/**
 * URL du service pour le moteur [engine], relue de sa colonne. Vide = son instance publique.
 *
 * Meme montage que les preferences ci-dessus, et pour la meme raison : la correspondance moteur -> colonne
 * vit ici seule, plutot que recopiee chez les trois lecteurs jusqu'au jour ou l'un d'eux oublierait un
 * moteur - et enverrait alors ses requetes a l'autre.
 */
fun SettingsEntity.routeUrl(engine: RouteEngine): String = when (engine) {
    RouteEngine.VALHALLA -> routingUrl
    RouteEngine.BROUTER -> routingUrlBrouter
}

/** La meme chose en ecriture : rend la copie des reglages ou seule l'URL de [engine] a change. */
fun SettingsEntity.withRouteUrl(engine: RouteEngine, url: String): SettingsEntity = when (engine) {
    RouteEngine.VALHALLA -> copy(routingUrl = url)
    RouteEngine.BROUTER -> copy(routingUrlBrouter = url)
}

/** La meme chose en ecriture : rend la copie des reglages ou seule la discipline [profile] a change. */
fun SettingsEntity.withRoutePrefs(profile: RoutingProfile, prefs: RoutingPrefs): SettingsEntity {
    val csv = prefs.asCsv()
    return when (profile) {
        RoutingProfile.ROAD_BIKE -> copy(routePrefsRoad = csv)
        RoutingProfile.GRAVEL -> copy(routePrefsGravel = csv)
        RoutingProfile.HYBRID_BIKE -> copy(routePrefsHybrid = csv)
        RoutingProfile.MOUNTAIN_BIKE -> copy(routePrefsMtb = csv)
        RoutingProfile.FOOT -> copy(routePrefsFoot = csv)
    }
}
