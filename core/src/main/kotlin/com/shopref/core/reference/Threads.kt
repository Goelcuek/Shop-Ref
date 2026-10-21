package com.shopref.core.reference

import kotlin.math.pow

enum class ThreadStandard(val label: String, val isInch: Boolean, val isPipe: Boolean = false) {
    METRIC_COARSE("ISO metric coarse (M)", false),
    METRIC_FINE("ISO metric fine (MF)", false),
    UNC("Unified coarse (UNC)", true),
    UNF("Unified fine (UNF)", true),
    UNEF("Unified extra-fine (UNEF)", true),
    NPT("NPT taper pipe (ASME B1.20.1)", true, isPipe = true),
    BSPP("BSP parallel pipe G (ISO 228-1)", true, isPipe = true),
}

/**
 * A screw thread. Lengths in mm.
 *
 * Basic profile relations (ISO 68-1 / ASME B1.1, 60° thread, H = 0.866025·P):
 *   pitch diameter  D2 = D − 0.649519·P
 *   basic minor     D1 = D − 1.082532·P   (internal thread, = minimum minor diameter)
 */
data class ThreadSpec(
    val designation: String,
    val standard: ThreadStandard,
    val majorDiameter: Double,
    val pitch: Double,
    /** Standard cut-tap drill as listed in common charts (≈75% thread), label and size. */
    val tapDrillLabel: String,
    val tapDrillMm: Double,
    /** Pipe threads only: tap drill when a taper reamer is used first. */
    val reamedDrillLabel: String? = null,
) {
    val tpi: Double get() = 25.4 / pitch
    val pitchDiameter: Double get() = majorDiameter - 0.649519 * pitch
    val minorDiameterMin: Double get() = majorDiameter - 1.082532 * pitch

    /** Maximum internal minor diameter (6H for metric, 2B for unified), or null for pipe threads. */
    val minorDiameterMax: Double?
        get() = when {
            standard.isPipe -> null
            !standard.isInch -> MetricTolerances.td1Grade6(pitch)?.let { minorDiameterMin + it }
            else -> minorDiameterMin + unified2BMinorTolerance(majorDiameter / 25.4, pitch / 25.4) * 25.4
        }

    override fun toString(): String = designation

    companion object {
        /**
         * ASME B1.1 class 2B minor-diameter tolerance [in]:
         *   D < 0.25 in:  0.05·P^(2/3) + 0.03·P/D − 0.002
         *   D ≥ 0.25 in:  0.25·P − 0.40·P²  (P < 0.25 in), 0.15·P otherwise.
         */
        fun unified2BMinorTolerance(dInch: Double, pInch: Double): Double = when {
            dInch < 0.25 -> 0.05 * pInch.pow(2.0 / 3.0) + 0.03 * pInch / dInch - 0.002
            pInch < 0.25 -> 0.25 * pInch - 0.40 * pInch * pInch
            else -> 0.15 * pInch
        }
    }
}

/** Thread-engagement (percent thread) relations used for tap-drill selection. */
object ThreadMath {
    /**
     * Percent of full thread for a cut tap (Machinery's Handbook):
     *   %thread = (D − d_drill) / (1.299038·P) × 100     [equivalently TPI·(D − d)/0.01299 in inch]
     */
    fun percentThreadCut(major: Double, pitch: Double, drill: Double): Double =
        (major - drill) / (1.299038 * pitch) * 100.0

    fun drillForPercentCut(major: Double, pitch: Double, percent: Double): Double =
        major - percent / 100.0 * 1.299038 * pitch

    /**
     * Thread forming (roll) taps displace material, so the hole is larger (OSG/Emuge rule):
     *   d_drill = D − 0.0068 · %thread · P      (P = 1/TPI for inch threads)
     */
    fun drillForPercentForm(major: Double, pitch: Double, percent: Double): Double =
        major - 0.0068 * percent * pitch

    fun percentThreadForm(major: Double, pitch: Double, drill: Double): Double =
        (major - drill) / (0.0068 * pitch)
}

object MetricTolerances {
    /** ISO 965-1 minor-diameter tolerance TD1 for tolerance grade 6 (6H), by pitch [mm]. */
    private val td1Grade6 = mapOf(
        0.2 to 0.056, 0.25 to 0.071, 0.3 to 0.085, 0.35 to 0.100, 0.4 to 0.112, 0.45 to 0.125, 0.5 to 0.140,
        0.6 to 0.160, 0.7 to 0.180, 0.75 to 0.190, 0.8 to 0.200, 1.0 to 0.236, 1.25 to 0.265, 1.5 to 0.300,
        1.75 to 0.335, 2.0 to 0.375, 2.5 to 0.450, 3.0 to 0.500, 3.5 to 0.560, 4.0 to 0.600, 4.5 to 0.670,
        5.0 to 0.710, 5.5 to 0.750, 6.0 to 0.800,
    )

