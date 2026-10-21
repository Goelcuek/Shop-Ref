package com.shopref.core.reference

data class Citation(val key: String, val text: String, val usedFor: String)

data class FormulaEntry(val title: String, val formula: String, val notes: String, val refs: List<String>)

/** Literature behind the calculators, shown on the References screen. */
object Citations {
    val all = listOf(
        Citation("Kienzle1952", "Kienzle, O. (1952). Die Bestimmung von Kräften und Leistungen an spanenden Werkzeugen und Werkzeugmaschinen. VDI-Z 94(11/12), 299–305.", "Specific cutting force law kc = kc1.1·h^−mc"),
        Citation("Kienzle1957", "Kienzle, O., Victor, H. (1957). Spezifische Schnittkräfte bei der Metallbearbeitung. Werkstattstechnik und Maschinenbau 47(5), 224–225.", "Kienzle constants"),
        Citation("KoenigEssel1973", "König, W., Essel, K. (1973). Spezifische Schnittkraftwerte für die Zerspanung metallischer Werkstoffe. Verlag Stahleisen, Düsseldorf.", "Material constants kc1.1, mc"),
        Citation("Merchant1945", "Merchant, M.E. (1945). Mechanics of the metal cutting process. I. Orthogonal cutting and a type 2 chip. J. Applied Physics 16(5), 267–275.", "Thrust/cutting force ratio tan(β − α)"),
        Citation("Stabler1951", "Stabler, G.V. (1951). The fundamental geometry of cutting tools. Proc. IMechE 165, 14–26.", "Chip-flow rule η = i"),
        Citation("Martellotti1941", "Martellotti, M.E. (1941). An analysis of the milling process. Trans. ASME 63, 677–700.", "Chip thickness h = fz·sin φ, chip thinning, feed marks"),
        Citation("Tlusty1975", "Tlusty, J., MacNeil, P. (1975). Dynamics of cutting forces in end milling. Annals of the CIRP 24(1), 21–25.", "Helical end-mill force model, lag angle"),
        Citation("Budak1996", "Budak, E., Altintaş, Y., Armarego, E.J.A. (1996). Prediction of milling force coefficients from orthogonal cutting data. ASME J. Manuf. Sci. Eng. 118(2), 216–224. doi:10.1115/1.2831014", "Oblique transformation for Kr/Kt, Ka/Kt"),
        Citation("Altintas2012", "Altintaş, Y. (2012). Manufacturing Automation: Metal Cutting Mechanics, Machine Tool Vibrations, and CNC Design, 2nd ed. Cambridge University Press.", "Mechanistic milling force model, closed-form mean forces"),
        Citation("Schmitz2009", "Schmitz, T.L., Smith, K.S. (2009). Machining Dynamics: Frequency Response to Improved Productivity. Springer.", "Milling force simulation, average force method"),
        Citation("KopsVo1990", "Kops, L., Vo, D.T. (1990). Determination of the equivalent diameter of an end mill based on its compliance. CIRP Annals 39(1), 93–96.", "Equivalent diameter 0.8·D for deflection"),
        Citation("ShawOxford1957", "Shaw, M.C., Oxford, C.J. (1957). On the drilling of metals 2 — The torque and thrust in drilling. Trans. ASME 79, 139–148.", "Drill torque/thrust, chisel-edge contribution"),
        Citation("CaoSutherland2002", "Cao, T., Sutherland, J.W. (2002). Investigation of thread tapping load characteristics through mechanistics modeling and experimentation. Int. J. Machine Tools & Manufacture 42(14), 1527–1538. doi:10.1016/S0890-6955(02)00108-6", "Tapping torque: chip formation + friction base load, chip packing"),
        Citation("ArmaregoChen2002", "Armarego, E.J.A., Chen, M.N.P. (2002). Predictive cutting models for the forces and torque in machine tapping with straight flute taps. CIRP Annals 51(1), 75–78.", "Tapping torque mechanics"),
        Citation("Taylor1907", "Taylor, F.W. (1907). On the art of cutting metals. Trans. ASME 28, 31–350.", "Tool life v·T^n = C"),
        Citation("Sandvik", "Sandvik Coromant. Metalworking formulas and definitions; Technical Guide (material classification CMC/ISO).", "kc rake correction (1 − γ/100), drilling/turning power formulas, material groups"),
        Citation("MachHB", "Oberg, E. et al. Machinery's Handbook, 31st ed. Industrial Press (2020).", "Percent thread, tap drills, drill sizes, clearance holes, unit power, 15-min tool-life basis of speed tables"),
        Citation("MMC", "Mitsubishi Materials. Technical information: end-mill deflection (rod 0.7–0.8·D), drilling chisel-edge thrust (50–70%).", "Rules of thumb cross-check"),
        Citation("ISO286", "ISO 286-1/-2:2010. Geometrical product specifications — ISO code system for tolerances on linear sizes.", "Fits and tolerances"),
        Citation("ISO965", "ISO 965-1:2013. ISO general purpose metric screw threads — Tolerances.", "6H minor diameter tolerance"),
        Citation("ASMEB11", "ASME B1.1-2019. Unified Inch Screw Threads (UN and UNR Thread Form).", "2B minor diameter limits"),
        Citation("ASMEB1201", "ASME B1.20.1-2013. Pipe Threads, General Purpose, Inch (NPT).", "NPT data"),
        Citation("ISO228", "ISO 228-1:2000. Pipe threads where pressure-tight joints are not made on the threads.", "G (BSPP) data"),
        Citation("ISO273", "ISO 273:1979. Fasteners — Clearance holes for bolts and screws.", "Metric clearance holes"),
        Citation("ASTME140", "ASTM E140-12b(2019). Standard Hardness Conversion Tables for Metals.", "Hardness conversion"),
        Citation("ISO513", "ISO 513:2012. Classification and application of hard cutting materials (P/M/K/N/S/H).", "Material groups and colour code"),
    )

