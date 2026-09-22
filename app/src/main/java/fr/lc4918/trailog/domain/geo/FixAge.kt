package fr.lc4918.trailog.domain.geo

/**
 * L'age d'une position, et ce qu'il autorise a en faire.
 *
 * **Le defaut qu'il repare.** "Partir d'ou je suis" prenait la derniere position recue par le suivi, telle
 * quelle, sans jamais demander de quand elle datait. Or le suivi peut se taire longtemps sans s'arreter :
 * l'economie d'energie de certains telephones eteint le GPS des que l'ecran s'eteint (cf. `PowerSave`), et
 * la derniere position recue est alors celle du moment ou l'ecran s'est eteint. Dix kilometres plus loin,
 * un itineraire demande "depuis ma position" partait toujours du point de depart - sans un mot, et sans
 * que rien a l'ecran ne le laisse deviner.
 *
 * Les instants sont MONOTONES (`SystemClock.elapsedRealtime`, et non l'horloge murale) : c'est le seul
 * temps qui ne saute pas quand le reseau remet le telephone a l'heure, et une position vieille d'une
 * seconde ne doit pas se retrouver datee de demain.
 */
object FixAge {

    /**
     * La position mesuree a [measuredAtMs] a-t-elle moins de [maxAgeMs] a l'instant [nowMs] ?
     *
     * Une mesure datee dans l'avenir passe pour fraiche : elle ne peut pas etre plus vieille que
     * maintenant, et refuser une position pour une pendule de travers nous priverait de la seule
     * information dont on dispose.
     */
    fun fresh(measuredAtMs: Long, nowMs: Long, maxAgeMs: Long): Boolean = nowMs - measuredAtMs <= maxAgeMs
}
