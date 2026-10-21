package com.shopref.core.calc

import com.shopref.core.material.Family
import com.shopref.core.material.IsoGroup
import com.shopref.core.material.Material
import com.shopref.core.tooling.Coating
import com.shopref.core.tooling.Machine
import com.shopref.core.tooling.ToolCondition
import com.shopref.core.tooling.ToolMaterial
import com.shopref.core.units.UnitSystem
import kotlin.math.asin
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

enum class MillToolType(val label: String) {
    SQUARE_END("Square end mill"),
    BALL_END("Ball end mill"),
    CORNER_RADIUS("Corner-radius (bull nose)"),
    CHAMFER("Chamfer mill / spot"),
    INDEXABLE("Indexable face / shoulder mill"),
}

enum class MillStrategy(val label: String, val description: String) {
    SLOTTING("Slotting", "Full-width slot, ae = D"),
    SIDE_MILLING("Side milling / roughing", "Conventional roughing, ae ≈ 0.3–0.5·D"),
    HEM("HEM / adaptive / trochoidal", "Low radial, high axial engagement with chip thinning"),
    FINISHING("Finishing", "Light radial wall or 3D finish pass"),
    FACING("Facing", "Face milling a flat surface"),
}

data class MillingInput(
    val material: Material,
    val toolMaterial: ToolMaterial = ToolMaterial.CARBIDE,
    val coating: Coating = Coating.ALTIN,
    val toolType: MillToolType = MillToolType.SQUARE_END,
    /** Cutting diameter [mm]. */
    val diameter: Double = 12.0,
    val flutes: Int = 4,
    val helixDeg: Double = 35.0,
    /** Radial (normal) rake angle [deg]; null = default for the material family. */
    val rakeDeg: Double? = null,
    /** Corner radius for bull-nose cutters [mm]. */
    val cornerRadius: Double = 1.0,
    /** Included point angle of a chamfer mill [deg]. */
    val chamferAngle: Double = 90.0,
    /** Tip (flat) diameter of a chamfer mill [mm]. */
    val chamferTipDiameter: Double = 0.0,
    /** Lead (entering) angle κr of an indexable cutter [deg]; 90 = square shoulder. */
    val leadAngleDeg: Double = 45.0,
    /** Flute length [mm]; null = 2.5·D. */
    val fluteLength: Double? = null,
    /** Stick-out from holder [mm]; null = flute length + 0.5·D. */
    val stickout: Double? = null,
    /** Shank diameter [mm]; null = cutting diameter. */
    val shankDiameter: Double? = null,
    val strategy: MillStrategy = MillStrategy.SIDE_MILLING,
    /** Axial depth [mm]; null = recommended. */
    val ap: Double? = null,
    /** Radial depth (stepover) [mm]; null = recommended. */
    val ae: Double? = null,
    val climb: Boolean = true,
    val condition: ToolCondition = ToolCondition.NORMAL,
    /** Speed override in percent of the recommendation. */
    val speedPercent: Double = 100.0,
    /** Chip-load override in percent of the recommendation. */
    val feedPercent: Double = 100.0,
    val machine: Machine = Machine(),
    /** Unit system used for advice text. */
    val units: UnitSystem = UnitSystem.METRIC,
)

