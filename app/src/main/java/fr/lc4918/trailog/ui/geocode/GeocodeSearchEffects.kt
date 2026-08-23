package fr.lc4918.trailog.ui.geocode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import fr.lc4918.trailog.data.db.SettingsEntity
import fr.lc4918.trailog.geocode.Photon
import kotlinx.coroutines.delay

/**
 * L'interrogation du geocodeur depuis la barre de recherche de la carte.
 *
 * @param resultLimit combien de propositions demander. Plus que les quatre visibles : le defilement de la
 *   liste n'a de sens que s'il y a de quoi defiler, et le service facture le meme aller-retour dans les
 *   deux cas.
 */
@Composable
fun GeocodeSearchEffects(
    geo: GeocodeSearchState,
    settings: SettingsEntity?,
    resultLimit: Int,
) {
    val ctx = LocalContext.current
    // Interrogation du géocodeur, une frappe stabilisée. Sans ce délai, chaque lettre partirait en requête :
    // le service public le refuserait, et les réponses arriveraient dans le désordre.
    LaunchedEffect(geo.query, settings?.geocodingUrl) {
        val q = geo.query.trim()
        if (q.length < 3) { geo.results = emptyList(); geo.searching = false; return@LaunchedEffect }
        geo.searching = true
        delay(350)
        // Une seconde tentative avant d'abandonner, comme dans le planificateur : le premier appel paie
        // l'ouverture de la liaison et echoue parfois au delai. Un echec reste ici une liste vide - la
        // barre de recherche de la carte n'a pas de place pour un message.
        val base = settings?.geocodingUrl?.takeIf { it.isNotBlank() } ?: Photon.DEFAULT_URL
        val lang = ctx.resources.configuration.locales[0].language
        geo.results = (Photon.search(base, q, lang, resultLimit)
            ?: Photon.search(base, q, lang, resultLimit)).orEmpty()
        geo.searching = false
    }
    // Le géocodage désactivé dans les réglages alors qu'une recherche est en cours efface tout : sans cela
    // le marqueur noir et son infobulle survivraient au réglage qui les a fait naître.
    LaunchedEffect(settings?.geocodingEnabled) { if (settings?.geocodingEnabled == false) geo.clear() }
}
