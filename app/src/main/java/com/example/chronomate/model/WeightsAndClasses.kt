package com.example.chronomate.model

import kotlin.math.abs
import kotlin.math.pow

// ---------------------------------------------------------------------------------------------
// Projectiles: library + active lists (mirrors core/weights.py on the desktop).
// The mode (Airsoft / PCP) picks the kind: Airsoft = BBs, PCP = pellets & slugs. Each mode has an
// ordered active list (what the chips show) made of library entries plus the user's own projectiles
// of that kind, which are always in it. Ids: "bb:0.25", "pellet:<catalogue name>", "own:<id>".
// ---------------------------------------------------------------------------------------------

/** The 10 classic airsoft BB weights (Orga reference grid, tests). */
val STANDARD_BB_WEIGHTS = listOf(0.20f, 0.23f, 0.25f, 0.28f, 0.30f, 0.32f, 0.36f, 0.40f, 0.43f, 0.45f)

const val KIND_BB = "BB"
const val KIND_PELLET = "PELLET"

val BB_LIBRARY = listOf(0.12f, 0.20f, 0.23f, 0.25f, 0.28f, 0.30f, 0.32f, 0.33f, 0.36f, 0.40f, 0.43f, 0.45f, 0.46f, 0.48f)
val DEFAULT_ACTIVE_BB = STANDARD_BB_WEIGHTS.map { bbId(it) }
/** Starter set for PCP: the common .177 and .22 pellets on the German market. */
val DEFAULT_ACTIVE_PCP = listOf(
    "JSB Exact .177", "JSB Exact Heavy .177", "H&N Field Target Trophy .177", "H&N Baracuda Match .177",
    "RWS Superdome .177", "JSB Exact Jumbo .22", "JSB Exact Jumbo Heavy .22",
    "H&N Field Target Trophy .22", "H&N Baracuda Match .22", "RWS Superdome .22"
).map { "pellet:$it" }

private const val WEIGHT_MATCH_TOLERANCE = 0.0005f

fun bbId(w: Float) = "bb:" + "%.2f".format(java.util.Locale.ROOT, w)

data class WeightOption(
    val id: String,
    val weight: Float,
    val label: String,
    val kind: String = KIND_BB,
    /** The user's own projectile. */
    val isCustom: Boolean = false,
    val pellet: Pellet? = null
)

fun sameWeight(a: Float, b: Float): Boolean = abs(a - b) < WEIGHT_MATCH_TOLERANCE

fun ChronoData.modeKind(): String = if (ballisticMode == BallisticMode.PCP) KIND_PELLET else KIND_BB

fun ChronoData.activeIds(kind: String = modeKind()): List<String> = if (kind == KIND_PELLET) activePcp else activeAirsoft

fun resolve(id: String, own: List<CustomWeight>): WeightOption? = when {
    id.startsWith("bb:") -> id.drop(3).toFloatOrNull()?.let { WeightOption(id, it, "%.2f".format(it), KIND_BB) }
    id.startsWith("pellet:") -> PELLETS.firstOrNull { it.name == id.drop(7) }?.let {
        WeightOption(id, it.massGrams.toFloat(), "${it.name} %.3f g".format(it.massGrams), KIND_PELLET, pellet = it)
    }
    id.startsWith("own:") -> own.firstOrNull { it.id == id.drop(4) }?.let {
        val label = if (it.kind == KIND_PELLET) "${it.name} %.3f g".format(it.weight) else "${it.name} %.2f g".format(it.weight)
        WeightOption(id, it.weight, label, it.kind, isCustom = true)
    }
    else -> null
}

fun ChronoData.resolve(id: String): WeightOption? = resolve(id, customWeights)

/** Own projectiles are always in the active list of their kind; unknown ids are dropped. */
fun normalizedActive(ids: List<String>, kind: String, own: List<CustomWeight>): List<String> {
    val cleaned = ids.distinct().filter { resolve(it, own)?.kind == kind }.toMutableList()
    own.filter { it.kind == kind }.forEach { if ("own:${it.id}" !in cleaned) cleaned += "own:${it.id}" }
    return cleaned
}

/** The active list of the current mode (or [kind]), in the user's order; never empty. */
fun ChronoData.availableWeights(kind: String = modeKind()): List<WeightOption> {
    val opts = activeIds(kind).mapNotNull { resolve(it) }
    if (opts.isNotEmpty()) return opts
    return listOfNotNull(resolve(if (kind == KIND_BB) bbId(0.20f) else DEFAULT_ACTIVE_PCP.first()))
}

/** Library entries of a kind (filtered by caliber and search text). */
fun library(kind: String, caliber: String = ALL_CALIBERS, query: String = ""): List<WeightOption> {
    val q = query.trim().lowercase()
    val items = if (kind == KIND_BB) BB_LIBRARY.mapNotNull { resolve(bbId(it), emptyList()) }
    else pelletsFor(caliber).mapNotNull { resolve("pellet:${it.name}", emptyList()) }
    return items.filter { q.isEmpty() || q in it.label.lowercase() }
}

