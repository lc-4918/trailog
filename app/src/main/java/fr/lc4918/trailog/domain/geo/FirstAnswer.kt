package fr.lc4918.trailog.domain.geo

import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withTimeoutOrNull

/**
 * La premiere reponse de plusieurs sources interrogees EN MEME TEMPS, dans un delai donne.
 *
 * **Le defaut qu'elle repare.** Une position ponctuelle - "pars d'ou je suis" - n'etait demandee qu'au
 * GPS. Sous un toit, celui-ci ne rend rien, et s'accorde une bonne demi-minute avant de l'avouer : arrive
 * au travail, le planificateur annoncait "Position introuvable" alors que le fournisseur reseau savait
 * parfaitement ou se trouvait le telephone. Les interroger l'un apres l'autre aurait fait tourner le rond
 * d'attente une minute pour une reponse que le second tenait des la premiere seconde.
 *
 * **La premiere reponse, et non la meilleure.** Attendre tout le monde pour departager, c'est attendre le
 * plus lent - or c'est justement le muet qui est lent. Quand les reponses sont interchangeables pour
 * l'usage qu'on en fait, la fraicheur de la reponse vaut mieux que sa finesse.
 */
object FirstAnswer {

    /**
     * Interroge toutes les [sources] a la fois et rend la premiere reponse non nulle, ou null si aucune ne
     * repond avant [timeoutMs].
     *
     * Les autres demandes sont abandonnees des qu'une reponse arrive : ce sont des coroutines, leur
     * annulation remonte jusqu'a ce qu'elles avaient ouvert (cf. `CancellationSignal` du capteur).
     */
    suspend fun <T : Any> from(sources: List<suspend () -> T?>, timeoutMs: Long): T? {
        if (sources.isEmpty()) return null
        return withTimeoutOrNull(timeoutMs) {
            sources.map { source -> flow { emit(source()) } }.merge().filterNotNull().firstOrNull()
        }
    }
}
