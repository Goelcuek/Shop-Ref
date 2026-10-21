package com.shopref.core.calc

import com.shopref.core.i18n.Lang
import com.shopref.core.units.Quantity
import com.shopref.core.units.UnitSystem
import com.shopref.core.units.formatNumber

/** A warning or tip attached to a calculation result. */
data class Advice(val level: Level, val text: String) {
    enum class Level { INFO, CAUTION, DANGER }

    companion object {
        fun info(text: String) = Advice(Level.INFO, text)
        fun caution(text: String) = Advice(Level.CAUTION, text)
        fun danger(text: String) = Advice(Level.DANGER, text)
    }
}

/** Unit-aware formatting for advice text (base values are metric). */
class Fmt(val units: UnitSystem, val lang: Lang = Lang.EN) {
    fun len(mm: Double) = Quantity.LENGTH.formatWithUnit(mm, units, if (units == UnitSystem.METRIC) 2 else 3, lang)
    fun chip(mm: Double) = Quantity.CHIP_LOAD.formatWithUnit(mm, units, lang = lang)
    fun speed(mMin: Double) = Quantity.CUTTING_SPEED.formatWithUnit(mMin, units, lang = lang)
    fun feed(mmMin: Double) = Quantity.FEED_RATE.formatWithUnit(mmMin, units, lang = lang)
    fun power(kw: Double) = Quantity.POWER.formatWithUnit(kw, units, lang = lang)
    fun force(n: Double) = Quantity.FORCE.formatWithUnit(n, units, lang = lang)
    fun torque(nm: Double) = Quantity.TORQUE.formatWithUnit(nm, units, lang = lang)
    fun stress(mpa: Double) = Quantity.STRESS.formatWithUnit(mpa, units, lang = lang)
    fun deflection(mm: Double) = Quantity.DEFLECTION.formatWithUnit(mm * 1000.0, units, lang = lang)
    fun rpm(rpm: Double) = Quantity.SPINDLE_SPEED.formatWithUnit(rpm, units, lang = lang)
    fun num(v: Double, decimals: Int = 1) = formatNumber(v, decimals)
}

/** "Using N% of available spindle power." in the requested language. */
internal fun powerShareText(lang: Lang, power: Double, available: Double): String {
    val pct = (100 * power / available).toInt()
    return lang.t("Using $pct% of available spindle power.", "Mevcut iş mili gücünün %$pct kadarı kullanılıyor.")
}

internal fun feedLimitedText(lang: Lang, rpm: String): String = lang.t(
    "Feed limited by the machine; spindle reduced to $rpm.",
    "İlerleme makine sınırında; devir $rpm değerine düşürüldü.",
)

internal fun toolLifeOutsideText(lang: Lang): String = lang.t(
    "Speed or chip load is far from the recommendation, outside the range where the Taylor tool-life model holds.",
    "Hız veya diş başı ilerleme önerilenden çok uzak; Taylor takım ömrü modelinin geçerli olduğu aralığın dışında.",
)

/** Basic speed/feed relations shared by all operations. */
object CuttingBasics {
    /** Spindle speed [rpm] for cutting speed [vc] (m/min) on diameter [d] (mm). */
    fun rpm(vc: Double, d: Double): Double = if (d <= 0) 0.0 else 1000.0 * vc / (kotlin.math.PI * d)

    /** Cutting speed [m/min] for spindle speed [rpm] on diameter [d] (mm). */
    fun cuttingSpeed(rpm: Double, d: Double): Double = kotlin.math.PI * d * rpm / 1000.0

    /** Power [kW] from torque [N·m] and spindle speed [rpm]. */
    fun powerKw(torqueNm: Double, rpm: Double): Double = torqueNm * 2.0 * kotlin.math.PI * rpm / 60.0 / 1000.0

    /** Torque [N·m] from power [kW] and spindle speed [rpm]. */
    fun torqueNm(powerKw: Double, rpm: Double): Double = if (rpm <= 0) 0.0 else powerKw * 1000.0 * 60.0 / (2.0 * kotlin.math.PI * rpm)
}
