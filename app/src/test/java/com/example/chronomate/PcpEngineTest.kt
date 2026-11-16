package com.example.chronomate

import com.example.chronomate.ballistics.Atmosphere
import com.example.chronomate.ballistics.DragModel
import com.example.chronomate.ballistics.PcpConstants
import com.example.chronomate.ballistics.PcpEngine
import com.example.chronomate.ballistics.PcpParams
import com.example.chronomate.ballistics.PcpWarning
import com.example.chronomate.ballistics.dragCoefficient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Same reference numbers as ChronoMate Desktop's tests/test_pcp_ballistics.py:
 * both apps must compute identical range cards.
 */
class PcpEngineTest {
    private val engine = PcpEngine()

    private val cases = mapOf(
        "base" to (PcpParams(massGrams = 18.13 * PcpConstants.GRAIN_TO_GRAM, bc = 0.035, muzzleVelocityMps = 274.32,
            zeroRangeM = 30.0, sightHeightCm = 5.0) to Atmosphere(15.0, 1013.25, 0.0)),
        "g7slug" to (PcpParams(massGrams = 2.2, bc = 0.045, dragModel = DragModel.G7, muzzleVelocityMps = 290.0,
            zeroRangeM = 50.0, sightHeightCm = 6.0, windSpeedMps = 3.0, windClock = 2.0, shotAngleDeg = 15.0,
            maxRangeM = 150.0) to Atmosphere(25.0, 950.0, 70.0)),
        "alt177" to (PcpParams(massGrams = 0.547, bc = 0.021, muzzleVelocityMps = 250.0, zeroRangeM = 25.0,
            sightHeightCm = 4.5, windSpeedMps = 2.0, windClock = 9.0) to Atmosphere(-5.0, 0.0, 30.0, 600.0, true))
    )

    // (case, distance m) -> (drop cm, drift cm, velocity m/s)
    private val reference = listOf(
        Triple("base", 25.0, doubleArrayOf(0.1355, 0.0, 244.3650)),
        Triple("base", 50.0, doubleArrayOf(-5.0159, 0.0, 220.0634)),
        Triple("base", 100.0, doubleArrayOf(-56.2234, 0.0, 180.3056)),
        Triple("g7slug", 25.0, doubleArrayOf(1.1753, -0.4954, 277.4201)),
        Triple("g7slug", 100.0, doubleArrayOf(-25.4253, -8.1115, 243.8517)),
        Triple("alt177", 50.0, doubleArrayOf(-9.6003, 7.9195, 177.3047)),
        Triple("alt177", 100.0, doubleArrayOf(-95.5985, 34.8558, 126.7843))
    )

    @Test
    fun sameNumbersAsDesktop() {
        val results = cases.mapValues { (_, c) -> engine.solve(c.first, c.second) }
        for ((name, dist, exp) in reference) {
            val pt = results.getValue(name).pointAt(dist)
            assertNotNull("$name@$dist", pt)
            assertEquals("$name@$dist drop", exp[0], pt!!.dropCm, 0.002)
            assertEquals("$name@$dist drift", exp[1], pt.driftCm, 0.002)
            assertEquals("$name@$dist v", exp[2], pt.velocityMps, 0.002)
        }
    }

    @Test
    fun noDragMatchesTheParabola() {
        val p = PcpParams(bc = 1e6, muzzleVelocityMps = 100.0, sightHeightCm = 0.0)
        val pt = engine.fly(p, Atmosphere(), 0.0, 0.0, 50.0, withWind = false).first { it.rangeM >= 50.0 }
        val t = pt.rangeM / 100.0
        assertEquals(-0.5 * p.gravity * t * t * 100.0, pt.dropCm, 0.001)
    }

    @Test
    fun zeroIsExact() {
        val (p, a) = cases.getValue("base")
        for (zero in listOf(15.0, 30.0, 45.0)) {
            val r = engine.solve(p.copy(zeroRangeM = zero), a)
            assertEquals(0.0, r.pointAt(zero)!!.dropCm, 0.01)
        }
    }

    @Test
    fun windDriftMatchesLagTimeRule() {
        val (p0, a) = cases.getValue("base")
        val p = p0.copy(windSpeedMps = 4.0, windClock = 3.0)
        val r = engine.solve(p, a)
        for (d in listOf(25.0, 50.0, 100.0)) {
            val pt = r.pointAt(d)!!
            val lag = pt.timeS - d / p.muzzleVelocityMps
            assertEquals(-4.0 * lag * 100.0, pt.driftCm, 0.05)
        }
    }

    @Test
    fun bcFromTwoSpeeds() {
        val (p, a) = cases.getValue("base")
        val v40 = engine.solve(p, a).pointAt(40.0)!!.velocityMps
        assertEquals(0.035, engine.solveBc(p, a, p.muzzleVelocityMps, 0.0, v40, 40.0)!!, 0.0003)
        assertNull(engine.solveBc(p, a, 250.0, 0.0, 260.0, 20.0))
    }

    @Test
    fun atmosphereAndTables() {
        val std = Atmosphere(15.0, 1013.25, 0.0)
        assertEquals(1.225, std.density(), 0.001)
        assertEquals(340.3, std.speedOfSound(), 0.1)
        assertEquals(0.2032, dragCoefficient(DragModel.G1, 0.5), 1e-9)
        assertEquals(0.3803, dragCoefficient(DragModel.G7, 1.0), 1e-9)
    }

    @Test
    fun transonicWarningAndBestZero() {
        val (p, a) = cases.getValue("base")
        assertTrue(PcpWarning.TRANSONIC in engine.solve(p.copy(muzzleVelocityMps = 320.0), a).warnings)
        val best = engine.bestZeroForKillZone(p.copy(killZoneCm = 3.0), a)!!
        val r = engine.solve(p.copy(zeroRangeM = best.zeroM, killZoneCm = 3.0), a)
        assertTrue(r.maxOrdinateCm <= 1.55)
        assertTrue(best.toM - best.fromM > 30.0)
    }
}
