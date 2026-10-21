package com.shopref.core.calc

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
class Fmt(val units: UnitSystem) {
    fun len(mm: Double) = Quantity.LENGTH.formatWithUnit(mm, units, if (units == UnitSystem.METRIC) 2 else 3)
    fun chip(mm: Double) = Quantity.CHIP_LOAD.formatWithUnit(mm, units)
    fun speed(mMin: Double) = Quantity.CUTTING_SPEED.formatWithUnit(mMin, units)
    fun feed(mmMin: Double) = Quantity.FEED_RATE.formatWithUnit(mmMin, units)
    fun power(kw: Double) = Quantity.POWER.formatWithUnit(kw, units)
    fun force(n: Double) = Quantity.FORCE.formatWithUnit(n, units)
    fun torque(nm: Double) = Quantity.TORQUE.formatWithUnit(nm, units)
    fun stress(mpa: Double) = Quantity.STRESS.formatWithUnit(mpa, units)
    fun deflection(mm: Double) = Quantity.DEFLECTION.formatWithUnit(mm * 1000.0, units)
    fun num(v: Double, decimals: Int = 1) = formatNumber(v, decimals)
}

/** Basic speed/feed relations shared by all operations. */
object CuttingBasics {
    /** Spindle speed [rpm] for cutting speed [vc] (m/min) on diameter [d] (mm). */
    fun rpm(vc: Double, d: Double): Double = if (d <= 0) 0.0 else 1000.0 * vc / (Math.PI * d)

    /** Cutting speed [m/min] for spindle speed [rpm] on diameter [d] (mm). */
    fun cuttingSpeed(rpm: Double, d: Double): Double = Math.PI * d * rpm / 1000.0

    /** Power [kW] from torque [N·m] and spindle speed [rpm]. */
    fun powerKw(torqueNm: Double, rpm: Double): Double = torqueNm * 2.0 * Math.PI * rpm / 60.0 / 1000.0

    /** Torque [N·m] from power [kW] and spindle speed [rpm]. */
    fun torqueNm(powerKw: Double, rpm: Double): Double = if (rpm <= 0) 0.0 else powerKw * 1000.0 * 60.0 / (2.0 * Math.PI * rpm)
}
