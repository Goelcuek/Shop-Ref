package com.shopref.core.calc

import com.shopref.core.material.Family
import com.shopref.core.material.Material
import com.shopref.core.tooling.ToolMaterial
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

internal fun Double.toRad() = this * kotlin.math.PI / 180.0
internal fun Double.toDeg() = this * 180.0 / kotlin.math.PI

/**
 * Kienzle specific cutting force model.
 *
 *   Fc = kc1.1 · b · h^(1 − mc)          (Kienzle 1952; Kienzle & Victor 1957)
 *   kc = kc1.1 · h^(−mc) · Kγ · Kwear     (rake correction Kγ = 1 − γ/100, Sandvik Coromant)
 *
 * with h the uncut chip thickness [mm], b the chip width [mm], γ the effective rake angle [deg].
 */
object Kienzle {
    /** Lower bound on h used when evaluating kc on its own, to avoid the h→0 singularity. */
    const val MIN_CHIP_MM = 0.001

    fun rakeFactor(rakeDeg: Double): Double = (1.0 - rakeDeg / 100.0).coerceIn(0.6, 1.4)

    /** Specific cutting force [N/mm²] at chip thickness [h] (mm). */
    fun kc(material: Material, h: Double, rakeDeg: Double = 0.0, wearFactor: Double = 1.0): Double =
        material.kc11 * max(h, MIN_CHIP_MM).pow(-material.mc) * rakeFactor(rakeDeg) * wearFactor

    /** Main (tangential) cutting force [N] for chip width [b] and thickness [h] (mm). */
    fun force(material: Material, b: Double, h: Double, rakeDeg: Double = 0.0, wearFactor: Double = 1.0): Double {
        if (h <= 0.0 || b <= 0.0) return 0.0
        return material.kc11 * b * h.pow(1.0 - material.mc) * rakeFactor(rakeDeg) * wearFactor
    }
}

/**
 * Ratios of the edge-normal (radial / thrust) and axial (along-edge) force to the
 * tangential force for a sharp edge with normal rake αn and inclination (helix) i.
 *
 * Uses the oblique-cutting transformation of Budak, Altintas & Armarego (1996),
 * J. Manuf. Sci. Eng. 118:216–224, with Stabler's chip-flow rule η = i (Stabler 1951):
 *
 *   Kr/Kt = sin(βn − αn) / [cos i · (cos(βn − αn) + tan i · tan η · sin βn)]
 *   Ka/Kt = (cos(βn − αn)·tan i − tan η · sin βn) / (cos(βn − αn) + tan i · tan η · sin βn)
 *
 * where βn is the normal friction angle, tan βn = tan βa · cos η. The shear stress and
 * shear angle cancel out of the ratios, so only the friction angle βa is required.
 * For i = 0 this reduces to Merchant's orthogonal result Ft/Fc = tan(β − α).
 */
data class ForceRatios(val radial: Double, val axial: Double) {
    companion object {
        fun oblique(frictionAngleDeg: Double, normalRakeDeg: Double, inclinationDeg: Double): ForceRatios {
            val i = inclinationDeg.toRad()
            val eta = i
            val alpha = normalRakeDeg.toRad()
            val betaN = atan(tan(frictionAngleDeg.toRad()) * cos(eta))
            val denom = cos(betaN - alpha) + tan(i) * tan(eta) * sin(betaN)
            val radial = sin(betaN - alpha) / (cos(i) * denom)
            val axial = (cos(betaN - alpha) * tan(i) - tan(eta) * sin(betaN)) / denom
            // A real edge always ploughs a little: keep a small positive radial ratio.
            return ForceRatios(radial = max(radial, 0.1), axial = axial)
        }
    }
}

/** Chip-thinning and effective-diameter geometry for milling cutters. */
object MillingGeometry {
    /** Radial engagement (arc of contact) angle [rad] for radial depth [ae] on diameter [d]. */
    fun engagementAngle(ae: Double, d: Double): Double {
        val r = (ae / d).coerceIn(0.0, 1.0)
        return kotlin.math.acos(1.0 - 2.0 * r)
    }

    /**
     * Radial chip-thinning factor: fz/hmax. For ae < D/2 the maximum chip thickness is
     * hmax = fz·sin(φe) = 2·fz·√(ae/D − (ae/D)²) (Martellotti 1941), so the feed per tooth
     * must be raised by D / (2·√(ae·D − ae²)) to keep the same hmax.
     */
    fun radialChipThinningFactor(ae: Double, d: Double): Double {
        if (ae <= 0.0 || d <= 0.0) return 1.0
        if (ae >= d / 2.0) return 1.0
        return d / (2.0 * kotlin.math.sqrt(ae * d - ae * ae))
    }

