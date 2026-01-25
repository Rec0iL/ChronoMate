package com.example.chronomate.viewmodel

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.WindowManager
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chronomate.model.ChronoData
import com.example.chronomate.model.CustomWeight
import com.example.chronomate.model.Shot
import com.example.chronomate.model.WeightType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlin.math.sqrt

class ChronoViewModel(context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(ChronoData())
    val uiState = _uiState.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private var lastSeenRawShots = emptyList<Float>()
    private val url = "http://8.8.8.8"
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        startPolling()
        loadPreferences(context)
    }

    private fun loadPreferences(context: Context) {
        val prefs = context.getSharedPreferences("chrono_prefs", Context.MODE_PRIVATE)
        
        val customWeightsJson = prefs.getString("custom_weights", "[]") ?: "[]"
        val customWeights = if (customWeightsJson != "[]" && customWeightsJson.isNotEmpty()) {
            customWeightsJson.split(";").mapNotNull {
                val parts = it.split(":")
                if (parts.size >= 2) {
                    CustomWeight(
                        name = parts[0],
                        weight = parts[1].toFloatOrNull() ?: 0.20f,
                        caliber = if (parts.size >= 3) parts[2].toFloatOrNull() ?: 6.0f else 6.0f,
                        caliberUnit = if (parts.size >= 4) parts[3] else "mm"
                    )
                } else null
            }
        } else emptyList()

        _uiState.update { it.copy(
            isDarkMode = prefs.getBoolean("dark_mode", true),
            keepScreenOn = prefs.getBoolean("keep_screen_on", false),
            language = prefs.getString("language", "en") ?: "en",
            maxAllowedJoule = prefs.getFloat("max_joule", 1.5f),
            maxAllowedOverhopCm = prefs.getFloat("max_overhop", 15f),
            weightType = WeightType.valueOf(prefs.getString("weight_type", WeightType.BB.name) ?: WeightType.BB.name),
            customWeights = customWeights,
            diameterMm = prefs.getFloat("diameter", 5.95f),
            airDensityRho = prefs.getFloat("air_density", 1.225f),
            dragCoefficientCw = prefs.getFloat("drag_coeff", 0.35f),
            magnusCoefficientK = prefs.getFloat("magnus_coeff", 0.002f),
            spinDampingCr = prefs.getFloat("spin_damping", 0.01f),
            gravity = prefs.getFloat("gravity", 9.81f)
        ) }
        
        // Apply initial screen on state
        if (_uiState.value.keepScreenOn) {
            applyKeepScreenOn(context, true)
        }
    }

    private fun savePreference(context: Context, key: String, value: Any) {
        val prefs = context.getSharedPreferences("chrono_prefs", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Float -> putFloat(key, value)
                is String -> putString(key, value)
            }
            apply()
        }
    }

    private fun saveCustomWeights(context: Context, weights: List<CustomWeight>) {
        val serialized = weights.joinToString(";") { "${it.name}:${it.weight}:${it.caliber}:${it.caliberUnit}" }
        savePreference(context, "custom_weights", serialized)
    }

    fun toggleDarkMode(context: Context, enabled: Boolean) {
        _uiState.update { it.copy(isDarkMode = enabled) }
        savePreference(context, "dark_mode", enabled)
    }

    fun toggleKeepScreenOn(context: Context, enabled: Boolean) {
        _uiState.update { it.copy(keepScreenOn = enabled) }
        savePreference(context, "keep_screen_on", enabled)
        applyKeepScreenOn(context, enabled)
    }

    private fun applyKeepScreenOn(context: Context, enabled: Boolean) {
        if (context is Activity) {
            if (enabled) {
                context.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                context.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    fun setLanguage(context: Context, langCode: String) {
        savePreference(context, "language", langCode)
        _uiState.update { it.copy(language = langCode) }
        
        // Trigger restart
        if (context is Activity) {
            val intent = context.intent
            context.finish()
            context.startActivity(intent)
        } else {
            // Fallback for cases where context is not an activity
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun setWeight(weight: Float) {
        _uiState.update { it.copy(selectedWeight = weight) }
    }

    fun setWeightType(context: Context, type: WeightType) {
        _uiState.update { it.copy(weightType = type) }
        savePreference(context, "weight_type", type.name)
    }

    fun addCustomWeight(context: Context) {
        _uiState.update { current ->
            val newList = current.customWeights + CustomWeight("New Weight", 0.20f)
            saveCustomWeights(context, newList)
            current.copy(customWeights = newList)
        }
    }

    fun updateCustomWeight(context: Context, index: Int, name: String, weight: Float, caliber: Float? = null, unit: String? = null) {
        _uiState.update { current ->
            val newList = current.customWeights.toMutableList()
            if (index in newList.indices) {
                val old = newList[index]
                newList[index] = CustomWeight(
                    name = name,
                    weight = weight,
                    caliber = caliber ?: old.caliber,
                    caliberUnit = unit ?: old.caliberUnit
                )
                saveCustomWeights(context, newList)
            }
            current.copy(customWeights = newList)
        }
    }

    fun removeCustomWeight(context: Context, index: Int) {
        _uiState.update { current ->
            val newList = current.customWeights.toMutableList()
            if (index in newList.indices) {
                newList.removeAt(index)
                saveCustomWeights(context, newList)
            }
            current.copy(customWeights = newList)
        }
    }

    fun setMaxAllowedJoule(joule: Float) {
        _uiState.update { it.copy(maxAllowedJoule = joule) }
    }

    fun setMaxAllowedOverhop(cm: Float) {
        _uiState.update { it.copy(maxAllowedOverhopCm = cm) }
    }

    fun updateBallisticSettings(
        diameterMm: Float? = null,
        airDensityRho: Float? = null,
        dragCoefficientCw: Float? = null,
        magnusCoefficientK: Float? = null,
        spinDampingCr: Float? = null,
        gravity: Float? = null
    ) {
        _uiState.update { current ->
            current.copy(
                diameterMm = diameterMm ?: current.diameterMm,
                airDensityRho = airDensityRho ?: current.airDensityRho,
                dragCoefficientCw = dragCoefficientCw ?: current.dragCoefficientCw,
                magnusCoefficientK = magnusCoefficientK ?: current.magnusCoefficientK,
                spinDampingCr = spinDampingCr ?: current.spinDampingCr,
                gravity = gravity ?: current.gravity
            )
        }
    }

    fun connectToChronoWifi(context: Context) {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            
            networkCallback?.let { 
                try { connectivityManager.unregisterNetworkCallback(it) } catch (e: Exception) {}
            }

            val requestBuilder = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val specifier = WifiNetworkSpecifier.Builder()
                    .setSsid("HT-X3000")
                    .setWpa2Passphrase("88888888")
                    .build()
                requestBuilder.setNetworkSpecifier(specifier)
            }

            val request = requestBuilder.build()
            _uiState.update { it.copy(wifiStatus = "Connecting...") }

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    connectivityManager.bindProcessToNetwork(network)
                    _uiState.update { it.copy(wifiStatus = "Connected to Chrono", isConnected = true) }
                }

                override fun onLost(network: Network) {
                    connectivityManager.bindProcessToNetwork(null)
                    _uiState.update { it.copy(wifiStatus = "Disconnected", isConnected = false) }
                }

                override fun onUnavailable() {
                    _uiState.update { it.copy(wifiStatus = "Connection Failed", isConnected = false) }
                }
            }
            
            networkCallback = callback
            connectivityManager.requestNetwork(request, callback)
        } catch (e: Exception) {
            _uiState.update { it.copy(wifiStatus = "Error: ${e.message}") }
        }
    }

    private fun startPolling() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("Cache-Control", "no-cache") 
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

        val fireRate = "(?i)(?:firerate|射速)[^0-9]*(\\d+(?:\\.\\d+)?)".toRegex()
            .find(text)?.groupValues?.get(1) ?: "0.0"

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
                    for (i in rawShots.size downTo 0) {
                        val head = rawShots.take(i)
                        if (lastSeenRawShots.size >= i && lastSeenRawShots.takeLast(i) == head) {
                            newShotsToAdd = rawShots.drop(i)
                            break
                        } else if (head.isEmpty() && i == 0) {
                            newShotsToAdd = rawShots
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
            val currentVelocityString = latestShot?.velocity?.let { "%.2f".format(it) } ?: "0.00"
            val current_energy = latestShot?.energyJoules ?: 0f

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
                velocity = currentVelocityString,
                fireRate = fireRate,
                shots = currentSessionShots,
                averageVelocity = avg,
                maxVelocity = max,
                minVelocity = min,
                extremeSpread = es,
                standardDeviation = sd,
                variance = varVal,
                currentEnergy = current_energy,
                isConnected = true
            )
        }
    }

    fun exportToPdf(context: Context, shotCount: Int) {
        viewModelScope.launch {
            val data = _uiState.value
            val shotsToExport = data.shots.takeLast(shotCount)
            
            if (shotsToExport.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No shots to export", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            withContext(Dispatchers.IO) {
                try {
                    val pdfDocument = PdfDocument()
                    var pageNumber = 1
                    var pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                    var currentPage = pdfDocument.startPage(pageInfo)
                    var currentCanvas = currentPage.canvas
                    
                    val titlePaint = Paint().apply {
                        textSize = 24f
                        isFakeBoldText = true
                        color = Color.BLACK
                    }
                    val headerPaint = Paint().apply {
                        textSize = 14f
                        isFakeBoldText = true
                        color = Color.DKGRAY
                    }
                    val bodyPaint = Paint().apply {
                        textSize = 12f
                        color = Color.BLACK
                    }
                    val labelPaint = Paint().apply {
                        textSize = 10f
                        color = Color.GRAY
                    }
                    val accentPaint = Paint().apply {
                        color = Color.parseColor("#88FF11")
                    }

                    var y = 50f
                    currentCanvas.drawRect(40f, y, 60f, y + 30f, accentPaint)
                    currentCanvas.drawText("ChronoMate Report", 70f, y + 24f, titlePaint)
                    
                    y += 60f
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    currentCanvas.drawText("Date: ${dateFormat.format(Date())}", 40f, y, bodyPaint)
                    y += 20f
                    currentCanvas.drawText("Total Shots in Report: ${shotsToExport.size}", 40f, y, bodyPaint)

                    // Stats Section
                    y += 40f
                    currentCanvas.drawRect(40f, y, 555f, y + 2f, accentPaint)
                    y += 25f
                    currentCanvas.drawText("SESSION STATISTICS", 40f, y, headerPaint)
                    
                    y += 30f
                    val col1 = 40f
                    val col2 = 230f
                    val col3 = 420f
                    
                    val velocities = shotsToExport.map { it.velocity }
                    val avg = velocities.average().toFloat()
                    val max = velocities.maxOrNull() ?: 0f
                    val min = velocities.minOrNull() ?: 0f
                    val es = max - min
                    val mean = velocities.average()
                    val varVal = if (velocities.size > 1) velocities.sumOf { (it - mean).pow(2.0) }.toFloat() / velocities.size else 0f
                    val sd = sqrt(varVal)

                    currentCanvas.drawText("AVERAGE: %.1f m/s".format(avg), col1, y, bodyPaint)
                    currentCanvas.drawText("MAX: %.1f m/s".format(max), col2, y, bodyPaint)
                    currentCanvas.drawText("MIN: %.1f m/s".format(min), col3, y, bodyPaint)
                    
                    y += 25f
                    currentCanvas.drawText("EXTREME SPREAD: %.1f m/s".format(es), col1, y, bodyPaint)
                    currentCanvas.drawText("STANDARD DEVIATION: %.2f m/s".format(sd), col2, y, bodyPaint)
                    currentCanvas.drawText("ROF: ${data.fireRate} r/m", col3, y, bodyPaint)

                    // Graph Section
                    y += 50f
                    currentCanvas.drawText("VELOCITY TREND (m/s)", 40f, y, headerPaint)
                    y += 20f
                    
                    val graphHeight = 150f
                    val graphWidth = 475f // Adjusted for labels
                    val graphLeft = 80f // Shifted right for Y axis labels
                    val graphTop = y
                    
                    currentCanvas.drawRect(graphLeft, graphTop, graphLeft + graphWidth, graphTop + graphHeight, Paint().apply {
                        color = Color.LTGRAY
                        alpha = 30
                        style = Paint.Style.FILL
                    })

                    val minV = velocities.minOrNull() ?: 0f
                    val maxV = velocities.maxOrNull() ?: 0f
                    val rangeV = (maxV - minV).coerceAtLeast(1f)
                    val displayMin = (minV - rangeV * 0.2f).coerceAtLeast(0f)
                    val displayMax = maxV + rangeV * 0.2f
                    val displayRange = (displayMax - displayMin).coerceAtLeast(0.1f)

                    // Draw Chart Axis Labels
                    currentCanvas.drawText("%.1f".format(displayMax), graphLeft - 5f, graphTop + 10f, labelPaint.apply { textAlign = Paint.Align.RIGHT })
                    currentCanvas.drawText("%.1f".format(displayMin), graphLeft - 5f, graphTop + graphHeight, labelPaint)
                    currentCanvas.drawText("m/s", graphLeft - 5f, graphTop - 5f, labelPaint)
                    currentCanvas.drawText("SHOT #", graphLeft + graphWidth/2, graphTop + graphHeight + 25f, labelPaint.apply { textAlign = Paint.Align.CENTER })

                    // Draw Average Line (Orange)
                    val avgY = graphTop + graphHeight - ((avg - displayMin) / displayRange) * graphHeight
                    val avgLinePaint = Paint().apply {
                        color = Color.parseColor("#E55A16")
                        strokeWidth = 1f
                        pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
                        style = Paint.Style.STROKE
                    }
                    currentCanvas.drawLine(graphLeft, avgY, graphLeft + graphWidth, avgY, avgLinePaint)
                    
                    val path = Path()
                    val graphPaint = Paint().apply {
                        color = Color.parseColor("#4CAF50")
                        strokeWidth = 2f
                        style = Paint.Style.STROKE
                    }

                    shotsToExport.forEachIndexed { index, shot ->
                        val x = graphLeft + (index.toFloat() / (shotsToExport.size - 1).coerceAtLeast(1)) * graphWidth
                        val py = graphTop + graphHeight - ((shot.velocity - displayMin) / displayRange) * graphHeight
                        
                        if (index == 0) path.moveTo(x, py) else path.lineTo(x, py)
                        currentCanvas.drawCircle(x, py, 3f, Paint().apply { color = Color.parseColor("#2E7D32") })
                    }
                    currentCanvas.drawPath(path, graphPaint)
                    
                    y += graphHeight + 45f
                    
                    // Table Header
                    currentCanvas.drawRect(40f, y, 555f, y + 20f, Paint().apply { color = Color.LTGRAY; alpha = 50 })
                    currentCanvas.drawText("#", 50f, y + 15f, headerPaint)
                    currentCanvas.drawText("Weight (g)", 100f, y + 15f, headerPaint)
                    currentCanvas.drawText("Velocity (m/s)", 250f, y + 15f, headerPaint)
                    currentCanvas.drawText("Energy (J)", 450f, y + 15f, headerPaint)
                    
                    y += 35f
                    // Now showing shots in chronological order (oldest first)
                    for (index in shotsToExport.indices) {
                        // Pagination check
                        if (y > 780f) {
                            pdfDocument.finishPage(currentPage)
                            pageNumber++
                            pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                            currentPage = pdfDocument.startPage(pageInfo)
                            currentCanvas = currentPage.canvas
                            y = 50f
                            
                            // Redraw table header on new page
                            currentCanvas.drawRect(40f, y, 555f, y + 20f, Paint().apply { color = Color.LTGRAY; alpha = 50 })
                            currentCanvas.drawText("#", 50f, y + 15f, headerPaint)
                            currentCanvas.drawText("Weight (g)", 100f, y + 15f, headerPaint)
                            currentCanvas.drawText("Velocity (m/s)", 250f, y + 15f, headerPaint)
                            currentCanvas.drawText("Energy (J)", 450f, y + 15f, headerPaint)
                            y += 35f
                        }

                        val shot = shotsToExport[index]
                        // shotNum relative to the session
                        val shotNum = (data.shots.size - shotsToExport.size) + index + 1
                        currentCanvas.drawText("%02d".format(shotNum), 50f, y, bodyPaint)
                        currentCanvas.drawText("%.2f".format(shot.weightGrams), 100f, y, bodyPaint)
                        currentCanvas.drawText("%.1f".format(shot.velocity), 250f, y, bodyPaint)
                        currentCanvas.drawText("%.2f".format(shot.energyJoules), 450f, y, bodyPaint)
                        y += 20f
                    }

                    pdfDocument.finishPage(currentPage)

                    // Modern way to save to Downloads using MediaStore
                    val fileName = "ChronoMate_Report_${System.currentTimeMillis()}.pdf"
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ChronoMate")
                        }
                    }

                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { outputStream ->
                            pdfDocument.writeTo(outputStream)
                        }
                        pdfDocument.close()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "PDF saved to Downloads/ChronoMate", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        throw Exception("Could not create PDF file in Downloads")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
