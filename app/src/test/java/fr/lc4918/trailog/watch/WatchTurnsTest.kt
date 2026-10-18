package fr.lc4918.trailog.watch

import fr.lc4918.trailog.watch.WatchTrack.WatchTrackPoint
import fr.lc4918.trailog.watch.WatchTurns.WatchTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchTurnsTest {

    private val latitudeMicro = 45_000_000
    private val longitudeMicro = 5_000_000
    private val metersPerMicroLatitude = 0.11054
    private val metersPerMicroLongitude = 0.11132 * Math.cos(Math.toRadians(45.0))

    /**
     * Une trace faite de branches droites : chaque pas donne un cap (degres, 0 au nord) et une longueur (m),
     * avec un point tous les 10 m comme une trace reelle apres simplification des lignes droites.
     */
    private fun walk(vararg legs: Pair<Double, Int>): List<WatchTrackPoint> {
        val points = ArrayList<WatchTrackPoint>()
        var latitude = latitudeMicro.toDouble()
        var longitude = longitudeMicro.toDouble()
        var distance = 0
        points.add(WatchTrackPoint(latitude.toInt(), longitude.toInt(), distance))
        for ((heading, length) in legs) {
            repeat(length / 10) {
                latitude += Math.cos(Math.toRadians(heading)) * 10 / metersPerMicroLatitude
                longitude += Math.sin(Math.toRadians(heading)) * 10 / metersPerMicroLongitude
                distance += 10
                points.add(WatchTrackPoint(latitude.roundToIntValue(), longitude.roundToIntValue(), distance))
            }
        }
        return points
    }

    private fun Double.roundToIntValue() = Math.round(this).toInt()

    @Test fun `a straight track has no turn`() {
        assertTrue(WatchTurns.detect(walk(90.0 to 500)).isEmpty())
    }

    @Test fun `a right angle to the right is a turn of plus 90 degrees`() {
        val turns = WatchTurns.detect(walk(0.0 to 200, 90.0 to 200))
        assertEquals(1, turns.size)
        assertEquals(90, turns[0].angleDegrees)
        assertEquals(90, turns[0].headingDegrees)
        assertEquals(200, turns[0].distanceMeters)
    }

    @Test fun `a right angle to the left is a turn of minus 90 degrees`() {
        val turns = WatchTurns.detect(walk(0.0 to 200, 270.0 to 200))
        assertEquals(1, turns.size)
        assertEquals(-90, turns[0].angleDegrees)
        assertEquals(270, turns[0].headingDegrees)
    }

    @Test fun `a turn gives the position of its vertex`() {
        val track = walk(0.0 to 200, 90.0 to 200)
        val turn = WatchTurns.detect(track)[0]
        val vertex = track.first { it.distanceMeters == 200 }
        assertEquals(vertex.latitudeMicro, turn.latitudeMicro)
        assertEquals(vertex.longitudeMicro, turn.longitudeMicro)
    }

    @Test fun `a u-turn is reported near 180 degrees`() {
        val turns = WatchTurns.detect(walk(0.0 to 200, 180.0 to 200))
        assertEquals(1, turns.size)
        assertTrue(Math.abs(turns[0].angleDegrees) >= 170)
    }

    @Test fun `a slight bend under the threshold is not a turn`() {
        assertTrue(WatchTurns.detect(walk(0.0 to 200, 20.0 to 200)).isEmpty())
    }

    @Test fun `a turn is found at the threshold`() {
        assertEquals(1, WatchTurns.detect(walk(0.0 to 200, 40.0 to 200)).size)
    }

    @Test fun `a gentle curve spread over a long distance is not a turn`() {
        // 90 degres repartis sur 18 branches de 20 m : 5 degres par branche, jamais 35 sur 30 m.
        val legs = (0 until 18).map { (it * 5.0) to 20 }.toTypedArray()
        assertTrue(WatchTurns.detect(walk(0.0 to 200, *legs, 85.0 to 200)).isEmpty())
    }

    @Test fun `two vertices of the same turn make one turn`() {
        // Coin coupe en deux branches de 45 degres : deux sommets a 10 m l'un de l'autre.
        val turns = WatchTurns.detect(walk(0.0 to 200, 45.0 to 10, 90.0 to 200))
        assertEquals(1, turns.size)
    }

    @Test fun `separate turns stay separate and come in order`() {
        val turns = WatchTurns.detect(walk(0.0 to 200, 90.0 to 200, 0.0 to 200))
        assertEquals(2, turns.size)
        assertTrue(turns[0].distanceMeters < turns[1].distanceMeters)
        assertEquals(90, turns[0].angleDegrees)
        assertEquals(-90, turns[1].angleDegrees)
    }

    @Test fun `tracks too short for a turn give none`() {
        assertTrue(WatchTurns.detect(emptyList()).isEmpty())
        assertTrue(WatchTurns.detect(listOf(WatchTrackPoint(1, 1, 0))).isEmpty())
        assertTrue(WatchTurns.detect(listOf(WatchTrackPoint(1, 1, 0), WatchTrackPoint(2, 2, 5))).isEmpty())
    }

    @Test fun `heading after the turn is between 0 and 359`() {
        val turns = WatchTurns.detect(walk(270.0 to 200, 0.0 to 200))
        assertEquals(1, turns.size)
        assertTrue(turns[0].headingDegrees in 0..359)
        assertEquals(0, turns[0].headingDegrees)
    }
}
