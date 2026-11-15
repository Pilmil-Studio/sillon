# Architecture & Choix Techniques — Sillon

## 1. Structure du projet et modules

Sillon suit une architecture **Kotlin Multiplatform (KMP)** avec interface Compose Multiplatform :
- `:app` (`fr.pilmil.sillon`) : point d'entrée Android, caméra (`CameraX`), inférence LiteRT, accès fichiers/SAF, notifications et `AndroidBackupVault`.
- `:shared` (`commonMain`, `androidMain`, `iosMain`) : catalogues agronomiques (`VegetableCatalog`), moteurs de rotation (`RotationEngine`), calcul de semis/climat/altitude, projection isométrique (`GardenProjection`, `GardenScene`, `PlantArt`) et UI Compose Multiplatform.
- `pilmil-core` : design system (`:core-designsystem`) et coffre-fort chiffré `.pilmil` (`:core-platform`).

---

## 2. Manifeste, permissions et confidentialité

Dans `AndroidManifest.xml` :
- **Permissions déclarées** :
  - `POST_NOTIFICATIONS` : rappel hebdomadaire optionnel du jardin.
  - `CAMERA` (`android:required="false"`) : diagnostic photo local ; l'application reste pleinement utilisable sur les appareils sans caméra.
- **Aucune permission `INTERNET`** : zéro requête réseau, zéro téléchargement de modèle, zéro télémétrie.
- **`android:allowBackup="false"`** par défaut et `FileProvider` pour le partage local d'exports sans accès global au stockage externe.

---

## 3. Choix techniques vérifiés dans le code

### Persistance atomique (`GardenStore`)
- Le potager chargé reste en mémoire et sert de base aux modifications. Fichier absent : potager de départ créé ; fichier corrompu : mise de côté et avis à l'utilisateur ; erreur de lecture passagère ou version de fichier plus récente que l'appli : aucune écriture, modifications refusées jusqu'à une lecture réussie. Le JSON porte un champ `schemaVersion` (absent = version 1).
- L'état du jardin et l'historique pluriannuel des parcelles sont sérialisés en JSON localement.
- Écriture sécurisée en 3 temps pour éviter toute corruption en cas de coupure : écriture dans `sillon_garden.json.tmp`, synchronisation disque (`fd.sync()`), puis renommage atomique (`ATOMIC_MOVE`).
- En cas de fichier illisible, mise en quarantaine automatique sous `.corrupt-<timestamp>` plutôt que suppression silencieuse.

### Sauvegarde chiffrée `.pilmil`
- Intégration de `AndroidBackupVault` (`pilmil-core`) : compression GZIP, dérivation de clé par **PBKDF2-HMAC-SHA256 (600 000 itérations)** et chiffrement **AES-256-GCM**.

### Diagnostic photo embarqué (LiteRT 1.4.0)
- Modèle embarqué : `sillon_disease.tflite` (3 327 384 octets), tenseur d'entrée `[1, 288, 288, 3]`, **13 classes** (10 maladies ou ravageurs, plus `sain`, `autre` et `hors_sujet`) définies dans `sillon_disease.json`, seuil de confiance minimal fixé à **0,65**, lecture sur deux vues (image et son miroir). Un détecteur de feuille (`sillon_detector.tflite`, `LeafDetector`) cadre la photo avant l'analyse.
- Viseur intelligent (`SmartCameraViewfinder`) : analyse temps réel à ~30 fps de la netteté (variance du Laplacien) et de la dominance végétale (indice **ExG** — *Excess Green*) avant capture.
- Si le modèle ne peut pas être chargé, la carte de diagnostic photo n'est pas affichée (`PhotoClassifier.isAvailable`) et la clé de diagnostic reste disponible.

### Rendu isométrique multiplateforme (`GardenScene` / `PlantArt`)
- Dessin vectoriel procédural sur `Canvas` Compose Multiplatform (aucune dépendance OpenGL/Unity), permettant un partage identique entre Android et iOS.
