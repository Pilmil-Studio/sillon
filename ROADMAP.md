# Feuille de route — Sillon

> Dernière mise à jour : novembre 2026. Voir aussi `pilmil-prive/projets/sillon/feuille-de-route.md`.

## 1. Fonctionnalités à venir
- **v1.0 (socle & validation)** :
  - Évaluation du modèle embarqué (14 classes, 1,1 Mo) sur plusieurs centaines de vraies photos de jardin, avec publication honnête du taux de réussite réel.
  - Première release étiquetée sur GitHub et fiche Google Play / F-Droid.
- **v1.5 (mémoire du jardin & culture urbaine)** :
  - Historique pluriannuel des cultures par emplacement avec alertes de rotation.
  - Conseils d'arrosage tenant compte du paillage et de la météo saisie (100 % hors-ligne).
  - Intégration de variétés anciennes et semences reproductibles dans le catalogue.
  - Modes « Balcon » (pots, orientation, vent) et « Intérieur » (calendrier sans gelée, estimation de la lumière) pour les personnes en appartement — voir [`specs/balcon-interieur.md`](specs/balcon-interieur.md).
  - Modèle photo v5 déployé (`288 px`, précision terrain `0.861` sur `n = 1182`, `ECE 0.029`, `93,0 %` de précision à seuil `0.65`, TTA 2 vues + regroupement `mildiou`/`alternariose` et pré-cadrage YOLO v2 `sillon_detector.tflite`).
  - Prochain entraînement du modèle photo : élargir le jeu **terrain** (hors fond uni) et porter les effectifs de test au-delà de `60–100` images pour `mildiou` (`n = 49`), `alternariose` (`n = 28`), `rouille` (`n = 57`) et `autre` (`n = 61`), renforcer `sain` contre les faux positifs `oidium`/`hors_sujet`, et améliorer `sillon_detector.tflite` (actuellement `mAP@50 = 0.362` en test) — voir [`specs/modele-diagnostic-ia.md`](specs/modele-diagnostic-ia.md) et [`tools/ml/README.md`](tools/ml/README.md#pistes-damélioration-pour-le-prochain-entraînement-v15).
- **v2.0 (horizon 2028, sous réserve de partenariat/financement)** :
  - Détection d'objets en direct caméra (type YOLO nano quantifié TFLite/ONNX) après constitution d'un jeu d'images annotées.

## 2. Aspect visuel & UX
- Amélioration de l'aide au cadrage photo (indication claire netteté / exposition / part de végétal).
- Tests de captures d'écran sur les fiches cultures, le plan du potager et le mode nuit.

## 3. Qualité de code & sécurité
- Sauvegarde Android configurable par application (`-Ppilmil.allowAutoBackup=false` par défaut, avec `data_extraction_rules.xml` et `backup_rules.xml`).
- Exécution systématique de `testDebugUnitTest` et `:app:lintDebug` en CI.
- À faire : ajouter `ktlint` / `detekt` et brancher `pilmil-core` (`BackupVault` `.pilmil`) pour les exports de plans de jardin.

## 4. Feuille de route iOS
- **État actuel** : 100 % Android (`VegetableCatalog` et `DiseaseCatalog` sont déjà en Kotlin pur).
- **Étapes** :
  1. Migrer les catalogues et la logique de calendrier/rotation vers un module KMP `commonMain`.
  2. Porter l'interface en Compose Multiplatform avec `PHPickerViewController` pour les photos.
  3. Publier une v1 iOS sans diagnostic photo, puis étudier le pont Core ML / LiteRT pour une v1.1.
