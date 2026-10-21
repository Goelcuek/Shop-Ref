package com.shopref.app.ui.reference

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shopref.app.state.AppSettings

enum class ReferencePage(val title: String, val description: String) {
    TAP_DRILLS("Tap drill charts", "Metric, UNC/UNF/UNEF, NPT and BSP — cut & form tap drills, minor Ø limits"),
    THREAD_PERCENT("Thread engagement", "Percent thread for any drill, drill for any %"),
    DRILL_SIZES("Drill sizes", "Number, letter, fractional and metric drills"),
    CLEARANCE("Clearance holes", "ISO 273 metric and inch close/free fits"),
    COUNTERBORES("Counterbores & countersinks", "Socket head and flat head screws, countersink depth"),
    FITS("Fits & tolerances", "ISO 286 hole/shaft limits and fit calculator"),
    HARDNESS("Hardness conversion", "HRC ↔ HV ↔ HB ↔ approx. tensile strength"),
    SURFACE("Surface finish", "Ra/RMS, N-grades and process capability"),
    CALCULATORS("Quick calculators", "rpm ↔ surface speed, feed ↔ chip load, unit conversions"),
    MATERIALS("Material data", "Kienzle constants and base speeds for every material"),
    FORMULAS("Formulas & references", "Equations used by the calculators with academic citations"),
}

@Composable
fun ReferenceScreen(settings: AppSettings, page: ReferencePage?, onOpen: (ReferencePage) -> Unit, modifier: Modifier = Modifier) {
    when (page) {
        null -> ReferenceIndex(onOpen, modifier)
        ReferencePage.TAP_DRILLS -> TapDrillChartPage(settings, modifier)
        ReferencePage.THREAD_PERCENT -> ThreadPercentPage(settings, modifier)
        ReferencePage.DRILL_SIZES -> DrillSizesPage(settings, modifier)
        ReferencePage.CLEARANCE -> ClearancePage(settings, modifier)
        ReferencePage.COUNTERBORES -> CounterborePage(settings, modifier)
        ReferencePage.FITS -> FitsPage(settings, modifier)
        ReferencePage.HARDNESS -> HardnessPage(modifier)
        ReferencePage.SURFACE -> SurfaceFinishPage(settings, modifier)
        ReferencePage.CALCULATORS -> QuickCalculatorsPage(settings, modifier)
        ReferencePage.MATERIALS -> MaterialDataPage(settings, modifier)
        ReferencePage.FORMULAS -> FormulasPage(modifier)
    }
}

@Composable
private fun ReferenceIndex(onOpen: (ReferencePage) -> Unit, modifier: Modifier) {
    LazyColumn(modifier) {
        items(ReferencePage.entries) { p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(p) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.title, style = MaterialTheme.typography.titleMedium)
                    Text(p.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
            HorizontalDivider()
        }
    }
}

/** One row of a simple data table. [weights] sets relative column widths. */
@Composable
fun TableRow(cells: List<String>, weights: List<Float>, header: Boolean = false, highlight: Boolean = false) {
    val bg = when {
        header -> MaterialTheme.colorScheme.primaryContainer
        highlight -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 8.dp, vertical = if (header) 8.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cells.forEachIndexed { i, c ->
            Text(
                c,
                modifier = Modifier.weight(weights.getOrElse(i) { 1f }),
                style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                fontWeight = if (header || i == 0) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = if (!header && i > 0) FontFamily.Monospace else FontFamily.Default,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                color = if (header) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    if (!header) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
