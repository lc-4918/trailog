package fr.lc4918.trailog.ui.routes

import kotlin.math.abs

/**
 * D'ou la fleche de position tire sa direction, et comment on l'empeche de trembler.
 *
 * **Le defaut que ceci corrige, et il tenait a une confusion.** La fleche montrait le cap de la BOUSSOLE,
 * c'est-a-dire la direction dans laquelle le telephone est pointe. Deux consequences, toutes deux
 * signalees par le testeur :
 *
 * - *"dans la chambre d'hotel, la fleche bouge tres vite sur elle-meme"* : elle suit l'appareil qu'on
 *   manipule, et le magnetometre est en outre perturbe par tout ce qu'un batiment contient de metal et
 *   d'electronique. Ce n'est pas une histoire d'immobilite - la fleche tourne parce que le TELEPHONE
 *   tourne, qu'on marche ou non ;
 * - *"sur la piste, la fleche est souvent en biais par rapport a la trace"* : evidemment - un telephone
 *   dans une sacoche, sur un guidon ou au fond d'une poche pointe ou son support le tient, et non vers ou
 *   l'on avance. L'ecart entre les deux est exactement l'angle du support.
 *
 * **Ce qu'on veut voir est la direction du DEPLACEMENT**, et le GPS la donne : `Location.bearing`, en
 * degres depuis le nord vrai. Elle etait publiee par `LocationHub` et lue par personne. Elle est stable
 * par construction - elle vient du deplacement lui-meme, pas d'un capteur magnetique - et alignee sur la
 * trace des qu'on la suit.
 *
 * Elle n'a en revanche aucun sens a l'arret : sous quelques km/h, le cap GPS part dans tous les sens, le
 * bruit de position depassant le deplacement reel. La boussole reprend alors la main - c'est le seul
 * moment ou elle dit quelque chose d'utile, quand on tient son telephone pour se reperer.
 */
object HeadingFusion {

    /**
     * Vitesse a partir de laquelle le cap du GPS vaut mieux que la boussole, en metres par seconde.
     *
     * 1,5 m/s, soit 5,4 km/h : au-dessus, on marche d'un bon pas ou l'on roule, et le deplacement domine
     * largement le bruit de position. En dessous, deux mesures successives peuvent se contredire du tout
     * au tout sans qu'on ait bouge d'un metre.
     */
    const val MIN_SPEED_MPS = 1.5f

    /**
     * Poids d'une nouvelle mesure de boussole dans le cap affiche.
     *
     * 0,15 : le cap met environ une demi-seconde a rattraper un quart de tour franc, ce qui suffit a
     * suivre un geste volontaire, et absorbe les sursauts d'un magnetometre perturbe. Le lissage ne
     * s'applique qu'a la boussole - le cap du GPS est deja stable, et le retarder le desalignerait de la
     * trace dans les virages.
     */
    const val COMPASS_ALPHA = 0.15f

    /**
     * Le cap a AFFICHER : celui du deplacement quand on avance, celui de la boussole sinon.
     *
     * [previous] est le cap affiche a l'instant d'avant, ou null au premier point : le lissage part alors
     * de la mesure elle-meme plutot que de zero, faute de quoi la fleche balaierait tout le tour du cadran
     * a l'allumage.
     */
    fun heading(
        previous: Float?,
        compassDeg: Float?,
        speedMps: Float?,
        gpsBearingDeg: Float?,
    ): Float? {
        val duGps = travelBearing(speedMps, gpsBearingDeg)
        // Le cap du deplacement s'impose tel quel : il ne tremble pas, et le lisser le mettrait en retard
        // sur la trace a chaque virage.
        if (duGps != null) return duGps
        val boussole = compassDeg ?: return previous
        return smooth(previous, boussole, COMPASS_ALPHA)
    }

    /** Le cap du deplacement, ou null quand il ne veut rien dire - trop lent, ou absent de la mesure. */
    fun travelBearing(speedMps: Float?, gpsBearingDeg: Float?): Float? {
        if (gpsBearingDeg == null || speedMps == null) return null
        if (speedMps < MIN_SPEED_MPS) return null
        return normalize(gpsBearingDeg)
    }

    /**
     * Lissage exponentiel d'un ANGLE : on avance de [alpha] fois l'ecart, par le plus court des deux
     * chemins.
     *
     * Le plus court chemin est tout l'objet de cette fonction : la moyenne naive de 359 et 1 donne 180,
     * soit exactement le sens oppose. La fleche faisait alors un demi-tour complet chaque fois qu'on
     * passait devant le nord.
     */
    fun smooth(previous: Float?, target: Float, alpha: Float): Float {
        val cible = normalize(target)
        val precedent = previous?.let { normalize(it) } ?: return cible
        return normalize(precedent + signedGap(precedent, cible) * alpha)
    }

    /** L'ecart signe de [from] vers [to], dans -180..180 : negatif vers l'ouest, positif vers l'est. */
    fun signedGap(from: Float, to: Float): Float {
        val d = ((to - from) % 360f + 540f) % 360f - 180f
        return d
    }

    /** Un cap ramene dans 0..360, quel que soit le tour qu'il a fait pour en sortir. */
    fun normalize(deg: Float): Float = ((deg % 360f) + 360f) % 360f

    /** Ecart entre deux caps, par le plus court des deux chemins : 359 et 1 sont a 2 degres l'un de
     *  l'autre. */
    fun gap(a: Float, b: Float): Float = abs(signedGap(a, b))
}
