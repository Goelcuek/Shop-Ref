package com.shopref.core.tooling

import com.shopref.core.material.Family
import com.shopref.core.material.Material

/** Cutting tool substrate. */
enum class ToolMaterial(
    val label: String,
    /** Young's modulus [MPa] used for deflection. */
    val youngsModulus: Double,
    /** Practical allowable bending stress for a rotating tool [MPa] (fatigue + notch effect). */
    val allowableBendingStress: Double,
    /** Ultimate shear strength [MPa] used for tap torsional strength. */
    val shearStrength: Double,
    /** Taylor tool-life exponent n in v·T^n = C. */
    val taylorN: Double,
    val isHss: Boolean,
) {
    HSS("HSS", 210_000.0, 900.0, 1500.0, 0.125, true),
    HSS_CO("HSS-Co (M42/HSS-E)", 215_000.0, 950.0, 1550.0, 0.13, true),
    PM_HSS("PM-HSS (powder metal)", 220_000.0, 1000.0, 1700.0, 0.14, true),
    CARBIDE("Solid carbide", 600_000.0, 1200.0, 2000.0, 0.25, false),
    CERAMIC("Ceramic (SiAlON)", 300_000.0, 500.0, 600.0, 0.5, false),
    PCD("PCD / diamond tipped", 600_000.0, 1200.0, 2000.0, 0.4, false),
    ;

    /**
     * Cutting speed for milling with this substrate [m/min], before coating and engagement
     * corrections. Returns null when the substrate is not recommended for the material.
     */
    fun millingSpeed(m: Material): Double? = when (this) {
        HSS -> m.vcHss.takeIf { it > 0 }
        HSS_CO -> m.vcHss.takeIf { it > 0 }?.times(1.15)
        PM_HSS -> m.vcHss.takeIf { it > 0 }?.times(1.30)
        CARBIDE -> m.vcCarbide
        CERAMIC -> when (m.family) {
            Family.NICKEL -> m.vcCarbide * 25.0
            Family.CAST_IRON -> m.vcCarbide * 4.0
            else -> null
        }
        PCD -> when (m.family) {
            Family.ALUMINUM, Family.MAGNESIUM, Family.COMPOSITE, Family.GRAPHITE -> m.vcCarbide * 3.0
            Family.COPPER -> m.vcCarbide * 2.5
            Family.PLASTIC -> m.vcCarbide * 2.0
            else -> null
        }
    }

    /** Chip-load multiplier relative to solid carbide. */
    val chipLoadFactor: Double
        get() = when (this) {
            HSS -> 0.75
            HSS_CO -> 0.8
            PM_HSS -> 0.85
            CARBIDE -> 1.0
            CERAMIC -> 0.8
            PCD -> 1.0
        }

    fun incompatibilityReason(m: Material): String? = when {
        isHss && m.vcHss <= 0 -> "HSS is not suitable for ${m.name.lowercase()} — use carbide."
        this == CERAMIC && millingSpeed(m) == null ->
            "Ceramic end mills are intended for nickel superalloys (and cast iron)."
        this == PCD && millingSpeed(m) == null ->
            "PCD/diamond reacts with iron and titanium at cutting temperatures; use carbide."
        else -> null
    }
}

/** Tool coatings with speed multipliers per material family (null = not recommended). */
enum class Coating(val label: String, val description: String) {
    UNCOATED("Uncoated", "Bright/polished carbide or HSS"),
    TIN("TiN", "Titanium nitride — general purpose, gold colour"),
    TICN("TiCN", "Titanium carbonitride — harder than TiN, steels & cast iron"),
    TIALN("TiAlN", "Titanium aluminium nitride — heat resistant, dry/high speed steel"),
    ALTIN("AlTiN", "Aluminium titanium nitride — high Al content, hard/hot work"),
    ALCRN("AlCrN", "Aluminium chromium nitride — steels, stainless, high temperature"),
    ALTISIN("AlTiSiN (nACo)", "Nano-composite — hardened steel, superalloys"),
    ZRN("ZrN", "Zirconium nitride — aluminium & non-ferrous, low adhesion"),
    TIB2("TiB2", "Titanium diboride — aluminium, magnesium, copper"),
    DLC("DLC", "Diamond-like carbon — aluminium, copper, plastics, graphite"),
    DIAMOND("CVD diamond", "Polycrystalline diamond film — composites, graphite, high-Si Al"),
    ;

