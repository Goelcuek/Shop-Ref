package com.shopref.core.calc

import com.shopref.core.material.Material
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * Time-domain mechanistic milling force model (rigid tool, no runout).
 *
 * The cutter is discretised into axial slices of height dz. For tooth j at cutter rotation θ,
 * the immersion angle of the slice at height z is (Altintas 2012, ch. 2)
 *
 *   φj(z) = θ + j·2π/N − (2·tan β / D)·z               (helix lag, Tlusty & MacNeil 1975)
 *
 * measured clockwise from the +y axis with feed along +x. The uncut chip thickness is
 * h = fz·sin φ·sin κ (Martellotti 1941; κ = lead angle), the tangential element force follows
 * the Kienzle law dFt = kc1.1·h^(1−mc)·dz/sin κ, and the radial/axial components use the
 * oblique-transformation ratios dFr = rr·dFt, dFa = ra·dFt. Element forces are projected as
 *
 *   dFx = −dFt·cos φ − dFr·sin φ
 *   dFy = +dFt·sin φ − dFr·cos φ
 *
 * and summed over all teeth and slices. Up-milling engages φ ∈ [0, φe]; down-milling
 * (climb) engages φ ∈ [π − φe, π], with cos φe = 1 − 2·ae/D.
 */
object MillingForceModel {

    data class Input(
        val material: Material,
        /** Effective cutting diameter [mm]. */
        val diameter: Double,
        val flutes: Int,
        val helixDeg: Double,
        val rakeDeg: Double,
        /** Lead angle κ [deg]: 90 for a square shoulder/peripheral edge, 45 for a 45° face mill. */
        val leadAngleDeg: Double = 90.0,
        /** Axial depth of cut [mm]. */
        val ap: Double,
        /** Radial depth of cut [mm]. */
        val ae: Double,
        /** Feed per tooth [mm]. */
        val fz: Double,
        val climb: Boolean = true,
        val wearFactor: Double = 1.0,
        val angularSteps: Int = 360,
        val axialSlices: Int = 40,
    )

    data class Sample(val angleDeg: Double, val fx: Double, val fy: Double, val fz: Double, val torque: Double) {
        val fxy: Double get() = hypot(fx, fy)
    }

    data class Result(
        val samples: List<Sample>,
        val meanFx: Double,
        val meanFy: Double,
        val meanFz: Double,
        /** Peak in-plane (bending) force magnitude [N]. */
        val peakFxy: Double,
        /** Mean in-plane force magnitude [N]. */
        val meanFxy: Double,
        val peakFz: Double,
        /** Mean and peak spindle torque [N·m]. */
        val meanTorque: Double,
        val peakTorque: Double,
        /** Mean tangential force per revolution, summed over teeth in cut [N]. */
        val meanTangential: Double,
        val ratios: ForceRatios,
        /** Entry/exit immersion angles [deg]. */
        val startAngleDeg: Double,
        val exitAngleDeg: Double,
        /** Average teeth in cut. */
        val teethInCut: Double,
        /** Mean specific cutting force over the cut [N/mm²]. */
        val meanKc: Double,
    )

    fun simulate(input: Input): Result {
        val d = input.diameter
        val n = input.flutes.coerceAtLeast(1)
        val ap = max(input.ap, 1e-6)
        val ae = input.ae.coerceIn(1e-6, d)
        val kappa = input.leadAngleDeg.coerceIn(5.0, 90.0).toRad()
        val sinK = sin(kappa)
        val cosK = cos(kappa)

        val phiE = MillingGeometry.engagementAngle(ae, d)
        val (phiSt, phiEx) = if (input.climb) (PI - phiE) to PI else 0.0 to phiE

        val ratios = ForceRatios.oblique(input.material.frictionAngle, input.rakeDeg, input.helixDeg)
        val kFactor = input.material.kc11 * Kienzle.rakeFactor(input.rakeDeg) * input.wearFactor
        val exponent = 1.0 - input.material.mc

        val steps = input.angularSteps.coerceAtLeast(36)
        val slices = input.axialSlices.coerceAtLeast(1)
        val dz = ap / slices
        val chipWidth = dz / sinK
        val lagPerMm = 2.0 * tan(input.helixDeg.toRad()) / d
        val pitch = 2.0 * PI / n
        val radiusM = d / 2.0 / 1000.0

        val samples = ArrayList<Sample>(steps)
        var sumFx = 0.0; var sumFy = 0.0; var sumFz = 0.0; var sumT = 0.0; var sumFxy = 0.0
        var peakFxy = 0.0; var peakFz = 0.0; var peakT = 0.0
        var sumFt = 0.0
        var engagedElements = 0L
        var sumKcWeighted = 0.0
        var sumAreaWeighted = 0.0

        for (s in 0 until steps) {
            val theta = 2.0 * PI * s / steps
            var fx = 0.0; var fy = 0.0; var fzAx = 0.0; var ftTotal = 0.0
            for (j in 0 until n) {
                for (k in 0 until slices) {
                    val z = (k + 0.5) * dz
                    var phi = (theta + j * pitch - lagPerMm * z) % (2.0 * PI)
                    if (phi < 0) phi += 2.0 * PI
                    if (phi < phiSt || phi > phiEx) continue
                    val h = input.fz * sin(phi) * sinK
                    if (h <= 0.0) continue
                    val dFt = kFactor * chipWidth * h.pow(exponent)
                    val dFn = ratios.radial * dFt
                    // Edge-normal force splits into radial and axial components by the lead angle.
                    val dFr = dFn * sinK
                    val dFa = dFn * cosK + ratios.axial * dFt
                    fx += -dFt * cos(phi) - dFr * sin(phi)
                    fy += dFt * sin(phi) - dFr * cos(phi)
                    fzAx += dFa
                    ftTotal += dFt
                    engagedElements++
                    val area = chipWidth * h
                    sumKcWeighted += dFt
                    sumAreaWeighted += area
                }
            }
            val torque = ftTotal * radiusM
            val sample = Sample(theta.toDeg(), fx, fy, fzAx, torque)
            samples += sample
            sumFx += fx; sumFy += fy; sumFz += fzAx; sumT += torque; sumFt += ftTotal
            sumFxy += sample.fxy
            peakFxy = max(peakFxy, sample.fxy)
            peakFz = max(peakFz, abs(fzAx))
            peakT = max(peakT, torque)
        }

        val teethInCut = engagedElements.toDouble() / (steps * slices)
        return Result(
            samples = samples,
            meanFx = sumFx / steps,
            meanFy = sumFy / steps,
            meanFz = sumFz / steps,
            peakFxy = peakFxy,
            meanFxy = sumFxy / steps,
            peakFz = peakFz,
            meanTorque = sumT / steps,
            peakTorque = peakT,
            meanTangential = sumFt / steps,
            ratios = ratios,
            startAngleDeg = phiSt.toDeg(),
            exitAngleDeg = phiEx.toDeg(),
            teethInCut = teethInCut,
            meanKc = if (sumAreaWeighted > 0) sumKcWeighted / sumAreaWeighted else 0.0,
        )
    }

    /**
     * Closed-form mean tangential force per revolution for a linear (Kt constant) model,
     * F̄t = (N·ap/2π)·Kt·fz·(cos φst − cos φex) (Altintas 2012, eq. 2.21 form). Used in tests
     * to validate the numerical integration when mc = 0.
     */
    fun closedFormMeanTangential(kt: Double, n: Int, ap: Double, fz: Double, phiSt: Double, phiEx: Double): Double =
        n * ap / (2.0 * PI) * kt * fz * (cos(phiSt) - cos(phiEx))
}
