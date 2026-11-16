package com.example.chronomate.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chronomate.R
import com.example.chronomate.ballistics.DragModel
import com.example.chronomate.model.*
import com.example.chronomate.viewmodel.ChronoViewModel

/**
 * Settings → Projectiles. One switch for the mode (Airsoft = BBs + hop-up trajectory, PCP = pellets
 * & slugs + PCP calculator), the chips to pick the weight, the ordered active list (what the chips
 * show) and the library to add from. Own projectiles always sit in the active list of their kind.
 * Mirrors ui/components/projectiles_card.py on the desktop.
 */
@Composable
fun ProjectilesSection(data: ChronoData, viewModel: ChronoViewModel) {
    val kind = data.modeKind()
    var editing by remember { mutableStateOf<CustomWeight?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CustomWeight?>(null) }
    var showLibrary by remember { mutableStateOf(false) }

    Text(stringResource(R.string.projectiles_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))

    // One switch for both: projectile kind + trajectory model
    val modes = listOf(BallisticMode.AIRSOFT to stringResource(R.string.mode_airsoft), BallisticMode.PCP to stringResource(R.string.mode_pcp))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        modes.forEachIndexed { i, (mode, label) ->
            SegmentedButton(
                selected = data.ballisticMode == mode,
                onClick = { viewModel.setMode(mode) },
                shape = SegmentedButtonDefaults.itemShape(i, modes.size)
            ) { Text(label, maxLines = 1, fontSize = 13.sp) }
        }
    }
    Text(
        stringResource(if (kind == KIND_PELLET) R.string.mode_hint_pcp else R.string.mode_hint_airsoft),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )

    // Active list
    Spacer(Modifier.height(12.dp))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(stringResource(R.string.active_list_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            val ids = data.activeIds()
            ids.forEachIndexed { i, id ->
                val opt = data.resolve(id) ?: return@forEachIndexed
                val own = if (opt.isCustom) data.customWeights.firstOrNull { "own:${it.id}" == id } else null
                ListRow(text = (if (opt.isCustom) "★ " else "") + opt.label, sub = bcText(opt, own)) {
                    IconButton(onClick = { viewModel.moveActive(id, -1) }, enabled = i > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.move_up))
                    }
                    IconButton(onClick = { viewModel.moveActive(id, 1) }, enabled = i < ids.size - 1) {
                        Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.move_down))
                    }
                    if (own != null) {
                        IconButton(onClick = { editing = own }) { Icon(Icons.Default.Edit, stringResource(R.string.own_projectile_edit)) }
                        IconButton(onClick = { deleting = own }) { Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error) }
                    } else {
                        IconButton(onClick = { viewModel.removeFromActive(id) }, enabled = ids.size > 1) {
                            Icon(Icons.Default.Close, stringResource(R.string.remove_from_active))
                        }
                    }
                }
            }
            OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.own_projectile_new), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    // Library (BB weights or the pellet catalogue), collapsed behind a chevron
    Spacer(Modifier.height(4.dp))
    Row(
        Modifier.fillMaxWidth().clickable { showLibrary = !showLibrary }.padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (showLibrary) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.library_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
    if (showLibrary) {
        Spacer(Modifier.height(8.dp))
        LibraryList(data, viewModel, kind)
    }

    if (creating || editing != null) {
        OwnProjectileDialog(
            kind = editing?.kind ?: kind,
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { viewModel.saveOwnProjectile(it); creating = false; editing = null }
        )
    }
    deleting?.let { cw ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.own_projectile_confirm_delete, cw.name)) },
            confirmButton = { TextButton(onClick = { viewModel.deleteOwnProjectile(cw.id); deleting = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun bcText(opt: WeightOption, own: CustomWeight?): String? {
    val p = opt.pellet
    return when {
        p != null && p.bc != null -> "BC ≈%.3f · %s".format(p.bc, p.source)
        p != null -> stringResource(R.string.pcp_bc_unknown)
        own?.bc != null -> "BC %.3f %s".format(own.bc, own.dragModel.name)
        else -> null
    }
}

@Composable
private fun ListRow(text: String, sub: String?, actions: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        actions()
    }
}

@Composable
private fun LibraryList(data: ChronoData, viewModel: ChronoViewModel, kind: String) {
    var query by remember { mutableStateOf("") }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.library_search)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
            if (kind == KIND_PELLET) {
                val calibers = CALIBERS.map { it.first to it.first } + (ALL_CALIBERS to stringResource(R.string.all_calibers))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(8.dp)) {
                    calibers.forEachIndexed { i, (key, label) ->
                        SegmentedButton(
                            selected = data.libraryCaliber == key,
                            onClick = { viewModel.setLibraryCaliber(key) },
                            shape = SegmentedButtonDefaults.itemShape(i, calibers.size)
                        ) { Text(label, maxLines = 1, fontSize = 12.sp) }
                    }
                }
            }
            val active = data.activeIds(kind).toSet()
            library(kind, if (kind == KIND_PELLET) data.libraryCaliber else ALL_CALIBERS, query).forEach { opt ->
                val added = opt.id in active
                ListRow(text = opt.label, sub = bcText(opt, null)) {
                    IconButton(onClick = { viewModel.addToActive(opt.id) }, enabled = !added) {
                        Icon(if (added) Icons.Default.Check else Icons.Default.Add,
                            stringResource(if (added) R.string.in_active else R.string.add_to_active))
                    }
                }
            }
        }
    }
}

@Composable
private fun OwnProjectileDialog(kind: String, initial: CustomWeight?, onDismiss: () -> Unit, onSave: (CustomWeight) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var weight by remember { mutableStateOf(initial?.weight?.toString() ?: if (kind == KIND_BB) "0.25" else "1.0") }
    var caliber by remember { mutableStateOf(initial?.caliber?.toString() ?: if (kind == KIND_BB) "6.0" else "5.5") }
    var hasBc by remember { mutableStateOf(initial?.bc != null) }
    var bc by remember { mutableStateOf(initial?.bc?.toString() ?: "0.030") }
    var model by remember { mutableStateOf(initial?.dragModel ?: DragModel.G1) }
    fun num(t: String) = t.replace(',', '.').toFloatOrNull()
    val valid = num(weight)?.let { it in 0.05f..20f } == true && num(caliber) != null && (!hasBc || num(bc)?.let { it in 0.005f..1f } == true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.own_projectile_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.weight_name_label)) },
                    placeholder = { Text(stringResource(R.string.own_projectile_name_hint)) }, singleLine = true)
                OutlinedTextField(weight, { weight = it }, label = { Text(stringResource(R.string.weight_value_label)) }, suffix = { Text("g") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(caliber, { caliber = it }, label = { Text(stringResource(R.string.caliber_label)) }, suffix = { Text("mm") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                if (kind == KIND_PELLET) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = hasBc, onCheckedChange = { hasBc = it })
                        Text(stringResource(R.string.own_projectile_has_bc))
                    }
                    if (hasBc) {
                        OutlinedTextField(bc, { bc = it }, label = { Text("BC") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DragModel.entries.forEach { m -> FilterChip(selected = model == m, onClick = { model = m }, label = { Text(m.name) }) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val base = initial ?: CustomWeight(name = "", weight = 0.25f, kind = kind)
                onSave(base.copy(
                    name = name.trim().ifEmpty { if (kind == KIND_BB) "BB" else "Pellet" },
                    weight = num(weight)!!, caliber = num(caliber)!!, caliberUnit = "mm", kind = kind,
                    bc = if (kind == KIND_PELLET && hasBc) num(bc) else null,
                    dragModel = model
                ))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
