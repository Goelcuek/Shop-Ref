package com.shopref.app.ui.reference

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.shopref.app.state.AppSettings
import com.shopref.app.ui.components.Dropdown
import com.shopref.app.ui.components.FieldRow
import com.shopref.app.ui.components.IntField
import com.shopref.app.ui.components.IsoBadge
import com.shopref.app.ui.components.NumberField
import com.shopref.app.ui.components.QuantityRow
import com.shopref.app.ui.components.ResultRow
import com.shopref.app.ui.components.SectionCard
import com.shopref.app.ui.components.SmallNote
import com.shopref.core.calc.CuttingBasics
import com.shopref.core.material.MaterialLibrary
import com.shopref.core.reference.Citations
import com.shopref.core.reference.FitType
import com.shopref.core.reference.Fits
import com.shopref.core.reference.Hardness
import com.shopref.core.reference.SurfaceFinish
import com.shopref.core.reference.Tolerance
import com.shopref.core.units.Quantity
import com.shopref.core.units.UnitSystem
import com.shopref.core.units.formatNumber
import kotlin.math.abs

// ------------------------------------------------------------------------------------- Fits

private fun dev(um: Double): String = (if (um > 0) "+" else "") + formatNumber(um, if (um % 1.0 == 0.0) 0 else 1)

private fun limits(nominal: Double, t: Tolerance, units: UnitSystem): String {
    val hi = nominal + t.upper / 1000.0
    val lo = nominal + t.lower / 1000.0
    val d = if (units == UnitSystem.METRIC) 3 else 5
    return Quantity.LENGTH.format(hi, units, d) + " / " + Quantity.LENGTH.format(lo, units, d)
}

private fun clearanceText(um: Double, units: UnitSystem): String =
    if (units == UnitSystem.METRIC) dev(um) + " µm" else (if (um > 0) "+" else "") + formatNumber(um / 25_400.0, 5) + " in"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitsPage(settings: AppSettings, modifier: Modifier = Modifier) {
    val units = settings.units
    var size by remember { mutableDoubleStateOf(25.0) }
    var holeLetter by remember { mutableStateOf("H") }
    var holeGrade by remember { mutableIntStateOf(7) }
    var shaftLetter by remember { mutableStateOf("g") }
    var shaftGrade by remember { mutableIntStateOf(6) }
    val gradeOptions = (5..11).toList()
    val fit = Fits.fit(holeLetter, holeGrade, shaftLetter, shaftGrade, size)
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard("Fit") {
            NumberField("Nominal size", size, { v -> v?.let { size = it } }, Quantity.LENGTH, units, Modifier.fillMaxWidth(), min = 0.01)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Fits.commonFits.forEach { cf ->
                    AssistChip(
                        onClick = {
                            holeLetter = cf.hole; holeGrade = cf.holeGrade; shaftLetter = cf.shaft; shaftGrade = cf.shaftGrade
                        },
                        label = { Text(cf.label) },
                    )
                }
            }
            FieldRow {
                Dropdown("Hole", Fits.holeLetters, holeLetter, { holeLetter = it }, Modifier.weight(1f))
                Dropdown("IT", gradeOptions, holeGrade, { holeGrade = it }, Modifier.weight(1f))
                Dropdown("Shaft", Fits.shaftLetters, shaftLetter, { shaftLetter = it }, Modifier.weight(1f))
                Dropdown("IT", gradeOptions, shaftGrade, { shaftGrade = it }, Modifier.weight(1f))
            }
            Fits.commonFits.firstOrNull { it.hole == holeLetter && it.holeGrade == holeGrade && it.shaft == shaftLetter && it.shaftGrade == shaftGrade }?.let {
                SmallNote("${it.name}: ${it.use}")
            }
        }
        if (fit == null) {
            SmallNote("This combination is not available (sizes up to 500 mm; K/M/N holes up to IT8, P/R/S up to IT7).")
        } else {
            SectionCard("$holeLetter$holeGrade / $shaftLetter$shaftGrade at ${Quantity.LENGTH.formatWithUnit(size, units)}") {
                ResultRow("Hole deviations", dev(fit.hole.upper) + " / " + dev(fit.hole.lower) + " µm")
                ResultRow("Hole limits", limits(size, fit.hole, units) + " " + Quantity.LENGTH.label(units), emphasize = true)
                ResultRow("Shaft deviations", dev(fit.shaft.upper) + " / " + dev(fit.shaft.lower) + " µm")
                ResultRow("Shaft limits", limits(size, fit.shaft, units) + " " + Quantity.LENGTH.label(units), emphasize = true)
                HorizontalDivider()
                ResultRow("Max clearance", clearanceText(fit.maxClearance, units))
                ResultRow("Min clearance", clearanceText(fit.minClearance, units))
                ResultRow(
                    "Fit type",
                    when (fit.type) {
                        FitType.CLEARANCE -> "Clearance"
                        FitType.TRANSITION -> "Transition"
                        FitType.INTERFERENCE -> "Interference"
                    },
                    emphasize = true,
                )
                SmallNote("Negative clearance = interference.")
            }
        }
        SectionCard("Standard tolerance grades at this size (µm)") {
            val grades = Fits.grades.toList()
            val w = List(grades.size) { 1f }
            TableRow(grades.map { "IT$it" }, w, header = true)
            TableRow(grades.map { g -> Fits.it(g, size)?.toString() ?: "—" }, w)
            SmallNote("ISO 286-1. Typical: IT6–IT7 ground/reamed, IT8–IT9 fine turned/milled, IT11 general machining.")
        }
    }
}

