package fr.lc4918.trailog.domain.geo

/**
 * Le suivi est abonne au capteur, mais plus aucune position n'arrive.
 *
 * **Ce qui manquait.** L'application savait dire que la localisation avait ete coupee, parce que le
 * telephone l'annonce. Elle ne savait pas dire qu'elle etait abonnee a un fournisseur muet, parce
 * que rien ne l'annonce : en mode economie d'energie, le GPS s'eteint avec l'ecran sans que la
 * localisation soit coupee pour autant. Le suivi se croyait donc en marche, la carte aussi, et
 * quelqu'un a roule dix fois le seuil de son alerte d'eloignement sans que rien ne sonne.
 *
 * **Deux delais et non un.** Le premier tente de reprendre la main - un reabonnement suffit quand
 * c'est le fournisseur qui a lache -, le second renonce et le DIT. Annoncer tout de suite ferait
 * crier l'application a chaque tunnel.
 *
 * **Seulement en marche.** A la cadence de repos, plus aucune position n'arrive tant qu'on ne bouge
 * pas : c'est voulu (cf. `LocationService.pace`), et le silence n'y veut rien dire. L'horloge du
 * silence ne court donc que quand on attend des positions.
 */
object FixWatchdog {

    /** Sans position depuis ce temps alors qu'il en arrive une toutes les deux secondes : on se rabonne. */
    const val RESUBSCRIBE_MS = 45_000L

    /** Toujours rien apres ce temps : le suivi ne recoit plus rien, et le porteur doit l'apprendre. */
    const val ALERT_MS = 90_000L

    /**
     * [lastFixAtMs] est l'instant monotone de la derniere position recue - ou du dernier instant ou
     * le silence etait normal. Les deux drapeaux disent ce qui a deja ete tente pour ce trou-ci :
     * sans eux, on se rabonnerait toutes les cinq secondes et la notification se reposerait sans fin.
     */
    data class State(
        val lastFixAtMs: Long = 0L,
        val resubscribed: Boolean = false,
        val alerted: Boolean = false,
    )

    /** Ce que le silence demande a cet instant. */
    enum class Action { None, Resubscribe, Alert }

    /** Une position est arrivee : l'horloge repart, et ce qui a ete tente est oublie. */
    fun onFix(nowMs: Long): State = State(lastFixAtMs = nowMs)

    /**
     * Le battement : [walking] dit qu'on attend des positions (cadence de marche, abonnement en
     * cours). Rend l'etat suivant et ce qu'il y a a faire.
     *
     * Hors marche, l'horloge est remise a l'heure plutot qu'arretee : on quitte le repos sans
     * qu'aucune position ne soit arrivee, et une horloge gardee depuis la derniere crierait aussitot.
     */
    fun check(state: State, nowMs: Long, walking: Boolean): Pair<State, Action> {
        if (!walking) return State(lastFixAtMs = nowMs) to Action.None
        val silence = nowMs - state.lastFixAtMs
        return when {
            silence >= ALERT_MS && !state.alerted -> state.copy(alerted = true) to Action.Alert
            silence >= RESUBSCRIBE_MS && !state.resubscribed -> state.copy(resubscribed = true) to Action.Resubscribe
            else -> state to Action.None
        }
    }
}
