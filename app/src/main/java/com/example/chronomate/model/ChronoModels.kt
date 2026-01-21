package com.example.chronomate.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.chronomate.R
import kotlin.math.pow

data class Shot(
    val velocity: Float,
    val weightGrams: Float,
    val energyJoules: Float = 0.5f * (weightGrams / 1000f) * velocity.pow(2)
)

enum class WeightType {
    BB, DIABLO, CUSTOM
}

data class CustomWeight(
    val name: String,
    val weight: Float,
    val caliber: Float = 6.0f,
    val caliberUnit: String = "mm"
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
    val language: String = "en",
    val maxAllowedJoule: Float = 1.5f,
    val maxAllowedOverhopCm: Float = 15.0f,
    
    // Weight Settings
    val weightType: WeightType = WeightType.BB,
    val customWeights: List<CustomWeight> = emptyList(),
    
    // Ballistic Settings
    val diameterMm: Float = 5.95f,
    val airDensityRho: Float = 1.225f,
    val dragCoefficientCw: Float = 0.35f,
    val magnusCoefficientK: Float = 0.002f,
    val spinDampingCr: Float = 0.01f,
    val gravity: Float = 9.81f
)

sealed class Screen(val route: String, val titleResId: Int, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", R.string.dashboard, Icons.Default.Home)
    object OrgaChrono : Screen("orga_chrono", R.string.orga_chrono, Icons.Default.Shield)
    object Trajectory : Screen("trajectory", R.string.trajectory, Icons.Default.Timeline)
    object History : Screen("history", R.string.history, Icons.Default.History)
    object Export : Screen("export", R.string.export, Icons.Default.PictureAsPdf)
    object Settings : Screen("settings", R.string.settings, Icons.Default.Settings)
}
