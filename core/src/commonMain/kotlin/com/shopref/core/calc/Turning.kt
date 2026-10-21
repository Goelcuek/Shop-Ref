package com.shopref.core.calc

import com.shopref.core.i18n.Lang
import com.shopref.core.material.Family
import com.shopref.core.material.IsoGroup
import com.shopref.core.material.Material
import com.shopref.core.tooling.Machine
import com.shopref.core.tooling.ToolCondition
import com.shopref.core.units.UnitSystem
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

enum class InsertGrade(val label: String, val taylorN: Double) {
    COATED_CARBIDE("Coated carbide (CVD/PVD)", 0.3),
    UNCOATED_CARBIDE("Uncoated carbide", 0.25),
    CERMET("Cermet", 0.3),
    CERAMIC("Ceramic", 0.5),
    CBN("CBN", 0.5),
    PCD("PCD", 0.4),
    HSS("HSS tool bit", 0.125),
    ;

    /** Speed factor relative to coated carbide, or null if unsuitable for the family. */
    fun speedFactor(family: Family): Double? = when (this) {
        COATED_CARBIDE -> 1.0
        UNCOATED_CARBIDE -> 0.65
        CERMET -> when (family) {
            Family.STEEL -> 1.15
            Family.STAINLESS -> 1.05
            Family.CAST_IRON -> 0.9
            Family.ALUMINUM, Family.COPPER, Family.MAGNESIUM -> 1.0
            else -> null
        }
        CERAMIC -> when (family) {
            Family.CAST_IRON -> 3.0
            Family.NICKEL -> 5.0
            Family.HARDENED -> 1.5
            else -> null
        }
        CBN -> when (family) {
            Family.HARDENED -> 2.5
            Family.CAST_IRON -> 3.0
            else -> null
        }
        PCD -> when (family) {
            Family.ALUMINUM, Family.MAGNESIUM, Family.COPPER, Family.PLASTIC, Family.COMPOSITE, Family.GRAPHITE -> 2.5
            else -> null
        }
        HSS -> when (family) {
            Family.HARDENED -> null
            Family.ALUMINUM, Family.MAGNESIUM, Family.COPPER, Family.PLASTIC -> 0.3
            else -> 0.2
        }
    }
}

enum class TurnOperation(val label: String, val speedFactor: Double, val feedFactor: Double, val depthFactor: Double) {
    ROUGHING("Roughing", 0.85, 1.0, 1.0),
    MEDIUM("Medium machining", 1.0, 0.6, 0.5),
    FINISHING("Finishing", 1.15, 0.3, 0.15),
}

data class TurningInput(
    val material: Material,
    val grade: InsertGrade = InsertGrade.COATED_CARBIDE,
    val operation: TurnOperation = TurnOperation.MEDIUM,
    /** Workpiece diameter being cut [mm]. */
    val diameter: Double = 50.0,
    /** Depth of cut (radial) [mm]; null = recommended. */
    val ap: Double? = null,
    /** Feed per revolution [mm/rev]; null = recommended. */
    val feed: Double? = null,
    /** Insert nose radius [mm]. */
    val noseRadius: Double = 0.8,
    /** Entering (lead) angle κr [deg] (95° for CNMG/DNMG holders). */
    val enteringAngle: Double = 95.0,
    /** Effective rake angle [deg]. */
    val rakeDeg: Double = 6.0,
    /** Length of cut [mm] for cycle time. */
    val lengthOfCut: Double = 100.0,
    val condition: ToolCondition = ToolCondition.NORMAL,
    val speedPercent: Double = 100.0,
    val machine: Machine = Machine.PRESETS.last(),
    val units: UnitSystem = UnitSystem.METRIC,
    val lang: Lang = Lang.EN,
)

data class TurningResult(
    val input: TurningInput,
    val ap: Double,
    val feed: Double,
    val vcRecommended: Double,
    val vc: Double,
    val rpm: Double,
    val feedRate: Double,
    val mrr: Double,
    val chipThickness: Double,
    val kc: Double,
    /** Main (tangential) cutting force [N]. */
    val fc: Double,
    /** Feed (axial) force [N]. */
    val ff: Double,
    /** Passive (radial) force [N]. */
    val fp: Double,
    val powerKw: Double,
    val availablePowerKw: Double,
    val torqueNm: Double,
    /** Theoretical peak-to-valley roughness Rt = f²/(8·rε) [µm]. */
    val rtMicron: Double,
    /** Theoretical Ra ≈ f²/(32·rε) [µm]. */
    val raMicron: Double,
    val timeMin: Double,
    /** Estimated insert edge life (extended Taylor, anchored at the recommendation). */
    val toolLife: ToolLife.Estimate,
    /** Passes of the given length of cut per edge, or null without a length. */
    val passesPerEdge: Double?,
    val advice: List<Advice>,
)

