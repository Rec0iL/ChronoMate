package com.example.chronomate.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chronomate.R
import com.example.chronomate.ballistics.Atmosphere
import com.example.chronomate.ballistics.DragModel
import com.example.chronomate.ballistics.KillZoneZero
import com.example.chronomate.ballistics.PcpConstants
import com.example.chronomate.ballistics.PcpEngine
import com.example.chronomate.ballistics.PcpParams
import com.example.chronomate.ballistics.PcpPoint
import com.example.chronomate.ballistics.PcpResult
import com.example.chronomate.ballistics.PcpWarning
import com.example.chronomate.model.ChronoData
import com.example.chronomate.model.ClickUnit
import com.example.chronomate.model.PCP_PRESETS
import com.example.chronomate.model.VelocitySource
import com.example.chronomate.viewmodel.ChronoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private const val M_TO_YD = 1.0936133

/**
 * PCP airgun calculator (Settings → Ballistic model → PCP). Same engine and same numbers as the
 * desktop app: trajectory against the line of sight, range card, wind, weather, kill zone, BC tool.
 */
@Composable
fun PcpBallisticsScreen(data: ChronoData, viewModel: ChronoViewModel) {
    val pc = data.pcp
    val env = data.environment
    val imp = data.showImperial
    val engine = remember { PcpEngine() }

    val muzzleV = when (pc.velocitySource) {
        VelocitySource.LATEST -> data.shots.lastOrNull()?.velocity?.toDouble()
        VelocitySource.AVERAGE -> data.shots.takeIf { it.isNotEmpty() }?.map { it.velocity.toDouble() }?.average()
        VelocitySource.MANUAL -> null
    } ?: pc.manualVelocityMps

    val params = PcpParams(
        massGrams = pc.massGrams, caliberMm = pc.caliberMm, bc = pc.bc, dragModel = pc.dragModel,
        muzzleVelocityMps = muzzleV, sightHeightCm = pc.sightHeightCm, zeroRangeM = pc.zeroRangeM,
        shotAngleDeg = pc.shotAngleDeg, windSpeedMps = env.windSpeedMps, windClock = env.windClock,
        maxRangeM = pc.maxRangeM, stepM = pc.stepM, killZoneCm = pc.killZoneCm, gravity = data.gravity.toDouble()
    )
    val atm = Atmosphere(env.temperatureC, env.pressureHpa, env.humidityPct, env.altitudeM, env.useAltitude)

    // The solver runs off the main thread; the screen keeps the last result while it works.
    val result by produceState<PcpResult?>(null, params, atm) {
        value = withContext(Dispatchers.Default) { engine.solve(params, atm) }
    }
    val bestZero by produceState<KillZoneZero?>(null, result) {
        val r = result ?: return@produceState
        value = withContext(Dispatchers.Default) { engine.bestZeroForKillZone(params, atm, r) }
    }
    var probe by remember { mutableStateOf<PcpPoint?>(null) }

    fun v(mps: Double) = "%.1f m/s".format(mps) + if (imp) " (%.0f fps)".format(mps * PcpConstants.MPS_TO_FPS) else ""
    fun e(j: Double) = "%.2f J".format(j) + if (imp) " (%.1f ft-lb)".format(j * PcpConstants.J_TO_FTLB) else ""
    fun d(m: Double?) = if (m == null) "--" else "%.1f m".format(m) + if (imp) " (%.1f yd)".format(m * M_TO_YD) else ""

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(stringResource(R.string.pcp_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        result?.let { r ->
            Text(
                stringResource(R.string.pcp_atmo_line, v(muzzleV), r.muzzleMach, r.airDensity, r.speedOfSoundMps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))

        PcpChart(result, pc.maxRangeM, pc.killZoneCm, data.isDarkMode, onProbe = { probe = it })
        Text(
            probe?.let { p ->
                stringResource(
                    R.string.pcp_probe_line, d(p.rangeM), p.dropCm, p.holdMrad(),
                    p.holdMrad() / pc.clickUnit.mradPerClick, v(p.velocityMps), e(p.energyJ), p.timeS
                )
            } ?: stringResource(R.string.pcp_probe_hint),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 2
        )
        Spacer(Modifier.height(8.dp))

        // Summary
        result?.let { r ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                Column(Modifier.padding(12.dp)) {
                    val zp = r.pointAt(pc.zeroRangeM)
                    Row { Stat(stringResource(R.string.pcp_stat_me), e(r.muzzleEnergyJ), Modifier.weight(1f)); Stat(stringResource(R.string.pcp_stat_zero_e), zp?.let { e(it.energyJ) } ?: "--", Modifier.weight(1f)) }
                    Row { Stat(stringResource(R.string.pcp_stat_near), d(r.nearZeroM), Modifier.weight(1f)); Stat(stringResource(R.string.pcp_stat_far), d(r.farZeroM), Modifier.weight(1f)) }
                    Row {
                        Stat(stringResource(R.string.pcp_stat_maxord), if (r.points.isNotEmpty()) "%+.1f cm @ %.0f m".format(r.maxOrdinateCm, r.maxOrdinateRangeM) else "--", Modifier.weight(1f))
                        Stat(stringResource(R.string.pcp_stat_kz), if (r.killZoneFromM != null && r.killZoneToM != null) "%.0f–%.0f m".format(r.killZoneFromM, r.killZoneToM) else "--", Modifier.weight(1f))
                    }
                    bestZero?.let { b ->
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.pcp_best_zero, pc.killZoneCm, b.zeroM, b.fromM, b.toM),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                enabled = abs(b.zeroM - pc.zeroRangeM) >= 0.5,
                                onClick = { viewModel.updatePcp { it.copy(zeroRangeM = b.zeroM) } }
                            ) { Text(stringResource(R.string.pcp_use_best_zero)) }
                        }
                    }
                }
            }

            val warnings = buildList {
                if (PcpWarning.TRANSONIC in r.warnings) add(stringResource(R.string.pcp_warn_transonic, r.muzzleMach))
                if (PcpWarning.SHORT in r.warnings) add(stringResource(R.string.pcp_warn_short))
                if (PcpWarning.OVERHOP_KILL_ZONE in r.warnings) add(stringResource(R.string.pcp_warn_overhop_kz, r.maxOrdinateCm, pc.killZoneCm / 2))
                if (pc.energyLimitJ > 0 && r.muzzleEnergyJ > pc.energyLimitJ) add(stringResource(R.string.pcp_warn_energy, e(r.muzzleEnergyJ), e(pc.energyLimitJ)))
            }
            warnings.forEach { w ->
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Warning, null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(w, color = Color(0xFFFF9800), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // --- Projectile ---
        Section(stringResource(R.string.pcp_card_projectile)) {
            ProjectilePicker(data, onPreset = { pr ->
                viewModel.updatePcp {
                    it.copy(projectileName = pr.name, massGrams = pr.massGrams, caliberMm = pr.caliberMm, bc = pr.bc, dragModel = pr.dragModel)
                }
            }, onCustom = { viewModel.usePcpCustomWeight(it) })
            Text(pc.projectileName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            NumberField(stringResource(R.string.pcp_mass), pc.massGrams, "g", 3, 0.1..20.0,
                supporting = "= %.2f gr".format(pc.massGrams / PcpConstants.GRAIN_TO_GRAM)) { x -> viewModel.updatePcp { it.copy(massGrams = x) } }
            NumberField(stringResource(R.string.pcp_caliber), pc.caliberMm, "mm", 2, 4.0..12.0) { x -> viewModel.updatePcp { it.copy(caliberMm = x) } }
            NumberField(stringResource(R.string.pcp_bc), pc.bc, "", 3, 0.005..1.0) { x -> viewModel.updatePcp { it.copy(bc = x) } }
            Segmented(stringResource(R.string.pcp_drag_model), DragModel.entries.map { it.name }, pc.dragModel.ordinal) { i ->
                viewModel.updatePcp { it.copy(dragModel = DragModel.entries[i]) }
            }
            OutlinedButton(onClick = { viewModel.savePcpAsCustomWeight() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pcp_save_custom))
            }
            Text(stringResource(R.string.pcp_preset_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // --- Rifle & shot ---
        Section(stringResource(R.string.pcp_card_shot)) {
            Segmented(
                stringResource(R.string.pcp_velocity_source),
                listOf(stringResource(R.string.pcp_v_latest), stringResource(R.string.pcp_v_average), stringResource(R.string.pcp_v_manual)),
                pc.velocitySource.ordinal
            ) { i -> viewModel.updatePcp { it.copy(velocitySource = VelocitySource.entries[i]) } }
            if (pc.velocitySource == VelocitySource.MANUAL) {
                NumberField(stringResource(R.string.pcp_velocity), pc.manualVelocityMps, "m/s", 1, 30.0..450.0,
                    supporting = if (imp) "%.0f fps".format(pc.manualVelocityMps * PcpConstants.MPS_TO_FPS) else null) { x -> viewModel.updatePcp { it.copy(manualVelocityMps = x) } }
            } else {
                Text(v(muzzleV), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            NumberField(stringResource(R.string.pcp_zero), pc.zeroRangeM, "m", 0, 5.0..150.0) { x -> viewModel.updatePcp { it.copy(zeroRangeM = x) } }
            NumberField(stringResource(R.string.pcp_sight_height), pc.sightHeightCm, "cm", 1, 0.0..15.0) { x -> viewModel.updatePcp { it.copy(sightHeightCm = x) } }
            Segmented(stringResource(R.string.pcp_click), ClickUnit.entries.map { it.label }, pc.clickUnit.ordinal) { i ->
                viewModel.updatePcp { it.copy(clickUnit = ClickUnit.entries[i]) }
            }
            NumberField(stringResource(R.string.pcp_shot_angle), pc.shotAngleDeg, "°", 0, -60.0..60.0) { x -> viewModel.updatePcp { it.copy(shotAngleDeg = x) } }
            NumberField(stringResource(R.string.pcp_kill_zone), pc.killZoneCm, "cm", 1, 0.5..20.0) { x -> viewModel.updatePcp { it.copy(killZoneCm = x) } }
            NumberField(stringResource(R.string.pcp_step), pc.stepM, "m", 0, 1.0..25.0) { x -> viewModel.updatePcp { it.copy(stepM = x) } }
            NumberField(stringResource(R.string.pcp_max_range), pc.maxRangeM, "m", 0, 10.0..250.0) { x -> viewModel.updatePcp { it.copy(maxRangeM = x) } }
            NumberField(stringResource(R.string.pcp_energy_limit), pc.energyLimitJ, "J", 1, 0.0..200.0,
                supporting = stringResource(R.string.pcp_energy_limit_tip)) { x -> viewModel.updatePcp { it.copy(energyLimitJ = x) } }
        }

        // --- Weather & wind ---
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

        // --- BC from the chrono ---
        Section(stringResource(R.string.pcp_card_bc)) {
            Text(stringResource(R.string.pcp_bc_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            var v1 by rememberSaveable { mutableStateOf(274.0) }
            var d1 by rememberSaveable { mutableStateOf(1.0) }
            var v2 by rememberSaveable { mutableStateOf(250.0) }
            var d2 by rememberSaveable { mutableStateOf(20.0) }
            var bcResult by remember { mutableStateOf<Double?>(null) }
            var bcTried by remember { mutableStateOf(false) }
            NumberField(stringResource(R.string.pcp_bc_v1), v1, "m/s", 1, 30.0..450.0) { v1 = it }
            NumberField(stringResource(R.string.pcp_bc_d1), d1, "m", 1, 0.0..50.0) { d1 = it }
            NumberField(stringResource(R.string.pcp_bc_v2), v2, "m/s", 1, 30.0..450.0) { v2 = it }
            NumberField(stringResource(R.string.pcp_bc_d2), d2, "m", 1, 1.0..150.0) { d2 = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = data.shots.size >= 2,
                    onClick = { v1 = data.shots[data.shots.size - 2].velocity.toDouble(); v2 = data.shots.last().velocity.toDouble() },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.pcp_bc_take_last_two), maxLines = 1) }
                Button(onClick = { bcResult = engine.solveBc(params, atm, v1, d1, v2, d2); bcTried = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pcp_bc_calc), maxLines = 1)
                }
            }
            if (bcTried) {
                val bc = bcResult
                if (bc == null) {
                    Text(stringResource(R.string.pcp_bc_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.pcp_bc_result, bc, pc.dragModel.name), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.updatePcp { it.copy(bc = Math.round(bc * 1000.0) / 1000.0) } }) { Text(stringResource(R.string.pcp_bc_use)) }
                    }
                }
            }
        }

        // --- Range card ---
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.pcp_range_card), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        result?.let { RangeCardTable(it, pc.clickUnit, pc.zeroRangeM, imp) }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
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
private fun NumberField(
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

@Composable
private fun Segmented(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size)
            ) { Text(o, maxLines = 1, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun ProjectilePicker(
    data: ChronoData,
    onPreset: (com.example.chronomate.model.PcpPreset) -> Unit,
    onCustom: (Int) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pcp_pick_projectile), modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            data.customWeights.forEachIndexed { i, cw ->
                DropdownMenuItem(
                    text = { Text("★ ${cw.name} (%.3f g%s)".format(cw.weight, cw.bc?.let { ", BC %.3f".format(it) } ?: "")) },
                    onClick = { open = false; onCustom(i) }
                )
            }
            if (data.customWeights.isNotEmpty()) HorizontalDivider()
            PCP_PRESETS.forEach { pr ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(pr.name)
                            Text(
                                "%s gr / %.3f g · BC ≈%.3f · %s".format(pr.grains.toString().removeSuffix(".0"), pr.massGrams, pr.bc, pr.source),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = { open = false; onPreset(pr) }
                )
            }
        }
    }
}

@Composable
private fun RangeCardTable(r: PcpResult, click: ClickUnit, zero: Double, imp: Boolean) {
    val cols: List<Pair<String, Dp>> = listOf(
        stringResource(R.string.pcp_col_range) to 74.dp, stringResource(R.string.pcp_col_drop) to 74.dp,
        "MRAD" to 56.dp, "MOA" to 56.dp, stringResource(R.string.pcp_col_clicks) to 58.dp,
        stringResource(R.string.pcp_col_drift) to 76.dp, stringResource(R.string.pcp_col_drift_clicks) to 62.dp,
        stringResource(R.string.pcp_col_velocity) to (if (imp) 120.dp else 74.dp),
        stringResource(R.string.pcp_col_energy) to (if (imp) 120.dp else 70.dp),
        stringResource(R.string.pcp_col_time) to 58.dp
    )
    fun clicks(n: Double, plus: String, minus: String): String {
        val k = n.roundToInt()
        return if (k == 0) "0" else "${abs(k)} ${if (k > 0) plus else minus}"
    }
    val zeroColor = MaterialTheme.colorScheme.tertiary
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 6.dp)) {
            cols.forEach { (h, w) -> Text(h, Modifier.width(w).padding(horizontal = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
        }
        r.rangeCard.forEach { pt ->
            val hold = pt.holdMrad()
            val drift = pt.driftMrad()
            val cells = listOf(
                "%.0f m".format(pt.rangeM) + if (imp) "\n%.0f yd".format(pt.rangeM * M_TO_YD) else "",
                (if (abs(pt.dropCm) < 0.05) "0.0" else "%+.1f".format(pt.dropCm)) + " cm" + if (imp) "\n%+.1f\"".format(pt.dropCm / 2.54) else "",
                if (abs(hold) < 0.005) "0.00" else "%+.2f".format(hold),
                if (abs(hold) < 0.005) "0.00" else "%+.2f".format(hold / PcpConstants.MRAD_PER_MOA),
                clicks(hold / click.mradPerClick, "↑", "↓"),
                if (abs(pt.driftCm) < 0.05) "0.0 cm" else "%.1f cm %s".format(abs(pt.driftCm), if (pt.driftCm < 0) "L" else "R"),
                clicks(drift / click.mradPerClick, "R", "L"),
                "%.1f m/s".format(pt.velocityMps) + if (imp) "\n%.0f fps".format(pt.velocityMps * PcpConstants.MPS_TO_FPS) else "",
                "%.2f J".format(pt.energyJ) + if (imp) "\n%.1f ft-lb".format(pt.energyJ * PcpConstants.J_TO_FTLB) else "",
                "%.3f s".format(pt.timeS)
            )
            val isZero = abs(pt.rangeM - zero) < 0.01
            Row(Modifier.padding(vertical = 4.dp)) {
                cells.forEachIndexed { i, c ->
                    Text(
                        c, Modifier.width(cols[i].second).padding(horizontal = 4.dp),
                        fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 14.sp,
                        color = if (isZero) zeroColor else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
    }
}

@Composable
private fun PcpChart(result: PcpResult?, maxRange: Double, killZoneCm: Double, isDark: Boolean, onProbe: (PcpPoint?) -> Unit) {
    val accent = if (isDark) Color(0xFF88FF11) else Color(0xFF2E7D32)
    val cyan = if (isDark) Color(0xFF00E5FF) else Color(0xFF00838F)
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    var probeX by remember { mutableStateOf<Double?>(null) }
    val currentResult by rememberUpdatedState(result)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(if (isDark) Color(0xFF0B0E14) else Color.White, RoundedCornerShape(10.dp))
            .pointerInput(maxRange) {
                fun probe(x: Float) {
                    val left = 48f
                    val w = size.width - left - 12f
                    val r = ((x - left) / w * maxRange).coerceIn(0.0, maxRange)
                    probeX = r
                    onProbe(currentResult?.pointAt(r))
                }
                detectTapGestures { probe(it.x) }
            }
            .pointerInput(maxRange) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val left = 48f
                    val w = size.width - left - 12f
                    val r = ((change.position.x - left) / w * maxRange).coerceIn(0.0, maxRange)
                    probeX = r
                    onProbe(currentResult?.pointAt(r))
                }
            }
    ) {
        val r = result ?: return@Canvas
        if (r.points.isEmpty()) return@Canvas
        val left = 48f
        val top = 12f
        val w = size.width - left - 12f
        val h = size.height - top - 28f
        val drops = r.points.filter { it.rangeM <= maxRange }.map { it.dropCm }
        val lo0 = minOf(drops.minOrNull() ?: 0.0, -killZoneCm)
        val hi0 = maxOf(drops.maxOrNull() ?: 0.0, killZoneCm)
        val pad = maxOf(1.0, (hi0 - lo0) * 0.08)
        val lo = lo0 - pad
        val hi = hi0 + pad
        fun px(x: Double) = left + (x / maxRange * w).toFloat()
        fun py(y: Double) = top + h - ((y - lo) / (hi - lo) * h).toFloat()
        fun nice(span: Double, ticks: Int): Double {
            val raw = span / ticks
            val mag = 10.0.pow(floor(log10(raw)))
            return listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { raw <= it * mag } * mag
        }
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.argb((textColor.alpha * 255).toInt(), (textColor.red * 255).toInt(), (textColor.green * 255).toInt(), (textColor.blue * 255).toInt()); textSize = 24f; isAntiAlias = true }
        val xs = nice(maxRange, 5)
        var gx = 0.0
        while (gx <= maxRange + 1e-6) {
            drawLine(grid, Offset(px(gx), top), Offset(px(gx), top + h))
            paint.textAlign = android.graphics.Paint.Align.CENTER
            drawContext.canvas.nativeCanvas.drawText("%.0f".format(gx), px(gx), top + h + 24f, paint)
            gx += xs
        }
        val ys = nice(hi - lo, 5)
        var gy = ceil(lo / ys) * ys
        while (gy <= hi) {
            drawLine(grid, Offset(left, py(gy)), Offset(left + w, py(gy)))
            paint.textAlign = android.graphics.Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText("%+.0f".format(gy), left - 6f, py(gy) + 8f, paint)
            gy += ys
        }
        // kill zone band + line of sight
        drawRect(cyan.copy(alpha = 0.12f), Offset(left, py(killZoneCm / 2)), androidx.compose.ui.geometry.Size(w, py(-killZoneCm / 2) - py(killZoneCm / 2)))
        drawLine(cyan, Offset(left, py(0.0)), Offset(left + w, py(0.0)), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        // path
        val path = Path()
        var first = true
        r.points.forEach { p ->
            if (p.rangeM > maxRange) return@forEach
            if (first) { path.moveTo(px(p.rangeM), py(p.dropCm)); first = false } else path.lineTo(px(p.rangeM), py(p.dropCm))
        }
        drawPath(path, accent, style = Stroke(width = 4f))
        listOf(r.nearZeroM, r.farZeroM).forEach { z ->
            if (z != null && z <= maxRange) drawCircle(cyan, 7f, Offset(px(z), py(0.0)), style = Stroke(3f))
        }
        probeX?.let { x ->
            r.pointAt(x)?.let { p ->
                drawLine(Color(0xFFFFB74D), Offset(px(x), top), Offset(px(x), top + h), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
                drawCircle(Color(0xFFFFB74D), 9f, Offset(px(x), py(p.dropCm)))
            }
        }
    }
}
