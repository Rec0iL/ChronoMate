package com.example.chronomate.ballistics

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * PCP airgun ballistics (pellets & slugs): a point-mass (3-DOF) solver.
 *
 * - drag from the standard G1 / G7 tables, scaled by the ballistic coefficient (lb/in²) and looked
 *   up by Mach number, so drag rises the way it really does near the speed of sound;
 * - air density and speed of sound from temperature, pressure (or altitude) and humidity;
 * - wind as a vector in the drag (cross-, head- and tailwind);
 * - uphill / downhill shots (the zero is set on level ground, like a real rifle);
 * - the zero angle is solved exactly for the zero range and the scope height.
 *
 * This is a 1:1 port of ChronoMate Desktop's core/pcp_ballistics.py; both must give the same
 * numbers (see PcpEngineTest).
 */
object PcpConstants {
    const val GRAIN_TO_GRAM = 0.06479891
    const val MPS_TO_FPS = 1.0 / 0.3048
    const val J_TO_FTLB = 0.7375621
    const val LB_PER_IN2_TO_KG_PER_M2 = 703.0696
    const val MRAD_PER_MOA = 0.2908882
    const val INTEGRATION_DT = 0.0005
    const val MAX_FLIGHT_TIME = 4.0
    const val TRANSONIC_WARN_MACH = 0.90
}

enum class DragModel { G1, G7 }

private val G1_TABLE = doubleArrayOf(
    0.00, 0.2629, 0.05, 0.2558, 0.10, 0.2487, 0.15, 0.2413, 0.20, 0.2344,
    0.25, 0.2278, 0.30, 0.2214, 0.35, 0.2155, 0.40, 0.2104, 0.45, 0.2061,
    0.50, 0.2032, 0.55, 0.2020, 0.60, 0.2034, 0.70, 0.2165, 0.725, 0.2230,
    0.75, 0.2313, 0.775, 0.2417, 0.80, 0.2546, 0.825, 0.2706, 0.85, 0.2901,
    0.875, 0.3136, 0.90, 0.3415, 0.925, 0.3734, 0.95, 0.4084, 0.975, 0.4448,
    1.00, 0.4805, 1.025, 0.5136, 1.05, 0.5427, 1.075, 0.5677, 1.10, 0.5883,
    1.125, 0.6053, 1.15, 0.6191, 1.20, 0.6393, 1.25, 0.6518, 1.30, 0.6589,
    1.35, 0.6621, 1.40, 0.6625, 1.45, 0.6607, 1.50, 0.6573, 1.55, 0.6528,
    1.60, 0.6474, 1.65, 0.6413, 1.70, 0.6347, 1.75, 0.6280, 1.80, 0.6210,
    1.85, 0.6141, 1.90, 0.6072, 1.95, 0.6003, 2.00, 0.5934
)

private val G7_TABLE = doubleArrayOf(
    0.00, 0.1198, 0.05, 0.1197, 0.10, 0.1196, 0.15, 0.1194, 0.20, 0.1193,
    0.25, 0.1194, 0.30, 0.1194, 0.35, 0.1194, 0.40, 0.1193, 0.45, 0.1193,
    0.50, 0.1194, 0.55, 0.1193, 0.60, 0.1194, 0.65, 0.1197, 0.70, 0.1202,
    0.725, 0.1207, 0.75, 0.1215, 0.775, 0.1226, 0.80, 0.1242, 0.825, 0.1266,
    0.85, 0.1306, 0.875, 0.1368, 0.90, 0.1464, 0.925, 0.1660, 0.95, 0.2054,
    0.975, 0.2993, 1.00, 0.3803, 1.025, 0.4015, 1.05, 0.4043, 1.075, 0.4034,
    1.10, 0.4014, 1.125, 0.3987, 1.15, 0.3955, 1.20, 0.3884, 1.25, 0.3810,
    1.30, 0.3732, 1.35, 0.3657, 1.40, 0.3580, 1.50, 0.3440, 1.55, 0.3376,
    1.60, 0.3315, 1.65, 0.3260, 1.70, 0.3209, 1.75, 0.3160, 1.80, 0.3117,
    1.85, 0.3078, 1.90, 0.3042, 1.95, 0.3010, 2.00, 0.2980
)

