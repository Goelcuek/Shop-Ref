package com.shopref.core.reference

/** Tolerance zone limits in µm (upper/lower deviation from nominal). */
data class Tolerance(val upper: Double, val lower: Double) {
    constructor(upper: Int, lower: Int) : this(upper.toDouble(), lower.toDouble())
    val width: Double get() = upper - lower
}

enum class FitType { CLEARANCE, TRANSITION, INTERFERENCE }

data class FitResult(
    val hole: Tolerance,
    val shaft: Tolerance,
    /** Maximum clearance (negative = interference) [µm]. */
    val maxClearance: Double,
    /** Minimum clearance (negative = maximum interference) [µm]. */
    val minClearance: Double,
) {
    val type: FitType
        get() = when {
            minClearance >= 0 -> FitType.CLEARANCE
            maxClearance <= 0 -> FitType.INTERFERENCE
            else -> FitType.TRANSITION
        }
}

data class CommonFit(val hole: String, val holeGrade: Int, val shaft: String, val shaftGrade: Int, val name: String, val use: String) {
    val label: String get() = "$hole$holeGrade/$shaft$shaftGrade"
}

/**
 * ISO 286-1/-2 limits and fits for nominal sizes up to 500 mm: standard tolerance grades IT4–IT13
 * and the fundamental deviations of the commonly used hole (D–S) and shaft (d–s) positions.
 * Hole deviations K, M, N (≤ IT8) and P, R, S (≤ IT7) use the ISO "Δ rule" ES = −ei + Δ with
 * Δ = ITn − IT(n−1).
 */
object Fits {
    /** Upper bounds of the main nominal size ranges [mm] (each range is "above previous, up to and including"). */
    private val ranges = doubleArrayOf(3.0, 6.0, 10.0, 18.0, 30.0, 50.0, 80.0, 120.0, 180.0, 250.0, 315.0, 400.0, 500.0)

    private val itTable: Map<Int, IntArray> = mapOf(
        4 to intArrayOf(3, 4, 4, 5, 6, 7, 8, 10, 12, 14, 16, 18, 20),
        5 to intArrayOf(4, 5, 6, 8, 9, 11, 13, 15, 18, 20, 23, 25, 27),
        6 to intArrayOf(6, 8, 9, 11, 13, 16, 19, 22, 25, 29, 32, 36, 40),
        7 to intArrayOf(10, 12, 15, 18, 21, 25, 30, 35, 40, 46, 52, 57, 63),
        8 to intArrayOf(14, 18, 22, 27, 33, 39, 46, 54, 63, 72, 81, 89, 97),
        9 to intArrayOf(25, 30, 36, 43, 52, 62, 74, 87, 100, 115, 130, 140, 155),
        10 to intArrayOf(40, 48, 58, 70, 84, 100, 120, 140, 160, 185, 210, 230, 250),
        11 to intArrayOf(60, 75, 90, 110, 130, 160, 190, 220, 250, 290, 320, 360, 400),
        12 to intArrayOf(100, 120, 150, 180, 210, 250, 300, 350, 400, 460, 520, 570, 630),
        13 to intArrayOf(140, 180, 220, 270, 330, 390, 460, 540, 630, 720, 810, 890, 970),
    )

    // Shaft upper deviations es (a–h) and lower deviations ei (k–s) in µm, by main range.
    private val esD = intArrayOf(-20, -30, -40, -50, -65, -80, -100, -120, -145, -170, -190, -210, -230)
    private val esE = intArrayOf(-14, -20, -25, -32, -40, -50, -60, -72, -85, -100, -110, -125, -135)
    private val esF = intArrayOf(-6, -10, -13, -16, -20, -25, -30, -36, -43, -50, -56, -62, -68)
    private val esG = intArrayOf(-2, -4, -5, -6, -7, -9, -10, -12, -14, -15, -17, -18, -20)
    /** k for grades IT4–IT7 (0 for other grades). */
    private val eiK = intArrayOf(0, 1, 1, 1, 2, 2, 2, 3, 3, 4, 4, 4, 5)
    private val eiM = intArrayOf(2, 4, 6, 7, 8, 9, 11, 13, 15, 17, 20, 21, 23)
    private val eiN = intArrayOf(4, 8, 10, 12, 15, 17, 20, 23, 27, 31, 34, 37, 40)
    private val eiP = intArrayOf(6, 12, 15, 18, 22, 26, 32, 37, 43, 50, 56, 62, 68)

    // r and s change within the main ranges above 50 mm: (upper bound, ei).
    private val eiR = listOf(
        3.0 to 10, 6.0 to 15, 10.0 to 19, 18.0 to 23, 30.0 to 28, 50.0 to 34, 65.0 to 41, 80.0 to 43,
        100.0 to 51, 120.0 to 54, 140.0 to 63, 160.0 to 65, 180.0 to 68, 200.0 to 77, 225.0 to 80, 250.0 to 84,
        280.0 to 94, 315.0 to 98, 355.0 to 108, 400.0 to 114, 450.0 to 126, 500.0 to 132,
    )
    private val eiS = listOf(
        3.0 to 14, 6.0 to 19, 10.0 to 23, 18.0 to 28, 30.0 to 35, 50.0 to 43, 65.0 to 53, 80.0 to 59,
        100.0 to 71, 120.0 to 79, 140.0 to 92, 160.0 to 100, 180.0 to 108, 200.0 to 122, 225.0 to 130, 250.0 to 140,
        280.0 to 158, 315.0 to 170, 355.0 to 190, 400.0 to 208, 450.0 to 232, 500.0 to 252,
    )

