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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shopref.app.state.AppSettings
import com.shopref.app.ui.components.FieldRow
import com.shopref.app.ui.components.NumberField
import com.shopref.app.ui.components.QuantityRow
import com.shopref.app.ui.components.SectionCard
import com.shopref.app.ui.components.SmallNote
import com.shopref.core.reference.DrillSeries
import com.shopref.core.reference.DrillSizes
import com.shopref.core.reference.Holes
import com.shopref.core.units.Quantity
import com.shopref.core.units.formatNumber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }) }
    }
}

@Composable
fun DrillSizesPage(settings: AppSettings, modifier: Modifier = Modifier) {
    val units = settings.units
    var series by rememberSaveable { mutableStateOf<DrillSeries?>(null) }
    var target by remember { mutableStateOf<Double?>(null) }
    val list = if (series == null) DrillSizes.all else DrillSizes.all.filter { it.series == series }
    val nearest = target?.let { t -> if (list.isEmpty()) null else DrillSizes.nearest(t, list) }
    Column(modifier) {
        ChipRow(listOf<DrillSeries?>(null) + DrillSeries.entries, series, { it?.label ?: "All" }, { series = it })
        NumberField(
            "Find nearest drill to…", target, { target = it }, Quantity.LENGTH, units,
            Modifier.fillMaxWidth().padding(horizontal = 12.dp), allowAuto = true,
        )
        LazyColumn(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            val weights = listOf(1.2f, 1f, 1f, 1.2f)
            item { TableRow(listOf("Drill", "mm", "inch", "Series"), weights, header = true) }
            items(list) { d ->
                TableRow(
                    listOf(d.label, formatNumber(d.mm, 3), formatNumber(d.inch, 4), d.series.label),
                    weights, highlight = d == nearest,
                )
            }
        }
    }
}

private enum class ScrewSystem(val label: String) { METRIC("Metric (ISO)"), INCH("Inch (UNC/UNF)") }

@Composable
fun ClearancePage(settings: AppSettings, modifier: Modifier = Modifier) {
    val units = settings.units
    var system by rememberSaveable { mutableStateOf(ScrewSystem.METRIC) }
    val u = Quantity.LENGTH.label(units)
    Column(modifier) {
        ChipRow(ScrewSystem.entries, system, { it.label }, { system = it })
        LazyColumn(Modifier.fillMaxWidth()) {
            if (system == ScrewSystem.METRIC) {
                val w = listOf(1f, 1f, 1f, 1f)
                item { TableRow(listOf("Screw", "Fine ($u)", "Medium", "Coarse"), w, header = true) }
                items(Holes.metricClearance) { h ->
                    TableRow(listOf(h.size, len(h.close, units), len(h.normal, units), h.loose?.let { len(it, units) } ?: "—"), w)
                }
                item { SmallNote("ISO 273 series: fine (close fit), medium (normal), coarse (loose).", Modifier.padding(12.dp)) }
            } else {
                val w = listOf(0.8f, 1.3f, 1.3f)
                item { TableRow(listOf("Screw", "Close fit ($u)", "Free fit"), w, header = true) }
                items(Holes.inchClearance) { h ->
                    TableRow(listOf(h.size, "${h.closeLabel}\n${len(h.close, units)}", "${h.normalLabel}\n${len(h.normal, units)}"), w)
                }
            }
        }
    }
}

@Composable
fun CounterborePage(settings: AppSettings, modifier: Modifier = Modifier) {
    val units = settings.units
    var system by rememberSaveable { mutableStateOf(ScrewSystem.METRIC) }
    var cskDia by remember { mutableDoubleStateOf(12.0) }
    var cskAngle by remember { mutableDoubleStateOf(90.0) }
    var cskHole by remember { mutableDoubleStateOf(0.0) }
    val u = Quantity.LENGTH.label(units)
    Column(modifier) {
        ChipRow(ScrewSystem.entries, system, { it.label }, { system = it })
        LazyColumn(Modifier.fillMaxWidth()) {
            val cb = if (system == ScrewSystem.METRIC) Holes.metricCounterbores else Holes.inchCounterbores
            val w = listOf(0.8f, 1f, 1f, 1f, 1f)
            item {
                Text("Socket head cap screws (${u})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(12.dp))
                TableRow(listOf("Screw", "Head Ø", "C'bore Ø", "Depth", "Thru hole"), w, header = true)
            }
            items(cb) { c ->
                TableRow(listOf(c.size, len(c.headDiameter, units), len(c.cboreDiameter, units), len(c.cboreDepth, units), len(c.clearance, units)), w)
            }
            val cs = if (system == ScrewSystem.METRIC) Holes.metricCountersinks else Holes.inchCountersinks
            val w2 = listOf(0.8f, 1f, 0.7f, 1f, 1f)
            item {
                Text("Flat (countersunk) head screws (${u})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(12.dp))
                TableRow(listOf("Screw", "Head Ø", "Angle", "Thru hole", "Spot depth*"), w2, header = true)
            }
            items(cs) { c ->
                val depth = Holes.countersinkDepth(c.headDiameter + if (system == ScrewSystem.METRIC) 0.2 else 0.005 * 25.4, c.angle)
                TableRow(listOf(c.size, len(c.headDiameter, units), formatNumber(c.angle, 0) + "°", len(c.clearance, units), len(depth, units)), w2)
            }
            item {
                SmallNote("*Plunge depth of a sharp-pointed countersink/spot drill of the same angle for a head slightly below flush.", Modifier.padding(12.dp))
                Column(Modifier.padding(12.dp)) {
                    SectionCard("Countersink calculator") {
                        FieldRow {
                            NumberField("Countersink Ø", cskDia, { v -> v?.let { cskDia = it } }, Quantity.LENGTH, units, Modifier.weight(1f))
                            NumberField("Angle", cskAngle, { v -> v?.let { cskAngle = it.coerceIn(20.0, 179.0) } }, Quantity.ANGLE, units, Modifier.weight(1f))
                        }
                        NumberField("Tool tip Ø (0 = sharp)", cskHole, { v -> cskHole = v ?: 0.0 }, Quantity.LENGTH, units, Modifier.fillMaxWidth())
                        QuantityRow("Plunge depth", Holes.countersinkDepth(cskDia, cskAngle, cskHole), Quantity.LENGTH, units, emphasize = true)
                    }
                }
            }
        }
    }
}
