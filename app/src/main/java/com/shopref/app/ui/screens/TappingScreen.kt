package com.shopref.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shopref.app.state.AppSettings
import com.shopref.app.state.Session
import com.shopref.app.state.TappingState
import com.shopref.app.ui.components.AdviceList
import com.shopref.app.ui.components.SummaryBar
import com.shopref.app.ui.components.SummaryItem
import com.shopref.app.ui.components.summaryOf
import com.shopref.app.ui.components.summaryWarnColor
import com.shopref.app.ui.components.Dropdown
import com.shopref.app.ui.components.FieldRow
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
import com.shopref.core.calc.TapChamfer
import com.shopref.core.calc.TapType
import com.shopref.core.calc.TappingCalculator
import com.shopref.core.material.Material
import com.shopref.core.reference.ThreadSpec
import com.shopref.core.reference.ThreadStandard
import com.shopref.core.reference.Threads
import com.shopref.core.tooling.Coating
import com.shopref.core.tooling.Machine
import com.shopref.core.tooling.ToolCondition
import com.shopref.core.tooling.ToolMaterial
import com.shopref.core.units.Quantity
import com.shopref.core.units.UnitSystem
import com.shopref.core.units.formatNumber

private val tapSubstrates = listOf(ToolMaterial.HSS, ToolMaterial.HSS_CO, ToolMaterial.PM_HSS, ToolMaterial.CARBIDE)

fun threadPitchLabel(t: ThreadSpec): String =
    if (t.standard.isInch) "${formatNumber(t.tpi, if (t.tpi % 1.0 == 0.0) 0 else 1)} TPI" else "P ${formatNumber(t.pitch, 2)} mm"

