package fr.lc4918.trailog.domain.model

/** Type d'une propriété d'infobulle. */
enum class PropType { TEXT, IMAGE, LINK }

/** Valeur typée d'une propriété. */
sealed interface PropValue {
    data class Text(val value: String) : PropValue
    data class Image(val path: String) : PropValue          // chemin fichier local (filesDir/images/...) ou URL http(s)
    data class Link(val text: String, val url: String) : PropValue
}

/** Définition de propriété au niveau de la couche (ordre + type, appliqués à tous les marqueurs). */
data class SchemaItem(val key: String, val type: PropType)

/** Un marqueur : id stable, position, et ses propriétés (ordre d'insertion préservé).
 *  pinnedImageKey : clé d'une propriété IMAGE affichée en image de garde au-dessus du titre (1 max). */
data class PointFeature(
    val id: String,
    val lon: Double,
    val lat: Double,
    val props: LinkedHashMap<String, PropValue> = LinkedHashMap(),
    val pinnedImageKey: String? = null,
)

/** Données complètes d'une couche de points. */
data class PointLayerData(
    val name: String,
    val schema: MutableList<SchemaItem>,
    val features: MutableList<PointFeature>,
)
