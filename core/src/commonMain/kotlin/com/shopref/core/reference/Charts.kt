package com.shopref.core.reference

import kotlin.math.roundToInt

/** Approximate hardness conversion for steel (ASTM E140 / ISO 18265). */
data class HardnessRow(val hrc: Int, val hv: Int, val hb: Int?, val tensileMpa: Int?)

object Hardness {
    // HRC → (HV, HB with 10 mm carbide ball / 3000 kgf).
    private val data = listOf(
        68 to (940 to null), 67 to (900 to null), 66 to (865 to null), 65 to (832 to 739), 64 to (800 to 722),
        63 to (772 to 705), 62 to (746 to 688), 61 to (720 to 670), 60 to (697 to 654), 59 to (674 to 634),
        58 to (653 to 615), 57 to (633 to 595), 56 to (613 to 577), 55 to (595 to 560), 54 to (577 to 543),
        53 to (560 to 525), 52 to (544 to 512), 51 to (528 to 496), 50 to (513 to 481), 49 to (498 to 469),
        48 to (484 to 455), 47 to (471 to 443), 46 to (458 to 432), 45 to (446 to 421), 44 to (434 to 409),
        43 to (423 to 400), 42 to (412 to 390), 41 to (402 to 381), 40 to (392 to 371), 39 to (382 to 362),
        38 to (372 to 353), 37 to (363 to 344), 36 to (354 to 336), 35 to (345 to 327), 34 to (336 to 319),
        33 to (327 to 311), 32 to (318 to 301), 31 to (310 to 294), 30 to (302 to 286), 29 to (294 to 279),
        28 to (286 to 271), 27 to (279 to 264), 26 to (272 to 258), 25 to (266 to 253), 24 to (260 to 247),
        23 to (254 to 243), 22 to (248 to 237), 21 to (243 to 231), 20 to (238 to 226),
    )

    /** Tensile strength estimate Rm ≈ 3.45·HB [MPa] (valid roughly to 500 HB). */
    fun tensileFromHb(hb: Int): Int? = if (hb > 500) null else (3.45 * hb).roundToInt()

    val rows: List<HardnessRow> = data.map { (hrc, v) -> HardnessRow(hrc, v.first, v.second, v.second?.let { tensileFromHb(it) }) }

    /** Linear interpolation HRC → HV. */
    fun hvFromHrc(hrc: Double): Double? = interpolate(hrc, rows.map { it.hrc.toDouble() to it.hv.toDouble() })

    fun hrcFromHv(hv: Double): Double? = interpolate(hv, rows.map { it.hv.toDouble() to it.hrc.toDouble() })

    fun hrcFromHb(hb: Double): Double? = interpolate(hb, rows.filter { it.hb != null }.map { it.hb!!.toDouble() to it.hrc.toDouble() })

    private fun interpolate(x: Double, pts: List<Pair<Double, Double>>): Double? {
        val sorted = pts.sortedBy { it.first }
        if (x < sorted.first().first || x > sorted.last().first) return null
        for (i in 0 until sorted.size - 1) {
            val (x0, y0) = sorted[i]
            val (x1, y1) = sorted[i + 1]
            if (x in x0..x1) return if (x1 == x0) y0 else y0 + (y1 - y0) * (x - x0) / (x1 - x0)
        }
        return null
    }
}

/** ISO 1302 roughness grades and typical process capability. */
data class RoughnessGrade(val grade: String, val raMicron: Double, val raMicroInch: Int)

data class ProcessFinish(val process: String, val raMin: Double, val raMax: Double)

object SurfaceFinish {
    val grades = listOf(
        RoughnessGrade("N12", 50.0, 2000), RoughnessGrade("N11", 25.0, 1000), RoughnessGrade("N10", 12.5, 500),
        RoughnessGrade("N9", 6.3, 250), RoughnessGrade("N8", 3.2, 125), RoughnessGrade("N7", 1.6, 63),
        RoughnessGrade("N6", 0.8, 32), RoughnessGrade("N5", 0.4, 16), RoughnessGrade("N4", 0.2, 8),
        RoughnessGrade("N3", 0.1, 4), RoughnessGrade("N2", 0.05, 2), RoughnessGrade("N1", 0.025, 1),
    )

    val processes = listOf(
        ProcessFinish("Saw / flame cut", 6.3, 50.0),
        ProcessFinish("Drilling", 1.6, 6.3),
        ProcessFinish("Rough milling", 3.2, 12.5),
        ProcessFinish("Finish milling", 0.8, 3.2),
        ProcessFinish("Turning", 0.4, 6.3),
        ProcessFinish("Boring", 0.4, 6.3),
        ProcessFinish("Reaming", 0.4, 3.2),
        ProcessFinish("EDM", 0.8, 6.3),
        ProcessFinish("Grinding", 0.1, 1.6),
        ProcessFinish("Honing", 0.1, 0.8),
        ProcessFinish("Lapping / polishing", 0.025, 0.4),
    )

    /** Rq (RMS) ≈ 1.11·Ra for a sinusoidal profile. */
    fun rmsFromRa(ra: Double) = 1.11 * ra

    /** Rz ≈ 4–7 × Ra for machined surfaces; mid value 5.5 (rough guide only, not a conversion). */
    fun rzEstimateFromRa(ra: Double) = 5.5 * ra
}
