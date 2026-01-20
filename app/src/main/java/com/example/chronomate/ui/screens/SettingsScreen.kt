package com.example.chronomate.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chronomate.R
import com.example.chronomate.model.ChronoData
import com.example.chronomate.model.WeightType
import com.example.chronomate.viewmodel.ChronoViewModel

@Composable
fun SettingsScreen(data: ChronoData, viewModel: ChronoViewModel) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }
    var weightsExpanded by remember { mutableStateOf(false) }
    
    val languages = remember {
        listOf(
            Triple("English", "en", "English"),
            Triple("Deutsch", "de", "Deutsch"),
            Triple("Polski", "pl", "Polski"),
            Triple("Italiano", "it", "Italiano"),
            Triple("Español", "es", "Español"),
            Triple("Nederlands", "nl", "Nederlands"),
            Triple("日本語", "ja", "日本語"),
            Triple("繁體中文", "zh-rTW", "繁體中文"),
            Triple("简体中文", "zh-rCN", "简体中文"),
            Triple("Українська", "uk", "Українська"),
            Triple("Norsk", "no", "Norsk"),
            Triple("Svenska", "sv", "Svenska"),
            Triple("Dansk", "da", "Dansk")
        ).sortedBy { it.first }
    }

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
                Text(languages.find { it.second == data.language }?.first ?: "English") 
            },
            leadingContent = { Icon(Icons.Default.Language, contentDescription = null) },
            modifier = Modifier.clickable { showLanguageDialog = true }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Weight Type Selection
        Text(stringResource(R.string.weight_config_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(), 
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeightTypeButton(
                label = stringResource(R.string.weight_type_bbs),
                selected = data.weightType == WeightType.BB,
                onClick = { viewModel.setWeightType(context, WeightType.BB) },
                modifier = Modifier.weight(1f)
            )
            WeightTypeButton(
                label = stringResource(R.string.weight_type_diablos),
                selected = data.weightType == WeightType.DIABLO,
                onClick = { viewModel.setWeightType(context, WeightType.DIABLO) },
                modifier = Modifier.weight(1f)
            )
            WeightTypeButton(
                label = stringResource(R.string.weight_type_custom),
                selected = data.weightType == WeightType.CUSTOM,
                onClick = { viewModel.setWeightType(context, WeightType.CUSTOM) },
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Expandable Custom Weights Table
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { weightsExpanded = !weightsExpanded }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.custom_weights_list), style = MaterialTheme.typography.titleSmall)
                    Icon(
                        if (weightsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
                
                AnimatedVisibility(visible = weightsExpanded) {
                    Column {
                        if (data.customWeights.isEmpty()) {
                            Text(
                                stringResource(R.string.no_custom_weights),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally)
                            )
                        } else {
                            // Header
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text(stringResource(R.string.weight_name_label), modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.weight_value_label), modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(48.dp))
                            }
                            
                            data.customWeights.forEachIndexed { index, custom ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    var nameText by remember(custom.name) { mutableStateOf(custom.name) }
                                    var weightText by remember(custom.weight) { mutableStateOf(custom.weight.toString()) }
                                    
                                    TextField(
                                        value = nameText,
                                        onValueChange = { 
                                            nameText = it
                                            viewModel.updateCustomWeight(context, index, it, custom.weight)
                                        },
                                        modifier = Modifier.weight(2f),
                                        textStyle = MaterialTheme.typography.bodySmall,
                                        singleLine = true
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    TextField(
                                        value = weightText,
                                        onValueChange = { 
                                            weightText = it
                                            it.toFloatOrNull()?.let { w ->
                                                viewModel.updateCustomWeight(context, index, custom.name, w)
                                            }
                                        },
                                        modifier = Modifier.weight(1.5f),
                                        textStyle = MaterialTheme.typography.bodySmall,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true
                                    )
                                    IconButton(onClick = { viewModel.removeCustomWeight(context, index) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                        
                        Button(
                            onClick = { viewModel.addCustomWeight(context) },
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            colors = ButtonDefaults.buttonColors()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.add_row_button))
                        }
                    }
                }
            }
        }

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
fun WeightTypeButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var resizedFontSize by remember { mutableStateOf(12.sp) }
    
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = resizedFontSize,
                lineHeight = resizedFontSize
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            softWrap = false,
            onTextLayout = { textLayoutResult ->
                if (textLayoutResult.hasVisualOverflow) {
                    resizedFontSize *= 0.95f
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
