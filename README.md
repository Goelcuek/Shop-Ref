# Shop Ref — machining reference & feeds/speeds calculator (Android)

An offline Android app in the spirit of FSWizard / HSMAdvisor: feeds and speeds for milling, drilling,
reaming, tapping, thread milling and turning, with **cutting-force, torque, power and tool-deflection
predictions built on published cutting-mechanics models**, plus a shop reference library (tap drills,
drill sizes, clearance holes, counterbores, ISO fits, hardness, surface finish, unit conversion).

## Features

**Calculators** (metric or inch, switchable at any time)

| Calculator | Inputs | Outputs |
|---|---|---|
| Milling | 40+ materials (ISO P/M/K/N/S/H), HSS / HSS-Co / PM-HSS / carbide / ceramic / PCD, 11 coatings, square / ball / bull-nose / chamfer / indexable cutters, slotting / side milling / HEM-adaptive / finishing / facing, ap, ae, climb/conventional, stick-out, helix, rake, wear | rpm, feed, chip load with radial + axial chip thinning, MRR, power vs. machine limit, torque, **peak & mean cutting forces (Fx, Fy, Fz, \|Fxy\|) with a force-vs-rotation chart**, tool deflection, bending & torsional stress, ramp/plunge/helix feeds, scallop/cusp height, Taylor tool-life impact of speed overrides |
| Drilling | twist, split-point, solid carbide (with/without coolant), indexable, spot drill, reamer; depth, pre-drilled hole | rpm, feed, torque, thrust (lips + chisel edge), power, MRR, cycle time, peck strategy, spot/countersink depth |
| Tapping | metric coarse/fine, UNC/UNF/UNEF, BSPP; cut / spiral point / spiral flute / form taps; chamfer form; substrate & coating; blind/through | rpm, feed, recommended tap drill for target % thread (cut and form), achieved %, minor-Ø limits (6H/2B), **tapping torque vs. estimated tap breaking torque**, minimum drill depth, holder/clutch setting; thread-milling feeds with arc compensation |
| Turning | insert grade (coated/uncoated carbide, cermet, ceramic, CBN, PCD, HSS), roughing/medium/finishing, nose radius, entering angle | rpm, feed rate, Fc/Ff/Fp, power, torque, MRR, theoretical Ra/Rt, feed for a target Ra |

**Reference:** tap-drill charts (cut & form), thread-engagement calculator, number/letter/fractional/metric drill
chart with "nearest drill", ISO 273 & inch clearance holes, SHCS counterbores, flat-head countersinks, ISO 286
fits calculator (H/G/F/E/D/JS/K/M/N/P/R/S holes, d–s shafts, IT4–IT13), hardness conversion, surface-finish
grades, quick rpm/feed calculators, unit converter, full material data, and every formula with its source.

**Machine profiles:** max/min rpm, max feed, spindle power, torque limit (constant-torque region),
efficiency and rigidity class for the mill and the lathe — recommendations are clamped and checked against them.

**Chip-load level** (milling): *Conservative* uses cautious catalogue starting values (e.g. 0.0020" per tooth on a
½" end mill in pre-hardened 4140), *Standard* typical catalogue values, *Aggressive / HEM* high-end catalogue and
HEM practice (≈0.0035" before chip thinning on the same tool). Speed and chip-load overrides go from 50% to 200%.

## Cutting-force models and sources

All calculations live in the pure-Kotlin [`core`](core) module and are unit tested against closed-form results.

* **Specific cutting force** — Kienzle law `kc = kc1.1·h^−mc`, `Fc = kc1.1·b·h^(1−mc)` (Kienzle 1952;
  Kienzle & Victor 1957), rake correction `(1 − γ/100)` and wear factor (Sandvik Coromant). Material constants
  are typical values from the König & Essel (1973) tables and the Sandvik CMC/ISO material tables.
* **Milling forces** — time-domain mechanistic model (Altintas 2012; Tlusty & MacNeil 1975): chip thickness
  `h = fz·sin φ·sin κ` (Martellotti 1941), helix lag `2·z·tan β/D`, summed over teeth, axial slices and one
  revolution. Radial/axial force ratios come from the **oblique-cutting transformation** of Budak, Altintas &
  Armarego (1996, *J. Manuf. Sci. Eng.* 118:216) with Stabler's chip-flow rule; for zero helix this reduces to
  Merchant's `tan(β − α)`. The numerical mean force is checked against Altintas' closed form in the tests.
* **Deflection** — stepped cantilever (shank + fluted section), fluted section as an equivalent cylinder of
  0.8·D (Kops & Vo 1990, *CIRP Annals* 39(1):93), integrated with the unit-load method.
* **Drilling** — per-lip Kienzle forces: `Mc = kc·fn·(D² − d0²)/8000` N·m; thrust = lip thrust × (1 + chisel-edge
  factor) (Shaw & Oxford 1957; chisel edge ≈ 50–70 % of thrust for un-thinned webs).
* **Tapping** — energy balance over the thread groove, `M = kc·A·dm/2`, which reproduces the tap-maker rule
  `M ≈ kc·P²·D/8000`; process factors for land friction and chip packing in blind holes (Cao & Sutherland 2002,
  *IJMTM* 42:1527; Armarego & Chen 2002). Tap strength from torsion of the core.
* **Tool life** — Taylor `v·Tⁿ = C` (Taylor 1907).
* **Standards** — ISO 286 (fits), ISO 965-1 (6H minor Ø), ASME B1.1 (2B minor Ø), ASME B1.20.1 (NPT),
  ISO 228-1 (G), ISO 273 (clearance holes), ASTM E140 (hardness), ISO 513 (material groups).

The full list with page references is in the app under *Reference → Formulas & references* and in
[`Citations.kt`](core/src/main/kotlin/com/shopref/core/reference/Citations.kt).

> Results are starting points. Material constants vary with heat lot and tool geometry; the force model assumes
> a rigid, sharp tool without runout or chatter. Always check your tool manufacturer's data.

## Getting the APK

Every push runs the **Android CI** workflow (`.github/workflows/android.yml`):

1. `core-tests` — JVM unit tests of the calculation engine.
2. `build-apk` — builds `shop-ref-debug.apk` and `shop-ref-release.apk` (minified, signed with the debug key
   unless a release keystore is provided) and uploads them as the **`shop-ref-apk`** workflow artifact.
3. `ui-smoke-test` — runs instrumented Compose tests on an Android 11 emulator that open every screen.

Download the artifact from the workflow run, unzip, and install `shop-ref-release.apk` on the phone (allow
"install unknown apps").

To publish a GitHub Release with the APKs attached, either push a tag such as `v1.0.0`, or run the
**Android CI** workflow manually (*Actions → Android CI → Run workflow*) and enter the tag in `release_tag`;
the release step creates the tag on the selected commit.

To sign releases with your own key, set `SHOPREF_KEYSTORE_PATH`, `SHOPREF_KEYSTORE_PASSWORD`,
`SHOPREF_KEY_ALIAS` and `SHOPREF_KEY_PASSWORD` in the build environment.

## Building locally

Requirements: JDK 17+, Android SDK (API 35). Android Studio Ladybug or newer opens the project directly.

```bash
./gradlew -p core test          # calculation engine tests (no Android SDK needed)
./gradlew :app:assembleDebug    # APK in app/build/outputs/apk/debug/
./gradlew :app:connectedDebugAndroidTest   # UI smoke tests on a device/emulator
```

## Project layout

```
core/   Kotlin/JVM library: materials, tooling, calculators, reference data, citations (+ tests)
app/    Android app (Jetpack Compose, Material 3), minSdk 26
```
