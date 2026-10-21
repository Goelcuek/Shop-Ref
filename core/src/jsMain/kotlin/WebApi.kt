@file:OptIn(ExperimentalJsExport::class)
@file:Suppress("unused", "UnsafeCastFromDynamic")

// JavaScript API for the web app (web/). Every function takes and returns plain JS objects with
// values in base (metric) units; the page converts for display. Exposed on the global `shopref`.

import com.shopref.core.calc.Advice
import com.shopref.core.calc.DrillType
import com.shopref.core.calc.DrillingCalculator
import com.shopref.core.calc.DrillingInput
import com.shopref.core.calc.InsertGrade
import com.shopref.core.calc.MillStrategy
import com.shopref.core.calc.MillToolType
import com.shopref.core.calc.MillingCalculator
import com.shopref.core.calc.MillingInput
import com.shopref.core.calc.TapChamfer
import com.shopref.core.calc.TapType
import com.shopref.core.calc.TappingCalculator
import com.shopref.core.calc.TappingInput
import com.shopref.core.calc.TurnOperation
import com.shopref.core.calc.ToolLife
import com.shopref.core.calc.TurningCalculator
import com.shopref.core.calc.TurningInput
import com.shopref.core.i18n.Dictionary
import com.shopref.core.i18n.Lang
import com.shopref.core.material.IsoGroup
import com.shopref.core.material.Material
import com.shopref.core.material.MaterialLibrary
import com.shopref.core.reference.Citations
import com.shopref.core.reference.DrillSeries
import com.shopref.core.reference.DrillSize
import com.shopref.core.reference.DrillSizes
import com.shopref.core.reference.Fits
import com.shopref.core.reference.Hardness
import com.shopref.core.reference.Holes
import com.shopref.core.reference.SurfaceFinish
import com.shopref.core.reference.ThreadMath
import com.shopref.core.reference.ThreadSpec
import com.shopref.core.reference.ThreadStandard
import com.shopref.core.reference.Threads
import com.shopref.core.reference.Tolerance
import com.shopref.core.tooling.ChipLoadLevel
import com.shopref.core.tooling.Coating
import com.shopref.core.tooling.Machine
import com.shopref.core.tooling.Rigidity
import com.shopref.core.tooling.ToolCondition
import com.shopref.core.tooling.ToolMaterial
import com.shopref.core.units.Quantity
import com.shopref.core.units.UnitSystem

// ----------------------------------------------------------------------------------- helpers

private fun obj(): dynamic = js("({})")

private inline fun obj(init: (dynamic) -> Unit): dynamic {
    val o = obj()
    init(o)
    return o
}

private fun num(o: dynamic, key: String, default: Double): Double {
    val v = o[key]
    return if (v == null || jsTypeOf(v) != "number") default else (v as Double)
}

private fun numOrNull(o: dynamic, key: String): Double? {
    val v = o[key]
    return if (v == null || jsTypeOf(v) != "number" || (v as Double).isNaN()) null else v
}

private fun int(o: dynamic, key: String, default: Int): Int = num(o, key, default.toDouble()).toInt()

private fun bool(o: dynamic, key: String, default: Boolean): Boolean {
    val v = o[key]
    return if (v == null || jsTypeOf(v) != "boolean") default else (v as Boolean)
}

private fun str(o: dynamic, key: String): String? {
    val v = o[key]
    return if (v == null || jsTypeOf(v) != "string") null else (v as String)
}

private inline fun <reified T : Enum<T>> enumOf(o: dynamic, key: String, default: T): T =
    str(o, key)?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

private fun lang(o: dynamic) = Lang.fromCode(str(o, "lang"))
private fun units(o: dynamic) = enumOf(o, "units", UnitSystem.METRIC)
private fun material(o: dynamic): Material = str(o, "material")?.let { MaterialLibrary.byId(it) } ?: MaterialLibrary.default

private fun machine(o: dynamic, fallback: Machine): Machine {
    val m = o["machine"] ?: return fallback
    return Machine(
        name = str(m, "name") ?: fallback.name,
        maxRpm = num(m, "maxRpm", fallback.maxRpm),
        minRpm = num(m, "minRpm", fallback.minRpm),
        maxFeed = num(m, "maxFeed", fallback.maxFeed),
        powerKw = num(m, "powerKw", fallback.powerKw),
        maxTorqueNm = num(m, "maxTorqueNm", fallback.maxTorqueNm),
        efficiency = num(m, "efficiency", fallback.efficiency),
        rigidity = enumOf(m, "rigidity", fallback.rigidity),
    )
}