    val formulas = listOf(
        FormulaEntry("Spindle speed", "n = 1000·vc / (π·D)", "vc in m/min, D in mm. Inch: rpm = 3.82·SFM / D[in].", listOf("Sandvik")),
        FormulaEntry("Feed rate", "vf = fz · z · n   (milling)\nvf = fn · n   (drilling, turning)", "fz feed per tooth, z flutes.", listOf("Sandvik")),
        FormulaEntry("Radial chip thinning", "hmax = 2·fz·√(ae/D − (ae/D)²)   for ae < D/2", "Program fz = hmax,target · D / (2·√(ae·D − ae²)).", listOf("Martellotti1941")),
        FormulaEntry("Ball-nose effective diameter", "Deff = 2·√(ap·(D − ap)),  hmax = fz·Deff/D", "For ap < D/2 and no tilt.", listOf("Martellotti1941")),
        FormulaEntry("Specific cutting force", "kc = kc1.1 · h^(−mc) · (1 − γ/100) · Kwear\nFc = kc · b · h", "Kienzle law with Sandvik rake correction.", listOf("Kienzle1952", "KoenigEssel1973", "Sandvik")),
        FormulaEntry("Force ratios (oblique cutting)", "Kr/Kt = sin(βn − αn) / [cos i·(cos(βn − αn) + tan²i·sin βn)]\nKa/Kt = tan i·(cos(βn − αn) − sin βn) / (cos(βn − αn) + tan²i·sin βn)", "Stabler's rule η = i; tan βn = tan βa·cos η. For i = 0: Kr/Kt = tan(β − α) (Merchant).", listOf("Budak1996", "Merchant1945", "Stabler1951")),
        FormulaEntry("Milling force model", "φj(z) = θ + j·2π/N − 2·z·tan β/D\nh = fz·sin φ·sin κ\ndFt = kc1.1·h^(1−mc)·dz/sin κ,  dFr = Kr·dFt,  dFa = Ka·dFt\nFx = Σ(−dFt·cos φ − dFr·sin φ),  Fy = Σ(dFt·sin φ − dFr·cos φ)", "Numerically integrated over teeth, axial slices and one revolution; gives peak and mean forces, torque and power.", listOf("Altintas2012", "Tlusty1975", "Schmitz2009")),
        FormulaEntry("Cutting power", "Pc = T · 2π·n / 60 000   [kW, T in N·m]\nPc ≈ ap·ae·vf·kc / (60·10⁶)", "Spindle power required = Pc / η.", listOf("Sandvik")),
        FormulaEntry("Tool deflection", "δ = ∫ F·(a − x)·(L − x) / (E·I(x)) dx,  I = π·d⁴/64\nfluted section d = 0.8·D", "Stepped cantilever; uniform rod with tip load gives δ = F·L³/(3·E·I).", listOf("KopsVo1990", "MMC")),
        FormulaEntry("Drilling torque & thrust", "Mc = kc·fn·(D² − d0²)/8000   [N·m]\nFf = 0.5·kc·fn·(D − d0)/2·sin κr · (1 + chisel factor)", "kc at h = fn/2·sin κr; chisel factor ≈ 1 for an un-thinned web.", listOf("ShawOxford1957", "Sandvik", "MMC")),
        FormulaEntry("Tapping torque", "M = kc·A·dm/2,  A = 0.125·P·t + tan30°·t²\nfull depth: M ≈ kc·P²·D/8450   (rule of thumb kc·P²·D/8000)", "Energy balance on the thread groove; process factors for land friction and chip packing; forming ≈ 2× cutting.", listOf("CaoSutherland2002", "ArmaregoChen2002", "Kienzle1952")),
        FormulaEntry("Percent thread", "%cut = (D − d)/(1.299·P)·100\nd_form = D − 0.0068·%·P", "P = 1/TPI for inch threads.", listOf("MachHB")),
        FormulaEntry("Turning forces & finish", "Fc = kc1.1·(ap/sin κr)·(f·sin κr)^(1−mc)\nRt = f²/(8·rε),  Ra ≈ f²/(32·rε)", "Feed/passive forces from Ft = Fc·tan(β − γ).", listOf("Kienzle1952", "Merchant1945")),
        FormulaEntry(
            "Tool life",
            "v·T^n·h^a = C   ⇒   T = Tref·(vref/v)^(1/n)·(href/h)^(a/n)",
            "n ≈ 0.125 HSS, 0.25 carbide, 0.3 coated carbide, 0.5 ceramic; a = 0.3. Tref at the standard recommendation: " +
                "15 min for inserts and ceramics, 45 min for solid carbide, 60 min for HSS; ×4 in non-abrasive non-ferrous metals and plastics. " +
                "Expect ±50% from coolant, runout and work-holding.",
            listOf("Taylor1907", "MachHB"),
        ),
        FormulaEntry("Scallop height", "h = R − √(R² − (s/2)²)", "Ball radius R, stepover s.", listOf("Martellotti1941")),
        FormulaEntry("Arc feed compensation", "internal: vf,centre = vf·(Dw − Dt)/Dw\nexternal: vf,centre = vf·(Dw + Dt)/Dw", "Thread milling and circular interpolation.", listOf("Sandvik")),
    )

    fun byKey(key: String): Citation? = all.firstOrNull { it.key == key }
}
