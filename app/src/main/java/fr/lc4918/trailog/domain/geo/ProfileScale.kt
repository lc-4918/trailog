package fr.lc4918.trailog.domain.geo

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * L'echelle verticale du profil altimetrique : ce que la hauteur du graphe represente.
 *
 * **Deux regimes, et ils ne disent pas la meme chose.**
 * - [Mode.CAP] : le profil remplit la hauteur - c'est ce qui se lit le mieux - mais sans depasser un
 *   RAPPORT entre les deux axes. Sans plafond, une trace longue et douce voit sa pente de 0,2 % dessinee
 *   comme un mur, et l'axe cesse de parler du terrain.
 * - [Mode.M_PER_CM] : une echelle absolue, en metres d'altitude par centimetre d'ecran. Elle rend les
 *   AMPLITUDES comparables : deux profils se mesurent a la regle.
 *
 * **Un troisieme a ete retire : l'exageration fixe**, un rapport impose entre les deux axes. Sur le
 * papier, elle rendait les PENTES comparables d'une trace a l'autre. A l'usage, elle ne donnait un dessin
 * lisible pour aucune valeur : la hauteur d'un telephone est ce qu'elle est, et le rapport demande y
 * aboutit soit a un trait ecrase au bas du cadre, soit a un profil qui deborde. Le plafond de [Mode.CAP]
 * repond deja a ce qu'on cherchait - une pente douce n'y est jamais dressee en muraille - sans imposer
 * une valeur qui ne vaut que pour une trace. Un reglage ancien ("x:10") se relit comme le plafond par
 * defaut (cf. [parse]).
 *
 * Sans Android : ce sont des regles de geometrie, et une faute y est silencieuse - un profil faux se lit
 * comme un profil vrai.
 */
object ProfileScale {

    enum class Mode { CAP, M_PER_CM }

    /** Le regime retenu, et sa valeur : le plafond, ou les metres par centimetre. */
    data class Vertical(val mode: Mode, val value: Double)

    /** Le plafond par defaut : mesure sur des traces reelles, en deca les traces courtes debordent, au-dela
     *  une longue traversee n'occupe plus qu'une fraction du cadre. */
    const val DEFAULT_CAP = 25.0

    /** Le graphe ne descend pas sous cette hauteur (px) : en deca, l'axe n'a plus la place de trois
     *  graduations. */
    const val MIN_CHART_PX = 70f

    /** Le cadre ne depasse pas ce multiple de l'amplitude de la trace : au-dela, le dessin n'est plus qu'un
     *  trait au bas d'un cadre vide. */
    const val MAX_FRAME = 4.0

    /** Graduations visees, une par seize pixels de hauteur, entre trois et huit. */
    private const val PX_PER_TICK = 16f

    /**
     * L'echelle lue de la base (colonne `profileVerticalScale`).
     *
     * Les anciennes valeurs se relisent : la colonne portait un entier - zero pour "remplir la hauteur",
     * sinon des metres par centimetre - et une base migree ne doit pas perdre son reglage.
     *
     * L'exageration ("x:10") a ete retiree : son ecriture se relit encore, et rend le plafond par defaut.
     * Un reglage qui ne se comprend plus ne doit pas priver de profil, ni geler l'ecran des reglages sur
     * un regime qui n'existe plus.
     */
    fun parse(s: String?): Vertical {
        val t = s?.trim().orEmpty()
        val n = t.toIntOrNull()
        if (n != null) return if (n <= 0) Vertical(Mode.CAP, DEFAULT_CAP) else Vertical(Mode.M_PER_CM, n.toDouble())
        val v = t.substringAfter(':', "").toDoubleOrNull() ?: return Vertical(Mode.CAP, DEFAULT_CAP)
        return when (t.substringBefore(':')) {
            "m" -> if (v > 0) Vertical(Mode.M_PER_CM, v) else Vertical(Mode.CAP, DEFAULT_CAP)
            "cap" -> Vertical(Mode.CAP, if (v > 0) v else DEFAULT_CAP)
            // "x:" - l'exageration retiree - tombe avec tout le reste sur le plafond par defaut.
            else -> Vertical(Mode.CAP, DEFAULT_CAP)
        }
    }

    /** L'ecriture en base, relue par [parse]. */
    fun store(v: Vertical): String = when (v.mode) {
        Mode.CAP -> "cap:${fmt(v.value)}"
        Mode.M_PER_CM -> "m:${fmt(v.value)}"
    }

