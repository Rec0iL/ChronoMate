package com.example.chronomate.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chronomate.ballistics.BallisticsEngine
import com.example.chronomate.model.BallisticParams
import com.example.chronomate.model.ChronoData
import com.example.chronomate.model.TrajectoryPoint
import com.example.chronomate.ui.components.*
import com.example.chronomate.viewmodel.ChronoViewModel
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

@Composable
fun DashboardScreen(data: ChronoData, viewModel: ChronoViewModel) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "BB WEIGHT (g)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        val weights = listOf(0.20f, 0.23f, 0.25f, 0.28f, 0.30f, 0.32f, 0.36f, 0.40f, 0.43f, 0.45f)
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(weights) { weight ->
                FilterChip(
                    selected = data.selectedWeight == weight,
                    onClick = { viewModel.setWeight(weight) },
                    label = { Text("%.2f".format(weight)) }
                )
            }
        }

        if (isLandscape) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    HeroSection(data = data)
                    Spacer(modifier = Modifier.height(8.dp))
                    StatsGrid(data = data)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1.2f)) {
                    Text(
                        text = "VELOCITY TREND",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    ShotChart(shots = data.shots, modifier = Modifier.height(250.dp).fillMaxWidth())
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                HeroSection(data = data)
                Spacer(modifier = Modifier.height(12.dp))
                StatsGrid(data = data)
                Spacer(modifier = Modifier.height(16.dp))
                if (data.shots.isNotEmpty()) {
                    Text(
                        text = "VELOCITY TREND",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    ShotChart(shots = data.shots, modifier = Modifier.height(200.dp).fillMaxWidth())
                } else {
                    Box(modifier = Modifier.height(100.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No shots recorded yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun OrgaChronoScreen(data: ChronoData, viewModel: ChronoViewModel) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val weights = listOf(0.20f, 0.23f, 0.25f, 0.28f, 0.30f, 0.32f, 0.36f, 0.40f, 0.43f, 0.45f)
    
    val velocity = data.velocity.toFloatOrNull() ?: 0f
    
    // Calculate max allowed weight for the last shot velocity
    // E = 0.5 * m * v^2  => m = (2 * E) / v^2
    val maxWeightKg = if (velocity > 0) (2 * data.maxAllowedJoule) / velocity.pow(2) else 0f
    val maxWeightGrams = maxWeightKg * 1000f

    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    OrgaHeroSection(data = data, maxWeightGrams = maxWeightGrams)
                    Spacer(modifier = Modifier.height(16.dp))
                    OrgaSettingsSection(data = data, viewModel = viewModel)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(2f)) {
                    Text("Joule Reference Grid", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    JouleGrid(velocity = velocity, weights = weights, columns = 4)
                }
            }
        } else {
            OrgaHeroSection(data = data, maxWeightGrams = maxWeightGrams)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Joule Reference Grid", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            JouleGrid(velocity = velocity, weights = weights, modifier = Modifier.heightIn(max = 400.dp))
            Spacer(modifier = Modifier.height(24.dp))
            OrgaSettingsSection(data = data, viewModel = viewModel)
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun OrgaHeroSection(data: ChronoData, maxWeightGrams: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "LATEST SHOT",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF88FF11).copy(alpha = 0.6f)
            )
            Text(
                text = data.velocity + " m/s",
                fontSize = 48.sp,
                color = Color(0xFF88FF11),
                fontWeight = FontWeight.Black
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.1f))
            Text(
                text = "MAX BB WEIGHT FOR LIMIT",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                text = "%.2f g".format(maxWeightGrams),
                fontSize = 32.sp,
                color = if (maxWeightGrams >= 0.20f) Color(0xFF88FF11) else Color.Red,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun OrgaSettingsSection(data: ChronoData, viewModel: ChronoViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Orga Limits", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = data.maxAllowedJoule.toString(),
                onValueChange = { 
                    val newValue = it.toFloatOrNull()
                    if (newValue != null) viewModel.setMaxAllowedJoule(newValue)
                },
                label = { Text("Max Allowed Joule (J)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        }
    }
}

@Composable
fun JouleGrid(velocity: Float, weights: List<Float>, modifier: Modifier = Modifier, columns: Int = 2) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(weights) { weight ->
            val energy = 0.5f * (weight / 1000f) * velocity.pow(2)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.2f g".format(weight), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("%.2f J".format(energy), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun BallisticsScreen(data: ChronoData) {
    var weight by remember { mutableStateOf(data.selectedWeight) }
    var speedMps by remember { mutableStateOf(data.velocity.toDoubleOrNull() ?: 100.0) }
    var hopUp by remember { mutableStateOf(1000.0) } // rad/s
    var targetDistance by remember { mutableStateOf(50.0) } // meters
    var sightHeight by remember { mutableStateOf(5.0) } // cm above bore
    
    val engine = remember { BallisticsEngine() }
    val trajectory = remember(weight, speedMps, hopUp) {
        engine.calculateTrajectory(BallisticParams(
            massGrams = weight.toDouble(),
            muzzleVelocityMps = speedMps,
            hopUpRadS = hopUp,
            startingHeightM = 1.5
        ))
    }

    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        Text("Trajectory Simulation", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 16.dp))
        
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                Card(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = Color.Black)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text("SIDE VIEW", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        TrajectoryCanvas(trajectory = trajectory, targetDistance = targetDistance)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Card(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = Color.Black)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text("TARGET VIEW (@ %.0f m)".format(targetDistance), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        TargetViewCanvas(trajectory = trajectory, targetDistance = targetDistance, sightHeightCm = sightHeight)
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Column(Modifier.padding(8.dp)) {
                    Text("SIDE VIEW", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    TrajectoryCanvas(trajectory = trajectory, targetDistance = targetDistance)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth().height(250.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Column(Modifier.padding(8.dp)) {
                    Text("TARGET VIEW (@ %.0f m)".format(targetDistance), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    TargetViewCanvas(trajectory = trajectory, targetDistance = targetDistance, sightHeightCm = sightHeight)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Controls
        Text("Target Distance: %.0f m".format(targetDistance), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Slider(value = targetDistance.toFloat(), onValueChange = { targetDistance = it.toDouble() }, valueRange = 5f..100f)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Weight: %.2f g".format(weight), style = MaterialTheme.typography.labelMedium)
                Slider(value = weight, onValueChange = { weight = it }, valueRange = 0.20f..0.45f)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Speed: %.1f m/s".format(speedMps), style = MaterialTheme.typography.labelMedium)
                Slider(value = speedMps.toFloat(), onValueChange = { speedMps = it.toDouble() }, valueRange = 50f..200f)
            }
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Hop-Up: %.0f rad/s".format(hopUp), style = MaterialTheme.typography.labelMedium)
                Slider(value = hopUp.toFloat(), onValueChange = { hopUp = it.toDouble() }, valueRange = 0f..2000f)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Sight Height: %.1f cm".format(sightHeight), style = MaterialTheme.typography.labelMedium)
                Slider(value = sightHeight.toFloat(), onValueChange = { sightHeight = it.toDouble() }, valueRange = 0f..10f)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (trajectory.isNotEmpty()) {
            val maxDist = trajectory.last().x
            Text("Max Theoretical Range: %.1f m".format(maxDist), fontWeight = FontWeight.Bold, color = Color(0xFF88FF11))
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun TrajectoryCanvas(trajectory: List<TrajectoryPoint>, targetDistance: Double) {
    if (trajectory.isEmpty()) return
    
    val chronoGreen = Color(0xFF88FF11)
    
    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 16.dp)) {
        val maxX = trajectory.maxOf { it.x }.toFloat().coerceAtLeast(1f)
        val maxY = trajectory.maxOf { it.y }.toFloat().coerceAtLeast(2f)
        val minY = 0f
        
        val rangeY = maxY - minY
        
        val points = trajectory.map { pt ->
            val px = (pt.x.toFloat() / maxX) * size.width
            val py = size.height - (pt.y.toFloat() / rangeY) * size.height
            Offset(px, py)
        }
        
        val path = Path().apply {
            if (points.isNotEmpty()) {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }
        }
        
        drawPath(path = path, color = chronoGreen, style = Stroke(width = 2.dp.toPx()))
        
        // Target Distance marker
        val tx = (targetDistance.toFloat() / maxX) * size.width
        drawLine(
            color = Color.Red.copy(alpha = 0.5f),
            start = Offset(tx, 0f),
            end = Offset(tx, size.height),
            strokeWidth = 1.dp.toPx()
        )
        
        // Ground line
        drawLine(
            color = Color.White.copy(alpha = 0.2f),
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx()
        )
    }
}

@Composable
fun TargetViewCanvas(trajectory: List<TrajectoryPoint>, targetDistance: Double, sightHeightCm: Double) {
    val chronoGreen = Color(0xFF88FF11)
    
    // Find height at target distance
    val ptAtDist = trajectory.minByOrNull { abs(it.x - targetDistance) }
    val bbHeightM = ptAtDist?.y ?: 0.0
    
    // Shooter aims horizontally at 1.5m height.
    val launchHeightM = 1.5
    val sightHeightM = sightHeightCm / 100.0
    
    val relativeImpactM = bbHeightM - (launchHeightM + sightHeightM)
    val relativeImpactCm = relativeImpactM * 100.0

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Reticle Circle
        Surface(
            modifier = Modifier.size(200.dp).clip(CircleShape),
            color = Color.Transparent,
            border = BorderStroke(2.dp, Color.White.copy(alpha = 0.3f))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                
                // Crosshairs
                drawLine(Color.White, Offset(0f, center.y), Offset(size.width, center.y), 1.dp.toPx())
                drawLine(Color.White, Offset(center.x, 0f), Offset(center.x, size.height), 1.dp.toPx())
                
                // Impact point
                val scale = 5f 
                val impactY = center.y - (relativeImpactCm.toFloat() * scale)
                
                if (impactY in 0f..size.height) {
                    drawCircle(
                        color = chronoGreen,
                        radius = 6.dp.toPx(),
                        center = Offset(center.x, impactY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = Offset(center.x, impactY)
                    )
                }
            }
        }
        
        // Label
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (relativeImpactCm >= 0) "HIT: %.1f cm ABOVE".format(relativeImpactCm) 
                       else "HIT: %.1f cm BELOW".format(abs(relativeImpactCm)),
                color = if (relativeImpactCm >= 0) chronoGreen else Color.Red,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun HistoryScreen(data: ChronoData) {
    if (data.shots.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No history available", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(data.shots.asReversed().mapIndexed { index, shot -> index to shot }) { (reversedIndex, shot) ->
                val index = data.shots.size - reversedIndex
                ShotRow(shot = shot, index = index)
            }
        }
    }
}

@Composable
fun SettingsScreen(data: ChronoData, viewModel: ChronoViewModel) {
    val weights = listOf(0.20f, 0.23f, 0.25f, 0.28f, 0.30f, 0.32f, 0.36f, 0.40f, 0.43f, 0.45f)
    val scrollState = rememberScrollState()
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
        Text("General Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Dark Mode", style = MaterialTheme.typography.titleMedium)
                Text("Toggle between light and dark theme", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = data.isDarkMode,
                onCheckedChange = { viewModel.toggleDarkMode(it) }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Default BB Weight", style = MaterialTheme.typography.titleMedium)
        Text("Choose the weight that will be selected on app launch.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))
        
        weights.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { weight ->
                    OutlinedButton(
                        onClick = { viewModel.saveDefaultWeight(weight) },
                        modifier = Modifier.weight(1f),
                        colors = if (data.selectedWeight == weight) 
                            ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("%.2f".format(weight))
                    }
                }
                if (row.size < 3) Spacer(modifier = Modifier.weight((3 - row.size).toFloat()))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Text("ChronoMate v1.0", modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.height(16.dp))
    }
}
