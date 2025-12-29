package com.example.chronomate.ballistics

import com.example.chronomate.model.BallisticParams
import com.example.chronomate.model.TrajectoryPoint
import kotlin.math.*

class BallisticsEngine {
    /**
     * Calculates the trajectory based on standard physics formulas for airsoft BBs.
     * Ported accurately from JS logic provided.
     */
    fun calculateTrajectory(params: BallisticParams): List<TrajectoryPoint> {
        val points = mutableListOf<TrajectoryPoint>()
        
        // Constants matching JS source exactly
        val dt = 0.01
        val dti = 16.0
        val n = 5000
        val pi = Math.PI
        val stepDt = dt / dti
        
        // Input variables
        val m = params.massGrams / 1000.0 // g to kg
        val d = params.diameterMm / 1000.0 // mm to m
        val rho = params.airDensityRho
        val cw = params.dragCoefficientCw
        val k = params.magnusCoefficientK
        val cr = params.spinDampingCr
        val g = params.gravity
        
        // Initial spin (rad/s)
        // JS: omega = input * -1
        var omega = params.hopUpRadS * -1.0
        
        // Initial velocity components
        val thetaRad = params.launchAngleDeg * (pi / 180.0)
        var vx = params.muzzleVelocityMps * cos(thetaRad)
        var vy = params.muzzleVelocityMps * sin(thetaRad)
        
        var x = 0.0
        var y = params.startingHeightM
        
        // Pre-calculated area and drag coefficient part
        val areaA = pi * d.pow(2.0) / 4.0
        val coeffC = 0.5 * cw * rho * areaA
        
        // Initial state at t=0
        points.add(TrajectoryPoint(x, y, params.muzzleVelocityMps, 0.5 * m * params.muzzleVelocityMps.pow(2.0), 0.0))
        
        for (i in 0 until n) {
            // JS logic uses velocity at the START of the step for forces and energy
            val vx_start = vx
            val vy_now = vy
            val v_start = sqrt(vx_start.pow(2.0) + vy_now.pow(2.0))
            
            // 1. Update Rotation (decay based on current velocity)
            val decayCoeff = cr * v_start
            omega *= exp(-decayCoeff * stepDt)
            
            // 2. Forces
            val fg = -m * g
            val fdx = -coeffC * v_start * vx_start
            val fdy = -coeffC * v_start * vy_now
            
            // Magnus Effect (Lift)
            val fmx = k * rho * areaA * vy_now * omega
            val fmy = -k * rho * areaA * vx_start * omega
            
            // 3. Acceleration (F = m*a)
            val ax = (fdx + fmx) / m
            val ay = (fg + fdy + fmy) / m
            
            // 4. Update Position (using velocity from start of step)
            x += vx_start * stepDt
            y += vy_now * stepDt
            
            // 5. Update Velocity for the next step
            vx += ax * stepDt
            vy += ay * stepDt
            
            // Metrics for the new position reached
            val currentV = sqrt(vx.pow(2.0) + vy.pow(2.0))
            val currentE = 0.5 * m * v_start * v_start // JS pushes energy using v before update
            val currentTime = (i + 1) * stepDt
            
            points.add(TrajectoryPoint(x, y, currentV, currentE, currentTime))
            
            // Stop if BB hits the ground
            if (y <= 0) break
        }
        
        return points
    }
}