fun dragCoefficient(model: DragModel, mach: Double): Double {
    val t = if (model == DragModel.G7) G7_TABLE else G1_TABLE
    val n = t.size / 2
    if (mach <= t[0]) return t[1]
    if (mach >= t[2 * (n - 1)]) return t[2 * (n - 1) + 1]
    var lo = 0
    var hi = n - 1
    while (hi - lo > 1) {
        val mid = (lo + hi) / 2
        if (t[2 * mid] <= mach) lo = mid else hi = mid
    }
    val m0 = t[2 * lo]; val c0 = t[2 * lo + 1]
    val m1 = t[2 * hi]; val c1 = t[2 * hi + 1]
    return c0 + (c1 - c0) * (mach - m0) / (m1 - m0)
}

data class Atmosphere(
    val temperatureC: Double = 15.0,
    val pressureHpa: Double = 1013.25,
    val humidityPct: Double = 50.0,
    val altitudeM: Double = 0.0,
    val useAltitude: Boolean = false
) {
    fun stationPressurePa(): Double =
        if (useAltitude) 101325.0 * (1.0 - 2.25577e-5 * altitudeM).pow(5.25588) else pressureHpa * 100.0

    private fun vapourPressurePa(): Double {
        val t = temperatureC
        val saturation = 610.78 * 10.0.pow(7.5 * t / (t + 237.3))
        return humidityPct.coerceIn(0.0, 100.0) / 100.0 * saturation
    }

    fun density(): Double {
        val tK = temperatureC + 273.15
        val p = stationPressurePa()
        val e = vapourPressurePa()
        return (p - e) / (287.058 * tK) + e / (461.495 * tK)
    }

    fun speedOfSound(): Double {
        val tK = temperatureC + 273.15
        val p = stationPressurePa()
        val e = vapourPressurePa()
        val tVirtual = tK / (1.0 - 0.378 * e / p)
        return sqrt(1.4 * 287.058 * tVirtual)
    }
}

data class PcpParams(
    val massGrams: Double = 1.175,
    val caliberMm: Double = 5.52,
    val bc: Double = 0.035,
    val dragModel: DragModel = DragModel.G1,
    val muzzleVelocityMps: Double = 274.0,
    val sightHeightCm: Double = 5.0,
    val zeroRangeM: Double = 30.0,
    val shotAngleDeg: Double = 0.0,
    val windSpeedMps: Double = 0.0,
    val windClock: Double = 3.0,
    val maxRangeM: Double = 100.0,
    val stepM: Double = 5.0,
    val killZoneCm: Double = 3.0,
    val gravity: Double = 9.80665
)

data class PcpPoint(
    val rangeM: Double,
    val dropCm: Double,
    val driftCm: Double,
    val velocityMps: Double,
    val energyJ: Double,
    val timeS: Double,
    val mach: Double
) {
    /** Angle to hold / dial: + = hold over (dial up), − = hold under. */
    fun holdMrad(): Double = if (rangeM <= 0) 0.0 else -atan2(dropCm / 100.0, rangeM) * 1000.0

    /** + = hold right (pellet blown left), − = hold left. */
    fun driftMrad(): Double = if (rangeM <= 0) 0.0 else -atan2(driftCm / 100.0, rangeM) * 1000.0
}

enum class PcpWarning { TRANSONIC, SHORT, OVERHOP_KILL_ZONE }

data class PcpResult(
    val points: List<PcpPoint> = emptyList(),
    val rangeCard: List<PcpPoint> = emptyList(),
    val zeroAngleMrad: Double = 0.0,
    val nearZeroM: Double? = null,
    val farZeroM: Double? = null,
    val maxOrdinateCm: Double = 0.0,
    val maxOrdinateRangeM: Double = 0.0,
    val killZoneFromM: Double? = null,
    val killZoneToM: Double? = null,
    val muzzleEnergyJ: Double = 0.0,
    val muzzleMach: Double = 0.0,
    val speedOfSoundMps: Double = 340.0,
    val airDensity: Double = 1.225,
    val warnings: List<PcpWarning> = emptyList()
) {
    fun pointAt(rangeM: Double): PcpPoint? = interpolate(points, rangeM)
}

