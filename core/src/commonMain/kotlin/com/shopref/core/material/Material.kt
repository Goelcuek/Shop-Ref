package com.shopref.core.material

/** ISO 513 workpiece material groups, with the standard colour code (ARGB). */
enum class IsoGroup(val label: String, val description: String, val color: Long) {
    P("P", "Steel", 0xFF1E73BE),
    M("M", "Stainless steel", 0xFFF2C500),
    K("K", "Cast iron", 0xFFD7263D),
    N("N", "Non-ferrous", 0xFF2E9E44),
    S("S", "Superalloys & titanium", 0xFFE07B00),
    H("H", "Hardened materials", 0xFF7A7A7A),
}

/**
 * Material family. Finer than the ISO group; drives coating and tool-material
 * compatibility (e.g. diamond coatings are only offered for non-ferrous families).
 */
enum class Family(val label: String) {
    STEEL("Steel"),
    STAINLESS("Stainless"),
    CAST_IRON("Cast iron"),
    ALUMINUM("Aluminium"),
    MAGNESIUM("Magnesium"),
    COPPER("Copper alloys"),
    PLASTIC("Plastics"),
    COMPOSITE("Composites"),
    GRAPHITE("Graphite"),
    TITANIUM("Titanium"),
    NICKEL("Nickel/cobalt alloys"),
    HARDENED("Hardened steel"),
}

/**
 * Workpiece material with cutting data.
 *
 * Force data uses the Kienzle law kc = kc11 · h^-mc (N/mm², h in mm), referenced to
 * a rake angle of 0° (Sandvik Coromant convention); see [com.shopref.core.calc.Kienzle].
 * Speed/feed data are starting values for a rigid machine with flood coolant.
 */
data class Material(
    val id: String,
    val name: String,
    val group: IsoGroup,
    val family: Family,
    /** Example grades (AISI/SAE, EN, DIN, UNS…). */
    val examples: String,
    /** Typical hardness, as text (e.g. "≈130 HB"). */
    val hardness: String,
    /** Approximate Brinell hardness, used for checks such as form-tap suitability. */
    val hardnessHb: Double,
    /** Kienzle specific cutting force at h = b = 1 mm, rake 0° [N/mm²]. */
    val kc11: Double,
    /** Kienzle exponent (slope of log kc vs log h). */
    val mc: Double,
    /** Cutting speed for an uncoated solid-carbide end mill at ae ≈ 0.5·D [m/min]. */
    val vcCarbide: Double,
    /** Cutting speed for an HSS end mill [m/min]; 0 = HSS not suitable. */
    val vcHss: Double,
    /** Carbide end-mill max chip thickness target as a fraction of tool diameter. */
    val fzPerD: Double,
    /** Solid carbide drill feed per revolution as a fraction of drill diameter. */
    val drillFnPerD: Double,
    /** HSS-E cutting tap speed [m/min]; 0 = tapping not recommended (thread mill). */
    val tapVc: Double,
    /** Coated-carbide turning speed, medium roughing [m/min]. */
    val turnVc: Double,
    /** Apparent tool–chip friction angle [deg] used for the force-ratio model. */
    val frictionAngle: Double,
    /** Whether thread forming (roll taps) is generally suitable (ductile, elongation > ~7%). */
    val formTappable: Boolean,
    /** Machinability rating, % of AISI 1212 / B1112 = 100 (approximate). */
    val machinability: Int,
    val notes: String = "",
) {
    val displayName: String get() = "$name ($examples)"
}
