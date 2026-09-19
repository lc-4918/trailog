package fr.lc4918.trailog.routing.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le catalogue des zones d'itineraire hors ligne, et ce qu'on retire en en supprimant une.
 *
 * La faute a eviter est silencieuse : supprimer les Alpes et amputer la France de ses carres alpins - le
 * calcul hors ligne echouerait a Grenoble sans que rien ne dise pourquoi.
 */
class BrouterZonesTest {

    private fun zone(id: String) = BrouterZones.byId(id)!!

    /** Des lieux reels de chaque zone, extremites et iles comprises : chacun doit tomber dans un de ses carres. */
    private val lieux = mapOf(
        "fr" to listOf(2.35 to 48.86, -4.49 to 48.39, -5.10 to 48.46, 7.26 to 43.70, 8.74 to 41.92, 7.75 to 48.58,
            3.06 to 50.63, 2.89 to 42.70),
        "es" to listOf(-3.70 to 40.42, 2.17 to 41.39, -5.98 to 37.39, -8.40 to 43.37, 2.65 to 39.57, -6.29 to 36.53),
        "pt" to listOf(-9.14 to 38.72, -8.61 to 41.15, -7.93 to 37.02),
        "it" to listOf(12.50 to 41.90, 9.19 to 45.46, 13.36 to 38.12, 9.11 to 39.22, 18.17 to 40.35, 7.32 to 45.74),
        "ch" to listOf(6.14 to 46.20, 8.54 to 47.37, 9.84 to 46.50),
        "at" to listOf(16.37 to 48.21, 11.39 to 47.27, 9.75 to 47.50, 15.44 to 47.07),
        "de" to listOf(13.40 to 52.52, 11.58 to 48.14, 9.99 to 53.55, 6.08 to 50.78, 8.30 to 54.90, 14.99 to 51.15),
        "be" to listOf(4.35 to 50.85, 5.81 to 49.68, 2.92 to 51.22, 5.57 to 50.63),
        "gb" to listOf(-0.13 to 51.51, -3.19 to 55.95, -5.93 to 54.60, -5.54 to 50.12, -3.52 to 58.59, 1.31 to 51.13),
        "alps" to listOf(6.87 to 45.92, 5.72 to 45.19, 7.75 to 46.02, 11.35 to 46.50, 13.04 to 47.80, 7.26 to 43.70),
        "pyrenees" to listOf(-0.37 to 43.30, 1.52 to 42.51, 2.89 to 42.70, -1.64 to 42.81),
    )

    @Test fun `chaque zone couvre ses lieux`() {
        assertEquals(BrouterZones.all.map { it.id }.toSet(), lieux.keys)
        lieux.forEach { (id, pts) ->
            pts.forEach { (lon, lat) -> assertTrue("$id ne couvre pas $lon, $lat", BrouterTile.of(lon, lat) in zone(id).tiles) }
        }
    }

    @Test fun `les identifiants sont uniques`() {
        assertEquals(BrouterZones.all.size, BrouterZones.all.map { it.id }.toSet().size)
    }

    /** Les Alpes n'ont aucun carre qui ne soit aussi a un pays : les supprimer ne retire rien a la France. */
    @Test fun `supprimer une zone garde les carres d'une autre`() {
        val liberes = BrouterZones.releasable(zone("alps"), listOf(zone("fr"), zone("alps")))
        assertFalse(BrouterTile(5, 45) in liberes)
        assertFalse(BrouterTile(5, 40) in liberes)
        assertTrue(BrouterTile(10, 45) in liberes)
    }

    @Test fun `seule, une zone rend tous ses carres`() {
        assertEquals(zone("fr").tiles, BrouterZones.releasable(zone("fr"), listOf(zone("fr"))))
    }

    /** La Belgique ne telecharge pas un carre de France pour la pointe de Chimay. */
    @Test fun `une zone ne prend pas les carres qu'elle ne fait qu'effleurer`() {
        assertFalse(BrouterTile(0, 45) in zone("be").tiles)
        assertEquals(9, zone("fr").tiles.size)
    }

    // ---------- Ce que l'etat en dit ----------

    private fun remote(vararg t: Pair<BrouterTile, Long>) =
        t.associate { (tile, b) -> tile to BrouterSegments.Remote(tile, b, 1_000_000L) }

    /** Apres la France, les Alpes ne demandent plus que leurs deux carres de l'est. */
    @Test fun `ce qui reste a telecharger ne compte pas les carres deja la`() {
        val alpes = zone("alps")
        val st = BrouterDownloads.State(
            installed = listOf(BrouterSegments.Installed(BrouterTile(5, 45), 252, 2_000_000L),
                BrouterSegments.Installed(BrouterTile(5, 40), 69, 2_000_000L)),
            remote = remote(BrouterTile(5, 45) to 252L, BrouterTile(5, 40) to 69L,
                BrouterTile(10, 45) to 300L, BrouterTile(15, 45) to 100L),
        )
        assertEquals(setOf(BrouterTile(10, 45), BrouterTile(15, 45)), st.missing(alpes))
        assertEquals(400L, st.remaining(alpes))
        assertEquals(721L, st.weight(alpes))
    }

    /** Un carre dont le serveur a une version plus recente est a telecharger, lui aussi. */
    @Test fun `un carre perime compte dans ce qui reste`() {
        val st = BrouterDownloads.State(
            installed = listOf(BrouterSegments.Installed(BrouterTile(-5, 40), 90, 0L)),
            remote = remote(BrouterTile(-5, 40) to 92L, BrouterTile(0, 40) to 120L),
        )
        assertEquals(212L, st.remaining(zone("pyrenees")))
    }

    /** Deux zones en cours qui se partagent un carre : il ne compte qu'une fois dans le total. */
    @Test fun `la progression globale compte une fois un carre partage`() {
        val st = BrouterDownloads.State(
            remote = remote(BrouterTile(5, 45) to 250L, BrouterTile(10, 45) to 300L),
            pending = mapOf("ch" to setOf(BrouterTile(5, 45), BrouterTile(10, 45)),
                "alps" to setOf(BrouterTile(5, 45))),
            current = BrouterDownloads.Progress(BrouterTile(5, 45), 100L, 250L),
            queued = listOf(BrouterTile(10, 45)),
        )
        assertEquals(100L to 550L, st.overall())
        assertEquals(null, BrouterDownloads.State().overall())
    }
}