data class KillZoneZero(val zeroM: Double, val fromM: Double, val toM: Double)

internal fun interpolate(points: List<PcpPoint>, rangeM: Double): PcpPoint? {
    if (points.isEmpty() || rangeM < points.first().rangeM || rangeM > points.last().rangeM) return null
    var lo = 0
    var hi = points.size - 1
    while (hi - lo > 1) {
        val mid = (lo + hi) / 2
        if (points[mid].rangeM <= rangeM) lo = mid else hi = mid
    }
    val a = points[lo]
    val b = points[hi]
    val span = b.rangeM - a.rangeM
    val f = if (span <= 0) 0.0 else (rangeM - a.rangeM) / span
    fun mix(u: Double, v: Double) = u + (v - u) * f
    return PcpPoint(
        rangeM = rangeM,
        dropCm = mix(a.dropCm, b.dropCm),
        driftCm = mix(a.driftCm, b.driftCm),
        velocityMps = mix(a.velocityMps, b.velocityMps),
        energyJ = mix(a.energyJ, b.energyJ),
        timeS = mix(a.timeS, b.timeS),
        mach = mix(a.mach, b.mach)
    )
}

private fun cross(a: PcpPoint, b: PcpPoint, level: Double): Double {
    val span = b.dropCm - a.dropCm
    if (span == 0.0) return a.rangeM
    return a.rangeM + (level - a.dropCm) / span * (b.rangeM - a.rangeM)
}

class PcpEngine {

    /** Integrates one shot (RK4) and returns points relative to the line of sight. */
    fun fly(
        p: PcpParams,
        atm: Atmosphere,
        zeroAngleRad: Double,
        shotAngleRad: Double,
        maxRangeM: Double,
        withWind: Boolean = true
    ): List<PcpPoint> {
        val rho = atm.density()
        val c = atm.speedOfSound()
        val m = p.massGrams / 1000.0
        val k = (Math.PI / 8.0) * rho / (max(p.bc, 1e-4) * PcpConstants.LB_PER_IN2_TO_KG_PER_M2)
        val model = p.dragModel
        val g = p.gravity
        val sight = p.sightHeightCm / 100.0

        // Wind "from" clock direction → air velocity vector (x downrange, z to the right).
        var wx = 0.0
        var wz = 0.0
        if (withWind && p.windSpeedMps > 0) {
            val theta = Math.toRadians((p.windClock % 12.0) * 30.0)
            wx = -p.windSpeedMps * cos(theta)
            wz = -p.windSpeedMps * sin(theta)
        }

        val bore = shotAngleRad + zeroAngleRad
        val v0 = p.muzzleVelocityMps
        val s = doubleArrayOf(0.0, 0.0, 0.0, v0 * cos(bore), v0 * sin(bore), 0.0)

        // Line of sight: from the scope (perpendicular above the bore) along the shot angle.
        val ux = cos(shotAngleRad)
        val uy = sin(shotAngleRad)
        val nx = -uy
        val ny = ux
        val sx = -sight * sin(shotAngleRad)
        val sy = sight * cos(shotAngleRad)

        fun deriv(st: DoubleArray, out: DoubleArray) {
            val rx = st[3] - wx
            val ry = st[4]
            val rz = st[5] - wz
            val v = sqrt(rx * rx + ry * ry + rz * rz)
            val a = k * dragCoefficient(model, v / c) * v
            out[0] = st[3]; out[1] = st[4]; out[2] = st[5]
            out[3] = -a * rx; out[4] = -a * ry - g; out[5] = -a * rz
        }

        fun toPoint(st: DoubleArray, t: Double): PcpPoint {
            val dx = st[0] - sx
            val dy = st[1] - sy
            val v = sqrt(st[3] * st[3] + st[4] * st[4] + st[5] * st[5])
            return PcpPoint(
                rangeM = dx * ux + dy * uy,
                dropCm = (dx * nx + dy * ny) * 100.0,
                driftCm = st[2] * 100.0,
                velocityMps = v,
                energyJ = 0.5 * m * v * v,
                timeS = t,
                mach = v / c
            )
        }

        val dt = PcpConstants.INTEGRATION_DT
        var t = 0.0
        val points = ArrayList<PcpPoint>(2048)
        points.add(toPoint(s, 0.0))
        val k1 = DoubleArray(6); val k2 = DoubleArray(6); val k3 = DoubleArray(6); val k4 = DoubleArray(6)
        val tmp = DoubleArray(6)
        while (t < PcpConstants.MAX_FLIGHT_TIME) {
            deriv(s, k1)
            for (i in 0 until 6) tmp[i] = s[i] + 0.5 * dt * k1[i]
            deriv(tmp, k2)
            for (i in 0 until 6) tmp[i] = s[i] + 0.5 * dt * k2[i]
            deriv(tmp, k3)
            for (i in 0 until 6) tmp[i] = s[i] + dt * k3[i]
            deriv(tmp, k4)
            for (i in 0 until 6) s[i] = s[i] + dt / 6.0 * (k1[i] + 2.0 * k2[i] + 2.0 * k3[i] + k4[i])
            t += dt
            val pt = toPoint(s, t)
            points.add(pt)
            if (pt.rangeM >= maxRangeM || pt.velocityMps < 5.0) break
        }
        return points
    }

