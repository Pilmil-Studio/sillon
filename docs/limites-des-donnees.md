# Limites des données de Sillon

Ce document dit ce que les données de l'appli permettent d'affirmer et ce qu'elles ne permettent pas. Il décrit l'état du dépôt à la révision `ef00dec` (branche `audit-novembre`, 16 novembre 2026). Le code Kotlin n'a pas été modifié pour l'écrire.

## 0. Points à traiter en priorité

**Crédits des photos CC BY absents de l'appli et du site.** Le fichier `tools/ml/ATTRIBUTIONS.csv` liste 5 134 photos, dont 3 757 sous licence CC BY. Aucun écran de l'appli (réglages compris), aucune page de `docs/` et aucun README ne reprend ces crédits. Le fichier ne se trouve que dans le dépôt : il n'est pas embarqué dans l'APK. La licence CC BY demande de conserver l'identité de l'auteur et la mention de licence lors du partage, et de donner un lien vers le matériel lorsque c'est raisonnablement possible (Creative Commons, [Attribution 4.0 International, texte intégral, section 3(a)](https://creativecommons.org/licenses/by/4.0/legalcode.txt)). Une page de crédits est donc à ajouter dans l'appli, ou sur le site, avant la prochaine publication.

**Jeux d'entraînement sans attribution dans le dépôt.** Le modèle embarqué `sillon_disease.json` cite sept jeux supplémentaires (voir section 3). Aucun n'a de ligne dans `ATTRIBUTIONS.csv`. Le jeu PlantSeg est indiqué comme CC BY-NC 4.0 dans `tools/ml/train_sillon_yolo.py`, avec une autorisation écrite datée du 9 novembre 2026 que le dépôt ne contient pas. Cette question de licence relève d'une personne qualifiée.

## 1. D'où viennent les dates de gelée

Source du catalogue : `shared/src/commonMain/kotlin/fr/pilmil/sillon/data/FrenchFrostCatalog.kt`. Chaque département porte une seule date, la « date médiane de dernière gelée ».

