package com.shopref.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shopref.app.state.AppSettings
import com.shopref.app.state.MillingState
import com.shopref.app.state.Session
import com.shopref.app.ui.components.AdviceList
import com.shopref.app.ui.components.SummaryBar
import com.shopref.app.ui.components.summaryOf
import com.shopref.app.ui.components.summaryWarnColor
import com.shopref.app.ui.components.Dropdown
import com.shopref.app.ui.components.FieldRow
import com.shopref.app.ui.components.ForceChart
import com.shopref.app.ui.components.IntField
import com.shopref.app.ui.components.MaterialSelector
import com.shopref.app.ui.components.NumberField
import com.shopref.app.ui.components.PercentSlider
import com.shopref.app.ui.components.QuantityRow
import com.shopref.app.ui.components.ResultRow
import com.shopref.app.ui.components.SectionCard
import com.shopref.app.ui.components.SmallNote
import com.shopref.app.ui.components.SwitchRow
import com.shopref.app.ui.components.ThinDivider
import com.shopref.app.ui.components.utilisationColor
import com.shopref.core.calc.MillStrategy
import com.shopref.core.calc.MillToolType
import com.shopref.core.calc.MillingCalculator
import com.shopref.core.tooling.ChipLoadLevel
import com.shopref.core.tooling.Coating
import com.shopref.core.tooling.ToolCondition
import com.shopref.core.tooling.ToolMaterial
import com.shopref.core.units.Quantity
import com.shopref.core.units.formatNumber