data class MillingResult(
    val input: MillingInput,
    val ap: Double,
    val ae: Double,
    /** Effective cutting diameter at the engaged depth [mm]. */
    val effectiveDiameter: Double,
    /** Effective lead angle used in the chip thickness [deg]. */
    val leadAngleDeg: Double,
    val rakeDeg: Double,
    /** Recommended (uncorrected-for-machine) cutting speed [m/min]. */
    val vcRecommended: Double,
    /** Actual cutting speed at the programmed rpm [m/min]. */
    val vc: Double,
    val rpm: Double,
    /** Target maximum chip thickness [mm]. */
    val hmaxTarget: Double,
    /** Programmed feed per tooth [mm]. */
    val fz: Double,
    /** Combined radial/axial chip-thinning factor applied. */
    val chipThinningFactor: Double,
    /** Actual maximum chip thickness with the programmed fz [mm]. */
    val hmax: Double,
    /** Feed rate [mm/min]. */
    val feed: Double,
    /** Material removal rate [cm³/min]. */
    val mrr: Double,
    val engagementAngleDeg: Double,
    val forces: MillingForceModel.Result,
    /** Cutting power at the tool [kW]. */
    val powerKw: Double,
    /** Power available at the tool at this rpm [kW]. */
    val availablePowerKw: Double,
    /** Mean spindle torque [N·m]. */
    val torqueNm: Double,
    /** Tip deflection under the peak lateral force [mm]. */
    val deflection: Double,
    val bendingStress: Double,
    val torsionStress: Double,
    /** Tool life relative to the recommendation (1 = 100%). */
    val relativeToolLife: Double,
    /** Theoretical surface cusp/scallop height [mm] or null if not applicable. */
    val cuspHeight: Double?,
    val rampAngleDeg: Double,
    /** Plunge (Z) feed for a centre-cutting tool [mm/min]. */
    val plungeFeed: Double,
    /** Feed for helical/ramp entry [mm/min]. */
    val rampFeed: Double,
    val advice: List<Advice>,
)

/**
 * Feeds & speeds advisor for milling. Recommendation logic:
 *  1. vc = material base speed × substrate × coating × engagement factor (heat input falls with the
 *     arc of contact: (90°/φe)^0.3, clamped 0.8…1.6) × user override;
 *  2. hmax target = (fz/D) × D × substrate × rigidity × strategy factor, then fz = hmax × chip-thinning;
 *  3. rpm/feed are clamped to the machine; forces, torque and power come from [MillingForceModel],
 *     deflection and stress from [Deflection].
 */
object MillingCalculator {

    private data class Engagement(
        val slotAp: Double, val sideAe: Double, val sideAp: Double,
        val hemAe: Double, val hemAp: Double, val finishAe: Double,
    )

    private fun engagementFor(group: IsoGroup) = when (group) {
        IsoGroup.N -> Engagement(1.0, 0.5, 1.5, 0.15, 2.5, 0.05)
        IsoGroup.P -> Engagement(0.5, 0.4, 1.0, 0.10, 2.0, 0.03)
        IsoGroup.M -> Engagement(0.4, 0.3, 1.0, 0.08, 2.0, 0.03)
        IsoGroup.K -> Engagement(0.5, 0.4, 1.0, 0.10, 2.0, 0.03)
        IsoGroup.S -> Engagement(0.3, 0.2, 1.0, 0.07, 2.0, 0.02)
        IsoGroup.H -> Engagement(0.1, 0.05, 0.5, 0.05, 1.5, 0.02)
    }

    fun defaultRake(material: Material): Double = when (material.family) {
        Family.ALUMINUM, Family.MAGNESIUM, Family.PLASTIC -> 15.0
        Family.COPPER -> if (material.id == "n_brass") 0.0 else 10.0
        Family.STEEL -> 8.0
        Family.STAINLESS -> 10.0
        Family.CAST_IRON -> 6.0
        Family.TITANIUM -> 10.0
        Family.NICKEL -> 8.0
        Family.HARDENED -> -2.0
        Family.COMPOSITE -> 10.0
        Family.GRAPHITE -> 5.0
    }

    fun rampAngle(family: Family): Double = when (family) {
        Family.ALUMINUM, Family.MAGNESIUM, Family.GRAPHITE -> 3.0
        Family.PLASTIC -> 5.0
        Family.COPPER, Family.STEEL, Family.CAST_IRON -> 2.0
        Family.STAINLESS -> 1.5
        Family.TITANIUM, Family.NICKEL, Family.COMPOSITE -> 1.0
        Family.HARDENED -> 0.5
    }