- **Définition déclarée.** `docs/scientific-foundations.md`, section 3.3, indique qu'il s'agit du percentile 50 de séries trentenaires de Météo-France. Le document ne précise ni la période des trente années, ni la station retenue pour chaque département, ni le seuil de température (il cite « T ≤ 0 °C sous abri normalisé »).
- **Ce que la médiane signifie.** Une date médiane est une date que, une année sur deux environ, une gelée suit. Une année tardive peut donc se produire après la date affichée, et une année précoce avant. La donnée ne décrit pas cette dispersion.
- **Marge d'erreur admise.** Le catalogue n'en admet aucune en valeur : une date par département, au jour près, sans intervalle, sans écart type et sans correction d'altitude. La fenêtre de « ±7 jours » de `FrostEngine.kt` sert à afficher les actions d'une quinzaine. Ce n'est pas une estimation d'erreur.
- **Vérifiabilité.** Le dépôt ne contient ni les séries brutes ni le script qui calcule les médianes. Les dates ne peuvent donc pas être recalculées depuis le dépôt.
- **Dates du territoire.** L'appli permet à l'utilisateur de corriger la date (voir `documentation/GUIDE_UTILISATEUR.md`). Le document scientifique indique « 101 départements ». Le catalogue en contient 104 : 96 en métropole (01 à 19, 21 à 95, 2A et 2B, le code 20 n'existant plus) et 8 en outre-mer ou collectivités (971, 972, 973, 974, 976, 977, 978, 987).
- **Outre-mer.** Pour les cinq DOM et les trois collectivités listés, la date n'est pas une date de gelée : c'est un début de saison, choisi par convention. Le commentaire du catalogue dit que les sources varient d'un à deux mois.
- **Territoires absents.** Saint-Pierre-et-Miquelon (975), la Nouvelle-Calédonie (988) et Wallis-et-Futuna (986) ne figurent pas dans le catalogue. Si l'un d'eux est sélectionné, `getFrostDate` renvoie le 20 avril et `getClimate` le climat tempéré, ce qui n'est pas adapté à un territoire tropical.
- **Renvoi cassé.** Le commentaire du catalogue renvoie à « la section 3.4 » du document scientifique. Le document n'a que les sections 3.1 à 3.3.

## 2. Comment le compagnonnage est sourcé

Source : `shared/src/commonMain/kotlin/fr/pilmil/sillon/data/VegetableCatalog.kt`, avec le modèle `CompanionRelation.kt`.

- **Nombre de relations : 255.** 166 sont listées comme bénéfiques (`goodCompanions`) et 89 comme risques (`badCompanions`).
- **Nombre de relations avec une source : 0.** Le modèle `CompanionRelation` n'a aucun champ de source. Chaque relation ne porte qu'un court texte (`rationale`) et un type (bénéfice ou risque).
- **Renvois.** Le seul renvoi du fichier (ligne 1868) pointe vers une section « Cultures tropicales et saisons » de `docs/scientific-foundations.md`. Cette section n'existe pas dans le document. Le renvoi concerne d'ailleurs les calendriers tropicaux, pas le compagnonnage.
- **Formulations à revoir.** Certains textes affichés affirment plus que ce que le dépôt démontre. Par exemple, « effet nématicide racinaire éprouvé » pour l'œillet d'Inde (*Tagetes*) : aucune étude n'est rattachée à cette relation. Le document scientifique, lui, cite des travaux (par exemple Finch et Collier, 2000), mais ne les relie à aucune relation précise du catalogue. Il attribue en outre cette revue à deux titres de revue différents (section 2.1 et bibliographie). Ces références n'ont pas été vérifiées dans cette mission.

Conclusion : les 255 relations reposent sur le jugement des auteurs du catalogue, documenté seulement de manière générale. Aucune n'est sourcée individuellement.

## 3. D'où viennent les photos du diagnostic

Source : `tools/ml/ATTRIBUTIONS.csv`, `app/src/main/assets/models/sillon_disease.json`, `tools/ml/README.md`, `tools/ml/train_sillon.py`, `tools/ml/train_sillon_yolo.py`.

- **Contenu du CSV.** 5 134 lignes, toutes issues de `www.inaturalist.org`. Chaque ligne contient l'auteur, la licence et l'URL de l'observation. Les licences se répartissent ainsi : CC BY 3 757, CC0 1 156, CC BY-SA 221. Le CSV n'indique pas la version de la licence CC BY.
- **Répartition par classe.** hors_sujet 605, sain 603, oïdium 603, limaces 603, piéride du chou 602, doryphore 601, altises 601, pucerons 554, botrytis 244, rouille 70, mildiou 35, alternariose 13. Mildiou et alternariose sont donc très peu représentés dans ce fichier.
- **Jeux cités par le modèle.** Le fichier `sillon_disease.json` liste sept dossiers `extra_field` : plantwild, plantseg, potato_holeta, ibean, pumpkin_bd, cucumber_bd et field_gbif. Ils n'ont aucune ligne dans `ATTRIBUTIONS.csv`, et le dépôt ne contient aucun fichier d'attribution pour eux.
- **Autres jeux.** `tools/ml/README.md` et `train_sillon.py` prévoient l'entraînement sur PlantVillage et PlantDoc. `train_sillon_yolo.py` décrit PlantDoc Object Detection comme CC BY 4.0. Le dépôt ne donne la licence de PlantVillage nulle part, et les crédits de PlantDoc et de PlantVillage ne figurent pas dans le dépôt. Si les jeux Mendeley cités dans `tools/ml/README.md` (CC BY 4.0) ont servi à l'entraînement, ils posent le même problème : le dépôt ne le consigne pas.
- **PlantSeg.** `train_sillon_yolo.py` le décrit comme CC BY-NC 4.0 avec une autorisation écrite datée du 9 novembre 2026. La licence non commerciale limite l'usage du jeu. Ce point doit être tranché par une personne qualifiée, avec l'autorisation écrite en main, avant toute diffusion nouvelle du modèle.
- **Affichage des crédits.** Les réglages de l'appli (`SettingsScreen.kt`) mentionnent la licence MPL-2.0 de l'appli, pas les licences des photos. Les pages `docs/index.html` et `docs/confidentialite.html` ne citent aucune photo. Les README ne le font pas non plus.

## 4. Licence des icônes `veg_*.xml`

- **Fichiers.** 60 fichiers `veg_*.xml` se trouvent dans `shared/src/commonMain/composeResources/drawable/`. Aucun ne comporte de commentaire d'origine, et le dépôt ne contient aucun fichier de crédits pour les icônes.
- **Indice.** Le commentaire de `shared/src/commonMain/kotlin/fr/pilmil/sillon/ui/components/VegetableEmoji.kt` indique « CC0, Open Crop Icons ». Le README indique « ~60 icônes vectorielles CC0 ».
- **Vérification.** À faire : confirmer la licence CC0 sur la page officielle du projet Open Crop Icons (https://github.com/openfarmcc/open-crop-icons). Elle n'a pas pu être ouverte directement pendant cet audit.
- **Limite.** Je n'ai pas comparé les 60 fichiers un à un avec le jeu d'origine. La concordance repose sur le commentaire de `VegetableEmoji.kt`.
- **Résultat.** La licence CC0 est compatible avec la licence MPL-2.0 de l'appli, à condition que l'origine soit bien Open Crop Icons. Il est recommandé d'ajouter un fichier de mentions d'origine pour ces icônes.

## 5. Actions proposées (non effectuées)

1. Ajouter une page de crédits photo, générée à partir de `ATTRIBUTIONS.csv`, dans les réglages ou sur le site.
2. Consigner les licences de PlantVillage, PlantDoc et des sept jeux `extra_field`, et conserver l'autorisation écrite PlantSeg dans le dépôt privé.
3. Ajouter un fichier de mentions d'origine pour les icônes.
4. Corriger le nombre de départements (101 dans le document scientifique, 104 dans le catalogue), le renvoi « section 3.4 » et le renvoi « Cultures tropicales et saisons ».
5. Sourcer les relations de compagnonnage une par une, ou reformuler les textes affirmatifs qui n'ont pas de source.
