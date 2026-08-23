package fr.lc4918.trailog.ui.routes

import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.data.db.SettingsEntity

/**
 * Ou la camera se pose quand l'ecran s'ouvre - et a chaque ROTATION, qui le recree.
 *
 * **Pourquoi cette regle est ici et non dans l'effet qui l'applique.** Elle a quatre branches ordonnees,
 * et l'ordre EST la regle : c'est exactement le genre de chose qu'un test doit tenir, et que l'effet ne
 * peut pas offrir - `MapController` ne bouge aucune camera tant qu'aucune `MapView` n'existe, et il n'en
 * existe pas sur la JVM. Descendue ici, la decision se verifie ; ne reste la-haut qu'un appel.
 *
 * **La position d'abord, et c'est ce qui manquait.** Une rotation recree l'activite, donc la `MapView`,
 * donc ce placement : la carte sautait au cadrage enregistre - ou, faute de camera enregistree, sur
 * l'emprise de toutes les couches, ce qui la faisait dezoomer - juste apres qu'on l'ait amenee ou l'on
 * voulait. Quand le suivi de la carte est allume et le capteur en marche, l'endroit ou se poser n'est pas
 * une question ouverte : c'est la position.
 */
sealed interface CameraTarget {

    /** Un point, avec le zoom a prendre ; [zoom] nul veut dire "garder celui de la carte". */
    data class Point(val lat: Double, val lon: Double, val zoom: Double?) : CameraTarget

    /** Une emprise a faire tenir dans l'ecran. */
    data class Bounds(val west: Double, val south: Double, val east: Double, val north: Double) : CameraTarget
}

object CameraPlacement {

    /** Faute de tout le reste : la France entiere, a l'echelle ou on la voit d'un coup. */
    val France = CameraTarget.Point(46.6, 2.4, 4.8)

    /**
     * Les quatre branches, dans l'ordre :
     *
     * 1. **la position**, si la carte la suit et qu'on en connait une - elle prime sur tout, y compris sur
     *    un cadrage enregistre, qui decrit ou l'on regardait et non ou l'on est ;
     * 2. le **dernier cadrage enregistre**, qui rouvre l'application la ou on l'a laissee ;
     * 3. l'**emprise des couches visibles**, pour un premier lancement qui a deja des traces ;
     * 4. la **France**, quand il n'y a rien du tout.
     *
     * Le zoom de la premiere branche est celui qui etait enregistre, quand il y en a un : c'est celui
     * qu'on avait avant de tourner l'ecran, et une rotation n'est pas une demande de changer d'echelle.
     * A defaut, null - on garde celui ou la carte vient de s'ouvrir plutot que d'en inventer un.
     */
    fun target(
        followsPosition: Boolean,
        userLocation: Pair<Double, Double>?,
        settings: SettingsEntity,
        layers: List<LayerEntity>,
    ): CameraTarget {
        val pos = userLocation
        if (followsPosition && pos != null) {
            return CameraTarget.Point(pos.first, pos.second, settings.lastZoom.takeIf { settings.hasCamera })
        }
        if (settings.hasCamera) {
            return CameraTarget.Point(settings.lastLat, settings.lastLon, settings.lastZoom)
        }
        return boundsOf(layers) ?: France
    }

    /**
     * L'emprise des couches visibles, ou null s'il n'y en a aucune d'exploitable.
     *
     * Les zeros sont ecartes : une couche dont l'emprise n'a pas ete calculee les porte, et les garder
     * etirerait le cadrage jusqu'au golfe de Guinee.
     */
    private fun boundsOf(layers: List<LayerEntity>): CameraTarget.Bounds? {
        val visibles = layers.filter { it.visible }
        val w = visibles.map { it.west }.filter { it != 0.0 }.minOrNull() ?: return null
        val s = visibles.map { it.south }.filter { it != 0.0 }.minOrNull() ?: return null
        val e = visibles.map { it.east }.filter { it != 0.0 }.maxOrNull() ?: return null
        val n = visibles.map { it.north }.filter { it != 0.0 }.maxOrNull() ?: return null
        return CameraTarget.Bounds(w, s, e, n)
    }
}
