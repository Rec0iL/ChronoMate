package com.example.chronomate.model

import kotlin.math.pow

data class BallisticParams(
    val massGrams: Double = 0.20,
    val diameterMm: Double = 5.95,
    val muzzleVelocityMps: Double = 100.0,
    val launchAngleDeg: Double = 0.0,
    val startingHeightM: Double = 1.5,
    val hopUpRadS: Double = 1000.0,
    val airDensityRho: Double = 1.225,
    val dragCoefficientCw: Double = 0.35, // Optimized for GWC/High-performance Airsoft profiles
    val magnusCoefficientK: Double = 0.002, // Adjusted lift coefficient to match GWC Apex
    val spinDampingCr: Double = 0.01,
    val gravity: Double = 9.81
)

data class TrajectoryPoint(
    val x: Double,
    val y: Double,
    val velocity: Double,
    val energyJoules: Double,
    val time: Double
)
