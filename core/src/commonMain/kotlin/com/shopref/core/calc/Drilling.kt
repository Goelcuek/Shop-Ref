package com.shopref.core.calc

import com.shopref.core.i18n.Lang
import com.shopref.core.material.Material
import com.shopref.core.tooling.Coating
import com.shopref.core.tooling.Machine
import com.shopref.core.tooling.ToolCondition
import com.shopref.core.tooling.ToolMaterial
import com.shopref.core.units.UnitSystem
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

enum class DrillType(
    val label: String,
    /** Point angle [deg] (2·κr). For reamers this is twice the chamfer lead angle. */
    val pointAngle: Double,
    val substrate: ToolMaterial,
    /**
     * Chisel-edge thrust as a multiple of the lip thrust. A conventional (un-thinned) web
     * produces roughly half or more of the total thrust (Shaw & Oxford 1957; MMC drilling
     * guide: 50–70%); split points and thinned webs reduce it.
     */
    val chiselFactor: Double,
    val speedFactor: Double,
    val feedFactor: Double,
) {
    HSS_TWIST("HSS jobber drill, 118°", 118.0, ToolMaterial.HSS, 1.0, 0.9, 0.7),
    COBALT_SPLIT("HSS-Co split point, 135°", 135.0, ToolMaterial.HSS_CO, 0.45, 0.9, 0.75),
    CARBIDE("Solid carbide drill, 140°", 140.0, ToolMaterial.CARBIDE, 0.3, 0.8, 1.0),
    CARBIDE_COOLANT("Solid carbide, through-coolant", 140.0, ToolMaterial.CARBIDE, 0.3, 0.9, 1.1),
    INDEXABLE("Indexable insert drill", 180.0, ToolMaterial.CARBIDE, 0.1, 0.8, 1.0),
    SPOT("Spot drill, 90°", 90.0, ToolMaterial.CARBIDE, 0.5, 0.8, 0.5),
    REAMER("Carbide reamer", 90.0, ToolMaterial.CARBIDE, 0.0, 0.4, 1.5),
}

data class DrillingInput(
    val material: Material,
    val drillType: DrillType = DrillType.CARBIDE,
    val coating: Coating = Coating.TIALN,
    /** Drill diameter [mm]. */
    val diameter: Double = 10.0,
    /** Hole depth (full diameter) [mm]. */
    val depth: Double = 30.0,
    /** Pre-drilled hole for reaming / core drilling [mm]; 0 = solid. */
    val preHoleDiameter: Double = 0.0,
    /** Flutes (reamers only; drills use 2 cutting lips). */
    val flutes: Int = 6,
    val condition: ToolCondition = ToolCondition.NORMAL,
    val speedPercent: Double = 100.0,
    val feedPercent: Double = 100.0,
    val machine: Machine = Machine(),
    val units: UnitSystem = UnitSystem.METRIC,
    val lang: Lang = Lang.EN,
)

data class DrillingResult(
    val input: DrillingInput,
    val vcRecommended: Double,
    val vc: Double,
    val rpm: Double,
    /** Feed per revolution [mm/rev]. */
    val fn: Double,
    val feed: Double,
    /** Chip thickness per lip [mm]. */
    val chipThickness: Double,
    val kc: Double,
    val torqueNm: Double,
    /** Axial thrust force [N]. */
    val thrust: Double,
    val powerKw: Double,
    val availablePowerKw: Double,
    val mrr: Double,
    /** Cutting time for the hole depth plus the point length [min]. */
    val timeMin: Double,
    /** Length of the drill point (tip to full diameter) [mm]. */
    val pointLength: Double,
    val peckAdvice: String,
    /** Recommended pre-drill size for reaming [mm], if relevant. */
    val reamAllowance: Double?,
    /** Estimated tool life (extended Taylor, anchored at the recommendation). */
    val toolLife: ToolLife.Estimate,
    /** Holes of the given depth within the estimated tool life, or null without a depth. */
    val holesPerTool: Double?,
    /** Length drilled within the estimated tool life [m], or null without a depth. */
    val toolLifeLength: Double?,
    val advice: List<Advice>,
)