private fun machineJs(m: Machine): dynamic = obj {
    it.name = m.name; it.maxRpm = m.maxRpm; it.minRpm = m.minRpm; it.maxFeed = m.maxFeed; it.powerKw = m.powerKw
    it.maxTorqueNm = m.maxTorqueNm; it.efficiency = m.efficiency; it.rigidity = m.rigidity.name
}

private fun lifeJs(e: ToolLife.Estimate): dynamic = obj {
    it.minutes = e.minutes; it.referenceMinutes = e.referenceMinutes; it.factor = e.factor; it.outsideModel = e.outsideModel; it.n = e.n
}

private fun adviceJs(list: List<Advice>): dynamic =
    list.sortedByDescending { it.level.ordinal }.map { a -> obj { it.level = a.level.name; it.text = a.text } }.toTypedArray()

private fun option(id: String, label: String, description: String? = null): dynamic = obj {
    it.id = id
    it.label = label
    if (description != null) it.description = description
}

private fun drillJs(d: DrillSize): dynamic = obj { it.label = d.label; it.mm = d.mm; it.inch = d.inch; it.series = d.series.name }

private fun tolJs(t: Tolerance): dynamic = obj { it.upper = t.upper; it.lower = t.lower }

// ----------------------------------------------------------------------------------- catalogue

@JsExport
fun dictionary(): dynamic {
    val o = obj()
    for ((k, v) in Dictionary.tr) o[k] = v
    return o
}

@JsExport
fun quantities(): dynamic {
    val o = obj()
    for (q in Quantity.entries) {
        o[q.name] = obj {
            it.metric = q.metricLabel; it.imperial = q.imperialLabel; it.toImperial = q.toImperial
            it.metricDecimals = q.metricDecimals; it.imperialDecimals = q.imperialDecimals
        }
    }
    return o
}

@JsExport
fun materials(): dynamic = MaterialLibrary.all.map { m ->
    obj {
        it.id = m.id; it.name = m.name; it.group = m.group.name; it.family = m.family.label; it.examples = m.examples
        it.hardness = m.hardness; it.kc11 = m.kc11; it.mc = m.mc; it.vcCarbide = m.vcCarbide; it.vcHss = m.vcHss
        it.fzPerD = m.fzPerD; it.drillFnPerD = m.drillFnPerD; it.tapVc = m.tapVc; it.turnVc = m.turnVc
        it.frictionAngle = m.frictionAngle; it.machinability = m.machinability; it.notes = m.notes
    }
}.toTypedArray()

@JsExport
fun isoGroups(): dynamic = IsoGroup.entries.map { g ->
    obj { it.id = g.name; it.label = g.label; it.description = g.description; it.color = "#" + (g.color and 0xFFFFFF).toString(16).padStart(6, '0') }
}.toTypedArray()

@JsExport
fun options(): dynamic = obj { o ->
    o.toolMaterials = ToolMaterial.entries.map { option(it.name, it.label) }.toTypedArray()
    o.coatings = Coating.entries.map { option(it.name, it.label, it.description) }.toTypedArray()
    o.toolConditions = ToolCondition.entries.map { option(it.name, it.label) }.toTypedArray()
    o.chipLoadLevels = ChipLoadLevel.entries.map { option(it.name, it.label, it.description) }.toTypedArray()
    o.rigidity = Rigidity.entries.map { option(it.name, it.label) }.toTypedArray()
    o.millToolTypes = MillToolType.entries.map { option(it.name, it.label) }.toTypedArray()
    o.millStrategies = MillStrategy.entries.map { option(it.name, it.label, it.description) }.toTypedArray()
    o.drillTypes = DrillType.entries.map { option(it.name, it.label) }.toTypedArray()
    o.tapTypes = TapType.entries.map { option(it.name, it.label) }.toTypedArray()
    o.tapSubstrates = listOf(ToolMaterial.HSS, ToolMaterial.HSS_CO, ToolMaterial.PM_HSS, ToolMaterial.CARBIDE).map { option(it.name, it.label) }.toTypedArray()
    o.tapChamfers = TapChamfer.entries.map { option(it.name, it.label) }.toTypedArray()
    o.insertGrades = InsertGrade.entries.map { option(it.name, it.label) }.toTypedArray()
    o.turnOperations = TurnOperation.entries.map { option(it.name, it.label) }.toTypedArray()
    o.threadStandards = ThreadStandard.entries.map { s ->
        val op = option(s.name, s.label)
        op.isInch = s.isInch
        op.isPipe = s.isPipe
        op
    }.toTypedArray()
    o.drillSeries = DrillSeries.entries.map { option(it.name, it.label) }.toTypedArray()
    o.allowableStress = obj { a -> ToolMaterial.entries.forEach { a[it.name] = it.allowableBendingStress } }
}

