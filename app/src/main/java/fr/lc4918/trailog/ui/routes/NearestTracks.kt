package fr.lc4918.trailog.ui.routes

import fr.lc4918.trailog.data.db.LayerEntity
import fr.lc4918.trailog.domain.geo.OffTrack
import fr.lc4918.trailog.ui.alert.TrackCandidate

/**
 * Le tri en deux temps qui trouve la trace la plus proche du doigt, sans tout relire.
 *
 * Chercher la trace a suivre demande de PROJETER la position sur chaque trace, ce qui coute la lecture de
 * son profil - des milliers de points par couche. Le faire sur toutes les couches visibles serait
 * ruineux la ou l'utilisateur en a cinquante.
 *
 * D'ou deux passes. La premiere ne lit rien : elle classe les couches par la distance a leur **emprise**,
 * un rectangle deja en base, et n'en retient qu'une poignee. La seconde ne projette que celles-la, puis
 * classe les segments trouves par l'ecart reel.
 *
 * Pur et sans Android : ce sont les deux bornes qui decident du travail fourni, et elles se verrouillent
 * par des tests. La lecture des profils, elle, reste dans le ViewModel, ou elle peut etre parallelisee.
 */

/**
 * Couches reellement lues pour chercher la trace la plus proche.
 *
 * Douze, parce qu'une poignee de traces se recouvrent au meme endroit : en dessous, on risque d'ecarter la
 * bonne avant meme de l'avoir ouverte ; au-dessus, on paie des lectures pour des traces qui sont a des
 * kilometres.
 */
const val NEAREST_SCAN_LAYERS = 12

/**
 * Segments proposes au bout du compte.
 *
 * Huit, parce qu'au-dela on ne choisit plus, on cherche - et une liste de propositions qui demande elle-meme
 * a etre parcourue n'a pas rendu le service qu'on lui demandait.
 */
const val NEAREST_TRACK_COUNT = 8

/**
 * Premiere passe : les couches a ouvrir, classees par la distance a leur emprise.
 *
 * Seules les couches **visibles et porteuses d'une ligne** entrent : une couche eteinte n'est pas a
 * l'ecran, et une couche de points seuls n'a pas de trace a suivre.
 *
 * La distance a l'emprise est une minoration de la distance a la trace - le rectangle contient la trace -
 * si bien qu'un mauvais classement ici ne peut qu'ouvrir une couche pour rien, jamais en ecarter une qui
 * serait plus proche que celles retenues.
 */
fun layersToScan(
    layers: List<LayerEntity>,
    lat: Double,
    lon: Double,
    limit: Int = NEAREST_SCAN_LAYERS,
): List<LayerEntity> =
    layers.filter { it.visible && it.hasLine }
        .sortedBy { OffTrack.bboxDistanceM(lat, lon, it.west, it.south, it.east, it.north) }
        .take(limit)

/** Seconde passe : les segments trouves, du plus proche au plus loin, bornes a [limit]. */
fun closestCandidates(
    found: List<TrackCandidate>,
    limit: Int = NEAREST_TRACK_COUNT,
): List<TrackCandidate> = found.sortedBy { it.awayM }.take(limit)
