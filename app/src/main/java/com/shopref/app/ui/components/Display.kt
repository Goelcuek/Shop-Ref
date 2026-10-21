package com.shopref.app.ui.components

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.shopref.core.material.IsoGroup
import com.shopref.core.units.Quantity
import com.shopref.core.units.UnitSystem

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** Prominent result tile (rpm, feed). */
@Composable
fun BigResult(label: String, value: String, unit: String, modifier: Modifier = Modifier, sub: String? = null) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun BigQuantity(label: String, base: Double, quantity: Quantity, units: UnitSystem, modifier: Modifier = Modifier, sub: String? = null) =
    BigResult(label, quantity.format(base, units), quantity.label(units), modifier, sub)

@Composable
fun ResultRow(label: String, value: String, modifier: Modifier = Modifier, emphasize: Boolean = false, valueColor: Color? = null) {
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
    ResultRow(label, quantity.formatWithUnit(base, units, decimals ?: quantity.decimals(units)), emphasize = emphasize, valueColor = valueColor)
}

@Composable
fun ThinDivider() = HorizontalDivider(Modifier.padding(vertical = 4.dp))

@Composable
fun AdviceList(advice: List<Advice>, modifier: Modifier = Modifier) {
    if (advice.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        advice.sortedByDescending { it.level.ordinal }.forEach { AdviceItem(it) }
    }
}

@Composable
fun AdviceItem(advice: Advice) {
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
