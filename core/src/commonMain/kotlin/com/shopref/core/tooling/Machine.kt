package com.shopref.core.tooling

import kotlin.math.PI
import kotlin.math.min

/** Machine-tool limits used to clamp and check recommendations. */
data class Machine(
    val name: String = "Generic VMC (CAT40)",
    val maxRpm: Double = 10_000.0,
    val minRpm: Double = 50.0,
    /** Maximum cutting feed [mm/min]. */
    val maxFeed: Double = 10_000.0,
    /** Rated spindle power [kW]. */
    val powerKw: Double = 11.0,
    /** Maximum spindle torque [N·m]; 0 = limited by power only. */
    val maxTorqueNm: Double = 0.0,
    /** Mechanical efficiency from motor to tool, 0–1. */
    val efficiency: Double = 0.85,
    val rigidity: Rigidity = Rigidity.HEAVY,
) {
    /** Power available at the tool for the given spindle speed [kW] (constant torque below base speed). */
    fun availablePowerKw(rpm: Double): Double {
        val rated = powerKw * efficiency
        if (maxTorqueNm <= 0.0) return rated
        val torqueLimited = maxTorqueNm * efficiency * 2.0 * PI * rpm / 60.0 / 1000.0
        return min(rated, torqueLimited)
    }

    /** Torque available at the tool for the given spindle speed [N·m]. */
    fun availableTorqueNm(rpm: Double): Double {
        if (rpm <= 0) return 0.0
        return availablePowerKw(rpm) * 1000.0 * 60.0 / (2.0 * PI * rpm)
    }

    companion object {
        val PRESETS: List<Machine> = listOf(
            Machine(),
            Machine("Small VMC (BT30)", maxRpm = 15_000.0, maxFeed = 15_000.0, powerKw = 5.5, maxTorqueNm = 35.0, rigidity = Rigidity.MEDIUM),
            Machine("Knee mill (R8)", maxRpm = 4_200.0, maxFeed = 1_000.0, powerKw = 2.2, maxTorqueNm = 0.0, efficiency = 0.75, rigidity = Rigidity.MEDIUM),
            Machine("Benchtop mill", maxRpm = 2_500.0, maxFeed = 750.0, powerKw = 0.75, efficiency = 0.7, rigidity = Rigidity.LIGHT),
            Machine("CNC router (ER20 spindle)", maxRpm = 24_000.0, minRpm = 6_000.0, maxFeed = 15_000.0, powerKw = 2.2, efficiency = 0.8, rigidity = Rigidity.LIGHT),
            Machine("Lathe (medium)", maxRpm = 4_000.0, maxFeed = 5_000.0, powerKw = 11.0, maxTorqueNm = 300.0, rigidity = Rigidity.HEAVY),
        )
    }
}
