package fr.lc4918.trailog.domain.geo

import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.domain.model.SurfaceKind
import fr.lc4918.trailog.domain.model.WayKind
import fr.lc4918.trailog.domain.model.WaySegment

/**
 * Les "Details" d'un itineraire : la part de chaque revetement et de chaque type de voie, et ou ils se
 * trouvent le long du trajet.
 *
 * Tout part des attributs OSM que BRouter rend pour chaque morceau du parcours (cf. [WaySegment]). Sans
 * dependance Android, comme le reste du calcul de trace : verifiable sans emulateur.
 */
object RouteDetails {

    /** La part d'une categorie : sa longueur, et sa fraction du trajet (0 a 1). */
    data class Share<K>(val kind: K, val meters: Double, val fraction: Double)

    /** `highway=track surface=gravel` en table. Decoupe aux espaces : les valeurs sont celles du vocabulaire
     *  de BRouter (lookups.dat), qui n'en contient aucune avec un espace. */
    fun tagsOf(text: String): Map<String, String> =
        text.split(' ').mapNotNull { kv ->
            val i = kv.indexOf('=')
            if (i <= 0) null else kv.substring(0, i) to kv.substring(i + 1)
        }.toMap()

    /**
     * Le revetement d'une voie : celui qu'elle declare, et a defaut celui qu'on peut en attendre (cf.
     * [estimatedSurfaceOf]).
     *
     * Les valeurs sont celles que BRouter ECRIT, c'est-a-dire les valeurs principales de son vocabulaire :
     * il ramene deja `soil` a `ground`, `rocky` a `rock` ou `cobblestone:flattened` a `cobblestone`. Les
     * alias sont gardes malgre tout - un serveur d'une autre version peut les laisser passer.
     */
    fun surfaceOf(tags: Map<String, String>): SurfaceKind =
        declaredSurfaceOf(tags) ?: estimatedSurfaceOf(tags)

    /** Le revetement que la voie DECLARE (`surface`), ou null si elle n'en dit rien - ou rien de connu. */
    fun declaredSurfaceOf(tags: Map<String, String>): SurfaceKind? = when (tags["surface"]) {
        null, "" -> null
        "asphalt", "chipseal" -> SurfaceKind.ASPHALT
        "paved" -> SurfaceKind.PAVED
        "concrete", "concrete:plates", "concrete:lanes", "cement" -> SurfaceKind.CONCRETE
        "paving_stones", "paving_stones:30", "paving_stones:20", "sett", "cobblestone",
        "cobblestone:flattened", "unhewn_cobblestone", "bricks", "brick" -> SurfaceKind.SETT
        "wood", "metal" -> SurfaceKind.WOOD_METAL
        "compacted" -> SurfaceKind.COMPACTED
        "fine_gravel" -> SurfaceKind.FINE_GRAVEL
        "gravel", "pebblestone" -> SurfaceKind.GRAVEL
        "unpaved", "unpaved_minor" -> SurfaceKind.UNPAVED
        "ground", "dirt", "earth", "soil", "mud", "clay", "dirt/sand" -> SurfaceKind.GROUND
        "grass", "grass_paver", "artificial_turf" -> SurfaceKind.GRASS
        "sand" -> SurfaceKind.SAND
        "rock", "rocks", "rocky", "stone" -> SurfaceKind.ROCK
        else -> null
    }

    /**
     * Le revetement qu'on peut attendre d'une voie qui ne declare pas le sien.
     *
     * **Pourquoi estimer.** Un bon tiers des troncons d'un trajet reel n'a pas de `surface` (releve sur
     * 83 km autour de Mirepoix, Toulouse et Grenoble : 35 %) - des routes departementales et des rues pour
     * l'essentiel, que personne ne prend la peine de dire asphaltees. Les laisser "inconnus" noyait la
     * rubrique sous une categorie qui ne disait rien.
     *
     * **Ce qui guide l'estimation**, du plus sur au moins sur :
     * - le `tracktype`, fait pour cela : grade1 dur (revetu le plus souvent), grade2 gravier tasse,
     *   grade3 melange, grade4 et grade5 terre et herbe ;
     * - le type de voie : une route, une rue, une piste cyclable ou un trottoir sont revetus, un chemin
     *   agricole ou un sentier ne le sont pas.
     *
     * Estime n'est pas mesure : la rubrique dit quelle part de ses chiffres vient d'ici (cf.
     * [estimatedFraction]).
     */
    fun estimatedSurfaceOf(tags: Map<String, String>): SurfaceKind {
        when (tags["tracktype"]) {
            "grade1" -> return SurfaceKind.PAVED
            "grade2" -> return SurfaceKind.COMPACTED
            "grade3" -> return SurfaceKind.UNPAVED
            "grade4", "grade5" -> return SurfaceKind.GROUND
        }
        return when (tags["highway"]) {
            "motorway", "motorway_link", "trunk", "trunk_link", "primary", "primary_link", "secondary",
            "secondary_link", "tertiary", "tertiary_link", "unclassified", "road", "residential",
            "living_street", "service", "pedestrian", "cycleway", "footway", "steps" -> SurfaceKind.PAVED
            "track", "path", "bridleway" -> SurfaceKind.UNPAVED
            else -> SurfaceKind.UNKNOWN
        }
    }

    /** Vrai quand le revetement du troncon est estime et non declare (cf. [estimatedSurfaceOf]). */
    fun isSurfaceEstimated(segment: WaySegment): Boolean {
        val tags = tagsOf(segment.tags)
        return declaredSurfaceOf(tags) == null && estimatedSurfaceOf(tags) != SurfaceKind.UNKNOWN
    }