    private fun fmt(v: Double) = if (v == floor(v)) v.roundToInt().toString() else v.toString()

    /**
     * La fenetre du graphe : ce que l'axe vertical couvre, la hauteur qu'il faut au dessin, et les
     * altitudes graduees.
     *
     * @param heightPx la hauteur retenue, au plus [maxHeightPx] : sous un plafond, c'est la HAUTEUR qui
     *   plie et non l'axe - a echelle imposee, la hauteur en plus n'est que du vide au-dessus du dessin.
     */
    data class Window(
        val minZ: Double,
        val maxZ: Double,
        val heightPx: Float,
        val ticks: List<Double>,
        /** Le rapport entre les deux axes, tel qu'il ressort : c'est le seul moyen de voir qu'un plafond a
         *  ete depasse faute de hauteur. */
        val exaggeration: Double?,
    ) {
        val spanZ: Double get() = (maxZ - minZ).coerceAtLeast(1e-6)
    }

    /**
     * Calcule la fenetre du graphe.
     *
     * @param zMin, zMax l'altitude de la portion affichee (m).
     * @param distanceM la distance affichee (m) : c'est elle, et non la longueur de la trace, qui donne le
     *   rapport entre les axes - un recadrage change la pente apparente.
     * @param widthPx la largeur du dessin, marges retirees.
     * @param maxHeightPx la hauteur disponible pour le dessin.
     * @param pxPerCm les pixels d'un centimetre d'ecran : c'est ce qui donne un sens a "metres par cm".
     */
    fun window(
        v: Vertical, zMin: Double, zMax: Double, distanceM: Double,
        widthPx: Float, maxHeightPx: Float, pxPerCm: Float,
    ): Window {
        val amplitude = (zMax - zMin).coerceAtLeast(1e-6)
        val h = maxHeightPx.coerceAtLeast(1f)
        val cmW = if (pxPerCm > 0f) widthPx / pxPerCm else 0f
        // Le rapport entre les deux axes : la distance parcourue par centimetre, divisee par les metres
        // d'altitude par centimetre.
        fun rapport(etendue: Double, hauteur: Float): Double? {
            if (cmW <= 0f || etendue <= 0.0 || hauteur <= 0f) return null
            val mParCmV = etendue / (hauteur / pxPerCm)
            return (distanceM / cmW) / mParCmV
        }
        // Les metres par centimetre demandes, quand l'echelle est absolue.
        val mParCm = when (v.mode) {
            Mode.M_PER_CM -> v.value
            Mode.CAP -> 0.0
        }
        if (mParCm > 0.0) return fenetreAbsolue(mParCm, zMin, zMax, h, pxPerCm, ::rapport)

        // Remplir la hauteur : l'amplitude decide de tout, aux graduations rondes pres.
        val pleine = fenetreArrondie(zMin, zMax, amplitude, h)
        val obtenu = rapport(pleine.spanZ, h)
        val plafond = v.value
        if (plafond <= 0.0 || obtenu == null || obtenu <= plafond) {
            return pleine.copy(exaggeration = obtenu)
        }
        return fenetrePlafonnee(zMin, zMax, amplitude, distanceM, h, pxPerCm, cmW, plafond, ::rapport)
    }

    /**
     * Le remplissage de la hauteur, aux graduations rondes.
     *
     * L'axe part d'une graduation et s'arrete sur une graduation : "1 149 m" en haut d'un axe n'apprend
     * rien de plus que "1 200 m", et coute une lecture.
     */
    private fun fenetreArrondie(zMin: Double, zMax: Double, amplitude: Double, h: Float): Window {
        val cible = ticksFor(h)
        val pas = pasRond(amplitude / cible)
        val base = floor(zMin / pas) * pas
        val sommet = base + ceil((zMax - base) / pas).coerceAtLeast(1.0) * pas
        return Window(base, sommet, h, graduations(base, sommet, pas), null)
    }