// ------------------------------------------------------------------------------------- Hardness

@Composable
fun HardnessPage(modifier: Modifier = Modifier) {
    var hrc by remember { mutableDoubleStateOf(45.0) }
    LazyColumn(modifier) {
        item {
            Column(Modifier.padding(12.dp)) {
                SectionCard("Convert from HRC") {
                    NumberField("Rockwell C", hrc, { v -> v?.let { hrc = it } }, Quantity.NONE, UnitSystem.METRIC, Modifier.fillMaxWidth(), decimals = 1)
                    val hv = Hardness.hvFromHrc(hrc)
                    val row = Hardness.rows.minByOrNull { abs(it.hrc - hrc) }
                    ResultRow("Vickers HV", hv?.let { formatNumber(it, 0) } ?: "out of range", emphasize = true)
                    ResultRow("Brinell HB (≈)", row?.hb?.toString() ?: "—")
                    ResultRow("Tensile strength (≈)", row?.tensileMpa?.let { "$it MPa / ${formatNumber(it * 0.1450377, 0)} ksi" } ?: "—")
                }
            }
        }
        val w = listOf(1f, 1f, 1f, 1.4f)
        item { TableRow(listOf("HRC", "HV", "HB", "Rm MPa (≈)"), w, header = true) }
        items(Hardness.rows) { r ->
            TableRow(listOf(r.hrc.toString(), r.hv.toString(), r.hb?.toString() ?: "—", r.tensileMpa?.toString() ?: "—"), w, highlight = r.hrc == Math.round(hrc).toInt())
        }
        item { SmallNote("Approximate conversions for non-austenitic steels (ASTM E140). Tensile ≈ 3.45·HB.", Modifier.padding(12.dp)) }
    }
}

// ------------------------------------------------------------------------------------- Surface finish