    fun td1Grade6(pitch: Double): Double? = td1Grade6.entries.firstOrNull { kotlin.math.abs(it.key - pitch) < 1e-6 }?.value
}

object Threads {
    private fun metric(d: Double, p: Double, coarse: Boolean): ThreadSpec {
        val drill = roundTo(d - p, 0.05)
        return ThreadSpec("M${trim(d)}×${trim(p)}", if (coarse) ThreadStandard.METRIC_COARSE else ThreadStandard.METRIC_FINE,
            d, p, "${trim(drill)} mm", drill)
    }

    private fun unified(name: String, dInch: Double, tpi: Double, std: ThreadStandard, drillLabel: String, drillInch: Double) =
        ThreadSpec("$name-${trim(tpi)} ${std.name}", std, dInch * 25.4, 25.4 / tpi, drillLabel, drillInch * 25.4)

    private fun roundTo(v: Double, step: Double) = Math.round(v / step) * step

    internal fun trim(v: Double): String {
        val s = String.format(java.util.Locale.US, "%.3f", v)
        return s.trimEnd('0').trimEnd('.')
    }

    /** Number-size major diameters: #N = 0.060 + 0.013·N inch. */
    private fun numberSize(n: Int) = 0.060 + 0.013 * n

    val metricCoarse: List<ThreadSpec> = listOf(
        1.0 to 0.25, 1.2 to 0.25, 1.4 to 0.3, 1.6 to 0.35, 2.0 to 0.4, 2.5 to 0.45, 3.0 to 0.5, 3.5 to 0.6,
        4.0 to 0.7, 5.0 to 0.8, 6.0 to 1.0, 8.0 to 1.25, 10.0 to 1.5, 12.0 to 1.75, 14.0 to 2.0, 16.0 to 2.0,
        18.0 to 2.5, 20.0 to 2.5, 22.0 to 2.5, 24.0 to 3.0, 27.0 to 3.0, 30.0 to 3.5, 33.0 to 3.5, 36.0 to 4.0,
        39.0 to 4.0, 42.0 to 4.5, 45.0 to 4.5, 48.0 to 5.0, 52.0 to 5.0, 56.0 to 5.5, 60.0 to 5.5, 64.0 to 6.0,
    ).map { (d, p) -> metric(d, p, coarse = true) }

    val metricFine: List<ThreadSpec> = listOf(
        3.0 to 0.35, 4.0 to 0.5, 5.0 to 0.5, 6.0 to 0.75, 8.0 to 0.75, 8.0 to 1.0, 10.0 to 0.75, 10.0 to 1.0,
        10.0 to 1.25, 12.0 to 1.0, 12.0 to 1.25, 12.0 to 1.5, 14.0 to 1.0, 14.0 to 1.25, 14.0 to 1.5, 16.0 to 1.0,
        16.0 to 1.5, 18.0 to 1.0, 18.0 to 1.5, 18.0 to 2.0, 20.0 to 1.0, 20.0 to 1.5, 20.0 to 2.0, 22.0 to 1.5,
        22.0 to 2.0, 24.0 to 1.5, 24.0 to 2.0, 27.0 to 2.0, 30.0 to 1.5, 30.0 to 2.0, 33.0 to 2.0, 36.0 to 1.5,
        36.0 to 2.0, 36.0 to 3.0, 42.0 to 2.0, 42.0 to 3.0, 48.0 to 2.0, 48.0 to 3.0,
    ).map { (d, p) -> metric(d, p, coarse = false) }

