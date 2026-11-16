package com.example.chronomate.model

import com.example.chronomate.ballistics.DragModel
import com.example.chronomate.ballistics.PcpConstants

/**
 * Common pellets and slugs with published ballistic coefficients (approximate). H&N publishes its
 * own BCs; JSB and RWS don't, so those figures come from retailers (Pyramyd AIR, Vector Air), mostly
 * in Hawke's "GA" pellet model, used here as approximate G1 values. Collected 2026-11-16.
 * Mirrors core/pcp_presets.py on the desktop.
 */
data class PcpPreset(
    val name: String,
    val grains: Double,
    val caliberMm: Double,
    val bc: Double,
    val dragModel: DragModel = DragModel.G1,
    val slug: Boolean = false,
    val source: String = ""
) {
    val massGrams: Double get() = Math.round(grains * PcpConstants.GRAIN_TO_GRAM * 1000.0) / 1000.0
}

val PCP_PRESETS = listOf(
    PcpPreset("JSB Exact .177", 8.44, 4.52, 0.027, slug = false, source = "Pyramyd AIR (GA)"),
    PcpPreset("JSB Exact Heavy .177", 10.34, 4.52, 0.027, slug = false, source = "Pyramyd AIR (GA)"),
    PcpPreset("H&N Field Target Trophy .177", 8.64, 4.5, 0.021, slug = false, source = "H&N"),
    PcpPreset("H&N Baracuda Match .177", 10.65, 4.5, 0.024, slug = false, source = "H&N"),
    PcpPreset("RWS Superdome .177", 8.3, 4.5, 0.014, slug = false, source = "Vector Air"),
    PcpPreset("RWS Meisterkugeln .177", 8.2, 4.5, 0.01, slug = false, source = "Vector Air"),
    PcpPreset("JSB Exact Jumbo .22", 15.89, 5.52, 0.028, slug = false, source = "Pyramyd AIR (GA)"),
    PcpPreset("JSB Exact Jumbo Heavy .22", 18.13, 5.52, 0.033, slug = false, source = "Pyramyd AIR (GA)"),
    PcpPreset("H&N Field Target Trophy .22", 14.66, 5.5, 0.019, slug = false, source = "H&N"),
    PcpPreset("H&N Baracuda Match .22", 21.14, 5.5, 0.039, slug = false, source = "H&N"),
    PcpPreset("RWS Superdome .22", 14.5, 5.5, 0.028, slug = false, source = "Vector Air"),
    PcpPreset("JSB Exact King .25", 25.39, 6.35, 0.032, slug = false, source = "Pyramyd AIR (GA)"),
    PcpPreset("JSB Exact King Heavy MkII .25", 33.95, 6.35, 0.054, slug = false, source = "Pyramyd AIR (GA)"),
    PcpPreset("JSB Exact .30", 44.75, 7.62, 0.038, slug = false, source = "Pyramyd AIR (GA)"),
    PcpPreset("JSB Knock Out Slug .22", 25.39, 5.5, 0.086, slug = true, source = "Pyramyd AIR (GA)"),
    PcpPreset("H&N Slug HP .22", 21.0, 5.5, 0.091, slug = true, source = "H&N"),
    PcpPreset("JSB Knock Out Slug .25", 33.49, 6.37, 0.095, slug = true, source = "Pyramyd AIR (GA)"),
    PcpPreset("NSA Slug .25", 34.9, 6.35, 0.11, slug = true, source = "Pyramyd AIR (G1)"),
)
