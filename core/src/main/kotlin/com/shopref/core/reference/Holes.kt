package com.shopref.core.reference

/** Clearance holes for bolts and screws. Sizes in mm. */
data class ClearanceHole(val size: String, val nominal: Double, val close: Double, val normal: Double, val loose: Double?, val closeLabel: String? = null, val normalLabel: String? = null)

/** Socket head cap screw head and recommended counterbore. Sizes in mm. */
data class Counterbore(val size: String, val headDiameter: Double, val headHeight: Double, val cboreDiameter: Double, val cboreDepth: Double, val clearance: Double)

/** Flat (countersunk) head screw: theoretical sharp head diameter and countersink angle. */
data class Countersink(val size: String, val headDiameter: Double, val angle: Double, val clearance: Double)

object Holes {
    private fun inch(v: Double) = v * 25.4

    /** ISO 273 clearance holes: fine / medium / coarse series. */
    val metricClearance: List<ClearanceHole> = listOf(
        ClearanceHole("M1.6", 1.6, 1.7, 1.8, 2.0), ClearanceHole("M2", 2.0, 2.2, 2.4, 2.6),
        ClearanceHole("M2.5", 2.5, 2.7, 2.9, 3.1), ClearanceHole("M3", 3.0, 3.2, 3.4, 3.6),
        ClearanceHole("M3.5", 3.5, 3.7, 3.9, 4.2), ClearanceHole("M4", 4.0, 4.3, 4.5, 4.8),
        ClearanceHole("M5", 5.0, 5.3, 5.5, 5.8), ClearanceHole("M6", 6.0, 6.4, 6.6, 7.0),
        ClearanceHole("M8", 8.0, 8.4, 9.0, 10.0), ClearanceHole("M10", 10.0, 10.5, 11.0, 12.0),
        ClearanceHole("M12", 12.0, 13.0, 13.5, 14.5), ClearanceHole("M14", 14.0, 15.0, 15.5, 16.5),
        ClearanceHole("M16", 16.0, 17.0, 17.5, 18.5), ClearanceHole("M18", 18.0, 19.0, 20.0, 21.0),
        ClearanceHole("M20", 20.0, 21.0, 22.0, 24.0), ClearanceHole("M22", 22.0, 23.0, 24.0, 26.0),
        ClearanceHole("M24", 24.0, 25.0, 26.0, 28.0), ClearanceHole("M27", 27.0, 28.0, 30.0, 32.0),
        ClearanceHole("M30", 30.0, 31.0, 33.0, 35.0), ClearanceHole("M33", 33.0, 34.0, 36.0, 38.0),
        ClearanceHole("M36", 36.0, 37.0, 39.0, 42.0), ClearanceHole("M42", 42.0, 43.0, 45.0, 48.0),
        ClearanceHole("M48", 48.0, 50.0, 52.0, 56.0),
    )

    /** Inch screw clearance holes, close and free fit (common shop chart, Machinery's Handbook). */
    val inchClearance: List<ClearanceHole> = listOf(
        ic("#0", 0.060, "#52", 0.0635, "#48", 0.0760), ic("#1", 0.073, "#48", 0.0760, "#43", 0.0890),
        ic("#2", 0.086, "#43", 0.0890, "#38", 0.1015), ic("#3", 0.099, "#37", 0.1040, "#32", 0.1160),
        ic("#4", 0.112, "#32", 0.1160, "#30", 0.1285), ic("#5", 0.125, "#30", 0.1285, "#27", 0.1440),
        ic("#6", 0.138, "#27", 0.1440, "#18", 0.1695), ic("#8", 0.164, "#18", 0.1695, "#9", 0.1960),
        ic("#10", 0.190, "#9", 0.1960, "#2", 0.2210), ic("#12", 0.216, "#2", 0.2210, "D", 0.2460),
        ic("1/4", 0.250, "F", 0.2570, "H", 0.2660), ic("5/16", 0.3125, "P", 0.3230, "Q", 0.3320),
        ic("3/8", 0.375, "W", 0.3860, "X", 0.3970), ic("7/16", 0.4375, "29/64\"", 0.4531, "15/32\"", 0.4688),
        ic("1/2", 0.500, "33/64\"", 0.5156, "17/32\"", 0.5312), ic("5/8", 0.625, "41/64\"", 0.6406, "21/32\"", 0.6562),
        ic("3/4", 0.750, "49/64\"", 0.7656, "25/32\"", 0.7812), ic("7/8", 0.875, "57/64\"", 0.8906, "29/32\"", 0.9062),
        ic("1", 1.000, "1-1/64\"", 1.0156, "1-1/32\"", 1.0312),
    )

    private fun ic(size: String, nom: Double, closeL: String, close: Double, freeL: String, free: Double) =
        ClearanceHole(size, inch(nom), inch(close), inch(free), null, closeL, freeL)

