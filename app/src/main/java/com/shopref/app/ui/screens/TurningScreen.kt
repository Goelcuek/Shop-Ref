package com.shopref.app.ui.screens

import com.shopref.app.ui.LocalLang
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shopref.app.state.AppSettings
import com.shopref.app.state.Session
import com.shopref.app.state.TurningState
import com.shopref.app.ui.components.AdviceList
import com.shopref.app.ui.components.toolLifeSummary
import com.shopref.app.ui.components.ToolLifeCard
import com.shopref.app.ui.components.SummaryBar
import com.shopref.app.ui.components.summaryOf
import com.shopref.app.ui.components.summaryWarnColor
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
import com.shopref.core.units.formatSignificant
import com.shopref.core.units.formatNumber

private val noseRadii = listOf(0.2, 0.4, 0.8, 1.2, 1.6, 2.4)

@Composable
fun TurningScreen(settings: AppSettings, session: Session, state: TurningState, modifier: Modifier = Modifier) {
    val L = LocalLang.current
    val units = settings.units
    val input = state.input.copy(material = session.material, machine = settings.lathe, units = units, lang = L)
    val result = remember(input) { TurningCalculator.calculate(input) }

    Column(modifier) {
        SummaryBar(
            listOf(
                summaryOf(L.tx("Spindle"), result.rpm, Quantity.SPINDLE_SPEED, units),
                summaryOf(L.tx("Feed"), result.feedRate, Quantity.FEED_RATE, units),
                summaryOf(L.tx("Power"), result.powerKw, Quantity.POWER, units, summaryWarnColor(result.powerKw / result.availablePowerKw)),
                summaryOf(L.tx("Speed"), result.vc, Quantity.CUTTING_SPEED, units),
                summaryOf(L.tx("Feed/rev"), result.feed, Quantity.FEED_PER_REV, units),
                toolLifeSummary(L.tx("Tool life"), result.toolLife),
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

            SectionCard(L.tx("Insert & operation")) {
                FieldRow {
                    Dropdown(L.tx("Insert grade"), InsertGrade.entries, input.grade, { g -> state.update { it.copy(grade = g) } }, Modifier.weight(1f), optionLabel = { L.tx(it.label) })
                    Dropdown(L.tx("Operation"), TurnOperation.entries, input.operation, { o -> state.update { it.copy(operation = o, ap = null, feed = null) } }, Modifier.weight(1f), optionLabel = { L.tx(it.label) })
                }
                FieldRow {
                    Dropdown(L.tx("Nose radius rε"), noseRadii, input.noseRadius, { r -> state.update { it.copy(noseRadius = r) } }, Modifier.weight(1f),
                        optionLabel = { Quantity.LENGTH.formatWithUnit(it, units, if (units == com.shopref.core.units.UnitSystem.METRIC) 1 else 4) },
                    )
                    NumberField(L.tx("Entering angle κr"), input.enteringAngle, { v -> v?.let { a -> state.update { it.copy(enteringAngle = a) } } }, Quantity.ANGLE, units, Modifier.weight(1f))
                }
                FieldRow {
                    NumberField(L.tx("Rake angle"), input.rakeDeg, { v -> v?.let { a -> state.update { it.copy(rakeDeg = a) } } }, Quantity.ANGLE, units, Modifier.weight(1f))
                    Dropdown(L.tx("Edge condition"), ToolCondition.entries, input.condition, { c -> state.update { it.copy(condition = c) } }, Modifier.weight(1f), optionLabel = { L.tx(it.label) })
                }
            }

            SectionCard(L.tx("Cut")) {
                FieldRow {
                    NumberField(L.tx("Work diameter"), input.diameter, { v -> v?.let { d -> state.update { it.copy(diameter = d) } } }, Quantity.LENGTH, units, Modifier.weight(1f), min = 0.5)
                    NumberField(L.tx("Length of cut"), input.lengthOfCut, { v -> v?.let { l -> state.update { it.copy(lengthOfCut = l) } } }, Quantity.LENGTH, units, Modifier.weight(1f))
                }
                FieldRow {
                    NumberField(L.tx("Depth of cut ap"), input.ap, { v -> state.update { it.copy(ap = v) } }, Quantity.LENGTH, units, Modifier.weight(1f), autoValue = TurningCalculator.defaultDepth(input))
                    NumberField(L.tx("Feed f"), input.feed, { v -> state.update { it.copy(feed = v) } }, Quantity.FEED_PER_REV, units, Modifier.weight(1f), autoValue = TurningCalculator.defaultFeed(input))
                }
                SmallNote(L.tx("Lathe: {0}. Depth of cut is radial (diameter changes by 2·ap).", L.tx(settings.lathe.name)))
            }

            SectionCard(L.tx("Speeds & feeds")) {
                QuantityRow(L.tx("Cutting speed"), result.vc, Quantity.CUTTING_SPEED, units, emphasize = true)
                QuantityRow(L.tx("Feed per revolution"), result.feed, Quantity.FEED_PER_REV, units)
                QuantityRow(L.tx("Depth of cut"), result.ap, Quantity.LENGTH, units)
                PercentSlider(L.tx("Speed override"), input.speedPercent, { p -> state.update { it.copy(speedPercent = p) } })
                ResultRow(L.tx("Cycle time"), formatNumber(result.timeMin, 2) + " " + L.tx("min"))
            }

            SectionCard(L.tx("Forces, power & finish")) {
                QuantityRow(L.tx("Cutting force Fc"), result.fc, Quantity.FORCE, units, emphasize = true)
                QuantityRow(L.tx("Feed force Ff"), result.ff, Quantity.FORCE, units)
                QuantityRow(L.tx("Passive (radial) force Fp"), result.fp, Quantity.FORCE, units)
                QuantityRow(L.tx("Specific cutting force kc"), result.kc, Quantity.STRESS, units)
                QuantityRow(L.tx("Cutting power"), result.powerKw, Quantity.POWER, units, emphasize = true, valueColor = utilisationColor(result.powerKw / result.availablePowerKw))
                QuantityRow(L.tx("Available at this rpm"), result.availablePowerKw, Quantity.POWER, units)
                QuantityRow(L.tx("Spindle torque"), result.torqueNm, Quantity.TORQUE, units)
                QuantityRow(L.tx("Material removal rate"), result.mrr, Quantity.MRR, units)
                ThinDivider()
                QuantityRow(L.tx("Theoretical Ra"), result.raMicron, Quantity.ROUGHNESS, units, emphasize = true)
                QuantityRow(L.tx("Theoretical Rt (peak-to-valley)"), result.rtMicron, Quantity.ROUGHNESS, units)
                SmallNote(L.tx("Fc = kc1.1·b·h^(1−mc), h = f·sin κr, b = ap/sin κr. Ra ≈ f²/(32·rε); real finish is usually 1.5–3× worse."))
            }

            ToolLifeCard(result.toolLife) {
                result.passesPerEdge?.let {
                    ResultRow(L.tx("Passes per edge ({0} each)", Quantity.LENGTH.formatWithUnit(input.lengthOfCut, units, lang = L)), formatSignificant(it))
                }
            }

            RaFeedCard(settings)
            AdviceList(result.advice)
            TextButton(onClick = state::reset, modifier = Modifier.fillMaxWidth()) { Text(L.tx("Reset turning inputs")) }
        }
    }
}

@Composable
private fun RaFeedCard(settings: AppSettings) {
    val L = LocalLang.current
    val units = settings.units
    var ra by remember { mutableDoubleStateOf(1.6) }
    var r by remember { mutableDoubleStateOf(0.8) }
    SectionCard(L.tx("Feed for a target finish")) {
        FieldRow {
            NumberField(L.tx("Target Ra"), ra, { v -> v?.let { ra = it } }, Quantity.ROUGHNESS, units, Modifier.weight(1f), min = 0.01)
            NumberField(L.tx("Nose radius"), r, { v -> v?.let { r = it } }, Quantity.LENGTH, units, Modifier.weight(1f), min = 0.01)
        }
        QuantityRow(L.tx("Max feed"), TurningCalculator.feedForRa(ra, r), Quantity.FEED_PER_REV, units, emphasize = true)
    }
}