/**
 * Longitudinal turning. Kienzle: h = f·sin κr, b = ap / sin κr, Fc = kc1.1·b·h^(1−mc).
 * Thrust in the plane normal to the cutting speed follows Merchant: Ft = Fc·tan(β − γ), split
 * into feed force Ff = Ft·sin κr and passive force Fp = Ft·cos κr. Surface finish uses the
 * geometric relations Rt = f²/(8·rε), Ra ≈ f²/(32·rε) (= Rt/4). Power Pc = Fc·vc/60 000 [kW].
 */
object TurningCalculator {

    private fun roughFeed(g: IsoGroup) = when (g) {
        IsoGroup.P -> 0.35; IsoGroup.M -> 0.30; IsoGroup.K -> 0.40
        IsoGroup.N -> 0.30; IsoGroup.S -> 0.20; IsoGroup.H -> 0.15
    }

    private fun roughDepth(g: IsoGroup) = when (g) {
        IsoGroup.P -> 3.0; IsoGroup.M -> 2.5; IsoGroup.K -> 4.0
        IsoGroup.N -> 4.0; IsoGroup.S -> 1.5; IsoGroup.H -> 0.5
    }

    fun defaultFeed(input: TurningInput): Double {
        val f = roughFeed(input.material.group) * input.operation.feedFactor
        return if (input.operation == TurnOperation.FINISHING) minOf(f, 0.5 * input.noseRadius).coerceAtLeast(0.04) else f
    }

    fun defaultDepth(input: TurningInput): Double {
        val d = roughDepth(input.material.group) * input.operation.depthFactor
        return if (input.operation == TurnOperation.FINISHING) max(d, 0.1) else d
    }

