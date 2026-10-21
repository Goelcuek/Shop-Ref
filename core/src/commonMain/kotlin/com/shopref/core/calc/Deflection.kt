package com.shopref.core.calc

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow

/**
 * Static deflection of an end mill modelled as a stepped cantilever clamped in the holder.
 *
 * The shank (diameter Ds) runs from the holder face to the start of the flutes; the fluted
 * length is represented by a solid cylinder of equivalent diameter De = 0.8·D, following
 * Kops & Vo (1990), "Determination of the equivalent diameter of an end mill based on its
 * compliance", CIRP Annals 39(1):93–96 (MMC/Mitsubishi use 0.7–0.8·D).
 *
 * The tip deflection for a point load F at distance a from the holder follows from the
 * unit-load (Maxwell–Mohr) integral δ = ∫₀ᵃ F·(a − x)·(L − x) / (E·I(x)) dx, evaluated
 * numerically so any shank/flute split is handled.
 */
object Deflection {
    const val FLUTE_EQUIVALENT_RATIO = 0.8

    data class Input(
        /** Cutting diameter [mm]. */
        val diameter: Double,
        /** Shank diameter [mm]. */
        val shankDiameter: Double,
        /** Stick-out from holder face to tool tip [mm]. */
        val stickout: Double,
        /** Flute length / length of cut [mm]. */
        val fluteLength: Double,
        /** Young's modulus [MPa]. */
        val youngsModulus: Double,
        /** Lateral load [N]. */
        val force: Double,
        /** Distance of the load from the tip [mm] (typically ap/2). */
        val loadFromTip: Double,
    )

    data class Result(
        /** Tip deflection [mm]. */
        val tipDeflection: Double,
        /** Maximum bending stress [MPa] (checked at the holder face and at the flute run-out). */
        val maxBendingStress: Double,
        /** Bending stiffness at the load point [N/mm]. */
        val stiffness: Double,
    )

    private fun secondMoment(d: Double) = PI * d.pow(4) / 64.0

    fun compute(input: Input, steps: Int = 400): Result {
        val l = max(input.stickout, 1e-3)
        val lf = input.fluteLength.coerceIn(0.0, l)
        val fluteStart = l - lf
        val de = input.diameter * FLUTE_EQUIVALENT_RATIO
        val ds = input.shankDiameter
        val a = (l - input.loadFromTip).coerceIn(0.0, l)
        fun diameterAt(x: Double) = if (x >= fluteStart) de else ds

        // Unit-load integral for tip deflection; also the deflection at the load point for stiffness.
        val dx = a / steps
        var tip = 0.0
        var atLoad = 0.0
        for (i in 0 until steps) {
            val x = (i + 0.5) * dx
            val ei = input.youngsModulus * secondMoment(diameterAt(x))
            tip += (a - x) * (l - x) / ei * dx
            atLoad += (a - x) * (a - x) / ei * dx
        }
        val tipDeflection = input.force * tip
        val stiffness = if (atLoad > 0) 1.0 / atLoad else Double.POSITIVE_INFINITY

        // Bending stress σ = M·c/I = 32·M/(π·d³) at holder face and at the flute run-out.
        val stressHolder = 32.0 * input.force * a / (PI * diameterAt(0.0).pow(3))
        val momentAtFlute = input.force * max(0.0, a - fluteStart)
        val stressFlute = 32.0 * momentAtFlute / (PI * de.pow(3))
        return Result(tipDeflection, max(stressHolder, stressFlute), stiffness)
    }

    /** Torsional shear stress in the fluted section τ = 16·T/(π·De³) [MPa] for torque [N·m]. */
    fun torsionalStress(torqueNm: Double, diameter: Double): Double {
        val de = diameter * FLUTE_EQUIVALENT_RATIO
        return 16.0 * torqueNm * 1000.0 / (PI * de.pow(3))
    }
}