    /** Indexable insert chip thickness targets [mm] by ISO group. */
    private fun insertHex(group: IsoGroup) = when (group) {
        IsoGroup.P -> 0.15
        IsoGroup.M -> 0.12
        IsoGroup.K -> 0.18
        IsoGroup.N -> 0.15
        IsoGroup.S -> 0.10
        IsoGroup.H -> 0.08
    }

    fun fluteLengthOf(input: MillingInput) = input.fluteLength ?: (2.5 * input.diameter)
    fun stickoutOf(input: MillingInput) = input.stickout ?: (fluteLengthOf(input) + 0.5 * input.diameter)

    /** Recommended (ap, ae) for the input's strategy, tool and machine, ignoring user overrides. */
    fun defaultEngagement(input: MillingInput): Pair<Double, Double> {
        val d = input.diameter
        val e = engagementFor(input.material.group)
        val lf = fluteLengthOf(input)
        val rig = input.machine.rigidity.depthFactor
        val hssFactor = if (input.toolMaterial.isHss) 0.6 else 1.0
        val (apRaw, aeRaw) = when (input.toolType) {
            MillToolType.INDEXABLE -> when (input.strategy) {
                MillStrategy.FINISHING -> 0.5 to 0.7 * d
                MillStrategy.SLOTTING -> min(3.0, 0.15 * d) to d
                else -> (if (input.material.group == IsoGroup.H) 0.5 else 2.5) to 0.7 * d
            }
            MillToolType.CHAMFER -> (0.1 * d).coerceAtLeast(0.5) to (0.1 * d).coerceAtLeast(0.5)
            MillToolType.BALL_END -> when (input.strategy) {
                MillStrategy.FINISHING, MillStrategy.FACING -> 0.05 * d to 0.05 * d
                MillStrategy.SLOTTING -> 0.1 * d to d
                else -> 0.15 * d to 0.3 * d
            }
            else -> when (input.strategy) {
                MillStrategy.SLOTTING -> e.slotAp * d * hssFactor * rig to d
                MillStrategy.SIDE_MILLING -> e.sideAp * d * hssFactor * rig to e.sideAe * d
                MillStrategy.HEM -> min(e.hemAp * d * rig, lf) to e.hemAe * d
                MillStrategy.FINISHING -> min(1.5 * d, lf) to e.finishAe * d
                MillStrategy.FACING -> 0.1 * d * rig to 0.65 * d
            }
        }
        val apMax = if (input.toolType == MillToolType.INDEXABLE) apRaw else lf
        return min(apRaw, apMax).coerceAtLeast(0.01) to aeRaw.coerceIn(0.01, d)
    }

