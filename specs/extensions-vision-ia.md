# Spécification technique — Extensions vision IA v2 (`sillon`)

> Statut : Spécification de référence (15 novembre 2026).
> Complément à [`specs/modele-diagnostic-ia.md`](./modele-diagnostic-ia.md).
> Objectif : Étendre le diagnostic photo statique vers deux usages terrain complémentaires, **100 % hors-ligne** et sans aucune permission réseau.

---

## 1. Extension A — Détection en direct dans le viseur (*Sillon YOLO v2.0*)

### 1.1 Problème résolu
Un classifieur d'image entière (`224×224` ou `288×288`) souffre dès que la feuille malade n'occupe qu'une petite fraction du cadre ou que le fond (terre, paillis, tuteurs, autres plantes) domine. Une détection par boîtes englobantes en direct résout ce problème à la source :
- L'utilisateur balaie simplement son rang de potager avec la caméra ;
- Des cadres s'affichent en temps réel autour des zones suspectes (feuilles tachées, colonies de pucerons, doryphores, limaces) ;
- La capture ou l'analyse détaillée est recadrée automatiquement sur la boîte détectée.

### 1.2 Architecture cible
- **Modèle** : détecteur temps réel léger quantifié INT8 (`YOLOv8n` / `YOLO11n` ou `EfficientDet-Lite0` en TFLite, cible < 4 Mo, ≥ 10–15 FPS sur smartphone milieu de gamme via LiteRT + délégué GPU/NNAPI optionnel).
- **Pipeline hybride possible en transition** :
  1. Détecteur générique 2 classes (`feuille_symptome`, `ravageur`) ou multi-classes direct ;
  2. Envoi du crop recadré vers le classifieur haute précision `sillon_disease.tflite` pour confirmation calibrée.
- **Données d'entraînement** :
  - **PlantDoc Object Detection** (`CC BY 4.0`, boîtes annotées sur photos de terrain) ;
  - **PlantSeg** (`weitianqi/plantseg`, autorisation écrite du 9 nov. 2026 — les masques de segmentation permettent de dériver automatiquement des boîtes englobantes précises sur les lésions foliaires !).

---

## 2. Extension B — Reconnaissance de jeunes pousses / plantules (« Semis ou adventice ? »)

### 2.1 Problème résolu
Au printemps, les jardiniers débutants confondent fréquemment leurs jeunes semis (carotte, radis, laitue, persil, betterave, épinard) avec les adventices qui lèvent en même temps (chénopode, mouron des oiseaux, pissenlit, liseron, amarante) et hésitent avant de désherber.

### 2.2 Périmètre et conception
- **Classes cibles (~30 à 40 classes)** :
  - **Jeunes pousses potagères** issues du `VegetableCatalog` de Sillon (stade cotylédons + 2 premières vraies feuilles) ;
  - **10 à 15 adventices fréquentes** des potagers européens ;
  - Classe de rejet **`incertain / autre_plante`** pour éviter toute affirmation hasardeuse sur deux cotylédons génériques.
- **Sources de données libres compatibles (`CC0`, `CC-BY`, `CC-BY-SA`)** :
  - **iNaturalist Open Data** et **GBIF** (observations de plantules / semis jeunes de chaque espèce) ;
  - **V2 Plant Seedlings Dataset** (Aarhus University / Southern Denmark — vérifier la licence exacte avant toute ingestion ; couvre déjà plusieurs adventices et cultures européennes).
- **Intégration UX** : bascule simple dans l'écran *Docteur / Identifier* (« Maladie ou ravageur » vs « Jeune pousse ou mauvaise herbe ? »), avec renvoi vers la fiche légume si la plantule est reconnue comme une culture du catalogue.

---

## 3. Contraintes Pilmil Studio
- Aucune donnée image ne quitte le téléphone ;
- Citations académiques et `ATTRIBUTIONS.csv` maintenus pour tout jeu utilisé à l'entraînement ;
- Évaluation obligatoire sur photos de terrain jamais vues avec découpage par observateur et intervalles de confiance de Wilson à 95 %.