    fun calculate(input: TurningInput): TurningResult {
        val m = input.material
        val f = Fmt(input.units, input.lang)
        val L = input.lang
        val advice = mutableListOf<Advice>()
        val ap = (input.ap ?: defaultDepth(input)).coerceAtLeast(0.01)
        val fn = (input.feed ?: defaultFeed(input)).coerceAtLeast(0.005)
        val dia = input.diameter.coerceAtLeast(0.5)

        val gradeFactor = input.grade.speedFactor(m.family) ?: run {
            advice += Advice.danger(
                L.t(
                    "${input.grade.label} is not suitable for ${m.name.lowercase()}.",
                    "${L.tx(input.grade.label)}, ${L.tx(m.name)} için uygun değil.",
                ),
            )
            1.0
        }
        val vcRec = m.turnVc * gradeFactor * input.operation.speedFactor
        var rpm = CuttingBasics.rpm(vcRec * input.speedPercent / 100.0, dia)
        val machine = input.machine
        if (rpm > machine.maxRpm) {
            val vcMax = f.speed(CuttingBasics.cuttingSpeed(machine.maxRpm, dia))
            advice += Advice.caution(
                L.t(
                    "Spindle limited to ${f.rpm(machine.maxRpm)} — cutting speed $vcMax.",
                    "İş mili ${f.rpm(machine.maxRpm)} ile sınırlı — kesme hızı $vcMax.",
                ),
            )
            rpm = machine.maxRpm
        }
        if (rpm < machine.minRpm) rpm = machine.minRpm
        val vc = CuttingBasics.cuttingSpeed(rpm, dia)
        val feedRate = fn * rpm

        // Kienzle with entering angle; κr > 90° behaves like 180° − κr for the chip section.
        val kr = input.enteringAngle.coerceIn(10.0, 170.0)
        val sinK = sin(kr.toRad())
        val h = fn * sinK
        val b = ap / sinK
        val kc = Kienzle.kc(m, h, input.rakeDeg, input.condition.forceFactor)
        val fc = Kienzle.force(m, b, h, input.rakeDeg, input.condition.forceFactor)
        val thrust = fc * tan((m.frictionAngle - input.rakeDeg).coerceIn(5.0, 60.0).toRad())
        // Thrust direction follows the length-weighted mean edge angle of the engaged edge
        // (nose arc + straight edge), so small depths push mostly radially.
        val kEdge = if (kr > 90.0) 180.0 - kr else kr
        val r = input.noseRadius.coerceAtLeast(0.0)
        val kEff = when {
            r <= 0.0 -> kEdge
            ap < r -> kotlin.math.acos(1.0 - ap / r).toDeg() / 2.0
            else -> {
                val arcLen = kotlin.math.PI * r / 2.0
                val straight = ap - r
                (45.0 * arcLen + kEdge * straight) / (arcLen + straight)
            }
        }
        val ff = thrust * sin(kEff.toRad())
        val fp = thrust * cos(kEff.toRad())
        val power = fc * vc / 60_000.0
        val available = machine.availablePowerKw(rpm)
        val torque = fc * dia / 2.0 / 1000.0
        val mrr = vc * ap * fn
        val rt = fn * fn / (8.0 * input.noseRadius) * 1000.0
        val ra = fn * fn / (32.0 * input.noseRadius) * 1000.0
        val time = input.lengthOfCut / max(feedRate, 1e-6)

        if (power > available) {
            val apMax = f.len(ap * available / power)
            advice += Advice.danger(
                L.t(
                    "Needs ${f.power(power)} but only ${f.power(available)} is available — reduce depth to ≈$apMax.",
                    "${f.power(power)} güç gerekiyor ama yalnızca ${f.power(available)} mevcut — paso derinliğini ≈$apMax değerine düşürün.",
                ),
            )
        } else if (power > 0.85 * available) {
            advice += Advice.caution(powerShareText(L, power, available))
        }
        if (machine.maxTorqueNm > 0 && torque > machine.maxTorqueNm) {
            advice += Advice.danger(
                L.t(
                    "Spindle torque ${f.torque(torque)} exceeds the machine's ${f.torque(machine.maxTorqueNm)}.",
                    "İş mili torku ${f.torque(torque)}, makinenin ${f.torque(machine.maxTorqueNm)} sınırını aşıyor.",
                ),
            )
        }
        if (ap < 0.66 * input.noseRadius) {
            advice += Advice.caution(
                L.t(
                    "Depth of cut is less than ⅔ of the nose radius: poor chip control and vibration.",
                    "Paso derinliği uç radyüsünün ⅔'ünden küçük: kötü talaş kontrolü ve titreşim.",
                ),
            )
        }
        if (fn > 0.8 * input.noseRadius) {
            advice += Advice.caution(
                L.t(
                    "Feed exceeds 0.8× nose radius: risk of edge breakage and poor finish.",
                    "İlerleme uç radyüsünün 0,8 katını aşıyor: kenar kırılması ve kötü yüzey riski.",
                ),
            )
        }
        if (fn < 0.05 && m.group == IsoGroup.M) {
            advice += Advice.caution(L.t("Very light feed in stainless causes work hardening.", "Paslanmazda çok düşük ilerleme pekleşmeye yol açar."))
        }
        if (m.group == IsoGroup.H && input.grade == InsertGrade.COATED_CARBIDE) {
            advice += Advice.info(
                L.t(
                    "CBN or mixed ceramic inserts give much higher speeds and tool life in hardened steel.",
                    "Sertleştirilmiş çelikte CBN veya karışık seramik uçlar çok daha yüksek hız ve ömür sağlar.",
                ),
            )
        }
        if (m.notes.isNotBlank()) advice += Advice.info(L.tx(m.notes))

        val life = ToolLife.estimate(
            (if (input.grade == InsertGrade.HSS) 60.0 else 15.0) * ToolLife.slowWearFactor(m), input.grade.taylorN,
            vRef = vcRec, v = vc, hRef = defaultFeed(input), h = fn,
        )
        if (life.outsideModel) advice += Advice.info(toolLifeOutsideText(L))

        return TurningResult(
            input = input, ap = ap, feed = fn, vcRecommended = vcRec, vc = vc, rpm = rpm, feedRate = feedRate,
            mrr = mrr, chipThickness = h, kc = kc, fc = fc, ff = ff, fp = fp, powerKw = power,
            availablePowerKw = available, torqueNm = torque, rtMicron = rt, raMicron = ra, timeMin = time,
            toolLife = life, passesPerEdge = if (input.lengthOfCut > 0) life.minutes / time else null,
            advice = advice,
        )
    }

    /** Feed per rev [mm] needed for a target Ra [µm] with nose radius [r] (mm): f = √(32·r·Ra/1000). */
    fun feedForRa(raMicron: Double, noseRadius: Double): Double = kotlin.math.sqrt(32.0 * noseRadius * raMicron / 1000.0)
}