    /** La part du trajet (0 a 1) dont le revetement est estime et non declare. */
    fun estimatedFraction(segments: List<WaySegment>): Double {
        val total = segments.sumOf { it.meters }
        if (total <= 0.0) return 0.0
        return segments.filter(::isSurfaceEstimated).sumOf { it.meters } / total
    }

    /**
     * Le type d'une voie.
     *
     * Un sentier ou un chemin pieton reserve aux velos (`bicycle=designated`) est compte en piste cyclable :
     * c'est ce qu'il est sur le terrain, et c'est ainsi que les voies vertes sont le plus souvent decrites.
     */
    fun wayOf(tags: Map<String, String>): WayKind {
        if (tags["route"] == "ferry") return WayKind.FERRY
        val velo = tags["bicycle"] == "designated"
        return when (tags["highway"]) {
            "motorway", "motorway_link", "trunk", "trunk_link", "primary", "primary_link" -> WayKind.MAIN_ROAD
            "secondary", "secondary_link", "tertiary", "tertiary_link" -> WayKind.ROAD
            "unclassified", "road" -> WayKind.MINOR_ROAD
            "residential", "living_street" -> WayKind.STREET
            "pedestrian" -> WayKind.PEDESTRIAN
            "service" -> WayKind.SERVICE
            "cycleway" -> WayKind.CYCLEWAY
            "track" -> WayKind.TRACK
            "path", "bridleway" -> if (velo) WayKind.CYCLEWAY else WayKind.PATH
            "footway" -> if (velo) WayKind.CYCLEWAY else WayKind.FOOTWAY
            "steps" -> WayKind.STEPS
            else -> WayKind.OTHER
        }
    }

    fun surfaceOf(segment: WaySegment): SurfaceKind = surfaceOf(tagsOf(segment.tags))
    fun wayOf(segment: WaySegment): WayKind = wayOf(tagsOf(segment.tags))

    /**
     * Les parts de chaque categorie, de la plus longue a la plus courte.
     *
     * Les fractions se rapportent a la somme des morceaux, et non a la longueur du parcours affichee : les
     * deux different de quelques metres (arrondis du moteur), et des parts qui ne feraient pas 100 %
     * se remarqueraient.
     */
    fun <K> shares(segments: List<WaySegment>, classify: (WaySegment) -> K): List<Share<K>> {
        val total = segments.sumOf { it.meters }
        if (total <= 0.0) return emptyList()
        return segments.groupBy(classify)
            .map { (k, l) -> l.sumOf { it.meters }.let { m -> Share(k, m, m / total) } }
            .sortedByDescending { it.meters }
    }

    fun surfaces(segments: List<WaySegment>): List<Share<SurfaceKind>> = shares(segments) { surfaceOf(it) }
    fun ways(segments: List<WaySegment>): List<Share<WayKind>> = shares(segments) { wayOf(it) }

    /**
     * Revetu, non revetu, inconnu : la synthese du haut de la rubrique "Surfaces". Toujours dans cet ordre,
     * les parts nulles ecartees.
     */
    fun paved(segments: List<WaySegment>): List<Share<Boolean?>> =
        shares(segments) { surfaceOf(it).paved }
            .sortedBy { when (it.kind) { true -> 0; false -> 1; null -> 2 } }

    /**
     * Ou se trouvent les morceaux d'une categorie, en abscisses du trace ([totalX] : l'abscisse du dernier
     * echantillon), les morceaux voisins fusionnes.
     *
     * Les longueurs du moteur sont ramenees a celles du trace par une simple regle de trois : l'application
     * mesure le parcours sur ses points, le moteur sur son graphe, et les deux totaux different de quelques
     * metres - sans cette mise a l'echelle, le dernier morceau deborderait du trace ou s'arreterait avant.
     */
    fun <K> ranges(
        segments: List<WaySegment>, totalX: Double, classify: (WaySegment) -> K, kind: K,
    ): List<ClosedFloatingPointRange<Double>> {
        val total = segments.sumOf { it.meters }
        if (total <= 0.0 || totalX <= 0.0) return emptyList()
        val k = totalX / total
        val out = mutableListOf<ClosedFloatingPointRange<Double>>()
        var debut = 0.0
        for (s in segments) {
            val fin = debut + s.meters * k
            if (classify(s) == kind) {
                val prec = out.lastOrNull()
                if (prec != null && prec.endInclusive >= debut) out[out.lastIndex] = prec.start..fin
                else out += debut..fin
            }
            debut = fin
        }
        return out
    }

    /**
     * Le trace de chaque plage, en (lon, lat) : les echantillons qu'elle couvre, et ses deux bouts
     * interpoles - une plage commence et finit rarement sur un echantillon.
     */
    fun pieces(samples: List<Sample>, ranges: List<ClosedFloatingPointRange<Double>>): List<List<Pair<Double, Double>>> {
        if (samples.size < 2) return emptyList()
        return ranges.mapNotNull { r ->
            val a = TrackMath.sampleAt(samples, r.start) ?: return@mapNotNull null
            val b = TrackMath.sampleAt(samples, r.endInclusive) ?: return@mapNotNull null
            val milieu = samples.filter { it.x > r.start && it.x < r.endInclusive }
            val pts = (listOf(a) + milieu + b).map { it.lon to it.lat }
            if (pts.size < 2) null else pts
        }
    }

    /** La categorie qui se trouve a la fraction [f] (0 a 1) d'une barre ou les parts se suivent dans l'ordre. */
    fun <K> kindAt(shares: List<Share<K>>, f: Double): K? {
        if (shares.isEmpty()) return null
        var cumul = 0.0
        for (s in shares) {
            cumul += s.fraction
            if (f < cumul) return s.kind
        }
        return shares.last().kind
    }
}
