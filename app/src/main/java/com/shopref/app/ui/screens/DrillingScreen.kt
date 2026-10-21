package com.shopref.app.ui.screens

import com.shopref.app.ui.LocalLang
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
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shopref.app.state.AppSettings
import com.shopref.app.state.DrillingState
import com.shopref.app.state.Session
import com.shopref.app.ui.components.AdviceList
import com.shopref.app.ui.components.SummaryBar
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
import com.shopref.app.ui.components.ThinDivider
import com.shopref.app.ui.components.utilisationColor
import com.shopref.core.calc.DrillType
import com.shopref.core.calc.DrillingCalculator
import com.shopref.core.reference.DrillSizes
import com.shopref.core.tooling.Coating
import com.shopref.core.tooling.ToolCondition
import com.shopref.core.units.Quantity
import com.shopref.core.units.formatNumber

@Composable
fun DrillingScreen(settings: AppSettings, session: Session, state: DrillingState, modifier: Modifier = Modifier) {
    val L = LocalLang.current
    val units = settings.units
    val input = state.input.copy(material = session.material, machine = settings.mill, units = units, lang = L)
    val result = remember(input) { DrillingCalculator.calculate(input) }
    val isReamer = input.drillType == DrillType.REAMER

    Column(modifier) {
        SummaryBar(
            listOf(
                summaryOf(L.tx("Spindle"), result.rpm, Quantity.SPINDLE_SPEED, units),
                summaryOf(L.tx("Feed"), result.feed, Quantity.FEED_RATE, units),
                summaryOf(L.tx("Power"), result.powerKw, Quantity.POWER, units, summaryWarnColor(result.powerKw / result.availablePowerKw)),
                summaryOf(L.tx("Speed"), result.vc, Quantity.CUTTING_SPEED, units),
                summaryOf(L.tx("Feed/rev"), result.fn, Quantity.FEED_PER_REV, units),
                summaryOf(L.tx("Thrust"), result.thrust, Quantity.FORCE, units),
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

            SectionCard(L.tx("Drill")) {
                Dropdown(L.tx("Tool"), DrillType.entries, input.drillType, { t -> state.update { it.copy(drillType = t) } }, Modifier.fillMaxWidth(), optionLabel = { L.tx(it.label) })
                FieldRow {
                    NumberField(L.tx("Diameter"), input.diameter, { v -> v?.let { d -> state.update { it.copy(diameter = d) } } }, Quantity.LENGTH, units, Modifier.weight(1f), min = 0.1)
                    NumberField(L.tx("Hole depth"), input.depth, { v -> v?.let { d -> state.update { it.copy(depth = d) } } }, Quantity.LENGTH, units, Modifier.weight(1f), min = 0.0)
                }
                val nearest = DrillSizes.nearest(input.diameter)
                SmallNote(
                    L.tx(
                        "Nearest standard drill: {0} ({1} mm / {2} in) · depth {3}×D",
                        nearest.label, formatNumber(nearest.mm, 3), formatNumber(nearest.inch, 4), formatNumber(input.depth / input.diameter, 1),
                    ),
                )
                FieldRow {
                    if (input.drillType != DrillType.INDEXABLE) {
                        Dropdown(L.tx("Coating"), Coating.entries, input.coating, { c -> state.update { it.copy(coating = c) } }, Modifier.weight(1f), optionLabel = { L.tx(it.label) })
                    }
                    Dropdown(L.tx("Edge condition"), ToolCondition.entries, input.condition, { c -> state.update { it.copy(condition = c) } }, Modifier.weight(1f), optionLabel = { L.tx(it.label) })
                }
                if (isReamer) {
                    FieldRow {
                        NumberField(L.tx("Pre-drilled Ø"), input.preHoleDiameter.takeIf { it > 0 }, { v -> state.update { it.copy(preHoleDiameter = v ?: 0.0) } },
                            Quantity.LENGTH, units, Modifier.weight(1f), autoValue = input.diameter - DrillingCalculator.reamAllowance(input.diameter),
                        )
                        IntField(L.tx("Flutes"), input.flutes, { n -> state.update { it.copy(flutes = n) } }, Modifier.weight(1f), 2..16)
                    }
                } else {
                    NumberField(L.tx("Pre-drilled / core Ø (0 = solid)"), input.preHoleDiameter, { v -> state.update { it.copy(preHoleDiameter = v ?: 0.0) } },
                        Quantity.LENGTH, units, Modifier.fillMaxWidth(), min = 0.0,
                    )
                }
            }

            SectionCard(L.tx("Speeds & feeds")) {
                QuantityRow(L.tx("Cutting speed"), result.vc, Quantity.CUTTING_SPEED, units, emphasize = true)
                QuantityRow(L.tx("Feed per revolution"), result.fn, Quantity.FEED_PER_REV, units, emphasize = true)
                QuantityRow(L.tx("Chip thickness per lip"), result.chipThickness, Quantity.CHIP_LOAD, units)
                PercentSlider(L.tx("Speed override"), input.speedPercent, { p -> state.update { it.copy(speedPercent = p) } })
                PercentSlider(L.tx("Feed override"), input.feedPercent, { p -> state.update { it.copy(feedPercent = p) } })
                Text(L.tx("Pecking"), style = MaterialTheme.typography.labelLarge)
                Text(result.peckAdvice, style = MaterialTheme.typography.bodyMedium)
            }

            SectionCard(L.tx("Forces & power")) {
                QuantityRow(L.tx("Torque"), result.torqueNm, Quantity.TORQUE, units, emphasize = true)
                QuantityRow(L.tx("Thrust force"), result.thrust, Quantity.FORCE, units, emphasize = true)
                QuantityRow(L.tx("Cutting power"), result.powerKw, Quantity.POWER, units, valueColor = utilisationColor(result.powerKw / result.availablePowerKw))
                QuantityRow(L.tx("Available at this rpm"), result.availablePowerKw, Quantity.POWER, units)
                QuantityRow(L.tx("Specific cutting force kc"), result.kc, Quantity.STRESS, units)
                QuantityRow(L.tx("Material removal rate"), result.mrr, Quantity.MRR, units)
                ThinDivider()
                QuantityRow(L.tx("Point length"), result.pointLength, Quantity.LENGTH, units)
                ResultRow(L.tx("Cycle time (one hole)"), formatNumber(result.timeMin * 60.0, 1) + " s")
                SmallNote(L.tx("Mc = kc·fn·(D² − d0²)/8000; thrust = lip thrust × (1 + chisel-edge factor). kc from the Kienzle law at h = fn/2·sin κr."))
            }

            AdviceList(result.advice)
            SpotDrillCard(settings)
            TextButton(onClick = state::reset, modifier = Modifier.fillMaxWidth()) { Text(L.tx("Reset drilling inputs")) }
        }
    }
}

@Composable
private fun SpotDrillCard(settings: AppSettings) {
    val L = LocalLang.current
    val units = settings.units
    var chamfer by remember { mutableDoubleStateOf(6.0) }
    var angle by remember { mutableDoubleStateOf(90.0) }
    var tip by remember { mutableDoubleStateOf(0.0) }
    SectionCard(L.tx("Spot / countersink depth")) {
        FieldRow {
            NumberField(L.tx("Chamfer Ø"), chamfer, { v -> v?.let { chamfer = it } }, Quantity.LENGTH, units, Modifier.weight(1f))
            NumberField(L.tx("Included angle"), angle, { v -> v?.let { angle = it.coerceIn(20.0, 179.0) } }, Quantity.ANGLE, units, Modifier.weight(1f))
        }
        NumberField(L.tx("Tip / pilot Ø (0 = sharp)"), tip, { v -> tip = v ?: 0.0 }, Quantity.LENGTH, units, Modifier.fillMaxWidth())
        QuantityRow(L.tx("Plunge depth (Z)"), DrillingCalculator.spotDepth(chamfer, angle, tip), Quantity.LENGTH, units, emphasize = true)
        SmallNote(L.tx("Spot ≈0.1–0.2 mm larger than the drill so the drill lips, not the chisel edge, start the hole. Use spot angle ≥ drill point angle."))
    }
}
