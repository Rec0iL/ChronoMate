package com.example.chronomate.ui.screens

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
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
                Column(modifier = Modifier.weight(2.5f)) { 
                    Text("Joule Reference Grid", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    JouleGridStatic(velocity = velocity, weights = weights, columns = 4)
                }
            }
        } else {
            OrgaHeroSection(data = data, maxWeightGrams = maxWeightGrams)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Joule Reference Grid", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            JouleGridStatic(velocity = velocity, weights = weights, columns = 2)
            Spacer(modifier = Modifier.height(24.dp))
            OrgaSettingsSection(data = data, viewModel = viewModel)
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun JouleGridStatic(velocity: Float, weights: List<Float>, columns: Int) {
    val rows = weights.chunked(columns)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { rowWeights ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowWeights.forEach { weight ->
                    val energy = 0.5f * (weight / 1000f) * velocity.pow(2)
                    Card(
                        modifier = Modifier.weight(1f),
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
                if (rowWeights.size < columns) {
                    Spacer(modifier = Modifier.weight((columns - rowWeights.size).toFloat()))
                }
            }
        }
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
    var textValue by remember(data.maxAllowedJoule) { mutableStateOf(data.maxAllowedJoule.toString()) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Orga Limits", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = textValue,
                onValueChange = { 
                    textValue = it
                    it.toFloatOrNull()?.let { joule ->
                        viewModel.setMaxAllowedJoule(joule)
                    }
                },
                label = { Text("Max Allowed Joule (J)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        }
    }
}

data class ProbeResult(
    val distance: Float,
    val bbHeightCm: Float,
    val energyJ: Float,
    val timeS: Float,
    val relativeImpactCm: Float,
    val holdOverCm: Float
)

@Composable
fun BallisticsScreen(data: ChronoData, viewModel: ChronoViewModel) {
    var speedMps by remember { mutableStateOf(data.velocity.toDoubleOrNull() ?: 100.0) }
    
    val currentJoule = 0.5 * (data.selectedWeight.toDouble() / 1000.0) * speedMps.pow(2.0)
    
    var hopUp by remember { mutableStateOf(1000.0) }
    var targetDistance by remember { mutableStateOf(50.0) }
    var shooterHeight by remember { mutableStateOf(1.5) }
    var targetHeight by remember { mutableStateOf(1.5) }
    var coupleHeights by remember { mutableStateOf(true) }
    var sightHeight by remember { mutableStateOf(5.0) }
    
    var probeResult by remember { mutableStateOf<ProbeResult?>(null) }
    
    // Auto-dismiss probe after 10 seconds
    LaunchedEffect(probeResult) {
        if (probeResult != null) {
            delay(10000)
            probeResult = null
        }
    }
    
    val eyeHeightM = shooterHeight + (sightHeight / 100.0)
    val aimAngleDeg = Math.toDegrees(atan2(targetHeight - eyeHeightM, targetDistance))

    val engine = remember { BallisticsEngine() }
    val trajectory = remember(data.selectedWeight, speedMps, hopUp, shooterHeight, aimAngleDeg) {
        engine.calculateTrajectory(BallisticParams(
            massGrams = data.selectedWeight.toDouble(),
            muzzleVelocityMps = speedMps,
            hopUpRadS = hopUp,
            startingHeightM = shooterHeight,
            launchAngleDeg = aimAngleDeg
        ))
    }

    // Advanced Analysis
    val effectiveRange = remember(trajectory, eyeHeightM, targetHeight, targetDistance) {
        // Find x where BB crosses the Line of Sight for the SECOND time
        val firstCrossingIdx = trajectory.indexOfFirst { pt ->
            val aimY = eyeHeightM + (pt.x / targetDistance) * (targetHeight - eyeHeightM)
            pt.y > aimY
        }
        if (firstCrossingIdx == -1) {
            val secondCrossing = trajectory.drop(1).firstOrNull { pt ->
                val aimY = eyeHeightM + (pt.x / targetDistance) * (targetHeight - eyeHeightM)
                pt.y <= aimY
            }
            return@remember secondCrossing?.x ?: 0.0
        }
        
        val secondCrossing = trajectory.drop(firstCrossingIdx).firstOrNull { pt ->
            val aimY = eyeHeightM + (pt.x / targetDistance) * (targetHeight - eyeHeightM)
            pt.y <= aimY
        }
        secondCrossing?.x ?: trajectory.last().x
    }
    
    val maxRange = trajectory.last().x
    
    val ptAtTarget = trajectory.minByOrNull { abs(it.x - targetDistance) }
    val energyAtTarget = ptAtTarget?.energyJoules ?: 0.0
    val timeToTarget = ptAtTarget?.time ?: 0.0
    val currentHoldOverCm = if (ptAtTarget != null) (targetHeight - ptAtTarget.y) * 100.0 else 0.0

    val overhopResult = remember(trajectory, eyeHeightM, targetHeight, targetDistance) {
        var maxOH = 0.0
        var maxOHDist = 0.0
        trajectory.forEach { pt ->
            if (pt.x <= targetDistance) {
                val aimLineY = eyeHeightM + (pt.x / targetDistance) * (targetHeight - eyeHeightM)
                val diff = pt.y - aimLineY
                if (diff > maxOH) {
                    maxOH = diff
                    maxOHDist = pt.x
                }
            }
        }
        maxOH to maxOHDist
    }
    val overhop = overhopResult.first
    val overhopDist = overhopResult.second

    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val rainbowBrush = remember {
        Brush.linearGradient(
            colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
        )
    }

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
                        Text("SIDE VIEW (m/cm) - TAP TO PROBE", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        TrajectoryCanvas(
                            trajectory = trajectory, 
                            targetDistance = targetDistance,
                            eyeHeightM = eyeHeightM,
                            targetHeightM = targetHeight,
                            onProbe = { probeResult = it }
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Card(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = Color.Black)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text("TARGET VIEW (@ %.0f m)".format(targetDistance), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        TargetViewCanvas(trajectory = trajectory, targetDistance = targetDistance, targetHeightM = targetHeight)
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Column(Modifier.padding(8.dp)) {
                    Text("SIDE VIEW (m/cm) - TAP TO PROBE", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    TrajectoryCanvas(
                        trajectory = trajectory, 
                        targetDistance = targetDistance,
                        eyeHeightM = eyeHeightM,
                        targetHeightM = targetHeight,
                        onProbe = { probeResult = it }
                    )
                }
            }
            
            // Probe result popup positioned right below the side view
            AnimatedVisibility(
                visible = probeResult != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                probeResult?.let { res ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Dist: %.1f m".format(res.distance), fontWeight = FontWeight.Bold)
                                Text("Time: %.2f s".format(res.timeS))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Height: %.1f cm".format(res.bbHeightCm), style = MaterialTheme.typography.bodySmall)
                                Text("Energy: %.2f J".format(res.energyJ), style = MaterialTheme.typography.bodySmall)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Rel. Impact: %.1f cm".format(res.relativeImpactCm), color = if (res.relativeImpactCm < 0) Color.Red else Color.Unspecified)
                                Text("Hold-over: %.1f cm".format(res.holdOverCm), fontWeight = FontWeight.Bold, color = if (res.holdOverCm > 0) Color.Red else Color.Cyan)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth().height(250.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Column(Modifier.padding(8.dp)) {
                    Text("TARGET VIEW (@ %.0f m)".format(targetDistance), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    TargetViewCanvas(trajectory = trajectory, targetDistance = targetDistance, targetHeightM = targetHeight)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Advanced Stats Grid
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatItemSmall("Eff. Range", "%.1f m".format(effectiveRange), Color(0xFF88FF11))
                    StatItemSmall("Max Range", "%.1f m".format(maxRange), Color.White)
                    StatItemSmall("Hold-over", "%.1f cm".format(currentHoldOverCm), if (currentHoldOverCm > 0) Color.Red else Color.Cyan)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatItemSmall("Target E", "%.2f J".format(energyAtTarget), Color.White)
                    StatItemSmall("Max Overhop", "%.1f cm".format(overhop * 100), Color.Yellow)
                    StatItemSmall("OH Dist.", "%.1f m".format(overhopDist), Color.Cyan)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
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
            items(weights) { weightItem ->
                FilterChip(
                    selected = data.selectedWeight == weightItem,
                    onClick = { viewModel.setWeight(weightItem) },
                    label = { Text("%.2f".format(weightItem)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("Target Distance: %.0f m".format(targetDistance), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Slider(value = targetDistance.toFloat(), onValueChange = { targetDistance = it.toDouble() }, valueRange = 5f..100f)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Shooter Height: %.1f m".format(shooterHeight), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = shooterHeight.toFloat(), 
                    onValueChange = { 
                        shooterHeight = it.toDouble()
                        if (coupleHeights) targetHeight = shooterHeight
                    }, 
                    valueRange = 0.1f..2.5f
                )
            }
            
            IconButton(
                onClick = { 
                    coupleHeights = !coupleHeights 
                    if (coupleHeights) targetHeight = shooterHeight
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Icon(
                    imageVector = if (coupleHeights) Icons.Default.Link else Icons.Default.LinkOff,
                    contentDescription = "Couple Heights",
                    tint = if (coupleHeights) Color(0xFF88FF11) else Color.Gray
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text("Target Height: %.1f m".format(targetHeight), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = targetHeight.toFloat(), 
                    onValueChange = { 
                        targetHeight = it.toDouble()
                        if (coupleHeights) shooterHeight = targetHeight
                    }, 
                    valueRange = 0.1f..2.5f
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Speed: %.1f m/s".format(speedMps), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = speedMps.toFloat(), 
                    onValueChange = { speedMps = it.toDouble() }, 
                    valueRange = 50f..200f
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Energy: %.2f J".format(currentJoule), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = currentJoule.toFloat(),
                    onValueChange = { newJoule ->
                        speedMps = sqrt((2.0 * newJoule.toDouble()) / (data.selectedWeight.toDouble() / 1000.0))
                    },
                    valueRange = 0.1f..5.0f
                )
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

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Optimization Limit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("Max Overhop: %.0f cm".format(data.maxAllowedOverhopCm), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Row {
                    IconButton(onClick = { viewModel.setMaxAllowedOverhop((data.maxAllowedOverhopCm - 1).coerceAtLeast(1f)) }) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease")
                    }
                    IconButton(onClick = { viewModel.setMaxAllowedOverhop((data.maxAllowedOverhopCm + 1).coerceAtMost(50f)) }) {
                        Icon(Icons.Default.Add, contentDescription = "Increase")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                var bestHop = 0.0
                var maxRangeVal = 0.0
                
                for (h in 0..2000 step 5) {
                    val eyeHeightOptM = shooterHeight + (sightHeight / 100.0)
                    val aimAngleOptDeg = Math.toDegrees(atan2(targetHeight - eyeHeightOptM, targetDistance))
                    
                    val traj = engine.calculateTrajectory(BallisticParams(
                        massGrams = data.selectedWeight.toDouble(),
                        muzzleVelocityMps = speedMps,
                        hopUpRadS = h.toDouble(),
                        startingHeightM = shooterHeight,
                        launchAngleDeg = aimAngleOptDeg
                    ))
                    
                    val currentOverhop = traj.maxOfOrNull { pt ->
                        if (pt.x > targetDistance) 0.0 else {
                            val aimLineY = eyeHeightOptM + (pt.x / targetDistance) * (targetHeight - eyeHeightOptM)
                            (pt.y - aimLineY).coerceAtLeast(0.0)
                        }
                    } ?: 0.0
                    
                    if (currentOverhop <= (data.maxAllowedOverhopCm / 100.0)) {
                        val crossIdx = traj.indexOfFirst { pt ->
                            val aimY = eyeHeightOptM + (pt.x / targetDistance) * (targetHeight - eyeHeightOptM)
                            pt.y > aimY
                        }
                        val currentEffRange = if (crossIdx != -1) {
                            traj.drop(crossIdx).firstOrNull { pt ->
                                val aimY = eyeHeightOptM + (pt.x / targetDistance) * (targetHeight - eyeHeightOptM)
                                pt.y <= aimY
                            }?.x ?: 0.0
                        } else 0.0
                        
                        if (currentEffRange > maxRangeVal) {
                            maxRangeVal = currentEffRange
                            bestHop = h.toDouble()
                        }
                    }
                }
                hopUp = bestHop
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(rainbowBrush, shape = ButtonDefaults.shape),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ButtonDefaults.ContentPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("OPTIMIZE HOP-UP", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun StatItemSmall(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun TrajectoryCanvas(
    trajectory: List<TrajectoryPoint>, 
    targetDistance: Double,
    eyeHeightM: Double,
    targetHeightM: Double,
    onProbe: (ProbeResult) -> Unit
) {
    if (trajectory.isEmpty()) return
    
    val chronoGreen = Color(0xFF88FF11)
    val gridColor = Color.White.copy(alpha = 0.15f)
    val aimLineColor = Color.Yellow.copy(alpha = 0.4f)
    
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(trajectory) {
                detectTapGestures { offset ->
                    val leftMarginPx = 45.dp.toPx()
                    val rightMarginPx = 16.dp.toPx()
                    val topMarginPx = 16.dp.toPx()
                    val bottomMarginPx = 32.dp.toPx()
                    
                    val graphWidthPx = size.width - leftMarginPx - rightMarginPx
                    val graphHeightPx = size.height - topMarginPx - bottomMarginPx
                    
                    val relativeX = (offset.x - leftMarginPx) / graphWidthPx
                    if (relativeX in 0f..1f) {
                        val maxX = trajectory.last().x
                        val targetM = relativeX * maxX
                        val pt = trajectory.minByOrNull { abs(it.x - targetM) }
                        pt?.let {
                            val aimLineYAtX = eyeHeightM + (it.x / targetDistance) * (targetHeightM - eyeHeightM)
                            onProbe(ProbeResult(
                                distance = it.x.toFloat(),
                                bbHeightCm = (it.y * 100).toFloat(),
                                energyJ = it.energyJoules.toFloat(),
                                timeS = it.time.toFloat(),
                                relativeImpactCm = ((it.y - aimLineYAtX) * 100).toFloat(),
                                holdOverCm = ((targetHeightM - it.y) * 100).toFloat()
                            ))
                        }
                    }
                }
            }
        ) {
            val leftMargin = 45.dp.toPx()
            val bottomMargin = 32.dp.toPx()
            val rightMargin = 16.dp.toPx()
            val topMargin = 16.dp.toPx()
            
            val graphWidth = size.width - leftMargin - rightMargin
            val graphHeight = size.height - bottomMargin - topMargin
            
            val maxX = trajectory.maxOf { it.x }.toFloat().coerceAtLeast(1f)
            val maxY = trajectory.maxOf { it.y }.toFloat().coerceAtLeast(2f)
            val rangeY = maxY 

            // Draw Grid and Labels (X-axis: Meters)
            val xStep = 10f
            var xCoord = 0f
            while (xCoord <= maxX) {
                val xPos = leftMargin + (xCoord / maxX) * graphWidth
                drawLine(gridColor, Offset(xPos, topMargin), Offset(xPos, topMargin + graphHeight), 1.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText(
                    "%.0fm".format(xCoord),
                    xPos,
                    topMargin + graphHeight + 20.dp.toPx(),
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        alpha = 150
                        textSize = 10.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
                xCoord += xStep
            }

            val yStep = 0.5f 
            var yCoord = 0f
            while (yCoord <= maxY) {
                val yPos = topMargin + graphHeight - (yCoord / rangeY) * graphHeight
                drawLine(gridColor, Offset(leftMargin, yPos), Offset(leftMargin + graphWidth, yPos), 1.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText(
                    "%.0f".format(yCoord * 100),
                    leftMargin - 10.dp.toPx(),
                    yPos + 4.dp.toPx(),
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        alpha = 150
                        textSize = 10.sp.toPx()
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }
                )
                yCoord += yStep
            }
            
            drawContext.canvas.nativeCanvas.drawText(
                "cm",
                leftMargin - 10.dp.toPx(),
                topMargin - 4.dp.toPx(),
                android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    alpha = 150
                    textSize = 9.sp.toPx()
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
            )

            val aimStartX = leftMargin
            val aimStartY = topMargin + graphHeight - (eyeHeightM.toFloat() / rangeY) * graphHeight
            val aimEndX = leftMargin + (targetDistance.toFloat() / maxX) * graphWidth
            val aimEndY = topMargin + graphHeight - (targetHeightM.toFloat() / rangeY) * graphHeight
            drawLine(
                color = aimLineColor,
                start = Offset(aimStartX, aimStartY),
                end = Offset(aimEndX, aimEndY),
                strokeWidth = 1.dp.toPx()
            )

            val points = trajectory.map { pt ->
                val px = leftMargin + (pt.x.toFloat() / maxX) * graphWidth
                val py = topMargin + graphHeight - (pt.y.toFloat() / rangeY) * graphHeight
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
            
            val tx = leftMargin + (targetDistance.toFloat() / maxX) * graphWidth
            drawLine(Color.Red.copy(alpha = 0.3f), Offset(tx, topMargin), Offset(tx, topMargin + graphHeight), 1.dp.toPx())
            
            val ty = topMargin + graphHeight - (targetHeightM.toFloat() / rangeY) * graphHeight
            drawLine(
                color = Color.Red,
                start = Offset(tx - 10.dp.toPx(), ty),
                end = Offset(tx + 10.dp.toPx(), ty),
                strokeWidth = 3.dp.toPx()
            )
            
            drawLine(Color.White.copy(alpha = 0.3f), Offset(leftMargin, topMargin + graphHeight), Offset(leftMargin + graphWidth, topMargin + graphHeight), 1.dp.toPx())
        }
    }
}

@Composable
fun TargetViewCanvas(trajectory: List<TrajectoryPoint>, targetDistance: Double, targetHeightM: Double) {
    val chronoGreen = Color(0xFF88FF11)
    val gridColor = Color.White.copy(alpha = 0.1f)
    
    val ptAtDist = trajectory.minByOrNull { abs(it.x - targetDistance) }
    val bbHeightM = ptAtDist?.y ?: 0.0
    
    val relativeImpactM = bbHeightM - targetHeightM
    val relativeImpactCm = relativeImpactM * 100.0

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.size(200.dp).clip(CircleShape),
            color = Color.Transparent,
            border = BorderStroke(2.dp, Color.White.copy(alpha = 0.3f))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                
                val gridStep = 50f
                for (i in -5..5) {
                    val pos = center.y + (i * gridStep)
                    drawLine(gridColor, Offset(0f, pos), Offset(size.width, pos), 1.dp.toPx())
                    drawLine(gridColor, Offset(pos, 0f), Offset(pos, size.height), 1.dp.toPx())
                }

                drawLine(Color.White, Offset(0f, center.y), Offset(size.width, center.y), 1.dp.toPx())
                drawLine(Color.White, Offset(center.x, 0f), Offset(center.x, size.height), 1.dp.toPx())
                
                val scale = 5f 
                val impactY = center.y - (relativeImpactCm.toFloat() * scale)
                
                if (impactY in 0f..size.height) {
                    drawCircle(chronoGreen, 6.dp.toPx(), Offset(center.x, impactY))
                    drawCircle(Color.White, 2.dp.toPx(), Offset(center.x, impactY))
                }
            }
        }
        
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
        
        Spacer(modifier = Modifier.height(32.dp))
        Text("ChronoMate v1.0", modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.height(16.dp))
    }
}
