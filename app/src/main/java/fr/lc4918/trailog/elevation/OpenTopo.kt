package fr.lc4918.trailog.elevation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * Client d'**OpenTopography**, source altimetrique pour le reste du monde.
 *
 * Le service expose deux portes, et l'application se sert des deux :
 *
 * - `/v1/elevation` rend l'altitude d'**un** point. Une requete par point : parfait pour un waypoint isole,
 *   inutilisable pour une trace.
 * - `/globaldem` rend un **morceau de modele de terrain** sur une emprise, en grille ASCII (cf.
 *   [AsciiGrid]). Une requete pour tous les points d'un meme secteur, ce qui met une trace a quelques
 *   appels au lieu de quelques milliers.
 *
 * Le modele demande est le **Copernicus GLO-90**, et lui seul (cf. [DEM]).
 *
 * Le Copernicus plutot que le SRTM parce que celui-ci, issu du vol de 2000, **porte des trous** : ses
 * vides se concentrent dans les reliefs escarpes et les zones enneigees, c'est-a-dire exactement la ou
 * passent les traces qu'on importe ici, et un trou du modele coute la trace entiere (cf. la regle du tout
 * ou rien dans [ElevationFiller]).
 *
 * Le GLO-90 plutot que le GLO-30 pour le poids : les grilles Copernicus arrivent en flottants a dix-sept
 * chiffres, et le pas de 30 m multiplie par neuf le nombre de cellules d'une meme emprise - une soixantaine
 * de kilo-octets par secteur au lieu de plus d'un demi-mega. C'est le pas de terrain d'un profil, pas
 * celui d'un fond de carte : la France, elle, est servie au metre par l'IGN (cf. [IgnElevation]).
 *
 * Le modele porte les **deux noms** que lui donnent les deux API. Ce n'est pas une precaution theorique :
 * le SRTM, si on devait y revenir, s'ecrit `SRTM_GL3` cote point et `SRTMGL3` cote grille, et un nom
 * envoye a la mauvaise porte est refuse en silence. Les porter ensemble dans [Dem] evite d'avoir a s'en
 * souvenir - le Copernicus, lui, s'ecrit pareil des deux cotes.
 */
object OpenTopo {
    const val DEFAULT_URL = "https://portal.opentopography.org/API"

    /** Cle personnelle, en dur comme celles des fonds de carte (cf. `data/seed/Providers.kt`) : le service
     *  n'en delivre pas d'anonyme, et une cle a saisir a la main serait un obstacle pour une fonction dont
     *  l'interet est justement de ne rien demander. Reglable pour qui veut la sienne. */
    const val DEFAULT_KEY = "536fbf28ad1eabe9c26decf01ace8f32"

    /**
     * Un modele de terrain : son nom cote grille, son nom cote point, et son pas d'echantillonnage.
     *
     * Les trois voyagent ensemble parce qu'ils ne se devinent pas l'un l'autre et qu'en separer un seul
     * suffit a fausser tout le reste : une grille lue avec le pas d'un autre modele place ses altitudes a
     * cote, sans rien lever.
     */
    data class Dem(val grid: String, val point: String, val cellDeg: Double)

    /** Copernicus GLO-90 : 90 m, trois secondes d'arc. Le seul modele mondial interroge. */
    val DEM = Dem("COP90", "COP90", 3.0 / 3600.0)

    private val json = Json { ignoreUnknownKeys = true }

    /** URL de l'altitude d'un point. */
    fun pointUrl(base: String, key: String, p: LonLat, dem: Dem): String =
        base.trimEnd('/', '&', '?') + "/v1/elevation?longitude=" + fmt(p.lon) + "&latitude=" + fmt(p.lat) +
            "&dataset=" + dem.point + "&API_Key=" + key

    /**
     * URL du morceau de modele couvrant [box].
     *
     * Les bornes sont ecrites telles quelles : c'est a l'appelant d'avoir elargi l'emprise (cf.
     * [Bbox.padded]), le service refusant les emprises de moins de 250 m de cote.
     */
    fun gridUrl(base: String, key: String, box: Bbox, dem: Dem): String =
        base.trimEnd('/', '&', '?') + "/globaldem?demtype=" + dem.grid +
            "&south=" + fmt(box.south) + "&north=" + fmt(box.north) +
            "&west=" + fmt(box.west) + "&east=" + fmt(box.east) +
            "&outputFormat=AAIGrid&API_Key=" + key

    private fun fmt(v: Double): String = String.format(Locale.US, "%.6f", v)

    /**
     * Altitude lue dans la reponse d'un point, ou null.
     *
     * Null aussi bien pour une reponse illisible que pour un point sans donnee : le service repond alors un
     * corps `{"Status": "Not Found", "Elevation": null}`, que la lecture rend sans avoir a distinguer les
     * deux cas - l'appelant n'a rien de different a en faire.
     */
    fun parsePoint(body: String): Double? = runCatching {
        json.decodeFromString<PointResponse>(body).elevation
    }.getOrNull()

    @Serializable private data class PointResponse(@SerialName("Elevation") val elevation: Double? = null)
}
