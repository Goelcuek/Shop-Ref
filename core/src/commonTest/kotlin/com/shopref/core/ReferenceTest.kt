package com.shopref.core

import com.shopref.core.reference.DrillSizes
import com.shopref.core.reference.FitType
import com.shopref.core.reference.Fits
import com.shopref.core.reference.Hardness
import com.shopref.core.reference.ThreadMath
import com.shopref.core.reference.Threads
import com.shopref.core.reference.Tolerance
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReferenceTest {

    @Test
    fun drillSeriesAreComplete() {
        assertEquals(80, DrillSizes.number.size)
        assertEquals(26, DrillSizes.letter.size)
        assertEquals(0.201, DrillSizes.find("#7")!!.inch, 1e-9)
        assertEquals(0.257, DrillSizes.find("F")!!.inch, 1e-9)
        assertEquals("27/64\"", DrillSizes.fractionLabel(27))
        assertEquals("1-1/2\"", DrillSizes.fractionLabel(96))
        // Number drills get smaller as the number increases.
        assertTrue(DrillSizes.number.zipWithNext().all { (a, b) -> a.mm > b.mm })
    }

    @Test
    fun percentThreadForStandardTapDrills() {
        val t = Threads.unc.first { it.designation.startsWith("1/4-20") }
        val pct = ThreadMath.percentThreadCut(t.majorDiameter, t.pitch, t.tapDrillMm)
        assertTrue(abs(pct - 75.4) < 0.5, "1/4-20 with #7: $pct%")
        val m6 = Threads.metricCoarse.first { it.designation == "M6×1" }
        assertEquals(5.0, m6.tapDrillMm, 1e-9)
        assertEquals(4.917, m6.minorDiameterMin, 1e-3)
        assertEquals(5.153, m6.minorDiameterMax!!, 1e-3)
    }

    @Test
    fun unified2BMinorDiameterLimits() {
        // ASME B1.1: 1/4-20 UNC-2B minor 0.1959–0.2070, #10-24 0.1450–0.1560 (in)
        val q = Threads.unc.first { it.designation.startsWith("1/4-20") }
        assertEquals(0.1959, q.minorDiameterMin / 25.4, 2e-4)
        assertEquals(0.2074, q.minorDiameterMax!! / 25.4, 2e-4)
        val n10 = Threads.unc.first { it.designation.startsWith("#10-24") }
        assertEquals(0.1449, n10.minorDiameterMin / 25.4, 2e-4)
        assertEquals(0.1555, n10.minorDiameterMax!! / 25.4, 6e-4)
    }

    @Test
    fun iso286KnownValues() {
        // 25 mm: H7 = +21/0, g6 = −7/−20, p6 = +35/+22, s6 = +48/+35
        assertEquals(Tolerance(21, 0), Fits.hole("H", 7, 25.0))
        assertEquals(Tolerance(-7, -20), Fits.shaft("g", 6, 25.0))
        assertEquals(Tolerance(35, 22), Fits.shaft("p", 6, 25.0))
        assertEquals(Tolerance(48, 35), Fits.shaft("s", 6, 25.0))
        // 40 mm: K7 = +7/−18, M7 = 0/−25, N7 = −8/−33, P7 = −17/−42
        assertEquals(Tolerance(7, -18), Fits.hole("K", 7, 40.0))
        assertEquals(Tolerance(0, -25), Fits.hole("M", 7, 40.0))
        assertEquals(Tolerance(-8, -33), Fits.hole("N", 7, 40.0))
        assertEquals(Tolerance(-17, -42), Fits.hole("P", 7, 40.0))
        // 25 mm S7 = −27/−48
        assertEquals(Tolerance(-27, -48), Fits.hole("S", 7, 25.0))
        // js6 at 15 mm: IT6 = 11 → ±5.5
        assertEquals(Tolerance(5.5, -5.5), Fits.shaft("js", 6, 15.0))
        assertNull(Fits.hole("P", 9, 25.0))
        assertNull(Fits.hole("H", 7, 600.0))
    }

    @Test
    fun fitClassification() {
        val sliding = assertNotNull(Fits.fit("H", 7, "g", 6, 25.0))
        assertEquals(FitType.CLEARANCE, sliding.type)
        assertEquals(41.0, sliding.maxClearance)
        assertEquals(7.0, sliding.minClearance)
        assertEquals(FitType.TRANSITION, Fits.fit("H", 7, "k", 6, 25.0)!!.type)
        assertEquals(FitType.INTERFERENCE, Fits.fit("H", 7, "s", 6, 25.0)!!.type)
    }

    @Test
    fun hardnessInterpolation() {
        assertEquals(513.0, Hardness.hvFromHrc(50.0)!!, 1e-9)
        assertEquals(50.0, Hardness.hrcFromHv(513.0)!!, 1e-9)
        assertNull(Hardness.hvFromHrc(80.0))
    }
}