@Composable
fun SurfaceFinishPage(settings: AppSettings, modifier: Modifier = Modifier) {
    var ra by remember { mutableDoubleStateOf(1.6) }
    LazyColumn(modifier) {
        item {
            Column(Modifier.padding(12.dp)) {
                SectionCard("Convert") {
                    FieldRow {
                        NumberField("Ra", ra, { v -> v?.let { ra = it } }, Quantity.ROUGHNESS, UnitSystem.METRIC, Modifier.weight(1f), min = 0.0)
                        NumberField("Ra", ra, { v -> v?.let { ra = it } }, Quantity.ROUGHNESS, UnitSystem.IMPERIAL, Modifier.weight(1f), min = 0.0)
                    }
                    QuantityRow("Rq (RMS) ≈ 1.11·Ra", SurfaceFinish.rmsFromRa(ra), Quantity.ROUGHNESS, settings.units)
                    QuantityRow("Rz ≈ 4–7·Ra (typical 5.5)", SurfaceFinish.rzEstimateFromRa(ra), Quantity.ROUGHNESS, settings.units)
                    val grade = SurfaceFinish.grades.minByOrNull { abs(kotlin.math.ln(it.raMicron / ra.coerceAtLeast(1e-6))) }
                    ResultRow("Nearest ISO grade", grade?.grade ?: "—", emphasize = true)
                }
            }
        }
        val w = listOf(0.8f, 1f, 1f)
        item { TableRow(listOf("Grade", "Ra µm", "Ra µin"), w, header = true) }
        items(SurfaceFinish.grades) { g -> TableRow(listOf(g.grade, formatNumber(g.raMicron, 3).trimEnd('0').trimEnd('.'), g.raMicroInch.toString()), w) }
        val w2 = listOf(1.6f, 1f, 1f)
        item {
            Spacer(Modifier.padding(6.dp))
            TableRow(listOf("Process", "Ra min µm", "Ra max µm"), w2, header = true)
        }
        items(SurfaceFinish.processes) { p -> TableRow(listOf(p.process, formatNumber(p.raMin, 3).trimEnd('0').trimEnd('.'), formatNumber(p.raMax, 1)), w2) }
    }
}

// ------------------------------------------------------------------------------------- Quick calculators

@Composable
fun QuickCalculatorsPage(settings: AppSettings, modifier: Modifier = Modifier) {
    val units = settings.units
    var vc by remember { mutableDoubleStateOf(100.0) }
    var dia by remember { mutableDoubleStateOf(10.0) }
    var rpm by remember { mutableDoubleStateOf(3000.0) }
    var fz by remember { mutableDoubleStateOf(0.05) }
    var flutes by remember { mutableIntStateOf(4) }
    var feed by remember { mutableDoubleStateOf(600.0) }
    var conv by remember { mutableStateOf(Quantity.LENGTH) }
    var base by remember { mutableDoubleStateOf(25.4) }
    val convertible = listOf(
        Quantity.LENGTH, Quantity.CUTTING_SPEED, Quantity.FEED_RATE, Quantity.FEED_PER_REV, Quantity.POWER,
        Quantity.FORCE, Quantity.TORQUE, Quantity.STRESS, Quantity.MRR, Quantity.ROUGHNESS,
    )
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard("Spindle speed from cutting speed") {
            FieldRow {
                NumberField("Cutting speed", vc, { v -> v?.let { vc = it } }, Quantity.CUTTING_SPEED, units, Modifier.weight(1f), min = 0.0)
                NumberField("Diameter", dia, { v -> v?.let { dia = it } }, Quantity.LENGTH, units, Modifier.weight(1f), min = 0.01)
            }
            QuantityRow("Spindle speed", CuttingBasics.rpm(vc, dia), Quantity.SPINDLE_SPEED, units, emphasize = true)
        }
        SectionCard("Cutting speed from spindle speed") {
            FieldRow {
                NumberField("Spindle", rpm, { v -> v?.let { rpm = it } }, Quantity.SPINDLE_SPEED, units, Modifier.weight(1f), decimals = 0, min = 0.0)
                NumberField("Diameter", dia, { v -> v?.let { dia = it } }, Quantity.LENGTH, units, Modifier.weight(1f), min = 0.01)
            }
            QuantityRow("Cutting speed", CuttingBasics.cuttingSpeed(rpm, dia), Quantity.CUTTING_SPEED, units, emphasize = true)
        }
        SectionCard("Feed ↔ chip load") {
            FieldRow {
                NumberField("Chip load fz", fz, { v -> v?.let { fz = it } }, Quantity.CHIP_LOAD, units, Modifier.weight(1f), min = 0.0)
                IntField("Flutes", flutes, { flutes = it }, Modifier.weight(1f))
            }
            QuantityRow("Feed at ${formatNumber(rpm, 0)} rpm", fz * flutes * rpm, Quantity.FEED_RATE, units, emphasize = true)
            NumberField("Feed rate", feed, { v -> v?.let { feed = it } }, Quantity.FEED_RATE, units, Modifier.fillMaxWidth(), min = 0.0)
            QuantityRow("Chip load at ${formatNumber(rpm, 0)} rpm", if (rpm > 0) feed / (flutes * rpm) else 0.0, Quantity.CHIP_LOAD, units, emphasize = true)
        }
        SectionCard("Unit converter") {
            Dropdown("Quantity", convertible, conv, { conv = it }, Modifier.fillMaxWidth(), optionLabel = { "${it.metricLabel} ↔ ${it.imperialLabel}" })
            FieldRow {
                NumberField("Metric", base, { v -> v?.let { base = it } }, conv, UnitSystem.METRIC, Modifier.weight(1f))
                NumberField("Imperial", base, { v -> v?.let { base = it } }, conv, UnitSystem.IMPERIAL, Modifier.weight(1f))
            }
            SmallNote("Edit either side. Inch lengths accept fractions such as 1-1/4.")
        }
    }
}