    /**
     * La fenetre sous une echelle ABSOLUE : les metres par centimetre sont ceux demandes.
     *
     * Elle reste un PLANCHER et non un carcan : une trace dont l'amplitude depasse ce que la hauteur peut
     * montrer deborderait du cadre, ce qui est pire que de perdre la comparabilite.
     *
     * Le profil reste ancre sur son point bas - la place en trop se met AU-DESSUS : centrer la fenetre
     * creuserait sous la trace, et l'axe descendrait sous le niveau de la mer sur un profil de montagne.
     */
    private fun fenetreAbsolue(
        mParCm: Double, zMin: Double, zMax: Double, h: Float, pxPerCm: Float,
        rapport: (Double, Float) -> Double?,
    ): Window {
        val cmH = h / pxPerCm
        val etendue = maxOf(mParCm * cmH, zMax - zMin).coerceAtLeast(1e-6)
        val cible = ticksFor(h)
        val pas = pasRond(etendue / cible)
        val base = floor(zMin / pas) * pas
        val sommet = base + etendue
        return Window(base, sommet, h, graduations(base, sommet, pas), rapport(etendue, h))
    }

    /**
     * La fenetre sous un PLAFOND : c'est la hauteur qui plie, pas l'axe.
     *
     * A echelle imposee, le dessin ne depend pas de la hauteur du graphe : ses metres par pixel sont
     * fixes, la trace occupe les memes pixels, et tout ce que la hauteur ajoute est du vide au-dessus. On
     * la retire donc : le panneau se reduit, le dessin ne bouge pas, et l'axe s'arrete juste au-dessus de
     * la trace.
     *
     * Deux garde-fous, dans cet ordre : le cadre ne depasse pas [MAX_FRAME] fois l'amplitude - sans quoi
     * une trace plate resterait un trait au bas d'un cadre vide -, et le graphe ne descend pas sous
     * [MIN_CHART_PX]. Quand les deux se contredisent - une trace longue ET plate -, c'est le premier qui
     * l'emporte et le rapport depasse le plafond : c'est le seul moyen d'avoir a la fois un panneau
     * lisible et un axe qui parle du terrain.
     */
    private fun fenetrePlafonnee(
        zMin: Double, zMax: Double, amplitude: Double, distanceM: Double,
        maxH: Float, pxPerCm: Float, cmW: Float, plafond: Double,
        rapport: (Double, Float) -> Double?,
    ): Window {
        val mParCmPlafond = (distanceM / cmW) / plafond
        val mParPxPlafond = mParCmPlafond / pxPerCm
        val hMin = minOf(MIN_CHART_PX, maxH)
        var etendue = maxOf(amplitude, hMin * mParPxPlafond)
        etendue = maxOf(amplitude, minOf(etendue, MAX_FRAME * amplitude))
        var hauteur = (etendue / mParPxPlafond).toFloat()
        var mParPx = mParPxPlafond
        // Le cadre a mordu avant la hauteur minimale : on garde la hauteur, et le rapport depasse le
        // plafond - plutot qu'un graphe de quarante pixels.
        if (hauteur < hMin) { hauteur = hMin; mParPx = etendue / hMin }

        val cible = ticksFor(hauteur)
        val pas = pasRond(etendue / cible)
        val base = floor(zMin / pas) * pas
        val sommet = base + ceil((zMin + etendue - base) / pas).coerceAtLeast(1.0) * pas
        val etendueArrondie = sommet - base
        val h = (etendueArrondie / mParPx).toFloat().coerceIn(hMin, maxH)
        return Window(base, sommet, h, graduations(base, sommet, pas), rapport(etendueArrondie, h))
    }

    /** Graduations que la hauteur peut porter : une par seize pixels, entre trois et huit. */
    private fun ticksFor(h: Float): Int = (h / PX_PER_TICK).roundToInt().coerceIn(3, 8)

    /** Un pas de graduation qui se lit : 1, 2, 2,5 ou 5 fois une puissance de dix. */
    fun pasRond(brut: Double): Double {
        if (!(brut > 0.0) || brut.isNaN() || brut.isInfinite()) return 1.0
        val p = 10.0.pow(floor(log10(brut)))
        for (m in listOf(1.0, 2.0, 2.5, 5.0)) if (brut <= m * p) return m * p
        return 10 * p
    }

    private fun graduations(base: Double, sommet: Double, pas: Double): List<Double> {
        if (pas <= 0.0) return listOf(base, sommet)
        val out = ArrayList<Double>()
        var v = base
        var garde = 0
        while (v <= sommet + pas / 2 && garde < 64) {
            out += if (abs(v) < pas / 1e6) 0.0 else v
            v += pas
            garde++
        }
        return out
    }
}
