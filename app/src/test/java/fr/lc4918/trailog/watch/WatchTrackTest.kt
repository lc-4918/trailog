package fr.lc4918.trailog.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchTrackTest {

    @Test fun `coordinates become micro-degrees with latitude first`() {
        val points = WatchTrack.prepare(listOf(5.877123 to 45.121456, 5.9 to 45.2))
        assertEquals(WatchTrack.WatchTrackPoint(45_121_456, 5_877_123), points.first())
        assertEquals(WatchTrack.WatchTrackPoint(45_200_000, 5_900_000, points.last().distanceMeters), points.last())
        assertTrue(points.last().distanceMeters > 8_000)
    }

    @Test fun `negative coordinates keep their sign`() {
        val points = WatchTrack.prepare(listOf(-1.5 to -45.25, -1.4 to -45.2))
        assertEquals(WatchTrack.WatchTrackPoint(-45_250_000, -1_500_000), points.first())
    }

    @Test fun `points on a straight line are dropped but the ends stay`() {
        val straight = (0..100).map { 5.0 + it * 0.0001 to 45.0 }
        val points = WatchTrack.prepare(straight)
        assertEquals(2, points.size)
        assertEquals(5_000_000, points.first().longitudeMicro)
        assertEquals(5_010_000, points.last().longitudeMicro)
    }

    @Test fun `a corner larger than the tolerance is kept`() {
        // Coin a 90 degres : le point du coin est a des centaines de metres de la corde.
        val points = WatchTrack.prepare(listOf(5.0 to 45.0, 5.01 to 45.0, 5.01 to 45.01))
        assertEquals(3, points.size)
    }

    @Test fun `each point carries its distance from the start along the track`() {
        // 0,01 degre de latitude : environ 1112 m.
        val points = WatchTrack.prepare(listOf(5.0 to 45.0, 5.0 to 45.01, 5.01 to 45.01))
        assertEquals(0, points[0].distanceMeters)
        assertEquals(1112.0, points[1].distanceMeters.toDouble(), 2.0)
        assertEquals(1112.0 + 786.0, points[2].distanceMeters.toDouble(), 4.0)
    }

    @Test fun `an empty or single point track is kept as is`() {
        assertTrue(WatchTrack.prepare(emptyList()).isEmpty())
        assertEquals(1, WatchTrack.prepare(listOf(5.0 to 45.0)).size)
    }
}
