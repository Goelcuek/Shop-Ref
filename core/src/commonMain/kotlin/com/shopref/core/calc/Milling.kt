package com.shopref.core.calc

import com.shopref.core.i18n.Lang
import com.shopref.core.material.Family
import com.shopref.core.material.IsoGroup
import com.shopref.core.material.Material
import com.shopref.core.tooling.ChipLoadLevel
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

/**
 * Milling cutter families. Solid tools take their speeds and chip loads from the end-mill data of the
 * material; insert cutters from the insert (turning-derived) data, with the chip thickness set by the
 * lead angle κr: h = fz·sin κr. Round inserts engage an arc from κ = 0 to κmax = acos(1 − 2ap/iC).
 */
enum class MillToolType(
    val label: String,
    val description: String,
    /** Cuts with inserts in a steel body: insert speeds and chip loads, no coating choice. */
    val indexable: Boolean,
    /** Fixed lead angle κr [deg]; null when it follows the tool geometry or the lead-angle input. */
    val fixedLead: Double?,
    /** Default cutting diameter [mm]. */
    val defaultDiameter: Double,
    val defaultFlutes: Int,
    /** Default insert cutting-edge length [mm] (round inserts: iC; disc cutters: width); 0 for solid tools. */
    val defaultInsertSize: Double = 0.0,
    /** Largest axial depth as a fraction of the insert size. */
    val maxApPerInsert: Double = 0.0,
    /** Chip-thickness target relative to the generic insert value. */
    val hexFactor: Double = 1.0,
    /** Always arbor-mounted (shell or disc); other insert cutters are shank tools below 40 mm. */
    val arborMounted: Boolean = false,
    /** Strategy that suits the tool when it is picked; null keeps the current one. */
    val defaultStrategy: MillStrategy? = null,
) {
    SQUARE_END("Square end mill", "Solid, sharp 90° corner", false, 90.0, 12.0, 4),
    ROUGHING_END("Roughing end mill (serrated)", "Solid, chip-splitting serrations: ≈15% lower force, rough wall", false, 90.0, 12.0, 4),
    CORNER_RADIUS("Corner-radius (bull nose)", "Solid, radiused corner", false, null, 12.0, 4),
    BALL_END("Ball end mill", "Solid, full-radius tip for 3D surfaces", false, null, 12.0, 2),
    CHAMFER("Chamfer mill / spot", "Solid, pointed tool for chamfers and spotting", false, null, 12.0, 4),
    INDEXABLE_END_MILL(
        "Indexable shoulder end mill", "Shank tool with 90° inserts (APKT / R390 style)", true, 90.0, 25.0, 3,
        defaultInsertSize = 11.0, maxApPerInsert = 0.9, hexFactor = 0.85, defaultStrategy = MillStrategy.SIDE_MILLING,
    ),
    FACE_MILL_45(
        "Face mill 45°", "Shell mill with 45° inserts: smooth entry, thinner chips", true, 45.0, 63.0, 5,
        defaultInsertSize = 12.0, maxApPerInsert = 0.5, arborMounted = true, defaultStrategy = MillStrategy.FACING,
    ),
    FACE_MILL_90(
        "Square-shoulder face mill", "Shell mill with 90° inserts for faces and shoulders", true, 90.0, 63.0, 5,
        defaultInsertSize = 15.0, maxApPerInsert = 0.85, arborMounted = true, defaultStrategy = MillStrategy.FACING,
    ),
    ROUND_INSERT(
        "Round-insert (button) mill", "Strong round inserts; chip thinning grows as ap shrinks", true, null, 50.0, 4,
        defaultInsertSize = 12.0, maxApPerInsert = 0.5, defaultStrategy = MillStrategy.FACING,
    ),
    HIGH_FEED(
        "High-feed mill", "Shallow lead angle (≈12°): small ap, very high feed per tooth", true, null, 32.0, 3,
        defaultInsertSize = 9.0, maxApPerInsert = 0.15, defaultStrategy = MillStrategy.FACING,
    ),
    DISC(
        "Side-and-face / slot cutter", "Arbor disc for slots, T-slots and keyseats; ap = cutter width", true, 90.0, 100.0, 8,
        defaultInsertSize = 12.0, maxApPerInsert = 1.0, hexFactor = 0.6, arborMounted = true, defaultStrategy = MillStrategy.SIDE_MILLING,
    ),
    INDEXABLE(
        "Indexable, custom lead angle", "Any other insert cutter: set the lead angle", true, null, 50.0, 4,
        defaultInsertSize = 12.0, maxApPerInsert = 0.6, defaultStrategy = MillStrategy.FACING,
    ),
    ;

    /** Lead angle used when the input leaves it blank [deg]. */
    val defaultLead: Double get() = fixedLead ?: when (this) {
        HIGH_FEED -> 12.0
        INDEXABLE -> 45.0
        else -> 90.0
    }

    /** True for types whose lead angle is entered by the user. */
    val hasLeadInput: Boolean get() = this == HIGH_FEED || this == INDEXABLE

    /** Label of the insert-size input, or null for solid tools. */
    val insertSizeLabel: String? get() = when {
        !indexable -> null
        this == ROUND_INSERT -> "Insert iC"
        this == DISC -> "Cutter width"
        else -> "Insert edge length"
    }

    /** Shell and disc cutters sit on an arbor; insert cutters of 40 mm and up are shell-type too. */
    fun isArborMounted(diameter: Double) = arborMounted || (indexable && diameter >= 40.0)

    /** Default diameter in the unit system's sizes: inch shops get the nearest 1/4". */
    fun defaultDiameter(units: UnitSystem): Double =
        if (units == UnitSystem.METRIC) defaultDiameter else kotlin.math.round(defaultDiameter / 25.4 * 4.0) / 4.0 * 25.4
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
    /** Lead (entering) angle κr for high-feed and custom insert cutters [deg]; null = type default. */
    val leadAngleDeg: Double? = null,
    /** Insert cutting-edge length, round-insert iC or disc width [mm]; null = type default. */
    val insertSize: Double? = null,
    /** Flute length [mm]; null = 2.5·D. */
    val fluteLength: Double? = null,
    /** Stick-out from holder [mm]; null = flute length + 0.5·D (insert cutters 2·D). */
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
    /** Recommendation aggressiveness (chip load and speed). */
    val chipLoadLevel: ChipLoadLevel = ChipLoadLevel.STANDARD,
    /** Unit system used for advice text. */
    val units: UnitSystem = UnitSystem.METRIC,
    /** Language of advice text. */
    val lang: Lang = Lang.EN,
)

