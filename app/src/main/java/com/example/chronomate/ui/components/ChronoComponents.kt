package com.example.chronomate.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chronomate.R
import com.example.chronomate.model.ChronoData
import com.example.chronomate.model.Shot

@Composable
fun LogoImage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resourceId = remember(context) { 
        context.resources.getIdentifier("logo", "drawable", context.packageName) 
    }
    
    if (resourceId != 0) {
        Image(
            painter = painterResource(id = resourceId),
            contentDescription = "Logo",
            modifier = modifier,
            contentScale = ContentScale.FillBounds
        )
    } else {
        Box(modifier = modifier.background(Color.DarkGray), contentAlignment = Alignment.Center) {
            Text("CM", fontSize = 24.sp, color = Color(0xFF88FF11), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StatusBadge(isConnected: Boolean, wifiStatus: String, onClick: () -> Unit = {}) {
    val isLight = MaterialTheme.colorScheme.surface == Color.White || MaterialTheme.colorScheme.surface.red > 0.9f
    
    val green = if (isLight) Color(0xFF1B5E20) else Color(0xFF88FF11)
    val red = if (isLight) Color(0xFFB71C1C) else Color.Red
    val yellow = if (isLight) Color(0xFFFBC02D) else Color.Yellow
    
    val isConnecting = wifiStatus == "Connecting..."
    
    val badgeColor = when {
        isConnected -> green
        isConnecting -> yellow
        else -> red
    }
    
    val statusText = when {
        isConnected -> "LIVE"
        isConnecting -> "CONNECTING..."
        else -> "OFFLINE - TAP TO RECONNECT"
    }
    
    Surface(
        color = badgeColor.copy(alpha = 0.15f),
        shape = CircleShape,
        modifier = Modifier.clickable(enabled = !isConnected && !isConnecting) { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = badgeColor,
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = badgeColor,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label, 
            style = MaterialTheme.typography.labelSmall, 
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 12.sp
        )
        Text(
            text = value, 
            style = MaterialTheme.typography.titleMedium, 
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ShotChart(shots: List<Shot>, modifier: Modifier = Modifier) {
    if (shots.isEmpty()) return
    
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val velocities = shots.map { it.velocity }
    val avgVel = velocities.average().toFloat()
    val minVel = velocities.minOrNull() ?: 0f
    val maxVel = velocities.maxOrNull() ?: 0f
    val range = (maxVel - minVel).coerceAtLeast(1f)
    
    val padding = range * 0.3f
    val displayMin = (minVel - padding).coerceAtLeast(0f)
    val displayMax = maxVel + padding
    val displayRange = (displayMax - displayMin).coerceAtLeast(0.1f)
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val isLight = MaterialTheme.colorScheme.surface == Color.White || MaterialTheme.colorScheme.surface.red > 0.9f
    val trendColor = if (isLight) Color(0xFF2E7D32) else Color(0xFF88FF11)

    Box(modifier = modifier.padding(start = 40.dp, bottom = 20.dp, top = 10.dp, end = 10.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(shots) {
                    detectTapGestures { offset ->
                        val xStep = if (shots.size > 1) size.width.toFloat() / (shots.size - 1) else 0f
                        val index = if (xStep > 0f) (offset.x / xStep).toInt().coerceIn(0, shots.size - 1) else 0
                        selectedIndex = index
                    }
                }
        ) {
            val xStep = if (shots.size > 1) size.width.toFloat() / (shots.size - 1) else 0f
            
            // Draw Axis Labels
            drawContext.canvas.nativeCanvas.drawText(
                "m/s",
                -35.dp.toPx(),
                -5.dp.toPx(),
                android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 10.sp.toPx()
                }
            )
            
            drawContext.canvas.nativeCanvas.drawText(
                "%.1f".format(displayMax),
                -35.dp.toPx(),
                10.dp.toPx(),
                android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 10.sp.toPx()
                }
            )
            
            drawContext.canvas.nativeCanvas.drawText(
                "%.1f".format(displayMin),
                -35.dp.toPx(),
                size.height,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 10.sp.toPx()
                }
            )

            drawContext.canvas.nativeCanvas.drawText(
                "SHOT #",
                size.width / 2,
                size.height + 18.dp.toPx(),
                android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 10.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )

            // Draw Average Line (Yellow)
            val normalizedAvgY = (avgVel - displayMin) / displayRange
            val avgY = size.height - (normalizedAvgY * size.height)
            drawLine(
                color = Color.Yellow.copy(alpha = 0.6f),
                start = Offset(0f, avgY),
                end = Offset(size.width, avgY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )

            val points = shots.mapIndexed { index, shot ->
                val x = index * xStep
                val normalizedY = (shot.velocity - displayMin) / displayRange
                val y = size.height - (normalizedY * size.height)
                Offset(x, y)
            }

            if (points.size > 1) {
                val path = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                }
                drawPath(
                    path = path,
                    color = trendColor,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            points.forEachIndexed { index, point ->
                drawCircle(
                    color = if (selectedIndex == index) primaryColor else trendColor,
                    radius = if (selectedIndex == index) 5.dp.toPx() else 3.dp.toPx(),
                    center = point
                )
            }

            selectedIndex?.let { idx ->
                val point = points.getOrNull(idx) ?: return@let
                drawLine(
                    color = onSurfaceVariant.copy(alpha = 0.3f),
                    start = Offset(point.x, 0f),
                    end = Offset(point.x, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        selectedIndex?.let { idx ->
            val shot = shots.getOrNull(idx) ?: return@let
            Surface(
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(4.dp),
                tonalElevation = 8.dp,
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "Shot #${idx + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "%.1f m/s".format(shot.velocity),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "%.2f J (%.2fg)".format(shot.energyJoules, shot.weightGrams),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun ShotRow(shot: Shot, index: Int) {
    val isLight = MaterialTheme.colorScheme.surface == Color.White || MaterialTheme.colorScheme.surface.red > 0.9f
    val trendColor = if (isLight) Color(0xFF2E7D32) else Color(0xFF88FF11)

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "#%02d".format(index),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "%.2fg".format(shot.weightGrams),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(trendColor, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = "%.2f J".format(shot.energyJoules), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "%.1f m/s".format(shot.velocity),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun HeroSection(data: ChronoData) {
    val isLight = MaterialTheme.colorScheme.surface == Color.White || MaterialTheme.colorScheme.surface.red > 0.9f
    val chronoGreen = Color(0xFF88FF11)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLight) MaterialTheme.colorScheme.primaryContainer else Color.Black
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val contentColor = if (isLight) MaterialTheme.colorScheme.onPrimaryContainer else chronoGreen
            
            Text(
                text = stringResource(R.string.latest_shot),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.6f),
                letterSpacing = 1.sp
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = data.velocity,
                    fontSize = 52.sp,
                    color = contentColor,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = " m/s",
                    fontSize = 16.sp,
                    color = contentColor,
                    modifier = Modifier.padding(bottom = 12.dp),
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "%.2f J".format(data.currentEnergy),
                fontSize = 24.sp,
                color = contentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StatsGrid(data: ChronoData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(stringResource(R.string.stat_average), "%.1f".format(data.averageVelocity), Modifier.weight(1f))
                StatItem(stringResource(R.string.stat_max), "%.1f".format(data.maxVelocity), Modifier.weight(1f))
                StatItem(stringResource(R.string.stat_min), "%.1f".format(data.minVelocity), Modifier.weight(1f))
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(stringResource(R.string.stat_es), "%.1f".format(data.extremeSpread), Modifier.weight(1f))
                StatItem(stringResource(R.string.stat_sd), "%.2f".format(data.standardDeviation), Modifier.weight(1f))
                StatItem(stringResource(R.string.stat_rof), "${data.fireRate} r/m", Modifier.weight(1f))
            }
        }
    }
}