    /**
     * Bore angle above the line of sight (rad) that puts the pellet on the LOS at zero range.
     * Zeroing happens on level ground and without wind, like at the range.
     */
    fun solveZeroAngle(p: PcpParams, atm: Atmosphere): Double {
        val zero = max(1.0, p.zeroRangeM)
        fun miss(angle: Double): Double {
            val pts = fly(p, atm, angle, 0.0, zero + 0.5, withWind = false)
            return interpolate(pts, zero)?.dropCm ?: -1e9
        }
        var angle = 0.0
        repeat(12) {
            val err = miss(angle)
            if (kotlin.math.abs(err) < 1e-4) return angle
            // d(drop_cm)/d(angle) ≈ zero_range_cm, exact enough for quick convergence
            angle -= err / (zero * 100.0)
            angle = angle.coerceIn(-0.05, 0.25)
        }
        return angle
    }

    fun solve(p: PcpParams, atm: Atmosphere): PcpResult {
        val c = atm.speedOfSound()
        val rho = atm.density()
        val muzzleEnergy = 0.5 * (p.massGrams / 1000.0) * p.muzzleVelocityMps.pow(2)
        val muzzleMach = p.muzzleVelocityMps / c
        val base = PcpResult(
            muzzleEnergyJ = muzzleEnergy, muzzleMach = muzzleMach,
            speedOfSoundMps = c, airDensity = rho
        )
        if (p.muzzleVelocityMps <= 1.0 || p.massGrams <= 0) return base

        val zeroAngle = solveZeroAngle(p, atm)
        val points = fly(p, atm, zeroAngle, Math.toRadians(p.shotAngleDeg), p.maxRangeM)

        val step = max(1.0, p.stepM)
        val n = (p.maxRangeM / step).toInt()
        val card = (0..n).mapNotNull { interpolate(points, it * step) }

        val halfKz = p.killZoneCm / 2.0
        var nearZero: Double? = null
        var farZero: Double? = null
        var maxOrd = 0.0
        var maxOrdRange = 0.0
        var kzFrom: Double? = null
        var kzTo: Double? = null
        var prev = points[0]
        var inZone = kotlin.math.abs(prev.dropCm) <= halfKz
        if (inZone) kzFrom = prev.rangeM
        for (i in 1 until points.size) {
            val pt = points[i]
            if (prev.dropCm < 0 && pt.dropCm >= 0 && nearZero == null) {
                nearZero = cross(prev, pt, 0.0)
            } else if (prev.dropCm >= 0 && pt.dropCm < 0 && farZero == null) {
                farZero = cross(prev, pt, 0.0)
            }
            if (pt.dropCm > maxOrd && farZero == null) {
                maxOrd = pt.dropCm
                maxOrdRange = pt.rangeM
            }
            val nowIn = kotlin.math.abs(pt.dropCm) <= halfKz
            if (nowIn && !inZone && kzFrom == null) kzFrom = pt.rangeM
            if (inZone && !nowIn && kzFrom != null && kzTo == null && pt.dropCm < -halfKz) kzTo = pt.rangeM
            inZone = nowIn
            prev = pt
        }
        if (kzFrom != null && kzTo == null && inZone) kzTo = points.last().rangeM

        val warnings = buildList {
            if (muzzleMach >= PcpConstants.TRANSONIC_WARN_MACH) add(PcpWarning.TRANSONIC)
            if (points.last().rangeM < p.maxRangeM - 0.5) add(PcpWarning.SHORT)
            if (maxOrd > halfKz) add(PcpWarning.OVERHOP_KILL_ZONE)
        }

        return base.copy(
            points = points,
            rangeCard = card,
            zeroAngleMrad = zeroAngle * 1000.0,
            nearZeroM = nearZero,
            farZeroM = farZero,
            maxOrdinateCm = maxOrd,
            maxOrdinateRangeM = maxOrdRange,
            killZoneFromM = kzFrom,
            killZoneToM = kzTo,
            warnings = warnings
        )
    }

