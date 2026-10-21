package com.shopref.core

import com.shopref.core.calc.Deflection
import com.shopref.core.calc.DrillType
import com.shopref.core.calc.DrillingCalculator
import com.shopref.core.calc.DrillingInput
import com.shopref.core.calc.ForceRatios
import com.shopref.core.calc.Kienzle
import com.shopref.core.calc.MillStrategy
import com.shopref.core.calc.MillToolType
import com.shopref.core.calc.MillingCalculator
import com.shopref.core.calc.MillingForceModel
import com.shopref.core.calc.MillingGeometry
import com.shopref.core.calc.MillingInput
import com.shopref.core.calc.TapType
import com.shopref.core.calc.TappingCalculator
import com.shopref.core.calc.TappingInput
import com.shopref.core.calc.TurningCalculator
import com.shopref.core.calc.TurningInput
import com.shopref.core.material.MaterialLibrary
import com.shopref.core.reference.Threads
import com.shopref.core.tooling.ChipLoadLevel
import com.shopref.core.tooling.Coating
import com.shopref.core.tooling.Machine
import com.shopref.core.tooling.ToolCondition
import com.shopref.core.tooling.ToolMaterial
import com.shopref.core.units.Quantity
import com.shopref.core.units.UnitSystem
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.pow
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalcTest {
    private val steel = MaterialLibrary.byId("p_low_carbon")!!
    private val aluminium = MaterialLibrary.byId("n_al_wrought")!!

    private fun assertClose(expected: Double, actual: Double, relTol: Double, msg: String = "") {
        val err = abs(actual - expected) / abs(expected)
        assertTrue(err <= relTol, "$msg expected $expected but was $actual (rel err ${"%.4f".format(err)})")
    }

    @Test
    fun unitConversionsRoundTrip() {
        assertClose(25.4, Quantity.LENGTH.fromDisplay(1.0, UnitSystem.IMPERIAL), 1e-12)
        assertClose(328.084, Quantity.CUTTING_SPEED.toDisplay(100.0, UnitSystem.IMPERIAL), 1e-5)
        assertClose(1.341, Quantity.POWER.toDisplay(1.0, UnitSystem.IMPERIAL), 1e-3)
        assertEquals("1.50", Quantity.LENGTH.format(1.5, UnitSystem.METRIC, 2))
    }

    @Test
    fun kienzleForceMatchesDefinition() {
        // Fc = kc1.1 · b · h^(1−mc): b = 2 mm, h = 0.2 mm.
        val expected = steel.kc11 * 2.0 * 0.2.pow(1 - steel.mc)
        assertClose(expected, Kienzle.force(steel, 2.0, 0.2), 1e-12)
        // At h = 1 mm and 0° rake, kc equals kc1.1.
        assertClose(steel.kc11, Kienzle.kc(steel, 1.0), 1e-12)
    }

    @Test
    fun obliqueRatiosReduceToMerchantForZeroHelix() {
        val r = ForceRatios.oblique(frictionAngleDeg = 30.0, normalRakeDeg = 10.0, inclinationDeg = 0.0)
        assertClose(tan(20.0 * PI / 180), r.radial, 1e-9)
        assertEquals(0.0, r.axial, 1e-12)
        // Helix produces a positive axial component (pull-up for a right-hand helix).
        val h = ForceRatios.oblique(30.0, 10.0, 30.0)
        assertTrue(h.axial > 0.1 && h.axial < 0.5, "axial ratio ${h.axial}")
    }

    @Test
    fun simulatedMeanForceMatchesClosedFormForLinearModel() {
        // With mc = 0 the Kienzle law is linear (Kt = kc1.1), so the time-domain mean tangential
        // force must equal Altintas' closed form N·ap·Kt·fz·(cos φst − cos φex)/(2π).
        val linear = steel.copy(mc = 0.0)
        val d = 10.0; val ae = 3.0; val ap = 5.0; val fz = 0.1; val n = 4
        val res = MillingForceModel.simulate(
            MillingForceModel.Input(linear, d, n, helixDeg = 30.0, rakeDeg = 0.0, ap = ap, ae = ae, fz = fz,
                climb = false, angularSteps = 3600, axialSlices = 100),
        )
        val phiEx = acos(1 - 2 * ae / d)
        val closed = MillingForceModel.closedFormMeanTangential(linear.kc11, n, ap, fz, 0.0, phiEx)
        assertClose(closed, res.meanTangential, 0.005)
    }

    @Test
    fun chipThinningAndEffectiveDiameter() {
        // ae = 10% of D: factor = 1/(2·√(0.1·0.9)) = 1.6667
        assertClose(1.6667, MillingGeometry.radialChipThinningFactor(1.0, 10.0), 1e-3)
        assertEquals(1.0, MillingGeometry.radialChipThinningFactor(6.0, 10.0))
        // Ball D10 at ap 1: Deff = 2·√(1·9) = 6
        assertClose(6.0, MillingGeometry.ballEffectiveDiameter(10.0, 1.0), 1e-12)
        // Scallop for R5, stepover 1: 5 − √(25 − 0.25)
        assertClose(0.025063, MillingGeometry.scallopHeight(5.0, 1.0), 1e-4)
    }

    @Test
    fun deflectionOfUniformCantileverMatchesBeamFormula() {
        // Uniform rod (shank = 0.8·D, all "fluted") with tip load: δ = F·L³/(3·E·I).
        val d = 10.0; val l = 40.0; val e = 600_000.0; val f = 500.0
        val res = Deflection.compute(Deflection.Input(d, d * 0.8, l, l, e, f, loadFromTip = 0.0), steps = 4000)
        val de = 0.8 * d
        val i = PI * de.pow(4) / 64
        assertClose(f * l.pow(3) / (3 * e * i), res.tipDeflection, 1e-3)
    }

    @Test
    fun drillingTorqueMatchesClassicFormula() {
        val r = DrillingCalculator.calculate(
            DrillingInput(steel, DrillType.CARBIDE, Coating.UNCOATED, diameter = 10.0, depth = 20.0, condition = ToolCondition.SHARP),
        )
        // Mc = kc·fn·D²/8000 with kc at h = fn/2·sin κr.
        val expected = r.kc * r.fn * 100.0 / 8000.0
        assertClose(expected, r.torqueNm, 1e-6)
        assertTrue(r.torqueNm in 2.0..15.0, "10 mm drill torque ${r.torqueNm} N·m")
        assertTrue(r.thrust in 500.0..4000.0, "thrust ${r.thrust} N")
    }

    @Test
    fun millingRecommendationIsPlausible() {
        val r = MillingCalculator.calculate(
            MillingInput(steel, ToolMaterial.CARBIDE, Coating.ALTIN, MillToolType.SQUARE_END, diameter = 12.0, flutes = 4,
                strategy = MillStrategy.SIDE_MILLING, ap = 12.0, ae = 4.8, chipLoadLevel = ChipLoadLevel.CONSERVATIVE),
        )
        assertTrue(r.rpm in 4000.0..6000.0, "rpm ${r.rpm}")
        assertTrue(r.fz in 0.05..0.08, "fz ${r.fz}")
        assertTrue(r.powerKw in 2.0..5.0, "power ${r.powerKw}")
        // Power from the force model agrees with the MRR × kc estimate within 15%.
        val pMrr = r.mrr * r.forces.meanKc / 60_000.0
        assertClose(pMrr, r.powerKw, 0.15)
    }

    @Test
    fun chipLoadLevelsBracketPublishedHemData() {
        // ½" 4-flute AlTiN carbide, 4140 pre-hardened, 1" DOC × 0.04" WOC (8%), HEM.
        val base = MillingInput(
            MaterialLibrary.byId("p_alloy_prehard")!!, ToolMaterial.CARBIDE, Coating.ALTIN, MillToolType.SQUARE_END,
            diameter = 12.7, flutes = 4, strategy = MillStrategy.HEM, ap = 25.4, ae = 1.016, fluteLength = 31.75,
        )
        val ipm = { level: ChipLoadLevel -> MillingCalculator.calculate(base.copy(chipLoadLevel = level)).feed / 25.4 }
        val conservative = MillingCalculator.calculate(base.copy(chipLoadLevel = ChipLoadLevel.CONSERVATIVE))
        // Conservative keeps the cautious 0.0020" catalogue chip (RobbJack) before thinning.
        assertClose(0.0020, conservative.hmaxTarget / 25.4, 0.01)
        val aggressive = MillingCalculator.calculate(base.copy(chipLoadLevel = ChipLoadLevel.AGGRESSIVE))
        // Aggressive matches high-end catalogue/HEM practice: ≈0.0035" base chip (Maritool), ≈0.0065" programmed,
        // ≈625 SFM, ≈125 IPM (FSWizard gives 4792 rpm, 627 SFM, 0.0065 in/tooth, 124.7 IPM for this cut).
        assertClose(0.0035, aggressive.hmaxTarget / 25.4, 0.02)
        assertClose(627.0, aggressive.vc * 3.28084, 0.05)
        assertClose(124.7, ipm(ChipLoadLevel.AGGRESSIVE), 0.08)
        assertTrue(ipm(ChipLoadLevel.CONSERVATIVE) < ipm(ChipLoadLevel.STANDARD))
        assertTrue(ipm(ChipLoadLevel.STANDARD) < ipm(ChipLoadLevel.AGGRESSIVE))
    }

    @Test
    fun hemAppliesChipThinning() {
        val base = MillingInput(aluminium, diameter = 12.0, flutes = 3, strategy = MillStrategy.HEM, ae = 1.2, ap = 24.0)
        val r = MillingCalculator.calculate(base)
        assertClose(1.6667, r.chipThinningFactor, 1e-3)
        assertClose(r.hmaxTarget, r.hmax, 1e-9)
    }

    @Test
    fun machineLimitsClampRpmAndFeed() {
        val slow = Machine(maxRpm = 3000.0, maxFeed = 500.0)
        val r = MillingCalculator.calculate(MillingInput(aluminium, diameter = 6.0, flutes = 3, machine = slow))
        assertTrue(r.rpm <= 3000.0 + 1e-9)
        assertTrue(r.feed <= 500.0 + 1e-9)
    }

    @Test
    fun tappingTorqueAndStrengthArePlausible() {
        val m10 = Threads.metricCoarse.first { it.designation == "M10×1.5" }
        val r = TappingCalculator.calculate(TappingInput(steel, m10, TapType.SPIRAL_POINT, blindHole = false))
        assertTrue(r.torqueNm in 2.0..20.0, "M10 tapping torque ${r.torqueNm}")
        assertTrue(r.breakingTorqueNm in 25.0..60.0, "M10 breaking torque ${r.breakingTorqueNm}")
        assertClose(8.5, r.recommendedDrill.mm, 0.01)
        // Full-depth energy balance ≈ kc·P²·D/8450 (vs. the /8000 rule of thumb).
        val p = 1.5; val d = 10.0; val t = 0.541266 * p
        val area = 0.125 * p * t + tan(PI / 6) * t * t
        assertClose(p * p / 4.2228, area, 1e-3)
    }

    @Test
    fun formTapDrillIsLarger() {
        val m6 = Threads.metricCoarse.first { it.designation == "M6×1" }
        val form = TappingCalculator.calculate(TappingInput(steel, m6, TapType.FORM))
        assertClose(5.558, form.idealDrill, 1e-3)
        assertTrue(form.recommendedDrill.mm in 5.5..5.6)
    }

    @Test
    fun turningForcesAndFinish() {
        val r = TurningCalculator.calculate(TurningInput(steel, ap = 2.0, feed = 0.2, noseRadius = 0.8, enteringAngle = 90.0, rakeDeg = 0.0))
        val expectedFc = steel.kc11 * ToolCondition.NORMAL.forceFactor * 2.0 * 0.2.pow(1 - steel.mc)
        assertClose(expectedFc, r.fc, 1e-9)
        assertClose(0.2 * 0.2 / (32 * 0.8) * 1000, r.raMicron, 1e-9)
        assertTrue(r.ff > 0 && r.fp > 0)
    }
}
