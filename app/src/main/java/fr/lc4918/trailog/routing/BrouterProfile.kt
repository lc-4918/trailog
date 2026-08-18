package fr.lc4918.trailog.routing

import fr.lc4918.trailog.domain.model.HillPref
import fr.lc4918.trailog.domain.model.RoutingPrefs
import fr.lc4918.trailog.domain.model.RoutingProfile
import fr.lc4918.trailog.domain.model.SurfacePref
import fr.lc4918.trailog.domain.model.WayPref

/**
 * Le profil BRouter d'une discipline, réglé sur les trois préférences de l'utilisateur.
 *
 * C'est le pendant exact de `Valhalla.costingOptionsOf` : la même question posée à l'autre moteur. La
 * différence est que BRouter ne reçoit pas des options mais **le texte du profil entier**, qui décrit le
 * coût tag par tag. On part donc des profils officiels, repris tels quels dans les assets, et l'on y
 * réécrit les quelques lignes qui nous concernent - exactement ce que fait brouter-web quand on bouge un
 * curseur dans son panneau.
 *
 * **Chaque profil parle son propre dialecte**, et c'est la raison d'être de la table ci-dessous.
 * `trekking.brf` dit `stick_to_cycleroutes`, `gravel.brf` dit `prefer_cycle_routes`, `mtb.brf` dit
 * `cycleroutes_pref` et compte en nombres là où les deux premiers comptent en booléens. Traduire cinq
 * fois la même demande n'est pas une redondance : c'est le prix de profils que l'on n'a pas écrits, et
 * qui en échange savent des choses qu'on ne saurait pas réécrire.
 *
 * Comme chez Valhalla, **la position centrale n'écrit rien** - mais pour une raison inverse. Là-bas le
 * silence était le seul moyen de ne pas déranger un service qui n'applique pas ses propres défauts ; ici
 * il laisse au profil la valeur que ses auteurs ont retenue, ce qui est le plus sûr des défauts.
 */
object BrouterProfile {

    /** Fichier d'assets porteur du profil, discipline par discipline. */
    fun assetOf(profile: RoutingProfile): String = "brouter/" + when (profile) {
        RoutingProfile.ROAD_BIKE -> "fastbike"
        RoutingProfile.GRAVEL -> "gravel"
        RoutingProfile.HYBRID_BIKE -> "trekking"
        RoutingProfile.MOUNTAIN_BIKE -> "mtb"
        RoutingProfile.FOOT -> "hiking-mountain"
    } + ".brf"

