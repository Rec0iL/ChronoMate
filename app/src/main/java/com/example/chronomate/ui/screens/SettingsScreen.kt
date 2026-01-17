package com.example.chronomate.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.chronomate.R
import com.example.chronomate.model.ChronoData
import com.example.chronomate.viewmodel.ChronoViewModel

@Composable
fun SettingsScreen(data: ChronoData, viewModel: ChronoViewModel) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
        Text(stringResource(R.string.general_settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(stringResource(R.string.dark_mode), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.dark_mode_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = data.isDarkMode,
                onCheckedChange = { viewModel.toggleDarkMode(it) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        ListItem(
            headlineContent = { Text(stringResource(R.string.language)) },
            supportingContent = { 
                Text(when(data.language) {
                    "de" -> "Deutsch"
                    "pl" -> "Polski"
                    "it" -> "Italiano"
                    "es" -> "Español"
                    "nl" -> "Nederlands"
                    "ja" -> "日本語"
                    "zh-rTW" -> "繁體中文"
                    "uk" -> "Українська"
                    else -> "English"
                }) 
            },
            leadingContent = { Icon(Icons.Default.Language, contentDescription = null) },
            modifier = Modifier.clickable { showLanguageDialog = true }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(stringResource(R.string.ballistics_engine_params), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.fine_tune_physics), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))

        BallisticSettingField(stringResource(R.string.ballistic_diameter), data.diameterMm.toString()) { viewModel.updateBallisticSettings(diameterMm = it) }
        BallisticSettingField(stringResource(R.string.ballistic_air_density), data.airDensityRho.toString()) { viewModel.updateBallisticSettings(airDensityRho = it) }
        BallisticSettingField(stringResource(R.string.ballistic_drag_coeff), data.dragCoefficientCw.toString()) { viewModel.updateBallisticSettings(dragCoefficientCw = it) }
        BallisticSettingField(stringResource(R.string.ballistic_magnus_coeff), data.magnusCoefficientK.toString()) { viewModel.updateBallisticSettings(magnusCoefficientK = it) }
        BallisticSettingField(stringResource(R.string.ballistic_spin_damping), data.spinDampingCr.toString()) { viewModel.updateBallisticSettings(spinDampingCr = it) }
        BallisticSettingField(stringResource(R.string.ballistic_gravity), data.gravity.toString()) { viewModel.updateBallisticSettings(gravity = it) }

        Spacer(modifier = Modifier.height(32.dp))
        Text("ChronoMate v1.0", modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.language)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    val languages = listOf(
                        Triple("English", "en", "English"),
                        Triple("Deutsch", "de", "Deutsch"),
                        Triple("Polski", "pl", "Polski"),
                        Triple("Italiano", "it", "Italiano"),
                        Triple("Español", "es", "Español"),
                        Triple("Nederlands", "nl", "Nederlands"),
                        Triple("日本語", "ja", "日本語"),
                        Triple("繁體中文", "zh-rTW", "繁體中文"),
                        Triple("Українська", "uk", "Українська")
                    )
                    
                    languages.forEach { (name, code, _) ->
                        LanguageOption(name, code, data.language) {
                            viewModel.setLanguage(context, code)
                            showLanguageDialog = false
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.restart_required_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun LanguageOption(label: String, code: String, currentCode: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = code == currentCode, onClick = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
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
