# Sillon

[Français](README.md) · **English**

[![Build](https://github.com/Pilmil-Studio/sillon/actions/workflows/android.yml/badge.svg)](https://github.com/Pilmil-Studio/sillon/actions/workflows/android.yml)
[![License: MPL 2.0](https://img.shields.io/badge/License-MPL_2.0-brightgreen.svg)](LICENSE)
[![Offline](https://img.shields.io/badge/Network-100%25%20Offline-orange.svg)](app/src/main/AndroidManifest.xml)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-brightgreen.svg)](app/build.gradle.kts)

A **living vegetable garden & agronomic planning** application (Android & iOS via Kotlin Multiplatform / Compose Multiplatform), 100% offline, free, open-source, ad-free, with no account, no tracking, and zero internet permission. Part of the **Pilmil Studio** public interest suite (*Sauve*, *Sommelle*, *Lunaison*, *Respire*, *Fredonne*).

Sillon is a vegetable-garden companion that works without a network. It tells you what to sow, transplant or harvest over the next two weeks, based on the last frost date where you live. It draws your beds (garden, balcony or indoors), flags plants that help or hinder each other, reminds you to rotate vegetable families from one year to the next, and helps you recognise a problem on a leaf from a photo, without the photo ever leaving your phone.

Free, no account, no ads, and it does not even ask for permission to use the internet. The interface is currently available in French only.

**For the curious (technical part).** Growing windows are computed from the median last-frost date ($J_{\text{frost}}$) instead of fixed months, neighbour checks use documented allelopathy relations, rotation follows a 4-year cycle, and leaf recognition relies on bundled on-device models. Details below.

## What sets Sillon apart

- **Offline.** The calendar, the plan, companion planting, rotation, the encyclopedia and the diagnosis work without a connection. The Android manifest only declares camera and notification permissions, and no Internet permission.
- **No account.** No sign-up or login. The garden is saved in a file on the phone.
- **Calendar set to the last frost.** Sowing and planting dates are calculated from the median last-frost date of your department, not from fixed months. These are medians: a given year may differ. Limits are detailed in [`docs/limites-des-donnees.md`](docs/limites-des-donnees.md).
- **On-device photo diagnosis.** The photo of a leaf is analyzed on the phone, then deleted. If the model is not sure enough, it says so. The result remains an aid, not a certain diagnosis.

Google account backup is an option, disabled by default. It is described in "Ethical & Technical Commitments (Pilmil Studio)" below.

---

## Screenshots

<p align="center"><img src="docs/captures/s1.webp" width="150" alt="Fortnight tasks"> <img src="docs/captures/s2.webp" width="150" alt="Garden plan"> <img src="docs/captures/s3.webp" width="150" alt="Plant encyclopedia"> <img src="docs/captures/s4.webp" width="150" alt="Plant sheet"> <img src="docs/captures/s5.webp" width="150" alt="Garden doctor"> <img src="docs/captures/s6.webp" width="150" alt="Settings"></p>

## What the App Does

### 1. Dynamic Phenological Calendar Indexed to Last Frost ($J_{\text{frost}}$)
- **Beyond rigid calendar months:** Rigid guidelines like "sow tomatoes in March" fail to account for altitude and microclimates. Sillon shifts every indoor sowing, direct seeding, transplanting, and harvest window relative to the **median date of the last spring frost** ($J_{\text{frost}}$).
- **105 pre-configured French territories:** Integrated 30-year climatological normal database (`FrenchFrostCatalog`, covering 101 departments + overseas territories in **tropical** climate), customizable down to the exact day to fit local microclimates (hillside, sheltered valley, urban heat island).
- **Weekly gardening dashboard:** "Today" screen with swipeable forecasts, indoor seed starting, direct ground sowing, transplanting, and active harvest windows for the current fortnight ($\pm 7$ day tolerance), plus optional **weekly sowing reminders**.

### 2. Visual Garden Bed, Balcony & Indoor Planner
- **Modular layouts:** Configure raised square beds (3x3, 4x4), traditional in-ground row plots, **balcony planters or indoor pots** (with light estimation via `LightEstimator` and lux sensor), flat/perspective view toggle (`GardenProjection`, `GardenScene`), and CC0 vector icons.
- **Tailored densities:** Automatic calculation of Square Foot Gardening densities (1, 4, 9, or 16 plants per slot) or standard centimeter spacing between plants and rows.
- **A garden that grows:** each square shows the stage of its crop (settling in, young plant, growing, almost ready, ready to harvest) with a gauge and the time left before harvest, computed from the catalogue durations. The stage is always written and announced by screen readers, never carried by the picture alone.

### 3. Allelopathy & Companion Planting Engine (`CompanionEngine`)
- **74 modeled vegetable crops, aromatic herbs, and companion flowers** (`VegetableCatalog`) with botanical families, water requirements, sun exposure, and germination temperature thresholds.
- **2D spatial relational graph:** Automated evaluation of neighboring cells (orthogonal and diagonal adjacency using Chebyshev distance).
- **Documented synergies:** Detection of olfactory pest masking between Alliums and Apiaceae (*Psila rosae* vs. *Acrolepiopsis assectella*), nematicidal root exudates ($\alpha$-terthienyl from *Tagetes*), and root depth stratification.
- **Biological antagonism alerts:** Immediate warning for critical conflicts (shared blight vectors between tomatoes and potatoes, allelopathic growth inhibition from fennel or walnut).

### 4. 4-Year Crop Rotation Engine (`RotationEngine`)
- **Agroecological 4-year cycle:**
  1. *Year 1:* Heavy feeders (Solanaceae, Cucurbitaceae, Brassicaceae) on richly amended compost soil.
  2. *Year 2:* Medium feeders & deep taproots (Apiaceae, Alliums, Chenopodiaceae).
  3. *Year 3:* Atmospheric nitrogen-fixing legumes via symbiotic Rhizobium nodulation (Fabaceae).
  4. *Year 4:* Living cover crops, green manures (phacelia, white mustard, clover), and soil rest.
- **Soil-borne pest breaking:** Identifies family replanting violations within a 3- to 5-year exclusion window to break the lifecycle of resting spores, root-knot nematodes, and fungal pathogens (*Plasmodiophora*, *Pythium*, *Rhizoctonia*).

### 5. Plant Health Doctor & 100% Offline Edge Vision (`DiagnosticEngine`)
- **Dichotomous differential diagnosis:** Step-by-step symptomatic triage across affected organs (leaf, stem, fruit, collar, roots) covering 21 common garden diseases and pests.
- **On-device photo diagnosis (an indicative aid, continuously improving):** the photo is analysed on the phone and nothing is sent. The model (`sillon_disease.tflite`, 3.2 MB, 288 × 288 px input) tells 13 classes apart: healthy leaf, off-topic, "other" and 10 diseases or pests (downy mildew, powdery mildew, early blight, rust, grey mould, aphids, flea beetles, slugs, cabbage white, Colorado beetle). Below a 65% confidence threshold the app says it cannot recognise anything with enough certainty instead of guessing. Downy mildew and early blight look alike: the app shows them together as "leaf spot" and points to both sheets.
- **Measured accuracy:** on 1,182 held-out field photos (not used for training) the model returns the right class 86% of the time (95% confidence interval: 84 to 88%). At the 65% threshold the app answers on about 86% of photos, and 93% of those answers are correct. It is weaker for downy mildew and early blight (precision around 49% and 40%). These figures come from `sillon_disease.json` and promise nothing about your own photos: it is an aid, not a certain diagnosis, to be cross-checked with the in-app diagnostic key. The model is retrained regularly (pipeline in [`tools/ml`](tools/ml/README.md)).
- **Viewfinder and pre-framing:** a smart viewfinder (`SmartCameraViewfinder`, `FrameQualityAnalyzer`) checks sharpness and plant share, and a leaf detector (`sillon_detector.tflite`, `LeafDetector`) frames the photo before analysis (`PhotoClassifier`, two-view reading). Inference engine: LiteRT.
- **Biocontrol remedies:** Strictly organic biocontrol treatments (horsetail silica decoction to strengthen leaf cuticles, potassium bicarbonate for osmotic powdery mildew lysis, and copper strictly capped below 4 g/m²/year).

---

## Ethical & Technical Commitments (Pilmil Studio)

Like all applications created by **Pilmil Studio**, Sillon follows a strict ethical charter:

- **Zero INTERNET permission:** No network permissions declared in `AndroidManifest.xml`. The app is technically and structurally incapable of connecting to the internet.
- **0 accounts, 0 passwords, 0 servers:** No login walls, no onboarding delays, no personal profiling.
- **0 trackers, 0 telemetry, 0 ads:** Completely free of tracking SDKs (no Firebase, no Google Analytics, no third-party ad libraries).
- **Sovereign, open data:** Your garden data belongs exclusively to you. Stored locally in human-readable JSON (`sillon_garden.json`).
- **Backup & restore:** Export and import as an **encrypted `.pilmil` vault** (AES-256-GCM via `pilmil-core`), JSON, and CSV spreadsheet format via Android's native Storage Access Framework (SAF).
- **Copy to your Google account, your choice:** off by default. Sillon sends nothing and has no server. If you turn it on in settings (Android 12 or later, with a screen lock), Android copies the garden to your Google account, end-to-end encrypted with your phone's lock code: Google cannot read it, and the old phone's code is needed to restore. Direct transfer to a new phone and a JSON and CSV export saved wherever you want always remain possible. Those files are not encrypted.
- **Free & Open Source:** Published under the [Mozilla Public License 2.0](LICENSE) (MPL-2.0).

---

## Software Architecture

Built using Kotlin Multiplatform / Compose Multiplatform (`:shared`), an Android shell (`:app`), an iOS shell (`iosApp/`), and the `pilmil-core` submodule:

```
shared/src/commonMain/kotlin/fr/pilmil/sillon/
├── data/
│   ├── DiseaseCatalog.kt         # Catalog of 21 garden diseases and pests
│   ├── FrenchFrostCatalog.kt     # Climate normals for 105 territories (temperate + tropical)
│   ├── GardenCodec.kt / Store    # Cross-platform serialization and local persistence
│   └── VegetableCatalog.kt       # Scientific profiles for 74 garden crops
├── engine/
│   ├── CompanionEngine.kt        # Relational graph & spatial allelopathy evaluator
│   ├── DashboardPlanner.kt       # Weekly task planner
│   ├── DiagnosticEngine.kt       # Rule-based differential diagnostic engine
│   ├── FrostEngine.kt            # Dynamic phenology indexed to J_frost / season
│   ├── GrowthEngine.kt           # Growth stages & days-to-harvest engine
│   ├── LightEstimator.kt         # Light estimation for balcony & indoor growing
│   └── RotationEngine.kt         # Soil fatigue prevention & 4-year cycle engine
├── model/                        # BotanicalFamily, Climate, CompanionRelation, Disease, GardenModels, Vegetable
└── ui/                           # Onboarding, Dashboard, Garden (GardenProjection/Scene), Catalog, Doctor, Settings

app/src/main/java/fr/pilmil/sillon/
├── backup/                       # BackupPolicy & SillonBackupAgent
├── data/GardenRepository.kt      # .pilmil vault (AndroidBackupVault) & SAF/CSV exporters
├── reminders/SowingReminders.kt  # WorkManager weekly reminders
└── vision/                       # LeafDetector, PhotoClassifier, FrameQualityAnalyzer (LiteRT)

iosApp/                           # Xcode / SwiftUI iOS shell (SillonApp.swift)
```

### Technical specifications
- **Language & Targets:** Kotlin Multiplatform 2.0+, Compose Multiplatform (`androidTarget`, `iosX64`, `iosArm64`, `iosSimulatorArm64`).
- **User Interface:** Material 3 (light & dark themes) and ~60 CC0 vector icons.
- **Typography:** *Fraunces* (warm serif titles) and *Nunito* (sunlight-readable clean sans-serif).
- **Android Target:** `compileSdk 36`, `targetSdk 36`, `minSdk 26` (Android 8.0+). Permissions: optional `POST_NOTIFICATIONS` and `CAMERA`, zero `INTERNET`.
- **Pure Kotlin engines:** Calculation engines are decoupled from Android/iOS platform classes for fast unit testing and deterministic execution.

---

## Building and Testing

Build the project using standard Gradle and JDK 17.

### Run unit tests
Executes tests covering all agronomic engines (phenology, companion matrix, rotation rules, and chrominance classification):

```bash
./gradlew testDebugUnitTest
```

### Build debug APK
Compiles and packages the debug APK:

```bash
./gradlew assembleDebug
```
The output APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Scientific Foundations

To read the in-depth literature review of allelopathic interactions (alpha-terthienyl exudation, VOC olfactory masking, Rhizobium nitrogenase biochemistry, juglone cytotoxicity), mathematical models of Growing Degree Days (GDD), and organic biocontrol protocols (horsetail silica, potassium bicarbonate), read the scientific dossier:

**[Read the Scientific Foundations of Sillon (docs/scientific-foundations.md)](docs/scientific-foundations.md)**

---

## The Pilmil Studio Suite

Sillon is part of the digital commons initiative by **Pilmil Studio**:

- **[Sauve](https://github.com/Pilmil-Studio/sauve):** 100% offline anti-panic first aid with voice and haptic metronome guidance.
- **[Sommelle](https://github.com/Pilmil-Studio/sommelle):** Evidence-based CBT-I sleep companion without accounts or ads.
- **[Lunaison](https://github.com/Pilmil-Studio/lunaison):** Private, offline menstrual cycle tracking with medical guides.
- **[Respire](https://github.com/Pilmil-Studio/respire):** Mindful breathing pauses before reflex app usage.

---

## License

Sillon is free software distributed under the **[Mozilla Public License 2.0](LICENSE)** (MPL-2.0): you can use, study, modify and redistribute it; files you modify must stay under the same license, with their source code.

---

## Contact & Feedback

Agronomic questions, field feedback, and code contributions:  
**Pilmil Studio**: [pilmil.studio@proton.me](mailto:pilmil.studio@proton.me)
