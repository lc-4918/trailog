package fr.lc4918.trailog.map.offline

/**
 * Paramètres validés à l'étape de configuration (SPEC offline_map.md section 3), avant que le moteur
 * de téléchargement (domaine B) ne les consomme.
 *
 * **Ici et non dans `ui/offline`**, où ce type a d'abord vécu : l'écran de configuration le PRODUIT,
 * mais ce sont le moteur ([OfflineTileDownloader]) et le dépôt qui le CONSOMMENT. Le laisser dans la
 * couche d'interface obligeait les deux à en dépendre, soit une couche basse important une couche
 * haute - la seule inversion de dépendance du projet.
 */
data class OfflineDownloadRequest(
    val bbox: Bbox,
    val minZoom: Int,
    val maxZoom: Int,
    val name: String,
    val continueOnError: Boolean,
    /**
     * Parcours a border, ou null pour prendre toute l'emprise.
     *
     * L'emprise reste renseignee dans les deux cas : elle sert au cadrage de l'apercu et aux metadonnees
     * du fichier MBTiles, qui decrit ce qu'il couvre par un rectangle - c'est le format qui l'impose.
     */
    val corridor: OfflineCorridor? = null,
    /**
     * Emporter aussi les points d'interet de la zone.
     *
     * Une case, et non un automatisme : c'est une requete de plus a un service tiers, et tout le monde ne
     * se sert pas de cette couche. Elle n'est proposee que si la couche est allumee dans les reglages -
     * offrir d'emporter ce qu'on ne peut pas afficher n'aurait aucun sens.
     */
    val withPois: Boolean = false,
)

/** Un parcours et la largeur telechargee de chaque cote, en metres. */
data class OfflineCorridor(val points: List<Pair<Double, Double>>, val radiusM: Double)
