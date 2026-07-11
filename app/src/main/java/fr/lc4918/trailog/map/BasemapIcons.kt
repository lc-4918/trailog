package fr.lc4918.trailog.map

import fr.lc4918.trailog.data.db.ProviderEntity

/** Association id de provider -> code pays (ISO 3166-1 alpha-2), pour l'icône drapeau du Basemap Control.
 *  Liste fournie manuellement (cf. data/seed/Providers.kt, groupe "Pays") ; à défaut, icône globe générique. */
private val NATIONAL_FLAG_CODES = mapOf(
    "ign_fr" to "fr",
    "ign_es" to "es",
    "hu" to "hu",
    "sk" to "sk",
    "at" to "at",
    "no" to "no",
    "be" to "be",
    "se" to "se",
    "hr" to "hr",
)

/** Code pays du drapeau à afficher pour ce fond de plan, ou null si aucun (icône globe générique). */
fun flagCodeFor(provider: ProviderEntity): String? =
    if (provider.groupName == "Pays") NATIONAL_FLAG_CODES[provider.id] else null

/** Modèle Coil pointant vers l'asset SVG bundlé du drapeau (aucun appel réseau). */
fun flagAssetModel(code: String): String = "file:///android_asset/flags/$code.svg"
