package fr.lc4918.trailog.elevation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * Client du service altimetrique de la **Geoplateforme** (IGN), qui sert le RGE ALTI sur la France et ses
 * territoires d'outre-mer.
 *
 * Retenu comme source principale parce qu'il est d'un tout autre ordre de precision que les modeles
 * mondiaux : le RGE ALTI est au metre la ou le Copernicus mondial est a quatre-vingt-dix. Sans cle ni quota
 * annonce, et il accepte **deux cents points par requete**, ce qui met une trace de dix mille points a une
 * cinquantaine d'appels.
 *
 * Hors de sa couverture, le service ne repond pas une erreur : il rend la valeur [OUT_OF_COVERAGE] pour le
 * point concerne. C'est ce qui permet de ne coder aucune frontiere dans l'application - on lui demande, et
 * il dit lui-meme ou il ne sait pas (cf. [ElevationFiller], qui bascule alors sur le modele mondial).
 *
 * Meme decoupage que le geocodeur et le moteur d'itineraire : construction d'URL et lecture de la reponse
 * d'un cote, appel reseau de l'autre. Les deux premiers sont les seuls endroits ou une faute serait
 * silencieuse, et ils sont testes sans reseau.
 */
object IgnElevation {
    const val DEFAULT_URL = "https://data.geopf.fr/altimetrie/1.0/calcul/alti/rest/elevation.json"

    /** Ressource interrogee. Obligatoire : sans elle le service repond 405, et non un defaut. */
    private const val RESOURCE = "ign_rge_alti_wld"

    /** Points par requete. Le service en accepte davantage, mais l'URL grossit d'une vingtaine d'octets par
     *  point et finirait par se heurter aux limites de longueur des serveurs intermediaires. */
    const val MAX_POINTS = 200

    /** Ce que rend le service la ou il n'a pas de donnee - hors de France, pour l'essentiel. */
    private const val OUT_OF_COVERAGE = -99999.0

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * URL de requete pour [points], dans l'ordre donne : la reponse rend les altitudes dans le meme.
     *
     * `zonly=true` reduit la reponse aux seules altitudes, sans repeter les coordonnees envoyees ni la
     * mention d'exactitude - un tiers du poids pour la meme information.
     *
     * Coordonnees arrondies au millionieme de degre, soit une dizaine de centimetres : au-dela, on
     * n'ajoute que des chiffres a l'URL. `Locale.US` impose le point decimal, la ou une locale francaise
     * ecrirait une virgule que le service lirait comme un separateur de valeurs.
     */
    fun url(base: String, points: List<LonLat>): String {
        val sep = if ('?' in base) '&' else '?'
        val lons = points.joinToString("|") { fmt(it.lon) }
        val lats = points.joinToString("|") { fmt(it.lat) }
        return base.trimEnd('&', '?') + sep +
            "resource=" + RESOURCE + "&delimiter=|&zonly=true&lon=" + lons + "&lat=" + lats
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.6f", v)

    /**
     * Altitudes de la reponse, une par point demande, **null la ou le service n'a pas de donnee**.
     *
     * Null pour toute la reponse quand elle ne porte pas exactement [expected] altitudes : une reponse
     * dont on ne sait plus a quel point chaque valeur se rapporte est pire qu'une reponse absente - elle
     * poserait des altitudes sur les mauvais points.
     */
    fun parse(body: String, expected: Int): List<Double?>? = runCatching {
        val z = json.decodeFromString<ZOnly>(body).elevations
        if (z.size != expected) return@runCatching null
        z.map { v -> if (v == null || v <= OUT_OF_COVERAGE) null else v }
    }.getOrNull()

    @Serializable private data class ZOnly(val elevations: List<Double?> = emptyList())
}
