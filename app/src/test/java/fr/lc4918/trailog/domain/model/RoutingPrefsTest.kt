package fr.lc4918.trailog.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Lecture et ecriture des preferences de trace.
 *
 * Ce qui se joue ici : un reglage relu de travers n'echoue pas, il calcule un autre itineraire que celui
 * demande - et rien a l'ecran ne le dit.
 */
class RoutingPrefsTest {

    @Test fun `la forme enregistree se relit a l'identique`() {
        val p = RoutingPrefs(WayPref.SOFT, HillPref.SEEK, SurfacePref.ROUGH)
        assertEquals(p, RoutingPrefs.of(p.asCsv(), RoutingProfile.MOUNTAIN_BIKE))
        assertEquals("soft,seek,rough", p.asCsv())
    }

    /** Base d'avant le reglage, ou colonne videe a la main : chaque discipline retrouve son defaut. */
    @Test fun `sans rien d'enregistre, chaque discipline prend son defaut`() {
        RoutingProfile.entries.forEach { profile ->
            assertEquals(profile.name, RoutingPrefs.defaultFor(profile), RoutingPrefs.of(null, profile))
            assertEquals(profile.name, RoutingPrefs.defaultFor(profile), RoutingPrefs.of("", profile))
        }
    }

    /**
     * Une cle inconnue - reglage ecrit par une version plus recente, puis rouvert par une plus ancienne -
     * ne doit emporter que son propre champ.
     */
    @Test fun `une cle inconnue ne fait retomber que son champ`() {
        val p = RoutingPrefs.of("soft,pourquoi_pas,rough", RoutingProfile.HYBRID_BIKE)
        assertEquals(WayPref.SOFT, p.ways)
        assertEquals(RoutingPrefs.defaultFor(RoutingProfile.HYBRID_BIKE).hills, p.hills)
        assertEquals(SurfacePref.ROUGH, p.surface)
    }

    @Test fun `une forme tronquee garde ce qu'elle porte`() {
        val p = RoutingPrefs.of("roads", RoutingProfile.FOOT)
        assertEquals(WayPref.ROADS, p.ways)
        assertEquals(RoutingPrefs.defaultFor(RoutingProfile.FOOT).surface, p.surface)
    }

    /** Les defauts sont ceux que la mesure a retenus ; les changer doit etre un geste conscient. */
    @Test fun `les defauts mesures sont ceux qu'on croit`() {
        // Le velo de route ne demande rien sur le revetement : l'exiger le fait fuir les chemins par de
        // longs detours (Grenoble - Chamrousse : +3,5 km pour eviter 3 % de non revetu).
        assertEquals(RoutingPrefs(WayPref.SOFT, HillPref.BALANCED, SurfacePref.BALANCED),
            RoutingPrefs.defaultFor(RoutingProfile.ROAD_BIKE))
        assertEquals(RoutingPrefs(WayPref.SOFT, HillPref.SEEK, SurfacePref.ROUGH),
            RoutingPrefs.defaultFor(RoutingProfile.MOUNTAIN_BIKE))
        assertEquals(RoutingPrefs(WayPref.SOFT, HillPref.BALANCED, SurfacePref.ROUGH),
            RoutingPrefs.defaultFor(RoutingProfile.FOOT))
    }

    /** La position centrale des trois questions ne demande rien : c'est elle qui laisse le service faire. */
    @Test fun `la position centrale est celle qui ne demande rien`() {
        assertEquals("balanced,balanced,balanced", RoutingPrefs.Balanced.asCsv())
    }
}
