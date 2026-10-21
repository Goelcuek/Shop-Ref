package com.shopref.core.calc

import com.shopref.core.i18n.Lang
import com.shopref.core.material.IsoGroup
import com.shopref.core.material.Material
import com.shopref.core.reference.DrillSize
import com.shopref.core.reference.DrillSizes
import com.shopref.core.reference.ThreadMath
import com.shopref.core.reference.ThreadSpec
import com.shopref.core.tooling.Coating
import com.shopref.core.tooling.Machine
import com.shopref.core.tooling.ToolCondition
import com.shopref.core.tooling.ToolMaterial
import com.shopref.core.units.UnitSystem
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow

enum class TapType(val label: String, val speedFactor: Double, val isForming: Boolean) {
    STRAIGHT_FLUTE("Straight flute (hand / plug)", 0.8, false),
    SPIRAL_POINT("Spiral point (gun) — through holes", 1.1, false),
    SPIRAL_FLUTE("Spiral flute — blind holes", 0.9, false),
    FORM("Form / roll tap (fluteless)", 1.2, true),
}

/** Chamfer (lead) form, by the number of chamfered threads (ISO 8830 / DIN 2197 forms). */
enum class TapChamfer(val label: String, val threads: Double) {
    TAPER("Taper (form A, 6–8 thr)", 7.0),
    PLUG("Plug (form B/D, 3.5–5 thr)", 4.0),
    SEMI_BOTTOMING("Semi-bottoming (form C, 2–3 thr)", 2.5),
    BOTTOMING("Bottoming (form E, 1.5–2 thr)", 1.5),
}

data class TappingInput(
    val material: Material,
    val thread: ThreadSpec,
    val tapType: TapType = TapType.SPIRAL_FLUTE,
    val chamfer: TapChamfer = TapChamfer.SEMI_BOTTOMING,
    val tapMaterial: ToolMaterial = ToolMaterial.HSS_CO,
    val coating: Coating = Coating.TICN,
    /** Flutes (or lobes for form taps). */
    val flutes: Int = 3,
    val blindHole: Boolean = true,
    /** Thread depth [mm]. */
    val threadDepth: Double = 15.0,
    /** Target thread engagement [%]; null = recommended (75% cut / 65% form, less in tough materials). */
    val targetPercent: Double? = null,
    /** Actual drill used [mm]; null = use recommended drill. */
    val drillDiameter: Double? = null,
    val condition: ToolCondition = ToolCondition.NORMAL,
    val speedPercent: Double = 100.0,
    val machine: Machine = Machine(),
    val units: UnitSystem = UnitSystem.METRIC,
    val lang: Lang = Lang.EN,
)

data class TappingResult(
    val input: TappingInput,
    val vcRecommended: Double,
    val vc: Double,
    val rpm: Double,
    /** Feed rate = pitch × rpm [mm/min]. */
    val feed: Double,
    val targetPercent: Double,
    /** Theoretical drill for the target %. */
    val idealDrill: Double,
    /** Recommended standard drill. */
    val recommendedDrill: DrillSize,
    /** Drill actually used for the calculation [mm]. */
    val drill: Double,
    val percentThread: Double,
    /** Radial thread depth cut/formed [mm]. */
    val radialDepth: Double,
    /** Chip thickness per tooth on the chamfer [mm]. */
    val chipThickness: Double,
    val torqueNm: Double,
    val breakingTorqueNm: Double,
    val safetyFactor: Double,
    val powerKw: Double,
    /** Minimum drill depth for a blind hole with the requested full-thread depth [mm]. */
    val minDrillDepth: Double,
    /** Estimated tool life in cutting time (extended Taylor, anchored at the recommendation). */
    val toolLife: ToolLife.Estimate,
    /** Holes threaded within the estimated tool life, or null without a thread depth. */
    val holesPerTap: Double?,
    val advice: List<Advice>,
)

/**
 * Tapping. Torque follows from an energy balance on the thread groove removed per revolution
 * (Kienzle specific energy × volume): per revolution the tap generates one helix of groove,
 * volume A·π·dm, so
 *
 *   M = kc · A · dm / 2,   A = 0.125·P·t + tan30°·t²   (groove section for radial depth t)
 *
 * With full depth (t = 0.5413·P) this gives M ≈ kc·P²·D/8450 N·m, matching the classic tap-maker
 * rule M ≈ kc·P²·D/8000. kc is evaluated at the per-tooth chip thickness h = t/(chamfer threads ×
 * flutes). Flank/land friction and chip packing in blind holes (Cao & Sutherland 2002) are
 * covered by process factors; thread forming torque is taken as 2× the cutting torque for the
 * same engagement (industry data: 1.5–3×).
 *
 * Tap torsional strength: T_break = τu·π·dc³/16 with dc ≈ 0.6·minor diameter for fluted taps
 * (core/web) and 0.8·minor for fluteless form taps.
 */
