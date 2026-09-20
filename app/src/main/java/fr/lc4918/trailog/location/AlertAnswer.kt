package fr.lc4918.trailog.location

/**
 * Ce que l'arrivee de l'application a l'ecran, en pleine alerte d'eloignement, doit faire de cette alerte.
 *
 * **Deux facons d'arriver, et une seule est une reponse.** La notification de l'ecart porte deux intentions
 * vers la carte : celle du TAP, qui est bien un geste - on a lu, on a compris, on regarde la carte -, et
 * celle du PLEIN ECRAN, qu'Android declenche tout seul, telephone en poche, pour allumer l'ecran et poser
 * l'application par-dessus le verrouillage. Les deux amenaient la meme intention, et l'application se
 * taisait donc au moment meme ou elle se reveillait : l'ecran s'allumait, la carte apparaissait, la cloche
 * passait au rouge, et il ne restait ni banniere ni sonnerie - l'alerte s'etait repondu a elle-meme.
 *
 * D'ou deux actions distinctes : l'une repond, l'autre reveille seulement. La sonnerie continue alors de
 * boucler devant les yeux de celui qu'elle appelle, jusqu'au tap sur la banniere, sur la notification, ou
 * sur "Taire" - ou jusqu'au retour sur la trace.
 */
object AlertAnswer {

    /** L'ecran s'allume et la carte se pose par-dessus le verrouillage : les deux arrivees le demandent. */
    fun wakesScreen(action: String?): Boolean =
        action == LocationService.ACTION_SHOW_ALERT || action == LocationService.ACTION_WAKE_ALERT

    /**
     * L'alerte se tait : le TAP seulement.
     *
     * Le reveil automatique n'est la reponse de personne - c'est lui qui pose la question.
     */
    fun silences(action: String?): Boolean = action == LocationService.ACTION_SHOW_ALERT
}