@Composable
fun TappingScreen(settings: AppSettings, session: Session, state: TappingState, modifier: Modifier = Modifier) {
    val units = settings.units
    val input = state.input.copy(material = session.material, machine = settings.mill, units = units)
    val result = remember(input) { TappingCalculator.calculate(input) }
    val th = input.thread

    Column(modifier) {
        SummaryBar(
            listOf(
                summaryOf("Spindle", result.rpm, Quantity.SPINDLE_SPEED, units),
                summaryOf("Feed", result.feed, Quantity.FEED_RATE, units),
                summaryOf("Torque", result.torqueNm, Quantity.TORQUE, units, summaryWarnColor(1.5 / result.safetyFactor)),
                summaryOf("Speed", result.vc, Quantity.CUTTING_SPEED, units),
                summaryOf("Drill", result.drill, Quantity.LENGTH, units),
                SummaryItem("Thread", formatNumber(result.percentThread, 0), "%"),
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

            SectionCard("Thread") {
                val standards = ThreadStandard.entries.filter { it != ThreadStandard.NPT }
                FieldRow {
                    Dropdown(
                        "Standard", standards, th.standard,
                        { s -> state.update { it.copy(thread = Threads.byStandard(s).first(), drillDiameter = null) } },
                        Modifier.weight(1f), optionLabel = { it.label },
                    )
                    Dropdown(
                        "Size", Threads.byStandard(th.standard), th, { t -> state.update { it.copy(thread = t, drillDiameter = null) } },
                        Modifier.weight(1f), optionLabel = { it.designation }, optionDetail = { threadPitchLabel(it) },
                    )
                }
                QuantityRow("Major diameter", th.majorDiameter, Quantity.LENGTH, units)
                ResultRow("Pitch", threadPitchLabel(th))
                QuantityRow("Pitch diameter (basic)", th.pitchDiameter, Quantity.LENGTH, units)
                ResultRow(
                    if (th.standard.isInch) "Minor Ø (2B min–max)" else "Minor Ø (6H min–max)",
                    Quantity.LENGTH.format(th.minorDiameterMin, units) + " – " +
                        (th.minorDiameterMax?.let { Quantity.LENGTH.formatWithUnit(it, units) } ?: "—"),
                )
            }

            SectionCard("Tap") {
                Dropdown("Tap type", TapType.entries, input.tapType, { t -> state.update { it.copy(tapType = t, targetPercent = null, drillDiameter = null) } }, Modifier.fillMaxWidth(), optionLabel = { it.label })
                FieldRow {
                    Dropdown("Chamfer", TapChamfer.entries, input.chamfer, { c -> state.update { it.copy(chamfer = c) } }, Modifier.weight(1f), optionLabel = { it.label })
                    IntField(if (input.tapType.isForming) "Lobes" else "Flutes", input.flutes, { n -> state.update { it.copy(flutes = n) } }, Modifier.weight(1f), 1..8)
                }
                FieldRow {
                    Dropdown("Substrate", tapSubstrates, input.tapMaterial, { t -> state.update { it.copy(tapMaterial = t) } }, Modifier.weight(1f), optionLabel = { it.label })
                    Dropdown("Coating", Coating.entries, input.coating, { c -> state.update { it.copy(coating = c) } }, Modifier.weight(1f), optionLabel = { it.label })
                }
                SwitchRow("Blind hole", input.blindHole, { b -> state.update { it.copy(blindHole = b) } })
                FieldRow {
                    NumberField("Thread depth", input.threadDepth, { v -> v?.let { d -> state.update { it.copy(threadDepth = d) } } }, Quantity.LENGTH, units, Modifier.weight(1f))
                    NumberField("Target thread", input.targetPercent, { v -> state.update { it.copy(targetPercent = v, drillDiameter = null) } }, Quantity.PERCENT, units, Modifier.weight(1f), autoValue = TappingCalculator.recommendedPercent(input.material, input.tapType.isForming))
                }
                NumberField("Drill used (blank = recommended)", input.drillDiameter, { v -> state.update { it.copy(drillDiameter = v) } }, Quantity.LENGTH, units, Modifier.fillMaxWidth(), autoValue = result.recommendedDrill.mm, decimals = 4)
                Dropdown("Edge condition", ToolCondition.entries, input.condition, { c -> state.update { it.copy(condition = c) } }, Modifier.fillMaxWidth(), optionLabel = { it.label })
            }

            SectionCard("Tap drill") {
                ResultRow(
                    "Recommended drill",
                    "${result.recommendedDrill.label} (${Quantity.LENGTH.formatWithUnit(result.recommendedDrill.mm, units)})",
                    emphasize = true,
                )
                QuantityRow("Theoretical Ø for ${formatNumber(result.targetPercent, 0)}%", result.idealDrill, Quantity.LENGTH, units)
                ResultRow("Thread engagement with drill used", formatNumber(result.percentThread, 0) + "%", emphasize = true)
                if (!th.standard.isPipe) {
                    ResultRow("Chart drill (≈75%, cut tap)", "${th.tapDrillLabel} (${Quantity.LENGTH.formatWithUnit(th.tapDrillMm, units)})")
                } else {
                    ResultRow("Chart drill", "${th.tapDrillLabel} (${Quantity.LENGTH.formatWithUnit(th.tapDrillMm, units)})")
                }
                QuantityRow("Min. drill depth (blind)", result.minDrillDepth, Quantity.LENGTH, units)
            }

            SectionCard("Speeds, torque & safety") {
                QuantityRow("Cutting speed", result.vc, Quantity.CUTTING_SPEED, units)
                PercentSlider("Speed override", input.speedPercent, { p -> state.update { it.copy(speedPercent = p) } })
                ThinDivider()
                QuantityRow("Tapping torque (est.)", result.torqueNm, Quantity.TORQUE, units, emphasize = true, valueColor = utilisationColor(1.0 / result.safetyFactor * 1.5))
                QuantityRow("Tap breaking torque (est.)", result.breakingTorqueNm, Quantity.TORQUE, units)
                ResultRow("Safety factor", formatNumber(result.safetyFactor, 1))
                QuantityRow("Power", result.powerKw, Quantity.POWER, units)
                QuantityRow("Chip thickness per tooth", result.chipThickness, Quantity.CHIP_LOAD, units)
                SmallNote("Torque M = kc·A·dm/2 (energy balance over the thread groove, Kienzle kc) × process factors for land friction and chip packing (Cao & Sutherland 2002). Forming ≈ 2× cutting torque.")
            }

            AdviceList(result.advice)
            ThreadMillCard(input.material, th, settings.mill, units)
            TextButton(onClick = state::reset, modifier = Modifier.fillMaxWidth()) { Text("Reset tapping inputs") }
        }
    }
}

@Composable
private fun ThreadMillCard(material: Material, thread: ThreadSpec, machine: Machine, units: UnitSystem) {
    var toolDia by remember(thread) { mutableDoubleStateOf((thread.majorDiameter * 0.7).coerceAtLeast(1.0)) }
    var flutes by remember { mutableIntStateOf(3) }
    val r = remember(material, thread, toolDia, flutes, machine) { TappingCalculator.threadMill(material, thread, toolDia, flutes, true, machine) }
    SectionCard("Thread milling (internal)") {
        FieldRow {
            NumberField("Cutter Ø", toolDia, { v -> v?.let { toolDia = it } }, Quantity.LENGTH, units, Modifier.weight(1f), min = 0.1)
            IntField("Flutes", flutes, { flutes = it }, Modifier.weight(1f), 1..8)
        }
        QuantityRow("Spindle", r.rpm, Quantity.SPINDLE_SPEED, units, emphasize = true)
        QuantityRow("Feed at cutting edge", r.peripheralFeed, Quantity.FEED_RATE, units)
        QuantityRow("Programmed (tool-centre) feed", r.centreFeed, Quantity.FEED_RATE, units, emphasize = true)
        ResultRow("Radial passes", r.radialPasses.toString())
        if (toolDia >= thread.minorDiameterMin) {
            SmallNote("Cutter must be smaller than the minor diameter (${Quantity.LENGTH.formatWithUnit(thread.minorDiameterMin, units)}).")
        } else {
            SmallNote("Centre feed = edge feed × (D − d)/D for internal threads. Use a 3D helix: one pitch of Z per 360°.")
        }
    }
}
