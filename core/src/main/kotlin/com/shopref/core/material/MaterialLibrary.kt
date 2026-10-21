package com.shopref.core.material

/**
 * Built-in workpiece material library.
 *
 * Kienzle constants (kc1.1, mc) are typical literature values for a 0° rake reference,
 * compiled from the Sandvik Coromant CMC/ISO material tables and the König & Essel (1973)
 * Kienzle tables, cross-checked against Machinery's Handbook unit-power constants.
 * Speeds and feeds are conservative starting points compiled from tool-maker
 * catalogues. Actual values vary with heat lot, tool geometry and setup: always
 * verify against your tool manufacturer's data.
 */
object MaterialLibrary {

    val all: List<Material> = listOf(
        // ------------------------------------------------------------------ P: steels
        Material(
            id = "p_low_carbon", name = "Low-carbon steel", group = IsoGroup.P, family = Family.STEEL,
            examples = "1018, 1020, A36, S235JR, C15", hardness = "≈130 HB", hardnessHb = 130.0,
            kc11 = 1400.0, mc = 0.22, vcCarbide = 120.0, vcHss = 30.0, fzPerD = 0.0050,
            drillFnPerD = 0.025, tapVc = 15.0, turnVc = 250.0, frictionAngle = 28.0,
            formTappable = true, machinability = 70,
            notes = "Gummy when soft: keep chip load up and use sharp, positive-rake tools.",
        ),
        Material(
            id = "p_free_machining", name = "Free-machining steel", group = IsoGroup.P, family = Family.STEEL,
            examples = "12L14, 1215, 11SMnPb30", hardness = "≈150 HB", hardnessHb = 150.0,
            kc11 = 1250.0, mc = 0.22, vcCarbide = 160.0, vcHss = 45.0, fzPerD = 0.0060,
            drillFnPerD = 0.028, tapVc = 20.0, turnVc = 300.0, frictionAngle = 26.0,
            formTappable = true, machinability = 170,
        ),
        Material(
            id = "p_medium_carbon", name = "Medium-carbon steel", group = IsoGroup.P, family = Family.STEEL,
            examples = "1045, 1050, C45, S355", hardness = "≈180 HB", hardnessHb = 180.0,
            kc11 = 1500.0, mc = 0.22, vcCarbide = 100.0, vcHss = 25.0, fzPerD = 0.0045,
            drillFnPerD = 0.022, tapVc = 12.0, turnVc = 220.0, frictionAngle = 28.0,
            formTappable = true, machinability = 57,
        ),
        Material(
            id = "p_alloy_annealed", name = "Alloy steel, annealed", group = IsoGroup.P, family = Family.STEEL,
            examples = "4140, 4340, 8620, 42CrMo4, 16MnCr5", hardness = "≈200 HB", hardnessHb = 200.0,
            kc11 = 1700.0, mc = 0.24, vcCarbide = 95.0, vcHss = 22.0, fzPerD = 0.0045,
            drillFnPerD = 0.022, tapVc = 10.0, turnVc = 200.0, frictionAngle = 28.0,
            formTappable = true, machinability = 60,
        ),
        Material(
            id = "p_alloy_prehard", name = "Alloy steel, pre-hardened", group = IsoGroup.P, family = Family.STEEL,
            examples = "4140 PH, P20, 42CrMo4+QT", hardness = "28–32 HRC (≈300 HB)", hardnessHb = 300.0,
            kc11 = 1900.0, mc = 0.24, vcCarbide = 75.0, vcHss = 15.0, fzPerD = 0.0040,
            drillFnPerD = 0.018, tapVc = 7.0, turnVc = 160.0, frictionAngle = 29.0,
            formTappable = true, machinability = 45,
        ),
        Material(
            id = "p_alloy_qt", name = "Alloy steel, quenched & tempered", group = IsoGroup.P, family = Family.STEEL,
            examples = "4340 HT, 4140 HT, 34CrNiMo6", hardness = "35–40 HRC (≈350 HB)", hardnessHb = 350.0,
            kc11 = 2100.0, mc = 0.24, vcCarbide = 60.0, vcHss = 10.0, fzPerD = 0.0035,
            drillFnPerD = 0.015, tapVc = 4.0, turnVc = 130.0, frictionAngle = 30.0,
            formTappable = false, machinability = 35,
            notes = "Prefer coated carbide; HSS only at low speed.",
        ),
        Material(
            id = "p_tool_annealed", name = "Tool steel, annealed", group = IsoGroup.P, family = Family.STEEL,
            examples = "A2, O1, S7, H13, 1.2344", hardness = "≈220 HB", hardnessHb = 220.0,
            kc11 = 1900.0, mc = 0.24, vcCarbide = 75.0, vcHss = 15.0, fzPerD = 0.0040,
            drillFnPerD = 0.018, tapVc = 8.0, turnVc = 150.0, frictionAngle = 29.0,
            formTappable = false, machinability = 42,
        ),
        Material(
            id = "p_tool_d2", name = "High-chrome tool steel, annealed", group = IsoGroup.P, family = Family.STEEL,
            examples = "D2, D3, 1.2379, X153CrMoV12", hardness = "≈250 HB", hardnessHb = 250.0,
            kc11 = 2100.0, mc = 0.24, vcCarbide = 55.0, vcHss = 10.0, fzPerD = 0.0035,
            drillFnPerD = 0.015, tapVc = 5.0, turnVc = 110.0, frictionAngle = 30.0,
            formTappable = false, machinability = 27,
            notes = "Abrasive carbides: expect fast flank wear.",
        ),
        Material(
            id = "p_cast_steel", name = "Cast steel", group = IsoGroup.P, family = Family.STEEL,
            examples = "GS-45, WCB, 1.0619", hardness = "≈180 HB", hardnessHb = 180.0,
            kc11 = 1600.0, mc = 0.24, vcCarbide = 90.0, vcHss = 20.0, fzPerD = 0.0045,
            drillFnPerD = 0.020, tapVc = 10.0, turnVc = 180.0, frictionAngle = 28.0,
            formTappable = false, machinability = 50,
            notes = "Scale/sand inclusions: take the first cut below the skin.",
        ),
        // ------------------------------------------------------------------ M: stainless
        Material(
            id = "m_303", name = "Free-machining stainless", group = IsoGroup.M, family = Family.STAINLESS,
            examples = "303, 416, 1.4305", hardness = "≈180 HB", hardnessHb = 180.0,
            kc11 = 1900.0, mc = 0.20, vcCarbide = 100.0, vcHss = 25.0, fzPerD = 0.0045,
            drillFnPerD = 0.020, tapVc = 10.0, turnVc = 200.0, frictionAngle = 31.0,
            formTappable = true, machinability = 78,
        ),
        Material(
            id = "m_austenitic", name = "Austenitic stainless", group = IsoGroup.M, family = Family.STAINLESS,
            examples = "304, 304L, 316, 316L, 321, 1.4301, 1.4404", hardness = "≈180 HB", hardnessHb = 180.0,
            kc11 = 2150.0, mc = 0.20, vcCarbide = 70.0, vcHss = 15.0, fzPerD = 0.0040,
            drillFnPerD = 0.016, tapVc = 6.0, turnVc = 170.0, frictionAngle = 32.0,
            formTappable = true, machinability = 40,
            notes = "Work hardens: never dwell or rub; keep a positive chip load.",
        ),
        Material(
            id = "m_martensitic", name = "Martensitic/ferritic stainless, annealed", group = IsoGroup.M, family = Family.STAINLESS,
            examples = "410, 420, 430, 440C (ann.), 1.4021", hardness = "≈200 HB", hardnessHb = 200.0,
            kc11 = 1800.0, mc = 0.21, vcCarbide = 90.0, vcHss = 20.0, fzPerD = 0.0045,
            drillFnPerD = 0.018, tapVc = 8.0, turnVc = 190.0, frictionAngle = 30.0,
            formTappable = true, machinability = 55,
        ),
        Material(
            id = "m_ph_h1150", name = "PH stainless, overaged", group = IsoGroup.M, family = Family.STAINLESS,
            examples = "17-4 PH H1150, 15-5 PH H1150", hardness = "≈30 HRC (≈300 HB)", hardnessHb = 300.0,
            kc11 = 2300.0, mc = 0.21, vcCarbide = 60.0, vcHss = 12.0, fzPerD = 0.0038,
            drillFnPerD = 0.015, tapVc = 5.0, turnVc = 140.0, frictionAngle = 31.0,
            formTappable = false, machinability = 45,
        ),
        Material(
            id = "m_ph_h900", name = "PH stainless, peak aged", group = IsoGroup.M, family = Family.STAINLESS,
            examples = "17-4 PH H900, 13-8 Mo", hardness = "≈42 HRC (≈400 HB)", hardnessHb = 400.0,
            kc11 = 2600.0, mc = 0.21, vcCarbide = 45.0, vcHss = 8.0, fzPerD = 0.0030,
            drillFnPerD = 0.012, tapVc = 3.0, turnVc = 110.0, frictionAngle = 32.0,
            formTappable = false, machinability = 30,
        ),
        Material(
            id = "m_duplex", name = "Duplex stainless", group = IsoGroup.M, family = Family.STAINLESS,
            examples = "2205, 2507, 1.4462", hardness = "≈250 HB", hardnessHb = 250.0,
            kc11 = 2300.0, mc = 0.21, vcCarbide = 55.0, vcHss = 10.0, fzPerD = 0.0035,
            drillFnPerD = 0.013, tapVc = 4.0, turnVc = 120.0, frictionAngle = 32.0,
            formTappable = false, machinability = 30,
        ),
        // ------------------------------------------------------------------ K: cast iron
        Material(
            id = "k_grey_low", name = "Grey cast iron", group = IsoGroup.K, family = Family.CAST_IRON,
            examples = "Class 30, EN-GJL-250, GG-25", hardness = "≈200 HB", hardnessHb = 200.0,
            kc11 = 1150.0, mc = 0.20, vcCarbide = 120.0, vcHss = 30.0, fzPerD = 0.0050,
            drillFnPerD = 0.030, tapVc = 12.0, turnVc = 250.0, frictionAngle = 24.0,
            formTappable = false, machinability = 70,
            notes = "Short chips, abrasive dust. Usually machined dry or with air blast.",
        ),
        Material(
            id = "k_grey_high", name = "Grey cast iron, high tensile", group = IsoGroup.K, family = Family.CAST_IRON,
            examples = "Class 40/50, EN-GJL-300/350", hardness = "≈240 HB", hardnessHb = 240.0,
            kc11 = 1350.0, mc = 0.28, vcCarbide = 100.0, vcHss = 22.0, fzPerD = 0.0045,
            drillFnPerD = 0.026, tapVc = 10.0, turnVc = 210.0, frictionAngle = 25.0,
            formTappable = false, machinability = 55,
        ),
        Material(
            id = "k_ductile", name = "Ductile (nodular) iron", group = IsoGroup.K, family = Family.CAST_IRON,
            examples = "65-45-12, 80-55-06, EN-GJS-500, GGG-50", hardness = "≈200 HB", hardnessHb = 200.0,
            kc11 = 1350.0, mc = 0.25, vcCarbide = 100.0, vcHss = 25.0, fzPerD = 0.0045,
            drillFnPerD = 0.025, tapVc = 10.0, turnVc = 200.0, frictionAngle = 27.0,
            formTappable = false, machinability = 60,
        ),
        Material(
            id = "k_cgi", name = "Compacted graphite iron", group = IsoGroup.K, family = Family.CAST_IRON,
            examples = "CGI, EN-GJV-450", hardness = "≈230 HB", hardnessHb = 230.0,
            kc11 = 1500.0, mc = 0.25, vcCarbide = 80.0, vcHss = 18.0, fzPerD = 0.0040,
            drillFnPerD = 0.022, tapVc = 8.0, turnVc = 170.0, frictionAngle = 27.0,
            formTappable = false, machinability = 45,
        ),
        // ------------------------------------------------------------------ N: non-ferrous
        Material(
            id = "n_al_wrought", name = "Aluminium, wrought", group = IsoGroup.N, family = Family.ALUMINUM,
            examples = "6061-T6, 6082, 5083, 2024", hardness = "≈95 HB", hardnessHb = 95.0,
            kc11 = 600.0, mc = 0.25, vcCarbide = 350.0, vcHss = 100.0, fzPerD = 0.0100,
            drillFnPerD = 0.030, tapVc = 25.0, turnVc = 600.0, frictionAngle = 32.0,
            formTappable = true, machinability = 300,
            notes = "Use 2–3 flute polished tools; chip evacuation limits slotting.",
        ),
        Material(
            id = "n_al_7075", name = "Aluminium, high-strength", group = IsoGroup.N, family = Family.ALUMINUM,
            examples = "7075-T6, 7050-T7451, 2014", hardness = "≈150 HB", hardnessHb = 150.0,
            kc11 = 700.0, mc = 0.25, vcCarbide = 330.0, vcHss = 90.0, fzPerD = 0.0095,
            drillFnPerD = 0.030, tapVc = 22.0, turnVc = 550.0, frictionAngle = 31.0,
            formTappable = true, machinability = 300,
        ),
        Material(
            id = "n_al_cast", name = "Aluminium, cast (<12% Si)", group = IsoGroup.N, family = Family.ALUMINUM,
            examples = "A356, 356-T6, AlSi7Mg, MIC-6", hardness = "≈80 HB", hardnessHb = 80.0,
            kc11 = 650.0, mc = 0.25, vcCarbide = 250.0, vcHss = 80.0, fzPerD = 0.0080,
            drillFnPerD = 0.028, tapVc = 20.0, turnVc = 450.0, frictionAngle = 30.0,
            formTappable = true, machinability = 250,
        ),
        Material(
            id = "n_al_high_si", name = "Aluminium, high-silicon cast", group = IsoGroup.N, family = Family.ALUMINUM,
            examples = "A390, AlSi17Cu4", hardness = "≈120 HB", hardnessHb = 120.0,
            kc11 = 800.0, mc = 0.25, vcCarbide = 150.0, vcHss = 40.0, fzPerD = 0.0060,
            drillFnPerD = 0.022, tapVc = 12.0, turnVc = 250.0, frictionAngle = 28.0,
            formTappable = false, machinability = 120,
            notes = "Very abrasive: diamond coated or PCD tools strongly recommended.",
        ),
        Material(
            id = "n_magnesium", name = "Magnesium alloy", group = IsoGroup.N, family = Family.MAGNESIUM,
            examples = "AZ31B, AZ91D", hardness = "≈60 HB", hardnessHb = 60.0,
            kc11 = 350.0, mc = 0.25, vcCarbide = 400.0, vcHss = 150.0, fzPerD = 0.0100,
            drillFnPerD = 0.030, tapVc = 25.0, turnVc = 700.0, frictionAngle = 28.0,
            formTappable = true, machinability = 500,
            notes = "FIRE HAZARD: fine chips are flammable. No water-based coolant on fines; keep chips thick.",
        ),
        Material(
            id = "n_brass", name = "Brass, free-cutting", group = IsoGroup.N, family = Family.COPPER,
            examples = "C360, CW614N, CuZn39Pb3", hardness = "≈90 HB", hardnessHb = 90.0,
            kc11 = 700.0, mc = 0.25, vcCarbide = 300.0, vcHss = 100.0, fzPerD = 0.0080,
            drillFnPerD = 0.030, tapVc = 25.0, turnVc = 350.0, frictionAngle = 24.0,
            formTappable = false, machinability = 300,
            notes = "Use 0° or slightly negative rake to avoid tools grabbing (drills: dub the lips).",
        ),
        Material(
            id = "n_copper", name = "Copper, pure", group = IsoGroup.N, family = Family.COPPER,
            examples = "C101, C110, Cu-ETP, Cu-OF", hardness = "≈90 HB", hardnessHb = 90.0,
            kc11 = 1750.0, mc = 0.25, vcCarbide = 200.0, vcHss = 60.0, fzPerD = 0.0060,
            drillFnPerD = 0.020, tapVc = 15.0, turnVc = 250.0, frictionAngle = 36.0,
            formTappable = true, machinability = 60,
            notes = "Very gummy: sharp, polished, high-rake tools and plenty of lubricant.",
        ),
        Material(
            id = "n_bronze", name = "Bronze, bearing/leaded", group = IsoGroup.N, family = Family.COPPER,
            examples = "C932 (SAE 660), C936, CuSn7ZnPb", hardness = "≈70 HB", hardnessHb = 70.0,
            kc11 = 1000.0, mc = 0.25, vcCarbide = 180.0, vcHss = 50.0, fzPerD = 0.0060,
            drillFnPerD = 0.025, tapVc = 15.0, turnVc = 250.0, frictionAngle = 26.0,
            formTappable = false, machinability = 200,
        ),
        Material(
            id = "n_al_bronze", name = "Aluminium bronze", group = IsoGroup.N, family = Family.COPPER,
            examples = "C954, C630, CuAl10Fe5Ni5", hardness = "≈170 HB", hardnessHb = 170.0,
            kc11 = 1600.0, mc = 0.25, vcCarbide = 100.0, vcHss = 30.0, fzPerD = 0.0045,
            drillFnPerD = 0.020, tapVc = 8.0, turnVc = 150.0, frictionAngle = 30.0,
            formTappable = false, machinability = 60,
        ),
        Material(
            id = "n_acetal", name = "Acetal (POM)", group = IsoGroup.N, family = Family.PLASTIC,
            examples = "Delrin, POM-C, POM-H", hardness = "≈85 Shore D", hardnessHb = 20.0,
            kc11 = 200.0, mc = 0.30, vcCarbide = 300.0, vcHss = 150.0, fzPerD = 0.0120,
            drillFnPerD = 0.030, tapVc = 20.0, turnVc = 300.0, frictionAngle = 25.0,
            formTappable = false, machinability = 0,
            notes = "Use sharp single/2-flute 'O'-flute tools; heat causes melting, not wear.",
        ),
        Material(
            id = "n_nylon", name = "Nylon / UHMW-PE", group = IsoGroup.N, family = Family.PLASTIC,
            examples = "PA6, PA66, UHMW", hardness = "≈75 Shore D", hardnessHb = 15.0,
            kc11 = 180.0, mc = 0.30, vcCarbide = 250.0, vcHss = 120.0, fzPerD = 0.0120,
            drillFnPerD = 0.030, tapVc = 20.0, turnVc = 250.0, frictionAngle = 25.0,
            formTappable = false, machinability = 0,
            notes = "Springy: holes close up after drilling; allow for it.",
        ),
        Material(
            id = "n_peek", name = "PEEK / engineering plastics", group = IsoGroup.N, family = Family.PLASTIC,
            examples = "PEEK, PEI (Ultem), PPS", hardness = "≈85 Shore D", hardnessHb = 25.0,
            kc11 = 300.0, mc = 0.30, vcCarbide = 200.0, vcHss = 100.0, fzPerD = 0.0080,
            drillFnPerD = 0.025, tapVc = 15.0, turnVc = 250.0, frictionAngle = 27.0,
            formTappable = false, machinability = 0,
        ),
        Material(
            id = "n_acrylic", name = "Acrylic / polycarbonate", group = IsoGroup.N, family = Family.PLASTIC,
            examples = "PMMA, PC (Lexan)", hardness = "≈90 Shore D", hardnessHb = 25.0,
            kc11 = 220.0, mc = 0.30, vcCarbide = 250.0, vcHss = 120.0, fzPerD = 0.0100,
            drillFnPerD = 0.020, tapVc = 15.0, turnVc = 250.0, frictionAngle = 25.0,
            formTappable = false, machinability = 0,
            notes = "Acrylic chips/cracks: use zero-rake ('dubbed') drills and air cooling.",
        ),
        Material(
            id = "n_g10", name = "Glass-epoxy laminate", group = IsoGroup.N, family = Family.COMPOSITE,
            examples = "G10, FR4, GFRP", hardness = "abrasive", hardnessHb = 40.0,
            kc11 = 500.0, mc = 0.30, vcCarbide = 150.0, vcHss = 0.0, fzPerD = 0.0060,
            drillFnPerD = 0.020, tapVc = 10.0, turnVc = 200.0, frictionAngle = 25.0,
            formTappable = false, machinability = 0,
            notes = "Extremely abrasive: diamond-coated tools. Dust is a health hazard — use extraction.",
        ),
        Material(
            id = "n_cfrp", name = "Carbon-fibre composite", group = IsoGroup.N, family = Family.COMPOSITE,
            examples = "CFRP prepreg, carbon plate", hardness = "abrasive", hardnessHb = 40.0,
            kc11 = 600.0, mc = 0.30, vcCarbide = 150.0, vcHss = 0.0, fzPerD = 0.0050,
            drillFnPerD = 0.015, tapVc = 8.0, turnVc = 200.0, frictionAngle = 25.0,
            formTappable = false, machinability = 0,
            notes = "Use compression routers / diamond tools to prevent delamination. Conductive dust.",
        ),
        Material(
            id = "n_graphite", name = "Graphite", group = IsoGroup.N, family = Family.GRAPHITE,
            examples = "EDM graphite", hardness = "≈60 Shore", hardnessHb = 30.0,
            kc11 = 150.0, mc = 0.30, vcCarbide = 300.0, vcHss = 0.0, fzPerD = 0.0050,
            drillFnPerD = 0.020, tapVc = 15.0, turnVc = 300.0, frictionAngle = 22.0,
            formTappable = false, machinability = 0,
            notes = "Abrasive dust; diamond-coated tools and dust extraction.",
        ),
        // ------------------------------------------------------------------ S: superalloys & Ti
        Material(
            id = "s_ti_cp", name = "Titanium, commercially pure", group = IsoGroup.S, family = Family.TITANIUM,
            examples = "Grade 2, Grade 4", hardness = "≈200 HB", hardnessHb = 200.0,
            kc11 = 1300.0, mc = 0.23, vcCarbide = 60.0, vcHss = 18.0, fzPerD = 0.0045,
            drillFnPerD = 0.016, tapVc = 6.0, turnVc = 90.0, frictionAngle = 26.0,
            formTappable = true, machinability = 25,
            notes = "Low thermal conductivity: use high-pressure coolant and avoid rubbing.",
        ),
        Material(
            id = "s_ti64", name = "Titanium alloy", group = IsoGroup.S, family = Family.TITANIUM,
            examples = "Ti-6Al-4V (Grade 5), Ti-6Al-4V ELI", hardness = "≈330 HB (36 HRC)", hardnessHb = 330.0,
            kc11 = 1450.0, mc = 0.23, vcCarbide = 45.0, vcHss = 10.0, fzPerD = 0.0040,
            drillFnPerD = 0.014, tapVc = 4.0, turnVc = 60.0, frictionAngle = 26.0,
            formTappable = true, machinability = 20,
            notes = "Galling/heat: keep tools sharp, coolant on, radial engagement low with high axial depth.",
        ),
        Material(
            id = "s_inconel_718", name = "Nickel alloy, aged", group = IsoGroup.S, family = Family.NICKEL,
            examples = "Inconel 718, Waspaloy, Rene 41", hardness = "≈40 HRC (≈380 HB)", hardnessHb = 380.0,
            kc11 = 3300.0, mc = 0.25, vcCarbide = 20.0, vcHss = 4.0, fzPerD = 0.0030,
            drillFnPerD = 0.010, tapVc = 2.5, turnVc = 40.0, frictionAngle = 30.0,
            formTappable = false, machinability = 12,
            notes = "Work hardens severely. Notching at depth line: vary DOC between passes.",
        ),
        Material(
            id = "s_inconel_625", name = "Nickel alloy, solution treated", group = IsoGroup.S, family = Family.NICKEL,
            examples = "Inconel 625, 600, Monel 400, Hastelloy C-276", hardness = "≈200 HB", hardnessHb = 200.0,
            kc11 = 2900.0, mc = 0.25, vcCarbide = 25.0, vcHss = 6.0, fzPerD = 0.0032,
            drillFnPerD = 0.011, tapVc = 3.0, turnVc = 50.0, frictionAngle = 31.0,
            formTappable = true, machinability = 15,
        ),
        Material(
            id = "s_cobalt", name = "Cobalt-chrome alloy", group = IsoGroup.S, family = Family.NICKEL,
            examples = "CoCrMo (F75/F1537), Stellite 6", hardness = "≈35 HRC", hardnessHb = 330.0,
            kc11 = 3100.0, mc = 0.25, vcCarbide = 20.0, vcHss = 0.0, fzPerD = 0.0028,
            drillFnPerD = 0.010, tapVc = 2.0, turnVc = 35.0, frictionAngle = 31.0,
            formTappable = false, machinability = 10,
        ),
        // ------------------------------------------------------------------ H: hardened
        Material(
            id = "h_45_50", name = "Hardened steel 45–50 HRC", group = IsoGroup.H, family = Family.HARDENED,
            examples = "4140/4340 hardened, H13 47 HRC, 1.2343", hardness = "45–50 HRC", hardnessHb = 460.0,
            kc11 = 3000.0, mc = 0.25, vcCarbide = 50.0, vcHss = 0.0, fzPerD = 0.0030,
            drillFnPerD = 0.010, tapVc = 3.0, turnVc = 80.0, frictionAngle = 31.0,
            formTappable = false, machinability = 15,
            notes = "Use AlTiN/AlCrN/TiSiN coated carbide; tapping only with carbide taps.",
        ),
        Material(
            id = "h_50_55", name = "Hardened steel 50–55 HRC", group = IsoGroup.H, family = Family.HARDENED,
            examples = "S7, A2 hardened, 1.2363", hardness = "50–55 HRC", hardnessHb = 520.0,
            kc11 = 3500.0, mc = 0.25, vcCarbide = 40.0, vcHss = 0.0, fzPerD = 0.0025,
            drillFnPerD = 0.008, tapVc = 2.0, turnVc = 50.0, frictionAngle = 32.0,
            formTappable = false, machinability = 10,
        ),
        Material(
            id = "h_55_60", name = "Hardened steel 55–60 HRC", group = IsoGroup.H, family = Family.HARDENED,
            examples = "D2, A2, O1, 52100 hardened", hardness = "55–60 HRC", hardnessHb = 600.0,
            kc11 = 4000.0, mc = 0.25, vcCarbide = 30.0, vcHss = 0.0, fzPerD = 0.0020,
            drillFnPerD = 0.006, tapVc = 0.0, turnVc = 35.0, frictionAngle = 32.0,
            formTappable = false, machinability = 7,
            notes = "Thread-mill instead of tapping. CBN preferred for turning.",
        ),
        Material(
            id = "h_60_65", name = "Hardened steel 60–65 HRC", group = IsoGroup.H, family = Family.HARDENED,
            examples = "M2, D2 62 HRC, PM tool steels, chilled cast iron", hardness = "60–65 HRC", hardnessHb = 680.0,
            kc11 = 4600.0, mc = 0.25, vcCarbide = 22.0, vcHss = 0.0, fzPerD = 0.0015,
            drillFnPerD = 0.005, tapVc = 0.0, turnVc = 25.0, frictionAngle = 33.0,
            formTappable = false, machinability = 5,
            notes = "Hard milling: small radial steps, very rigid setup, nano-composite coated tools.",
        ),
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): Material? = byId[id]

    val default: Material get() = byId.getValue("p_low_carbon")

    fun byGroup(group: IsoGroup): List<Material> = all.filter { it.group == group }

    /** Case-insensitive search over name, examples and family. */
    fun search(query: String, group: IsoGroup? = null): List<Material> {
        val q = query.trim().lowercase()
        return all.filter { m ->
            (group == null || m.group == group) &&
                (q.isEmpty() || m.name.lowercase().contains(q) || m.examples.lowercase().contains(q) ||
                    m.family.label.lowercase().contains(q))
        }
    }
}
