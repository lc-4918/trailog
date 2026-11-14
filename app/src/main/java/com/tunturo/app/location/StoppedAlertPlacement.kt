package com.tunturo.app.location

/**
 * Ou dire qu'un suivi s'est interrompu : dans la notification permanente, ou dans une notification a part.
 *
 * Il y en avait toujours deux - la permanente, qui passait a "Localisation coupee - le suivi reprendra",
 * et l'alerte qui sonne, sous le meme titre et pour dire la meme chose. Desormais la permanente porte
 * l'annonce quand elle le peut : elle passe le temps de l'interruption sur le canal d'alerte.
 *
 * Elle ne le peut pas dans deux cas, ou l'alerte reste a part : en mode "ecran seul", ou il n'y a pas de
 * notification permanente, et quand le service s'arrete - sa notification part avec lui, l'annonce doit
 * lui survivre.
 */
internal object StoppedAlertPlacement {
    fun mergedIntoOngoing(inForeground: Boolean, serviceStopping: Boolean): Boolean = inForeground && !serviceStopping
}
