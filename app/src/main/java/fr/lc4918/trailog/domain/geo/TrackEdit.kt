package fr.lc4918.trailog.domain.geo

import fr.lc4918.trailog.domain.model.TrackPoint

/**
 * Retouches d'une trace deja importee : la couper, en inverser le sens, la fusionner avec une autre.
 *
 * Ce ne sont pas des outils de dessin - l'application ne cree pas de trace a la main, et n'entend pas s'y
 * mettre. Ce sont les trois gestes qu'on fait sur un fichier RECU : couper l'aller du retour, remettre une
 * trace dans le sens ou on l'a parcourue, recoller deux morceaux exportes separement.
 *
 * Sans dependance Android, comme le reste des calculs de trace : verifiable sans emulateur.
 */
object TrackEdit {

    /**
     * Le sens inverse : les segments dans l'ordre inverse, et chacun retourne.
     *
     * **Les horodatages tombent.** Un temps qui recule n'est pas une trace parcourue a l'envers, c'est un
     * fichier invalide : le GPX exige des dates croissantes, et le calcul du temps en mouvement compte des
     * durees negatives. Les garder en les recalculant serait pire encore - cela inventerait une sortie qui
     * n'a pas eu lieu. L'altitude, elle, ne depend pas du sens et reste.
     */
    fun reverse(lines: List<List<TrackPoint>>): List<List<TrackPoint>> =
        lines.asReversed().map { seg -> seg.asReversed().map { it.copy(timeMs = null) } }

