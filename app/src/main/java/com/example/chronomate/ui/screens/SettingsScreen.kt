package com.example.chronomate.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.chronomate.model.ChronoData
import com.example.chronomate.viewmodel.ChronoViewModel

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
        
        Text("Ballistics Engine Parameters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Fine-tune the physics simulation constants", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))

        BallisticSettingField("Diameter (mm)", data.diameterMm.toString()) { viewModel.updateBallisticSettings(diameterMm = it) }
        BallisticSettingField("Air Density (rho)", data.airDensityRho.toString()) { viewModel.updateBallisticSettings(airDensityRho = it) }
        BallisticSettingField("Drag Coefficient (Cw)", data.dragCoefficientCw.toString()) { viewModel.updateBallisticSettings(dragCoefficientCw = it) }
        BallisticSettingField("Magnus Coefficient (K)", data.magnusCoefficientK.toString()) { viewModel.updateBallisticSettings(magnusCoefficientK = it) }
        BallisticSettingField("Spin Damping (Cr)", data.spinDampingCr.toString()) { viewModel.updateBallisticSettings(spinDampingCr = it) }
        BallisticSettingField("Gravity (m/s²)", data.gravity.toString()) { viewModel.updateBallisticSettings(gravity = it) }

        Spacer(modifier = Modifier.height(32.dp))
        Text("ChronoMate v1.0", modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun BallisticSettingField(label: String, value: String, onUpdate: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toFloatOrNull()?.let { f -> onUpdate(f) }
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true
    )
}
