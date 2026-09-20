package fr.lc4918.trailog.domain.geo

/**
 * Laquelle de deux positions garder, quand plusieurs fournisseurs parlent en meme temps.
 *
 * **Pourquoi plusieurs fournisseurs.** Le mode economie d'energie de certains telephones eteint le
 * GPS des que l'ecran s'eteint, et lui seul : le fournisseur reseau continue de repondre. Le suivi
 * s'abonne donc a tous ceux qui sont actifs (cf. `LocationService.enabledProviders`) plutot qu'au
 * meilleur du moment, et recoit alors deux positions pour un meme instant - l'une fine, l'autre a
 * quelques centaines de metres pres. Les publier toutes les deux ferait sauter le repere de l'une a
 * l'autre, et la carte tremblerait sans que rien ne bouge.
 *
 * **La regle.** On garde la plus precise, sauf si celle qu'on tient a fait son temps : au-dela de
 * [STALE_MS], une position quelconque vaut mieux qu'un repere qui ment. Une position plus vieille
 * que celle en place ne la remplace jamais - les fournisseurs ne livrent pas dans l'ordre.
 *
 * Les instants sont monotones (depuis le demarrage de l'appareil) et sont ceux de la MESURE, pas de
 * sa reception : une salve rattrapee au reveil arrive d'un coup, et les dates de reception ne
 * diraient plus rien de l'ordre des mesures.
 */
object FixPicker {

    /** Au-dela, la position qu'on tient est perimee : la suivante passe, quelle que soit sa precision. */
    const val STALE_MS = 10_000L

    /**
     * Degradation de precision toleree pour une position plus recente DU MEME fournisseur :
     * cinquante metres.
     *
     * En deca, on prefere la fraicheur - un GPS qui se degrade en passant sous les arbres reste le
     * bon repere, et exiger de lui qu'il s'ameliore sans cesse figerait le curseur sur sa meilleure
     * mesure de la journee.
     *
     * **D'un fournisseur a l'autre, cette tolerance n'a pas de sens**, et un journal de sortie l'a
     * montre : le GPS tenait un repere a huit metres, et deux positions reseau a trente-quatre et
     * trente-neuf metres sont passees - le curseur a pu bouger d'une trentaine de metres sans que
     * rien ne bouge. Le reseau n'est pas un GPS qui faiblit, il est structurellement plus grossier :
     * il ne prend la main qu'en faisant mieux, ou quand le GPS s'est tu (cf. [STALE_MS]).
     */
    const val WORSE_M = 50f

    /**
     * La precision annoncee, ramenee a ce qu'elle vaut : une position sans precision ([accuracyM] a
     * zero ou negatif, ce que rend un `Location` qui n'en porte pas) n'est pas parfaite, elle est
     * inconnue - donc la pire de toutes.
     */
    fun weight(accuracyM: Float): Float = if (accuracyM > 0f) accuracyM else Float.MAX_VALUE

    /**
     * La position candidate remplace-t-elle celle qu'on tient ?
     *
     * [heldAtMs] et [newAtMs] sont les instants de mesure, [heldAccuracyM] et [newAccuracyM] les
     * precisions annoncees, [heldProvider] et [newProvider] ceux qui les ont rendues - c'est d'eux
     * que depend la degradation toleree (cf. [WORSE_M]).
     */
    fun better(
        heldProvider: String, heldAccuracyM: Float, heldAtMs: Long,
        newProvider: String, newAccuracyM: Float, newAtMs: Long,
    ): Boolean {
        if (newAtMs < heldAtMs) return false
        if (newAtMs - heldAtMs >= STALE_MS) return true
        val tenue = weight(heldAccuracyM)
        val candidate = weight(newAccuracyM)
        if (candidate <= tenue) return true
        return newProvider == heldProvider && candidate - tenue <= WORSE_M
    }
}
