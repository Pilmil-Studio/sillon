# Feuille de route : Sillon

> Dernière mise à jour : novembre 2026. Voir aussi `pilmil-prive/projets/sillon/feuille-de-route.md`.

## 1. Déjà livré
- Calendrier calé sur la date de dernière gelée, plan des parcelles (vue à plat et en perspective), compagnonnage, rotation sur 4 ans, historique par emplacement.
- Modes « Balcon » et « Intérieur » (pots, orientation, vent, estimation de la lumière) et climat tropical pour l'outre-mer, voir [`specs/balcon-interieur.md`](specs/balcon-interieur.md).
- Conseils d'arrosage tenant compte du paillage et de la météo saisie (100 % hors-ligne).
- Sauvegarde chiffrée `.pilmil`, export JSON et CSV.
- Diagnostic photo sur l'appareil : modèle v5 (288 px, 13 classes), précision de 0,861 sur 1 182 photos de terrain, `ECE 0.029`, 93,0 % de réponses justes au seuil 0,65, lecture sur deux vues, regroupement `mildiou`/`alternariose` et pré-cadrage `sillon_detector.tflite`. Le modèle est présenté comme une aide, en amélioration continue.
- Versions étiquetées sur GitHub : v1.0.0 (14 novembre 2026), v1.0.1 (14 novembre), v1.0.2 et v1.0.3 (15 novembre).
- Sauvegarde Android configurable par application (`-Ppilmil.allowAutoBackup=false` par défaut, avec `data_extraction_rules.xml` et `backup_rules.xml`).
- Export `.pilmil` branché sur `pilmil-core` (`AndroidBackupVault`).
- Diagnostic depuis le potager : « Il a un problème » sur un plant ouvre le diagnostic avec la plante, sa famille, sa date et la saison (causes réordonnées, aucune retirée) ; journal des problèmes par carré (schéma de données 3, migration testée) visible dans l'historique de l'emplacement ; rappel « Revérifier dans N jours » puis « Ça va mieux ? » ; avertissement de rotation l'année suivante pour les maladies qui persistent dans le sol (mildiou, alternariose, durées sourcées dans `docs/scientific-foundations.md` section 4.3) ; plant discrètement abîmé dans la scène tant que le problème est ouvert, sans pénalité.
- Diagnostic photo : a priori de plausibilité de la plante quand le diagnostic part d'une plantation, et deux questions pour départager mildiou et alternariose.
- Tests unitaires et `:app:lintDebug` exécutés en CI, analyse statique Detekt (avec baselines) et couverture Kover.

## 2. À venir
- **Publication sur les boutiques** : la release GitHub est faite. L'envoi vers Google Play n'a lieu que si le secret `PLAY_SERVICE_ACCOUNT_JSON` est configuré, et aucune fiche F-Droid n'est référencée dans le dépôt.
- **Interface multilingue** : français, anglais, allemand, espagnol, italien et néerlandais, avec saisie manuelle de la date de dernière gelée hors de France.
- **Modèle photo** : élargir le jeu **terrain** (hors fond uni) et porter les effectifs de test au-delà de `60` à `100` images pour `mildiou` (`n = 49`), `alternariose` (`n = 28`), `rouille` (`n = 57`) et `autre` (`n = 61`), renforcer `sain` contre les faux positifs `oidium`/`hors_sujet`, et améliorer `sillon_detector.tflite` (actuellement `mAP@50 = 0.362` en test), voir [`specs/modele-diagnostic-ia.md`](specs/modele-diagnostic-ia.md) et [`tools/ml/README.md`](tools/ml/README.md#pistes-damélioration-pour-le-prochain-entraînement-v15).
- **Persistance dans le sol** : durées sourcées seulement pour le mildiou et l'alternariose ; à sourcer pour l'anthracnose, la fonte des semis, le botrytis et la mouche de la carotte (`TODO(sources)` dans `DiseaseCatalog.kt`), et ajouter la hernie du chou au catalogue.
- **Modèle photo, a priori de la plante** : lancer `tools/ml/eval_host_prior.py` sur le test de terrain (images non incluses au dépôt) avant de toucher au seuil.
- **Piste : don de photos volontaire** (non implémenté) : après un diagnostic confirmé (« Ça va mieux » ou diagnostic validé), proposer une option explicite pour conserver la photo et l'envoyer soi-même (fichier exporté avec photo, culture, diagnostic confirmé et date, sans position ni EXIF, sans serveur), désactivée par défaut, avec consentement et licence (CC0 ou CC-BY) choisis par l'utilisateur, pour enrichir le jeu de terrain, surtout mildiou, alternariose et sain. Voir [`specs/modele-diagnostic-ia.md`](specs/modele-diagnostic-ia.md).
- **Catalogue** : variétés anciennes et semences reproductibles.
- **v2.0 (horizon 2028, sous réserve de partenariat/financement)** : détection d'objets en direct caméra (type YOLO nano quantifié TFLite/ONNX) après constitution d'un jeu d'images annotées.

## 3. Aspect visuel & UX
- Amélioration de l'aide au cadrage photo (indication claire netteté / exposition / part de végétal).
- Tests de captures d'écran sur les fiches cultures, le plan du potager et le mode nuit.

## 4. Qualité de code & sécurité
- Reste à faire : ajouter `ktlint`. Detekt et Kover sont en place (voir section 1).

## 5. Feuille de route iOS
- **État actuel** : catalogues, moteurs et interface sont en Kotlin / Compose Multiplatform (`:shared`) et une coque iOS existe (`iosApp/`). Elle n'a pas encore été publiée ni éprouvée sur appareil.
- **Étapes** :
  1. Valider la coque iOS sur appareil (persistance, sélecteur de photos, rappels).
  2. Publier une v1 iOS sans diagnostic photo.
  3. Étudier le pont Core ML / LiteRT pour y apporter le diagnostic photo.
