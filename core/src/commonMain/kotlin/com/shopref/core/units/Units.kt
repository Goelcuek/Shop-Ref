package com.shopref.core.units

import com.shopref.core.i18n.Lang

import kotlin.math.abs
import kotlin.math.roundToLong

/** Display unit system. All calculations run internally in metric (mm, m/min, N, W). */
enum class UnitSystem { METRIC, IMPERIAL }

/**
 * Kind of physical quantity. Each kind knows its internal SI-ish base unit (what the core
 * engine uses) and how to convert to the display unit of either system.
 */
enum class Quantity(
    val metricLabel: String,
    val imperialLabel: String,
    /** Multiply a metric value by this to get the imperial value. */
    val toImperial: Double,
    val metricDecimals: Int,
    val imperialDecimals: Int,
) {
    /** Base: mm */
    LENGTH("mm", "in", 1.0 / 25.4, 3, 4),
    /** Base: mm (used for small lengths such as chip load and deflection) */
    CHIP_LOAD("mm", "in", 1.0 / 25.4, 4, 5),
    /** Base: µm */
    DEFLECTION("µm", "in", 1.0 / 25_400.0, 1, 5),
    /** Base: m/min */
    CUTTING_SPEED("m/min", "SFM", 3.280839895, 0, 0),
    /** Base: mm/min */
    FEED_RATE("mm/min", "IPM", 1.0 / 25.4, 0, 2),
    /** Base: mm/rev */
    FEED_PER_REV("mm/rev", "IPR", 1.0 / 25.4, 3, 4),
    /** Base: cm³/min */
    MRR("cm³/min", "in³/min", 1.0 / 16.387064, 1, 2),
    /** Base: kW */
    POWER("kW", "hp", 1.341022090, 2, 2),
    /** Base: N */
    FORCE("N", "lbf", 0.2248089431, 0, 1),
    /** Base: N·m */
    TORQUE("N·m", "in·lbf", 8.850745791, 2, 1),
    /** Base: MPa */
    STRESS("MPa", "ksi", 0.1450377377, 0, 1),
    /** Base: µm */
    ROUGHNESS("µm", "µin", 39.37007874, 2, 0),
    /** Base: rpm (same in both systems) */
    SPINDLE_SPEED("rpm", "rpm", 1.0, 0, 0),
    /** Base: degrees */
    ANGLE("°", "°", 1.0, 1, 1),
    /** Base: % */
    PERCENT("%", "%", 1.0, 0, 0),
    /** Base: min */
    TIME("min", "min", 1.0, 2, 2),
    /** Base: dimensionless */
    NONE("", "", 1.0, 2, 2),
    ;

    fun label(system: UnitSystem, lang: Lang = Lang.EN): String =
        lang.tx(if (system == UnitSystem.METRIC) metricLabel else imperialLabel)

    fun decimals(system: UnitSystem): Int = if (system == UnitSystem.METRIC) metricDecimals else imperialDecimals

    fun toDisplay(base: Double, system: UnitSystem): Double =
        if (system == UnitSystem.METRIC) base else base * toImperial

    fun fromDisplay(display: Double, system: UnitSystem): Double =
        if (system == UnitSystem.METRIC) display else display / toImperial

    /** Formats a base value for display, without the unit label. */
    fun format(base: Double, system: UnitSystem, decimals: Int = decimals(system)): String =
        formatNumber(toDisplay(base, system), decimals)

    /** Formats a base value for display, with the unit label appended. */
    fun formatWithUnit(base: Double, system: UnitSystem, decimals: Int = decimals(system), lang: Lang = Lang.EN): String {
        val label = label(system, lang)
        val number = format(base, system, decimals)
        return if (label.isEmpty()) number else "$number $label"
    }
}

/** Locale-independent number formatting with thousands kept compact (no grouping). */
fun formatNumber(value: Double, decimals: Int): String {
    if (value.isNaN()) return "—"
    if (value.isInfinite()) return if (value > 0) "∞" else "-∞"
    if (decimals <= 0) return value.roundToLong().toString()
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = (abs(value) * factor).roundToLong()
    val intPart = scaled / factor
    val fracPart = scaled % factor
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    return sign + intPart.toString() + "." + fracPart.toString().padStart(decimals, '0')
}

object Conversions {
    const val MM_PER_INCH = 25.4
    const val M_MIN_TO_SFM = 3.280839895

    fun inchToMm(inch: Double) = inch * MM_PER_INCH
    fun mmToInch(mm: Double) = mm / MM_PER_INCH

    /** Parses fractional inch notation such as "1/4", "1-1/4", "1 1/4", "0.25". Returns inches. */
    fun parseInchFraction(text: String): Double? {
        val t = text.trim().replace('"', ' ').trim()
        if (t.isEmpty()) return null
        t.toDoubleOrNull()?.let { return it }
        val parts = t.split('-', ' ').filter { it.isNotBlank() }
        var total = 0.0
        for (p in parts) {
            if (p.contains('/')) {
                val (n, d) = p.split('/').let { (it.getOrNull(0)?.toDoubleOrNull()) to (it.getOrNull(1)?.toDoubleOrNull()) }
                if (n == null || d == null || d == 0.0) return null
                total += n / d
            } else {
                total += p.toDoubleOrNull() ?: return null
            }
        }
        return total
    }
}