    /** Speed multiplier vs. uncoated, or null when the coating is a poor choice. */
    fun speedFactor(family: Family): Double? {
        val row = FACTORS.getValue(this)
        return row[family.ordinal]
    }

    fun incompatibilityReason(family: Family): String? = when {
        speedFactor(family) != null -> null
        this == DIAMOND || this == DLC ->
            "Diamond-based coatings react with ferrous metals and titanium at cutting temperature."
        else -> "$label is not recommended for ${family.label.lowercase()}."
    }

    private companion object {
        // Column order follows Family.ordinal:
        // STEEL, STAINLESS, CAST_IRON, ALUMINUM, MAGNESIUM, COPPER, PLASTIC, COMPOSITE, GRAPHITE, TITANIUM, NICKEL, HARDENED
        val FACTORS: Map<Coating, List<Double?>> = mapOf(
            UNCOATED to listOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0),
            TIN to listOf(1.15, 1.10, 1.15, 1.05, 1.05, 1.10, 1.0, 1.0, 1.0, 1.05, 1.05, 1.0),
            TICN to listOf(1.25, 1.15, 1.25, 1.10, 1.10, 1.15, 1.0, 1.05, 1.05, 1.10, 1.10, 1.05),
            TIALN to listOf(1.40, 1.30, 1.35, 0.95, 0.95, 1.05, 1.0, 1.05, 1.05, 1.25, 1.30, 1.45),
            ALTIN to listOf(1.50, 1.35, 1.40, 0.90, 0.90, 1.0, 1.0, 1.05, 1.05, 1.30, 1.35, 1.60),
            ALCRN to listOf(1.50, 1.40, 1.40, 0.95, 0.95, 1.05, 1.0, 1.05, 1.05, 1.30, 1.35, 1.50),
            ALTISIN to listOf(1.60, 1.40, 1.45, 0.90, 0.90, 1.0, 1.0, 1.05, 1.05, 1.30, 1.40, 1.75),
            ZRN to listOf(1.05, 1.0, 1.05, 1.25, 1.20, 1.20, 1.10, 1.05, 1.05, 1.10, 1.0, 1.0),
            TIB2 to listOf(1.0, 1.0, 1.0, 1.30, 1.25, 1.25, 1.10, 1.05, 1.05, 1.15, 1.05, 1.0),
            DLC to listOf(null, null, null, 1.35, 1.25, 1.25, 1.15, 1.20, 1.20, null, null, null),
            DIAMOND to listOf(null, null, null, 1.60, 1.60, 1.50, 1.30, 2.0, 2.5, null, null, null),
        )
    }
}

/** Cutting-edge condition: multiplies specific cutting force (flank wear raises forces). */
enum class ToolCondition(val label: String, val forceFactor: Double) {
    SHARP("Sharp / new", 1.0),
    NORMAL("Normal wear", 1.1),
    WORN("Worn (end of life)", 1.3),
}

/**
 * How hard the milling recommendations push. Conservative matches cautious catalogue starting
 * values (e.g. RobbJack 4140: 0.0020" on a ½" end mill); Aggressive matches high-end catalogue and
 * HEM practice (e.g. Maritool 4140: 0.0035" on ½"). Multiplies the target chip thickness and speed.
 */
enum class ChipLoadLevel(val label: String, val description: String, val chipFactor: Double, val speedFactor: Double) {
    CONSERVATIVE("Conservative", "Cautious catalogue starting values", 1.0, 1.0),
    STANDARD("Standard", "Typical catalogue values on a rigid machine", 1.35, 1.05),
    AGGRESSIVE("Aggressive / HEM", "High-end catalogue values: rigid setup, good holder, premium tools", 1.75, 1.10),
}

/** Machine/setup rigidity class; scales default depths and chip loads. */
enum class Rigidity(val label: String, val depthFactor: Double, val feedFactor: Double) {
    LIGHT("Light (router, mini mill)", 0.5, 0.75),
    MEDIUM("Medium (R8 / BT30 / CAT30)", 0.75, 0.9),
    HEAVY("Heavy (CAT40 / BT40 / HSK63)", 1.0, 1.0),
}
