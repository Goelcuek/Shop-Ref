package com.shopref.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shopref.core.units.Conversions
import com.shopref.core.units.Quantity
import com.shopref.core.units.UnitSystem
import com.shopref.core.units.formatNumber
import kotlin.math.abs
import kotlin.math.roundToInt

/** Formats a number for an editable field: fixed decimals with trailing zeros trimmed. */
fun formatEditable(value: Double, decimals: Int): String {
    val s = formatNumber(value, decimals)
    return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
}

/** Parses user input; accepts "," as decimal separator and inch fractions such as "1/2" or "1-1/4". */
fun parseNumber(text: String, allowFraction: Boolean): Double? {
    val t = text.trim().replace(',', '.')
    if (t.isEmpty()) return null
    t.toDoubleOrNull()?.let { return it }
    return if (allowFraction) Conversions.parseInchFraction(t) else null
}

/**
 * Numeric input bound to a value in base (metric) units. The field shows and accepts the value
 * in the active [units]. A null value means "automatic": the field is blank and shows [autoValue]
 * as a placeholder.
 */
@Composable
fun NumberField(
    label: String,
    value: Double?,
    onValueChange: (Double?) -> Unit,
    quantity: Quantity,
    units: UnitSystem,
    modifier: Modifier = Modifier,
    autoValue: Double? = null,
    decimals: Int = quantity.decimals(units) + 1,
    allowAuto: Boolean = autoValue != null,
    min: Double? = null,
) {
    fun display(v: Double?): String = v?.let { formatEditable(quantity.toDisplay(it, units), decimals) } ?: ""

    var text by remember(units, quantity) { mutableStateOf(display(value)) }
    var lastEmitted by remember(units, quantity) { mutableStateOf(value) }

    // Sync when the value changes from outside (reset, unit switch, recommendation applied).
    LaunchedEffect(value, units) {
        val same = when {
            value == null && lastEmitted == null -> true
            value != null && lastEmitted != null -> abs(value - lastEmitted!!) <= 1e-9 * (1 + abs(value))
            else -> false
        }
        if (!same) {
            text = display(value)
            lastEmitted = value
        }
    }

    val parsed = parseNumber(text, allowFraction = units == UnitSystem.IMPERIAL && quantity == Quantity.LENGTH)
    val invalid = text.isNotBlank() && (parsed == null || (min != null && quantity.fromDisplay(parsed, units) < min))
    val unitLabel = quantity.label(units)

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            if (newText.isBlank()) {
                if (allowAuto) {
                    lastEmitted = null
                    onValueChange(null)
                }
            } else {
                val p = parseNumber(newText, allowFraction = units == UnitSystem.IMPERIAL && quantity == Quantity.LENGTH)
                if (p != null) {
                    val base = quantity.fromDisplay(p, units)
                    if (min == null || base >= min) {
                        lastEmitted = base
                        onValueChange(base)
                    }
                }
            }
        },
        label = { Text(label, maxLines = 1) },
        placeholder = if (autoValue != null) {
            { Text("auto " + formatEditable(quantity.toDisplay(autoValue, units), decimals)) }
        } else null,
        suffix = if (unitLabel.isNotEmpty()) {
            { Text(unitLabel, style = MaterialTheme.typography.bodySmall) }
        } else null,
        isError = invalid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        modifier = modifier,
    )
}

@Composable
fun IntField(label: String, value: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier, range: IntRange = 1..99) {
    var text by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) {
        if (text.toIntOrNull() != value) text = value.toString()
    }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t.filter { it.isDigit() }.take(3)
            text.toIntOrNull()?.takeIf { it in range }?.let(onValueChange)
        },
        label = { Text(label, maxLines = 1) },
        isError = text.toIntOrNull()?.let { it !in range } ?: true,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        modifier = modifier,
    )
}

/** Read-only text field that opens a menu of options. */
@Composable
fun <T> Dropdown(
    label: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionLabel: (T) -> String = { it.toString() },
    optionDetail: ((T) -> String?)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label, maxLines = 1) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparent overlay so a tap anywhere on the field opens the menu.
        Box(
            Modifier
                .matchParentSize()
                .padding(top = 8.dp)
                .clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(optionLabel(option))
                            optionDetail?.invoke(option)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
fun PercentSlider(label: String, percent: Double, onChange: (Double) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                "${percent.roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = if (percent.roundToInt() == 100) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onChange(100.0) },
            )
        }
        Slider(
            value = percent.toFloat(),
            onValueChange = { onChange((it / 5f).roundToInt() * 5.0) },
            valueRange = 50f..150f,
            steps = 19,
        )
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, detail: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