/**
 * The input with another cutter and that cutter's defaults. Size and tooth count change only when the
 * family's default size differs (square → ball keeps the diameter); depths, insert size and lead go back to
 * auto, insert cutters swap an HSS substrate for carbide, and face-type cutters switch to facing.
 */
fun MillingInput.withToolType(type: MillToolType, units: UnitSystem): MillingInput {
    val sizeChanges = type.defaultDiameter != toolType.defaultDiameter
    val needsInsertGrade = type.indexable && type != MillToolType.DISC && toolMaterial.isHss
    return copy(
        toolType = type,
        diameter = if (sizeChanges) type.defaultDiameter(units) else diameter,
        flutes = if (sizeChanges) type.defaultFlutes else flutes,
        insertSize = null, leadAngleDeg = null, ap = null, ae = null, stickout = null, fluteLength = null,
        shankDiameter = null, rakeDeg = null,
        strategy = type.defaultStrategy ?: strategy,
        toolMaterial = if (needsInsertGrade) ToolMaterial.CARBIDE else toolMaterial,
    )
}

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
    /** Deflection guideline for the strategy [mm] (finishing 10 µm, otherwise 25 µm). */
    val deflectionLimit: Double,
    /** False for arbor-mounted cutters, where tool deflection is not modelled. */
    val hasDeflection: Boolean,
    /** Bending-stress guideline of the tool body [MPa] (steel body for insert cutters). */
    val allowableBendingStress: Double,
    /** Largest axial depth the inserts allow [mm], or null for solid tools. */
    val apMaxInsert: Double?,
    val bendingStress: Double,
    val torsionStress: Double,
    /** Estimated tool life (extended Taylor, anchored at the standard recommendation). */
    val toolLife: ToolLife.Estimate,
    /** Feed path cut within the estimated tool life [m]. */
    val toolLifePath: Double,
    /** Volume removed within the estimated tool life [cm³]. */
    val toolLifeVolume: Double,
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

    /** Steel tool body of insert cutters (Young's modulus [MPa], bending-stress guideline [MPa]). */
    private const val STEEL_BODY_MODULUS = 210_000.0
    private const val STEEL_BODY_ALLOWABLE_STRESS = 700.0

    /** hmax target before rigidity, strategy and override [mm]. */
    private fun chipTarget(input: MillingInput, level: ChipLoadLevel): Double {
        val type = input.toolType
        val group = input.material.group
        return when {
            // Insert chip loads have less headroom than solid tools, so the level is applied at half strength.
            type.indexable && !(type == MillToolType.DISC && input.toolMaterial.isHss) ->
                insertHex(group) * type.hexFactor * (1.0 + (level.chipFactor - 1.0) * 0.5)
            type == MillToolType.DISC -> insertHex(group) * type.hexFactor * input.toolMaterial.chipLoadFactor * 0.6 * level.chipFactor
            else -> input.material.fzPerD * input.diameter * input.toolMaterial.chipLoadFactor * level.chipFactor
        }
    }

    fun fluteLengthOf(input: MillingInput) = input.fluteLength ?: (2.5 * input.diameter)
    /** Stick-out from the holder [mm]: solid tools flute length + 0.5·D, shank insert cutters ≈2·D. */
    fun stickoutOf(input: MillingInput) = input.stickout
        ?: if (input.toolType.indexable) 2.0 * input.diameter else fluteLengthOf(input) + 0.5 * input.diameter

    /** Insert size in use [mm]: the input, or the cutter type's default. */
    fun insertSizeOf(input: MillingInput) = input.insertSize ?: input.toolType.defaultInsertSize

    /** Largest axial depth the inserts allow [mm], or null for solid tools. */
    fun apMaxInsertOf(input: MillingInput): Double? =
        if (input.toolType.indexable) input.toolType.maxApPerInsert * insertSizeOf(input) else null

    /** Recommended (ap, ae) for the input's strategy, tool and machine, ignoring user overrides. */
    fun defaultEngagement(input: MillingInput): Pair<Double, Double> {
        val d = input.diameter
        val e = engagementFor(input.material.group)
        val lf = fluteLengthOf(input)
        val rig = input.machine.rigidity.depthFactor
        val hssFactor = if (input.toolMaterial.isHss) 0.6 else 1.0
        val hard = input.material.group == IsoGroup.H
        val insert = insertSizeOf(input)
        val apInsert = apMaxInsertOf(input)
        // Peripheral (end-mill style) engagement per strategy; [apScale] lets serrated roughers cut deeper.
        fun peripheral(apScale: Double = 1.0) = when (input.strategy) {
            MillStrategy.SLOTTING -> e.slotAp * d * hssFactor * rig * apScale to d
            MillStrategy.SIDE_MILLING -> e.sideAp * d * hssFactor * rig * apScale to e.sideAe * d
            MillStrategy.HEM -> min(e.hemAp * d * rig, lf) to e.hemAe * d
            MillStrategy.FINISHING -> min(1.5 * d, lf) to e.finishAe * d
            MillStrategy.FACING -> 0.1 * d * rig to 0.65 * d
        }
        // Face milling with inserts: ae ≈ 0.75·D keeps the entry chip thin (cutter offset from the edge).
        fun facing(ap: Double) = when (input.strategy) {
            MillStrategy.FINISHING -> 0.5 to 0.75 * d
            MillStrategy.SLOTTING -> min(ap, 0.1 * d) to d
            else -> (if (hard) 0.25 * ap else ap) * rig to 0.75 * d
        }
        val (apRaw, aeRaw) = when (input.toolType) {
            MillToolType.INDEXABLE_END_MILL -> peripheral()
            MillToolType.ROUGHING_END -> peripheral(apScale = 1.25)
            MillToolType.FACE_MILL_45 -> facing(3.0)
            MillToolType.FACE_MILL_90, MillToolType.INDEXABLE -> facing(2.5)
            MillToolType.ROUND_INSERT -> facing(0.2 * insert)
            MillToolType.HIGH_FEED -> when (input.strategy) {
                MillStrategy.FINISHING -> 0.3 * (apInsert ?: 1.0) to 0.6 * d
                MillStrategy.SLOTTING -> 0.6 * (apInsert ?: 1.0) to d
                else -> (if (hard) 0.4 else 0.8) * (apInsert ?: 1.0) to 0.7 * d
            }
            // Disc cutters: ap is the cutter width, ae the slot depth.
            MillToolType.DISC -> insert to min(0.15 * d, 3.0 * insert)
            MillToolType.CHAMFER -> (0.1 * d).coerceAtLeast(0.5) to (0.1 * d).coerceAtLeast(0.5)
            MillToolType.BALL_END -> when (input.strategy) {
                MillStrategy.FINISHING, MillStrategy.FACING -> 0.05 * d to 0.05 * d
                MillStrategy.SLOTTING -> 0.1 * d to d
                else -> 0.15 * d to 0.3 * d
            }
            MillToolType.SQUARE_END, MillToolType.CORNER_RADIUS -> peripheral()
        }
        val apMax = apInsert ?: lf
        return min(apRaw, apMax).coerceAtLeast(0.01) to aeRaw.coerceIn(0.01, d)
    }

    fun calculate(input: MillingInput): MillingResult {
        val m = input.material
        val f = Fmt(input.units, input.lang)
        val L = input.lang
        val advice = mutableListOf<Advice>()
        val d = input.diameter.coerceAtLeast(0.1)
        val z = input.flutes.coerceAtLeast(1)
        val (apDefault, aeDefault) = defaultEngagement(input)
        val ap = (input.ap ?: apDefault).coerceAtLeast(0.001)
        val ae = (input.ae ?: aeDefault).coerceIn(0.001, d)
        val type = input.toolType
        val rake = input.rakeDeg ?: if (type.indexable) 10.0 else defaultRake(m)
        val lf = fluteLengthOf(input)

        // --- Effective diameter and lead angle -----------------------------------------------------
        var deff = d
        var lead = type.defaultLead
        // Lead angle of the force model; differs from the chip-thickness lead only for round inserts.
        var forceLead: Double? = null
        when (type) {
            MillToolType.SQUARE_END, MillToolType.ROUGHING_END, MillToolType.INDEXABLE_END_MILL,
            MillToolType.FACE_MILL_45, MillToolType.FACE_MILL_90, MillToolType.DISC -> Unit
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
                    advice += Advice.caution(L.t("Chamfer depth exceeds the cutter's diameter capacity.", "Pah derinliği frezenin çap kapasitesini aşıyor."))
                }
            }
            MillToolType.HIGH_FEED, MillToolType.INDEXABLE -> lead = (input.leadAngleDeg ?: type.defaultLead).coerceIn(8.0, 90.0)
            MillToolType.ROUND_INSERT -> {
                // The edge engages from κ = 0 to κmax; hex = fz·sin κmax, the mean chip sits near κmax/2.
                val r = insertSizeOf(input).coerceAtLeast(1.0) / 2.0
                val kMax = kotlin.math.acos((1.0 - min(ap, r) / r).coerceIn(-1.0, 1.0)).toDeg().coerceAtLeast(5.0)
                lead = kMax
                forceLead = kMax / 2.0
            }
        }
        deff = deff.coerceAtLeast(0.05)
        val aeEff = min(ae, deff)

        // --- Cutting speed ---------------------------------------------------------------------------
        // Insert data apply to every insert cutter; an HSS disc (T-slot / keyseat cutter) runs on HSS end-mill data.
        val insertData = type.indexable && !(type == MillToolType.DISC && input.toolMaterial.isHss)
        val substrateSpeed: Double = if (insertData) {
            when (input.toolMaterial) {
                ToolMaterial.CERAMIC, ToolMaterial.PCD -> input.toolMaterial.millingSpeed(m) ?: (m.turnVc * 0.8)
                else -> m.turnVc * 0.8
            }
        } else {
            input.toolMaterial.millingSpeed(m) ?: run {
                input.toolMaterial.incompatibilityReason(m, L)?.let { advice += Advice.danger(it) }
                if (input.toolMaterial.isHss) m.vcCarbide * 0.25 else m.vcCarbide
            }
        }
        if (insertData && input.toolMaterial.isHss) {
            advice += Advice.caution(
                L.t(
                    "Indexable cutters use carbide inserts; speeds assume coated carbide grades.",
                    "Kesici uçlu frezeler karbür uç kullanır; hızlar kaplamalı karbür kaliteleri içindir.",
                ),
            )
        }
        val coatingFactor = if (insertData) 1.0 else {
            input.coating.speedFactor(m.family) ?: run {
                input.coating.incompatibilityReason(m.family, L)?.let { advice += Advice.caution(it) }
                1.0
            }
        }
        val phiE = MillingGeometry.engagementAngle(aeEff, deff)
        val engagementFactor = (90.0 / max(phiE.toDeg(), 1.0)).pow(0.3).coerceIn(0.8, 1.6)
        // HEM keeps the arc of contact short and the chips thin, so heat per edge drops further than the
        // engagement factor alone captures; tool makers' HEM data run ≈15% faster.
        val strategySpeed = when (input.strategy) {
            MillStrategy.FINISHING -> 1.05
            MillStrategy.HEM -> 1.15
            else -> 1.0
        }
        val level = input.chipLoadLevel
        val vcRec = substrateSpeed * coatingFactor * engagementFactor * strategySpeed * level.speedFactor
        val vcTarget = vcRec * input.speedPercent / 100.0
        var rpm = CuttingBasics.rpm(vcTarget, deff)

        // --- Chip load -----------------------------------------------------------------------------
        val strategyFeed = when (input.strategy) {
            MillStrategy.SLOTTING -> 0.9
            MillStrategy.FINISHING -> 0.75
            else -> 1.0
        }
        // Insert chip loads have less headroom than solid tools, so the level is applied at half strength.
        val hmaxBase = chipTarget(input, level)
        val hmaxTarget = (hmaxBase * input.machine.rigidity.feedFactor * strategyFeed * input.feedPercent / 100.0)
            .coerceAtLeast(0.002)
        val axialThinning = 1.0 / kotlin.math.sin(lead.toRad())
        val radialThinning = MillingGeometry.radialChipThinningFactor(aeEff, deff)
        // Radial thinning is capped (very light ae is dominated by edge rounding); the axial part is the
        // insert geometry itself, e.g. ≈4.8× on a 12° high-feed cutter.
        val radialCap = 4.0
        var radialUsed = radialThinning
        if (radialThinning > radialCap) {
            advice += Advice.info(
                L.t(
                    "Chip-thinning factor capped at ${radialCap.toInt()}× (calculated ${f.num(radialThinning)}×).",
                    "Talaş incelme katsayısı ${radialCap.toInt()}× ile sınırlandı (hesaplanan ${f.num(radialThinning)}×).",
                ),
            )
            radialUsed = radialCap
        }
        val thinning = axialThinning * radialUsed
        val fz = hmaxTarget * thinning

        // --- Machine limits --------------------------------------------------------------------------
        val machine = input.machine
        if (rpm > machine.maxRpm) {
            val vcMax = f.speed(CuttingBasics.cuttingSpeed(machine.maxRpm, deff))
            advice += Advice.caution(
                L.t(
                    "Spindle limited to ${f.rpm(machine.maxRpm)} — cutting speed reduced to $vcMax (chip load kept).",
                    "İş mili ${f.rpm(machine.maxRpm)} ile sınırlı — kesme hızı $vcMax değerine düştü (diş başı ilerleme korundu).",
                ),
            )
            rpm = machine.maxRpm
        }
        if (rpm < machine.minRpm) {
            advice += Advice.caution(
                L.t(
                    "Below the spindle minimum: running at ${f.rpm(machine.minRpm)} increases cutting speed and wear.",
                    "İş mili alt sınırının altında: ${f.rpm(machine.minRpm)} ile çalışmak kesme hızını ve aşınmayı artırır.",
                ),
            )
            rpm = machine.minRpm
        }
        var feed = fz * z * rpm
        if (feed > machine.maxFeed) {
            val newRpm = machine.maxFeed / (fz * z)
            advice += Advice.caution(
                L.t(
                    "Feed limited to ${f.feed(machine.maxFeed)} — spindle speed reduced to ${f.rpm(newRpm)} to hold chip load.",
                    "İlerleme ${f.feed(machine.maxFeed)} ile sınırlı — diş başı ilerlemeyi korumak için devir ${f.rpm(newRpm)} değerine düşürüldü.",
                ),
            )
            rpm = newRpm
            feed = machine.maxFeed
        }
        val vc = CuttingBasics.cuttingSpeed(rpm, deff)

        // --- Forces ----------------------------------------------------------------------------------
        val helix = if (type.indexable) 0.0 else input.helixDeg
        // Serrated roughers split the chip: ≈10–30% lower forces in published tests; 15% is used here.
        val profileFactor = if (type == MillToolType.ROUGHING_END) 0.85 else 1.0
        val forces = MillingForceModel.simulate(
            MillingForceModel.Input(
                material = m, diameter = deff, flutes = z, helixDeg = helix, rakeDeg = rake,
                leadAngleDeg = forceLead ?: lead, ap = ap, ae = aeEff, fz = fz, climb = input.climb,
                wearFactor = input.condition.forceFactor * profileFactor,
            ),
        )
        val torque = forces.meanTorque
        val power = CuttingBasics.powerKw(torque, rpm)
        val available = machine.availablePowerKw(rpm)
        val mrr = ap * aeEff * feed / 1000.0

        // --- Deflection & stress (shank tools; insert cutters bend in their steel body) ---------------------
        val stick = stickoutOf(input)
        val shank = input.shankDiameter ?: d
        val arbor = type.isArborMounted(d)
        // The reduced section of an insert body is the chip-gullet zone behind the pockets.
        val reducedLength = if (type.indexable) max(1.5 * insertSizeOf(input), 0.5 * d) else lf
        val defl = if (arbor) null else Deflection.compute(
            Deflection.Input(
                diameter = d, shankDiameter = shank, stickout = stick, fluteLength = min(reducedLength, stick),
                youngsModulus = if (type.indexable) STEEL_BODY_MODULUS else input.toolMaterial.youngsModulus, force = forces.peakFxy,
                loadFromTip = min(ap, reducedLength) / 2.0,
            ),
        )
        val torsion = if (defl == null) 0.0 else Deflection.torsionalStress(forces.peakTorque, d)
        val allowable = if (type.indexable) STEEL_BODY_ALLOWABLE_STRESS else input.toolMaterial.allowableBendingStress

        // --- Surface finish --------------------------------------------------------------------------
        val cusp: Double? = when (input.toolType) {
            MillToolType.BALL_END -> MillingGeometry.scallopHeight(d / 2.0, ae)
            MillToolType.CORNER_RADIUS -> if (input.cornerRadius > 0 && input.strategy == MillStrategy.FINISHING)
                MillingGeometry.scallopHeight(input.cornerRadius, ae) else MillingGeometry.peripheralCuspHeight(fz, d)
            MillToolType.SQUARE_END, MillToolType.INDEXABLE_END_MILL -> MillingGeometry.peripheralCuspHeight(fz, d)
            else -> null
        }

        val limit = if (input.strategy == MillStrategy.FINISHING) 0.010 else 0.025

        // --- Tool life, entry moves ------------------------------------------------------------------
        val n = if (insertData) 0.3 else input.toolMaterial.taylorN
        // Reference: the Standard chip-load level without overrides, at the same tool, strategy and machine.
        val std = ChipLoadLevel.STANDARD
        val hmaxStd = chipTarget(input, std) * input.machine.rigidity.feedFactor * strategyFeed
        val life = ToolLife.estimate(
            ToolLife.referenceMinutes(input.toolMaterial, insert = insertData, material = m), n,
            vRef = vcRec / level.speedFactor * std.speedFactor, v = vc,
            hRef = hmaxStd.coerceAtLeast(0.002), h = fz / (axialThinning * radialThinning),
        )
        val ramp = rampAngle(m.family)
        val plungeFeed = 0.4 * hmaxTarget * z * rpm
        val rampFeed = 0.6 * feed

        // --- Advice ----------------------------------------------------------------------------------
        if (power > available) {
            val apMax = ap * available / power
            advice += Advice.danger(
                L.t(
                    "Needs ${f.power(power)} at the tool but only ${f.power(available)} is available " +
                        "at this speed. Reduce axial depth to ≈${f.len(apMax)} or lower the feed.",
                    "Takımda ${f.power(power)} güç gerekiyor ama bu devirde yalnızca ${f.power(available)} mevcut. " +
                        "Eksenel derinliği ≈${f.len(apMax)} değerine düşürün ya da ilerlemeyi azaltın.",
                ),
            )
        } else if (power > 0.85 * available) {
            advice += Advice.caution(powerShareText(L, power, available))
        }
        if (defl != null) {
            if (defl.tipDeflection > 4 * limit) {
                advice += Advice.danger(
                    L.t(
                        "Tool deflection ${f.deflection(defl.tipDeflection)} is high — reduce stick-out, ae or ap.",
                        "Takım esnemesi ${f.deflection(defl.tipDeflection)} çok yüksek — sarkma boyunu, ae veya ap'yi azaltın.",
                    ),
                )
            } else if (defl.tipDeflection > limit) {
                advice += Advice.caution(
                    L.t(
                        "Tool deflection ${f.deflection(defl.tipDeflection)} exceeds the ${f.deflection(limit)} guideline.",
                        "Takım esnemesi ${f.deflection(defl.tipDeflection)}, önerilen ${f.deflection(limit)} sınırını aşıyor.",
                    ),
                )
            }
            val allow = allowable
            if (defl.maxBendingStress > allow) {
                advice += Advice.danger(
                    L.t(
                        "Bending stress ${f.stress(defl.maxBendingStress)} exceeds the ${f.stress(allow)} guideline — high breakage risk.",
                        "Eğilme gerilmesi ${f.stress(defl.maxBendingStress)}, önerilen ${f.stress(allow)} sınırını aşıyor — yüksek kırılma riski.",
                    ),
                )
            } else if (defl.maxBendingStress > 0.7 * allow) {
                val share = (100 * defl.maxBendingStress / allow).toInt()
                advice += Advice.caution(
                    L.t(
                        "Bending stress ${f.stress(defl.maxBendingStress)} is $share% of the guideline.",
                        "Eğilme gerilmesi ${f.stress(defl.maxBendingStress)}, sınırın %$share kadarı.",
                    ),
                )
            }
            if (torsion > 0.6 * allow) {
                advice += Advice.caution(
                    L.t(
                        "Torsional stress ${f.stress(torsion)} is high for this diameter.",
                        "Burulma gerilmesi ${f.stress(torsion)} bu çap için yüksek.",
                    ),
                )
            }
        }
        val apInsert = apMaxInsertOf(input)
        if (apInsert == null && ap > lf) {
            advice += Advice.danger(L.t("Axial depth exceeds the flute length (${f.len(lf)}).", "Eksenel derinlik ağız boyunu (${f.len(lf)}) aşıyor."))
        }
        if (apInsert != null && ap > apInsert * 1.001) {
            advice += Advice.danger(
                if (type == MillToolType.DISC) L.t("Axial depth exceeds the cutter width (${f.len(apInsert)}).", "Eksenel derinlik freze genişliğini (${f.len(apInsert)}) aşıyor.")
                else L.t(
                    "Axial depth exceeds what the inserts can cut (≈${f.len(apInsert)}); use a larger insert or more passes.",
                    "Eksenel derinlik ucun kesebileceğini (≈${f.len(apInsert)}) aşıyor; daha büyük uç kullanın ya da paso sayısını artırın.",
                ),
            )
        }
        if (type == MillToolType.ROUND_INSERT && ap > 0.25 * insertSizeOf(input) && ap <= apInsert!!) {
            advice += Advice.info(
                L.t(
                    "Round inserts thin the chip most at ap ≤ 0.25·iC (${f.len(0.25 * insertSizeOf(input))}); deeper cuts need lower fz.",
                    "Yuvarlak uçta talaş en çok ap ≤ 0,25·iC (${f.len(0.25 * insertSizeOf(input))}) iken incelir; daha derin kesimde fz düşer.",
                ),
            )
        }
        if (type == MillToolType.ROUGHING_END) {
            advice += Advice.info(
                L.t(
                    "Serrated edges leave a scalloped wall: leave 0.2–0.5 mm for a finishing pass.",
                    "Tırtıllı kesici ağız dalgalı bir duvar bırakır: bitirme pasosu için 0,2–0,5 mm bırakın.",
                ),
            )
        }
        if (type == MillToolType.DISC) {
            advice += Advice.info(
                L.t(
                    "Staggered-tooth disc cutters: enter half the inserts as z — each side edge cuts every other tooth.",
                    "Şaşırtmalı dişli disk frezelerde z olarak uçların yarısını girin — her yan kenar bir atlayarak keser.",
                ),
            )
        }
        if (!arbor && stick / d > 4.0) {
            advice += Advice.caution(
                L.t(
                    "Stick-out is ${f.num(stick / d)}×D: deflection and chatter risk rise with L³; use the shortest holder possible.",
                    "Sarkma boyu ${f.num(stick / d)}×D: esneme ve tırlama riski L³ ile artar; olabilecek en kısa tutucuyu kullanın.",
                ),
            )
        }
        if (input.strategy == MillStrategy.SLOTTING && z > 3 && (m.family == Family.ALUMINUM || m.family == Family.MAGNESIUM || m.family == Family.PLASTIC)) {
            advice += Advice.caution(
                L.t(
                    "$z flutes in a full slot in ${m.family.label.lowercase()} risks chip packing; prefer 2–3 flutes.",
                    "${L.tx(m.family.label)} malzemede tam kanalda $z ağız talaş sıkışması riski taşır; 2–3 ağız tercih edin.",
                ),
            )
        }
        if (input.strategy == MillStrategy.HEM && z < 4 && m.group != IsoGroup.N) {
            advice += Advice.info(
                L.t(
                    "HEM benefits from 5–7 flute tools in ${m.group.description.lowercase()}.",
                    "${L.tx(m.group.description)} grubunda HEM için 5–7 ağızlı takımlar daha verimlidir.",
                ),
            )
        }
        if (fz * 1000 < 5.0) {
            advice += Advice.caution(
                L.t(
                    "Chip load ${f.chip(fz)} is very small — risk of rubbing and work hardening.",
                    "Diş başı ilerleme ${f.chip(fz)} çok düşük — sürtünme ve pekleşme riski.",
                ),
            )
        }
        if (input.toolType == MillToolType.BALL_END && ap < 0.02 * d) {
            advice += Advice.info(
                L.t(
                    "Very shallow ball-nose cut: effective diameter only ${f.len(deff)}; tilt the tool if possible.",
                    "Çok sığ küresel uç kesimi: efektif çap yalnızca ${f.len(deff)}; mümkünse takımı eğin.",
                ),
            )
        }
        if (m.group == IsoGroup.H && !input.climb) {
            advice += Advice.caution(L.t("Use climb milling in hardened material.", "Sertleştirilmiş malzemede yukarı frezeleme (aynı yönlü) kullanın."))
        }
        if (life.outsideModel) advice += Advice.info(toolLifeOutsideText(L))
        if (m.notes.isNotBlank()) advice += Advice.info(L.tx(m.notes))

        return MillingResult(
            input = input, ap = ap, ae = aeEff, effectiveDiameter = deff, leadAngleDeg = lead, rakeDeg = rake,
            vcRecommended = vcRec, vc = vc, rpm = rpm, hmaxTarget = hmaxTarget, fz = fz,
            chipThinningFactor = thinning, hmax = fz / (axialThinning * radialThinning),
            feed = feed, mrr = mrr, engagementAngleDeg = phiE.toDeg(), forces = forces,
            powerKw = power, availablePowerKw = available, torqueNm = torque,
            deflection = defl?.tipDeflection ?: 0.0, deflectionLimit = limit,
            hasDeflection = defl != null, allowableBendingStress = allowable, apMaxInsert = apInsert, bendingStress = defl?.maxBendingStress ?: 0.0,
            torsionStress = torsion, toolLife = life, toolLifePath = feed * life.minutes / 1000.0,
            toolLifeVolume = mrr * life.minutes, cuspHeight = cusp,
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