object TappingCalculator {

    fun recommendedPercent(material: Material, forming: Boolean): Double = when {
        forming -> 65.0
        material.group == IsoGroup.S || material.group == IsoGroup.H -> 60.0
        material.group == IsoGroup.M || material.hardnessHb > 300 -> 65.0
        else -> 75.0
    }

    private fun tapRake(m: Material) = when (m.group) {
        IsoGroup.N -> 12.0
        IsoGroup.P, IsoGroup.M, IsoGroup.S -> 8.0
        IsoGroup.K -> 4.0
        IsoGroup.H -> 2.0
    }

    private fun substrateFactor(t: ToolMaterial) = when (t) {
        ToolMaterial.HSS -> 0.8
        ToolMaterial.HSS_CO -> 1.0
        ToolMaterial.PM_HSS -> 1.2
        ToolMaterial.CARBIDE -> 2.0
        else -> 1.0
    }

    fun calculate(input: TappingInput): TappingResult {
        val m = input.material
        val th = input.thread
        val f = Fmt(input.units, input.lang)
        val L = input.lang
        val advice = mutableListOf<Advice>()
        val d = th.majorDiameter
        val p = th.pitch
        val forming = input.tapType.isForming

        // --- Tap drill -------------------------------------------------------------------------------
        val target = input.targetPercent ?: recommendedPercent(m, forming)
        val ideal = if (forming) ThreadMath.drillForPercentForm(d, p, target) else ThreadMath.drillForPercentCut(d, p, target)
        val drillSet = if (th.standard.isInch && !th.standard.isPipe) DrillSizes.inchAll + DrillSizes.metric else DrillSizes.metric
        val recommended = DrillSizes.nearest(ideal, drillSet)
        val drill = input.drillDiameter ?: recommended.mm
        val percent = if (forming) ThreadMath.percentThreadForm(d, p, drill) else ThreadMath.percentThreadCut(d, p, drill)
        if (drill < th.minorDiameterMin - 1e-6 && !forming) {
            advice += Advice.caution(
                L.t(
                    "Drill is below the minimum minor diameter (${f.len(th.minorDiameterMin)}): high torque, tap breakage risk.",
                    "Matkap minimum diş dibi çapının (${f.len(th.minorDiameterMin)}) altında: yüksek tork, kılavuz kırılma riski.",
                ),
            )
        }
        th.minorDiameterMax?.let { maxMinor ->
            if (!forming && drill > maxMinor + 1e-6) {
                advice += Advice.caution(
                    L.t(
                        "Drill exceeds the maximum minor diameter (${f.len(maxMinor)}): thread will be out of tolerance.",
                        "Matkap maksimum diş dibi çapını (${f.len(maxMinor)}) aşıyor: diş tolerans dışı kalır.",
                    ),
                )
            }
        }
        if (percent > 85 && !forming) {
            advice += Advice.caution(
                L.t(
                    "Above ≈85% thread adds torque but almost no strength.",
                    "≈%85 üzeri diş oluşma oranı torku artırır ama mukavemete neredeyse hiç katkı vermez.",
                ),
            )
        }
        if (forming && !m.formTappable) {
            advice += Advice.danger(
                L.t(
                    "Form tapping is not recommended for ${m.name.lowercase()} (brittle / low ductility). Use a cutting tap.",
                    "${L.tx(m.name)} için ezme (form) kılavuz önerilmez (gevrek / düşük süneklik). Kesme kılavuzu kullanın.",
                ),
            )
        }

        // --- Speed -----------------------------------------------------------------------------------
        val coatingFactor = (input.coating.speedFactor(m.family) ?: 1.0).coerceAtMost(1.3)
        input.coating.incompatibilityReason(m.family, L)?.let { advice += Advice.caution(it) }
        var vcRec = m.tapVc * substrateFactor(input.tapMaterial) * coatingFactor * input.tapType.speedFactor
        if (m.tapVc <= 0.0) {
            advice += Advice.danger(
                L.t(
                    "Tapping ${m.name.lowercase()} is not recommended — thread mill instead.",
                    "${L.tx(m.name)} malzemeye kılavuz çekmek önerilmez — diş frezesi kullanın.",
                ),
            )
            vcRec = 1.0
        }
        if (m.group == IsoGroup.H && input.tapMaterial != ToolMaterial.CARBIDE) {
            advice += Advice.danger(
                L.t("Only carbide taps are suitable for hardened material.", "Sertleştirilmiş malzemede yalnızca karbür kılavuz kullanılabilir."),
            )
        }
        var rpm = CuttingBasics.rpm(vcRec * input.speedPercent / 100.0, d)
        val maxTapRpm = (input.machine.maxRpm * 0.5).coerceAtLeast(input.machine.minRpm)
        if (rpm > maxTapRpm) {
            advice += Advice.info(
                L.t(
                    "Limited to ${f.rpm(maxTapRpm)} (≈50% of spindle max) so the spindle can reverse at depth.",
                    "İş mili dipte yön değiştirebilsin diye ${f.rpm(maxTapRpm)} ile sınırlandı (maks. devrin ≈%50'si).",
                ),
            )
            rpm = maxTapRpm
        }
        val feed = rpm * p
        if (feed > input.machine.maxFeed) {
            rpm = input.machine.maxFeed / p
            advice += Advice.caution(feedLimitedText(L, f.rpm(rpm)))
        }
        val vc = CuttingBasics.cuttingSpeed(rpm, d)

        // --- Torque ----------------------------------------------------------------------------------
        val t = ((d - drill) / 2.0).coerceAtLeast(0.0)
        val area = 0.125 * p * t + kotlin.math.tan(30.0.toRad()) * t * t
        val teeth = input.chamfer.threads * input.flutes.coerceAtLeast(1)
        val h = (t / teeth).coerceAtLeast(0.002)
        val kc = Kienzle.kc(m, h, tapRake(m), input.condition.forceFactor)
        val dm = (d + drill) / 2.0
        val baseTorque = kc * area * dm / 2.0 / 1000.0
        val processFactor = 1.3 * when {
            forming -> 2.0
            !input.blindHole && input.tapType == TapType.SPIRAL_POINT -> 1.0
            !input.blindHole -> 1.15
            input.tapType == TapType.SPIRAL_FLUTE -> 1.2
            input.tapType == TapType.SPIRAL_POINT -> 1.6
            else -> 1.6
        }
        val torque = baseTorque * processFactor
        val core = (if (forming) 0.8 else 0.6) * th.minorDiameterMin
        val tau = input.tapMaterial.shearStrength
        val breaking = tau * PI * core.pow(3) / 16.0 / 1000.0
        val sf = if (torque > 0) breaking / torque else Double.POSITIVE_INFINITY
        val power = CuttingBasics.powerKw(torque, rpm)

        // --- Hole depth --------------------------------------------------------------------------------
        val chamferLength = input.chamfer.threads * p
        val minDrillDepth = input.threadDepth + chamferLength + p + 0.3 * drill

        // --- Advice ----------------------------------------------------------------------------------
        when {
            sf < 1.5 -> advice += Advice.danger(
                L.t(
                    "Torque ≈ ${f.torque(torque)} is close to the tap's estimated breaking torque (${f.torque(breaking)}).",
                    "Tork ≈ ${f.torque(torque)}, kılavuzun tahmini kırılma torkuna (${f.torque(breaking)}) çok yakın.",
                ),
            )
            sf < 2.5 -> advice += Advice.caution(
                L.t(
                    "Tap safety factor only ${f.num(sf)} — use a torque-limiting holder or reduce % thread.",
                    "Kılavuz emniyet katsayısı yalnızca ${f.num(sf)} — tork sınırlamalı tutucu kullanın ya da diş oluşma oranını azaltın.",
                ),
            )
        }
        if (input.blindHole && input.tapType == TapType.SPIRAL_POINT) {
            advice += Advice.caution(
                L.t(
                    "Spiral point taps push chips ahead — use them in through holes (or leave a deep sump).",
                    "Spiral uçlu kılavuz talaşı ileri iter — boydan boya delikte kullanın (ya da derin talaş cebi bırakın).",
                ),
            )
        }
        if (!input.blindHole && input.tapType == TapType.SPIRAL_FLUTE && m.group != IsoGroup.N) {
            advice += Advice.info(
                L.t("Spiral point taps are stronger and faster for through holes.", "Boydan boya delikte spiral uçlu kılavuz daha sağlam ve hızlıdır."),
            )
        }
        if (input.blindHole && input.chamfer == TapChamfer.TAPER) {
            advice += Advice.caution(
                L.t(
                    "Taper chamfer needs ${f.len(chamferLength)} of extra depth in blind holes.",
                    "Konik giriş kör delikte ${f.len(chamferLength)} ek derinlik ister.",
                ),
            )
        }
        if (m.group == IsoGroup.K && input.tapType == TapType.SPIRAL_FLUTE) {
            advice += Advice.info(
                L.t(
                    "Cast iron makes short chips: straight-flute taps work well even in blind holes.",
                    "Döküm kısa talaş verir: düz kanallı kılavuz kör delikte bile iyi çalışır.",
                ),
            )
        }
        if (input.threadDepth > 2.5 * d) {
            advice += Advice.caution(
                L.t(
                    "Thread deeper than 2.5×D adds torque without strength; 1–1.5×D engagement is enough in steel.",
                    "2,5×D'den derin diş mukavemet katmadan torku artırır; çelikte 1–1,5×D diş boyu yeterlidir.",
                ),
            )
        }
val clutch = f.torque(minOf(torque * 1.5, breaking * 0.7))
        advice += Advice.info(
            L.t(
                "Holder: rigid tapping if synchronised, otherwise tension/compression. Set a torque clutch to ≈$clutch.",
                "Tutucu: senkron ise rijit kılavuz çekme, değilse yaylı (çekme/basma) tutucu. Tork kavramasını ≈$clutch değerine ayarlayın.",
            ),
        )
        advice += Advice.info(
            L.t(
                "Blind hole: drill at least ${f.len(minDrillDepth)} deep for ${f.len(input.threadDepth)} of full thread.",
                "Kör delik: ${f.len(input.threadDepth)} tam diş için en az ${f.len(minDrillDepth)} derin delin.",
            ),
        )
        // Feed per revolution is the pitch, so only speed moves the tap along its Taylor curve.
        val life = ToolLife.estimate(ToolLife.referenceMinutes(input.tapMaterial, material = m), input.tapMaterial.taylorN, vRef = vcRec, v = vc)
        if (life.outsideModel) advice += Advice.info(toolLifeOutsideText(L))
        val tapTime = input.threadDepth / max(rpm * p, 1e-6)

        return TappingResult(
            input = input, vcRecommended = vcRec, vc = vc, rpm = rpm, feed = rpm * p, targetPercent = target,
            idealDrill = ideal, recommendedDrill = recommended, drill = drill, percentThread = percent,
            radialDepth = t, chipThickness = h, torqueNm = torque, breakingTorqueNm = breaking,
            safetyFactor = sf, powerKw = power, minDrillDepth = minDrillDepth, toolLife = life,
            holesPerTap = if (input.threadDepth > 0) life.minutes / tapTime else null, advice = advice,
        )
    }

    /** Thread milling: programmed tool-centre feed for an internal (or external) thread. */
    data class ThreadMillResult(val rpm: Double, val peripheralFeed: Double, val centreFeed: Double, val radialPasses: Int)

    fun threadMill(
        material: Material, thread: ThreadSpec, toolDiameter: Double, flutes: Int,
        internal: Boolean = true, machine: Machine = Machine(),
    ): ThreadMillResult {
        val vc = material.vcCarbide * 0.8 * (Coating.TIALN.speedFactor(material.family) ?: 1.0)
        val rpm = CuttingBasics.rpm(vc, toolDiameter).coerceAtMost(machine.maxRpm)
        val fz = (material.fzPerD * toolDiameter * 0.5).coerceAtLeast(0.005)
        val peripheral = fz * flutes * rpm
        val centre = MillingCalculator.arcFeed(peripheral, toolDiameter, thread.majorDiameter, internal)
        val depth = 0.6134 * thread.pitch
        val passes = when {
            material.group == IsoGroup.N -> 1
            material.group == IsoGroup.S || material.group == IsoGroup.H -> if (depth > 0.8) 3 else 2
            depth > 1.0 -> 2
            else -> 1
        }
        return ThreadMillResult(rpm, peripheral, centre, passes)
    }
}