    /** ISO 4762 socket head cap screws with DIN 974-1 counterbores (depth = head + 0.4–0.6 mm). */
    val metricCounterbores: List<Counterbore> = listOf(
        Counterbore("M2", 3.8, 2.0, 4.4, 2.4, 2.4), Counterbore("M2.5", 4.5, 2.5, 5.5, 2.9, 2.9),
        Counterbore("M3", 5.5, 3.0, 6.5, 3.4, 3.4), Counterbore("M4", 7.0, 4.0, 8.0, 4.4, 4.5),
        Counterbore("M5", 8.5, 5.0, 10.0, 5.4, 5.5), Counterbore("M6", 10.0, 6.0, 11.0, 6.4, 6.6),
        Counterbore("M8", 13.0, 8.0, 15.0, 8.6, 9.0), Counterbore("M10", 16.0, 10.0, 18.0, 10.6, 11.0),
        Counterbore("M12", 18.0, 12.0, 20.0, 12.6, 13.5), Counterbore("M14", 21.0, 14.0, 24.0, 14.6, 15.5),
        Counterbore("M16", 24.0, 16.0, 26.0, 16.6, 17.5), Counterbore("M20", 30.0, 20.0, 33.0, 20.6, 22.0),
        Counterbore("M24", 36.0, 24.0, 40.0, 24.6, 26.0), Counterbore("M30", 45.0, 30.0, 48.0, 30.6, 33.0),
        Counterbore("M36", 54.0, 36.0, 57.0, 36.6, 39.0),
    )

    /** ASME B18.3 socket head cap screws with common counterbore sizes. */
    val inchCounterbores: List<Counterbore> = listOf(
        icb("#2", 0.140, 0.086, 3.0 / 16, 0.096), icb("#4", 0.183, 0.112, 7.0 / 32, 0.1285),
        icb("#5", 0.205, 0.125, 1.0 / 4, 0.144), icb("#6", 0.226, 0.138, 9.0 / 32, 0.1695),
        icb("#8", 0.270, 0.164, 5.0 / 16, 0.196), icb("#10", 0.312, 0.190, 3.0 / 8, 0.221),
        icb("1/4", 0.375, 0.250, 7.0 / 16, 0.266), icb("5/16", 0.469, 0.312, 17.0 / 32, 0.332),
        icb("3/8", 0.562, 0.375, 5.0 / 8, 0.397), icb("7/16", 0.656, 0.438, 23.0 / 32, 0.469),
        icb("1/2", 0.750, 0.500, 13.0 / 16, 0.531), icb("5/8", 0.938, 0.625, 1.0, 0.656),
        icb("3/4", 1.125, 0.750, 1.0 + 3.0 / 16, 0.781), icb("7/8", 1.312, 0.875, 1.0 + 3.0 / 8, 0.906),
        icb("1", 1.500, 1.000, 1.0 + 5.0 / 8, 1.031),
    )

    private fun icb(size: String, head: Double, height: Double, cbore: Double, clr: Double) =
        Counterbore(size, inch(head), inch(height), inch(cbore), inch(height + 0.015), inch(clr))

    /** ISO 10642 countersunk socket screws (90°). */
    val metricCountersinks: List<Countersink> = listOf(
        Countersink("M3", 6.72, 90.0, 3.4), Countersink("M4", 8.96, 90.0, 4.5), Countersink("M5", 11.2, 90.0, 5.5),
        Countersink("M6", 13.44, 90.0, 6.6), Countersink("M8", 17.92, 90.0, 9.0), Countersink("M10", 22.4, 90.0, 11.0),
        Countersink("M12", 26.88, 90.0, 13.5), Countersink("M16", 33.6, 90.0, 17.5), Countersink("M20", 40.32, 90.0, 22.0),
    )

    /** ASME B18.3 flat countersunk head cap screws (82°), theoretical sharp head diameter. */
    val inchCountersinks: List<Countersink> = listOf(
        Countersink("#4", inch(0.255), 82.0, inch(0.1285)), Countersink("#6", inch(0.307), 82.0, inch(0.1695)),
        Countersink("#8", inch(0.359), 82.0, inch(0.196)), Countersink("#10", inch(0.411), 82.0, inch(0.221)),
        Countersink("1/4", inch(0.531), 82.0, inch(0.266)), Countersink("5/16", inch(0.656), 82.0, inch(0.332)),
        Countersink("3/8", inch(0.781), 82.0, inch(0.397)), Countersink("1/2", inch(0.938), 82.0, inch(0.531)),
    )

    /** Countersink depth from the surface (sharp corner) for diameter [dia] and included angle. */
    fun countersinkDepth(dia: Double, angle: Double, hole: Double = 0.0): Double =
        ((dia - hole) / 2.0) / kotlin.math.tan(Math.toRadians(angle / 2.0))
}
