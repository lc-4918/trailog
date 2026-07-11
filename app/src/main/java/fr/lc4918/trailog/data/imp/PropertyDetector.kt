package fr.lc4918.trailog.data.imp

import fr.lc4918.trailog.domain.model.PropValue

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg")

/** Détecte automatiquement le type d'une propriété importée (texte/image/lien) d'après sa valeur brute :
 *  chemin/URL se terminant par une extension d'image -> Image ; URL http(s) -> Lien ; sinon -> Texte. */
fun detectPropValue(raw: String): PropValue {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return PropValue.Text(trimmed)
    val looksLikePath = trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
        trimmed.startsWith("file://") || trimmed.contains('/') || trimmed.contains('\\')
    val ext = trimmed.substringAfterLast('.', "").substringBefore('?').lowercase()
    if (looksLikePath && ext in IMAGE_EXTENSIONS) return PropValue.Image(trimmed)
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return PropValue.Link(trimmed, trimmed)
    return PropValue.Text(trimmed)
}

/** Vrai si la chaîne ressemble à un chemin/URL d'image (mêmes règles que detectPropValue, sans construire de PropValue). */
fun looksLikeImagePath(raw: String): Boolean {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return false
    val looksLikePath = trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
        trimmed.startsWith("file://") || trimmed.contains('/') || trimmed.contains('\\')
    val ext = trimmed.substringAfterLast('.', "").substringBefore('?').lowercase()
    return looksLikePath && ext in IMAGE_EXTENSIONS
}