@JsExport
fun machinePresets(): dynamic = Machine.PRESETS.map { machineJs(it) }.toTypedArray()

// ----------------------------------------------------------------------------------- milling

private fun millingInput(o: dynamic): MillingInput = MillingInput(
    material = material(o),
    toolMaterial = enumOf(o, "toolMaterial", ToolMaterial.CARBIDE),
    coating = enumOf(o, "coating", Coating.ALTIN),
    toolType = enumOf(o, "toolType", MillToolType.SQUARE_END),
    diameter = num(o, "diameter", 12.0),
    flutes = int(o, "flutes", 4),
    helixDeg = num(o, "helixDeg", 35.0),
    rakeDeg = numOrNull(o, "rakeDeg"),
    cornerRadius = num(o, "cornerRadius", 1.0),
    chamferAngle = num(o, "chamferAngle", 90.0),
    chamferTipDiameter = num(o, "chamferTipDiameter", 0.0),
    leadAngleDeg = num(o, "leadAngleDeg", 45.0),
    fluteLength = numOrNull(o, "fluteLength"),
    stickout = numOrNull(o, "stickout"),
    shankDiameter = numOrNull(o, "shankDiameter"),
    strategy = enumOf(o, "strategy", MillStrategy.SIDE_MILLING),
    ap = numOrNull(o, "ap"),
    ae = numOrNull(o, "ae"),
    climb = bool(o, "climb", true),
    condition = enumOf(o, "condition", ToolCondition.NORMAL),
    speedPercent = num(o, "speedPercent", 100.0),
    feedPercent = num(o, "feedPercent", 100.0),
    machine = machine(o, Machine.PRESETS.first()),
    chipLoadLevel = enumOf(o, "chipLoadLevel", ChipLoadLevel.STANDARD),
    units = units(o),
    lang = lang(o),
)

@JsExport
fun milling(o: dynamic): dynamic {
    val input = millingInput(o)
    val r = MillingCalculator.calculate(input)
    val (apAuto, aeAuto) = MillingCalculator.defaultEngagement(input)
    return obj {
        it.rpm = r.rpm; it.vc = r.vc; it.vcRecommended = r.vcRecommended; it.feed = r.feed; it.fz = r.fz
        it.hmax = r.hmax; it.hmaxTarget = r.hmaxTarget; it.chipThinningFactor = r.chipThinningFactor
        it.effectiveDiameter = r.effectiveDiameter; it.engagementAngleDeg = r.engagementAngleDeg
        it.ap = r.ap; it.ae = r.ae; it.apAuto = apAuto; it.aeAuto = aeAuto
        it.fluteLengthAuto = MillingCalculator.fluteLengthOf(input.copy(fluteLength = null))
        it.stickoutAuto = MillingCalculator.stickoutOf(input.copy(stickout = null))
        it.rakeAuto = if (input.toolType == MillToolType.INDEXABLE) 10.0 else MillingCalculator.defaultRake(input.material)
        it.mrr = r.mrr; it.powerKw = r.powerKw; it.availablePowerKw = r.availablePowerKw; it.torqueNm = r.torqueNm
        it.peakTorque = r.forces.peakTorque; it.peakFxy = r.forces.peakFxy; it.meanFxy = r.forces.meanFxy
        it.meanFx = r.forces.meanFx; it.meanFy = r.forces.meanFy; it.meanFz = r.forces.meanFz; it.meanKc = r.forces.meanKc
        it.teethInCut = r.forces.teethInCut; it.deflection = r.deflection; it.deflectionLimit = r.deflectionLimit; it.bendingStress = r.bendingStress
        it.torsionStress = r.torsionStress; it.allowableStress = input.toolMaterial.allowableBendingStress
        it.toolLife = lifeJs(r.toolLife); it.toolLifePath = r.toolLifePath; it.toolLifeVolume = r.toolLifeVolume; it.cuspHeight = r.cuspHeight; it.rampAngleDeg = r.rampAngleDeg
        it.rampFeed = r.rampFeed; it.plungeFeed = r.plungeFeed; it.rakeDeg = r.rakeDeg; it.leadAngleDeg = r.leadAngleDeg
        it.samples = r.forces.samples.map { s ->
            obj { p -> p.a = s.angleDeg; p.fx = s.fx; p.fy = s.fy; p.fz = s.fz; p.fxy = s.fxy }
        }.toTypedArray()
        it.advice = adviceJs(r.advice)
    }
}

