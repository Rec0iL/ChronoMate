package com.example.chronomate.model

import kotlin.math.abs
import kotlin.math.pow

// ---------------------------------------------------------------------------------------------
// Weights: one place that decides which weights the chips offer (mirrors core/weights.py on the
// desktop), so Dashboard, Trajectory and Settings always show the same list.
// ---------------------------------------------------------------------------------------------

val STANDARD_BB_WEIGHTS = listOf(0.20f, 0.23f, 0.25f, 0.28f, 0.30f, 0.32f, 0.36f, 0.40f, 0.43f, 0.45f)
val STANDARD_DIABLO_WEIGHTS = listOf(0.50f, 0.51f, 0.52f, 0.53f, 0.54f)

private const val WEIGHT_MATCH_TOLERANCE = 0.0005f

data class WeightOption(val weight: Float, val label: String, val isCustom: Boolean = false)

fun sameWeight(a: Float, b: Float): Boolean = abs(a - b) < WEIGHT_MATCH_TOLERANCE

fun standardWeights(type: WeightType): List<Float> = when (type) {
    WeightType.BB -> STANDARD_BB_WEIGHTS
    WeightType.DIABLO -> STANDARD_DIABLO_WEIGHTS
    WeightType.CUSTOM -> emptyList()
}

/**
 * The loaded weights of the active type, then the custom weights. Custom weights always belong to
 * the CUSTOM type; for BB / Diablo they are appended only when [alwaysShowCustom] is on.
 */
fun availableWeights(type: WeightType, custom: List<CustomWeight>, alwaysShowCustom: Boolean): List<WeightOption> {
    val options = standardWeights(type).map { WeightOption(it, "%.2f".format(it)) }.toMutableList()
    if (type == WeightType.CUSTOM || alwaysShowCustom) {
        options += custom.map {
            WeightOption(it.weight, "${it.name} ${it.caliber}${it.caliberUnit} %.2f g".format(it.weight), isCustom = true)
        }
    }
    return options
}

fun ChronoData.availableWeights(): List<WeightOption> =
    availableWeights(weightType, customWeights, alwaysShowCustomWeights)

/** Keeps [selected] if [options] still offer it, else the first offered weight. */
fun weightToKeep(selected: Float, options: List<WeightOption>): Float =
    if (options.isEmpty() || options.any { sameWeight(it.weight, selected) }) selected else options.first().weight

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

fun complianceOf(wc: WeaponClass, velocity: Float): Compliance = when {
    velocity <= 0f -> Compliance.AWAITING
    0.5f * (ORGA_REFERENCE_BB / 1000f) * velocity.pow(2) <= wc.maxJoules -> Compliance.LEGAL
    else -> Compliance.ILLEGAL
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
