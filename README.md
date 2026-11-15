# Sillon

**Français** · [English](README.en.md)

[![Build](https://github.com/Pilmil-Studio/sillon/actions/workflows/android.yml/badge.svg)](https://github.com/Pilmil-Studio/sillon/actions/workflows/android.yml)
[![License: MPL 2.0](https://img.shields.io/badge/License-MPL_2.0-brightgreen.svg)](LICENSE)
[![Offline](https://img.shields.io/badge/Network-100%25%20Offline-orange.svg)](app/src/main/AndroidManifest.xml)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-brightgreen.svg)](app/build.gradle.kts)

Application de **potager vivant & calendrier agronomique** (Android & iOS via Kotlin Multiplatform / Compose Multiplatform), 100 % hors-ligne, libre, gratuite, sans publicité, sans inscription et sans la moindre collecte de données. Fait partie de la suite d'applications d'intérêt général **Pilmil Studio** (*Sauve*, *Sommelle*, *Lunaison*, *Respire*, *Fredonne*).

Sillon est un compagnon de potager qui fonctionne sans réseau. Il vous dit quoi semer, repiquer ou récolter ces quinze prochains jours, d'après la date des dernières gelées chez vous. Il dessine vos parcelles (potager, balcon ou intérieur), signale les plantes qui s'entraident ou se gênent, vous rappelle de changer de famille de légumes d'une année à l'autre, et vous aide à reconnaître un mal sur une feuille à partir d'une photo, sans que la photo quitte le téléphone.

Gratuit, sans compte, sans publicité, et il ne demande même pas l'autorisation d'accéder à Internet. L'interface est pour l'instant en français uniquement.

**Pour les curieux (partie technique).** Les fenêtres de culture sont calculées par rapport à la date médiane de dernière gelée ($J_{\text{gel}}$) plutôt que par mois fixes, la détection des voisinages utilise les relations d'allélopathie documentées, la rotation suit un cycle de 4 ans et la reconnaissance de feuille s'appuie sur des modèles embarqués. Le détail est plus bas.

---

## Captures d'écran

<p align="center"><img src="docs/captures/s1.webp" width="150" alt="Travaux de la quinzaine"> <img src="docs/captures/s2.webp" width="150" alt="Plan du potager"> <img src="docs/captures/s3.webp" width="150" alt="Encyclopédie des plantes"> <img src="docs/captures/s4.webp" width="150" alt="Fiche d'une plante"> <img src="docs/captures/s5.webp" width="150" alt="Docteur du potager"> <img src="docs/captures/s6.webp" width="150" alt="Réglages"></p>

## Ce que fait l'application

### 1. Calendrier agronomique dynamique indexé sur les gelées ($J_{\text{gel}}$)
- **Fini les calendriers rigides :** Les dates fixes (« semer les tomates en mars ») sont inadaptées aux écarts thermiques régionaux. Sillon asservit toutes les fenêtres de culture à la **date médiane des dernières gelées printanières** ($J_{\text{gel}}$), garantissant une cohérence thermique réelle.
- **105 territoires français pré-configurés :** Base climatique intégrée (`FrenchFrostCatalog`) issue des normales trentenaires (101 départements + territoires d'outre-mer en climat **tropical**), personnalisable au jour près pour épouser votre microclimat d'ubac, de versant ou d'altitude.
- **Guidage hebdomadaire bienveillant :** Écran « Aujourd'hui » avec prévisions balayables, semis sous abri/au chaud, semis directs en pleine terre, repiquages et récoltes actifs pour la quinzaine ($\pm 7$ jours), plus **rappels hebdomadaires de semis** facultatifs.

### 2. Planificateur visuel de parcelles, balcons & intérieur
- **Conception modulaire :** Créez vos carrés potagers surélevés (3x3, 4x4), planches en lignes, **jardinières de balcon ou pots d'intérieur** (avec estimation de la luminosité via `LightEstimator` et capteur de lux), avec bascule vue à plat / perspective (`GardenProjection`, `GardenScene`) et icônes vectorielles CC0.
- **Densités adaptées :** Calcul automatique des densités au pied carré (*Square Foot Gardening* : 1, 4, 9 ou 16 plants/case) ou des écartements maraîchers traditionnels en centimètres.
- **Un potager qui pousse :** chaque case montre le stade de sa culture (installation, jeune plant, en croissance, bientôt prête, à récolter) avec une jauge et le délai avant la récolte, calculés à partir des durées du catalogue. Le stade est toujours écrit et énoncé par les lecteurs d'écran, jamais porté par l'image seule.

### 3. Moteur d'allélopathie & compagnonnage végétal (`CompanionEngine`)
- **74 légumes, herbes aromatiques et fleurs compagnes** minutieusement modélisés (`VegetableCatalog`) avec leur famille botanique, besoins hydriques, exposition solaire et seuils thermiques de germination.
- **Graphe relationnel spatial en 2D :** Analyse automatique des cases adjacentes (contiguïté orthogonale et diagonale par distance de Tchebychev).
- **Synergies documentées :** Détection de la confusion olfactive Alliacées/Apiacées (*Psila rosae* et *Acrolepiopsis assectella*), des effets nématicides racinaires de l'alpha-terthiényle des *Tagetes*, et de l'optimisation racinaire stratifiée.
- **Alertes biologiques d'antagonisme :** Signalement immédiat des incompatibilités majeures (vecteurs épidémiques croisés comme le mildiou entre tomate et pomme de terre, allélopathie inhibitrice du fenouil ou du noyer).

### 4. Suivi pluriannuel des rotations culturales (`RotationEngine`)
- **Cycle agro-écologique sur 4 ans :**
  1. *Année 1 :* Cultures gourmandes très exigeantes (Solanacées, Cucurbitacées, Brassicacées) sur sol fraîchement amendé.
  2. *Année 2 :* Cultures moyennement exigeantes et racines pivotantes (Apiacées, Alliacées, Chénopodiacées).
  3. *Année 3 :* Légumineuses améliorantes fixatrices d'azote atmosphérique par symbiose rhizobienne (Fabacées).
  4. *Année 4 :* Couverts vivants, engrais verts régénérateurs (phacélie, moutarde, trèfle) et repos structuré du sol.
- **Prévention sanitaire tellurique :** Détection des récurrences d'une même famille sur un délai inférieur à la pause requise (3 à 5 ans) pour briser le cycle des sclérotes, nématodes et champignons terricoles (*Plasmodiophora*, *Pythium*, *Rhizoctonia*).

### 5. Guide de santé végétale & Edge Vision 100 % hors-ligne (`DiagnosticEngine`)
- **Diagnostic différentiel dichotomique :** Analyse pas-à-pas des symptômes selon les organes touchés (feuille, tige, collet, fruit, racine) sur 21 maladies et ravageurs courants.
- **Diagnostic photo sur l'appareil (aide indicative, en amélioration continue) :** la photo est analysée sur le téléphone, rien n'est envoyé. Le modèle (`sillon_disease.tflite`, 3,2 Mo, images de 288 × 288 px) distingue 13 classes : feuille saine, hors sujet, « autre » et 10 maladies ou ravageurs (mildiou, oïdium, alternariose, rouille, botrytis, pucerons, altises, limaces, piéride du chou, doryphore). Sous un seuil de confiance de 65 %, l'appli répond qu'elle ne reconnaît rien avec assez de certitude plutôt que de deviner. Mildiou et alternariose se ressemblent : l'appli les présente ensemble comme « tache foliaire » et renvoie vers les deux fiches.
- **Précision mesurée :** sur 1 182 photos de terrain mises de côté (non utilisées à l'entraînement), le modèle donne la bonne classe dans 86 % des cas (intervalle de confiance à 95 % : 84 à 88 %). Au seuil de 65 %, l'appli répond pour environ 86 % des photos, et 93 % de ces réponses sont justes. C'est plus faible pour mildiou et alternariose (précision d'environ 49 % et 40 %). Ces chiffres viennent de `sillon_disease.json` et ne garantissent rien sur vos propres photos : c'est une aide, pas un diagnostic certain, à recouper avec la clé de diagnostic de l'écran. Le modèle est entraîné de nouveau régulièrement (pipeline dans [`tools/ml`](tools/ml/README.md)).
- **Viseur et pré-cadrage :** un viseur intelligent (`SmartCameraViewfinder`, `FrameQualityAnalyzer`) contrôle netteté et part de végétal, et un détecteur de feuille (`sillon_detector.tflite`, `LeafDetector`) cadre la photo avant analyse (`PhotoClassifier`, lecture sur deux vues). Moteur d'inférence : LiteRT.
- **Remèdes de biocontrôle :** Préconisations strictes en biocontrôle respectant le cahier des charges de l'Agriculture Biologique (décoction de prêle pour sa silice renforçant la cuticule, bicarbonate de potassium à action osmotique contre l'oïdium, cuivre raisonné plafonné à moins de 4 g/m²/an).

---

## Les Engagements Éthiques et Techniques Pilmil Studio

Comme l'ensemble des créations de **Pilmil Studio**, Sillon est conçu selon une charte éthique :

- **Zéro permission INTERNET :** Aucune autorisation réseau n'est déclarée dans le manifeste (`AndroidManifest.xml`). L'application est structurellement incapable d'émettre ou de recevoir le moindre paquet sur le réseau.
- **0 compte, 0 mot de passe, 0 serveur :** Aucune authentification requise, aucun écran d'onboarding verbeux, aucun profilage utilisateur.
- **0 traceur, 0 publicité, 0 télémétrie :** Aucun kit d'analyse (Google Analytics, Firebase, etc.) n'est embarqué dans le code binaire.
- **Données souveraines & formats ouverts :** Votre potager vous appartient. Les données sont enregistrées localement dans un fichier JSON clair et accessible (`sillon_garden.json`).
- **Sauvegarde & Restauration :** Exportation et réimportation en **coffre chiffré `.pilmil`** (AES-256-GCM via `pilmil-core`), en JSON et au format tableur CSV (compatible LibreOffice Calc et Microsoft Excel) via le Storage Access Framework (SAF) d'Android.
- **Copie dans le compte Google, à votre choix :** désactivée par défaut. Sillon n'envoie rien et n'a aucun serveur. Si vous l'activez dans les réglages (Android 12 ou plus, avec un verrouillage d'écran), Android copie le potager dans votre compte Google, chiffré de bout en bout avec le code de votre téléphone : Google ne peut pas le lire, et il faudra le code de l'ancien téléphone pour restaurer. Le transfert direct vers un nouveau téléphone et l'export JSON et CSV enregistré où vous voulez restent toujours possibles. Ces fichiers ne sont pas chiffrés.
- **Bien commun libre :** Publié sous licence [Mozilla Public License 2.0](LICENSE) (MPL-2.0).

---

## Architecture Logicielle

Le projet s'appuie sur Kotlin Multiplatform / Compose Multiplatform (`:shared`), une coque Android (`:app`), une coque iOS (`iosApp/`) et le sous-module `pilmil-core` :

```
shared/src/commonMain/kotlin/fr/pilmil/sillon/
├── data/
│   ├── DiseaseCatalog.kt         # Base des 21 maladies et ravageurs
│   ├── FrenchFrostCatalog.kt     # Normales climatiques des 105 territoires (tempéré + tropical)
│   ├── GardenCodec.kt / Store    # Sérialisation et persistance locale multiplateforme
│   └── VegetableCatalog.kt       # Référentiel des 74 espèces maraîchères et compagnes
├── engine/
│   ├── CompanionEngine.kt        # Graphe relationnel & calcul spatial d'allélopathie
│   ├── DashboardPlanner.kt       # Planification hebdomadaire des travaux
│   ├── DiagnosticEngine.kt       # Moteur d'inférence de diagnostic différentiel
│   ├── FrostEngine.kt            # Phénologie dynamique indexée sur J_gel / saison
│   ├── GrowthEngine.kt           # Stades de croissance et délai avant récolte
│   ├── LightEstimator.kt         # Estimation d'ensoleillement (balcon / intérieur)
│   └── RotationEngine.kt         # Gestion de la fatigue des sols & cycle quadriennal
├── model/                        # BotanicalFamily, Climate, CompanionRelation, Disease, GardenModels, Vegetable
└── ui/                           # Onboarding, Dashboard, Garden (GardenProjection/Scene), Catalog, Doctor, Settings

app/src/main/java/fr/pilmil/sillon/
├── backup/                       # BackupPolicy & SillonBackupAgent
├── data/GardenRepository.kt      # Coffre .pilmil (AndroidBackupVault) & exports SAF/CSV
├── reminders/SowingReminders.kt  # Rappels hebdomadaires WorkManager
└── vision/                       # LeafDetector, PhotoClassifier, FrameQualityAnalyzer (LiteRT)

iosApp/                           # Coque Xcode / SwiftUI iOS (SillonApp.swift)
```

### Spécifications techniques
- **Langage & Plateformes :** Kotlin Multiplatform 2.0+, Compose Multiplatform (`androidTarget`, `iosX64`, `iosArm64`, `iosSimulatorArm64`).
- **Interface :** Material 3 (thème clair/sombre) et ~60 icônes vectorielles CC0.
- **Typographie :** *Fraunces* (titrages agronomiques chaleureux) et *Nunito* (lisibilité optimale en plein soleil).
- **Cible Android :** `compileSdk 36`, `targetSdk 36`, `minSdk 26`. Permissions : `POST_NOTIFICATIONS` et `CAMERA` (optionnelles), zéro `INTERNET`.
- **Moteurs purs sans dépendance plateforme :** Les moteurs de calcul agronomique sont découplés d'Android/iOS pour garantir une testabilité unitaire totale et des performances instantanées.

---

## Compilation et Tests

Le projet se compile directement avec Gradle et le JDK 17 standard.

### Exécuter la suite de tests unitaires
Les tests automatisés vérifient l'intégralité des moteurs agronomiques (phénologie, matrices de compagnonnage, règles de rotation, vision chromatique) :

```bash
./gradlew testDebugUnitTest
```

### Générer l'APK de développement
Pour compiler et empaqueter l'application Android :

```bash
./gradlew assembleDebug
```
L'exécutable généré sera disponible dans `app/build/outputs/apk/debug/app-debug.apk`.

---

## Fondements Scientifiques

Pour consulter la revue bibliographique complète des mécanismes allélopathiques (sécrétions d'alpha-terthiényle, confusion olfactive par COV, biochimie de la nitrogénase chez les Fabacées, toxicité de la juglone), l'explication mathématique des Degrés Jours de Croissance ($DJC$) et les protocoles de biocontrôle (silice de prêle, bicarbonate de potassium), référez-vous au dossier scientifique :

**[Consulter les Fondements Scientifiques de Sillon (docs/scientific-foundations.md)](docs/scientific-foundations.md)**

---

## La suite Pilmil Studio

Sillon s'inscrit dans la démarche des applications d'intérêt général de **Pilmil Studio** :

- **[Sauve](https://github.com/Pilmil-Studio/sauve) :** Premiers secours d'urgence « Anti-Panique » 100 % hors-ligne guidés par la voix.
- **[Sommelle](https://github.com/Pilmil-Studio/sommelle) :** Accompagnement de l'insomnie par la TCC-I sans compte ni publicité.
- **[Lunaison](https://github.com/Pilmil-Studio/lunaison) :** Suivi intime du cycle menstruel respectueux de la vie privée.
- **[Respire](https://github.com/Pilmil-Studio/respire) :** Déconnexion numérique et micro-pauses respiratoires sereines.
- **[Fredonne](https://github.com/Pilmil-Studio/fredonne) :** Saisie mélodique à une voix, lecture acoustique et transposition hors-ligne.

---

## Licence

Sillon est un logiciel libre distribué sous licence **[Mozilla Public License 2.0](LICENSE)** (MPL-2.0) : vous pouvez l'utiliser, l'étudier, le modifier et le redistribuer ; les fichiers que vous modifiez doivent rester publiés sous la même licence, avec leur code source.

---

## Contact & Contribution

Questions agronomiques, retours de terrain et contributions :  
**Pilmil Studio** : [pilmil.studio@proton.me](mailto:pilmil.studio@proton.me)