    val unc: List<ThreadSpec> = listOf(
        unified("#1", numberSize(1), 64.0, ThreadStandard.UNC, "#53", 0.0595),
        unified("#2", numberSize(2), 56.0, ThreadStandard.UNC, "#50", 0.0700),
        unified("#3", numberSize(3), 48.0, ThreadStandard.UNC, "#47", 0.0785),
        unified("#4", numberSize(4), 40.0, ThreadStandard.UNC, "#43", 0.0890),
        unified("#5", numberSize(5), 40.0, ThreadStandard.UNC, "#38", 0.1015),
        unified("#6", numberSize(6), 32.0, ThreadStandard.UNC, "#36", 0.1065),
        unified("#8", numberSize(8), 32.0, ThreadStandard.UNC, "#29", 0.1360),
        unified("#10", numberSize(10), 24.0, ThreadStandard.UNC, "#25", 0.1495),
        unified("#12", numberSize(12), 24.0, ThreadStandard.UNC, "#16", 0.1770),
        unified("1/4", 0.2500, 20.0, ThreadStandard.UNC, "#7", 0.2010),
        unified("5/16", 0.3125, 18.0, ThreadStandard.UNC, "F", 0.2570),
        unified("3/8", 0.3750, 16.0, ThreadStandard.UNC, "5/16\"", 0.3125),
        unified("7/16", 0.4375, 14.0, ThreadStandard.UNC, "U", 0.3680),
        unified("1/2", 0.5000, 13.0, ThreadStandard.UNC, "27/64\"", 0.4219),
        unified("9/16", 0.5625, 12.0, ThreadStandard.UNC, "31/64\"", 0.4844),
        unified("5/8", 0.6250, 11.0, ThreadStandard.UNC, "17/32\"", 0.5312),
        unified("3/4", 0.7500, 10.0, ThreadStandard.UNC, "21/32\"", 0.6562),
        unified("7/8", 0.8750, 9.0, ThreadStandard.UNC, "49/64\"", 0.7656),
        unified("1", 1.0000, 8.0, ThreadStandard.UNC, "7/8\"", 0.8750),
        unified("1-1/8", 1.1250, 7.0, ThreadStandard.UNC, "63/64\"", 0.9844),
        unified("1-1/4", 1.2500, 7.0, ThreadStandard.UNC, "1-7/64\"", 1.1094),
        unified("1-3/8", 1.3750, 6.0, ThreadStandard.UNC, "1-7/32\"", 1.2188),
        unified("1-1/2", 1.5000, 6.0, ThreadStandard.UNC, "1-11/32\"", 1.3438),
        unified("1-3/4", 1.7500, 5.0, ThreadStandard.UNC, "1-9/16\"", 1.5625),
        unified("2", 2.0000, 4.5, ThreadStandard.UNC, "1-25/32\"", 1.7812),
    )

    val unf: List<ThreadSpec> = listOf(
        unified("#0", numberSize(0), 80.0, ThreadStandard.UNF, "3/64\"", 0.0469),
        unified("#1", numberSize(1), 72.0, ThreadStandard.UNF, "#53", 0.0595),
        unified("#2", numberSize(2), 64.0, ThreadStandard.UNF, "#50", 0.0700),
        unified("#3", numberSize(3), 56.0, ThreadStandard.UNF, "#45", 0.0820),
        unified("#4", numberSize(4), 48.0, ThreadStandard.UNF, "#42", 0.0935),
        unified("#5", numberSize(5), 44.0, ThreadStandard.UNF, "#37", 0.1040),
        unified("#6", numberSize(6), 40.0, ThreadStandard.UNF, "#33", 0.1130),
        unified("#8", numberSize(8), 36.0, ThreadStandard.UNF, "#29", 0.1360),
        unified("#10", numberSize(10), 32.0, ThreadStandard.UNF, "#21", 0.1590),
        unified("#12", numberSize(12), 28.0, ThreadStandard.UNF, "#14", 0.1820),
        unified("1/4", 0.2500, 28.0, ThreadStandard.UNF, "#3", 0.2130),
        unified("5/16", 0.3125, 24.0, ThreadStandard.UNF, "I", 0.2720),
        unified("3/8", 0.3750, 24.0, ThreadStandard.UNF, "Q", 0.3320),
        unified("7/16", 0.4375, 20.0, ThreadStandard.UNF, "25/64\"", 0.3906),
        unified("1/2", 0.5000, 20.0, ThreadStandard.UNF, "29/64\"", 0.4531),
        unified("9/16", 0.5625, 18.0, ThreadStandard.UNF, "33/64\"", 0.5156),
        unified("5/8", 0.6250, 18.0, ThreadStandard.UNF, "37/64\"", 0.5781),
        unified("3/4", 0.7500, 16.0, ThreadStandard.UNF, "11/16\"", 0.6875),
        unified("7/8", 0.8750, 14.0, ThreadStandard.UNF, "13/16\"", 0.8125),
        unified("1", 1.0000, 12.0, ThreadStandard.UNF, "59/64\"", 0.9219),
        unified("1-1/8", 1.1250, 12.0, ThreadStandard.UNF, "1-3/64\"", 1.0469),
        unified("1-1/4", 1.2500, 12.0, ThreadStandard.UNF, "1-11/64\"", 1.1719),
        unified("1-3/8", 1.3750, 12.0, ThreadStandard.UNF, "1-19/64\"", 1.2969),
        unified("1-1/2", 1.5000, 12.0, ThreadStandard.UNF, "1-27/64\"", 1.4219),
    )

