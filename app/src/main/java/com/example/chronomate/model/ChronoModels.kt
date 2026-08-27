package com.example.chronomate.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.chronomate.R
import kotlin.math.pow

data class Shot(
    val rawVelocity: Float,
    val weightGrams: Float,
    val compensationFactor: Float = 1.0f
) {
    val velocity: Float get() = rawVelocity * compensationFactor
    val energyJoules: Float get() = 0.5f * (weightGrams / 1000f) * velocity.pow(2)
}

enum class WeightType {
    BB, DIABLO, CUSTOM
}

enum class CompensationMode {
    NONE, MEDIAN, TRUST_EXTERNAL
}

data class CalibrationEntry(
    val chronoValue: Float,
    val controlValue: Float? = null
)

data class CustomWeight(
    val name: String,
    val weight: Float,
    val caliber: Float = 6.0f,
    val caliberUnit: String = "mm"
)

enum class ChronoType(val ssid: String, val displayName: String) {
    HT_X3000("HT-X3000", "HT-X3000"),
    HT_50("HT50", "HT-50")
}

/**
 * Single source of truth for the link to the chronograph.
 *
 * The states are ordered by "how much do we actually know":
 * only [CONNECTED] means the chrono itself answered us, everything below that
 * describes how far the connection attempt got.
 */
enum class ConnectionState(val labelResId: Int, val badgeResId: Int) {
    /** No link to the chrono - nothing joined, nothing answering. */
    DISCONNECTED(R.string.status_disconnected, R.string.badge_offline),

    /** Wi-Fi association requested, waiting for the network to come up. */
    CONNECTING(R.string.status_connecting, R.string.badge_connecting),

    /** Joined the chrono Wi-Fi, but its web page has not answered (yet). */
    LINKED(R.string.status_waiting, R.string.badge_waiting),

    /** The chrono is answering - this is the only state that means "live". */
    CONNECTED(R.string.status_connected, R.string.badge_live),

    /** The Wi-Fi association itself failed or errored out. */
    FAILED(R.string.status_failed, R.string.badge_failed)
}

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
    val selectedWeight: Float = 0.20f,
    val currentEnergy: Float = 0f,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val connectionError: String? = null,
    val isDarkMode: Boolean = true,
    val keepScreenOn: Boolean = false,
    val language: String = "en",
    val maxAllowedJoule: Float = 1.5f,
    val maxAllowedOverhopCm: Float = 15.0f,
    
    // Chrono Settings
    val chronoType: ChronoType = ChronoType.HT_X3000,
    
    // Weight Settings
    val weightType: WeightType = WeightType.BB,
    val customWeights: List<CustomWeight> = emptyList(),
    
    // Ballistic Settings
    val diameterMm: Float = 5.95f,
    val airDensityRho: Float = 1.225f,
    val dragCoefficientCw: Float = 0.35f,
    val magnusCoefficientK: Float = 0.002f,
    val spinDampingCr: Float = 0.01f,
    val gravity: Float = 9.81f,

    // Calibration
    val compensationMode: CompensationMode = CompensationMode.NONE,
    val calibrationEntries: List<CalibrationEntry> = emptyList(),
    val isCalibrationActive: Boolean = false,
    val compensationFactor: Float = 1.0f
) {
    /** Derived - the app is "connected" exactly when the chrono is talking to us. */
    val isConnected: Boolean get() = connectionState == ConnectionState.CONNECTED
}

sealed class Screen(val route: String, val titleResId: Int, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", R.string.dashboard, Icons.Default.Home)
    object OrgaChrono : Screen("orga_chrono", R.string.orga_chrono, Icons.Default.Shield)
    object Trajectory : Screen("trajectory", R.string.trajectory, Icons.Default.Timeline)
    object History : Screen("history", R.string.history, Icons.Default.History)
    object Export : Screen("export", R.string.export, Icons.Default.PictureAsPdf)
    object Settings : Screen("settings", R.string.settings, Icons.Default.Settings)
}
