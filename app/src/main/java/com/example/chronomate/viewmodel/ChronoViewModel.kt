package com.example.chronomate.viewmodel

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.WindowManager
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chronomate.R
import com.example.chronomate.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlin.math.sqrt

class ChronoViewModel(context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(ChronoData())
    val uiState = _uiState.asStateFlow()

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    // This client will be swapped when the specific network is available
    @Volatile
    private var activeClient: OkHttpClient = baseClient

    /** Application context: settings are saved from places that have no Activity at hand. */
    private val appContext: Context = context.applicationContext

    private var lastSeenRawShots = emptyList<Float>()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var connectivityManager: ConnectivityManager? = null

    // --- Connection tracking -------------------------------------------------
    // The chrono is considered connected when - and only when - it answers us.
    // The Wi-Fi callback only tells us how far the *attempt* got; it is never
    // allowed to claim "connected" on its own, because the phone can stay joined
    // to the chrono AP long after the callback has given up on it (and the other
    // way round, the user may have joined the AP manually via Android settings).

    /** Address the chrono answered on, or null while we still have to look for it. */
    @Volatile
    private var chronoUrl: String? = null

    /** True while our own NetworkCallback owns the link to the chrono AP. */
    @Volatile
    private var boundToChronoAp = false

    /** Consecutive failed polls; we tolerate a few before dropping the status. */
    private var missedPolls = 0

    init {
        // Settings first: discovery needs the chrono model (Wi-Fi name) from them.
        loadPreferences(context)
        startPolling()
    }

    private fun loadPreferences(context: Context) {
        val prefs = context.getSharedPreferences("chrono_prefs", Context.MODE_PRIVATE)
        
        val customWeights = prefs.getString(KEY_CUSTOM_WEIGHTS_JSON, null)?.let { parseCustomWeights(it) }
            ?: loadLegacyCustomWeights(prefs)

        val calibrationEntriesJson = prefs.getString("calibration_entries", "[]") ?: "[]"
        val calibrationEntries = if (calibrationEntriesJson != "[]" && calibrationEntriesJson.isNotEmpty()) {
            calibrationEntriesJson.split(";").mapNotNull {
                val parts = it.split(":")
                if (parts.size >= 1) {
                    val chronoValue = parts[0].toFloatOrNull() ?: return@mapNotNull null
                    val controlValue = if (parts.size >= 2) parts[1].toFloatOrNull() else null
                    CalibrationEntry(chronoValue, controlValue)
                } else null
            }
        } else emptyList()

        val compMode = enumOr(prefs.getString("comp_mode", null), CompensationMode.NONE)
        val chronoType = enumOr(prefs.getString("chrono_type", null), ChronoType.HT_X3000)
        val weightType = enumOr(prefs.getString("weight_type", null), WeightType.BB)
        val weaponClasses = prefs.getString(KEY_WEAPON_CLASSES, null)?.let { parseWeaponClasses(it) }
            ?.takeIf { it.isNotEmpty() } ?: DEFAULT_WEAPON_CLASSES
        // Projectiles: own weights need an id and a kind (with a BC = pellet); active lists default
        // to the standard BBs / the PCP starter set; the old pellet selection becomes an id.
        val own = customWeights
        val mode = enumOr(prefs.getString(KEY_BALLISTIC_MODE, null), BallisticMode.AIRSOFT)
        val oldPellet = prefs.getString(KEY_SELECTED_PELLET, "") ?: ""
        val activeAirsoft = normalizedActive(prefs.getString(KEY_ACTIVE_AIRSOFT, null)?.let { parseIds(it) } ?: DEFAULT_ACTIVE_BB, KIND_BB, own)
        val pcpIds = prefs.getString(KEY_ACTIVE_PCP, null)?.let { parseIds(it) }
            ?: (DEFAULT_ACTIVE_PCP + listOfNotNull(oldPellet.takeIf { n -> n.isNotEmpty() && PELLETS.any { it.name == n } }?.let { "pellet:$it" }))
        val activePcp = normalizedActive(pcpIds, KIND_PELLET, own)
        val savedWeight = prefs.getFloat(KEY_SELECTED_WEIGHT, 0.20f)
        val savedItem = prefs.getString(KEY_SELECTED_ITEM, null)
            ?: if (oldPellet.isNotEmpty() && mode == BallisticMode.PCP) "pellet:$oldPellet" else ""
        val offered = (if (mode == BallisticMode.PCP) activePcp else activeAirsoft).mapNotNull { resolve(it, own) }
        val keep = optionToKeep(savedItem, savedWeight, offered)

        _uiState.update { it.copy(
            isDarkMode = prefs.getBoolean("dark_mode", true),
            keepScreenOn = prefs.getBoolean("keep_screen_on", false),
            language = prefs.getString("language", "en") ?: "en",
            maxAllowedJoule = prefs.getFloat("max_joule", 1.5f),
            maxAllowedOverhopCm = prefs.getFloat("max_overhop", 15f),
            weightType = weightType,
            customWeights = customWeights,
            selectedWeight = keep?.weight ?: savedWeight,
            selectedItem = keep?.id ?: savedItem,
            selectedAirsoft = prefs.getString(KEY_SELECTED_AIRSOFT, null)
                ?: (keep?.id?.takeIf { it.startsWith("bb:") || mode == BallisticMode.AIRSOFT } ?: "bb:0.20"),
            selectedPcp = prefs.getString(KEY_SELECTED_PCP, null)
                ?: (keep?.id?.takeIf { mode == BallisticMode.PCP } ?: ""),
            activeAirsoft = activeAirsoft,
            activePcp = activePcp,
            libraryCaliber = prefs.getString(KEY_LIBRARY_CALIBER, ".177") ?: ".177",
            weaponClasses = weaponClasses,
            selectedWeaponClass = prefs.getString(KEY_SELECTED_CLASS, "AR") ?: "AR",
            ballisticMode = mode,
            showImperial = prefs.getBoolean(KEY_SHOW_IMPERIAL, false),
            pcp = prefs.getString(KEY_PCP, null)?.let { parsePcp(it) } ?: PcpSettings(),
            environment = prefs.getString(KEY_ENVIRONMENT, null)?.let { parseEnvironment(it) } ?: EnvironmentSettings(),
            diameterMm = prefs.getFloat("diameter", 5.95f),
            airDensityRho = prefs.getFloat("air_density", 1.225f),
            dragCoefficientCw = prefs.getFloat("drag_coeff", 0.35f),
            magnusCoefficientK = prefs.getFloat("magnus_coeff", 0.002f),
            spinDampingCr = prefs.getFloat("spin_damping", 0.01f),
            gravity = prefs.getFloat("gravity", 9.81f),
            compensationMode = compMode,
            calibrationEntries = calibrationEntries,
            chronoType = chronoType
        ) }

        updateCompensationFactor()

        if (_uiState.value.keepScreenOn) {
            applyKeepScreenOn(context, true)
        }
    }

    /** Settings written by app versions before 1.0.7: "name:weight:caliber:unit;…". */
    private fun loadLegacyCustomWeights(prefs: android.content.SharedPreferences): List<CustomWeight> {
        val customWeightsJson = prefs.getString("custom_weights", "[]") ?: "[]"
        return if (customWeightsJson != "[]" && customWeightsJson.isNotEmpty()) {
            customWeightsJson.split(";").mapNotNull {
                val parts = it.split(":")
                if (parts.size >= 2) {
                    CustomWeight(
                        name = parts[0],
                        weight = parts[1].toFloatOrNull() ?: 0.20f,
                        caliber = if (parts.size >= 3) parts[2].toFloatOrNull() ?: 6.0f else 6.0f,
                        caliberUnit = if (parts.size >= 4) parts[3] else "mm"
                    )
                } else null
            }
        } else emptyList()
    }

    private fun savePreference(context: Context, key: String, value: Any) {
        val prefs = context.getSharedPreferences("chrono_prefs", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Float -> putFloat(key, value)
                is String -> putString(key, value)
            }
            apply()
        }
    }

    private fun saveCustomWeights(context: Context, weights: List<CustomWeight>) {
        // JSON, so names may contain ':' or ';' (the old "a:b;c:d" format broke on those).
        savePreference(context, KEY_CUSTOM_WEIGHTS_JSON, customWeightsToJson(weights))
    }

    private fun saveWeaponClasses(classes: List<WeaponClass>, selected: String) {
        savePreference(appContext, KEY_WEAPON_CLASSES, weaponClassesToJson(classes))
        savePreference(appContext, KEY_SELECTED_CLASS, selected)
    }

    private fun saveCalibration(context: Context) {
        val state = _uiState.value
        val serialized = state.calibrationEntries.joinToString(";") { 
            "${it.chronoValue}:${it.controlValue ?: ""}"
        }
        savePreference(context, "calibration_entries", serialized)
        savePreference(context, "comp_mode", state.compensationMode.name)
    }

    fun toggleDarkMode(context: Context, enabled: Boolean) {
        _uiState.update { it.copy(isDarkMode = enabled) }
        savePreference(context, "dark_mode", enabled)
    }

    fun toggleKeepScreenOn(context: Context, enabled: Boolean) {
        _uiState.update { it.copy(keepScreenOn = enabled) }
        savePreference(context, "keep_screen_on", enabled)
        applyKeepScreenOn(context, enabled)
    }

    private fun applyKeepScreenOn(context: Context, enabled: Boolean) {
        if (context is Activity) {
            if (enabled) {
                context.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                context.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    fun setLanguage(context: Context, langCode: String) {
        savePreference(context, "language", langCode)
        _uiState.update { it.copy(language = langCode) }
        
        if (context is Activity) {
            val intent = context.intent
            context.finish()
            context.startActivity(intent)
        } else {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    // --- Projectiles: mode, active lists, library, own projectiles, selection ----------------------

    /** One switch: Airsoft = BBs + hop-up trajectory, PCP = pellets & slugs + PCP calculator. */
    fun setMode(mode: BallisticMode) {
        if (mode == _uiState.value.ballisticMode) return
        _uiState.update { it.copy(ballisticMode = mode) }
        savePreference(appContext, KEY_BALLISTIC_MODE, mode.name)
        // bring back this mode's last pick
        val s = _uiState.value
        val back = s.resolve(if (mode == BallisticMode.PCP) s.selectedPcp else s.selectedAirsoft)
        if (back != null) _uiState.update { it.copy(selectedItem = back.id, selectedWeight = back.weight) }
        keepSelectionOffered()
    }

    /** Select a chip / grid tile / picker entry. A pellet (or own pellet with a BC) is the PCP projectile too. */
    fun selectWeightOption(opt: WeightOption) {
        // Remember it as its mode's pick; in the current mode it's the weight shots are recorded with
        _uiState.update {
            if (opt.kind == KIND_PELLET) it.copy(selectedPcp = opt.id) else it.copy(selectedAirsoft = opt.id)
        }
        savePreference(appContext, if (opt.kind == KIND_PELLET) KEY_SELECTED_PCP else KEY_SELECTED_AIRSOFT, opt.id)
        if (opt.kind == _uiState.value.modeKind()) {
            _uiState.update { it.copy(selectedItem = opt.id, selectedWeight = opt.weight) }
            savePreference(appContext, KEY_SELECTED_ITEM, opt.id)
            savePreference(appContext, KEY_SELECTED_WEIGHT, opt.weight)
        }
        val p = opt.pellet
        if (p != null) {
            updatePcp {
                it.copy(
                    projectileName = p.name, massGrams = p.massGrams, caliberMm = p.caliberMm,
                    bc = p.bc ?: it.bc,   // unknown BC: keep the user's value (measure it!)
                    dragModel = if (p.bc != null) com.example.chronomate.ballistics.DragModel.G1 else it.dragModel
                )
            }
        } else if (opt.isCustom && opt.kind == KIND_PELLET) {
            val cw = _uiState.value.customWeights.firstOrNull { "own:${it.id}" == opt.id } ?: return
            updatePcp {
                it.copy(
                    projectileName = cw.name, massGrams = cw.weight.toDouble(),
                    caliberMm = if (cw.caliberUnit == "mm") cw.caliber.toDouble() else it.caliberMm,
                    bc = cw.bc?.toDouble() ?: it.bc, dragModel = if (cw.bc != null) cw.dragModel else it.dragModel
                )
            }
        }
    }

    /** Picked on the PCP screen: from the library it also joins the active list. */
    fun pickProjectile(id: String) {
        addToActive(id)
        _uiState.value.resolve(id)?.let { selectWeightOption(it) }
    }

    private fun keepSelectionOffered() {
        val s = _uiState.value
        val opt = optionToKeep(s.selectedItem, s.selectedWeight, s.availableWeights()) ?: return
        if (opt.id != s.selectedItem || !s.isSelected(opt)) selectWeightOption(opt)
    }

    private fun setActive(kind: String, ids: List<String>) {
        val own = _uiState.value.customWeights
        val clean = normalizedActive(ids, kind, own)
        _uiState.update { if (kind == KIND_PELLET) it.copy(activePcp = clean) else it.copy(activeAirsoft = clean) }
        savePreference(appContext, if (kind == KIND_PELLET) KEY_ACTIVE_PCP else KEY_ACTIVE_AIRSOFT, JSONArray(clean).toString())
    }

    fun addToActive(id: String) {
        val opt = _uiState.value.resolve(id) ?: return
        val ids = _uiState.value.activeIds(opt.kind)
        if (id !in ids) setActive(opt.kind, ids + id)
    }

    /** Library entries leave the active list; own projectiles stay (delete them instead). */
    fun removeFromActive(id: String) {
        if (id.startsWith("own:")) return
        val opt = _uiState.value.resolve(id) ?: return
        setActive(opt.kind, _uiState.value.activeIds(opt.kind) - id)
        keepSelectionOffered()
    }

    fun moveActive(id: String, step: Int) {
        val opt = _uiState.value.resolve(id) ?: return
        val ids = _uiState.value.activeIds(opt.kind)
        val i = ids.indexOf(id)
        setActive(opt.kind, ids.moved(i, i + step))
    }

    fun setLibraryCaliber(caliber: String) {
        _uiState.update { it.copy(libraryCaliber = caliber) }
        savePreference(appContext, KEY_LIBRARY_CALIBER, caliber)
    }

    /** Create or update one of your own projectiles; it always sits in the active list of its kind. */
    fun saveOwnProjectile(cw: CustomWeight) {
        val list = _uiState.value.customWeights.toMutableList()
        val i = list.indexOfFirst { it.id == cw.id }
        if (i >= 0) list[i] = cw else list += cw
        _uiState.update { it.copy(customWeights = list) }
        saveCustomWeights(appContext, list)
        setActive(cw.kind, _uiState.value.activeIds(cw.kind))
        if (_uiState.value.selectedItem == "own:${cw.id}") _uiState.value.resolve("own:${cw.id}")?.let { selectWeightOption(it) }
    }

    fun deleteOwnProjectile(id: String) {
        val cw = _uiState.value.customWeights.firstOrNull { it.id == id } ?: return
        val list = _uiState.value.customWeights - cw
        _uiState.update { it.copy(customWeights = list) }
        saveCustomWeights(appContext, list)
        setActive(cw.kind, _uiState.value.activeIds(cw.kind) - "own:$id")
        keepSelectionOffered()
    }

    /** "Save as custom weight" on the PCP screen: the projectile (with its BC) becomes your own pellet. */
    fun savePcpAsOwnProjectile() {
        val pc = _uiState.value.pcp
        val weight = (Math.round(pc.massGrams * 1000.0) / 1000.0).toFloat()
        val existing = _uiState.value.customWeights.firstOrNull { it.kind == KIND_PELLET && it.name == pc.projectileName && sameWeight(it.weight, weight) }
        val cw = (existing ?: CustomWeight(pc.projectileName.ifBlank { "Pellet" }, weight, kind = KIND_PELLET))
            .copy(caliber = pc.caliberMm.toFloat(), caliberUnit = "mm", bc = pc.bc.toFloat(), dragModel = pc.dragModel, kind = KIND_PELLET)
        saveOwnProjectile(cw)
        _uiState.value.resolve("own:${cw.id}")?.let { selectWeightOption(it) }
    }

    fun setExportShotChoice(choice: Int) {
        _uiState.update { it.copy(exportShotChoice = choice) }
    }

    // --- Ballistic mode / PCP calculator -------------------------------------------------------

    fun setShowImperial(enabled: Boolean) {
        _uiState.update { it.copy(showImperial = enabled) }
        savePreference(appContext, KEY_SHOW_IMPERIAL, enabled)
    }

    fun updatePcp(transform: (PcpSettings) -> PcpSettings) {
        _uiState.update { it.copy(pcp = transform(it.pcp)) }
        savePreference(appContext, KEY_PCP, pcpToJson(_uiState.value.pcp))
    }

    fun updateEnvironment(transform: (EnvironmentSettings) -> EnvironmentSettings) {
        _uiState.update { it.copy(environment = transform(it.environment)) }
        savePreference(appContext, KEY_ENVIRONMENT, environmentToJson(_uiState.value.environment))
    }

    // --- Orga weapon classes ---------------------------------------------------------------

    fun selectWeaponClass(name: String) {
        _uiState.update { it.copy(selectedWeaponClass = name) }
        savePreference(appContext, KEY_SELECTED_CLASS, name)
    }

    fun addWeaponClass(wc: WeaponClass) {
        val clean = wc.copy(name = uniqueClassName(wc.name.trim().uppercase().ifEmpty { "CUSTOM" }, null))
        _uiState.update { it.copy(weaponClasses = it.weaponClasses + clean, selectedWeaponClass = clean.name) }
        saveWeaponClasses(_uiState.value.weaponClasses, clean.name)
    }

    fun updateWeaponClass(oldName: String, updated: WeaponClass) {
        val name = uniqueClassName(updated.name.trim().uppercase().ifEmpty { oldName }, oldName)
        _uiState.update { s ->
            s.copy(
                weaponClasses = s.weaponClasses.map { if (it.name == oldName) updated.copy(name = name) else it },
                selectedWeaponClass = if (s.selectedWeaponClass == oldName) name else s.selectedWeaponClass
            )
        }
        saveWeaponClasses(_uiState.value.weaponClasses, _uiState.value.selectedWeaponClass)
    }

    fun deleteWeaponClass(name: String) {
        _uiState.update { s ->
            if (s.weaponClasses.size <= 1) return@update s
            val rest = s.weaponClasses.filterNot { it.name == name }
            s.copy(
                weaponClasses = rest,
                selectedWeaponClass = if (s.selectedWeaponClass == name) rest.first().name else s.selectedWeaponClass
            )
        }
        saveWeaponClasses(_uiState.value.weaponClasses, _uiState.value.selectedWeaponClass)
    }

    fun moveWeaponClass(from: Int, to: Int) {
        _uiState.update { it.copy(weaponClasses = it.weaponClasses.moved(from, to)) }
        saveWeaponClasses(_uiState.value.weaponClasses, _uiState.value.selectedWeaponClass)
    }

    fun resetWeaponClasses() {
        _uiState.update { it.copy(weaponClasses = DEFAULT_WEAPON_CLASSES, selectedWeaponClass = "AR") }
        saveWeaponClasses(DEFAULT_WEAPON_CLASSES, "AR")
    }

    private fun uniqueClassName(wanted: String, ignore: String?): String {
        val taken = _uiState.value.weaponClasses.map { it.name }.filter { it != ignore }.toSet()
        if (wanted !in taken) return wanted
        var i = 2
        while ("$wanted $i" in taken) i++
        return "$wanted $i"
    }

    fun setChronoType(context: Context, type: ChronoType) {
        _uiState.update { it.copy(chronoType = type) }
        savePreference(context, "chrono_type", type.name)
    }

    fun setMaxAllowedJoule(joule: Float) {
        _uiState.update { it.copy(maxAllowedJoule = joule) }
        savePreference(appContext, "max_joule", joule)
    }

    fun setMaxAllowedOverhop(cm: Float) {
        _uiState.update { it.copy(maxAllowedOverhopCm = cm) }
        savePreference(appContext, "max_overhop", cm)
    }

    fun updateBallisticSettings(
        diameterMm: Float? = null,
        airDensityRho: Float? = null,
        dragCoefficientCw: Float? = null,
        magnusCoefficientK: Float? = null,
        spinDampingCr: Float? = null,
        gravity: Float? = null
    ) {
        _uiState.update { current ->
            current.copy(
                diameterMm = diameterMm ?: current.diameterMm,
                airDensityRho = airDensityRho ?: current.airDensityRho,
                dragCoefficientCw = dragCoefficientCw ?: current.dragCoefficientCw,
                magnusCoefficientK = magnusCoefficientK ?: current.magnusCoefficientK,
                spinDampingCr = spinDampingCr ?: current.spinDampingCr,
                gravity = gravity ?: current.gravity
            )
        }
        // These were loaded on start but never written, so every change was lost on restart.
        val s = _uiState.value
        savePreference(appContext, "diameter", s.diameterMm)
        savePreference(appContext, "air_density", s.airDensityRho)
        savePreference(appContext, "drag_coeff", s.dragCoefficientCw)
        savePreference(appContext, "magnus_coeff", s.magnusCoefficientK)
        savePreference(appContext, "spin_damping", s.spinDampingCr)
        savePreference(appContext, "gravity", s.gravity)
    }

    // Calibration Methods
    fun setCompensationMode(context: Context, mode: CompensationMode) {
        _uiState.update { it.copy(compensationMode = mode) }
        updateCompensationFactor()
        saveCalibration(context)
    }

    fun startCalibration() {
        _uiState.update { it.copy(isCalibrationActive = true) }
    }

    fun stopCalibration() {
        _uiState.update { it.copy(isCalibrationActive = false) }
    }

    fun updateCalibrationControlValue(context: Context, index: Int, value: Float?) {
        _uiState.update { current ->
            val newList = current.calibrationEntries.toMutableList()
            if (index in newList.indices) {
                newList[index] = newList[index].copy(controlValue = value)
            }
            current.copy(calibrationEntries = newList)
        }
        updateCompensationFactor()
        saveCalibration(context)
    }

    fun removeCalibrationEntry(context: Context, index: Int) {
        _uiState.update { current ->
            val newList = current.calibrationEntries.toMutableList()
            if (index in newList.indices) {
                newList.removeAt(index)
            }
            current.copy(calibrationEntries = newList)
        }
        updateCompensationFactor()
        saveCalibration(context)
    }

    fun clearCalibration(context: Context) {
        _uiState.update { it.copy(calibrationEntries = emptyList()) }
        updateCompensationFactor()
        saveCalibration(context)
    }

    private fun updateCompensationFactor() {
        _uiState.update { current ->
            val entriesWithControl = current.calibrationEntries.filter { it.controlValue != null && it.chronoValue > 0 }
            val factor = when (current.compensationMode) {
                CompensationMode.NONE -> 1.0f
                CompensationMode.MEDIAN -> {
                    if (entriesWithControl.isEmpty()) 1.0f
                    else {
                        val ratios = entriesWithControl.map { it.controlValue!! / it.chronoValue }
                        val medianRatio = ratios.sorted()[ratios.size / 2]
                        (1.0f + medianRatio) / 2.0f
                    }
                }
                CompensationMode.TRUST_EXTERNAL -> {
                    if (entriesWithControl.isEmpty()) 1.0f
                    else {
                        val ratios = entriesWithControl.map { it.controlValue!! / it.chronoValue }
                        ratios.sorted()[ratios.size / 2]
                    }
                }
            }
            
            // Re-apply factor to all existing shots to ensure history/stats update instantly
            val updatedShots = current.shots.map { it.copy(compensationFactor = factor) }
            
            // Recalculate stats based on updated shots
            val velocities = updatedShots.map { it.velocity }
            val avg = if (velocities.isNotEmpty()) velocities.average().toFloat() else 0f
            val max = if (velocities.isNotEmpty()) velocities.maxOrNull() ?: 0f else 0f
            val min = if (velocities.isNotEmpty()) velocities.minOrNull() ?: 0f else 0f
            val es = if (velocities.isNotEmpty()) max - min else 0f
            
            val varVal = if (velocities.size > 1) {
                val mean = velocities.average()
                velocities.sumOf { (it - mean).pow(2.0) }.toFloat() / velocities.size
            } else 0f
            val sd = sqrt(varVal)

            val latestShot = updatedShots.lastOrNull()
            val currentVelocityString = latestShot?.velocity?.let { "%.2f".format(it) } ?: "0.00"
            val currentEnergy = latestShot?.energyJoules ?: 0f

            current.copy(
                compensationFactor = factor,
                shots = updatedShots,
                averageVelocity = avg,
                maxVelocity = max,
                minVelocity = min,
                extremeSpread = es,
                standardDeviation = sd,
                variance = varVal,
                velocity = currentVelocityString,
                currentEnergy = currentEnergy
            )
        }
    }

    fun connectToChronoWifi(context: Context) {
        try {
            // Application context: the request outlives the Activity that started it.
            val connectivityManager = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            this.connectivityManager = connectivityManager

            networkCallback?.let { 
                try { connectivityManager.unregisterNetworkCallback(it) } catch (e: Exception) {}
            }

            val requestBuilder = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val chrono = _uiState.value.chronoType
                val specifier = WifiNetworkSpecifier.Builder()
                    .setSsid(chrono.ssid)
                    .setWpa2Passphrase("88888888")
                    .build()
                requestBuilder.setNetworkSpecifier(specifier)
            }

            val request = requestBuilder.build()

            // A deliberate reconnect starts a fresh link: forget the old address and
            // the shots we have already seen on the previous device.
            boundToChronoAp = false
            chronoUrl = null
            missedPolls = 0
            lastSeenRawShots = emptyList()
            setConnectionState(ConnectionState.CONNECTING)

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // 1. Bind the process (hint for the OS)
                    connectivityManager.bindProcessToNetwork(network)

                    // 2. Bind the OkHttpClient specifically (guarantee for the app)
                    // We use a custom connection pool to avoid reusing sockets from previous networks
                    activeClient = baseClient.newBuilder()
                        .socketFactory(network.socketFactory)
                        .connectionPool(okhttp3.ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                        .build()

                    boundToChronoAp = true
                    chronoUrl = null
                    missedPolls = 0

                    // The Wi-Fi is up - but that is not the same as "the chrono is
                    // talking to us". The poller promotes us to CONNECTED as soon as
                    // the device actually answers.
                    setConnectionState(ConnectionState.LINKED)
                }

                override fun onLost(network: Network) {
                    connectivityManager.bindProcessToNetwork(null)
                    activeClient = baseClient
                    boundToChronoAp = false

                    // Do NOT declare a disconnect here: Android drops this callback
                    // routinely (e.g. when the internet-less AP is de-prioritised)
                    // while the phone stays joined and the chrono keeps answering.
                    // Re-run discovery on the default network and let the poll result
                    // decide what the status is.
                    chronoUrl = null
                    missedPolls = 0
                }

                override fun onUnavailable() {
                    boundToChronoAp = false
                    setConnectionState(ConnectionState.FAILED)
                }
            }

            networkCallback = callback
            connectivityManager.requestNetwork(request, callback)
        } catch (e: Exception) {
            setConnectionState(ConnectionState.FAILED, e.message)
        }
    }

    private fun setConnectionState(state: ConnectionState, error: String? = null) {
        _uiState.update { it.copy(connectionState = state, connectionError = error) }
    }

    // --- Polling / status detection -----------------------------------------

    /**
     * Runs forever: finds the chrono, keeps reading it, and keeps the connection
     * status in sync with what is really happening on the wire.
     */
    private fun startPolling() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val target = chronoUrl
                if (target == null) {
                    discoverChrono()
                } else {
                    val html = fetch(target, activeClient)
                    if (html != null && (boundToChronoAp || looksLikeChronoPage(html))) {
                        onChronoAnswered(html)
                    } else {
                        onPollFailed()
                    }
                }

                delay(if (_uiState.value.isConnected) POLL_INTERVAL_MS else RETRY_INTERVAL_MS)
            }
        }
    }

    /**
     * Looks for the chrono on the addresses the different models are known to use.
     * A page is only accepted if it actually looks like a chronograph page, so a
     * home router on 192.168.1.1 can never be mistaken for the device.
     */
    private fun discoverChrono() {
        val probeClient = activeClient.newBuilder()
            .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()

        // Anything that replies at all, used as a fallback when we are sitting on
        // the chrono's own AP and none of the pages matched the known markers.
        var fallback: Pair<String, String>? = null

        for (host in CANDIDATE_HOSTS) {
            val candidate = "http://$host"
            val html = fetch(candidate, probeClient) ?: continue

            if (looksLikeChronoPage(html)) {
                chronoUrl = candidate
                onChronoAnswered(html)
                return
            }
            if (fallback == null) fallback = candidate to html
        }

        val (fallbackUrl, fallbackHtml) = fallback ?: run { onPollFailed(); return }

        if (boundToChronoAp) {
            // We are on the chrono's private AP - whatever answers there is it.
            chronoUrl = fallbackUrl
            onChronoAnswered(fallbackHtml)
        } else {
            onPollFailed()
        }
    }

    private fun fetch(url: String, client: OkHttpClient): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Cache-Control", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()?.takeIf { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** The chrono replied - this is the only place that reports CONNECTED. */
    private fun onChronoAnswered(html: String) {
        missedPolls = 0
        parseHtml(html)
        saveCalibrationIfDirty()
        if (_uiState.value.connectionState != ConnectionState.CONNECTED) {
            setConnectionState(ConnectionState.CONNECTED)
        }
    }

    /**
     * A poll went nowhere. Single hiccups are ignored - only after
     * [MAX_MISSED_POLLS] in a row do we downgrade the status, and then only to
     * what we can still prove (Wi-Fi link up vs. nothing at all).
     */
    private fun onPollFailed() {
        missedPolls++
        if (missedPolls < MAX_MISSED_POLLS) return

        // Address may have changed (other model, DHCP) - look for it again.
        chronoUrl = null

        val next = when {
            boundToChronoAp -> ConnectionState.LINKED
            // An association attempt is still running / already failed: those states
            // are owned by the network callback, don't overwrite them.
            _uiState.value.connectionState == ConnectionState.CONNECTING -> return
            _uiState.value.connectionState == ConnectionState.FAILED -> return
            else -> ConnectionState.DISCONNECTED
        }
        setConnectionState(next)
    }

    /**
     * Cheap sanity check that a fetched page belongs to a chronograph and not to
     * whatever else happens to serve HTTP on that address.
     */
    private fun looksLikeChronoPage(html: String): Boolean {
        val text = Jsoup.parse(html).text()
        return FIRE_RATE_REGEX.containsMatchIn(text) || CHRONO_MARKER_REGEX.containsMatchIn(text)
    }

    override fun onCleared() {
        super.onCleared()
        networkCallback?.let { callback ->
            try { connectivityManager?.unregisterNetworkCallback(callback) } catch (e: Exception) {}
        }
        connectivityManager?.bindProcessToNetwork(null)
        networkCallback = null
        connectivityManager = null
    }

    private fun parseHtml(html: String) {
        val doc = Jsoup.parse(html)
        val text = doc.text()

        val fireRate = FIRE_RATE_REGEX.find(text)?.groupValues?.get(1) ?: "0.0"

        val rawShots = SHOT_REGEX.findAll(text)
            .map { it.groupValues[2].toFloat() }
            .filter { it > 0.0f }
            .toList()

        _uiState.update { current ->
            val currentSessionShots = current.shots.toMutableList()
            val currentCalibrationEntries = current.calibrationEntries.toMutableList()
            val currentWeight = current.selectedWeight
            var updatedLastSeen = lastSeenRawShots

            if (rawShots.isNotEmpty() && rawShots != lastSeenRawShots) {
                var newShotsToAdd = emptyList<Float>()
                
                if (lastSeenRawShots.isEmpty()) {
                    newShotsToAdd = rawShots
                } else {
                    for (i in rawShots.size downTo 0) {
                        val head = rawShots.take(i)
                        if (lastSeenRawShots.size >= i && lastSeenRawShots.takeLast(i) == head) {
                            newShotsToAdd = rawShots.drop(i)
                            break
                        } else if (head.isEmpty() && i == 0) {
                            newShotsToAdd = rawShots
                        }
                    }
                }
                
                newShotsToAdd.forEach { vel ->
                    currentSessionShots.add(Shot(
                        rawVelocity = vel, 
                        weightGrams = currentWeight, 
                        compensationFactor = current.compensationFactor
                    ))
                    
                    if (current.isCalibrationActive && currentCalibrationEntries.size < 100) {
                        currentCalibrationEntries.add(CalibrationEntry(vel))
                    }
                }
                updatedLastSeen = rawShots
            }

            lastSeenRawShots = updatedLastSeen
            if (currentCalibrationEntries.size != current.calibrationEntries.size) calibrationDirty = true

            val latestShot = currentSessionShots.lastOrNull()
            val currentVelocityString = latestShot?.velocity?.let { "%.2f".format(it) } ?: "0.00"
            val currentEnergy = latestShot?.energyJoules ?: 0f

            val velocities = currentSessionShots.map { it.velocity }
            val avg = if (velocities.isNotEmpty()) velocities.average().toFloat() else 0f
            val max = if (velocities.isNotEmpty()) velocities.maxOrNull() ?: 0f else 0f
            val min = if (velocities.isNotEmpty()) velocities.minOrNull() ?: 0f else 0f
            val es = if (velocities.isNotEmpty()) max - min else 0f
            
            val varVal = if (velocities.size > 1) {
                val mean = velocities.average()
                velocities.sumOf { (it - mean).pow(2.0) }.toFloat() / velocities.size
            } else 0f
            val sd = sqrt(varVal)

            current.copy(
                velocity = currentVelocityString,
                fireRate = fireRate,
                shots = currentSessionShots,
                calibrationEntries = currentCalibrationEntries,
                averageVelocity = avg,
                maxVelocity = max,
                minVelocity = min,
                extremeSpread = es,
                standardDeviation = sd,
                variance = varVal,
                currentEnergy = currentEnergy
            )
        }
    }

    /** Set by the poller when calibration mode recorded new entries; saved outside the state update. */
    @Volatile
    private var calibrationDirty = false

    private fun saveCalibrationIfDirty() {
        if (calibrationDirty) {
            calibrationDirty = false
            saveCalibration(appContext)
        }
    }

    fun exportToPdf(context: Context, shotCount: Int, gunName: String = "") {
        viewModelScope.launch {
            val data = _uiState.value
            val shotsToExport = data.shots.takeLast(shotCount)
            
            if (shotsToExport.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No shots to export", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            withContext(Dispatchers.IO) {
                try {
                    val pdfDocument = PdfDocument()
                    var pageNumber = 1
                    var pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                    var currentPage = pdfDocument.startPage(pageInfo)
                    var currentCanvas = currentPage.canvas
                    
                    val titlePaint = Paint().apply {
                        textSize = 24f
                        isFakeBoldText = true
                        color = Color.BLACK
                    }
                    val headerPaint = Paint().apply {
                        textSize = 14f
                        isFakeBoldText = true
                        color = Color.DKGRAY
                    }
                    val bodyPaint = Paint().apply {
                        textSize = 12f
                        color = Color.BLACK
                    }
                    val labelPaint = Paint().apply {
                        textSize = 10f
                        color = Color.GRAY
                    }
                    val accentPaint = Paint().apply {
                        color = Color.parseColor("#88FF11")
                    }

                    var y = 50f
                    currentCanvas.drawRect(40f, y, 60f, y + 30f, accentPaint)
                    currentCanvas.drawText(context.getString(R.string.pdf_report_title), 70f, y + 24f, titlePaint)
                    
                    if (gunName.isNotBlank()) {
                        y += 55f
                        currentCanvas.drawText(context.getString(R.string.pdf_gun_label, gunName), 70f, y, headerPaint.apply { textSize = 18f })
                        y += 25f
                    } else {
                        y += 65f
                    }

                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    currentCanvas.drawText(context.getString(R.string.pdf_date_label, dateFormat.format(Date())), 40f, y, bodyPaint)
                    y += 20f
                    currentCanvas.drawText(context.getString(R.string.pdf_total_shots, shotsToExport.size), 40f, y, bodyPaint)

                    // Stats Section
                    y += 40f
                    currentCanvas.drawRect(40f, y, 555f, y + 2f, accentPaint)
                    y += 25f
                    currentCanvas.drawText(context.getString(R.string.report_settings), 40f, y, headerPaint.apply { textSize = 14f })

                    y += 30f
                    val col1 = 40f
                    val col2 = 230f
                    val col3 = 420f

                    val velocities = shotsToExport.map { it.velocity }
                    val avg = velocities.average().toFloat()
                    val max = velocities.maxOrNull() ?: 0f
                    val min = velocities.minOrNull() ?: 0f
                    val es = max - min
                    val mean = velocities.average()
                    val varVal = if (velocities.size > 1) velocities.sumOf { (it - mean).pow(2.0) }.toFloat() / velocities.size else 0f
                    val sd = sqrt(varVal)

                    currentCanvas.drawText("${context.getString(R.string.stat_average)}: %.1f m/s".format(avg), col1, y, bodyPaint)
                    currentCanvas.drawText("${context.getString(R.string.stat_max)}: %.1f m/s".format(max), col2, y, bodyPaint)
                    currentCanvas.drawText("${context.getString(R.string.stat_min)}: %.1f m/s".format(min), col3, y, bodyPaint)

                    y += 25f
                    currentCanvas.drawText("${context.getString(R.string.stat_es)}: %.1f m/s".format(es), col1, y, bodyPaint)
                    currentCanvas.drawText("${context.getString(R.string.stat_sd)}: %.2f m/s".format(sd), col2, y, bodyPaint)
                    currentCanvas.drawText("${context.getString(R.string.stat_rof)}: ${data.fireRate} r/m", col3, y, bodyPaint)

                    // Graph Section
                    y += 50f
                    currentCanvas.drawText(context.getString(R.string.velocity_trend), 40f, y, headerPaint)
                    y += 20f

                    val graphHeight = 150f
                    val graphWidth = 475f // Adjusted for labels
                    val graphLeft = 80f // Shifted right for Y axis labels
                    val graphTop = y

                    currentCanvas.drawRect(graphLeft, graphTop, graphLeft + graphWidth, graphTop + graphHeight, Paint().apply {
                        color = Color.LTGRAY
                        alpha = 30
                        style = Paint.Style.FILL
                    })

                    val minV = velocities.minOrNull() ?: 0f
                    val maxV = velocities.maxOrNull() ?: 0f
                    val rangeV = (maxV - minV).coerceAtLeast(1f)
                    val displayMin = (minV - rangeV * 0.2f).coerceAtLeast(0f)
                    val displayMax = maxV + rangeV * 0.2f
                    val displayRange = (displayMax - displayMin).coerceAtLeast(0.1f)

                    // Draw Chart Axis Labels
                    currentCanvas.drawText("%.1f".format(displayMax), graphLeft - 5f, graphTop + 10f, labelPaint.apply { textAlign = Paint.Align.RIGHT })
                    currentCanvas.drawText("%.1f".format(displayMin), graphLeft - 5f, graphTop + graphHeight, labelPaint)
                    currentCanvas.drawText("m/s", graphLeft - 5f, graphTop - 5f, labelPaint)
                    currentCanvas.drawText("SHOT #", graphLeft + graphWidth/2, graphTop + graphHeight + 25f, labelPaint.apply { textAlign = Paint.Align.CENTER })

                    // Draw Average Line (Orange)
                    val avgY = graphTop + graphHeight - ((avg - displayMin) / displayRange) * graphHeight
                    val avgLinePaint = Paint().apply {
                        color = Color.parseColor("#E55A16")
                        strokeWidth = 1f
                        pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
                        style = Paint.Style.STROKE
                    }
                    currentCanvas.drawLine(graphLeft, avgY, graphLeft + graphWidth, avgY, avgLinePaint)

                    val path = Path()
                    val graphPaint = Paint().apply {
                        color = Color.parseColor("#4CAF50")
                        strokeWidth = 2f
                        style = Paint.Style.STROKE
                    }

                    shotsToExport.forEachIndexed { index, shot ->
                        val x = graphLeft + (index.toFloat() / (shotsToExport.size - 1).coerceAtLeast(1)) * graphWidth
                        val py = graphTop + graphHeight - ((shot.velocity - displayMin) / displayRange) * graphHeight

                        if (index == 0) path.moveTo(x, py) else path.lineTo(x, py)
                        currentCanvas.drawCircle(x, py, 3f, Paint().apply { color = Color.parseColor("#2E7D32") })
                    }
                    currentCanvas.drawPath(path, graphPaint)

                    y += graphHeight + 45f

                    // Table Header
                    currentCanvas.drawRect(40f, y, 555f, y + 20f, Paint().apply { color = Color.LTGRAY; alpha = 50 })
                    currentCanvas.drawText("#", 50f, y + 15f, headerPaint)
                    currentCanvas.drawText("${context.getString(R.string.weight_name_label)} (g)", 100f, y + 15f, headerPaint)
                    currentCanvas.drawText(context.getString(R.string.pdf_velocity_header), 250f, y + 15f, headerPaint)
                    currentCanvas.drawText(context.getString(R.string.pdf_energy_header), 450f, y + 15f, headerPaint)

                    y += 35f
                    // Now showing shots in chronological order (oldest first)
                    for (index in shotsToExport.indices) {
                        // Pagination check
                        if (y > 780f) {
                            pdfDocument.finishPage(currentPage)
                            pageNumber++
                            pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                            currentPage = pdfDocument.startPage(pageInfo)
                            currentCanvas = currentPage.canvas
                            y = 50f

                            // Redraw table header on new page
                            currentCanvas.drawRect(40f, y, 555f, y + 20f, Paint().apply { color = Color.LTGRAY; alpha = 50 })
                            currentCanvas.drawText("#", 50f, y + 15f, headerPaint)
                            currentCanvas.drawText("${context.getString(R.string.weight_name_label)} (g)", 100f, y + 15f, headerPaint)
                            currentCanvas.drawText(context.getString(R.string.pdf_velocity_header), 250f, y + 15f, headerPaint)
                            currentCanvas.drawText(context.getString(R.string.pdf_energy_header), 450f, y + 15f, headerPaint)
                            y += 35f
                        }

                        val shot = shotsToExport[index]
                        // shotNum relative to the session
                        val shotNum = (data.shots.size - shotsToExport.size) + index + 1
                        currentCanvas.drawText("%02d".format(shotNum), 50f, y, bodyPaint)
                        currentCanvas.drawText("%.2f".format(shot.weightGrams), 100f, y, bodyPaint)
                        currentCanvas.drawText("%.1f".format(shot.velocity), 250f, y, bodyPaint)
                        currentCanvas.drawText("%.2f".format(shot.energyJoules), 450f, y, bodyPaint)
                        y += 20f
                    }

                    pdfDocument.finishPage(currentPage)

                    // Modern way to save to Downloads using MediaStore
                    val cleanGunName = gunName.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
                    val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                    val fileName = if (cleanGunName.isNotBlank()) {
                        "${cleanGunName}_$dateStr.pdf"
                    } else {
                        "ChronoMate_Report_$dateStr.pdf"
                    }
                    
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ChronoMate")
                        }
                    }

                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { outputStream ->
                            pdfDocument.writeTo(outputStream)
                        }
                        pdfDocument.close()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "PDF saved: $fileName", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        throw Exception("Could not create PDF file in Downloads")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private companion object {
        const val KEY_CUSTOM_WEIGHTS_JSON = "custom_weights_json"
        const val KEY_WEAPON_CLASSES = "weapon_classes_json"
        const val KEY_SELECTED_CLASS = "selected_weapon_class"
        const val KEY_SELECTED_WEIGHT = "selected_weight"
        const val KEY_BALLISTIC_MODE = "ballistic_mode"
        const val KEY_SELECTED_PELLET = "selected_pellet"   // before the library: migrated
        const val KEY_SELECTED_ITEM = "selected_item"
        const val KEY_SELECTED_AIRSOFT = "selected_airsoft"
        const val KEY_SELECTED_PCP = "selected_pcp"
        const val KEY_ACTIVE_AIRSOFT = "active_airsoft_json"
        const val KEY_ACTIVE_PCP = "active_pcp_json"
        const val KEY_LIBRARY_CALIBER = "library_caliber"

        fun parseIds(json: String): List<String>? = try {
            val arr = JSONArray(json); (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) { null }
        const val KEY_SHOW_IMPERIAL = "show_imperial"
        const val KEY_PCP = "pcp_settings_json"
        const val KEY_ENVIRONMENT = "environment_json"

        fun pcpToJson(p: PcpSettings): String = JSONObject()
            .put("projectileName", p.projectileName).put("massGrams", p.massGrams).put("caliberMm", p.caliberMm)
            .put("bc", p.bc).put("dragModel", p.dragModel.name).put("velocitySource", p.velocitySource.name)
            .put("manualVelocityMps", p.manualVelocityMps).put("zeroRangeM", p.zeroRangeM)
            .put("sightHeightCm", p.sightHeightCm).put("clickUnit", p.clickUnit.name)
            .put("shotAngleDeg", p.shotAngleDeg).put("killZoneCm", p.killZoneCm).put("stepM", p.stepM)
            .put("maxRangeM", p.maxRangeM).put("energyLimitJ", p.energyLimitJ)
            .put("targetDistanceM", p.targetDistanceM).toString()

        fun parsePcp(json: String): PcpSettings? = try {
            val o = JSONObject(json)
            val d = PcpSettings()
            PcpSettings(
                projectileName = o.optString("projectileName", d.projectileName),
                massGrams = o.optDouble("massGrams", d.massGrams),
                caliberMm = o.optDouble("caliberMm", d.caliberMm),
                bc = o.optDouble("bc", d.bc),
                dragModel = enumOr(o.optString("dragModel"), d.dragModel),
                velocitySource = enumOr(o.optString("velocitySource"), d.velocitySource),
                manualVelocityMps = o.optDouble("manualVelocityMps", d.manualVelocityMps),
                zeroRangeM = o.optDouble("zeroRangeM", d.zeroRangeM),
                sightHeightCm = o.optDouble("sightHeightCm", d.sightHeightCm),
                clickUnit = enumOr(o.optString("clickUnit"), d.clickUnit),
                shotAngleDeg = o.optDouble("shotAngleDeg", d.shotAngleDeg),
                killZoneCm = o.optDouble("killZoneCm", d.killZoneCm),
                stepM = o.optDouble("stepM", d.stepM),
                maxRangeM = o.optDouble("maxRangeM", d.maxRangeM),
                energyLimitJ = o.optDouble("energyLimitJ", d.energyLimitJ),
                targetDistanceM = o.optDouble("targetDistanceM", d.targetDistanceM)
            )
        } catch (e: Exception) { null }

        fun environmentToJson(e: EnvironmentSettings): String = JSONObject()
            .put("temperatureC", e.temperatureC).put("pressureHpa", e.pressureHpa).put("humidityPct", e.humidityPct)
            .put("altitudeM", e.altitudeM).put("useAltitude", e.useAltitude).put("windSpeedMps", e.windSpeedMps)
            .put("windClock", e.windClock).toString()

        fun parseEnvironment(json: String): EnvironmentSettings? = try {
            val o = JSONObject(json)
            val d = EnvironmentSettings()
            EnvironmentSettings(
                temperatureC = o.optDouble("temperatureC", d.temperatureC),
                pressureHpa = o.optDouble("pressureHpa", d.pressureHpa),
                humidityPct = o.optDouble("humidityPct", d.humidityPct),
                altitudeM = o.optDouble("altitudeM", d.altitudeM),
                useAltitude = o.optBoolean("useAltitude", d.useAltitude),
                windSpeedMps = o.optDouble("windSpeedMps", d.windSpeedMps),
                windClock = o.optDouble("windClock", d.windClock)
            )
        } catch (e: Exception) { null }

        inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
            name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: fallback

        fun customWeightsToJson(list: List<CustomWeight>): String = JSONArray().apply {
            list.forEach {
                put(JSONObject().put("name", it.name).put("weight", it.weight.toDouble())
                    .put("caliber", it.caliber.toDouble()).put("caliberUnit", it.caliberUnit)
                    .put("bc", it.bc?.toDouble() ?: JSONObject.NULL).put("dragModel", it.dragModel.name)
                    .put("id", it.id).put("kind", it.kind))
            }
        }.toString()

        fun parseCustomWeights(json: String): List<CustomWeight>? = try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CustomWeight(
                    name = o.optString("name", "Custom"),
                    weight = o.optDouble("weight", 0.20).toFloat(),
                    caliber = o.optDouble("caliber", 6.0).toFloat(),
                    caliberUnit = o.optString("caliberUnit", "mm"),
                    bc = if (o.isNull("bc")) null else o.optDouble("bc").toFloat().takeIf { !it.isNaN() },
                    dragModel = enumOr(o.optString("dragModel"), com.example.chronomate.ballistics.DragModel.G1)
                ).let { cw ->
                    // weights saved before the library have no id / kind yet
                    cw.copy(
                        id = o.optString("id").ifEmpty { cw.id },
                        kind = o.optString("kind").ifEmpty { if (cw.bc != null) KIND_PELLET else KIND_BB }
                    )
                }
            }
        } catch (e: Exception) { null }

        fun weaponClassesToJson(list: List<WeaponClass>): String = JSONArray().apply {
            list.forEach {
                put(JSONObject().put("name", it.name).put("maxJoules", it.maxJoules.toDouble())
                    .put("description", it.description))
            }
        }.toString()

        fun parseWeaponClasses(json: String): List<WeaponClass>? = try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                WeaponClass(o.optString("name", "AR"), o.optDouble("maxJoules", 1.5).toFloat(), o.optString("description", ""))
            }
        } catch (e: Exception) { null }

        /** Addresses the supported chrono models are known to serve their page on. */
        val CANDIDATE_HOSTS = listOf("8.8.8.8", "192.168.4.1", "192.168.1.1", "192.168.0.1")

        const val POLL_INTERVAL_MS = 1000L
        const val RETRY_INTERVAL_MS = 3000L
        const val PROBE_TIMEOUT_MS = 1500L

        /** Failed polls tolerated before the status is downgraded. */
        const val MAX_MISSED_POLLS = 3

        val FIRE_RATE_REGEX = "(?i)(?:firerate|fire rate|射速)[^0-9]*(\\d+(?:\\.\\d+)?)".toRegex()
        val SHOT_REGEX = "(\\d{2})\\s*[:：]\\s*(\\d+(?:\\.\\d+)?)".toRegex()

        /** Markers that only a chronograph page will carry. */
        val CHRONO_MARKER_REGEX = "(?i)(m/s|fps|ft/s|速度|射速|chrono)".toRegex()
    }
}
