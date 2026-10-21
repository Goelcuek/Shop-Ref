package com.shopref.core.calc

import com.shopref.core.material.Material
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

internal fun Double.toRad() = this * Math.PI / 180.0
internal fun Double.toDeg() = this * 180.0 / Math.PI

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

/** Taylor tool-life relation v·T^n = C (Taylor 1907). */
object ToolLife {
    /** Tool life relative to the reference speed: T/Tref = (vref/v)^(1/n). */
    fun relativeLife(vRef: Double, v: Double, n: Double): Double {
        if (v <= 0 || vRef <= 0) return 1.0
        return (vRef / v).pow(1.0 / n)
    }
}