// ----------------------------------------------------------------------------------- drilling

@JsExport
fun drilling(o: dynamic): dynamic {
    val type = enumOf(o, "drillType", DrillType.CARBIDE)
    val d = num(o, "diameter", 10.0)
    val input = DrillingInput(
        material = material(o), drillType = type, coating = enumOf(o, "coating", Coating.TIALN), diameter = d,
        depth = num(o, "depth", 30.0), preHoleDiameter = num(o, "preHoleDiameter", 0.0), flutes = int(o, "flutes", 6),
        condition = enumOf(o, "condition", ToolCondition.NORMAL), speedPercent = num(o, "speedPercent", 100.0),
        feedPercent = num(o, "feedPercent", 100.0), machine = machine(o, Machine.PRESETS.first()), units = units(o), lang = lang(o),
    )
    val r = DrillingCalculator.calculate(input)
    return obj {
        it.rpm = r.rpm; it.vc = r.vc; it.vcRecommended = r.vcRecommended; it.fn = r.fn; it.feed = r.feed
        it.chipThickness = r.chipThickness; it.kc = r.kc; it.torqueNm = r.torqueNm; it.thrust = r.thrust
        it.powerKw = r.powerKw; it.availablePowerKw = r.availablePowerKw; it.mrr = r.mrr; it.timeMin = r.timeMin
        it.toolLife = lifeJs(r.toolLife); it.holesPerTool = r.holesPerTool; it.toolLifeLength = r.toolLifeLength
        it.pointLength = r.pointLength; it.peckAdvice = r.peckAdvice; it.reamAllowance = r.reamAllowance
        it.preHoleAuto = d - DrillingCalculator.reamAllowance(d)
        it.nearest = drillJs(DrillSizes.nearest(d))
        it.advice = adviceJs(r.advice)
    }
}

@JsExport
fun spotDepth(chamferDia: Double, angle: Double, tipDia: Double): Double = DrillingCalculator.spotDepth(chamferDia, angle, tipDia)

// ----------------------------------------------------------------------------------- tapping

private fun threadJs(t: ThreadSpec): dynamic = obj {
    it.designation = t.designation; it.standard = t.standard.name; it.major = t.majorDiameter; it.pitch = t.pitch
    it.tpi = t.tpi; it.isInch = t.standard.isInch; it.isPipe = t.standard.isPipe; it.pitchDiameter = t.pitchDiameter
    it.minorMin = t.minorDiameterMin; it.minorMax = t.minorDiameterMax; it.tapDrillLabel = t.tapDrillLabel
    it.tapDrillMm = t.tapDrillMm; it.reamedDrillLabel = t.reamedDrillLabel
    if (!t.standard.isPipe) {
        val set = if (t.standard.isInch) DrillSizes.inchAll else DrillSizes.metric
        it.formDrill = drillJs(DrillSizes.nearest(ThreadMath.drillForPercentForm(t.majorDiameter, t.pitch, 65.0), set))
    }
}

@JsExport
fun threads(standard: String): dynamic {
    val std = ThreadStandard.entries.firstOrNull { it.name == standard } ?: ThreadStandard.METRIC_COARSE
    return Threads.byStandard(std).map { threadJs(it) }.toTypedArray()
}

private fun findThread(o: dynamic): ThreadSpec =
    str(o, "thread")?.let { Threads.find(it) } ?: Threads.metricCoarse.first { it.designation == "M8×1.25" }

