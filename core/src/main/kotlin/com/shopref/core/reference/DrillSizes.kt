package com.shopref.core.reference

import kotlin.math.abs

enum class DrillSeries(val label: String) {
    METRIC("Metric"),
    FRACTIONAL("Fractional inch"),
    NUMBER("Number (wire gauge)"),
    LETTER("Letter"),
}

data class DrillSize(val label: String, val mm: Double, val series: DrillSeries) {
    val inch: Double get() = mm / 25.4
}

/** Standard twist-drill sizes: number (#80–#1), letter (A–Z), fractional (1/64"–2") and metric. */
object DrillSizes {

    private val numberInches = doubleArrayOf(
        // #1 … #80
        0.2280, 0.2210, 0.2130, 0.2090, 0.2055, 0.2040, 0.2010, 0.1990, 0.1960, 0.1935,
        0.1910, 0.1890, 0.1850, 0.1820, 0.1800, 0.1770, 0.1730, 0.1695, 0.1660, 0.1610,
        0.1590, 0.1570, 0.1540, 0.1520, 0.1495, 0.1470, 0.1440, 0.1405, 0.1360, 0.1285,
        0.1200, 0.1160, 0.1130, 0.1110, 0.1100, 0.1065, 0.1040, 0.1015, 0.0995, 0.0980,
        0.0960, 0.0935, 0.0890, 0.0860, 0.0820, 0.0810, 0.0785, 0.0760, 0.0730, 0.0700,
        0.0670, 0.0635, 0.0595, 0.0550, 0.0520, 0.0465, 0.0430, 0.0420, 0.0410, 0.0400,
        0.0390, 0.0380, 0.0370, 0.0360, 0.0350, 0.0330, 0.0320, 0.0310, 0.0292, 0.0280,
        0.0260, 0.0250, 0.0240, 0.0225, 0.0210, 0.0200, 0.0180, 0.0160, 0.0145, 0.0135,
    )

    private val letterInches = doubleArrayOf(
        0.234, 0.238, 0.242, 0.246, 0.250, 0.257, 0.261, 0.266, 0.272, 0.277, 0.281, 0.290, 0.295,
        0.302, 0.316, 0.323, 0.332, 0.339, 0.348, 0.358, 0.368, 0.377, 0.386, 0.397, 0.404, 0.413,
    )

    val number: List<DrillSize> = numberInches.mapIndexed { i, inch ->
        DrillSize("#${i + 1}", inch * 25.4, DrillSeries.NUMBER)
    }

    val letter: List<DrillSize> = letterInches.mapIndexed { i, inch ->
        DrillSize(('A' + i).toString(), inch * 25.4, DrillSeries.LETTER)
    }

    val fractional: List<DrillSize> = (1..128).map { n64 ->
        DrillSize(fractionLabel(n64), n64 / 64.0 * 25.4, DrillSeries.FRACTIONAL)
    }

    val metric: List<DrillSize> = buildList {
        var tenths = 3 // 0.3 mm
        while (tenths <= 130) { add(metricSize(tenths / 10.0)); tenths += 1 }
        var quarters = 13 * 4 + 1
        while (quarters <= 20 * 4) { add(metricSize(quarters / 4.0)); quarters += 1 }
        var halves = 20 * 2 + 1
        while (halves <= 65 * 2) { add(metricSize(halves / 2.0)); halves += 1 }
    }

    val inchAll: List<DrillSize> = (number + letter + fractional).sortedBy { it.mm }
    val all: List<DrillSize> = (inchAll + metric).sortedBy { it.mm }

    private fun metricSize(mm: Double): DrillSize {
        val label = if (mm == Math.floor(mm)) "${mm.toInt()}.0 mm" else "${trimZeros(mm)} mm"
        return DrillSize(label, mm, DrillSeries.METRIC)
    }

    private fun trimZeros(v: Double): String {
        val s = String.format(java.util.Locale.US, "%.2f", v)
        return s.trimEnd('0').trimEnd('.')
    }

    fun fractionLabel(n64: Int): String {
        var num = n64
        var den = 64
        while (num % 2 == 0 && den > 1) { num /= 2; den /= 2 }
        val whole = num / den
        val rem = num % den
        return when {
            rem == 0 -> "$whole\""
            whole == 0 -> "$rem/$den\""
            else -> "$whole-$rem/$den\""
        }
    }

    /** Nearest drill in [sizes] to [mm]. */
    fun nearest(mm: Double, sizes: List<DrillSize> = all): DrillSize = sizes.minBy { abs(it.mm - mm) }

    /** Nearest drill not smaller than [mm] (e.g. to stay at or below a target thread %). */
    fun nearestAtLeast(mm: Double, sizes: List<DrillSize> = all): DrillSize =
        sizes.filter { it.mm >= mm - 1e-6 }.minByOrNull { it.mm } ?: sizes.maxBy { it.mm }

    fun find(label: String): DrillSize? = all.firstOrNull { it.label.equals(label, ignoreCase = true) }
}
