package com.shopref.app.ui.screens

import com.shopref.app.ui.LocalLang
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shopref.app.state.AppSettings
import com.shopref.app.state.LanguagePref
import com.shopref.app.ui.components.Dropdown
import com.shopref.app.ui.components.FieldRow
import com.shopref.app.ui.components.NumberField
import com.shopref.app.ui.components.SectionCard
import com.shopref.app.ui.components.SmallNote
import com.shopref.core.tooling.ChipLoadLevel
import com.shopref.core.tooling.Machine
import com.shopref.core.tooling.Rigidity
import com.shopref.core.units.Quantity
import com.shopref.core.units.UnitSystem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: AppSettings, modifier: Modifier = Modifier) {
    val L = LocalLang.current
    val units = settings.units
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(L.tx("Language")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguagePref.entries.forEach { p ->
                    FilterChip(
                        selected = settings.languagePref == p,
                        onClick = { settings.updateLanguage(p) },
                        label = { Text(if (p == LanguagePref.SYSTEM) L.tx("System") else p.label) },
                    )
                }
            }
        }
        SectionCard(L.tx("Units")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = units == UnitSystem.METRIC, onClick = { settings.updateUnits(UnitSystem.METRIC) }, label = { Text(L.tx("Metric (mm, m/min, kW, N)")) })
                FilterChip(selected = units == UnitSystem.IMPERIAL, onClick = { settings.updateUnits(UnitSystem.IMPERIAL) }, label = { Text(L.tx("Inch (in, SFM, hp, lbf)")) })
            }
        }
        SectionCard(L.tx("Milling recommendations")) {
            Dropdown(L.tx("Chip-load level"), ChipLoadLevel.entries, settings.chipLoadLevel, settings::updateChipLoadLevel, Modifier.fillMaxWidth(),
                optionLabel = { L.tx(it.label) }, optionDetail = { L.tx(it.description) },
            )
            SmallNote(L.tx("Scales the recommended chip load and cutting speed. Conservative ≈ cautious catalogue starting values; " +
                    "Aggressive ≈ high-end catalogue / HEM practice on a rigid machine with a good holder."),
            )
        }
        MachineEditor(L.tx("Milling machine"), settings.mill, units, settings::updateMill)
        MachineEditor(L.tx("Lathe"), settings.lathe, units, settings::updateLathe)
        SectionCard(L.tx("About")) {
            Text(L.tx("Shop Ref — machining reference and feeds & speeds calculator."), style = MaterialTheme.typography.bodyMedium)
            SmallNote(L.tx("Recommendations are starting points computed from published cutting-mechanics models and typical " +
                    "catalogue data (see Reference → Formulas & references). Always follow your tool manufacturer's " +
                    "data, start conservatively and watch the cut. Forces assume a rigid tool without runout or chatter."),
            )
        }
    }
}

@Composable
private fun MachineEditor(title: String, machine: Machine, units: UnitSystem, onChange: (Machine) -> Unit) {
    val L = LocalLang.current
    SectionCard(title) {
        Dropdown(L.tx("Load preset"), Machine.PRESETS, machine, { p -> onChange(p) }, Modifier.fillMaxWidth(),
            optionLabel = { L.tx(it.name) },
        )
        OutlinedTextField(
            value = machine.name,
            onValueChange = { onChange(machine.copy(name = it)) },
            label = { Text(L.tx("Name")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth(),
        )
        FieldRow {
            NumberField(L.tx("Max rpm"), machine.maxRpm, { v -> v?.let { onChange(machine.copy(maxRpm = it)) } }, Quantity.SPINDLE_SPEED, units, Modifier.weight(1f), decimals = 0, min = 1.0)
            NumberField(L.tx("Min rpm"), machine.minRpm, { v -> v?.let { onChange(machine.copy(minRpm = it)) } }, Quantity.SPINDLE_SPEED, units, Modifier.weight(1f), decimals = 0, min = 0.0)
        }
        FieldRow {
            NumberField(L.tx("Spindle power"), machine.powerKw, { v -> v?.let { onChange(machine.copy(powerKw = it)) } }, Quantity.POWER, units, Modifier.weight(1f), min = 0.01)
            NumberField(L.tx("Max torque (0 = n/a)"), machine.maxTorqueNm, { v -> v?.let { onChange(machine.copy(maxTorqueNm = it)) } }, Quantity.TORQUE, units, Modifier.weight(1f), min = 0.0)
        }
        FieldRow {
            NumberField(L.tx("Max feed"), machine.maxFeed, { v -> v?.let { onChange(machine.copy(maxFeed = it)) } }, Quantity.FEED_RATE, units, Modifier.weight(1f), min = 1.0)
            NumberField(L.tx("Efficiency"), machine.efficiency * 100.0, { v -> v?.let { onChange(machine.copy(efficiency = (it / 100.0).coerceIn(0.1, 1.0))) } }, Quantity.PERCENT, units, Modifier.weight(1f), decimals = 0)
        }
        Dropdown(L.tx("Rigidity"), Rigidity.entries, machine.rigidity, { r -> onChange(machine.copy(rigidity = r)) }, Modifier.fillMaxWidth(), optionLabel = { L.tx(it.label) })
        SmallNote(L.tx("Rigidity scales recommended depths and chip loads. Max torque enables the constant-torque region below base speed."))
    }
}