@JsExport
fun tapping(o: dynamic): dynamic {
    val thread = findThread(o)
    val input = TappingInput(
        material = material(o), thread = thread, tapType = enumOf(o, "tapType", TapType.SPIRAL_FLUTE),
        chamfer = enumOf(o, "chamfer", TapChamfer.SEMI_BOTTOMING), tapMaterial = enumOf(o, "tapMaterial", ToolMaterial.HSS_CO),
        coating = enumOf(o, "coating", Coating.TICN), flutes = int(o, "flutes", 3), blindHole = bool(o, "blindHole", true),
        threadDepth = num(o, "threadDepth", 15.0), targetPercent = numOrNull(o, "targetPercent"),
        drillDiameter = numOrNull(o, "drillDiameter"), condition = enumOf(o, "condition", ToolCondition.NORMAL),
        speedPercent = num(o, "speedPercent", 100.0), machine = machine(o, Machine.PRESETS.first()), units = units(o), lang = lang(o),
    )
    val r = TappingCalculator.calculate(input)
    return obj {
        it.thread = threadJs(thread); it.rpm = r.rpm; it.vc = r.vc; it.vcRecommended = r.vcRecommended; it.feed = r.feed
        it.targetPercent = r.targetPercent; it.recommendedPercent = TappingCalculator.recommendedPercent(input.material, input.tapType.isForming)
        it.idealDrill = r.idealDrill; it.recommendedDrill = drillJs(r.recommendedDrill); it.drill = r.drill
        it.percentThread = r.percentThread; it.radialDepth = r.radialDepth; it.chipThickness = r.chipThickness
        it.torqueNm = r.torqueNm; it.breakingTorqueNm = r.breakingTorqueNm; it.safetyFactor = r.safetyFactor
        it.powerKw = r.powerKw; it.minDrillDepth = r.minDrillDepth; it.advice = adviceJs(r.advice)
        it.toolLife = lifeJs(r.toolLife); it.holesPerTap = r.holesPerTap
    }
}

@JsExport
fun threadMill(o: dynamic): dynamic {
    val thread = findThread(o)
    val r = TappingCalculator.threadMill(material(o), thread, num(o, "toolDiameter", thread.majorDiameter * 0.7), int(o, "flutes", 3), true, machine(o, Machine.PRESETS.first()))
    return obj { it.rpm = r.rpm; it.peripheralFeed = r.peripheralFeed; it.centreFeed = r.centreFeed; it.radialPasses = r.radialPasses }
}

// ----------------------------------------------------------------------------------- turning

@JsExport
fun turning(o: dynamic): dynamic {
    val input = TurningInput(
        material = material(o), grade = enumOf(o, "grade", InsertGrade.COATED_CARBIDE), operation = enumOf(o, "operation", TurnOperation.MEDIUM),
        diameter = num(o, "diameter", 50.0), ap = numOrNull(o, "ap"), feed = numOrNull(o, "feed"), noseRadius = num(o, "noseRadius", 0.8),
        enteringAngle = num(o, "enteringAngle", 95.0), rakeDeg = num(o, "rakeDeg", 6.0), lengthOfCut = num(o, "lengthOfCut", 100.0),
        condition = enumOf(o, "condition", ToolCondition.NORMAL), speedPercent = num(o, "speedPercent", 100.0),
        machine = machine(o, Machine.PRESETS.last()), units = units(o), lang = lang(o),
    )
    val r = TurningCalculator.calculate(input)
    return obj {
        it.ap = r.ap; it.feed = r.feed; it.apAuto = TurningCalculator.defaultDepth(input); it.feedAuto = TurningCalculator.defaultFeed(input)
        it.vc = r.vc; it.vcRecommended = r.vcRecommended; it.rpm = r.rpm; it.feedRate = r.feedRate; it.mrr = r.mrr
        it.chipThickness = r.chipThickness; it.kc = r.kc; it.fc = r.fc; it.ff = r.ff; it.fp = r.fp; it.powerKw = r.powerKw
        it.availablePowerKw = r.availablePowerKw; it.torqueNm = r.torqueNm; it.rt = r.rtMicron; it.ra = r.raMicron
        it.timeMin = r.timeMin; it.advice = adviceJs(r.advice)
        it.toolLife = lifeJs(r.toolLife); it.passesPerEdge = r.passesPerEdge
    }
}

@JsExport
fun feedForRa(raMicron: Double, noseRadius: Double): Double = TurningCalculator.feedForRa(raMicron, noseRadius)

// ----------------------------------------------------------------------------------- reference

@JsExport
fun drills(): dynamic = DrillSizes.all.map { drillJs(it) }.toTypedArray()

@JsExport
fun nearestDrill(mm: Double, set: String): dynamic = drillJs(
    DrillSizes.nearest(
        mm,
        when (set) {
            "inch" -> DrillSizes.inchAll
            "metric" -> DrillSizes.metric
            else -> DrillSizes.all
        },
    ),
)