    /**
     * Zero range that makes the kill-zone window as long as possible, on level ground without wind.
     * Moving the zero only tilts the bore by a tiny angle, so the new path is the old one plus a
     * straight line through the muzzle: drop'(s) = drop(s) - drop(Z) * s / Z.
     */
    fun bestZeroForKillZone(p: PcpParams, atm: Atmosphere, result: PcpResult? = null): KillZoneZero? {
        val r = if (result == null || p.shotAngleDeg != 0.0 || p.windSpeedMps != 0.0) {
            solve(p.copy(shotAngleDeg = 0.0, windSpeedMps = 0.0), atm)
        } else result
        val pts = r.points
        if (pts.size < 3) return null
        val half = p.killZoneCm / 2.0
        var best: KillZoneZero? = null
        val top = min(p.maxRangeM, pts.last().rangeM).toInt()
        for (zero in 5..top) {
            val atZero = interpolate(pts, zero.toDouble()) ?: break
            val tilt = atZero.dropCm / zero
            var start: Double? = null
            var end: Double? = null
            var broken = false
            for (pt in pts) {
                val d = pt.dropCm - tilt * pt.rangeM
                if (start == null) {
                    if (d >= -half) start = pt.rangeM
                    continue
                }
                if (d > half) { broken = true; break }
                if (d < -half) { end = pt.rangeM; break }
            }
            if (broken || start == null) continue
            val stop = end ?: pts.last().rangeM
            if (best == null || stop - start > best.toM - best.fromM) best = KillZoneZero(zero.toDouble(), start, stop)
        }
        return best
    }

    /** BC that turns vNear at dNear into vFar at dFar (two chrono readings). */
    fun solveBc(p: PcpParams, atm: Atmosphere, vNear: Double, dNear: Double, vFar: Double, dFar: Double): Double? {
        if (vFar >= vNear || dFar <= dNear || vNear <= 0) return null
        fun speedAt(bc: Double, dist: Double, v0: Double): Double {
            val q = p.copy(bc = bc, muzzleVelocityMps = v0, windSpeedMps = 0.0, shotAngleDeg = 0.0)
            val pts = fly(q, atm, 0.0, 0.0, dist + 0.5, withWind = false)
            return interpolate(pts, dist)?.velocityMps ?: 0.0
        }
        val span = dFar - dNear
        var lo = 0.002
        var hi = 1.5
        if (speedAt(lo, span, vNear) > vFar || speedAt(hi, span, vNear) < vFar) return null
        repeat(50) {
            val mid = sqrt(lo * hi)
            if (speedAt(mid, span, vNear) < vFar) lo = mid else hi = mid
        }
        return sqrt(lo * hi)
    }
}