fun ChronoData.isSelected(opt: WeightOption): Boolean =
    opt.id == (if (opt.kind == KIND_PELLET) selectedPcp else selectedAirsoft)

/** Orga is always airsoft: the BB selection, whatever mode the Trajectory screen is in. */
fun ChronoData.orgaSelection(): WeightOption {
    val opts = availableWeights(KIND_BB)
    return opts.firstOrNull { it.id == selectedAirsoft } ?: opts.first()
}

/** The option to select after the list changed: the current one if offered, else same weight, else first. */
fun optionToKeep(selectedId: String, selectedWeight: Float, options: List<WeightOption>): WeightOption? =
    options.firstOrNull { it.id == selectedId }
        ?: options.firstOrNull { sameWeight(it.weight, selectedWeight) }
        ?: options.firstOrNull()

// ---------------------------------------------------------------------------------------------
// Export: "shots to include" keeps the user's choice when new shots arrive (same rule as desktop).
// ---------------------------------------------------------------------------------------------

const val DEFAULT_EXPORT_SHOTS = 10
/** [ChronoData.exportShotChoice] values besides a fixed count. */
const val EXPORT_CHOICE_DEFAULT = 0
const val EXPORT_CHOICE_ALL = -1

fun shotsToInclude(choice: Int, total: Int): Int {
    val wanted = when {
        choice == EXPORT_CHOICE_ALL -> total
        choice > 0 -> choice
        else -> DEFAULT_EXPORT_SHOTS
    }
    return wanted.coerceAtMost(total.coerceAtLeast(1)).coerceAtLeast(1)
}

/** What the slider position means: the last position is "all", so it keeps following new shots. */
fun exportChoiceFor(sliderValue: Int, total: Int): Int =
    if (total > 0 && sliderValue >= total) EXPORT_CHOICE_ALL else sliderValue

// ---------------------------------------------------------------------------------------------
// Orga weapon classes (port of the desktop compliance board)
// ---------------------------------------------------------------------------------------------

data class WeaponClass(val name: String, val maxJoules: Float, val description: String = "")

val DEFAULT_WEAPON_CLASSES = listOf(
    WeaponClass("BACKUP", 1.00f, "Pistols / Sidearms (≤ 1.00 J)"),
    WeaponClass("AR", 1.50f, "Assault Rifles / Carbines (≤ 1.50 J)"),
    WeaponClass("LMG", 1.50f, "Light Machine Guns (≤ 1.50 J)"),
    WeaponClass("DMR", 2.00f, "Designated Marksman (≤ 2.00 J)"),
    WeaponClass("SNIPER", 2.80f, "Bolt Action Snipers (≤ 2.80 J)")
)

/** Lightest standard BB the compliance board judges a class by. */
const val ORGA_REFERENCE_BB = 0.20f

enum class Compliance { AWAITING, LEGAL, ILLEGAL }

fun complianceOf(wc: WeaponClass, velocity: Float, weightG: Float = ORGA_REFERENCE_BB): Compliance = when {
    velocity <= 0f -> Compliance.AWAITING
    0.5f * (weightG / 1000f) * velocity.pow(2) <= wc.maxJoules + 1e-6f -> Compliance.LEGAL
    else -> Compliance.ILLEGAL
}

/**
 * What a marshal reads on a class card (mirrors core/orga.py): the verdict at the selected (field)
 * weight, the energy there, the heaviest weight that stays legal, and the heaviest weight from the
 * active list that would pass (null: not even the lightest).
 */
data class OrgaVerdict(val status: Compliance, val energyJ: Float = 0f, val maxWeightG: Float = 0f, val best: WeightOption? = null)

fun judgeClass(wc: WeaponClass, velocity: Float, weightG: Float, options: List<WeightOption>): OrgaVerdict {
    if (velocity <= 0f) return OrgaVerdict(Compliance.AWAITING)
    val energy = 0.5f * (weightG / 1000f) * velocity.pow(2)
    val maxW = maxWeightGrams(wc.maxJoules, velocity)
    val best = options.filter { it.weight <= maxW + 1e-6f }.maxByOrNull { it.weight }
    return OrgaVerdict(complianceOf(wc, velocity, weightG), energy, maxW, best)
}

/** Heaviest BB (grams) that stays within the class limit at [velocity]. */
fun maxWeightGrams(maxJoules: Float, velocity: Float): Float =
    if (velocity <= 0f) 0f else (2f * maxJoules / velocity.pow(2)) * 1000f

fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    val out = toMutableList()
    val item = out.removeAt(from)
    out.add(to, item)
    return out
}

fun ChronoData.activeWeaponClass(): WeaponClass? =
    weaponClasses.firstOrNull { it.name == selectedWeaponClass } ?: weaponClasses.firstOrNull()

/** Joule limit of the active Orga class (falls back to the old global limit). */
fun ChronoData.activeJouleLimit(): Float = activeWeaponClass()?.maxJoules ?: maxAllowedJoule