    /** Effective cutting diameter of a ball-nose cutter at axial depth [ap] (no tilt). */
    fun ballEffectiveDiameter(d: Double, ap: Double): Double {
        if (ap >= d / 2.0) return d
        if (ap <= 0.0) return 0.0
        return 2.0 * kotlin.math.sqrt(ap * (d - ap))
    }

    /** Effective diameter of a bull-nose (corner radius [r]) cutter at axial depth [ap]. */
    fun bullEffectiveDiameter(d: Double, r: Double, ap: Double): Double {
        if (r <= 0.0 || ap >= r) return d
        if (ap <= 0.0) return d - 2.0 * r
        return d - 2.0 * r + 2.0 * kotlin.math.sqrt(r * r - (r - ap) * (r - ap))
    }

    /** Scallop (cusp) height left by a ball of radius [r] at stepover [s]. */
    fun scallopHeight(r: Double, s: Double): Double {
        if (s >= 2 * r) return r
        return r - kotlin.math.sqrt(r * r - (s / 2.0) * (s / 2.0))
    }

    /** Peripheral-milling feed-mark cusp height ≈ fz² / (4·D) (Martellotti 1941). */
    fun peripheralCuspHeight(fz: Double, d: Double): Double = fz * fz / (4.0 * d)
}

/**
 * Tool life from the extended Taylor relation v·T^n·h^a = C (Taylor 1907; feed term as in the
 * extended form used in Machinery's Handbook), anchored at the standard recommendation:
 *
 *   T = Tref · (vref/v)^(1/n) · (href/h)^(a/n)
 *
 * Catalogue cutting data are set for a nominal life (Machinery's Handbook speed tables use 15 min for
 * carbide turning, with adjustment curves for 45–120 min), so the recommendation of this app is taken
 * to give [referenceMinutes] and departures in speed or chip thickness scale it. The chip-thickness
 * weight a = 0.3 sits at the low end of published extended-Taylor feed exponents (≈0.3–0.7), since the
 * recommendations already apply chip thinning.
 */
object ToolLife {
    /** Chip-thickness weight a in v·T^n·h^a = C. */
    const val CHIP_WEIGHT = 0.3

    /** Outside this range of T/Tref other wear mechanisms dominate and Taylor no longer applies. */
    private const val MIN_FACTOR = 0.05
    private const val MAX_FACTOR = 8.0

    data class Estimate(
        /** Cutting time until the wear limit [min]. */
        val minutes: Double,
        /** Life assumed at the standard recommendation [min]. */
        val referenceMinutes: Double,
        /** minutes / referenceMinutes. */
        val factor: Double,
        /** True when the conditions are so far from the recommendation that the factor was clamped. */
        val outsideModel: Boolean,
        /** Taylor exponent used. */
        val n: Double,
    )

    /** Tool life relative to the reference speed: T/Tref = (vref/v)^(1/n). */
    fun relativeLife(vRef: Double, v: Double, n: Double): Double {
        if (v <= 0 || vRef <= 0) return 1.0
        return (vRef / v).pow(1.0 / n)
    }

    fun estimate(referenceMinutes: Double, n: Double, vRef: Double, v: Double, hRef: Double = 1.0, h: Double = 1.0): Estimate {
        val speedTerm = relativeLife(vRef, v, n)
        val chipTerm = if (h > 0 && hRef > 0) (hRef / h).pow(CHIP_WEIGHT / n) else 1.0
        val raw = speedTerm * chipTerm
        val factor = raw.coerceIn(MIN_FACTOR, MAX_FACTOR)
        return Estimate(referenceMinutes * factor, referenceMinutes, factor, factor != raw, n)
    }

    /**
     * Cutting time the recommended data are assumed to give [min]: 15 min per edge for inserts (their
     * speeds derive from turning data, Machinery's Handbook V15 basis) and ceramics, 60 min for HSS
     * (classic v60 basis), 45 min for solid carbide and PCD tools. Non-abrasive non-ferrous work
     * (aluminium except high-silicon, magnesium, copper alloys, plastics) wears tools far more slowly at
     * its recommended speeds, so the basis is four times longer there.
     */
    fun referenceMinutes(substrate: ToolMaterial, insert: Boolean = false, material: Material? = null): Double = when {
        substrate.isHss -> 60.0
        insert || substrate == ToolMaterial.CERAMIC -> 15.0
        else -> 45.0
    } * slowWearFactor(material)

    fun slowWearFactor(material: Material?): Double = when (material?.family) {
        Family.ALUMINUM -> if (material.id == "n_al_high_si") 1.0 else 4.0
        Family.MAGNESIUM, Family.COPPER, Family.PLASTIC -> 4.0
        else -> 1.0
    }
}
