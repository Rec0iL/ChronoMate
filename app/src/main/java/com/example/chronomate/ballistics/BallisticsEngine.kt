package com.example.chronomate.ballistics

import com.example.chronomate.model.BallisticParams
import com.example.chronomate.model.TrajectoryPoint
import kotlin.math.*

class BallisticsEngine {
    fun calculateTrajectory(params: BallisticParams): List<TrajectoryPoint> {
        val points = mutableListOf<TrajectoryPoint>()
        
        // Constants from JS
        val dt = 0.01
        val dti = 16.0
        val n = 5000
        val pi = Math.PI
        
        // Initial values
        val m = params.massGrams / 1000.0 // kg
        val d = params.diameterMm / 1000.0 // m
        val rho = params.airDensityRho
        val cw = params.dragCoefficientCw
        val k = params.magnusCoefficientK
        val cr = params.spinDampingCr
        val g = params.gravity
        
        // In JS: omega = document...value * -1;
        // The negative sign is important for the Magnus force orientation
        var omega = params.hopUpRadS * -1.0
        val thetaRad = params.launchAngleDeg * (pi / 180.0)
        
        var vx = params.muzzleVelocityMps * cos(thetaRad)
        var vy = params.muzzleVelocityMps * sin(thetaRad)
        
        var x = 0.0
        var y = params.startingHeightM
        
        val areaA = pi * d.pow(2.0) / 4.0
        val coeffC = 0.5 * cw * rho * areaA
        
        val stepDt = dt / dti
        
        points.add(TrajectoryPoint(x, y, params.muzzleVelocityMps, 0.5 * m * params.muzzleVelocityMps.pow(2.0), 0.0))
        
        for (i in 0 until n) {
            val v = sqrt(vx.pow(2.0) + vy.pow(2.0))
            
            // Rotation decay
            val cDecay = cr * v
            omega *= exp(-cDecay * stepDt)
            
            // Forces
            val fg = -m * g
            val fdx = -coeffC * v * vx
            val fdy = -coeffC * v * vy
            
            // Magnus force (Lift)
            // Fm_x = k * rho * A * v_y * omega
            // Fm_y = -k * rho * A * v_x * omega
            val fmx = k * rho * areaA * vy * omega
            val fmy = -k * rho * areaA * vx * omega
            
            // Acceleration
            val ax = (fdx + fmx) / m
            val ay = (fg + fdy + fmy) / m
            
            // Velocity update (Euler integration matching JS)
            // x.push((x[i_1] + v_x[i_1] * (dt / dti)));
            val nextX = x + vx * stepDt
            val nextY = y + vy * stepDt
            
            // v_x.push((v_x[i_1] + ax * (dt / dti)));
            vx += ax * stepDt
            vy += ay * stepDt
            
            x = nextX
            y = nextY
            
            val currentV = sqrt(vx.pow(2.0) + vy.pow(2.0))
            val currentE = 0.5 * m * currentV.pow(2.0)
            val currentTime = (i + 1) * stepDt
            
            points.add(TrajectoryPoint(x, y, currentV, currentE, currentTime))
            
            if (y <= 0) break
        }
        
        return points
    }
}
