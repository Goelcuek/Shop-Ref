package com.shopref.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.shopref.core.calc.MillingForceModel
import com.shopref.core.units.Quantity
import com.shopref.core.units.UnitSystem
import kotlin.math.abs
import kotlin.math.max

/** Cutting forces over one cutter revolution: Fx (feed), Fy (normal), resultant |Fxy| and Fz. */
@Composable
fun ForceChart(samples: List<MillingForceModel.Sample>, units: UnitSystem, modifier: Modifier = Modifier) {
    if (samples.isEmpty()) return
    val cResultant = MaterialTheme.colorScheme.primary
    val cFx = Color(0xFFE07B00)
    val cFy = Color(0xFF2E9E44)
    val cFz = Color(0xFF8E6BBE)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val axis = MaterialTheme.colorScheme.outline

    val maxAbs = samples.maxOf { max(max(abs(it.fx), abs(it.fy)), max(it.fxy, abs(it.fz))) }.coerceAtLeast(1e-6)
    val minVal = samples.minOf { minOf(it.fx, it.fy, it.fz, 0.0) }
    val top = maxAbs * 1.1
    val bottom = if (minVal < 0) -maxAbs * 1.1 else 0.0

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SmallNote("+" + Quantity.FORCE.formatWithUnit(top, units))
            SmallNote("one revolution →")
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp),
        ) {
            val w = size.width
            val h = size.height
            fun y(v: Double) = (h * (top - v) / (top - bottom)).toFloat()
            val n = samples.size
            fun x(i: Int) = w * i / (n - 1).coerceAtLeast(1)

            // Grid: quarter revolutions and zero line.
            for (q in 0..4) {
                val gx = w * q / 4f
                drawLine(grid, Offset(gx, 0f), Offset(gx, h), strokeWidth = 1f)
            }
            drawLine(axis, Offset(0f, y(0.0)), Offset(w, y(0.0)), strokeWidth = 1.5f)

            fun series(color: Color, dashed: Boolean = false, value: (MillingForceModel.Sample) -> Double) {
                val path = Path()
                samples.forEachIndexed { i, s ->
                    val px = x(i)
                    val py = y(value(s))
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(
                    path,
                    color,
                    style = Stroke(
                        width = 2.5f,
                        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null,
                    ),
                )
            }
            series(cFz, dashed = true) { it.fz }
            series(cFx) { it.fx }
            series(cFy) { it.fy }
            series(cResultant) { it.fxy }
        }
        if (bottom < 0) SmallNote(Quantity.FORCE.formatWithUnit(bottom, units))
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(cResultant, "|Fxy|")
            Legend(cFx, "Fx feed")
            Legend(cFy, "Fy normal")
            Legend(cFz, "Fz axial")
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
