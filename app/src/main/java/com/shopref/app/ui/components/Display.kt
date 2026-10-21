package com.shopref.app.ui.components

import com.shopref.app.ui.LocalLang
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shopref.core.calc.Advice
import com.shopref.core.calc.ToolLife
import com.shopref.core.material.IsoGroup
import com.shopref.core.units.Quantity
import com.shopref.core.units.UnitSystem
import com.shopref.core.units.formatNumber
import com.shopref.core.units.formatSignificant
import kotlin.math.roundToInt

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val L = LocalLang.current
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

/** Two fields side by side. */
@Composable
fun FieldRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val L = LocalLang.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
fun ResultRow(label: String, value: String, modifier: Modifier = Modifier, emphasize: Boolean = false, valueColor: Color? = null) {
    val L = LocalLang.current
    Row(modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            value,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
fun QuantityRow(label: String, base: Double, quantity: Quantity, units: UnitSystem, emphasize: Boolean = false, decimals: Int? = null, valueColor: Color? = null) {
    val L = LocalLang.current
    ResultRow(label, quantity.formatWithUnit(base, units, decimals ?: quantity.decimals(units), L), emphasize = emphasize, valueColor = valueColor)
}

@Composable
fun ThinDivider() = HorizontalDivider(Modifier.padding(vertical = 4.dp))

/** A rough estimate shown to two significant figures. */
@Composable
fun EstimateRow(label: String, base: Double, quantity: Quantity, units: UnitSystem) {
    val L = LocalLang.current
    val unit = quantity.label(units, L)
    ResultRow(label, formatSignificant(quantity.toDisplay(base, units)) + if (unit.isEmpty()) "" else " $unit")
}

/** Tool-life estimate: minutes of cutting, what they buy ([content]) and the model behind them. */
@Composable
fun ToolLifeCard(life: ToolLife.Estimate, content: @Composable ColumnScope.() -> Unit = {}) {
    val L = LocalLang.current
    SectionCard(L.tx("Tool life (estimate)")) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatSignificant(life.minutes), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                " " + L.tx("min of cutting"), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Text(
            L.tx("{0}% of the life at the standard recommendation ({1} min)", (life.factor * 100).roundToInt(), formatSignificant(life.referenceMinutes)),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
        SmallNote(
            L.tx(
                "Extended Taylor: T = Tref·(vref/v)^(1/n)·(href/h)^(0.3/n), n = {0}. Expect ±50% with coolant, runout and work-holding.",
                formatNumber(life.n, 3).trimEnd('0').trimEnd('.'),
            ),
        )
    }
}

fun toolLifeSummary(label: String, life: ToolLife.Estimate) = SummaryItem(label, formatSignificant(life.minutes), "min")

@Composable
fun AdviceList(advice: List<Advice>, modifier: Modifier = Modifier) {
    val L = LocalLang.current
    if (advice.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        advice.sortedByDescending { it.level.ordinal }.forEach { AdviceItem(it) }
    }
}

@Composable
fun AdviceItem(advice: Advice) {
    val L = LocalLang.current
    val (bg, fg) = when (advice.level) {
        Advice.Level.DANGER -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        Advice.Level.CAUTION -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        Advice.Level.INFO -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (advice.level == Advice.Level.INFO) Icons.Default.Info else Icons.Default.Warning,
            contentDescription = advice.level.name,
            tint = fg,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(advice.text, style = MaterialTheme.typography.bodyMedium, color = fg)
    }
}

@Composable
fun IsoBadge(group: IsoGroup, modifier: Modifier = Modifier) {
    val L = LocalLang.current
    val color = Color(group.color)
    Box(
        modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            group.label,
            color = if (group == IsoGroup.M) Color.Black else Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun SmallNote(text: String, modifier: Modifier = Modifier) {
    val L = LocalLang.current
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
fun VSpace(h: Int = 12) = Spacer(Modifier.height(h.dp))

/** Colour for a utilisation ratio (0–1+): neutral, caution, danger. */
@Composable
fun utilisationColor(ratio: Double): Color = when {
    ratio > 1.0 -> MaterialTheme.colorScheme.error
    ratio > 0.85 -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.onSurface
}

/** One value in the pinned results bar. [color] overrides the text colour (e.g. power over the limit). */
data class SummaryItem(val label: String, val value: String, val unit: String, val color: Color? = null)

fun summaryOf(label: String, base: Double, quantity: Quantity, units: UnitSystem, color: Color? = null, decimals: Int? = null) =
    SummaryItem(label, quantity.format(base, units, decimals ?: quantity.decimals(units)), quantity.label(units), color)

/** Text colour for a utilisation ratio in the summary bar, or null when within limits. */
@Composable
fun summaryWarnColor(ratio: Double): Color? = when {
    ratio > 1.0 -> MaterialTheme.colorScheme.error
    ratio > 0.85 -> MaterialTheme.colorScheme.secondary
    else -> null
}

/**
 * Results bar pinned above the scrolling inputs, so the key numbers stay visible while
 * parameters are edited. Items are laid out three per row.
 */
@Composable
fun SummaryBar(items: List<SummaryItem>, modifier: Modifier = Modifier) {
    val L = LocalLang.current
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { SummaryCell(it, Modifier.weight(1f)) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SummaryCell(item: SummaryItem, modifier: Modifier = Modifier) {
    val L = LocalLang.current
    val fg = MaterialTheme.colorScheme.onPrimaryContainer
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(item.label, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.75f), maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                item.value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = item.color ?: fg,
                maxLines = 1,
            )
            if (item.unit.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(L.tx(item.unit), style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}
