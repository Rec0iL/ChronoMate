package com.example.chronomate.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.chronomate.R
import com.example.chronomate.model.EnvironmentSettings
import com.example.chronomate.viewmodel.ChronoViewModel
import kotlin.math.roundToInt

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(16.dp))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

/** Number input that accepts "," and "." and only reports valid values inside [range]. */
@Composable
fun NumberField(
    label: String,
    value: Double,
    suffix: String,
    decimals: Int,
    range: ClosedFloatingPointRange<Double>,
    supporting: String? = null,
    onValue: (Double) -> Unit
) {
    fun fmt(x: Double) = "%.${decimals}f".format(java.util.Locale.ROOT, x)
    var text by remember { mutableStateOf(fmt(value)) }
    // Follow outside changes (preset picked, best zero used) unless the text already means that value.
    LaunchedEffect(value) {
        if (text.replace(',', '.').toDoubleOrNull() != value) text = fmt(value)
    }
    val parsed = text.replace(',', '.').toDoubleOrNull()
    val valid = parsed != null && parsed in range
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.replace(',', '.').toDoubleOrNull()?.takeIf { x -> x in range }?.let(onValue)
        },
        label = { Text(label) },
        suffix = if (suffix.isNotEmpty()) ({ Text(suffix) }) else null,
        supportingText = supporting?.let { s -> { Text(s) } },
        isError = !valid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Weather & wind, shared by the airsoft and the PCP trajectory screens (one setting for both). */
@Composable
fun EnvironmentSection(env: EnvironmentSettings, viewModel: ChronoViewModel) {
    Section(stringResource(R.string.pcp_card_environment)) {
        NumberField(stringResource(R.string.pcp_temperature), env.temperatureC, "°C", 1, -30.0..50.0) { x -> viewModel.updateEnvironment { it.copy(temperatureC = x) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.pcp_use_altitude), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = env.useAltitude, onCheckedChange = { b -> viewModel.updateEnvironment { it.copy(useAltitude = b) } })
        }
        if (env.useAltitude) {
            NumberField(stringResource(R.string.pcp_altitude), env.altitudeM, "m", 0, -400.0..5000.0) { x -> viewModel.updateEnvironment { it.copy(altitudeM = x) } }
        } else {
            NumberField(stringResource(R.string.pcp_pressure), env.pressureHpa, "hPa", 1, 600.0..1100.0) { x -> viewModel.updateEnvironment { it.copy(pressureHpa = x) } }
        }
        NumberField(stringResource(R.string.pcp_humidity), env.humidityPct, "%", 0, 0.0..100.0) { x -> viewModel.updateEnvironment { it.copy(humidityPct = x) } }
        NumberField(stringResource(R.string.pcp_wind_speed), env.windSpeedMps, "m/s", 1, 0.0..20.0) { x -> viewModel.updateEnvironment { it.copy(windSpeedMps = x) } }
        Text(stringResource(R.string.pcp_wind_dir) + ": " + "%.1f".format(if (env.windClock < 0.5) 12.0 else env.windClock) + " " + stringResource(R.string.pcp_oclock), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = (if (env.windClock < 0.5) 12.0 else env.windClock).toFloat(),
            onValueChange = { f -> viewModel.updateEnvironment { it.copy(windClock = (f * 2).roundToInt() / 2.0 % 12.0) } },
            valueRange = 0.5f..12f,
            steps = 22
        )
        Text(stringResource(R.string.pcp_wind_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