    val holeLetters = listOf("D", "E", "F", "G", "H", "JS", "K", "M", "N", "P", "R", "S")
    val shaftLetters = listOf("d", "e", "f", "g", "h", "js", "k", "m", "n", "p", "r", "s")
    val grades = 4..13

    val commonFits = listOf(
        CommonFit("H", 9, "d", 9, "Free running", "Loose bearings, large temperature variation"),
        CommonFit("H", 8, "f", 7, "Close running", "Running fits on accurate machines, spindles"),
        CommonFit("H", 7, "g", 6, "Sliding", "Precise location, parts slide and turn freely when lubricated"),
        CommonFit("H", 7, "h", 6, "Locational clearance", "Snug fit for stationary parts, assembled by hand"),
        CommonFit("H", 7, "k", 6, "Locational transition", "Accurate location, light mallet assembly"),
        CommonFit("H", 7, "n", 6, "Locational transition (tight)", "More accurate location, press assembly"),
        CommonFit("H", 7, "p", 6, "Locational interference", "Rigid alignment, press fit (dowel pins in steel)"),
        CommonFit("H", 7, "r", 6, "Light press", "Light drive fit, bushings"),
        CommonFit("H", 7, "s", 6, "Medium drive", "Permanent assembly of steel parts, shrink fit for cast iron"),
    )

    private fun rangeIndex(size: Double): Int? {
        if (size <= 0.0 || size > 500.0) return null
        return ranges.indexOfFirst { size <= it }
    }

    private fun lookupSub(table: List<Pair<Double, Int>>, size: Double): Int = table.first { size <= it.first }.second

    /** Standard tolerance [µm] for grade [grade] at nominal [size] mm. */
    fun it(grade: Int, size: Double): Int? {
        val idx = rangeIndex(size) ?: return null
        return itTable[grade]?.get(idx)
    }

    private fun js(grade: Int, it: Int): Tolerance {
        // ISO 286-1: for js7–js11 an odd IT value is rounded down to the nearest even value.
        val v = if (grade >= 7 && it % 2 == 1) it - 1 else it
        return Tolerance(v / 2.0, -v / 2.0)
    }

    fun shaft(letter: String, grade: Int, size: Double): Tolerance? {
        val idx = rangeIndex(size) ?: return null
        val itv = it(grade, size) ?: return null
        return when (letter) {
            "d" -> Tolerance(esD[idx], esD[idx] - itv)
            "e" -> Tolerance(esE[idx], esE[idx] - itv)
            "f" -> Tolerance(esF[idx], esF[idx] - itv)
            "g" -> Tolerance(esG[idx], esG[idx] - itv)
            "h" -> Tolerance(0, -itv)
            "js" -> js(grade, itv)
            "k" -> { val ei = if (grade in 4..7) eiK[idx] else 0; Tolerance(ei + itv, ei) }
            "m" -> Tolerance(eiM[idx] + itv, eiM[idx])
            "n" -> Tolerance(eiN[idx] + itv, eiN[idx])
            "p" -> Tolerance(eiP[idx] + itv, eiP[idx])
            "r" -> lookupSub(eiR, size).let { Tolerance(it + itv, it) }
            "s" -> lookupSub(eiS, size).let { Tolerance(it + itv, it) }
            else -> null
        }
    }

    fun hole(letter: String, grade: Int, size: Double): Tolerance? {
        val idx = rangeIndex(size) ?: return null
        val itv = it(grade, size) ?: return null
        val prev = it(grade - 1, size)
        val delta = if (idx == 0) 0 else itv - (prev ?: itv)
        if (letter in setOf("K", "M", "N", "P", "R", "S") && prev == null) return null
        return when (letter) {
            "D" -> Tolerance(-esD[idx] + itv, -esD[idx])
            "E" -> Tolerance(-esE[idx] + itv, -esE[idx])
            "F" -> Tolerance(-esF[idx] + itv, -esF[idx])
            "G" -> Tolerance(-esG[idx] + itv, -esG[idx])
            "H" -> Tolerance(itv, 0)
            "JS" -> js(grade, itv)
            "K" -> if (grade > 8) null else (if (idx == 0) 0 else -eiK[idx] + delta).let { Tolerance(it, it - itv) }
            "M" -> if (grade > 8) null else (if (idx == 0) -2 else -eiM[idx] + delta).let { Tolerance(it, it - itv) }
            "N" -> if (grade > 8) null else (if (idx == 0) -4 else -eiN[idx] + delta).let { Tolerance(it, it - itv) }
            "P" -> if (grade > 7) null else (if (idx == 0) -6 else -eiP[idx] + delta).let { Tolerance(it, it - itv) }
            "R" -> if (grade > 7) null else (-lookupSub(eiR, size) + delta).let { Tolerance(it, it - itv) }
            "S" -> if (grade > 7) null else (-lookupSub(eiS, size) + delta).let { Tolerance(it, it - itv) }
            else -> null
        }
    }

    fun fit(holeLetter: String, holeGrade: Int, shaftLetter: String, shaftGrade: Int, size: Double): FitResult? {
        val h = hole(holeLetter, holeGrade, size) ?: return null
        val s = shaft(shaftLetter, shaftGrade, size) ?: return null
        return FitResult(h, s, maxClearance = h.upper - s.lower, minClearance = h.lower - s.upper)
    }
}
