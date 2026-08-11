package fr.lc4918.trailog.domain.geo

import fr.lc4918.trailog.domain.model.Sample
import fr.lc4918.trailog.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMathTest {

    @Test fun haversine_oneDegreeLat_isAbout111km() {
        val d = TrackMath.haversine(0.0, 0.0, 0.0, 1.0)
        assertEquals(111195.0, d, 200.0)   // ~111,2 km, tolérance 200 m
    }

    @Test fun compute_ascentDescent_andDuration() {
        // montée 0->100 m puis descente 100->50 m, 1 point / 60 s, vitesse > seuil d'arrêt
        val base = 45.0; val lon0 = 6.0
        val eles = listOf(0.0, 50.0, 100.0, 75.0, 50.0)
        val pts = eles.mapIndexed { i, e ->
            TrackPoint(lon = lon0 + i * 0.001, lat = base, ele = e, timeMs = i * 60_000L)
        }
        val c = TrackMath.compute(pts, ignoreStops = false, maxPoints = 0)
        assertTrue(c.hasZ); assertTrue(c.hasTime)
        assertEquals(100.0, c.stats.ascent, 1.0)      // +50 +50
        assertEquals(50.0, c.stats.descent, 1.0)      // -25 -25
        assertEquals(100.0, c.stats.max, 0.1)
        assertEquals(0.0, c.stats.min, 0.1)
        assertEquals(240.0, c.stats.duration ?: -1.0, 0.1)  // 4 intervalles * 60 s
    }

    // ---------- Temps de marche estime (Tobler) ----------

    /** Un echantillon a x metres du depart, a l'altitude z. */
    private fun s(x: Double, z: Double) = Sample(x, z, 0.0, null, 6.0, 45.0)

    /**
     * A plat, Tobler donne 6 km/h moins le decalage de 0,05 qui place son maximum en legere descente :
     * 6 x exp(-3,5 x 0,05) = 5,04 km/h, soit 714 s pour un kilometre.
     */
    @Test fun `un kilometre de plat se marche a cinq kilometres heure`() {
        val plat = (0..10).map { s(it * 100.0, 500.0) }
        assertEquals(714.0, TrackMath.toblerSeconds(plat), 5.0)
    }

    /**
     * Ce que cette fonction apporte sur une moyenne : la meme distance ne se marche pas dans le meme temps
     * selon la pente, et une legere descente est plus rapide que le plat.
     */
    @Test fun `la pente change le temps, et la legere descente est le plus rapide`() {
        val plat = (0..10).map { s(it * 100.0, 500.0) }
        val montee = (0..10).map { s(it * 100.0, 500.0 + it * 20.0) }        // 20 %
        val descenteDouce = (0..10).map { s(it * 100.0, 500.0 - it * 5.0) }  // -5 %
        val tPlat = TrackMath.toblerSeconds(plat)
        assertTrue("une montee a 20 % doit couter plus cher que le plat",
            TrackMath.toblerSeconds(montee) > tPlat * 2)
        assertTrue("une descente douce doit etre plus rapide que le plat",
            TrackMath.toblerSeconds(descenteDouce) < tPlat)
    }

    @Test fun `une trace trop courte ne donne aucune estimation`() {
        assertEquals(0.0, TrackMath.toblerSeconds(emptyList()), 0.0)
        assertEquals(0.0, TrackMath.toblerSeconds(listOf(s(0.0, 500.0))), 0.0)
    }

    // ---------- Point courant continu ----------

    /**
     * Le point courant se pose ENTRE deux echantillons. Sans cette interpolation, il sautait de l'un a
     * l'autre - jusqu'a plusieurs dizaines de metres sur une longue trace, dont le profil n'affiche que
     * deux mille points - et la coupe qui s'y fiait heritait de la meme limite.
     */
    @Test fun `l'echantillon interpole se pose entre deux points`() {
        val montee = listOf(s(0.0, 500.0), s(100.0, 600.0))
        val mid = TrackMath.sampleAt(montee, 25.0)!!
        assertEquals(25.0, mid.x, 1e-9)
        assertEquals(525.0, mid.z, 1e-9)
    }

    @Test fun `l'interpolation porte aussi sur la position et le temps`() {
        val a = Sample(0.0, 500.0, 0.0, 0.0, 6.0, 45.0)
        val b = Sample(100.0, 600.0, 10.0, 60.0, 6.001, 45.001)
        val mid = TrackMath.sampleAt(listOf(a, b), 50.0)!!
        assertEquals(6.0005, mid.lon, 1e-9)
        assertEquals(45.0005, mid.lat, 1e-9)
        assertEquals(30.0, mid.t!!, 1e-9)
        // La pente est celle du troncon parcouru, non une moyenne : la moyenner l'adoucirait la ou elle change.
        assertEquals(10.0, mid.slope, 1e-9)
    }

    @Test fun `demander au-dela des bouts rend le bout`() {
        val montee = listOf(s(0.0, 500.0), s(100.0, 600.0))
        assertEquals(500.0, TrackMath.sampleAt(montee, -50.0)!!.z, 1e-9)
        assertEquals(600.0, TrackMath.sampleAt(montee, 5000.0)!!.z, 1e-9)
        assertNull(TrackMath.sampleAt(emptyList(), 0.0))
    }

    /** Deux mille echantillons, un curseur qui bouge au doigt : la recherche doit tomber juste a chaque
     *  fois, y compris sur les bornes exactes des troncons. */
    @Test fun `chaque sommet se retrouve exactement`() {
        val trace = (0..100).map { s(it * 37.0, 500.0 + it) }
        trace.forEach { pt ->
            assertEquals("abscisse ${pt.x}", pt.z, TrackMath.sampleAt(trace, pt.x)!!.z, 1e-9)
        }
    }

    // ---------- Ce qui reste a parcourir ----------

    @Test fun `le restant se compte depuis le point ou l'on se trouve`() {
        val montee = (0..10).map { s(it * 100.0, 500.0 + it * 10.0) }   // 1 km, +100 m
        val r = TrackMath.remaining(montee, 400.0)
        assertEquals(600.0, r.distance, 0.001)
        assertEquals(60.0, r.ascent, 0.001)
    }

    /** Le point ou l'on se trouve tombe rarement sur un echantillon : sans interpolation, la montee du
     *  segment en cours serait comptee en entier alors qu'on en a deja fait la moitie. */
    @Test fun `le denivele du segment en cours n'est compte que pour ce qu'il en reste`() {
        val montee = listOf(s(0.0, 500.0), s(100.0, 600.0))
        assertEquals(50.0, TrackMath.remaining(montee, 50.0).ascent, 0.001)
    }

    /** Seule la montee compte : ce qui reste a descendre n'est pas un effort a prevoir. */
    @Test fun `les descentes ne comptent pas dans le denivele restant`() {
        val vallonne = listOf(s(0.0, 500.0), s(100.0, 550.0), s(200.0, 500.0), s(300.0, 530.0))
        assertEquals(80.0, TrackMath.remaining(vallonne, 0.0).ascent, 0.001)
    }

    @Test fun `arrive au bout, il ne reste rien`() {
        val montee = (0..10).map { s(it * 100.0, 500.0 + it * 10.0) }
        assertEquals(0.0, TrackMath.remaining(montee, 1000.0).distance, 0.001)
        assertEquals(0.0, TrackMath.remaining(montee, 5000.0).ascent, 0.001)
        assertEquals(0.0, TrackMath.remaining(emptyList(), 0.0).distance, 0.001)
    }

    @Test fun compute_movingTime_ignoresStops() {
        // 3 points : 2e point quasi immobile pendant 600 s -> ignoré en temps de mouvement
        val pts = listOf(
            TrackPoint(6.000, 45.0, 10.0, 0L),
            TrackPoint(6.010, 45.0, 12.0, 60_000L),       // ~785 m en 60 s : roule
            TrackPoint(6.0100001, 45.0, 12.0, 660_000L),  // ~0 m en 600 s : arrêt
        )
        val moving = TrackMath.compute(pts, ignoreStops = true, stopSpeed = 0.5, maxPoints = 0)
        val all = TrackMath.compute(pts, ignoreStops = false, maxPoints = 0)
        assertEquals(60.0, moving.stats.duration ?: -1.0, 1.0)   // seul le 1er intervalle compte
        assertEquals(660.0, all.stats.duration ?: -1.0, 1.0)
    }
}
