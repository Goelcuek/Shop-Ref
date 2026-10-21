package com.shopref.app.ui.reference

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shopref.app.state.AppSettings
import com.shopref.app.ui.components.Dropdown
import com.shopref.app.ui.components.FieldRow
import com.shopref.app.ui.components.NumberField
import com.shopref.app.ui.components.ResultRow
import com.shopref.app.ui.components.SectionCard
import com.shopref.app.ui.components.SmallNote
import com.shopref.app.ui.screens.threadPitchLabel
import com.shopref.core.reference.DrillSize
import com.shopref.core.reference.DrillSizes
import com.shopref.core.reference.ThreadMath
import com.shopref.core.reference.ThreadSpec
import com.shopref.core.reference.ThreadStandard
import com.shopref.core.reference.Threads
import com.shopref.core.units.Quantity
import com.shopref.core.units.UnitSystem
import com.shopref.core.units.formatNumber

internal fun len(mm: Double, units: UnitSystem) = Quantity.LENGTH.format(mm, units)

internal fun drillSetFor(thread: ThreadSpec): List<DrillSize> =
    if (thread.standard.isInch && !thread.standard.isPipe) DrillSizes.inchAll else DrillSizes.metric

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardChips(selected: ThreadStandard, onSelect: (ThreadStandard) -> Unit, includePipe: Boolean = true) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ThreadStandard.entries.filter { includePipe || !it.isPipe }.forEach { s ->
            FilterChip(selected = s == selected, onClick = { onSelect(s) }, label = { Text(s.label) })
        }
    }
}

@Composable
fun TapDrillChartPage(settings: AppSettings, modifier: Modifier = Modifier) {
    val units = settings.units
    var standard by rememberSaveable { mutableStateOf(ThreadStandard.METRIC_COARSE) }
    val threads = Threads.byStandard(standard)
    val u = Quantity.LENGTH.label(units)
    Column(modifier) {
        StandardChips(standard, { standard = it })
        LazyColumn(Modifier.fillMaxWidth()) {
            if (standard.isPipe) {
                val weights = listOf(1.2f, 1f, 1.3f, 1.3f)
                item {
                    TableRow(
                        listOf("Size", "Major Ø ($u)", "Tap drill", if (standard == ThreadStandard.NPT) "With reamer" else "Pitch"),
                        weights, header = true,
                    )
                }
                items(threads) { t ->
                    TableRow(
                        listOf(
                            t.designation,
                            len(t.majorDiameter, units),
                            "${t.tapDrillLabel}\n${len(t.tapDrillMm, units)}",
                            t.reamedDrillLabel ?: threadPitchLabel(t),
                        ),
                        weights,
                    )
                }
            } else {
                val weights = listOf(1.3f, 1.2f, 1.2f, 1.3f)
                item { TableRow(listOf("Thread", "Cut tap (≈75%)", "Form tap (65%)", "Minor Ø ($u)"), weights, header = true) }
                items(threads) { t ->
                    val formIdeal = ThreadMath.drillForPercentForm(t.majorDiameter, t.pitch, 65.0)
                    val form = DrillSizes.nearest(formIdeal, drillSetFor(t))
                    TableRow(
                        listOf(
                            "${t.designation}\n${threadPitchLabel(t)}",
                            "${t.tapDrillLabel}\n${len(t.tapDrillMm, units)}",
                            "${form.label}\n${len(form.mm, units)}",
                            "${len(t.minorDiameterMin, units)}\n${t.minorDiameterMax?.let { len(it, units) } ?: "—"}",
                        ),
                        weights,
                    )
                }
            }
            item {
                SmallNote(
                    "Cut-tap drills are the common chart sizes (≈75% thread). Form-tap drills use d = D − 0.0068·%·P. " +
                        "Minor Ø shows the internal-thread limits (6H metric, 2B unified).",
                    Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
fun ThreadPercentPage(settings: AppSettings, modifier: Modifier = Modifier) {
    val units = settings.units
    var standard by rememberSaveable { mutableStateOf(ThreadStandard.UNC) }
    var thread by remember(standard) { mutableStateOf(Threads.byStandard(standard).first()) }
    var drill by remember(thread) { mutableStateOf(thread.tapDrillMm) }
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard("Thread & drill") {
            FieldRow {
                Dropdown("Standard", ThreadStandard.entries.filter { !it.isPipe }, standard, { standard = it }, Modifier.weight(1f), optionLabel = { it.label })
                Dropdown("Size", Threads.byStandard(standard), thread, { thread = it }, Modifier.weight(1f), optionLabel = { it.designation }, optionDetail = { threadPitchLabel(it) })
            }
            NumberField("Drill Ø", drill, { v -> v?.let { drill = it } }, Quantity.LENGTH, units, Modifier.fillMaxWidth(), decimals = 4, min = 0.1)
            val near = DrillSizes.nearest(drill)
            SmallNote("Nearest standard drill: ${near.label} (${Quantity.LENGTH.formatWithUnit(near.mm, units)})")
            ResultRow("Cut tap engagement", formatNumber(ThreadMath.percentThreadCut(thread.majorDiameter, thread.pitch, drill), 1) + "%", emphasize = true)
            ResultRow("Form tap engagement", formatNumber(ThreadMath.percentThreadForm(thread.majorDiameter, thread.pitch, drill), 1) + "%", emphasize = true)
        }
        SectionCard("Drill for a target engagement") {
            val weights = listOf(0.7f, 1f, 1.3f, 0.8f)
            TableRow(listOf("%", "Exact Ø", "Nearest drill", "Actual"), weights, header = true)
            for (pct in listOf(50, 55, 60, 65, 70, 75, 80, 85)) {
                val exact = ThreadMath.drillForPercentCut(thread.majorDiameter, thread.pitch, pct.toDouble())
                val d = DrillSizes.nearest(exact, drillSetFor(thread))
                val actual = ThreadMath.percentThreadCut(thread.majorDiameter, thread.pitch, d.mm)
                TableRow(listOf("$pct% cut", len(exact, units), "${d.label}\n${len(d.mm, units)}", formatNumber(actual, 0) + "%"), weights)
            }
            for (pct in listOf(55, 60, 65, 70)) {
                val exact = ThreadMath.drillForPercentForm(thread.majorDiameter, thread.pitch, pct.toDouble())
                val d = DrillSizes.nearest(exact, drillSetFor(thread))
                val actual = ThreadMath.percentThreadForm(thread.majorDiameter, thread.pitch, d.mm)
                TableRow(listOf("$pct% form", len(exact, units), "${d.label}\n${len(d.mm, units)}", formatNumber(actual, 0) + "%"), weights)
            }
            SmallNote("Above ≈75% thread, strength barely increases while tapping torque rises sharply. Use 60–65% in tough materials (stainless, titanium, nickel alloys).")
        }
    }
}