    /**
     * Le point de la geometrie le plus proche de (lon, lat) : son segment et son rang. Null si la
     * geometrie n'a aucun point.
     *
     * On cherche dans la geometrie COMPLETE et non dans les echantillons du profil : ceux-ci sont decimes
     * a deux mille points pour l'affichage, et couper sur l'un d'eux deplacerait la coupe de plusieurs
     * dizaines de metres sur une longue trace.
     */
    fun nearest(lines: List<List<TrackPoint>>, lon: Double, lat: Double): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var bestD = Double.MAX_VALUE
        lines.forEachIndexed { si, seg ->
            seg.forEachIndexed { pi, p ->
                val d = TrackMath.haversine(lon, lat, p.lon, p.lat)
                if (d < bestD) { bestD = d; best = si to pi }
            }
        }
        return best
    }

    /**
     * Un endroit vise sur une geometrie : son segment, le troncon touche, et le point exact.
     *
     * [index] est le sommet qui PRECEDE le point vise, et [point] le point lui-meme - lequel n'existe pas
     * forcement dans la trace : c'est le projete du doigt sur le troncon, entre deux sommets.
     */
    data class Hit(
        val segment: Int,
        val index: Int,
        val point: TrackPoint,
        /** Ecart entre le doigt et la trace, en metres : departage deux segments voisins. */
        val awayM: Double,
        /** Les deux bouts du troncon touche, dans l'ordre du parcours. Ils disent PAR OU la trace passe au
         *  point vise, ce dont a besoin l'affichage pour poser sa bulle a cote et non dessus. Nuls pour un
         *  segment reduit a un point, qui n'a pas de direction. */
        val before: TrackPoint? = null,
        val after: TrackPoint? = null,
    )

    /**
     * Ou tombe (lon, lat) sur la geometrie : le troncon le plus proche, tous segments confondus.
     *
     * Le point rendu est **interpole sur le troncon**, et non rabattu sur un sommet : c'est ce qui permet
     * de couper n'importe ou, y compris au milieu d'une ligne droite de deux kilometres qui ne porte que
     * ses deux extremites.
     *
     * L'altitude suit la meme interpolation quand les deux sommets en portent une ; l'horodatage, non : un
     * point insere n'a pas ete parcouru a une heure connue, et en inventer une le ferait entrer dans le
     * calcul du temps de mouvement.
     */
    fun locate(lines: List<List<TrackPoint>>, lon: Double, lat: Double): Hit? {
        var best: Hit? = null
        val kx = kotlin.math.cos(Math.toRadians(lat))
        lines.forEachIndexed { si, seg ->
            for (i in 0 until seg.size - 1) {
                val a = seg[i]; val b = seg[i + 1]
                // Plan local, comme la mesure sur trace : sur la longueur d'un troncon, l'ecart avec la
                // sphere est sans consequence, et une projection exacte couterait une trigonometrie par
                // troncon de trace.
                val abx = (b.lon - a.lon) * kx; val aby = b.lat - a.lat
                val apx = (lon - a.lon) * kx; val apy = lat - a.lat
                val len2 = abx * abx + aby * aby
                val t = if (len2 <= 0.0) 0.0 else ((apx * abx + apy * aby) / len2).coerceIn(0.0, 1.0)
                val plon = a.lon + (b.lon - a.lon) * t
                val plat = a.lat + (b.lat - a.lat) * t
                val away = TrackMath.haversine(lon, lat, plon, plat)
                if (best == null || away < best.awayM) {
                    val ele = if (a.ele != null && b.ele != null) a.ele + (b.ele - a.ele) * t else a.ele ?: b.ele
                    best = Hit(si, i, TrackPoint(plon, plat, ele), away, before = a, after = b)
                }
            }
            // Segment reduit a un point : aucun troncon a projeter, le point est sa propre reponse.
            if (seg.size == 1) {
                val p = seg[0]
                val away = TrackMath.haversine(lon, lat, p.lon, p.lat)
                if (best == null || away < best.awayM) best = Hit(si, 0, p, away)
            }
        }
        return best
    }

    /**
     * Les portions de trace qui passent a moins de [radiusM] du point.
     *
     * Sert a savoir ce qu'une etiquette posee la recouvrirait. On garde les TRONCONS dont la distance au
     * point est inferieure au rayon - et non les sommets proches : un troncon de deux kilometres passant a
     * dix metres du point n'a aucun sommet dans le voisinage, et c'est pourtant lui qu'on risque de couvrir.
     *
     * Les troncons retenus ressortent en suites continues, pretes a etre projetees a l'ecran : une trace
     * qui traverse le voisinage deux fois donne deux suites, et non une droite fantome entre les deux.
     */
    fun nearbyRuns(
        lines: List<List<TrackPoint>>, lon: Double, lat: Double, radiusM: Double,
    ): List<List<TrackPoint>> {
        val out = ArrayList<List<TrackPoint>>()
        val kx = kotlin.math.cos(Math.toRadians(lat))
        lines.forEach { seg ->
            var run = ArrayList<TrackPoint>()
            for (i in 0 until seg.size - 1) {
                val a = seg[i]; val b = seg[i + 1]
                if (distanceToSegment(lon, lat, a, b, kx) <= radiusM) {
                    if (run.isEmpty()) run.add(a)
                    run.add(b)
                } else if (run.isNotEmpty()) {
                    out.add(run); run = ArrayList()
                }
            }
            if (run.isNotEmpty()) out.add(run)
        }
        return out
    }

    /** Distance du point au troncon [a]-[b], en metres, dans le plan local (cf. [locate]). */
    private fun distanceToSegment(lon: Double, lat: Double, a: TrackPoint, b: TrackPoint, kx: Double): Double {
        val abx = (b.lon - a.lon) * kx; val aby = b.lat - a.lat
        val apx = (lon - a.lon) * kx; val apy = lat - a.lat
        val len2 = abx * abx + aby * aby
        val t = if (len2 <= 0.0) 0.0 else ((apx * abx + apy * aby) / len2).coerceIn(0.0, 1.0)
        return TrackMath.haversine(lon, lat, a.lon + (b.lon - a.lon) * t, a.lat + (b.lat - a.lat) * t)
    }

    /**
     * Coupe a l'endroit vise, en **inserant** le point de coupe s'il tombe entre deux sommets.
     *
     * C'est ce que la coupe au sommet ne savait pas faire : elle ne pouvait tomber qu'entre deux points
     * deja presents, et sur une trace tracee a la regle, ils sont parfois a des kilometres l'un de l'autre.
     */
    fun splitAtHit(
        lines: List<List<TrackPoint>>, hit: Hit,
    ): Pair<List<List<TrackPoint>>, List<List<TrackPoint>>>? {
        val seg = lines.getOrNull(hit.segment) ?: return null
        // Le point vise coincide-t-il avec un sommet ? Alors on coupe dessus, sans en ajouter un double.
        val onVertex = seg.getOrNull(hit.index)?.let { same(it, hit.point) } == true
        val onNext = seg.getOrNull(hit.index + 1)?.let { same(it, hit.point) } == true
        return when {
            onVertex -> splitAt(lines, hit.segment, hit.index)
            onNext -> splitAt(lines, hit.segment, hit.index + 1)
            else -> {
                val withPoint = seg.take(hit.index + 1) + hit.point + seg.drop(hit.index + 1)
                splitAt(lines.toMutableList().also { it[hit.segment] = withPoint }, hit.segment, hit.index + 1)
            }
        }
    }

    /** Deux points au meme endroit, au centimetre pres : inutile d'en inserer un second. */
    private fun same(a: TrackPoint, b: TrackPoint): Boolean =
        kotlin.math.abs(a.lon - b.lon) < 1e-7 && kotlin.math.abs(a.lat - b.lat) < 1e-7

    /**
     * Joint les segments [a] et [b] en **un seul**, [bridge] intercale entre eux.
     *
     * Les deux segments sont orientes pour que la jonction se fasse par leurs **extremites les plus
     * proches** : on joint deux morceaux qui se touchent, sans avoir a se demander lequel a ete enregistre
     * dans quel sens. Le segment retourne pour l'occasion perd ses horodatages, pour la meme raison que
     * [reverse] - un temps qui recule n'est pas une trace valide.
     *
     * [bridge] vide donne une jonction en ligne droite : les deux extremites se suivent alors directement,
     * et la distance compte la corde entre elles. Un pont calcule par le moteur d'itineraire remplace cette
     * corde par un chemin praticable.
     *
     * Le segment joint prend la place du premier des deux ; l'autre disparait. Null si l'un des deux
     * indices ne designe rien, ou s'ils designent le meme segment.
     */
    fun join(
        lines: List<List<TrackPoint>>, a: Int, b: Int, bridge: List<TrackPoint> = emptyList(),
    ): List<List<TrackPoint>>? {
        if (a == b) return null
        val first = lines.getOrNull(a) ?: return null
        val second = lines.getOrNull(b) ?: return null
        if (first.isEmpty() || second.isEmpty()) return null
        // Les quatre facons de mettre bout a bout deux segments : on retient la plus courte.
        val ends = listOf(
            Triple(false, false, gap(first.last(), second.first())),
            Triple(false, true, gap(first.last(), second.last())),
            Triple(true, false, gap(first.first(), second.first())),
            Triple(true, true, gap(first.first(), second.last())),
        ).minBy { it.third }
        val head = if (ends.first) first.asReversed().map { it.copy(timeMs = null) } else first
        val tail = if (ends.second) second.asReversed().map { it.copy(timeMs = null) } else second
        val joined = head + bridge + tail
        val out = ArrayList<List<TrackPoint>>(lines.size - 1)
        lines.forEachIndexed { i, seg ->
            when (i) {
                a -> out.add(joined)
                b -> Unit
                else -> out.add(seg)
            }
        }
        return out
    }

    private fun gap(p: TrackPoint, q: TrackPoint) = TrackMath.haversine(p.lon, p.lat, q.lon, q.lat)

    /**
     * Coupe en deux au point ([segment], [index]).
     *
     * Le point de coupe appartient **aux deux** morceaux : il termine le premier et commence le second.
     * Sans cela, les deux traces s'arreteraient a un point d'ecart l'une de l'autre et un trou apparaitrait
     * a la jointure - visible sur la carte, et compte en moins dans les deux distances.
     *
     * Null quand la coupe ne donne pas deux morceaux parcourables : couper sur le premier ou le dernier
     * point d'une trace d'un seul segment laisserait un morceau d'un seul point, qui n'a ni distance ni
     * profil. Mieux vaut refuser que produire une couche vide de sens.
     */
    fun splitAt(
        lines: List<List<TrackPoint>>, segment: Int, index: Int,
    ): Pair<List<List<TrackPoint>>, List<List<TrackPoint>>>? {
        val seg = lines.getOrNull(segment) ?: return null
        if (index < 0 || index > seg.lastIndex) return null
        val head = lines.take(segment) + listOf(seg.take(index + 1))
        val tail = listOf(seg.drop(index)) + lines.drop(segment + 1)
        val usable = { parts: List<List<TrackPoint>> -> parts.any { it.size >= 2 } }
        if (!usable(head) || !usable(tail)) return null
        // Un segment reduit a un point isole ne sert a rien dans le morceau qui le recoit : il ne se dessine
        // pas et ne porte aucune distance. Il disparait, le reste du morceau etant intact.
        return head.filter { it.size >= 2 } to tail.filter { it.size >= 2 }
    }

    /**
     * Deux geometries bout a bout, chacune gardant ses segments.
     *
     * On ne recolle PAS les deux polylignes en une seule : deux traces qui ne se touchent pas verraient
     * apparaitre entre elles une droite qu'on n'a jamais parcourue, comptee dans la distance et dessinee
     * sur la carte. En les gardant segment par segment, le calcul les traite comme il traite deja les
     * segments d'un meme fichier - sans saut fantome d'un bout a l'autre.
     */
    fun merge(
        a: List<List<TrackPoint>>, b: List<List<TrackPoint>>,
    ): List<List<TrackPoint>> = a + b
}
