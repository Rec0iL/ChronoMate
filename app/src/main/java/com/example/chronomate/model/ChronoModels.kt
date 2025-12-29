package com.example.chronomate.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.pow

data class Shot(
    val velocity: Float,
    val weightGrams: Float,
    val energyJoules: Float = 0.5f * (weightGrams / 1000f) * velocity.pow(2)
)

data class ChronoData(
    val velocity: String = "0.00",
    val fireRate: String = "0.0",
    val shots: List<Shot> = emptyList(),
    val averageVelocity: Float = 0f,
    val maxVelocity: Float = 0f,
    val minVelocity: Float = 0f,
    val extremeSpread: Float = 0f,
    val standardDeviation: Float = 0f,
    val variance: Float = 0f,
    val isConnected: Boolean = false,
    val selectedWeight: Float = 0.20f,
    val currentEnergy: Float = 0f,
    val wifiStatus: String = "Disconnected",
    val isDarkMode: Boolean = true,
    val maxAllowedJoule: Float = 1.5f,
    val maxAllowedOverhopCm: Float = 15.0f
)

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Home)
    object OrgaChrono : Screen("orga_chrono", "Orga Chrono", Icons.Default.Shield)
    object Trajectory : Screen("trajectory", "Trajectory", Icons.Default.Timeline)
    object History : Screen("history", "History", Icons.Default.History)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}
