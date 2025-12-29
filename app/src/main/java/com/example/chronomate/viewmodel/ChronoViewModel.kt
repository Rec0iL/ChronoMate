package com.example.chronomate.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chronomate.model.ChronoData
import com.example.chronomate.model.Shot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlin.math.sqrt

class ChronoViewModel(context: Context) : ViewModel() {
    private val prefs = context.getSharedPreferences("chrono_prefs", Context.MODE_PRIVATE)
    
    private val _uiState = MutableStateFlow(ChronoData(
        selectedWeight = prefs.getFloat("default_weight", 0.20f),
        isDarkMode = prefs.getBoolean("is_dark_mode", true),
        maxAllowedJoule = prefs.getFloat("max_allowed_joule", 1.5f),
        maxAllowedOverhopCm = prefs.getFloat("max_allowed_overhop", 15.0f)
    ))
    val uiState = _uiState.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()
    private val url = "http://8.8.8.8/"

    private var lastSeenRawShots = emptyList<Float>()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        startPolling()
    }

    fun toggleDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean("is_dark_mode", enabled).apply()
        _uiState.update { it.copy(isDarkMode = enabled) }
    }

    fun setMaxAllowedJoule(joule: Float) {
        prefs.edit().putFloat("max_allowed_joule", joule).apply()
        _uiState.update { it.copy(maxAllowedJoule = joule) }
    }

    fun setMaxAllowedOverhop(cm: Float) {
        prefs.edit().putFloat("max_allowed_overhop", cm).apply()
        _uiState.update { it.copy(maxAllowedOverhopCm = cm) }
    }

    fun connectToChronoWifi(context: Context) {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            
            networkCallback?.let { 
                try { connectivityManager.unregisterNetworkCallback(it) } catch (e: Exception) {}
            }

            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid("HT-X3000")
                .setWpa2Passphrase("88888888")
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build()

            _uiState.update { it.copy(wifiStatus = "Connecting...") }

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    connectivityManager.bindProcessToNetwork(network)
                    _uiState.update { it.copy(wifiStatus = "Connected to Chrono") }
                }

                override fun onLost(network: Network) {
                    connectivityManager.bindProcessToNetwork(null)
                    _uiState.update { it.copy(wifiStatus = "Disconnected") }
                }

                override fun onUnavailable() {
                    _uiState.update { it.copy(wifiStatus = "Connection Failed") }
                }
            }
            
            networkCallback = callback
            connectivityManager.requestNetwork(request, callback)
        } catch (e: Exception) {
            _uiState.update { it.copy(wifiStatus = "Error: ${e.message}") }
        }
    }

    fun setWeight(weight: Float) {
        _uiState.update { it.copy(selectedWeight = weight) }
    }

    fun saveDefaultWeight(weight: Float) {
        prefs.edit().putFloat("default_weight", weight).apply()
        setWeight(weight)
    }

    private fun startPolling() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("Cache-Control", "no-cache") // Ensure fresh data
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val html = response.body?.string() ?: ""
                            if (html.isNotEmpty()) {
                                parseHtml(html)
                            }
                        } else {
                            _uiState.update { it.copy(isConnected = false) }
                        }
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isConnected = false) }
                }
                delay(1000)
            }
        }
    }

    private fun parseHtml(html: String) {
        val doc = Jsoup.parse(html)
        val text = doc.text()

        // Extract fire rate
        val fireRate = "(?i)(?:firerate|射速)[^0-9]*(\\d+(?:\\.\\d+)?)".toRegex()
            .find(text)?.groupValues?.get(1) ?: "0.0"

        // Extract shots from the Chrono history [01...10]. 01 is oldest, 10 is newest.
        val rawShots = "(\\d{2})\\s*[:：]\\s*(\\d+(?:\\.\\d+)?)".toRegex().findAll(text)
            .map { it.groupValues[2].toFloat() }
            .filter { it > 0.0f }
            .toList()

        _uiState.update { current ->
            val currentSessionShots = current.shots.toMutableList()
            val currentWeight = current.selectedWeight
            var updatedLastSeen = lastSeenRawShots

            if (rawShots.isNotEmpty() && rawShots != lastSeenRawShots) {
                var newShotsToAdd = emptyList<Float>()
                
                if (lastSeenRawShots.isEmpty()) {
                    newShotsToAdd = rawShots
                } else {
                    // Match suffix of lastSeen with prefix of rawShots to find new entries
                    for (i in rawShots.size downTo 0) {
                        val head = rawShots.take(i)
                        if (lastSeenRawShots.size >= i && lastSeenRawShots.takeLast(i) == head) {
                            newShotsToAdd = rawShots.drop(i)
                            break
                        }
                    }
                }
                
                newShotsToAdd.forEach { vel ->
                    currentSessionShots.add(Shot(vel, currentWeight))
                }
                updatedLastSeen = rawShots
            }

            lastSeenRawShots = updatedLastSeen

            val latestShot = currentSessionShots.lastOrNull()
            val currentVelocity = latestShot?.velocity?.let { "%.2f".format(it) } ?: "0.00"
            val currentEnergy = latestShot?.energyJoules ?: 0f

            val velocities = currentSessionShots.map { it.velocity }
            val avg = if (velocities.isNotEmpty()) velocities.average().toFloat() else 0f
            val max = if (velocities.isNotEmpty()) velocities.maxOrNull() ?: 0f else 0f
            val min = if (velocities.isNotEmpty()) velocities.minOrNull() ?: 0f else 0f
            val es = if (velocities.isNotEmpty()) max - min else 0f
            
            val varVal = if (velocities.size > 1) {
                val mean = velocities.average()
                velocities.sumOf { (it - mean).pow(2.0) }.toFloat() / velocities.size
            } else 0f
            val sd = sqrt(varVal)

            current.copy(
                velocity = currentVelocity,
                fireRate = fireRate,
                shots = currentSessionShots,
                averageVelocity = avg,
                maxVelocity = max,
                minVelocity = min,
                extremeSpread = es,
                standardDeviation = sd,
                variance = varVal,
                currentEnergy = currentEnergy,
                isConnected = true
            )
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        networkCallback = null
    }
}
