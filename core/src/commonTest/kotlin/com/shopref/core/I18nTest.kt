package com.shopref.core

import com.shopref.core.calc.DrillType
import com.shopref.core.calc.DrillingCalculator
import com.shopref.core.calc.DrillingInput
import com.shopref.core.calc.InsertGrade
import com.shopref.core.calc.MillStrategy
import com.shopref.core.calc.MillToolType
import com.shopref.core.calc.MillingCalculator
import com.shopref.core.calc.MillingInput
import com.shopref.core.calc.TapChamfer
import com.shopref.core.calc.TapType
import com.shopref.core.calc.TurnOperation
import com.shopref.core.i18n.Dictionary
import com.shopref.core.i18n.Lang
import com.shopref.core.material.Family
import com.shopref.core.material.IsoGroup
import com.shopref.core.material.MaterialLibrary
import com.shopref.core.reference.Citations
import com.shopref.core.reference.DrillSeries
import com.shopref.core.reference.Fits
import com.shopref.core.reference.SurfaceFinish
import com.shopref.core.reference.ThreadStandard
import com.shopref.core.tooling.ChipLoadLevel
import com.shopref.core.tooling.Coating
import com.shopref.core.tooling.Machine
import com.shopref.core.tooling.Rigidity
import com.shopref.core.tooling.ToolCondition
import com.shopref.core.tooling.ToolMaterial
import com.shopref.core.units.Quantity
import com.shopref.core.units.UnitSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class I18nTest {
    /** Strings that are identical in Turkish (symbols, trade names, grades). */
    private val sameInTurkish = setOf(
        "HSS", "HSS-Co (M42/HSS-E)", "TiN", "TiCN", "TiAlN", "AlTiN", "AlCrN", "AlTiSiN (nACo)", "ZrN", "TiB2", "DLC",
        "CBN", "PCD", "mm", "in", "µm", "m", "ft", "cm³", "in³", "SFM", "IPM", "IPR", "kW", "hp", "N", "lbf", "N·m", "in·lbf", "MPa", "ksi", "µin", "°", "%",
    )

    private fun engineStrings(): List<String> = buildList {
        IsoGroup.entries.forEach { add(it.description) }
        Family.entries.forEach { add(it.label) }
        ToolMaterial.entries.forEach { add(it.label) }
        Coating.entries.forEach { add(it.label); add(it.description) }
        ToolCondition.entries.forEach { add(it.label) }
        ChipLoadLevel.entries.forEach { add(it.label); add(it.description) }
        Rigidity.entries.forEach { add(it.label) }
        Machine.PRESETS.forEach { add(it.name) }
        MillToolType.entries.forEach { add(it.label); add(it.description); it.insertSizeLabel?.let(::add) }
        MillStrategy.entries.forEach { add(it.label); add(it.description) }
        DrillType.entries.forEach { add(it.label) }
        TapType.entries.forEach { add(it.label) }
        TapChamfer.entries.forEach { add(it.label) }
        InsertGrade.entries.forEach { add(it.label) }
        TurnOperation.entries.forEach { add(it.label) }
        ThreadStandard.entries.forEach { add(it.label) }
        DrillSeries.entries.forEach { add(it.label) }
        Fits.commonFits.forEach { add(it.name); add(it.use) }
        SurfaceFinish.processes.forEach { add(it.process) }
        MaterialLibrary.all.forEach { add(it.name); if (it.notes.isNotBlank()) add(it.notes) }
        Quantity.entries.forEach { add(it.metricLabel); add(it.imperialLabel) }
        Citations.formulas.forEach { add(it.title); add(it.notes) }
        Citations.all.forEach { add(it.usedFor) }
    }.filter { it.isNotBlank() && it !in sameInTurkish }.distinct()

    @Test
    fun everyEngineLabelHasATurkishTranslation() {
        val missing = engineStrings().filter { it !in Dictionary.tr }
        assertTrue(missing.isEmpty(), "Missing Turkish translations: $missing")
    }

    @Test
    fun placeholdersAndFallback() {
        assertEquals("Devir", Lang.TR.tx("Spindle speed"))
        assertEquals("Spindle speed", Lang.EN.tx("Spindle speed"))
        assertEquals("Not in dictionary", Lang.TR.tx("Not in dictionary"))
        assertEquals("%40 ezme", Lang.TR.tx("{0}% form", 40))
        assertEquals("dev/dk", Quantity.SPINDLE_SPEED.label(UnitSystem.METRIC, Lang.TR))
        assertEquals(Lang.TR, Lang.fromCode("tr-TR"))
        assertEquals(Lang.EN, Lang.fromCode("de"))
    }

    @Test
    fun calculatorAdviceIsTranslated() {
        val steel = MaterialLibrary.byId("p_low_carbon")!!
        val base = MillingInput(steel, diameter = 12.0, flutes = 4, strategy = MillStrategy.SLOTTING, stickout = 80.0)
        val en = MillingCalculator.calculate(base).advice.map { it.text }
        val tr = MillingCalculator.calculate(base.copy(lang = Lang.TR)).advice.map { it.text }
        assertEquals(en.size, tr.size)
        assertTrue(en.isNotEmpty())
        en.zip(tr).forEach { (e, t) -> assertNotEquals(e, t) }
        assertTrue(tr.any { "Sarkma boyu" in it }, tr.toString())
        val drill = DrillingCalculator.calculate(DrillingInput(steel, DrillType.HSS_TWIST, depth = 80.0, lang = Lang.TR))
        assertTrue("gagalama" in drill.peckAdvice.lowercase(), drill.peckAdvice)
    }
}