/**
 * Drilling, reaming and core drilling. For z cutting edges with lead angle κr = point/2, each
 * edge cuts a chip of thickness h = (fn/z)·sin κr and width b = (D − d0)/(2·sin κr). With the
 * Kienzle cutting force Fc,e = kc1.1·b·h^(1−mc):
 *
 *   torque     Mc = z·Fc,e·(D + d0)/4                ⇒ kc·fn·D²/8000 [N·m] for solid drilling
 *   lip thrust Ff = z·0.5·Fc,e·sin κr                (Sandvik: Ff ≈ 0.5·kc·(D/2)·fn·sin κr)
 *   total thrust = lip thrust · (1 + chisel factor)   (solid drilling only)
 */
object DrillingCalculator {
    private const val DRILL_RAKE = 12.0

    fun reamAllowance(d: Double): Double = when {
        d <= 5 -> 0.15
        d <= 10 -> 0.2
        d <= 20 -> 0.3
        d <= 30 -> 0.4
        else -> 0.5
    }

    fun calculate(input: DrillingInput): DrillingResult {
        val m = input.material
        val f = Fmt(input.units, input.lang)
        val L = input.lang
        val advice = mutableListOf<Advice>()
        val d = input.diameter.coerceAtLeast(0.1)
        val type = input.drillType
        val isReamer = type == DrillType.REAMER
        val d0 = when {
            isReamer && input.preHoleDiameter <= 0.0 -> d - reamAllowance(d)
            else -> input.preHoleDiameter.coerceIn(0.0, d * 0.98)
        }
        val edges = if (isReamer) input.flutes.coerceAtLeast(2) else 2
        val ld = input.depth / d

        // --- Speed -----------------------------------------------------------------------------
        val substrate = type.substrate
        val baseSpeed = when {
            type == DrillType.INDEXABLE -> m.turnVc * type.speedFactor
            substrate.isHss -> {
                if (m.vcHss <= 0) {
                    advice += Advice.danger(
                        L.t(
                            "HSS drills are not suitable for ${m.name.lowercase()} — use carbide.",
                            "HSS matkaplar ${L.tx(m.name)} için uygun değil — karbür kullanın.",
                        ),
                    )
                    m.vcCarbide * 0.25
                } else m.vcHss * type.speedFactor * (if (substrate == ToolMaterial.HSS_CO) 1.15 else 1.0)
            }
            else -> m.vcCarbide * type.speedFactor
        }
        val coatingFactor = if (type == DrillType.INDEXABLE) 1.0 else input.coating.speedFactor(m.family) ?: run {
            input.coating.incompatibilityReason(m.family, L)?.let { advice += Advice.caution(it) }
            1.0
        }
        val coolant = type == DrillType.CARBIDE_COOLANT
        val (depthSpeed, depthFeed) = when {
            isReamer || type == DrillType.SPOT -> 1.0 to 1.0
            ld <= 3 -> 1.0 to 1.0
            ld <= 5 -> (if (coolant) 1.0 else 0.95) to (if (coolant) 1.0 else 0.9)
            ld <= 8 -> (if (coolant) 0.95 else 0.85) to (if (coolant) 0.95 else 0.8)
            ld <= 12 -> (if (coolant) 0.9 else 0.75) to (if (coolant) 0.85 else 0.7)
            else -> (if (coolant) 0.85 else 0.65) to (if (coolant) 0.8 else 0.6)
        }
        val vcRec = baseSpeed * coatingFactor * depthSpeed
        var rpm = CuttingBasics.rpm(vcRec * input.speedPercent / 100.0, d)

        // --- Feed ------------------------------------------------------------------------------
        val substrateFeed = if (type == DrillType.INDEXABLE) 0.2 else type.feedFactor
        val fnRaw = m.drillFnPerD * d * substrateFeed * depthFeed * input.machine.rigidity.feedFactor.coerceAtLeast(0.85)
        val fnClamped = when (type) {
            DrillType.INDEXABLE -> fnRaw.coerceIn(0.04, 0.25)
            else -> fnRaw.coerceIn(0.005, 0.8)
        }
        val fn = fnClamped * input.feedPercent / 100.0

        // --- Machine limits ------------------------------------------------------------------------
        val machine = input.machine
        if (rpm > machine.maxRpm) {
            val vcMax = f.speed(CuttingBasics.cuttingSpeed(machine.maxRpm, d))
            advice += Advice.caution(
                L.t(
                    "Spindle limited to ${f.rpm(machine.maxRpm)} (cutting speed $vcMax).",
                    "İş mili ${f.rpm(machine.maxRpm)} ile sınırlı (kesme hızı $vcMax).",
                ),
            )
            rpm = machine.maxRpm
        }
        if (rpm < machine.minRpm) rpm = machine.minRpm
        var feed = fn * rpm
        if (feed > machine.maxFeed) {
            rpm = machine.maxFeed / fn
            feed = machine.maxFeed
            advice += Advice.caution(feedLimitedText(L, f.rpm(rpm)))
        }
        val vc = CuttingBasics.cuttingSpeed(rpm, d)

        // --- Forces -------------------------------------------------------------------------------
        val kappa = (type.pointAngle / 2.0).coerceIn(10.0, 90.0).toRad()
        val h = fn / edges * sin(kappa)
        val width = (d - d0) / 2.0 / sin(kappa)
        val rake = if (isReamer) 5.0 else DRILL_RAKE
        val kc = Kienzle.kc(m, h, rake, input.condition.forceFactor)
        val fcEdge = Kienzle.force(m, width, h, rake, input.condition.forceFactor)
        val torque = edges * fcEdge * (d + d0) / 4.0 / 1000.0
        val lipThrust = edges * 0.5 * fcEdge * sin(kappa)
        val thrust = lipThrust * (1.0 + if (d0 <= 0.0) type.chiselFactor else 0.0)
        val power = CuttingBasics.powerKw(torque, rpm)
        val available = machine.availablePowerKw(rpm)
        val mrr = PI / 4.0 * (d * d - d0 * d0) * feed / 1000.0
        val pointLength = if (type.pointAngle >= 179.0) 0.0 else (d - d0) / 2.0 / kotlin.math.tan(kappa)
        val time = (input.depth + pointLength) / max(feed, 1e-6)

        // --- Peck strategy -------------------------------------------------------------------------
        val peck = when {
            isReamer -> L.t(
                "No pecking. Ream in one pass and retract at feed (or rapid out if the finish allows).",
                "Gagalama yok. Tek pasoda raybalayın, ilerlemeyle geri çekin (yüzey izin veriyorsa hızlı çıkış).",
            )
            type == DrillType.SPOT -> L.t(
                "Spot to a chamfer ≈0.1–0.2 mm larger than the drill so the drill centres on the spot cone.",
                "Matkap punta konisine otursun diye pahı matkaptan ≈0,1–0,2 mm büyük açın.",
            )
            coolant && ld <= 8 -> L.t(
                "No pecking needed with through-coolant up to 8×D.",
                "İçten soğutmalı matkapla 8×D'ye kadar gagalama gerekmez.",
            )
            ld <= 3 -> L.t("No pecking needed (≤3×D). Use G81.", "Gagalama gerekmez (≤3×D). G81 kullanın.")
            ld <= 5 -> L.t(
                "Chip-break pecking (G73) every ≈1×D (${f.len(d)}).",
                "Talaş kırma gagalaması (G73), her ≈1×D'de (${f.len(d)}).",
            )
            ld <= 10 -> L.t(
                "Full-retract pecking (G83): first peck ≈2×D, then ≈1×D, decreasing with depth. " +
                    "Consider a parabolic-flute drill or through-coolant.",
                "Tam geri çekmeli gagalama (G83): ilk paso ≈2×D, sonra ≈1×D, derinlikle azaltarak. " +
                    "Parabolik kanallı ya da içten soğutmalı matkap düşünün.",
            )
            else -> L.t(
                "Deep hole (${f.num(ld)}×D): pilot hole 2×D with a stiff drill, then a long drill with through-coolant " +
                    "or gun drilling; full-retract pecking otherwise.",
                "Derin delik (${f.num(ld)}×D): rijit matkapla 2×D pilot delik, sonra içten soğutmalı uzun matkap " +
                    "ya da namlu delme; değilse tam geri çekmeli gagalama.",
            )
        }

        // --- Advice ----------------------------------------------------------------------------------
        if (power > available) {
            advice += Advice.danger(
                L.t(
                    "Needs ${f.power(power)} but only ${f.power(available)} is available — reduce feed or diameter.",
                    "${f.power(power)} güç gerekiyor ama yalnızca ${f.power(available)} mevcut — ilerlemeyi veya çapı düşürün.",
                ),
            )
        } else if (power > 0.85 * available) {
            advice += Advice.caution(powerShareText(L, power, available))
        }
        if (thrust > 5000.0) {
            advice += Advice.caution(
                L.t(
                    "High thrust (${f.force(thrust)}): check workholding and spindle thrust rating.",
                    "Yüksek eksenel kuvvet (${f.force(thrust)}): bağlamayı ve iş milinin eksenel yük sınırını kontrol edin.",
                ),
            )
        }
        if (type == DrillType.HSS_TWIST && m.hardnessHb > 300) {
            advice += Advice.caution(
                L.t(
                    "Use 135° split-point cobalt or carbide drills above ≈300 HB.",
                    "≈300 HB üzerinde 135° split uçlu kobaltlı ya da karbür matkap kullanın.",
                ),
            )
        }
        if (type == DrillType.INDEXABLE && ld > 4) {
            advice += Advice.caution(
                L.t("Indexable drills are normally limited to 2–5×D.", "Kesici uçlu (U) matkaplar genelde 2–5×D derinlikle sınırlıdır."),
            )
        }
        if (type == DrillType.INDEXABLE && d < 12) {
            advice += Advice.caution(L.t("Indexable drills start at ≈12 mm diameter.", "Kesici uçlu matkaplar ≈12 mm çaptan başlar."))
        }
        if (!isReamer && type != DrillType.SPOT && type != DrillType.INDEXABLE && type.substrate == ToolMaterial.CARBIDE) {
            advice += Advice.info(
                L.t(
                    "Solid carbide drills self-centre: skip the spot drill or spot with ≥140° (142° spot drill).",
                    "Karbür matkap kendini merkezler: punta şart değil; atacaksanız ≥140° (142° punta matkabı) kullanın.",
                ),
            )
        }
        if (isReamer) {
            advice += Advice.info(
                L.t(
                    "Pre-drill to ${f.len(d0)} (stock ${f.len(d - d0)} on diameter) for a ${f.len(d)} reamer.",
                    "${f.len(d)} rayba için ${f.len(d0)} ile ön delik açın (çapta ${f.len(d - d0)} rayba payı).",
                ),
            )
        }
        if (m.id == "n_brass") {
            advice += Advice.info(
                L.t(
                    "Brass: dub the cutting lips (zero rake) to stop the drill pulling in on breakthrough.",
                    "Pirinç: delik çıkışında matkap çekmesin diye kesici ağızları köreltin (sıfır talaş açısı).",
                ),
            )
        }
        val indexable = type == DrillType.INDEXABLE
        val life = ToolLife.estimate(
            ToolLife.referenceMinutes(substrate, insert = indexable, material = m), if (indexable) 0.3 else substrate.taylorN,
            vRef = vcRec, v = vc, hRef = fnClamped, h = fn,
        )
        if (life.outsideModel) advice += Advice.info(toolLifeOutsideText(L))
        val holes = if (input.depth > 0) life.minutes / time else null
        if (m.notes.isNotBlank()) advice += Advice.info(L.tx(m.notes))

        return DrillingResult(
            input = input, vcRecommended = vcRec, vc = vc, rpm = rpm, fn = fn, feed = feed,
            chipThickness = h, kc = kc, torqueNm = torque, thrust = thrust, powerKw = power,
            availablePowerKw = available, mrr = mrr, timeMin = time, pointLength = pointLength,
            peckAdvice = peck, reamAllowance = if (isReamer) d - d0 else null,
            toolLife = life, holesPerTool = holes, toolLifeLength = holes?.let { it * input.depth / 1000.0 }, advice = advice,
        )
    }

    /** Depth to plunge a spot/countersink of included [angle] to reach chamfer diameter [chamferDia]. */
    fun spotDepth(chamferDia: Double, angle: Double, tipDia: Double = 0.0): Double {
        val half = (angle / 2.0).toRad()
        return max(0.0, (chamferDia - tipDia) / 2.0 / kotlin.math.tan(half))
    }

    /** Chamfer diameter produced at plunge [depth] with included [angle]. */
    fun spotDiameter(depth: Double, angle: Double, tipDia: Double = 0.0): Double =
        tipDia + 2.0 * depth * kotlin.math.tan((angle / 2.0).toRad())
}
