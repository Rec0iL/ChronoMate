package com.example.chronomate.model

import com.example.chronomate.ballistics.DragModel

enum class BallisticMode { AIRSOFT, PCP }

enum class ClickUnit(val label: String, val mradPerClick: Double) {
    MRAD_01("0.1 MRAD", 0.1),
    MOA_025("1/4 MOA", 0.25 * 0.2908882),
    MOA_0125("1/8 MOA", 0.125 * 0.2908882)
}

enum class VelocitySource { LATEST, AVERAGE, MANUAL }

/** Weather at the range (mirrors EnvironmentSettings on the desktop). */
data class EnvironmentSettings(
    val temperatureC: Double = 15.0,
    val pressureHpa: Double = 1013.25,
    val humidityPct: Double = 50.0,
    val altitudeM: Double = 0.0,
    val useAltitude: Boolean = false,
    val windSpeedMps: Double = 0.0,
    val windClock: Double = 3.0
)

/** PCP calculator inputs (mirrors PcpSettings on the desktop). */
data class PcpSettings(
    val projectileName: String = "JSB Exact Jumbo Heavy .22",
    val massGrams: Double = 1.175,
    val caliberMm: Double = 5.52,
    val bc: Double = 0.033,
    val dragModel: DragModel = DragModel.G1,
    val velocitySource: VelocitySource = VelocitySource.LATEST,
    val manualVelocityMps: Double = 274.0,
    val zeroRangeM: Double = 30.0,
    val sightHeightCm: Double = 5.0,
    val clickUnit: ClickUnit = ClickUnit.MRAD_01,
    val shotAngleDeg: Double = 0.0,
    val killZoneCm: Double = 3.0,
    val stepM: Double = 5.0,
    val maxRangeM: Double = 100.0,
    /** 0 = off; e.g. 7.5 J (German F-mark) or 16.27 J (UK 12 ft-lb). */
    val energyLimitJ: Double = 0.0
)
