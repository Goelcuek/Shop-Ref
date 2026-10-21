package com.shopref.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shopref.app.state.AppSettings
import com.shopref.app.state.Session
import com.shopref.app.state.TurningState
import com.shopref.app.ui.components.AdviceList
import com.shopref.app.ui.components.BigQuantity
import com.shopref.app.ui.components.Dropdown
import com.shopref.app.ui.components.FieldRow
import com.shopref.app.ui.components.MaterialSelector
import com.shopref.app.ui.components.NumberField
import com.shopref.app.ui.components.PercentSlider
import com.shopref.app.ui.components.QuantityRow
import com.shopref.app.ui.components.ResultRow
import com.shopref.app.ui.components.SectionCard
import com.shopref.app.ui.components.SmallNote
import com.shopref.app.ui.components.ThinDivider
import com.shopref.app.ui.components.utilisationColor
import com.shopref.core.calc.InsertGrade
import com.shopref.core.calc.TurnOperation
import com.shopref.core.calc.TurningCalculator
import com.shopref.core.tooling.ToolCondition
import com.shopref.core.units.Quantity
import com.shopref.core.units.formatNumber

private val noseRadii = listOf(0.2, 0.4, 0.8, 1.2, 1.6, 2.4)

@Composable
fun TurningScreen(settings: AppSettings, session: Session, state: TurningState, modifier: Modifier = Modifier) {
    val units = settings.units
    val input = state.input.copy(material = session.material, machine = settings.lathe, units = units)
    val result = remember(input) { TurningCalculator.calculate(input) }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MaterialSelector(session.material, onSelect = session::selectMaterial)

        SectionCard("Insert & operation") {
            FieldRow {
                Dropdown("Insert grade", InsertGrade.entries, input.grade, { g -> state.update { it.copy(grade = g) } }, Modifier.weight(1f), optionLabel = { it.label })
                Dropdown("Operation", TurnOperation.entries, input.operation, { o -> state.update { it.copy(operation = o, ap = null, feed = null) } }, Modifier.weight(1f), optionLabel = { it.label })
            }
            FieldRow {
                Dropdown(
                    "Nose radius rε", noseRadii, input.noseRadius, { r -> state.update { it.copy(noseRadius = r) } }, Modifier.weight(1f),
                    optionLabel = { Quantity.LENGTH.formatWithUnit(it, units, if (units == com.shopref.core.units.UnitSystem.METRIC) 1 else 4) },
                )
                NumberField("Entering angle κr", input.enteringAngle, { v -> v?.let { a -> state.update { it.copy(enteringAngle = a) } } }, Quantity.ANGLE, units, Modifier.weight(1f))
            }
            FieldRow {
                NumberField("Rake angle", input.rakeDeg, { v -> v?.let { a -> state.update { it.copy(rakeDeg = a) } } }, Quantity.ANGLE, units, Modifier.weight(1f))
                Dropdown("Edge condition", ToolCondition.entries, input.condition, { c -> state.update { it.copy(condition = c) } }, Modifier.weight(1f), optionLabel = { it.label })
            }
        }

        SectionCard("Cut") {
            FieldRow {
                NumberField("Work diameter", input.diameter, { v -> v?.let { d -> state.update { it.copy(diameter = d) } } }, Quantity.LENGTH, units, Modifier.weight(1f), min = 0.5)
                NumberField("Length of cut", input.lengthOfCut, { v -> v?.let { l -> state.update { it.copy(lengthOfCut = l) } } }, Quantity.LENGTH, units, Modifier.weight(1f))
            }
            FieldRow {
                NumberField("Depth of cut ap", input.ap, { v -> state.update { it.copy(ap = v) } }, Quantity.LENGTH, units, Modifier.weight(1f), autoValue = TurningCalculator.defaultDepth(input))
                NumberField("Feed f", input.feed, { v -> state.update { it.copy(feed = v) } }, Quantity.FEED_PER_REV, units, Modifier.weight(1f), autoValue = TurningCalculator.defaultFeed(input))
            }
            SmallNote("Lathe: ${settings.lathe.name}. Depth of cut is radial (diameter changes by 2·ap).")
        }

        SectionCard("Speeds & feeds") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigQuantity("Spindle", result.rpm, Quantity.SPINDLE_SPEED, units, Modifier.weight(1f))
                BigQuantity("Feed rate", result.feedRate, Quantity.FEED_RATE, units, Modifier.weight(1f))
            }
            QuantityRow("Cutting speed", result.vc, Quantity.CUTTING_SPEED, units, emphasize = true)
            QuantityRow("Feed per revolution", result.feed, Quantity.FEED_PER_REV, units)
            QuantityRow("Depth of cut", result.ap, Quantity.LENGTH, units)
            PercentSlider("Speed override", input.speedPercent, { p -> state.update { it.copy(speedPercent = p) } })
            ResultRow("Cycle time", formatNumber(result.timeMin, 2) + " min")
        }

        SectionCard("Forces, power & finish") {
            QuantityRow("Cutting force Fc", result.fc, Quantity.FORCE, units, emphasize = true)
            QuantityRow("Feed force Ff", result.ff, Quantity.FORCE, units)
            QuantityRow("Passive (radial) force Fp", result.fp, Quantity.FORCE, units)
            QuantityRow("Specific cutting force kc", result.kc, Quantity.STRESS, units)
            QuantityRow("Cutting power", result.powerKw, Quantity.POWER, units, emphasize = true, valueColor = utilisationColor(result.powerKw / result.availablePowerKw))
            QuantityRow("Available at this rpm", result.availablePowerKw, Quantity.POWER, units)
            QuantityRow("Spindle torque", result.torqueNm, Quantity.TORQUE, units)
            QuantityRow("Material removal rate", result.mrr, Quantity.MRR, units)
            ThinDivider()
            QuantityRow("Theoretical Ra", result.raMicron, Quantity.ROUGHNESS, units, emphasize = true)
            QuantityRow("Theoretical Rt (peak-to-valley)", result.rtMicron, Quantity.ROUGHNESS, units)
            SmallNote("Fc = kc1.1·b·h^(1−mc), h = f·sin κr, b = ap/sin κr. Ra ≈ f²/(32·rε); real finish is usually 1.5–3× worse.")
        }

        RaFeedCard(settings)
        AdviceList(result.advice)
        TextButton(onClick = state::reset, modifier = Modifier.fillMaxWidth()) { Text("Reset turning inputs") }
    }
}

@Composable
private fun RaFeedCard(settings: AppSettings) {
    val units = settings.units
    var ra by remember { mutableDoubleStateOf(1.6) }
    var r by remember { mutableDoubleStateOf(0.8) }
    SectionCard("Feed for a target finish") {
        FieldRow {
            NumberField("Target Ra", ra, { v -> v?.let { ra = it } }, Quantity.ROUGHNESS, units, Modifier.weight(1f), min = 0.01)
            NumberField("Nose radius", r, { v -> v?.let { r = it } }, Quantity.LENGTH, units, Modifier.weight(1f), min = 0.01)
        }
        QuantityRow("Max feed", TurningCalculator.feedForRa(ra, r), Quantity.FEED_PER_REV, units, emphasize = true)
    }
}
