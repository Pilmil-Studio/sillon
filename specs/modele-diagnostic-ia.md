# Fiche technique du modèle de diagnostic photo (`sillon_disease`)

> Dernière mise à jour : 14 novembre 2026.
> Fichiers de référence : [`app/src/main/assets/models/sillon_disease.json`](../app/src/main/assets/models/sillon_disease.json), [`tools/ml/train_sillon.py`](../tools/ml/train_sillon.py), [`tools/ml/README.md`](../tools/ml/README.md).

---

## 1. Sources des données et licences

Le modèle embarqué est entraîné exclusivement sur des sources ouvertes redistribuables, avec séparation stricte entre photos de laboratoire (utilisées uniquement comme régularisateur d'entraînement) et photos de terrain (seules utilisées pour évaluer le modèle).

| Source | Volume retenu | Nature des prises de vue | Licence | Usage dans le pipeline |
|---|---:|---|---|---|
| **PlantVillage** (Hughes & Salathé 2015 ; Mohanty 2016) | 9 965 images | Feuilles isolées sur fond uni en laboratoire (tomate, pomme de terre, poivron, courge, fraisier) | CC-BY 3.0 | Entraînement et validation labo uniquement (jamais dans le test terrain) |
| **PlantDoc** (Singh et al., CoDS-COMAD 2020) | 1 326 images | Photos *in situ* en plein champ, ombres et arrière-plans complexes | CC-BY 4.0 | Entraînement + jeu de test terrain officiel jamais vu |
| **iNaturalist Open Data** (`tools/ml/collect_data.py`) | 5 134 images | Observations citoyennes validées (« Research Grade »), ravageurs, maladies et adventices | CC0, CC-BY, CC-BY-SA uniquement | Entraînement, validation et test terrain (découpage par observateur) |

- **Traçabilité** : chaque photo iNaturalist est répertoriée avec son auteur, son identifiant d'observation et sa licence exacte dans [`tools/ml/ATTRIBUTIONS.csv`](../tools/ml/ATTRIBUTIONS.csv).
- **Prévention des fuites de données (data leakage)** : toutes les photos d'un même observateur iNaturalist (`u<user_id>_inat<obs_id>.jpg`) sont placées dans le même sous-ensemble (entraînement, validation ou test) afin qu'aucune série de photos d'un même jardin ne se retrouve des deux côtés.

### 1.1 Audit de compatibilité des autres sources externes (novembre 2026)

Seules les licences **CC0**, **CC-BY**, **CC-BY-SA** et les sources couvertes par une **autorisation écrite explicite des auteurs** (`author-permission-nc`) sont acceptées par `expand_dataset.py`.

#### A. Sources externes sous licence libre (`CC0` / `CC-BY 4.0` / `CC-BY-SA 4.0`) à intégrer pour la v1.5

| Source / Jeu de données | Identifiant / DOI | Licence vérifiée | Classes Sillon ciblées | Intérêt technique |
|---|---|---|---|---|
| **Pl@ntNet via API GBIF** | `publishingOrg = da86174a-a605-43a4-a5e8-53d484152cd3` (`mediaType=StillImage`) | **CC-BY 4.0 / CC-BY-SA 4.0** *(filtrage par média)* | `sain` (aromatiques, salades, légumes en pot) | Des milliers de photos citoyennes de feuillages sains d'aromatiques et légumes-feuilles en conditions réelles |
| **Observation.org via API GBIF** | API GBIF `/v1/occurrence/search` (`license=CC_BY_4_0,CC0_1_0`) | **CC0 / CC-BY 4.0 / CC-BY-SA 4.0** *(filtrage par média)* | `mildiou`, `alternariose`, `rouille`, `botrytis`, `tetranyques`, `cochenilles` | Très forte couverture européenne en mycologie et entomologie de terrain |
| **Potato Leaf (Healthy & Late Blight)** | Mendeley Data — [`10.17632/zfv4jj7855`](https://doi.org/10.17632/zfv4jj7855) | **CC BY 4.0** | `mildiou`, `sain` | Photos de terrain non contrôlées en plein champ (Holeta) avec arrière-plans naturels |
| **Field-Based Detection of Potato Blight in Complex Backgrounds** | Mendeley Data — [`10.17632/pbnw43s6kt.1`](https://doi.org/10.17632/pbnw43s6kt.1) | **CC BY 4.0** | `mildiou`, `alternariose` | Feuilles au champ sur fonds complexes, utile pour réduire la confusion mildiou/alternariose |
| **Pota-Toma-To leaf disease images** *(sous-dossier RGB brut uniquement)* | Mendeley Data — [`10.17632/354fsxwccb.1`](https://doi.org/10.17632/354fsxwccb.1) | **CC BY 4.0** | `mildiou`, `alternariose`, `sain` | 435 images tomate et pomme de terre *(exclure les variantes pré-traitées DWT/CLAHE)* |
| **Potato disease classification** | Mendeley Data — [`10.17632/ttgtfp3vjb.1`](https://doi.org/10.17632/ttgtfp3vjb.1) | **CC BY 4.0** | `mildiou`, `alternariose` | Complément sur les maladies fongiques des Solanacées |

#### B. Sources autorisées par accord écrit des auteurs (évaluation privée & entraînement des poids, sans redistribution des images)

Par courriel du **9 novembre 2026**, **Dr Tianqi Wei** (`tianqi.wei@uq.edu.au`, en copie **Pr Helen Huang** `helen.huang@uq.edu.au`, The University of Queensland) a accordé à **Pilmil Studio** (Quentin Noilou) l'autorisation explicite d'utiliser **PlantWild** et **PlantSeg** pour :
1. L'évaluation privée du modèle de Sillon ;
2. L'entraînement d'un classifieur sur des sous-ensembles sélectionnés (notamment tomate, pomme de terre et cultures potagères) et la **distribution des poids du modèle résultant** (`sillon_disease.tflite`) au sein de l'application sous licence MPL-2.0.

| Source / Jeu de données | Identifiant / Dépôt | Régime d'autorisation | Conditions strictes d'utilisation |
|---|---|---|---|
| **PlantWild** (Wei et al., ECCV 2024) | GitHub `tqwei05/MVPDR`, Kaggle `uqtwei2/PlantWild` | **Accord écrit des auteurs (9 nov. 2026)** (`author-permission-nc`) | Usage **strictement non commercial** ; **aucune redistribution des images brutes** dans le dépôt Git ou l'APK (seuls les poids `.tflite` sont distribués) ; **citation obligatoire** du jeu de données et de l'article dans l'application et sa documentation |
| **PlantSeg** (Wei et al., CIKM 2024) | GitHub `tqwei05/PlantSeg`, Zenodo [`10.5281/zenodo.13924591`](https://doi.org/10.5281/zenodo.13924591) (**CC BY-NC 4.0** sur la dernière version Zenodo) | **CC BY-NC 4.0 + Accord écrit des auteurs (9 nov. 2026)** (`author-permission-nc`) | Idem : usage **strictement non commercial**, **aucune redistribution des images brutes**, et **citation obligatoire** du jeu de données et de l'article |

##### Références bibliographiques à citer dans l'application et la documentation dès intégration des poids
- **PlantWild** : Tianqi Wei, Zane Chen, Xin Yu, Scott Chapman, Paul Melloy, Zi Huang, *"MVPDR: Multi-Modal Visual Prompt for Wild Plant Disease Recognition"*, European Conference on Computer Vision (ECCV), 2024.
- **PlantSeg** : Tianqi Wei, Trench Chen, Xin Yu, Scott Chapman, Paul Melloy, Zi Huang, *"PlantSeg: A Large-Scale In-the-Wild Dataset for Plant Disease Segmentation"*, Proceedings of the 33rd ACM International Conference on Information and Knowledge Management (CIKM), 2024. DOI Zenodo : `10.5281/zenodo.13924591`.

##### Trace de l'accord écrit (9–10 novembre 2026)
> **De** : Tianqi Wei `<tianqi.wei@uq.edu.au>` — **À** : Quentin N. `<quentin.noilou@gmail.com>` — **Cc** : Helen Huang `<helen.huang@uq.edu.au>`
> **Date** : Lun. 9 nov. 2026 à 23:47 (UTC+1) / Mar. 10 nov. 2026 à 08:47 (AEST)
> **Objet** : *Re: Request for permission to use PlantSeg and PlantWild in a free, open-source gardening app*
>
> *"Dear Quentin, Your proposed use for private evaluation and training on selected subsets, including the distribution of resulting model weights, should be acceptable, provided that all activities remain strictly non-commercial and both datasets and their associated papers are properly cited. For PlantSeg, please refer to the licence of the latest version of the dataset. Best regards, Tianqi Wei"*

#### C. Sources écartées ou restreintes après vérification des licences

| Source étudiée | Licence constatée | Décision pour Sillon |
|---|---|---|
| **Pl@ntNet-300K** (archive Zenodo complète) | Métadonnées CC-BY 4.0, mais images individuelles mixtes (`cc-by-nc`, `cc-by-nc-sa`) | **Interdit en ingestion brute** : n'utiliser que les images filtrées via `plantnet300K_metadata.csv` (`license in {cc-by, cc-by-sa, cc0}`) ou via l'API GBIF |
| **Plant Pathology 2020 (FGVC7)** | CC BY 4.0 | **Hors périmètre** (centré sur le pommier, non prioritaire pour le potager) |

---

## 2. Architecture et protocole d'entraînement

Script reproductible : [`tools/ml/train_sillon.py`](../tools/ml/train_sillon.py) (et notebook GPU [`tools/ml/sillon_kaggle.ipynb`](../tools/ml/sillon_kaggle.ipynb)).

- **Architecture** : `MobileNetV3-Small` pré-entraîné sur ImageNet.
- **Entrée** : tenseur `1 × 224 × 224 × 3` (`NHWC`, `RGB`, valeurs `float32` dans `[0, 255]`).
- **Sortie** : vecteur de probabilités calibrées sur **13 classes** (10 affections du catalogue + `sain` + `autre` + `hors_sujet`).
- **Quantification** : quantification dynamique 8 bits des poids à l'export TFLite (`sillon_disease.tflite`, ~1,1 Mo), exécutée 100 % hors-ligne sur le téléphone.
- **Pondération et régularisation** :
  - Pondération par classe (`class_weight`) pour compenser les déséquilibres d'effectifs.
  - Sur-pondération **×3** des photos de terrain par rapport aux photos de laboratoire.
  - `label_smoothing = 0.1`.
  - Entraînement en deux phases : tête de classification seule (base gelée), puis fine-tuning avec arrêt anticipé (`EarlyStopping`).
- **Calibration et seuil de rejet** :
  - Calibration post-entraînement par température (`temperature = 0.7`) optimisée sur la validation terrain.
  - Seuil de confiance retenu : **`threshold = 0.65`** (couverture de **75,5 %** sur le test de terrain). En dessous de `0.65`, ou si la classe gagnante est `autre` / `hors_sujet`, l'application renvoie `PhotoVerdict.Unsure` et oriente vers la clé de diagnostic et le triage contextuel.

---

## 3. Résultats mesurés (modèle v1.0 du 8 novembre 2026)

### 3.1 Synthèse globale (`sillon_disease.json`)

| Jeu d'évaluation | Effectif (`n`) | Exactitude (`accuracy`) | Macro-F1 | Erreur de calibration (`ECE`) | Couverture au seuil `0.65` |
|---|---:|---:|---:|---:|---:|
| **Test de terrain** (référence réelle) | **1 244** | **75,0 %** | **0,676** | **0,067** | **75,5 %** |
| Validation laboratoire (PlantVillage) | 1 514 | 96,6 % | 0,967 | 0,028 | — |

> L'écart entre le score laboratoire (`96,6 %`) et le score terrain (`75,0 %`) illustre pourquoi Sillon refuse de publier des métriques calculées sur PlantVillage.

### 3.2 Détail par classe sur le test de terrain (`n = 1 244`)

| Classe | Fiche `DiseaseCatalog` | Précision | Rappel | Score F1 | Effectif test (`n`) | Diagnostic technique |
|---|---|---:|---:|---:|---:|---|
| `doryphore` | `doryphore` | 88,9 % | 98,2 % | 0,933 | 114 | Très solide |
| `altises` | `altises` | 94,7 % | 91,9 % | 0,932 | 135 | Très solide (criblure caractéristique) |
| `limaces` | `limaces` | 88,5 % | 95,2 % | 0,917 | 145 | Très solide |
| `pucerons` | `pucerons` | 81,5 % | 85,4 % | 0,834 | 103 | Solide |
| `pieride_chou` | `pieride_chou` | 73,9 % | 87,2 % | 0,800 | 94 | Solide |
| `botrytis` | `botrytis` | 66,7 % | 86,6 % | 0,753 | 67 | Bon rappel, quelques faux positifs |
| `oidium` | `oidium` | 77,4 % | 63,1 % | 0,695 | 141 | Correct (feutrage blanc franc) |
| `rouille` | `rouille` | 63,9 % | 73,0 % | 0,681 | 63 | Correct mais effectif d'entraînement limité |
| `hors_sujet` | *(aucune — rejet)* | 57,7 % | 80,3 % | 0,671 | 117 | Filtre bien les adventices/auxiliaires |
| `autre` | *(aucune — rejet)* | 46,2 % | 82,4 % | 0,592 | 51 | Absorbe les pathologies hors catalogue |
| **`sain`** | *(aucune — plante saine)* | **87,0 %** | **32,9 %** | **0,477** | **143** | **Très fiable quand il répond (87 %), mais trop prudent (rappel 32,9 %)** |
| **`mildiou`** | `mildiou` | **52,9 %** | **18,4 %** | **0,273** | **49** | **Insuffisant sur terrain (n < 60, confondu avec `alternariose` / `autre`)** |
| **`alternariose`** | `alternariose` | **30,8 %** | **18,2 %** | **0,229** | **22** | **Insuffisant sur terrain (n = 22 trop faible)** |

---

## 4. Ce qu'il faut faire pour améliorer le modèle (feuille de route v1.5)

### Chantier 1 — Remonter le rappel de la classe `sain` (cible : rappel ≥ 65 % sans perdre la précision > 85 %)
- **Cause actuelle** : la classe `sain` ne contient que 5 cultures de plein champ (tomate, pomme de terre, poivron, courge, fraisier). Sur37 images saines du test terrain, le modèle prédit `hors_sujet`, et sur 17 autres il prédit `autre`.
- **Action** : enrichir `sain` via `expand_dataset.py` (iNaturalist + GBIF, licences CC0/CC-BY/CC-BY-SA) avec des feuillages sains d'aromatiques et légumes-feuilles cultivés en pot / intérieur (basilic, menthe, persil, ciboulette, coriandre, laitue, roquette, radis, épinard) photographiés sous différents éclairages (rebord de fenêtre, intérieur).

### Chantier 2 — Résoudre le goulot d'étranglement `mildiou` / `alternariose`
- **Cause actuelle** : trop peu d'images de terrain dans PlantDoc (`n = 49` et `n = 22` au test) et forte ressemblance visuelle des nécroses brunes à basse résolution (`224×224`).
- **Actions** :
  1. Collecter 150 à 300 observations terrain supplémentaires par classe via `python expand_dataset.py --gbif --out extra_gbif --per-class 150` (*Phytophthora infestans* et *Alternaria solani*), complétées par les sous-ensembles tomate/pomme de terre de **PlantWild** et **PlantSeg** (autorisés par accord écrit du Dr Tianqi Wei le 9 nov. 2026 via `--default-license author-permission-nc`, sans redistribution des images brutes).
  2. Exécuter le comparatif Kaggle (`sillon_kaggle.ipynb`) avec `--recipe improved --backbone large --img-size 288`.
  3. Si la distinction fine entre mildiou et alternariose reste sous 50 % de rappel sur le terrain, activer `--merge-leaf-spots` (classe fusionnée `tache_feuille` avec `group_members: ["mildiou", "alternariose"]`) et mettre à jour `PhotoModelSpec.ModelClass` pour proposer conjointement les deux fiches à l'utilisateur.

### Chantier 3 — Couvrir les ravageurs d'intérieur et de balcon (`tetranyques`, `cochenilles`)
- **Action** : ajouter dans `collect_data.py` les taxons *Tetranychus urticae* (`tetranyques`) et *Pseudococcidae / Coccidae* (`cochenilles`) en ciblant les observations montrant le feuillage ou la tige attaquée (et non des spécimens isolés au microscope), avec un minimum de `n = 60` photos de test par observateur distinct.

### Chantier 4 — Gérer les stress abiotiques (manque d'eau, excès d'eau, manque de lumière) par approche hybride
- **Règle méthodologique** : ne **jamais** entraîner deux classes visuelles distinctes « manque d'eau » et « excès d'eau » dans le réseau de neurones, car toutes deux se manifestent par un flétrissement/jaunissement foliaire indiscernable sans connaître l'humidité du substrat.
- **Ce qui est déjà fait côté application** : lorsque le modèle renvoie `PhotoVerdict.Unsure`, `PhotoDiagnosisCard` propose un triage en 2 clics (`SubstrateMoisture` + `AbioticSymptomHint` via `PhotoDiagnosis.resolveAbioticTriage`) qui oriente vers les fiches `manque_eau`, `exces_eau_racines`, `etiolement`, `moucherons_terreau` ou `tetranyques`.
- **Prochaine étape côté modèle** : évaluer l'ajout d'une classe visuelle `etiolement` (semis filants à entre-nœuds allongés) et d'une classe groupée `fletrissement_abiotique` (`catalogId = null`, déclenchant automatiquement la question sur l'humidité du terreau dans l'interface).

## 5. Contexte de la plante : a priori, départage mildiou / alternariose, évaluation

### 5.1 A priori de plausibilité de l'hôte (diagnostic lancé depuis une plantation)
- Quand la photo est analysée depuis un carré, `PhotoDiagnosis.applyHostPrior` multiplie la probabilité de chaque classe
  qui a une fiche par `HostPlausibility.plausibility` : 1 si la plante est dans les `targetFamilies` de la fiche, si la fiche n'en
  a pas, ou si la cause est à large spectre (oïdium, rouille, botrytis, pucerons, limaces) ; **0,05** sinon (jamais zéro). Puis le vecteur
  est renormalisé. `sain`, `autre`, `hors_sujet` ne sont pas pondérées.
- Sans contexte (médecin autonome), le comportement est inchangé. Le seuil de confiance (`0.65`) n'a pas été recalé : il a été
  calibré sans a priori. Le plancher de 0,05 est un choix éditorial, pas une valeur ajustée sur des mesures.
- L'effet réel n'est pas mesuré faute d'images de terrain dans le dépôt. `tools/ml/eval_host_prior.py` est écrit pour le faire (voir
  `tools/ml/README.md`) ; aucun résultat n'est annoncé ici.

### 5.2 Départage mildiou / alternariose par deux questions
- Déclenchement : verdict `tache_feuille` (classe fusionnée, ses `group_members` donnent les deux pistes à parts égales) ou
  mildiou et alternariose en deux premières pistes à moins de `0.15` d'écart.
- Questions : duvet blanc grisâtre au revers ou taches d'aspect huileux (oriente vers le mildiou) ; taches brunes à anneaux
  concentriques en cible (oriente vers l'alternariose). Réponses oui, non, je ne sais pas. L'appli conseille aussi de photographier
  le revers de la feuille.
- Mise à jour : le poids de la maladie visée par la question est multiplié par 3 pour « oui », divisé par 1,5 pour « non », inchangé
  pour « je ne sais pas » ; les deux poids sont renormalisés sur leur masse initiale, donc les autres pistes ne bougent pas. La maladie visée
  par chaque question est déduite des symptômes des fiches par `DiagnosticEngine`.
- Les facteurs 3 et 1,5 sont des choix éditoriaux, non étalonnés sur des données ; ils ne servent qu'à ordonner deux pistes
  proposées à l'utilisateur, qui garde les deux fiches.

### 5.3 Piste : don de photos volontaire (non implémenté)
À étudier, rien n'est codé. Après un diagnostic confirmé (« Ça va mieux » ou diagnostic validé), proposer une option explicite,
désactivée par défaut, pour conserver la photo et l'envoyer soi-même : fichier exporté contenant la photo, la culture, le diagnostic
confirmé et la date, sans position ni métadonnées EXIF, sans serveur. L'utilisateur choisit la licence (CC0 ou CC-BY) et consent
explicitement. But : enrichir le jeu de terrain, surtout `mildiou`, `alternariose` et `sain`. Aujourd'hui la photo est analysée puis supprimée,
et ne va jamais dans le journal.
