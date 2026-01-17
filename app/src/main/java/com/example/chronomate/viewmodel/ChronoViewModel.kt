package com.example.chronomate.viewmodel

import android.content.ContentValues
import android.content.Context
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
import android.widget.Toast
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
    private val prefs = context.getSharedPreferences("chrono_prefs", Context.MODE_PRIVATE)
    
    private val _uiState = MutableStateFlow(ChronoData(
        selectedWeight = prefs.getFloat("default_weight", 0.20f),
        isDarkMode = prefs.getBoolean("is_dark_mode", true),
        maxAllowedJoule = prefs.getFloat("max_allowed_joule", 1.5f),
        maxAllowedOverhopCm = prefs.getFloat("max_allowed_overhop", 15.0f),
        diameterMm = prefs.getFloat("diameterMm", 5.95f),
        airDensityRho = prefs.getFloat("airDensityRho", 1.225f),
        dragCoefficientCw = prefs.getFloat("dragCoefficientCw", 0.35f),
        magnusCoefficientK = prefs.getFloat("magnusCoefficientK", 0.002f),
        spinDampingCr = prefs.getFloat("spinDampingCr", 0.01f),
        gravity = prefs.getFloat("gravity", 9.81f)
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

    fun updateBallisticSettings(
        diameterMm: Float? = null,
        airDensityRho: Float? = null,
        dragCoefficientCw: Float? = null,
        magnusCoefficientK: Float? = null,
        spinDampingCr: Float? = null,
        gravity: Float? = null
    ) {
        val editor = prefs.edit()
        _uiState.update { current ->
            val newState = current.copy(
                diameterMm = diameterMm ?: current.diameterMm,
                airDensityRho = airDensityRho ?: current.airDensityRho,
                dragCoefficientCw = dragCoefficientCw ?: current.dragCoefficientCw,
                magnusCoefficientK = magnusCoefficientK ?: current.magnusCoefficientK,
                spinDampingCr = spinDampingCr ?: current.spinDampingCr,
                gravity = gravity ?: current.gravity
            )
            
            diameterMm?.let { editor.putFloat("diameterMm", it) }
            airDensityRho?.let { editor.putFloat("airDensityRho", it) }
            dragCoefficientCw?.let { editor.putFloat("dragCoefficientCw", it) }
            magnusCoefficientK?.let { editor.putFloat("magnusCoefficientK", it) }
            spinDampingCr?.let { editor.putFloat("spinDampingCr", it) }
            gravity?.let { editor.putFloat("gravity", it) }
            editor.apply()
            
            newState
        }
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
                velocity = currentVelocityString,
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
                    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas
                    val paint = Paint()
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
                    canvas.drawRect(40f, y, 60f, y + 30f, accentPaint)
                    canvas.drawText("ChronoMate Report", 70f, y + 24f, titlePaint)
                    
                    y += 60f
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    canvas.drawText("Date: ${dateFormat.format(Date())}", 40f, y, bodyPaint)
                    y += 20f
                    canvas.drawText("Total Shots in Report: ${shotsToExport.size}", 40f, y, bodyPaint)

                    // Stats Section
                    y += 40f
                    canvas.drawRect(40f, y, 555f, y + 2f, accentPaint)
                    y += 25f
                    canvas.drawText("SESSION STATISTICS", 40f, y, headerPaint)
                    
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

                    canvas.drawText("AVERAGE: %.1f m/s".format(avg), col1, y, bodyPaint)
                    canvas.drawText("MAX: %.1f m/s".format(max), col2, y, bodyPaint)
                    canvas.drawText("MIN: %.1f m/s".format(min), col3, y, bodyPaint)
                    
                    y += 25f
                    canvas.drawText("EXTREME SPREAD: %.1f m/s".format(es), col1, y, bodyPaint)
                    canvas.drawText("STANDARD DEVIATION: %.2f m/s".format(sd), col2, y, bodyPaint)
                    canvas.drawText("ROF: ${data.fireRate} r/m", col3, y, bodyPaint)

                    // Graph Section
                    y += 50f
                    canvas.drawText("VELOCITY TREND (m/s)", 40f, y, headerPaint)
                    y += 20f
                    
                    val graphHeight = 150f
                    val graphWidth = 475f // Adjusted for labels
                    val graphLeft = 80f // Shifted right for Y axis labels
                    val graphTop = y
                    
                    canvas.drawRect(graphLeft, graphTop, graphLeft + graphWidth, graphTop + graphHeight, Paint().apply {
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
                    canvas.drawText("%.1f".format(displayMax), graphLeft - 5f, graphTop + 10f, labelPaint.apply { textAlign = Paint.Align.RIGHT })
                    canvas.drawText("%.1f".format(displayMin), graphLeft - 5f, graphTop + graphHeight, labelPaint)
                    canvas.drawText("m/s", graphLeft - 5f, graphTop - 5f, labelPaint)
                    canvas.drawText("SHOT #", graphLeft + graphWidth/2, graphTop + graphHeight + 25f, labelPaint.apply { textAlign = Paint.Align.CENTER })

                    // Draw Average Line (Orange)
                    val avgY = graphTop + graphHeight - ((avg - displayMin) / displayRange) * graphHeight
                    val avgLinePaint = Paint().apply {
                        color = Color.parseColor("#E55A16")
                        strokeWidth = 1f
                        pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
                        style = Paint.Style.STROKE
                    }
                    canvas.drawLine(graphLeft, avgY, graphLeft + graphWidth, avgY, avgLinePaint)
                    
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
                        canvas.drawCircle(x, py, 3f, Paint().apply { color = Color.parseColor("#2E7D32") })
                    }
                    canvas.drawPath(path, graphPaint)
                    
                    y += graphHeight + 45f
                    
                    // Table Header
                    canvas.drawRect(40f, y, 555f, y + 20f, Paint().apply { color = Color.LTGRAY; alpha = 50 })
                    canvas.drawText("#", 50f, y + 15f, headerPaint)
                    canvas.drawText("Weight (g)", 100f, y + 15f, headerPaint)
                    canvas.drawText("Velocity (m/s)", 250f, y + 15f, headerPaint)
                    canvas.drawText("Energy (J)", 450f, y + 15f, headerPaint)
                    
                    y += 35f
                    var pageFinished = false
                    val reversedShots = shotsToExport.asReversed()
                    for (index in reversedShots.indices) {
                        val shot = reversedShots[index]
                        val shotNum = shotsToExport.size - index
                        canvas.drawText("%02d".format(shotNum), 50f, y, bodyPaint)
                        canvas.drawText("%.2f".format(shot.weightGrams), 100f, y, bodyPaint)
                        canvas.drawText("%.1f".format(shot.velocity), 250f, y, bodyPaint)
                        canvas.drawText("%.2f".format(shot.energyJoules), 450f, y, bodyPaint)
                        y += 20f
                        
                        if (y > 780f && index < shotsToExport.size - 1) {
                            pdfDocument.finishPage(page)
                            pageFinished = true
                            break
                        }
                    }

                    if (!pageFinished) {
                        pdfDocument.finishPage(page)
                    }

                    val fileName = "ChronoMate_Report_${System.currentTimeMillis()}.pdf"
                    
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ChronoMate")
                    }

                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { outputStream ->
                            pdfDocument.writeTo(outputStream)
                        }
                        pdfDocument.close()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Report saved to Downloads/ChronoMate", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        // Fallback to app-specific if MediaStore fails
                        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
                        pdfDocument.writeTo(FileOutputStream(file))
                        pdfDocument.close()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Saved to app folder (Downloads failed)", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        networkCallback = null
    }
}
