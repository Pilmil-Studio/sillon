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

Source : `shared/src/commonMain/kotlin/fr/pilmil/sillon/data/VegetableCatalog.kt`, avec le modèle `CompanionRelation.kt`. L'analyse détaillée, le classement par mécanisme et la liste des sources ouvertes sont dans `docs/sources-compagnonnage.md`, les 255 relations dans `docs/data/compagnonnage.csv`.

- **Nombre de relations : 255.** 166 sont listées comme bénéfiques (`goodCompanions`) et 89 comme risques (`badCompanions`).
- **Origine.** 243 relations sont arrivées d'un bloc dans le commit initial du 6 novembre 2026, 12 relations tropicales ont été ajoutées les 14 et 15 novembre. Aucun script, jeu de données ni message de commit n'indique comment la liste a été constituée. Elle a très probablement été rédigée à partir de connaissances générales de jardinage lors du développement assisté par IA, sans référence par relation. Le dépôt ne permet pas de le démontrer.
- **Sources dans le code.** Le modèle `CompanionRelation` n'a aucun champ de source ni de niveau de preuve. Dix relations tropicales citent une source dans leur texte (Guide tropical CIRAD/RITA, Chambre d'agriculture de la Martinique, TCO de La Réunion) : ces sources n'ont pas été ouvertes, et elles décrivent des pratiques, pas des effets mesurés. Le renvoi du fichier (ligne 1868) pointe vers une section « Cultures tropicales et saisons » de `docs/scientific-foundations.md` qui n'existe pas.
- **Ce que les sources ouvertes soutiennent.** Sur 255 relations, 37 sont étayées par au moins un essai ou une source ouverte (en général avec des conditions précises), 84 sont plausibles mais peu étudiées, 113 relèvent de la tradition sans preuve trouvée et 21 sont contredites par des essais (herbes aromatiques censées repousser ou masquer les ravageurs des choux et de la carotte).
- **Formulations à revoir.** « Effet nématicide racinaire éprouvé » pour l'œillet d'Inde (*Tagetes*) vaut pour une culture de Tagetes avant la culture suivante, pas pour une association simultanée. Les affirmations sur le blocage des nodosités des légumineuses par les alliacées, sur le masquage olfactif et sur l'effet de saveur n'ont pas de source ouverte, ou sont contredites.
- **Incohérences.** Aucune relation n'est amie dans un sens et ennemie dans l'autre. 123 relations n'ont pas de réciproque. `tagete` et `oeillet_d_inde` désignent le même genre sous deux identifiants.

Conclusion : les 255 relations reposent sur le jugement des auteurs du catalogue. Aucune n'est sourcée individuellement dans le code. Des propositions de retrait et de changement de niveau de preuve sont dans `docs/sources-compagnonnage.md` ; elles n'ont pas été appliquées au code.

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
5. Appliquer au catalogue les propositions de `docs/sources-compagnonnage.md` : champ de niveau de preuve et de source, retrait ou reformulation des relations contredites ou sans source.

## Référence Finch et Collier (poireau, oignon et carotte)

`docs/scientific-foundations.md` attribuait à Finch et Collier (2000) un masquage olfactif et une baisse de ponte de 40 à 70 % chez la mouche de la carotte. Aucune source ouverte ne donne ce chiffre : ni le résumé d'Uvah et Coaker (1984), ni les pages de Finch et Collier ouvertes (2003, 2012). Les travaux de Finch et collaborateurs expliquent l'effet des plantes associées par leur feuillage vert et non par leur odeur, et les plantes aromatiques n'y sont pas plus efficaces que les autres. Le chiffre a été retiré du document scientifique et remplacé par les références ouvertes (voir `docs/sources-compagnonnage.md`). Autres affirmations du même document non vérifiées : la baisse de 80 % des galles racinaires attribuée à l'INRAE et au Rodale Institute, et plusieurs références de la bibliographie.