@JsExport
fun threadPercent(major: Double, pitch: Double, drill: Double): dynamic = obj {
    it.cut = ThreadMath.percentThreadCut(major, pitch, drill); it.form = ThreadMath.percentThreadForm(major, pitch, drill)
}

@JsExport
fun drillForPercent(major: Double, pitch: Double, percent: Double, forming: Boolean): Double =
    if (forming) ThreadMath.drillForPercentForm(major, pitch, percent) else ThreadMath.drillForPercentCut(major, pitch, percent)

@JsExport
fun fitsInfo(): dynamic = obj { o ->
    o.holeLetters = Fits.holeLetters.toTypedArray()
    o.shaftLetters = Fits.shaftLetters.toTypedArray()
    o.grades = Fits.grades.toList().toTypedArray()
    o.commonFits = Fits.commonFits.map { f ->
        obj { it.label = f.label; it.hole = f.hole; it.holeGrade = f.holeGrade; it.shaft = f.shaft; it.shaftGrade = f.shaftGrade; it.name = f.name; it.use = f.use }
    }.toTypedArray()
}

@JsExport
fun fit(hole: String, holeGrade: Int, shaft: String, shaftGrade: Int, size: Double): dynamic {
    val f = Fits.fit(hole, holeGrade, shaft, shaftGrade, size) ?: return null
    return obj {
        it.hole = tolJs(f.hole); it.shaft = tolJs(f.shaft); it.maxClearance = f.maxClearance; it.minClearance = f.minClearance
        it.type = f.type.name
    }
}

@JsExport
fun itGrades(size: Double): dynamic = Fits.grades.map { g -> obj { it.grade = g; it.value = Fits.it(g, size) } }.toTypedArray()

@JsExport
fun holes(): dynamic = obj { o ->
    fun ch(list: List<com.shopref.core.reference.ClearanceHole>) = list.map { h ->
        obj { it.size = h.size; it.nominal = h.nominal; it.close = h.close; it.normal = h.normal; it.loose = h.loose; it.closeLabel = h.closeLabel; it.normalLabel = h.normalLabel }
    }.toTypedArray()
    fun cb(list: List<com.shopref.core.reference.Counterbore>) = list.map { c ->
        obj { it.size = c.size; it.head = c.headDiameter; it.headHeight = c.headHeight; it.cbore = c.cboreDiameter; it.depth = c.cboreDepth; it.clearance = c.clearance }
    }.toTypedArray()
    fun cs(list: List<com.shopref.core.reference.Countersink>) = list.map { c ->
        obj {
            it.size = c.size; it.head = c.headDiameter; it.angle = c.angle; it.clearance = c.clearance
            it.spotDepth = Holes.countersinkDepth(c.headDiameter + if (c.angle == 90.0) 0.2 else 0.127, c.angle)
        }
    }.toTypedArray()
    o.metricClearance = ch(Holes.metricClearance); o.inchClearance = ch(Holes.inchClearance)
    o.metricCounterbores = cb(Holes.metricCounterbores); o.inchCounterbores = cb(Holes.inchCounterbores)
    o.metricCountersinks = cs(Holes.metricCountersinks); o.inchCountersinks = cs(Holes.inchCountersinks)
}

@JsExport
fun countersinkDepth(dia: Double, angle: Double, hole: Double): Double = Holes.countersinkDepth(dia, angle, hole)

@JsExport
fun hardness(): dynamic = Hardness.rows.map { r -> obj { it.hrc = r.hrc; it.hv = r.hv; it.hb = r.hb; it.tensile = r.tensileMpa } }.toTypedArray()

@JsExport
fun hvFromHrc(hrc: Double): dynamic = Hardness.hvFromHrc(hrc)

@JsExport
fun surface(): dynamic = obj { o ->
    o.grades = SurfaceFinish.grades.map { g -> obj { it.grade = g.grade; it.ra = g.raMicron; it.uin = g.raMicroInch } }.toTypedArray()
    o.processes = SurfaceFinish.processes.map { p -> obj { it.process = p.process; it.min = p.raMin; it.max = p.raMax } }.toTypedArray()
}

@JsExport
fun formulas(): dynamic = Citations.formulas.map { f ->
    obj { it.title = f.title; it.formula = f.formula; it.notes = f.notes; it.refs = f.refs.toTypedArray() }
}.toTypedArray()

@JsExport
fun citations(): dynamic = Citations.all.map { c -> obj { it.key = c.key; it.text = c.text; it.usedFor = c.usedFor } }.toTypedArray()