@Composable
fun MillingScreen(settings: AppSettings, session: Session, state: MillingState, modifier: Modifier = Modifier) {
    val units = settings.units
    val input = state.input.copy(material = session.material, machine = settings.mill, units = units, chipLoadLevel = settings.chipLoadLevel)
    val result = remember(input) { MillingCalculator.calculate(input) }
    val (apAuto, aeAuto) = remember(input) { MillingCalculator.defaultEngagement(input) }
    var showAdvanced by remember { mutableStateOf(false) }

    Column(modifier) {
        SummaryBar(
            listOf(
                summaryOf("Spindle", result.rpm, Quantity.SPINDLE_SPEED, units),
                summaryOf("Feed", result.feed, Quantity.FEED_RATE, units),
                summaryOf("Power", result.powerKw, Quantity.POWER, units, summaryWarnColor(result.powerKw / result.availablePowerKw)),
                summaryOf("Speed", result.vc, Quantity.CUTTING_SPEED, units),
                summaryOf("Chip load", result.fz, Quantity.CHIP_LOAD, units),
                summaryOf("MRR", result.mrr, Quantity.MRR, units),
            ),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MaterialSelector(session.material, onSelect = session::selectMaterial)

            SectionCard("Tool") {
                Dropdown("Tool type", MillToolType.entries, input.toolType, { t -> state.update { it.copy(toolType = t) } }, Modifier.fillMaxWidth(), optionLabel = { it.label })
                FieldRow {
                    Dropdown("Substrate", ToolMaterial.entries, input.toolMaterial, { t -> state.update { it.copy(toolMaterial = t) } }, Modifier.weight(1f), optionLabel = { it.label })
                    if (input.toolType != MillToolType.INDEXABLE) {
                        Dropdown(
                            "Coating", Coating.entries, input.coating, { c -> state.update { it.copy(coating = c) } }, Modifier.weight(1f),
                            optionLabel = { it.label }, optionDetail = { it.description },
                        )
                    }
                }
                FieldRow {
                    NumberField("Diameter", input.diameter, { v -> v?.let { d -> state.update { it.copy(diameter = d) } } }, Quantity.LENGTH, units, Modifier.weight(1f), min = 0.1)
                    IntField(if (input.toolType == MillToolType.INDEXABLE) "Inserts" else "Flutes", input.flutes, { n -> state.update { it.copy(flutes = n) } }, Modifier.weight(1f), 1..40)
                }
                when (input.toolType) {
                    MillToolType.CORNER_RADIUS -> NumberField("Corner radius", input.cornerRadius, { v -> v?.let { r -> state.update { it.copy(cornerRadius = r) } } }, Quantity.LENGTH, units, Modifier.fillMaxWidth())
                    MillToolType.CHAMFER -> FieldRow {
                        NumberField("Included angle", input.chamferAngle, { v -> v?.let { a -> state.update { it.copy(chamferAngle = a) } } }, Quantity.ANGLE, units, Modifier.weight(1f))
                        NumberField("Tip diameter", input.chamferTipDiameter, { v -> v?.let { a -> state.update { it.copy(chamferTipDiameter = a) } } }, Quantity.LENGTH, units, Modifier.weight(1f))
                    }
                    MillToolType.INDEXABLE -> NumberField("Lead angle κr (90° = square shoulder)", input.leadAngleDeg, { v -> v?.let { a -> state.update { it.copy(leadAngleDeg = a) } } }, Quantity.ANGLE, units, Modifier.fillMaxWidth())
                    else -> Unit
                }
                if (input.toolType != MillToolType.INDEXABLE) {
                    FieldRow {
                        NumberField("Flute length", input.fluteLength, { v -> state.update { it.copy(fluteLength = v) } }, Quantity.LENGTH, units, Modifier.weight(1f), autoValue = MillingCalculator.fluteLengthOf(input.copy(fluteLength = null)))
                        NumberField("Stick-out", input.stickout, { v -> state.update { it.copy(stickout = v) } }, Quantity.LENGTH, units, Modifier.weight(1f), autoValue = MillingCalculator.stickoutOf(input.copy(stickout = null)))
                    }
                }
                TextButton(onClick = { showAdvanced = !showAdvanced }) { Text(if (showAdvanced) "Hide advanced" else "Advanced: helix, rake, shank, wear") }
                if (showAdvanced) {
                    FieldRow {
                        NumberField("Helix", input.helixDeg, { v -> v?.let { h -> state.update { it.copy(helixDeg = h) } } }, Quantity.ANGLE, units, Modifier.weight(1f))
                        NumberField("Radial rake", input.rakeDeg, { v -> state.update { it.copy(rakeDeg = v) } }, Quantity.ANGLE, units, Modifier.weight(1f), autoValue = if (input.toolType == MillToolType.INDEXABLE) 10.0 else MillingCalculator.defaultRake(input.material))
                    }
                    FieldRow {
                        NumberField("Shank Ø", input.shankDiameter, { v -> state.update { it.copy(shankDiameter = v) } }, Quantity.LENGTH, units, Modifier.weight(1f), autoValue = input.diameter)
                        Dropdown("Edge condition", ToolCondition.entries, input.condition, { c -> state.update { it.copy(condition = c) } }, Modifier.weight(1f), optionLabel = { it.label })
                    }
                }
            }

            SectionCard("Cut") {
                Dropdown(
                    "Strategy", MillStrategy.entries, input.strategy, { s -> state.update { it.copy(strategy = s, ap = null, ae = null) } },
                    Modifier.fillMaxWidth(), optionLabel = { it.label }, optionDetail = { it.description },
                )
                FieldRow {
                    NumberField("Axial depth ap", input.ap, { v -> state.update { it.copy(ap = v) } }, Quantity.LENGTH, units, Modifier.weight(1f), autoValue = apAuto)
                    NumberField("Radial width ae", input.ae, { v -> state.update { it.copy(ae = v) } }, Quantity.LENGTH, units, Modifier.weight(1f), autoValue = aeAuto)
                }
                SwitchRow("Climb milling", input.climb, { c -> state.update { it.copy(climb = c) } }, detail = if (input.climb) "Down-milling (recommended on CNC)" else "Conventional (up-milling)")
                SmallNote("Clear a field to return to the recommended value. Machine: ${settings.mill.name}")
            }

            SectionCard("Speeds & feeds") {
                QuantityRow("Cutting speed", result.vc, Quantity.CUTTING_SPEED, units, emphasize = true)
                QuantityRow("Chip load (programmed fz)", result.fz, Quantity.CHIP_LOAD, units, emphasize = true)
                QuantityRow("Max chip thickness hmax", result.hmax, Quantity.CHIP_LOAD, units)
                ResultRow("Chip-thinning factor", formatNumber(result.chipThinningFactor, 2) + "×")
                QuantityRow("Effective diameter", result.effectiveDiameter, Quantity.LENGTH, units)
                ResultRow("Engagement angle", formatNumber(result.engagementAngleDeg, 0) + "°")
                QuantityRow("Depths used: ap", result.ap, Quantity.LENGTH, units)
                QuantityRow("ae", result.ae, Quantity.LENGTH, units)
                Dropdown(
                    "Chip-load level", ChipLoadLevel.entries, settings.chipLoadLevel, settings::updateChipLoadLevel, Modifier.fillMaxWidth(),
                    optionLabel = { it.label }, optionDetail = { it.description },
                )
                PercentSlider("Speed override", input.speedPercent, { p -> state.update { it.copy(speedPercent = p) } })
                PercentSlider("Chip-load override", input.feedPercent, { p -> state.update { it.copy(feedPercent = p) } })
                if (input.speedPercent != 100.0) {
                    ResultRow("Est. tool life vs. recommended", formatNumber(result.relativeToolLife * 100, 0) + "%")
                }
            }

            SectionCard("Forces & power") {
                QuantityRow("Material removal rate", result.mrr, Quantity.MRR, units, emphasize = true)
                QuantityRow("Cutting power", result.powerKw, Quantity.POWER, units, emphasize = true, valueColor = utilisationColor(result.powerKw / result.availablePowerKw))
                QuantityRow("Available at this rpm", result.availablePowerKw, Quantity.POWER, units)
                QuantityRow("Spindle torque (mean)", result.torqueNm, Quantity.TORQUE, units)
                QuantityRow("Spindle torque (peak)", result.forces.peakTorque, Quantity.TORQUE, units)
                ThinDivider()
                QuantityRow("Peak lateral force |Fxy|", result.forces.peakFxy, Quantity.FORCE, units, emphasize = true)
                QuantityRow("Mean lateral force", result.forces.meanFxy, Quantity.FORCE, units)
                QuantityRow("Mean feed force Fx", result.forces.meanFx, Quantity.FORCE, units)
                QuantityRow("Mean normal force Fy", result.forces.meanFy, Quantity.FORCE, units)
                QuantityRow("Mean axial force Fz (pull-up)", result.forces.meanFz, Quantity.FORCE, units)
                QuantityRow("Mean specific cutting force kc", result.forces.meanKc, Quantity.STRESS, units)
                ResultRow("Teeth in cut (avg)", formatNumber(result.forces.teethInCut, 2))
                ThinDivider()
                if (input.toolType != MillToolType.INDEXABLE) {
                    QuantityRow("Tool deflection", result.deflection * 1000.0, Quantity.DEFLECTION, units, emphasize = true)
                    QuantityRow("Bending stress", result.bendingStress, Quantity.STRESS, units, valueColor = utilisationColor(result.bendingStress / input.toolMaterial.allowableBendingStress))
                    QuantityRow("Torsional stress", result.torsionStress, Quantity.STRESS, units)
                }
                Text("Force over one revolution", style = MaterialTheme.typography.labelLarge)
                ForceChart(result.forces.samples, units)
                SmallNote("Mechanistic model: Kienzle kc with oblique-cutting force ratios, integrated over teeth, helix and rotation. Rigid tool, no runout.")
            }

            SectionCard("Entry moves & finish") {
                ResultRow("Max ramp angle", formatNumber(result.rampAngleDeg, 1) + "°")
                QuantityRow("Ramp / helix feed", result.rampFeed, Quantity.FEED_RATE, units)
                QuantityRow("Plunge feed (centre-cutting)", result.plungeFeed, Quantity.FEED_RATE, units)
                ResultRow(
                    "Helical bore Ø",
                    Quantity.LENGTH.format(input.diameter * 1.2, units) + " – " + Quantity.LENGTH.formatWithUnit(input.diameter * 1.8, units),
                )
                result.cuspHeight?.let {
                    QuantityRow(if (input.toolType == MillToolType.BALL_END) "Scallop height" else "Theoretical cusp height", it * 1000.0, Quantity.DEFLECTION, units)
                }
            }

            AdviceList(result.advice)
            TextButton(onClick = state::reset, modifier = Modifier.fillMaxWidth()) { Text("Reset milling inputs") }
        }
    }
}
