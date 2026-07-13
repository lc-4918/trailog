package fr.lc4918.trailog.data.db

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
)

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
    val tapToleranceDp: Int = 16,
    val terrain3d: Boolean = false,
    val ambientCacheMb: Int = 200,
    val defaultBasemapId: String = "osm",
    val mbtilesDir: String = "",           // chemin réel ; vide = dossier privé de l'app
    val theme: String = "system",          // system | light | dark
    val profileGrid: Boolean = true,       // grille du profil
    val profileSlope: Boolean = true,      // colorer l'aire par pente
    val profileSlopeLegend: Boolean = true,// afficher la légende des pentes
    val bubbleFont: Int = 14,              // taille police infobulle (sp)
    val profAxisFont: Int = 9,             // axes du profil
    val profTitleFont: Int = 13,           // titre (nom)
    val profBarFont: Int = 11,             // infos barre de titre
    val profLegendFont: Int = 9,           // légende des pentes
    val profCursorFont: Int = 11,          // infos du point courant
    val titleInfos: String = "dist,asc,desc,dur",   // infos de la ligne de titre du profil
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
    val showGpsButton: Boolean = false,             // bouton de localisation GPS sur la carte
    val bubbleBold: Boolean = false,
    val profAxisBold: Boolean = false,
    val profTitleBold: Boolean = true,
    val profBarBold: Boolean = false,
    val profLegendBold: Boolean = false,
    val profCursorBold: Boolean = false,
    val customTitle: String = "",                   // titre du menu latéral ; vide = titre par défaut traduit
    val avatarSource: String = "",                  // chemin fichier local ou URL ; vide = icône par défaut
    val showBasemapControlButton: Boolean = true,   // bouton du gestionnaire de fonds de plan sur la carte
    val basemapControlWidthPct: Int = 50,           // largeur du panneau (% de la largeur d'écran)
    val basemapControlOpacityPct: Int = 20,         // transparence du panneau (%)
    val bubbleTitleFont: Int = 14,                  // taille police du titre de l'infobulle ("Marqueur")
    val bubbleTitleBold: Boolean = true,
)