    /** UNEF tap drills are the nearest standard drill to 75% thread. */
    val unef: List<ThreadSpec> = listOf(
        "#12" to (numberSize(12) to 32.0), "1/4" to (0.25 to 32.0), "5/16" to (0.3125 to 32.0),
        "3/8" to (0.375 to 32.0), "7/16" to (0.4375 to 28.0), "1/2" to (0.5 to 28.0), "9/16" to (0.5625 to 24.0),
        "5/8" to (0.625 to 24.0), "11/16" to (0.6875 to 24.0), "3/4" to (0.75 to 20.0), "13/16" to (0.8125 to 20.0),
        "7/8" to (0.875 to 20.0), "15/16" to (0.9375 to 20.0), "1" to (1.0 to 20.0),
    ).map { (name, spec) ->
        val (d, tpi) = spec
        val p = 1.0 / tpi
        val ideal = d - 0.75 * 1.299038 * p
        val drill = DrillSizes.nearest(ideal * 25.4, DrillSizes.inchAll)
        unified(name, d, tpi, ThreadStandard.UNEF, drill.label, drill.inch)
    }

    val npt: List<ThreadSpec> = listOf(
        npt("1/16", 0.3125, 27.0, "D", 0.246, "15/64\""),
        npt("1/8", 0.405, 27.0, "R", 0.339, "21/64\""),
        npt("1/4", 0.540, 18.0, "7/16\"", 0.4375, "27/64\""),
        npt("3/8", 0.675, 18.0, "37/64\"", 0.5781, "9/16\""),
        npt("1/2", 0.840, 14.0, "23/32\"", 0.7188, "11/16\""),
        npt("3/4", 1.050, 14.0, "59/64\"", 0.9219, "57/64\""),
        npt("1", 1.315, 11.5, "1-5/32\"", 1.1562, "1-1/8\""),
        npt("1-1/4", 1.660, 11.5, "1-1/2\"", 1.5000, "1-15/32\""),
        npt("1-1/2", 1.900, 11.5, "1-47/64\"", 1.7344, "1-23/32\""),
        npt("2", 2.375, 11.5, "2-7/32\"", 2.2188, "2-3/16\""),
    )

    private fun npt(name: String, odInch: Double, tpi: Double, drill: String, drillInch: Double, reamed: String) =
        ThreadSpec("$name-${trim(tpi)} NPT", ThreadStandard.NPT, odInch * 25.4, 25.4 / tpi, drill, drillInch * 25.4, reamed)

    val bspp: List<ThreadSpec> = listOf(
        bsp("1/16", 7.723, 28.0, 6.8), bsp("1/8", 9.728, 28.0, 8.8), bsp("1/4", 13.157, 19.0, 11.8),
        bsp("3/8", 16.662, 19.0, 15.25), bsp("1/2", 20.955, 14.0, 19.0), bsp("5/8", 22.911, 14.0, 21.0),
        bsp("3/4", 26.441, 14.0, 24.5), bsp("1", 33.249, 11.0, 30.75), bsp("1-1/4", 41.910, 11.0, 39.5),
        bsp("1-1/2", 47.803, 11.0, 45.25), bsp("2", 59.614, 11.0, 57.0),
    )

    private fun bsp(name: String, major: Double, tpi: Double, drill: Double) =
        ThreadSpec("G$name-${trim(tpi)}", ThreadStandard.BSPP, major, 25.4 / tpi, "${trim(drill)} mm", drill)

    fun byStandard(standard: ThreadStandard): List<ThreadSpec> = when (standard) {
        ThreadStandard.METRIC_COARSE -> metricCoarse
        ThreadStandard.METRIC_FINE -> metricFine
        ThreadStandard.UNC -> unc
        ThreadStandard.UNF -> unf
        ThreadStandard.UNEF -> unef
        ThreadStandard.NPT -> npt
        ThreadStandard.BSPP -> bspp
    }

    val all: List<ThreadSpec> by lazy { ThreadStandard.entries.flatMap { byStandard(it) } }

    /** Tappable straight threads (excludes taper pipe threads, whose torque model differs). */
    val tappable: List<ThreadSpec> by lazy { all.filter { it.standard != ThreadStandard.NPT } }

    fun find(designation: String): ThreadSpec? = all.firstOrNull { it.designation == designation }
}
