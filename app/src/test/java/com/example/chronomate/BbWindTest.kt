package com.example.chronomate

import com.example.chronomate.ballistics.BallisticsEngine
import com.example.chronomate.model.BallisticParams
import com.example.chronomate.model.ChronoData
import com.example.chronomate.model.EnvironmentSettings
import com.example.chronomate.model.effectiveAirDensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Weather & wind in the airsoft BB engine; numbers match ChronoMate Desktop's core/ballistics.py. */
class BbWindTest {
    private val engine = BallisticsEngine()
    private val base = BallisticParams(massGrams = 0.25, muzzleVelocityMps = 105.0, hopUpRadS = 900.0)

    @Test
    fun noWindNoDrift() {
        assertTrue(engine.calculateTrajectory(base).all { it.z == 0.0 })
    }

    @Test
    fun crosswindMatchesDesktop() {
        val pts = engine.calculateTrajectory(base.copy(windSpeedMps = 3.0, windClock = 3.0))
        assertEquals(2853, pts.size)
        val p = pts.minBy { abs(it.x - 30.0) }
        assertEquals(-0.394979, p.z, 1e-5)
        assertEquals(2.027588, p.y, 1e-5)
        assertEquals(51.248267, p.velocity, 1e-5)
    }

    @Test
    fun headwindShortensRangeLikeDesktop() {
        val pts = engine.calculateTrajectory(base.copy(windSpeedMps = 4.0, windClock = 12.0))
        assertEquals(65.659474, pts.last().x, 1e-5)
    }

    @Test
    fun weatherScalesTheTunedDensity() {
        val std = ChronoData(environment = EnvironmentSettings(15.0, 1013.25, 0.0))
        assertEquals(1.225, std.effectiveAirDensity(), 1e-5)
        val hot = ChronoData(environment = EnvironmentSettings(35.0, 1013.25, 0.0))
        assertTrue(hot.effectiveAirDensity() < 1.225)
    }
}