    /**
     * Les valeurs à réécrire dans le profil de [profile] pour obtenir [prefs].
     *
     * Ce que chaque dialecte appelle nos trois questions :
     * - **quelles voies** : `consider_traffic` au vélo de route (sa seule façon de fuir la circulation),
     *   `prefer_cycle_routes` au gravel, `stick_to_cycleroutes` / `ignore_cycleroutes` au VTC,
     *   `cycleroutes_pref` au VTT, et au marcheur `hiking_routes_preference` - qui pénalise ce qui n'est
     *   PAS un itinéraire de randonnée balisé. Ce dernier n'a aucun équivalent chez Valhalla, dont le
     *   modèle piéton ignore les relations d'itinéraire : c'est le manque qui a motivé ce second moteur.
     * - **quel relief** : `consider_elevation` partout, qui commande les coûts de montée et de descente
     *   du profil. Le sens est contre-intuitif et vaut d'être dit : le mettre à vrai fait ÉVITER le
     *   dénivelé. "Accepter le dénivelé" s'écrit donc en l'éteignant.
     * - **quel revêtement** : `unpavedPenalty` au VTC, `prefer_unpaved_paths` au gravel,
     *   `path_preference` au VTT, et au marcheur `SAC_scale_limit` - la cotation de randonnée au-delà de
     *   laquelle un sentier lui est interdit, le pendant du `max_hiking_difficulty` demandé à Valhalla.
     *
     * Le vélo de route n'a de ligne ni pour le revêtement ni pour les voies douces, et les deux silences
     * sont mesurés : `fastbike.brf` n'expose aucun levier de revêtement, et son levier de circulation
     * échange justement le trafic contre le revêtement - l'actionner l'envoie sur les chemins. Sans rien
     * lui dire, il tient 100 % de route sur les quatre trajets de référence.
     */
    internal fun tuningOf(profile: RoutingProfile, prefs: RoutingPrefs): Map<String, String> {
        val o = linkedMapOf<String, String>()
        when (profile) {
            RoutingProfile.ROAD_BIKE -> {
                // Rien pour "voies douces", et c'est une mesure, pas un oubli : le seul levier de
                // fastbike.brf echange la circulation contre le revetement, exactement a l'envers de ce
                // qu'un velo de route demande. Au-dela de 0,2, Grenoble - Voiron part a 60 % sur les
                // chemins. Le profil sans rien tient deja les 100 % de route sur les quatre trajets.
                when (prefs.ways) {
                    WayPref.ROADS -> o["consider_traffic"] = "0.0"
                    WayPref.BALANCED, WayPref.SOFT -> Unit
                }
                when (prefs.hills) {
                    HillPref.AVOID -> o["consider_elevation"] = "true"
                    HillPref.BALANCED -> Unit
                    HillPref.SEEK -> o["consider_elevation"] = "false"
                }
            }
            RoutingProfile.GRAVEL -> {
                when (prefs.ways) {
                    WayPref.ROADS -> o["prefer_cycle_routes"] = "false"
                    WayPref.BALANCED -> Unit
                    WayPref.SOFT -> o["prefer_cycle_routes"] = "true"
                }
                when (prefs.hills) {
                    HillPref.AVOID -> { o["consider_elevation"] = "true"; o["avoid_steep_inclines"] = "true" }
                    HillPref.BALANCED -> Unit
                    HillPref.SEEK -> { o["consider_elevation"] = "false"; o["avoid_steep_inclines"] = "false" }
                }
                when (prefs.surface) {
                    SurfacePref.PAVED -> o["prefer_unpaved_paths"] = "false"
                    SurfacePref.BALANCED -> Unit
                    SurfacePref.ROUGH -> o["prefer_unpaved_paths"] = "true"
                }
            }
            RoutingProfile.HYBRID_BIKE -> {
                when (prefs.ways) {
                    WayPref.ROADS -> o["ignore_cycleroutes"] = "true"
                    WayPref.BALANCED -> Unit
                    WayPref.SOFT -> o["stick_to_cycleroutes"] = "true"
                }
                when (prefs.hills) {
                    HillPref.AVOID -> o["consider_elevation"] = "true"
                    HillPref.BALANCED -> Unit
                    HillPref.SEEK -> o["consider_elevation"] = "false"
                }
                when (prefs.surface) {
                    SurfacePref.PAVED -> o["unpavedPenalty"] = "3.0"
                    SurfacePref.BALANCED -> Unit
                    SurfacePref.ROUGH -> o["unpavedPenalty"] = "0.0"
                }
            }
            RoutingProfile.MOUNTAIN_BIKE -> {
                when (prefs.ways) {
                    WayPref.ROADS -> o["cycleroutes_pref"] = "0.0"
                    WayPref.BALANCED -> Unit
                    WayPref.SOFT -> o["cycleroutes_pref"] = "0.5"
                }
                when (prefs.hills) {
                    HillPref.AVOID -> { o["consider_elevation"] = "1"; o["hills"] = "1" }
                    HillPref.BALANCED -> Unit
                    HillPref.SEEK -> { o["consider_elevation"] = "0"; o["hills"] = "0" }
                }
                when (prefs.surface) {
                    SurfacePref.PAVED -> { o["path_preference"] = "0.0"; o["MTB_factor"] = "-0.5" }
                    SurfacePref.BALANCED -> Unit
                    SurfacePref.ROUGH -> { o["path_preference"] = "20.0"; o["MTB_factor"] = "0.5" }
                }
            }
            RoutingProfile.FOOT -> {
                // Rien non plus pour "voies douces", et pour la meme raison que le velo de route : le
                // profil le fait deja mieux que la demande. Pousser `hiking_routes_preference` au-dela de
                // son 0,20 ne bouge rien jusqu'a 0,30, puis se retourne - a 0,40 Revel - Soreze tombe de
                // 57 a 22 % de chemins, le balisage y suivant la route. En baisser reste utile : c'est ce
                // que veut dire "par les routes".
                when (prefs.ways) {
                    WayPref.ROADS -> o["hiking_routes_preference"] = "0.0"
                    WayPref.BALANCED, WayPref.SOFT -> Unit
                }
                when (prefs.hills) {
                    HillPref.AVOID -> o["consider_elevation"] = "true"
                    HillPref.BALANCED -> Unit
                    HillPref.SEEK -> o["consider_elevation"] = "false"
                }
                // La cotation, et elle seule. `path_preference` penalise ce qui n'est pas un sentier,
                // voie verte comprise : a 2 deja, Moulin-Neuf - Mirepoix perd la sienne (87 % de voies
                // douces tombent a 24 %) et gagne 144 m de denivele ; a 10, Grenoble - Voiron passe de
                // 26 a 39 km. On le laisse ou ses auteurs l'ont mis.
                when (prefs.surface) {
                    SurfacePref.PAVED -> o["SAC_scale_limit"] = "1"
                    SurfacePref.BALANCED -> Unit
                    SurfacePref.ROUGH -> o["SAC_scale_limit"] = "3"
                }
            }
        }
        return o
    }

    /** Le texte à envoyer : le profil officiel [raw], ses valeurs réglées sur [prefs]. */
    fun tune(raw: String, profile: RoutingProfile, prefs: RoutingPrefs): String =
        tuningOf(profile, prefs).entries.fold(raw) { texte, (nom, valeur) -> assign(texte, nom, valeur) }

    /**
     * Réécrit la valeur de la **première** déclaration de [name], et rend [text] inchangé s'il n'y en a pas.
     *
     * La première, et non toutes : un profil déclare sa variable puis la dérive parfois plus bas
     * (`assign downhillcost = if consider_elevation then downhillcost else 0`). Écraser la dérivée
     * emporterait la logique du profil, celle-là même qu'on veut garder.
     *
     * Inchangé plutôt qu'une erreur si la variable manque : un profil amont qui renomme un curseur ne doit
     * pas priver d'itinéraire. On perd le réglage, pas le trajet - et le test de la table le rattrape.
     *
     * Le signe égal est facultatif : `trekking.brf` écrit `assign x = 1`, `mtb.brf` écrit `assign x 1`.
     * Les deux dialectes cohabitent dans les assets, et la même expression doit les lire tous les deux.
     */
    internal fun assign(text: String, name: String, value: String): String {
        val re = Regex("""^(\s*assign\s+${Regex.escape(name)}\s*(?:=\s*)?)(\S+)""", RegexOption.MULTILINE)
        val m = re.find(text) ?: return text
        return text.substring(0, m.range.first) + m.groupValues[1] + value + text.substring(m.range.last + 1)
    }
}
