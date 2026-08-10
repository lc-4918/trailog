package fr.lc4918.trailog.data.seed

import fr.lc4918.trailog.data.db.CompositeEntity
import fr.lc4918.trailog.data.db.CompositeSortOrder

/**
 * Fonds composites par défaut : un fond opaque en arrière-plan, un fond transparent en premier plan.
 * Semés à vide comme les fournisseurs (cf. TrailogRepository.ensureSeed), puis librement éditables.
 *
 * id laissé à 0 : la clé est auto-générée, c'est Room qui l'attribue à l'insertion.
 */
object Composites {
    fun defaults(): List<CompositeEntity> = listOf(
        // Les voies cyclables de l'AF3V par-dessus Mapbox Outdoors. Opacité 1 et non le défaut 0.5 :
        // ce tracé est déjà fin et l'atténuer le rendrait illisible sur le relief ombré du fond.
        // Ses deux couches se composent bien que l'AF3V soit décoché dans la liste des fournisseurs
        // (cf. le KDoc de Providers).
        CompositeEntity(
            name = "Mapbox Outdoors + Af3v",
            backgroundProviderId = "mapbox_outdoors",
            foregroundProviderId = "af3v",
            foregroundOpacity = 1f,
            sortOrder = CompositeSortOrder,
        ),
    )
}
