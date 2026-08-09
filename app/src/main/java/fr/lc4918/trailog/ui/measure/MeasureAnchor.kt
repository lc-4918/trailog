package fr.lc4918.trailog.ui.measure

/**
 * Choix du point ou l'infobulle de la mesure vient s'ancrer.
 *
 * Le milieu du parcours mesure est le bon endroit - il relie l'infobulle aux deux marqueurs sans en
 * privilegier aucun - mais il n'est pas toujours a l'ecran : on a zoome pour poser le second point, et le
 * milieu est souvent reste loin derriere. L'infobulle allait alors se placer hors de la carte, et il
 * fallait dezoomer pour lire le resultat qu'on venait de demander.
 *
 * L'ancre glisse donc le long du parcours : le milieu s'il est visible, sinon le point du parcours le plus
 * proche de lui qui tienne dans l'emprise. Elle reste sur la trace mesuree dans les deux cas - jamais
 * plaquee au bord de la carte - et retourne d'elle-meme au milieu des qu'un deplacement le ramene a
 * l'ecran.
 */
object MeasureAnchor {

    /**
     * Index de l'ancre parmi [count] points ordonnes a pas constant le long du parcours, dont le central
     * est le milieu (cf. TrackMeasure.portion). Null si aucun point n'est visible.
     *
     * La recherche s'ecarte du milieu d'un cran a la fois, des deux cotes a la fois : le premier point
     * accepte est donc le plus proche du milieu en distance parcourue. [visible] n'est appele que sur les
     * points effectivement examines, et la reponse tient en un appel des que le milieu convient.
     */
    fun pick(count: Int, visible: (Int) -> Boolean): Int? {
        if (count <= 0) return null
        val mid = count / 2
        if (visible(mid)) return mid
        for (d in 1..maxOf(mid, count - 1 - mid)) {
            val before = mid - d
            if (before >= 0 && visible(before)) return before
            val after = mid + d
            if (after < count && visible(after)) return after
        }
        return null
    }
}