// ------------------------------------------------------------------------------------- Material data

@Composable
fun MaterialDataPage(settings: AppSettings, modifier: Modifier = Modifier) {
    val units = settings.units
    LazyColumn(modifier.padding(horizontal = 12.dp)) {
        items(MaterialLibrary.all, key = { it.id }) { m ->
            Column(Modifier.padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IsoBadge(m.group)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(m.name, style = MaterialTheme.typography.titleSmall)
                        SmallNote("${m.examples} · ${m.hardness}")
                    }
                }
                val vcU = { v: Double -> Quantity.CUTTING_SPEED.formatWithUnit(v, units) }
                Text(
                    "kc1.1 = ${formatNumber(m.kc11, 0)} N/mm²   mc = ${formatNumber(m.mc, 2)}   friction ≈ ${formatNumber(m.frictionAngle, 0)}°",
                    style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                )
                Text(
                    "End mill: carbide ${vcU(m.vcCarbide)}, HSS ${if (m.vcHss > 0) vcU(m.vcHss) else "n/a"}, hmax ≈ ${formatNumber(m.fzPerD, 4)}·D",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Drill fn ≈ ${formatNumber(m.drillFnPerD, 3)}·D · Tap ${if (m.tapVc > 0) vcU(m.tapVc) else "thread mill"} · Turning ${vcU(m.turnVc)}" +
                        (if (m.machinability > 0) " · Machinability ${m.machinability}%" else ""),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (m.notes.isNotBlank()) SmallNote(m.notes)
            }
            HorizontalDivider()
        }
        item { SmallNote("Speeds: uncoated tools, base values before coating/engagement corrections. kc1.1 referenced to 0° rake.", Modifier.padding(vertical = 12.dp)) }
    }
}

// ------------------------------------------------------------------------------------- Formulas

@Composable
fun FormulasPage(modifier: Modifier = Modifier) {
    LazyColumn(modifier.padding(horizontal = 12.dp)) {
        item { Text("Formulas", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp)) }
        items(Citations.formulas) { f ->
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(f.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    f.formula,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp),
                )
                Text(f.notes, style = MaterialTheme.typography.bodySmall)
                SmallNote("Refs: " + f.refs.joinToString(", "))
            }
        }
        item { Text("References", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp)) }
        items(Citations.all) { c ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Text("[${c.key}] ${c.text}", style = MaterialTheme.typography.bodySmall)
                SmallNote("Used for: ${c.usedFor}")
            }
            HorizontalDivider()
        }
        item { Spacer(Modifier.padding(12.dp)) }
    }
}
