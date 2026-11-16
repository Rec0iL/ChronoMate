package com.example.chronomate.ui.screens

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.chronomate.R
import com.example.chronomate.model.ChronoData
import com.example.chronomate.model.Compliance
import com.example.chronomate.model.availableWeights
import com.example.chronomate.model.isSelected
import com.example.chronomate.model.WeaponClass
import com.example.chronomate.model.activeWeaponClass
import com.example.chronomate.model.complianceOf
import com.example.chronomate.model.maxWeightGrams
import com.example.chronomate.model.moved
import com.example.chronomate.viewmodel.ChronoViewModel
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt

private val ActiveAccentDark = Color(0xFF00E5FF)
private val ActiveAccentLight = Color(0xFF00838F)
private val CARD_HEIGHT = 132.dp
private val GRID_GAP = 10.dp

/**
 * Orga Chrono: port of the desktop compliance board.
 * Every weapon class is judged at once against the measured velocity (at a 0.20 g BB); tapping a
 * card makes it the active reference for the Joule grid; long-press + drag reorders the classes.
 */
@Composable
fun OrgaChronoScreen(data: ChronoData, viewModel: ChronoViewModel) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isDark = data.isDarkMode
    val latestVelocity = data.shots.lastOrNull()?.velocity ?: 0f
    val active = data.activeWeaponClass()

    var editing by remember { mutableStateOf<WeaponClass?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var dragActive by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState(), enabled = !dragActive)
            .padding(16.dp)
    ) {
        // Title row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.orga_limits),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { confirmReset = true }) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reset_weapon_classes))
            }
            FilledTonalButton(onClick = { adding = true }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.add_weapon_class_short))
            }
        }
        Spacer(Modifier.height(12.dp))

        MeasuredVelocityCard(latestVelocity, isDark)
        Spacer(Modifier.height(20.dp))

        Text(
            stringResource(R.string.compliance_board_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(R.string.drag_to_reorder_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        ReorderableClassGrid(
            classes = data.weaponClasses,
            columns = if (isLandscape) 5 else 2,
            onMove = { from, to -> viewModel.moveWeaponClass(from, to) },
            onDragActive = { dragActive = it }
        ) { wc, isDragged ->
            ClassComplianceCard(
                wc = wc,
                velocity = latestVelocity,
                isActive = wc.name == active?.name,
                isDragged = isDragged,
                isDark = isDark,
                onClick = { viewModel.selectWeaponClass(wc.name) },
                onEdit = { editing = wc }
            )
        }

        Spacer(Modifier.height(20.dp))
        val limit = active?.maxJoules ?: data.maxAllowedJoule
        Text(
            stringResource(R.string.joule_grid_for_class, active?.name ?: "-", limit),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (isDark) ActiveAccentDark else ActiveAccentLight
        )
        Spacer(Modifier.height(8.dp))
        // Same list as the Dashboard chips; tap a tile to make it the weight shots are judged with.
        val options = data.availableWeights()
        val weightItems = options.map { it.weight to if (it.isCustom) it.label else "%.2f g".format(it.weight) }
        val maxGrams = maxWeightGrams(limit, latestVelocity)
        val practical = options.map { it.weight }.filter { it <= maxGrams }.maxOrNull() ?: 0f
        JouleGridStatic(
            velocity = latestVelocity,
            weights = weightItems,
            practicalWeight = practical,
            columns = if (isLandscape) 5 else 2,
            isDark = isDark,
            selectedIndex = options.indexOfFirst { data.isSelected(it) },
            onSelect = { i -> viewModel.selectWeightOption(options[i]) }
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.joule_grid_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
    }

    if (adding || editing != null) {
        WeaponClassDialog(
            initial = editing,
            canDelete = editing != null && data.weaponClasses.size > 1,
            onDismiss = { adding = false; editing = null },
            onSave = { updated ->
                val old = editing
                if (old == null) viewModel.addWeaponClass(updated) else viewModel.updateWeaponClass(old.name, updated)
                adding = false; editing = null
            },
            onDelete = {
                editing?.let { viewModel.deleteWeaponClass(it.name) }
                editing = null
            }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_weapon_classes)) },
            text = { Text(stringResource(R.string.confirm_reset_classes)) },
            confirmButton = {
                TextButton(onClick = { viewModel.resetWeaponClasses(); confirmReset = false }) {
                    Text(stringResource(R.string.reset_weapon_classes))
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun MeasuredVelocityCard(velocity: Float, isDark: Boolean) {
    val green = if (isDark) Color(0xFF88FF11) else Color(0xFF2E7D32)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color.Black else MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(
                stringResource(R.string.measured_velocity),
                style = MaterialTheme.typography.labelMedium,
                color = green.copy(alpha = 0.7f)
            )
            Text(
                if (velocity > 0f) "%.2f m/s".format(velocity) else "-- m/s",
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = green,
                maxLines = 1
            )
        }
    }
}

/**
 * A grid whose cards can be reordered: long-press lifts a card (it scales up and casts a shadow),
 * it follows the finger, and the other cards slide aside to show where it will land.
 * All cells have the same fixed size, so state changes inside a card never move the grid.
 */
@Composable
private fun ReorderableClassGrid(
    classes: List<WeaponClass>,
    columns: Int,
    onMove: (from: Int, to: Int) -> Unit,
    onDragActive: (Boolean) -> Unit,
    card: @Composable (WeaponClass, Boolean) -> Unit
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentClasses by rememberUpdatedState(classes)
    val currentOnMove by rememberUpdatedState(onMove)

    var draggedName by remember { mutableStateOf<String?>(null) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragTarget by remember { mutableIntStateOf(-1) }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellW: Dp = (maxWidth - GRID_GAP * (columns - 1)) / columns
        val rows = ceil(classes.size / columns.toFloat()).toInt().coerceAtLeast(1)
        val cellWpx = with(density) { cellW.toPx() }
        val cellHpx = with(density) { CARD_HEIGHT.toPx() }
        val gapPx = with(density) { GRID_GAP.toPx() }

        fun slot(index: Int) = IntOffset(
            ((index % columns) * (cellWpx + gapPx)).roundToInt(),
            ((index / columns) * (cellHpx + gapPx)).roundToInt()
        )

        fun indexAt(center: Offset): Int {
            val col = (center.x / (cellWpx + gapPx)).toInt().coerceIn(0, columns - 1)
            val row = (center.y / (cellHpx + gapPx)).toInt().coerceIn(0, rows - 1)
            return (row * columns + col).coerceIn(0, currentClasses.size - 1)
        }

        // Order shown while dragging: the dragged card already sits at its drop target.
        val order = if (draggedName != null && dragFrom >= 0 && dragTarget >= 0) classes.moved(dragFrom, dragTarget) else classes

        Box(Modifier.fillMaxWidth().height(CARD_HEIGHT * rows + GRID_GAP * (rows - 1))) {
            classes.forEach { wc ->
                key(wc.name) {
                    val isDragged = wc.name == draggedName
                    val target = slot(order.indexOf(wc).coerceAtLeast(0))
                    val position = remember { Animatable(target, IntOffset.VectorConverter) }
                    LaunchedEffect(target, isDragged) {
                        if (!isDragged) {
                            position.animateTo(target, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
                        }
                    }
                    val lift by animateFloatAsState(if (isDragged) 1.06f else 1f, label = "lift")

                    Box(
                        Modifier
                            .offset { position.value }
                            .size(cellW, CARD_HEIGHT)
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer {
                                scaleX = lift
                                scaleY = lift
                                shadowElevation = if (isDragged) 18f else 0f
                                shape = RoundedCornerShape(12.dp)
                                clip = false
                            }
                            .pointerInput(wc.name) {
                                var start = IntOffset.Zero
                                var drag = Offset.Zero
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        val from = currentClasses.indexOfFirst { it.name == wc.name }
                                        if (from < 0) return@detectDragGesturesAfterLongPress
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        start = position.value
                                        drag = Offset.Zero
                                        dragFrom = from
                                        dragTarget = from
                                        draggedName = wc.name
                                        onDragActive(true)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        drag += amount
                                        val now = IntOffset(start.x + drag.x.roundToInt(), start.y + drag.y.roundToInt())
                                        scope.launch { position.snapTo(now) }
                                        val newTarget = indexAt(Offset(now.x + cellWpx / 2f, now.y + cellHpx / 2f))
                                        if (newTarget != dragTarget) {
                                            dragTarget = newTarget
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    },
                                    onDragEnd = {
                                        val from = dragFrom
                                        val to = dragTarget
                                        draggedName = null
                                        dragFrom = -1
                                        dragTarget = -1
                                        onDragActive(false)
                                        if (from >= 0 && to >= 0 && from != to) currentOnMove(from, to)
                                    },
                                    onDragCancel = {
                                        draggedName = null
                                        dragFrom = -1
                                        dragTarget = -1
                                        onDragActive(false)
                                    }
                                )
                            }
                    ) {
                        card(wc, isDragged)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassComplianceCard(
    wc: WeaponClass,
    velocity: Float,
    isActive: Boolean,
    isDragged: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val compliance = complianceOf(wc, velocity)
    val accent = if (isDark) ActiveAccentDark else ActiveAccentLight
    val green = if (isDark) Color(0xFF88FF11) else Color(0xFF1B5E20)
    val red = if (isDark) Color(0xFFF44336) else Color(0xFFC62828)
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant

    val (bgTarget, fgTarget) = when (compliance) {
        Compliance.AWAITING -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) to neutral
        Compliance.LEGAL -> Color(0xFF2E7D32).copy(alpha = if (isDark) 0.28f else 0.15f) to green
        Compliance.ILLEGAL -> Color(0xFFF44336).copy(alpha = if (isDark) 0.28f else 0.15f) to red
    }
    val bg by animateColorAsState(bgTarget, label = "bg")
    val fg by animateColorAsState(fgTarget, label = "fg")
    val borderColor = when {
        isActive || isDragged -> accent
        compliance == Compliance.LEGAL -> green.copy(alpha = 0.6f)
        compliance == Compliance.ILLEGAL -> red.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    val status = when (compliance) {
        Compliance.AWAITING -> stringResource(R.string.awaiting_shot)
        Compliance.LEGAL -> stringResource(R.string.legal) + " ✓"
        Compliance.ILLEGAL -> stringResource(R.string.illegal) + " ✕"
    }
    val detail = when (compliance) {
        Compliance.AWAITING -> "--"
        Compliance.LEGAL -> stringResource(R.string.max_bb_weight_label, maxWeightGrams(wc.maxJoules, velocity))
        Compliance.ILLEGAL -> stringResource(R.string.exceeds_limit_20)
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .border(BorderStroke(if (isActive) 2.5.dp else 1.5.dp, borderColor), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = if (isDark) Color(0xFF161B22) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(bg)   // green / red tint over the card surface
                .padding(start = 6.dp, end = 2.dp, top = 2.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Fixed-height top row: drag handle, active badge, edit button
            Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.DragIndicator,
                    contentDescription = null,
                    tint = neutral.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    if (isActive) "★ " + stringResource(R.string.active_reference) else "",
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_weapon_class), modifier = Modifier.size(16.dp), tint = neutral)
                }
            }
            // Each line has a fixed height and line count, so LEGAL ↔ ILLEGAL never resizes the card.
            Text(wc.name, color = fg, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("≤ %.2f J".format(wc.maxJoules), color = neutral, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
            Text(status, color = fg, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.fillMaxWidth().height(30.dp), contentAlignment = Alignment.Center) {
                Text(
                    detail,
                    color = fg,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun WeaponClassDialog(
    initial: WeaponClass?,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (WeaponClass) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var joules by remember { mutableStateOf(initial?.maxJoules?.let { "%.2f".format(it) } ?: "1.50") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val joulesValue = joules.replace(',', '.').toFloatOrNull()
    val valid = joulesValue != null && joulesValue in 0.1f..100f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.dialog_add_weapon_class else R.string.dialog_edit_weapon_class)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.class_name_label)) },
                    placeholder = { Text("HPA, SMG, CQB") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = joules,
                    onValueChange = { joules = it },
                    label = { Text(stringResource(R.string.max_joules_label)) },
                    suffix = { Text("J") },
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text(stringResource(R.string.description_label)) },
                    singleLine = true
                )
                if (canDelete) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.delete)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(WeaponClass(name.trim().uppercase().ifEmpty { "CUSTOM" }, joulesValue ?: 1.5f, desc.trim())) }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.confirm_delete_class, initial?.name ?: "")) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

/**
 * Joule reference grid. Shows the same weights as the Dashboard chips; the selected weight (the one
 * shots and the over-limit check use) has a star and a thick border, and tapping a tile selects it.
 * Numbers are white with a dark outline so they stay readable on red, orange and green.
 */
@Composable
fun JouleGridStatic(
    velocity: Float,
    weights: List<Pair<Float, String>>,
    practicalWeight: Float,
    columns: Int,
    isDark: Boolean,
    selectedIndex: Int = -1,
    onSelect: ((Int) -> Unit)? = null
) {
    val rows = weights.withIndex().toList().chunked(columns)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { rowWeights ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowWeights.forEach { (index, item) ->
                    val (weight, label) = item
                    val energy = 0.5f * (weight / 1000f) * velocity.pow(2)
                    val selected = index == selectedIndex

                    val (bgColor, borderColor) = when {
                        velocity <= 0f -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) to MaterialTheme.colorScheme.outline
                        weight < practicalWeight -> Color(0xFF2E7D32) to Color(0xFF88FF11)
                        weight == practicalWeight -> Color(0xFFE65100) to Color(0xFFFFCC80)
                        else -> Color(0xFFB71C1C) to Color(0xFFFF8A80)
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .then(if (onSelect != null) Modifier.clickable { onSelect(index) } else Modifier),
                        colors = CardDefaults.cardColors(containerColor = bgColor),
                        border = if (selected) BorderStroke(3.dp, borderColor) else null
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            OutlinedText(
                                if (selected) "★ $label" else label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                dim = velocity <= 0f && !isDark
                            )
                            OutlinedText(
                                if (velocity > 0f) "%.2f J".format(energy) else "-- J",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                dim = velocity <= 0f && !isDark
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

/** White text with a dark outline (dark text on the light, empty tiles). */
@Composable
private fun OutlinedText(text: String, fontSize: androidx.compose.ui.unit.TextUnit, fontWeight: FontWeight, dim: Boolean) {
    if (dim) {
        Text(text, fontSize = fontSize, fontWeight = fontWeight, color = Color(0xFF263238), maxLines = 1)
        return
    }
    Box(contentAlignment = Alignment.Center) {
        Text(
            text, fontSize = fontSize, fontWeight = fontWeight, maxLines = 1, color = Color(0xCC000000),
            style = LocalTextStyle.current.copy(drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        )
        Text(text, fontSize = fontSize, fontWeight = fontWeight, maxLines = 1, color = Color.White)
    }
}