    fun calculate(input: MillingInput): MillingResult {
        val m = input.material
        val f = Fmt(input.units)
        val advice = mutableListOf<Advice>()
        val d = input.diameter.coerceAtLeast(0.1)
        val z = input.flutes.coerceAtLeast(1)
        val (apDefault, aeDefault) = defaultEngagement(input)
        val ap = (input.ap ?: apDefault).coerceAtLeast(0.001)
        val ae = (input.ae ?: aeDefault).coerceIn(0.001, d)
        val rake = input.rakeDeg ?: if (input.toolType == MillToolType.INDEXABLE) 10.0 else defaultRake(m)
        val lf = fluteLengthOf(input)

        // --- Effective diameter and lead angle -----------------------------------------------------
        var deff = d
        var lead = 90.0
        when (input.toolType) {
            MillToolType.SQUARE_END -> Unit
            MillToolType.BALL_END -> {
                deff = MillingGeometry.ballEffectiveDiameter(d, ap)
                lead = asin((deff / d).coerceIn(0.05, 1.0)).toDeg()
            }
            MillToolType.CORNER_RADIUS -> {
                val r = input.cornerRadius.coerceIn(0.0, d / 2)
                if (r > 0 && ap < r) {
                    deff = MillingGeometry.bullEffectiveDiameter(d, r, ap)
                    lead = kotlin.math.acos((1.0 - ap / r).coerceIn(-1.0, 1.0)).toDeg().coerceAtLeast(5.0)
                }
            }
            MillToolType.CHAMFER -> {
                val half = (input.chamferAngle / 2.0).coerceIn(5.0, 85.0)
                deff = min(d, input.chamferTipDiameter + 2.0 * ap * tan(half.toRad()))
                lead = 90.0 - half
                if (input.chamferTipDiameter + 2.0 * ap * tan(half.toRad()) > d) {
                    advice += Advice.caution("Chamfer depth exceeds the cutter's diameter capacity.")
                }
            }
            MillToolType.INDEXABLE -> lead = input.leadAngleDeg.coerceIn(10.0, 90.0)
        }
        deff = deff.coerceAtLeast(0.05)
        val aeEff = min(ae, deff)

        // --- Cutting speed ---------------------------------------------------------------------------
        val substrateSpeed: Double = if (input.toolType == MillToolType.INDEXABLE) {
            when (input.toolMaterial) {
                ToolMaterial.CERAMIC, ToolMaterial.PCD -> input.toolMaterial.millingSpeed(m) ?: (m.turnVc * 0.8)
                else -> m.turnVc * 0.8
            }
        } else {
            input.toolMaterial.millingSpeed(m) ?: run {
                input.toolMaterial.incompatibilityReason(m)?.let { advice += Advice.danger(it) }
                if (input.toolMaterial.isHss) m.vcCarbide * 0.25 else m.vcCarbide
            }
        }
        if (input.toolType == MillToolType.INDEXABLE && input.toolMaterial.isHss) {
            advice += Advice.caution("Indexable cutters use carbide inserts; speeds assume coated carbide grades.")
        }
        val coatingFactor = if (input.toolType == MillToolType.INDEXABLE) 1.0 else {
            input.coating.speedFactor(m.family) ?: run {
                input.coating.incompatibilityReason(m.family)?.let { advice += Advice.caution(it) }
                1.0
            }
        }
        val phiE = MillingGeometry.engagementAngle(aeEff, deff)
        val engagementFactor = (90.0 / max(phiE.toDeg(), 1.0)).pow(0.3).coerceIn(0.8, 1.6)
        val strategySpeed = if (input.strategy == MillStrategy.FINISHING) 1.05 else 1.0
        val vcRec = substrateSpeed * coatingFactor * engagementFactor * strategySpeed
        val vcTarget = vcRec * input.speedPercent / 100.0
        var rpm = CuttingBasics.rpm(vcTarget, deff)

        // --- Chip load -----------------------------------------------------------------------------
        val strategyFeed = when (input.strategy) {
            MillStrategy.SLOTTING -> 0.9
            MillStrategy.FINISHING -> 0.75
            else -> 1.0
        }
        val hmaxBase = if (input.toolType == MillToolType.INDEXABLE) insertHex(m.group)
        else m.fzPerD * d * input.toolMaterial.chipLoadFactor
        val hmaxTarget = (hmaxBase * input.machine.rigidity.feedFactor * strategyFeed * input.feedPercent / 100.0)
            .coerceAtLeast(0.002)
        val axialThinning = 1.0 / kotlin.math.sin(lead.toRad())
        val radialThinning = MillingGeometry.radialChipThinningFactor(aeEff, deff)
        var thinning = axialThinning * radialThinning
        val thinningCap = 4.0
        if (thinning > thinningCap) {
            advice += Advice.info("Chip-thinning factor capped at ${thinningCap.toInt()}× (calculated ${f.num(thinning)}×).")
            thinning = thinningCap
        }
        val fz = hmaxTarget * thinning

        // --- Machine limits --------------------------------------------------------------------------
        val machine = input.machine
        if (rpm > machine.maxRpm) {
            advice += Advice.caution(
                "Spindle limited to ${machine.maxRpm.toInt()} rpm — cutting speed reduced to " +
                    "${f.speed(CuttingBasics.cuttingSpeed(machine.maxRpm, deff))} (chip load kept).",
            )
            rpm = machine.maxRpm
        }
        if (rpm < machine.minRpm) {
            advice += Advice.caution("Below the spindle minimum: running at ${machine.minRpm.toInt()} rpm increases cutting speed and wear.")
            rpm = machine.minRpm
        }
        var feed = fz * z * rpm
        if (feed > machine.maxFeed) {
            val newRpm = machine.maxFeed / (fz * z)
            advice += Advice.caution("Feed limited to ${f.feed(machine.maxFeed)} — spindle speed reduced to ${newRpm.toInt()} rpm to hold chip load.")
            rpm = newRpm
            feed = machine.maxFeed
        }
        val vc = CuttingBasics.cuttingSpeed(rpm, deff)

        // --- Forces ----------------------------------------------------------------------------------
        val helix = if (input.toolType == MillToolType.INDEXABLE) 0.0 else input.helixDeg
        val forces = MillingForceModel.simulate(
            MillingForceModel.Input(
                material = m, diameter = deff, flutes = z, helixDeg = helix, rakeDeg = rake,
                leadAngleDeg = lead, ap = ap, ae = aeEff, fz = fz, climb = input.climb,
                wearFactor = input.condition.forceFactor,
            ),
        )
        val torque = forces.meanTorque
        val power = CuttingBasics.powerKw(torque, rpm)
        val available = machine.availablePowerKw(rpm)
        val mrr = ap * aeEff * feed / 1000.0

        // --- Deflection & stress (solid tools only) ----------------------------------------------------
        val stick = stickoutOf(input)
        val shank = input.shankDiameter ?: d
        val defl = if (input.toolType == MillToolType.INDEXABLE) null else Deflection.compute(
            Deflection.Input(
                diameter = d, shankDiameter = shank, stickout = stick, fluteLength = min(lf, stick),
                youngsModulus = input.toolMaterial.youngsModulus, force = forces.peakFxy,
                loadFromTip = min(ap, lf) / 2.0,
            ),
        )
        val torsion = if (input.toolType == MillToolType.INDEXABLE) 0.0 else Deflection.torsionalStress(forces.peakTorque, d)

        // --- Surface finish --------------------------------------------------------------------------
        val cusp: Double? = when (input.toolType) {
            MillToolType.BALL_END -> MillingGeometry.scallopHeight(d / 2.0, ae)
            MillToolType.CORNER_RADIUS -> if (input.cornerRadius > 0 && input.strategy == MillStrategy.FINISHING)
                MillingGeometry.scallopHeight(input.cornerRadius, ae) else MillingGeometry.peripheralCuspHeight(fz, d)
            MillToolType.SQUARE_END -> MillingGeometry.peripheralCuspHeight(fz, d)
            else -> null
        }

        // --- Tool life, entry moves ------------------------------------------------------------------
        val n = if (input.toolType == MillToolType.INDEXABLE) 0.3 else input.toolMaterial.taylorN
        val relLife = ToolLife.relativeLife(vcRec, vc, n)
        val ramp = rampAngle(m.family)
        val plungeFeed = 0.4 * hmaxTarget * z * rpm
        val rampFeed = 0.6 * feed

        // --- Advice ----------------------------------------------------------------------------------
        if (power > available) {
            val apMax = ap * available / power
            advice += Advice.danger(
                "Needs ${f.power(power)} at the tool but only ${f.power(available)} is available " +
                    "at this speed. Reduce axial depth to ≈${f.len(apMax)} or lower the feed.",
            )
        } else if (power > 0.85 * available) {
            advice += Advice.caution("Using ${(100 * power / available).toInt()}% of available spindle power.")
        }
        if (defl != null) {
            val limit = if (input.strategy == MillStrategy.FINISHING) 0.010 else 0.025
            if (defl.tipDeflection > 4 * limit) {
                advice += Advice.danger("Tool deflection ${f.deflection(defl.tipDeflection)} is high — reduce stick-out, ae or ap.")
            } else if (defl.tipDeflection > limit) {
                advice += Advice.caution("Tool deflection ${f.deflection(defl.tipDeflection)} exceeds the ${f.deflection(limit)} guideline.")
            }
            val allow = input.toolMaterial.allowableBendingStress
            if (defl.maxBendingStress > allow) {
                advice += Advice.danger("Bending stress ${f.stress(defl.maxBendingStress)} exceeds the ${f.stress(allow)} guideline — high breakage risk.")
            } else if (defl.maxBendingStress > 0.7 * allow) {
                advice += Advice.caution("Bending stress ${f.stress(defl.maxBendingStress)} is ${(100 * defl.maxBendingStress / allow).toInt()}% of the guideline.")
            }
            if (torsion > 0.6 * allow) {
                advice += Advice.caution("Torsional stress ${f.stress(torsion)} is high for this diameter.")
            }
        }
        if (ap > lf && input.toolType != MillToolType.INDEXABLE) {
            advice += Advice.danger("Axial depth exceeds the flute length (${f.len(lf)}).")
        }
        if (stick / d > 4.0 && input.toolType != MillToolType.INDEXABLE) {
            advice += Advice.caution("Stick-out is ${f.num(stick / d)}×D: deflection and chatter risk rise with L³; use the shortest holder possible.")
        }
        if (input.strategy == MillStrategy.SLOTTING && z > 3 && (m.family == Family.ALUMINUM || m.family == Family.MAGNESIUM || m.family == Family.PLASTIC)) {
            advice += Advice.caution("$z flutes in a full slot in ${m.family.label.lowercase()} risks chip packing; prefer 2–3 flutes.")
        }
        if (input.strategy == MillStrategy.HEM && z < 4 && m.group != IsoGroup.N) {
            advice += Advice.info("HEM benefits from 5–7 flute tools in ${m.group.description.lowercase()}.")
        }
        if (fz * 1000 < 5.0) {
            advice += Advice.caution("Chip load ${f.chip(fz)} is very small — risk of rubbing and work hardening.")
        }
        if (input.toolType == MillToolType.BALL_END && ap < 0.02 * d) {
            advice += Advice.info("Very shallow ball-nose cut: effective diameter only ${f.len(deff)}; tilt the tool if possible.")
        }
        if (m.group == IsoGroup.H && !input.climb) {
            advice += Advice.caution("Use climb milling in hardened material.")
        }
        if (input.speedPercent != 100.0 && relLife < 0.95) {
            advice += Advice.info("Estimated tool life ${(relLife * 100).toInt()}% of recommendation (Taylor n = $n).")
        }
        if (m.notes.isNotBlank()) advice += Advice.info(m.notes)

        return MillingResult(
            input = input, ap = ap, ae = aeEff, effectiveDiameter = deff, leadAngleDeg = lead, rakeDeg = rake,
            vcRecommended = vcRec, vc = vc, rpm = rpm, hmaxTarget = hmaxTarget, fz = fz,
            chipThinningFactor = thinning, hmax = fz / (axialThinning * radialThinning),
            feed = feed, mrr = mrr, engagementAngleDeg = phiE.toDeg(), forces = forces,
            powerKw = power, availablePowerKw = available, torqueNm = torque,
            deflection = defl?.tipDeflection ?: 0.0, bendingStress = defl?.maxBendingStress ?: 0.0,
            torsionStress = torsion, relativeToolLife = relLife, cuspHeight = cusp,
            rampAngleDeg = ramp, plungeFeed = plungeFeed, rampFeed = rampFeed, advice = advice,
        )
    }

    /** Circular-interpolation feed compensation: programmed (centre) feed for an internal/external arc. */
    fun arcFeed(peripheralFeed: Double, toolDiameter: Double, arcDiameter: Double, internal: Boolean): Double {
        if (arcDiameter <= 0) return peripheralFeed
        val ratio = if (internal) (arcDiameter - toolDiameter) / arcDiameter else (arcDiameter + toolDiameter) / arcDiameter
        return peripheralFeed * max(ratio, 0.0)
    }
}
