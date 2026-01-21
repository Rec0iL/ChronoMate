package com.example.chronomate.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chronomate.R
import com.example.chronomate.model.ChronoData
import com.example.chronomate.model.WeightType
import com.example.chronomate.viewmodel.ChronoViewModel
import kotlin.math.pow

@Composable
fun OrgaChronoScreen(data: ChronoData, viewModel: ChronoViewModel) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    val weightItems = when (data.weightType) {
        WeightType.BB -> listOf(0.20f, 0.23f, 0.25f, 0.28f, 0.30f, 0.32f, 0.36f, 0.40f, 0.43f, 0.45f).map { it to "%.2f g".format(it) }
        WeightType.DIABLO -> listOf(0.50f, 0.51f, 0.52f, 0.53f, 0.54f).map { it to "%.2f g".format(it) }
        WeightType.CUSTOM -> data.customWeights.map { it.weight to "${it.name} ${it.caliber}${it.caliberUnit} %.2f g".format(it.weight) }
    }
    
    val weights = weightItems.map { it.first }
    
    // We get the raw velocity float from the latest shot for precision
    val latestVelocity = data.shots.lastOrNull()?.velocity ?: 0f
    
    val maxWeightKg = if (latestVelocity > 0f) (2f * data.maxAllowedJoule) / latestVelocity.pow(2) else 0f
    val maxWeightGrams = maxWeightKg * 1000f

    // Calculate practical weight (highest available weight that stays within Joule limit)
    val practicalWeight = weights.lastOrNull { it <= maxWeightGrams } ?: 0f
    val sparePercentage = if (practicalWeight > 0f) {
        ((maxWeightGrams - practicalWeight) / practicalWeight) * 100f
    } else 0f

    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    OrgaHeroSection(data = data, practicalWeight = practicalWeight, sparePercentage = sparePercentage)
                    Spacer(modifier = Modifier.height(16.dp))
                    OrgaSettingsSection(data = data, viewModel = viewModel)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(2.5f)) { 
                    Text(stringResource(R.string.joule_grid_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (weightItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No weights available. Check settings.")
                        }
                    } else {
                        JouleGridStatic(velocity = latestVelocity, weights = weightItems, practicalWeight = practicalWeight, columns = 4)
                    }
                }
            }
        } else {
            OrgaHeroSection(data = data, practicalWeight = practicalWeight, sparePercentage = sparePercentage)
            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.joule_grid_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            if (weightItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("No weights available. Check settings.")
                }
            } else {
                JouleGridStatic(velocity = latestVelocity, weights = weightItems, practicalWeight = practicalWeight, columns = 2)
            }
            Spacer(modifier = Modifier.height(24.dp))
            OrgaSettingsSection(data = data, viewModel = viewModel)
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun JouleGridStatic(velocity: Float, weights: List<Pair<Float, String>>, practicalWeight: Float, columns: Int) {
    val isDark = isSystemInDarkTheme()
    val rows = weights.chunked(columns)
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { rowWeights ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowWeights.forEach { (weight, label) ->
                    val energy = 0.5f * (weight / 1000f) * velocity.pow(2)
                    
                    // Determine colors based on limits and theme
                    val (bgColor, contentColor) = when {
                        velocity <= 0f -> {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        weight < practicalWeight -> {
                            if (isDark) Color(0xFF1B5E20).copy(alpha = 0.4f) to Color(0xFFA5D6A7)
                            else Color(0xFFC8E6C9) to Color(0xFF1B5E20)
                        }
                        weight == practicalWeight -> {
                            if (isDark) Color(0xFFE65100).copy(alpha = 0.4f) to Color(0xFFFFCC80)
                            else Color(0xFFFFE0B2) to Color(0xFFE65100)
                        }
                        else -> {
                            if (isDark) Color(0xFFB71C1C).copy(alpha = 0.4f) to Color(0xFFEF9A9A)
                            else Color(0xFFFFCDD2) to Color(0xFFB71C1C)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = bgColor)
                    ) {
                        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                label, 
                                style = MaterialTheme.typography.labelSmall, 
                                color = contentColor.copy(alpha = 0.8f)
                            )
                            Text(
                                "%.2f J".format(energy), 
                                style = MaterialTheme.typography.titleMedium, 
                                fontWeight = FontWeight.Black,
                                color = contentColor
                            )
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
fun OrgaHeroSection(data: ChronoData, practicalWeight: Float, sparePercentage: Float) {
    val isLight = MaterialTheme.colorScheme.surface == Color.White || MaterialTheme.colorScheme.surface.red > 0.9f
    val chronoGreen = Color(0xFF88FF11)
    val contentColor = if (isLight) MaterialTheme.colorScheme.onPrimaryContainer else chronoGreen

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
            Text(
                text = stringResource(R.string.latest_shot),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.6f)
            )
            Text(
                text = data.velocity + " m/s",
                fontSize = 48.sp,
                color = contentColor,
                fontWeight = FontWeight.Black
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp), 
                color = contentColor.copy(alpha = 0.1f)
            )
            Text(
                text = stringResource(R.string.max_practical_weight),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.6f)
            )
            if (practicalWeight > 0f) {
                Text(
                    text = "%.2f g".format(practicalWeight),
                    fontSize = 32.sp,
                    color = contentColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.room_to_limit).format(sparePercentage),
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.8f)
                )
            } else {
                Text(
                    text = stringResource(R.string.over_limit),
                    fontSize = 32.sp,
                    color = if (isLight) Color(0xFFB71C1C) else Color.Red,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.too_hot_for_20),
                    style = MaterialTheme.typography.labelSmall,
                    color = (if (isLight) Color(0xFFB71C1C) else Color.Red).copy(alpha = 0.7f)
                )
            }
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
            Text(stringResource(R.string.orga_limits), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = textValue,
                onValueChange = { 
                    textValue = it
                    it.toFloatOrNull()?.let { joule ->
                        viewModel.setMaxAllowedJoule(joule)
                    }
                },
                label = { Text(stringResource(R.string.max_allowed_joule_label)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        }
    }
}
