package com.shopref.app.ui.screens

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
    val units = settings.units
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard("Units") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = units == UnitSystem.METRIC, onClick = { settings.updateUnits(UnitSystem.METRIC) }, label = { Text("Metric (mm, m/min, kW, N)") })
                FilterChip(selected = units == UnitSystem.IMPERIAL, onClick = { settings.updateUnits(UnitSystem.IMPERIAL) }, label = { Text("Inch (in, SFM, hp, lbf)") })
            }
        }
        SectionCard("Milling recommendations") {
            Dropdown(
                "Chip-load level", ChipLoadLevel.entries, settings.chipLoadLevel, settings::updateChipLoadLevel, Modifier.fillMaxWidth(),
                optionLabel = { it.label }, optionDetail = { it.description },
            )
            SmallNote(
                "Scales the recommended chip load and cutting speed. Conservative ≈ cautious catalogue starting values; " +
                    "Aggressive ≈ high-end catalogue / HEM practice on a rigid machine with a good holder.",
            )
        }
        MachineEditor("Milling machine", settings.mill, units, settings::updateMill)
        MachineEditor("Lathe", settings.lathe, units, settings::updateLathe)
        SectionCard("About") {
            Text("Shop Ref — machining reference and feeds & speeds calculator.", style = MaterialTheme.typography.bodyMedium)
            SmallNote(
                "Recommendations are starting points computed from published cutting-mechanics models and typical " +
                    "catalogue data (see Reference → Formulas & references). Always follow your tool manufacturer's " +
                    "data, start conservatively and watch the cut. Forces assume a rigid tool without runout or chatter.",
            )
        }
    }
}

@Composable
private fun MachineEditor(title: String, machine: Machine, units: UnitSystem, onChange: (Machine) -> Unit) {
    SectionCard(title) {
        Dropdown(
            "Load preset", Machine.PRESETS, machine, { p -> onChange(p) }, Modifier.fillMaxWidth(),
            optionLabel = { it.name },
        )
        OutlinedTextField(
            value = machine.name,
            onValueChange = { onChange(machine.copy(name = it)) },
            label = { Text("Name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth(),
        )
        FieldRow {
            NumberField("Max rpm", machine.maxRpm, { v -> v?.let { onChange(machine.copy(maxRpm = it)) } }, Quantity.SPINDLE_SPEED, units, Modifier.weight(1f), decimals = 0, min = 1.0)
            NumberField("Min rpm", machine.minRpm, { v -> v?.let { onChange(machine.copy(minRpm = it)) } }, Quantity.SPINDLE_SPEED, units, Modifier.weight(1f), decimals = 0, min = 0.0)
        }
        FieldRow {
            NumberField("Spindle power", machine.powerKw, { v -> v?.let { onChange(machine.copy(powerKw = it)) } }, Quantity.POWER, units, Modifier.weight(1f), min = 0.01)
            NumberField("Max torque (0 = n/a)", machine.maxTorqueNm, { v -> v?.let { onChange(machine.copy(maxTorqueNm = it)) } }, Quantity.TORQUE, units, Modifier.weight(1f), min = 0.0)
        }
        FieldRow {
            NumberField("Max feed", machine.maxFeed, { v -> v?.let { onChange(machine.copy(maxFeed = it)) } }, Quantity.FEED_RATE, units, Modifier.weight(1f), min = 1.0)
            NumberField("Efficiency", machine.efficiency * 100.0, { v -> v?.let { onChange(machine.copy(efficiency = (it / 100.0).coerceIn(0.1, 1.0))) } }, Quantity.PERCENT, units, Modifier.weight(1f), decimals = 0)
        }
        Dropdown("Rigidity", Rigidity.entries, machine.rigidity, { r -> onChange(machine.copy(rigidity = r)) }, Modifier.fillMaxWidth(), optionLabel = { it.label })
        SmallNote("Rigidity scales recommended depths and chip loads. Max torque enables the constant-torque region below base speed.")
    }
}
