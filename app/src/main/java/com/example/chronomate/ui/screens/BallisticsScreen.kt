package com.example.chronomate.ui.screens

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chronomate.ballistics.BallisticsEngine
import com.example.chronomate.model.BallisticParams
import com.example.chronomate.model.ChronoData
import com.example.chronomate.model.TrajectoryPoint
import com.example.chronomate.viewmodel.ChronoViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

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
    val isLight = MaterialTheme.colorScheme.surface == Color.White || MaterialTheme.colorScheme.surface.red > 0.9f
    
    var speedMps by rememberSaveable { mutableStateOf(data.velocity.toDoubleOrNull() ?: 100.0) }
    
    val currentJoule = 0.5 * (data.selectedWeight.toDouble() / 1000.0) * speedMps.pow(2.0)
    
    var hopUp by rememberSaveable { mutableStateOf(1000.0) }
    var targetDistance by rememberSaveable { mutableStateOf(50.0) }
    var shooterHeight by rememberSaveable { mutableStateOf(1.5) }
    var targetHeight by rememberSaveable { mutableStateOf(1.5) }
    
    var shooterHeightText by remember { mutableStateOf("1.5") }
    var targetHeightText by remember { mutableStateOf("1.5") }
    
    var coupleHeights by rememberSaveable { mutableStateOf(true) }
    var sightHeight by rememberSaveable { mutableStateOf(5.0) }
    
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
    val trajectory = remember(
        data.selectedWeight, speedMps, hopUp, shooterHeight, aimAngleDeg,
        data.diameterMm, data.airDensityRho, data.dragCoefficientCw, 
        data.magnusCoefficientK, data.spinDampingCr, data.gravity
    ) {
        engine.calculateTrajectory(BallisticParams(
            massGrams = data.selectedWeight.toDouble(),
            muzzleVelocityMps = speedMps,
            hopUpRadS = hopUp,
            startingHeightM = shooterHeight,
            launchAngleDeg = aimAngleDeg,
            diameterMm = data.diameterMm.toDouble(),
            airDensityRho = data.airDensityRho.toDouble(),
            dragCoefficientCw = data.dragCoefficientCw.toDouble(),
            magnusCoefficientK = data.magnusCoefficientK.toDouble(),
            spinDampingCr = data.spinDampingCr.toDouble(),
            gravity = data.gravity.toDouble()
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
    val context = LocalContext.current

    val rainbowBrush = remember {
        Brush.linearGradient(
            colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
        )
    }

    val chartBgColor = if (isLight) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Black
    val greenColor = if (isLight) Color(0xFF1B5E20) else Color(0xFF88FF11)
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val redColor = if (isLight) Color(0xFFB71C1C) else Color.Red
    val cyanColor = if (isLight) Color(0xFF006064) else Color.Cyan
    val yellowColor = if (isLight) Color(0xFFFBC02D) else Color.Yellow

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
                    colors = CardDefaults.cardColors(containerColor = chartBgColor)
                ) {
                    Column(Modifier.padding(8.dp).fillMaxSize()) {
                        Text("SIDE VIEW (m/cm) - TAP TO PROBE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TrajectoryCanvas(
                            trajectory = trajectory, 
                            targetDistance = targetDistance,
                            eyeHeightM = eyeHeightM,
                            targetHeightM = targetHeight,
                            onProbe = { probeResult = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Card(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = chartBgColor)
                ) {
                    Column(Modifier.padding(8.dp).fillMaxSize()) {
                        Text("TARGET VIEW (@ %.0f m)".format(targetDistance), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TargetViewCanvas(trajectory = trajectory, targetDistance = targetDistance, targetHeightM = targetHeight)
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                colors = CardDefaults.cardColors(containerColor = chartBgColor)
            ) {
                Column(Modifier.padding(8.dp).fillMaxSize()) {
                    Text("SIDE VIEW (m/cm) - TAP TO PROBE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TrajectoryCanvas(
                        trajectory = trajectory, 
                        targetDistance = targetDistance,
                        eyeHeightM = eyeHeightM,
                        targetHeightM = targetHeight,
                        onProbe = { probeResult = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth().height(250.dp),
                colors = CardDefaults.cardColors(containerColor = chartBgColor)
            ) {
                Column(Modifier.padding(8.dp).fillMaxSize()) {
                    Text("TARGET VIEW (@ %.0f m)".format(targetDistance), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TargetViewCanvas(trajectory = trajectory, targetDistance = targetDistance, targetHeightM = targetHeight)
                }
            }
        }
        
        // Probe result popup positioned below the charts in both orientations
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
                            Text("Rel. Impact: %.1f cm".format(res.relativeImpactCm), color = if (res.relativeImpactCm < 0) redColor else Color.Unspecified)
                            Text("Hold-over: %.1f cm".format(res.holdOverCm), fontWeight = FontWeight.Bold, color = if (res.holdOverCm > 0) redColor else cyanColor)
                        }
                    }
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
                    StatItemSmall("Eff. Range", "%.1f m".format(effectiveRange), greenColor)
                    StatItemSmall("Max Range", "%.1f m".format(maxRange), onSurfaceColor)
                    StatItemSmall("Hold-over", "%.1f cm".format(currentHoldOverCm), if (currentHoldOverCm > 0) redColor else cyanColor)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatItemSmall("Target E", "%.2f J".format(energyAtTarget), onSurfaceColor)
                    StatItemSmall("Max Overhop", "%.1f cm".format(overhop * 100), onSurfaceColor)
                    StatItemSmall("OH Dist.", "%.1f m".format(overhopDist), cyanColor)
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

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = shooterHeightText,
                onValueChange = { 
                    shooterHeightText = it
                    it.toDoubleOrNull()?.let { h -> 
                        shooterHeight = h
                        if (coupleHeights) {
                            targetHeight = h
                            targetHeightText = it
                        }
                    }
                },
                label = { Text("Shooter H (m)") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
            
            IconButton(
                onClick = { 
                    coupleHeights = !coupleHeights 
                    if (coupleHeights) {
                        targetHeight = shooterHeight
                        targetHeightText = shooterHeightText
                    }
                },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = if (coupleHeights) Icons.Default.Link else Icons.Default.LinkOff,
                    contentDescription = "Couple Heights",
                    tint = if (coupleHeights) greenColor else Color.Gray
                )
            }

            OutlinedTextField(
                value = targetHeightText,
                onValueChange = { 
                    targetHeightText = it
                    it.toDoubleOrNull()?.let { h -> 
                        targetHeight = h
                        if (coupleHeights) {
                            shooterHeight = h
                            shooterHeightText = it
                        }
                    }
                },
                label = { Text("Target H (m)") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                        launchAngleDeg = aimAngleOptDeg,
                        diameterMm = data.diameterMm.toDouble(),
                        airDensityRho = data.airDensityRho.toDouble(),
                        dragCoefficientCw = data.dragCoefficientCw.toDouble(),
                        magnusCoefficientK = data.magnusCoefficientK.toDouble(),
                        spinDampingCr = data.spinDampingCr.toDouble(),
                        gravity = data.gravity.toDouble()
                    ))
                    
                    val currentOverhop = traj.maxOfOrNull { pt ->
                        if (pt.x > targetDistance) 0.0 else {
                            val aimLineY = eyeHeightOptM + (pt.x / targetDistance) * (targetHeight - eyeHeightOptM)
                            (pt.y - aimLineY).coerceAtLeast(0.0)
                        }
                    } ?: 0.0
                    
                    if (currentOverhop <= (data.maxAllowedOverhopCm / 100.0)) {
                        val crossIdx = traj.indexOfFirst { pt ->
                            val aimY = eyeHeightOptM + (pt.x / targetDistance) * (targetHeight - eyeHeightM)
                            pt.y > aimY
                        }
                        val currentEffRange = if (crossIdx != -1) {
                            traj.drop(crossIdx).firstOrNull { pt ->
                                val aimY = eyeHeightOptM + (pt.x / targetDistance) * (targetHeight - eyeHeightM)
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

        Spacer(modifier = Modifier.height(16.dp))

        val annotatedString = buildAnnotatedString {
            append("Ballistic calculator powered by GWC Airsoft Team Leipzig.\n")
            pushStringAnnotation(tag = "URL", annotation = "https://gwc-leipzig.de/")
            withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                append("https://gwc-leipzig.de/")
            }
            pop()
            append("\n(Ghost Warrior Commando Airsoft-Team Leipzig)")
        }

        Text(
            text = annotatedString,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable {
                    annotatedString.getStringAnnotations(tag = "URL", start = 0, end = annotatedString.length)
                        .firstOrNull()?.let { annotation ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                            context.startActivity(intent)
                        }
                }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun StatItemSmall(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label, 
            style = MaterialTheme.typography.labelSmall, 
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value, 
            style = MaterialTheme.typography.titleSmall, 
            fontWeight = FontWeight.Bold, 
            color = color
        )
    }
}

@Composable
fun TrajectoryCanvas(
    trajectory: List<TrajectoryPoint>, 
    targetDistance: Double,
    eyeHeightM: Double,
    targetHeightM: Double,
    onProbe: (ProbeResult) -> Unit,
    modifier: Modifier = Modifier
) {
    if (trajectory.isEmpty()) return
    
    val isLight = MaterialTheme.colorScheme.surface == Color.White || MaterialTheme.colorScheme.surface.red > 0.9f
    
    val greenColor = if (isLight) Color(0xFF1B5E20) else Color(0xFF88FF11)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val aimLineColor = if (isLight) Color(0xFFE55A16).copy(alpha = 0.6f) else Color.Yellow.copy(alpha = 0.4f)
    val labelArgb = labelColor.toArgb()
    
    BoxWithConstraints(modifier = modifier) {
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
                        color = labelArgb
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
                        color = labelArgb
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
                    color = labelArgb
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
            drawPath(path = path, color = greenColor, style = Stroke(width = 2.dp.toPx()))
            
            val tx = leftMargin + (targetDistance.toFloat() / maxX) * graphWidth
            drawLine(Color.Red.copy(alpha = 0.3f), Offset(tx, topMargin), Offset(tx, topMargin + graphHeight), 1.dp.toPx())
            
            val ty = topMargin + graphHeight - (targetHeightM.toFloat() / rangeY) * graphHeight
            drawLine(
                color = if (isLight) Color(0xFFB71C1C) else Color.Red,
                start = Offset(tx - 10.dp.toPx(), ty),
                end = Offset(tx + 10.dp.toPx(), ty),
                strokeWidth = 3.dp.toPx()
            )
            
            drawLine(labelColor.copy(alpha = 0.3f), Offset(leftMargin, topMargin + graphHeight), Offset(leftMargin + graphWidth, topMargin + graphHeight), 1.dp.toPx())
        }
    }
}

@Composable
fun TargetViewCanvas(trajectory: List<TrajectoryPoint>, targetDistance: Double, targetHeightM: Double) {
    val isLight = MaterialTheme.colorScheme.surface == Color.White || MaterialTheme.colorScheme.surface.red > 0.9f
    
    val chronoGreen = if (isLight) Color(0xFF1B5E20) else Color(0xFF88FF11)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val crosshairColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    val surfaceColor = MaterialTheme.colorScheme.surface
    
    val ptAtDist = trajectory.minByOrNull { abs(it.x - targetDistance) }
    val bbHeightM = ptAtDist?.y ?: 0.0
    
    val relativeImpactM = bbHeightM - targetHeightM
    val relativeImpactCm = relativeImpactM * 100.0

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.size(200.dp).clip(CircleShape),
            color = Color.Transparent,
            border = BorderStroke(2.dp, crosshairColor)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                
                val gridStep = 50f
                for (i in -5..5) {
                    val pos = center.y + (i * gridStep)
                    drawLine(gridColor, Offset(0f, pos), Offset(size.width, pos), 1.dp.toPx())
                    drawLine(gridColor, Offset(pos, 0f), Offset(pos, size.height), 1.dp.toPx())
                }

                drawLine(crosshairColor, Offset(0f, center.y), Offset(size.width, center.y), 1.dp.toPx())
                drawLine(crosshairColor, Offset(center.x, 0f), Offset(center.x, size.height), 1.dp.toPx())
                
                val scale = 5f 
                val impactY = center.y - (relativeImpactCm.toFloat() * scale)
                
                if (impactY in 0f..size.height) {
                    drawCircle(chronoGreen, 6.dp.toPx(), Offset(center.x, impactY))
                    drawCircle(surfaceColor, 2.dp.toPx(), Offset(center.x, impactY))
                }
            }
        }
        
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val redColor = if (isLight) Color(0xFFB71C1C) else Color.Red
            Text(
                text = if (relativeImpactCm >= 0) "HIT: %.1f cm ABOVE".format(relativeImpactCm) 
                       else "HIT: %.1f cm BELOW".format(abs(relativeImpactCm)),
                color = if (relativeImpactCm >= 0) chronoGreen else redColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold)
        }
    }
}
