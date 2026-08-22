package fr.lc4918.trailog.ui.routes

import fr.lc4918.trailog.data.db.CompositeEntity
import fr.lc4918.trailog.data.db.ProviderEntity
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.map.compositeIdFromBasemapId

/**
 * Quels fonds sont REELLEMENT sous les yeux, dans leur ordre d'empilement.
 *
 * La question n'a rien d'evident : le reglage nomme un seul fond, mais ce nom peut designer un
 * **composite**, qui en empile deux. Et le relief n'est pas un fond, seulement un ombrage pose par-dessus
 * ce qui l'est.
 *
 * Sert au bouton "info" de la carte : la legende proposee doit etre celle de ce qu'on voit, pas celle du
 * reglage. Un composite mal forme - fond disparu, ou fond devenu un relief - doit donc retomber sur
 * quelque chose de sense plutot que de ne rien proposer.
 *
 * Pur et sans Android, pour que ces trois replis se verrouillent par des tests.
 */

/** Le composite designe par le reglage, s'il en designe un et qu'il est allume. */
internal fun activeComposite(s: SettingsEntity, comps: List<CompositeEntity>): CompositeEntity? =
    compositeIdFromBasemapId(s.defaultBasemapId)?.let { id -> comps.firstOrNull { it.id == id && it.enabled } }

/**
 * Les fonds affiches, du dessous vers le dessus.
 *
 * Trois cas, dans cet ordre :
 * 1. un composite allume dont le fond existe et n'est pas un relief : ce fond, puis son calque du dessus ;
 * 2. sinon le fond que le reglage nomme, s'il existe et n'est pas un relief ;
 * 3. sinon le premier fond disponible qui ne soit pas un relief.
 *
 * Le relief est ecarte partout, et c'est deliberé : il ne se suffit pas a lui-meme, et l'annoncer comme
 * fond affiche donnerait une legende qui ne decrit pas ce qu'on regarde.
 */
internal fun displayedProviders(
    s: SettingsEntity,
    provs: List<ProviderEntity>,
    comps: List<CompositeEntity>,
): List<ProviderEntity> {
    val composite = activeComposite(s, comps)
    if (composite != null) {
        val bg = provs.firstOrNull { it.id == composite.backgroundProviderId }
        if (bg != null && bg.type != "DEM") {
            val fg = provs.firstOrNull { it.id == composite.foregroundProviderId }
            return listOfNotNull(bg, fg?.takeIf { it.type != "DEM" })
        }
    }
    return listOfNotNull(
        provs.firstOrNull { it.id == s.defaultBasemapId && it.type != "DEM" }
            ?: provs.firstOrNull { it.type != "DEM" }
    )
}
