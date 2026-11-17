# Sources du compagnonnage de Sillon

Ce document répond à une question : d'où viennent les 255 relations de compagnonnage du catalogue, et que peut-on en justifier avec des sources que l'on peut ouvrir ? Il décrit le dépôt à la révision `d435751` (branche `audit-novembre`, 16 novembre 2026). Le code Kotlin n'a pas été modifié : les retraits et changements de niveau de preuve ci-dessous sont des propositions, l'auteur tranche.

Données complètes : `docs/data/compagnonnage.csv` (255 lignes, une par relation).

## (a) Origine établie de la liste

**Ce que le dépôt montre.**

- Les relations sont écrites en dur dans `shared/src/commonMain/kotlin/fr/pilmil/sillon/data/VegetableCatalog.kt` (à l'origine `app/src/main/java/.../VegetableCatalog.kt`, déplacé sans changement de contenu par le commit `6c6477d` du 14 novembre 2026). Chaque plante porte deux listes, `goodCompanions` et `badCompanions`, d'objets `CompanionRelation` : l'identifiant de la plante cible, un texte court (`rationale`) et un type de bénéfice ou de risque.
- Historique du fichier (`git log --follow`) : trois commits seulement.
  - `e511bd7` (6 novembre 2026, « Initial release ») : le fichier arrive d'un bloc, avec 61 plantes et 243 relations, dans le même commit que toute l'appli et que `docs/scientific-foundations.md`. Le message ne cite aucune source.
  - `6c6477d` (14 novembre) : déplacement du fichier, sans modification.
  - `e24e441` (15 novembre) : ajout de 12 relations pour les cultures tropicales, soit 13 plantes de plus (74 au total) et 255 relations (dix d'entre elles figurent déjà dans `d81c3d3`, 14 novembre, intégré par ce commit). Dix de ces relations citent une source dans leur texte (Guide tropical CIRAD/RITA fiche FT17, Chambre d'agriculture de la Martinique, guide du jardinage au naturel du TCO à La Réunion). Les deux relations du manioc n'en citent aucune. Ces sources n'ont pas été ouvertes pendant cette mission : elles décrivent des associations pratiquées, pas leur efficacité.
- Aucun script, aucun fichier de données (CSV, JSON) et aucun message de commit ne montre un procédé de constitution : `scripts/` ne contient que `gen_site.py`, `play-whatsnew.sh` et `version-from-tag.sh`, et `tools/ml/` concerne le modèle de photos.
- Le modèle de données n'a aucun champ de source ni de niveau de preuve. Le mot « éprouvé » apparaît une fois, dans le texte libre de la relation tomate vers œillet d'Inde (« Effet nématicide racinaire éprouvé »). « Documenté » apparaît une fois (alliacées contre légumineuses), « traditionnel », « classique » ou « Milpa » huit fois. Dans 235 des 255 textes, rien n'indique le niveau de preuve.
- Les mentions de source existantes sont donc les dix textes tropicaux ci-dessus, plus un commentaire (ligne 1868) qui renvoie à une section « Cultures tropicales et saisons » absente de `docs/scientific-foundations.md`.
- Le site (`docs/index.html`) indique que le code est écrit avec l'aide d'un assistant d'intelligence artificielle. Les sept commits de l'agent « Claude » du dépôt ne touchent pas le catalogue. Le commit initial est signé de l'auteur humain.

**Conclusion.** Aucun élément du dépôt n'indique une source pour les 243 relations d'origine, ni un script ou un jeu de données qui les aurait produites. La liste a très probablement été rédigée à partir de connaissances générales de jardinage lors du développement assisté par IA, sans référence par relation. C'est une inférence : le dépôt ne permet pas de la démontrer, ni de savoir quel outil ou quelle page de référence a servi. Les relations ont été saisies d'un bloc le 6 novembre.

## (b) Mécanismes, nombre de relations et niveau de preuve réel

Méthode : un script jetable (hors dépôt) extrait les relations du fichier Kotlin. Le total retrouvé est de 255 (166 bénéfiques, 89 à risque), identique au décompte précédent. Les relations sont ensuite classées par mécanisme avec des règles écrites à la main (type déclaré, plantes concernées, texte), puis chaque mécanisme est confronté aux sources ouvertes. Ce classement est un jugement : il est à relire, et chaque ligne du CSV porte son code de mécanisme.

Niveaux utilisés :

- **Étayé par essais** : au moins un essai ou une synthèse ouverte observe l'effet affirmé, dans des conditions que la note précise.
- **Plausible mais peu étudié** : le mécanisme est biologiquement raisonnable ou documenté ailleurs, sans essai trouvé pour cette association.
- **Tradition sans preuve** : aucune source ouverte, ou seulement des sources qui répètent la croyance sans données.
- **Contredit** : des essais ouverts montrent l'absence de l'effet affirmé.

| Code | Mécanisme | Relations | Niveau de preuve réel | Ce que la science dit | Sources ouvertes |
|---|---|---|---|---|---|
| M01 | Alliacées et mouche de la carotte | 10 (2 étayées, 8 plausibles) | Étayé pour carotte et oignon seulement ; explication par l'odeur non soutenue | Un essai a observé moins de dégâts avec des oignons, surtout jeunes. Un essai finlandais trouve un effet faible. L'effet viendrait du feuillage vert, pas de l'odeur. Rien d'ouvert pour ail, échalote, poireau, ciboulette. | Uvah et Coaker 1984 ; Varis 1991 ; Finch et Collier 2003 et 2012 |
| M02 | Aromatiques et masquage olfactif sur choux et carotte | 21 | Contredit (mécanisme) | Les plantes aromatiques ne perturbent pas la mouche du chou ni la mouche de l'oignon plus que d'autres plantes. Pas de preuve que des non-hôtes repoussent. Essais sur ces deux mouches, pas sur la piéride. | Finch, Billiald et Collier 2003 ; Finch et Collier 2003 et 2012 |
| M02b | Même idée pour d'autres cultures (aubergine, poivron, piment, pois chiche, tomate) | 8 | Tradition sans preuve | Même réserve, non testé pour ces cultures. | Idem M02 |
| M03 | Œillet d'Inde (Tagetes), nématodes et aleurodes | 10 | Étayé sous conditions | Effet sur les nématodes à galles et des lésions comme engrais vert avant la culture, variable selon cultivar et espèce. En culture associée : probablement inefficace selon l'extension de Floride. Un essai en serre : baisse des aleurodes avec l'œillet d'Inde français, planté tôt. | Hooks et al. 2010 ; UF/IFAS ENY-056 ; UMass ; Conboy et al. 2019 |
| M04 | Trois sœurs (maïs, haricot, courge) | 11 (7 étayées, 4 plausibles) | Étayé en rendement ; mécanisme azote non requis | Des essais de terrain et une modélisation trouvent un gain par unité de surface (jusqu'à 7 % de prélèvement d'azote et de biomasse en plus dans le modèle), dû à des racines complémentaires, sans dépendre de la fixation d'azote. Le maïs sert de tuteur. Des services d'extension notent qu'aucun essai n'est cité ou que le gain de productivité est improbable. | Zhang et al. 2014 ; Postma et Lynch 2012 ; UMN ; Garden Professors |
| M05 | Azote des légumineuses pour la culture voisine | 10 | Plausible mais peu étudié | Le transfert existe mais varie de 0 à 80 % des besoins du receveur, vient surtout de la décomposition des racines et se fait lentement, donc plutôt les années suivantes. Rien d'ouvert pour carotte, radis ou navet la même saison. | Revue sur le transfert d'azote (Springer) |
| M06 | Alliacées contre légumineuses (blocage des nodosités) | 26 | Tradition sans preuve | Aucune étude trouvée. Les pages qui l'affirment ne citent pas de données. | FREDON ; Ask Extension |
| M07 | Fenouil (allélopathie) | 9 (8 plausibles, 1 incohérente) | Plausible mais peu étudié | L'huile essentielle de fenouil inhibe la germination en boîte de Petri (blé, quinoa). Pas d'essai au potager, pas de test sur tomate ni carotte. | Hamada et al. 2021 |
| M08 | Autres allélopathies (persil et laitue, aneth et carotte, tournesol...) | 7 | Tradition sans preuve | Aucune source ouverte. | Aucune |
| M09 | Plantes pièges (capucine) | 4 (1 plausible, 3 tradition) | Plausible pour les aleurodes, sinon tradition | Plante non hôte associée à la tomate : moins d'aleurodes en serre. L'extension du Minnesota ne la décrit pas comme plante piège. La capucine est attaquée par les altises. | Conboy 2019 ; UMN ; RHS |
| M10 | Fleurs attractives (bourrache, phacélie, aneth, coriandre) | 13 (2 étayées, 11 plausibles) | Étayé pour bourrache et fraise ; reste plausible | Un essai de jardins trouve environ un tiers de fruits en plus avec de la bourrache près des fraisiers. Les bandes fleuries améliorent la lutte contre les ravageurs d'environ 16 % en moyenne, avec des effets variables sur la pollinisation et le rendement. | Griffiths-Lee et al. 2023 ; Albrecht et al. 2020 ; UMN fruit |
| M11 | Ravageurs et maladies partagés (même famille) | 18 (13 étayées, 5 plausibles) | Étayé pour mildiou, altises, mouche de la carotte | La tomate et la pomme de terre partagent l'agent du mildiou. Les altises attaquent radis, navet, roquette et choux. La mouche de la carotte attaque aussi panais, persil, céleri et céleri-rave. | UMass (mildiou) ; RHS (altises) ; RHS (mouche de la carotte) |
| M11b | Maladies « partagées » entre pomme de terre et cucurbitacées | 5 | Tradition sans preuve | Familles différentes, aucune source ouverte pour une maladie commune. | Aucune |
| M12 | Concurrence nutritive, racinaire ou ombrage | 19 | Plausible mais peu étudié | Principe agronomique raisonnable. L'extension recommande d'associer racines profondes et superficielles pour limiter la concurrence. Aucun essai trouvé pour les paires listées. | Garden Professors |
| M13 | Couvre-sol, ombre, microclimat | 10 (9 plausibles, 1 tradition) | Plausible mais peu étudié | Mêmes éléments que M12. | Garden Professors |
| M14 | Basilic et tomate | 3 | Étayé avec réserves | Moins d'aleurodes et plus de rendement dans deux essais (l'un sous filet, l'autre thèse non publiée en revue). Pas de préférence de saveur constante. | Mutisya et al. 2016 ; Bomford 2004 ; UMN |
| M15 | Radis marqueur ou intercalaire rapide | 9 | Plausible mais peu étudié | Pratique de culture sensée (radis récolté avant que la culture lente ait besoin de la place), non testée. | RHS (panais) |
| M16 | Associations tropicales citées | 12 | Tradition sans preuve d'efficacité | Pratiques attestées par des sources citées dans le code, non ouvertes ici. Deux relations (manioc) sans source. | Non ouvertes |
| M17 | Synergie ou antagonisme « général » sans mécanisme | 20 | Tradition sans preuve | Aucun mécanisme énoncé. | Aucune |
| M18 | Stimulation de croissance ou de saveur | 19 | Tradition sans preuve | Pas de preuve d'effet sur la saveur. | Bomford 2004 |
| M19 | Répulsifs divers (souci, ail antifongique, solanine...) | 11 | Tradition sans preuve | Aucune source ouverte. L'extension du Minnesota note que beaucoup de tableaux en ligne listent des plantes répulsives sans recherche. | UMN |

Répartition des 255 relations par catégorie de preuve : étayé par essais 37, plausible mais peu étudié 84, tradition sans preuve 113, contredit 21. Par paires de plantes (189 paires, certaines relations étant réciproques) : 23 paires étayées, 65 plausibles, 85 tradition, 15 contredites, 1 mixte (aubergine et haricot nain).

Limites des sources : pour Uvah et Coaker (1984), Hooks et al. (2010) et Finch et Collier (2012), seul le résumé ou un aperçu a été ouvert. Aucune source d'INRAE, de l'ITAB ou de Cornell n'a pu être ouverte sur ces associations. Le GRAB (bulletin 2020) ne traite ni oignon et carotte ni Tagetes. Les chiffres cités viennent des pages ouvertes et sont reformulés.

## (c) Paires individuellement étayées (23)

| Paire | Source et condition |
|---|---|
| Carotte et oignon | Uvah et Coaker 1984 : moins de dégâts de mouche de la carotte avec des oignons, surtout jeunes. Varis 1991 : effet faible. Mécanisme olfactif non soutenu. |
| Tomate et basilic ; tomate cerise et basilic | Mutisya et al. 2016 (moins d'aleurodes, plus de rendement, sous filet) ; Bomford 2004 (rendement en hausse d'environ 20 %, pas de préférence de saveur). |
| Tomate et œillet d'Inde (2 entrées : `tagete`, `oeillet_d_inde`) ; tomate cerise, aubergine, poivron, melon et Tagetes | Hooks et al. 2010 et UF/IFAS : nématodes à galles et des lésions réduits quand le Tagetes est cultivé avant et en densité ; l'association simultanée est jugée probablement inefficace. Conboy et al. 2019 : aleurodes de la tomate en serre. |
| Tomate et pomme de terre ; tomate cerise et pomme de terre | UMass : même agent du mildiou, *Phytophthora infestans*. |
| Chou pomme et radis ; navet et radis ; roquette et chou pomme | RHS : les altises attaquent radis, navet, roquette et choux. |
| Carotte et céleri-rave ; panais et carotte ; panais et céleri-rave ; céleri branche et carotte | RHS : les larves de la mouche de la carotte attaquent aussi panais, persil, céleri et céleri-rave. |
| Maïs et haricot grimpant ; maïs et courgette ; maïs et courge butternut ; maïs et potimarron | Zhang et al. 2014 et Postma et Lynch 2012 : gain par unité de surface avec racines complémentaires ; le maïs sert de tuteur. Le gain ne dépend pas de l'azote du haricot. |
| Bourrache et fraise | Griffiths-Lee et al. 2023 : environ 35 % de fruits en plus avec de la bourrache. Le mécanisme est la pollinisation, pas une « stimulation de croissance » comme le dit le texte. |

## (d) Paires contredites ou incohérentes dans le catalogue

**Contradictions de signe.** Aucune : pas de plante A amie de B alors que B est ennemie de A.

**Relations à sens unique.** 123 relations n'ont pas de réciproque (84 bénéfiques, 39 à risque), soit 66 paires renseignées dans les deux sens et 123 dans un seul. Dans la vue du potager (`GardenNeighborhood.kt`), chaque case ne regarde que sa propre liste : la case de A signale B, mais la case de B ne signale pas A.

**Relations contredites par des essais (21, 15 paires).** Mécanisme olfactif ou aromatique contre les ravageurs des choux et de la carotte (M02) : chou pomme avec menthe, sauge, thym et romarin ; chou fleur avec menthe et céleri branche ; brocoli avec menthe et romarin ; chou kale avec menthe et thym ; navet et romarin ; céleri-rave et chou pomme ; carotte avec romarin, sauge et coriandre. Liste complète dans le CSV (code M02).

**Incohérences internes.**

1. Deux identifiants pour le même genre : `tagete` et `oeillet_d_inde` (Tagetes). La tomate liste les deux, et chacun liste la tomate.
2. Fenouil : le texte de conseil du fenouil en fait « le mauvais compagnon par excellence », mais la relation fenouil vers poireau est classée bénéfique (« Tolérance mutuelle acceptable »).
3. Alliacées et betterave : l'oignon, l'ail et l'échalote sont amis de la betterave, le poireau (même genre) est son ennemi (« compétition racinaire »).
4. Oignon vers laitue : le texte (« Protection fongicide légère des bulbes ») ne correspond pas à la plante cible.
5. Pomme de terre contre courgette, concombre, melon, pastèque et courge : les textes invoquent des maladies communes entre familles différentes (M11b), sans source.
6. Aneth contre carotte (2 relations, « hybridation ») et aneth contre fenouil (« pollinisation croisée ») : mécanismes invoqués sans source ouverte, à vérifier par l'auteur.
7. Capucine bénéfique pour le chou pomme (« détourne la ponte de la piéride ») : la RHS note que la capucine est attaquée par les altises, ravageurs du chou.
8. Œillet d'Inde et tomate, texte « éprouvé » : l'effet nématicide est établi pour une culture avant la tomate, pas pour l'association simultanée.
9. Panais vers carotte : la paire est étayée (mouche de la carotte), mais le texte parle de « maladies racinaires ».
10. Dans les textes, 12 relations citent une pratique (`pratiquée`) : 10 ont une source, 2 (manioc) n'en ont pas.

## (e) Recommandation

**Niveaux à afficher dans l'appli** (quatre libellés, un par catégorie) :

| Catégorie | Libellé proposé | Remarque d'affichage |
|---|---|---|
| Étayé par essais | « Observé en essais » | Ajouter la condition en une phrase (par exemple pour Tagetes : « en culture avant la tomate »). Pas de promesse de résultat. |
| Plausible mais peu étudié | « Plausible, peu étudié » | Garder le texte du mécanisme, au conditionnel. |
| Tradition sans preuve | « Tradition de jardinage, non démontré » | Retirer les mécanismes précis (« bactéricide », « allélopathie puissante »). |
| Contredit | Ne pas afficher comme bénéfice | Voir les retraits. |

**Relations à retirer (proposition, 30 relations)**

- Les 21 relations M02 (aromatiques répulsives ou masquantes contre piéride, mouche du chou, mouche de la carotte). Si l'auteur veut conserver une trace, une note générale « les herbes aromatiques ne sont pas démontrées répulsives » suffit.
- Les 5 relations M11b (maladies partagées entre pomme de terre et cucurbitacées).
- Les 2 relations aneth contre carotte (« hybridation »), jusqu'à vérification.
- La relation capucine vers chou pomme.
- La relation fenouil vers poireau, incohérente avec le conseil du catalogue.
- Fusionner `tagete` et `oeillet_d_inde` en un seul identifiant (2 relations en doublon), à traiter avec les identifiants du code.

**Relations à conserver en changeant le niveau ou le texte**

- M06 (26 relations alliacées contre légumineuses) : garder comme précaution, niveau « tradition », sans mécanisme affirmé.
- M03 (10 relations Tagetes) : niveau « Observé en essais » avec condition ; retirer « éprouvé » et « puissant » ; mentionner aleurodes en serre à part.
- M01 : n'afficher « Observé en essais » que pour carotte et oignon, « plausible » pour les autres alliacées, sans l'explication par l'odeur.
- M04 : « Observé en essais » pour la polyculture ; retirer l'azote du haricot comme explication.
- M07 (fenouil) : « plausible », texte limité à « peut inhiber des plantes voisines ».
- M17, M18 et M19 (50 relations de tradition) : étiquette « tradition », texte de saveur à retirer.
- M16 : étiquette « pratique attestée », citer la source dans le texte pour les 10 relations qui en ont une ; ouvrir ces sources avant de les afficher comme références.
- Bourrache et fraise : remplacer « stimule la croissance » par « attire les pollinisateurs ».

**Mécanique.** Ajouter au modèle un champ de niveau de preuve et un champ de source (identifiant vers une liste de références de ce fichier), plutôt que de glisser « éprouvé » dans le texte libre. Le test existant (`CatalogTest`) ne vérifie que l'absence de texte vide et la validité des identifiants : il n'est pas contredit par ces propositions.

## Référence Finch et Collier du document scientifique

`docs/scientific-foundations.md` attribuait à Finch et Collier (2000, « Horticultural Research International ») un masquage olfactif réduisant la ponte de la mouche de la carotte de 40 à 70 %.

- Aucune source ouverte ne donne ce chiffre. Le résumé d'Uvah et Coaker (1984) ne contient aucun taux de ponte ou de dégâts. Les pages de Finch et Collier ouvertes (article de *Biologist* 2003, résumé de 2012, page de l'université de Warwick) n'en contiennent pas non plus. Le seul chiffre trouvé concerne le chou sous trèfle : 36 % des mouches du chou pondent à côté d'un chou sur sol nu contre 7 % dans le trèfle, ce qui n'a rien à voir avec la carotte.
- Les travaux de Finch et Collier expliquent ces effets par le feuillage vert et non par l'odeur : l'hypothèse d'un masquage olfactif n'est pas soutenue.
- « Horticultural Research International » est le nom de l'institut de Wellesbourne, pas d'une revue. La référence de la bibliographie (*Bulletin of Entomological Research* 90(2), 91-108, titre sur les plantes qui « tamper with host-plant odor ») n'a pu être rattachée à aucune source ouverte. Les pages ouvertes situent l'article de 2000 dans *Entomologia Experimentalis et Applicata* 96, 91-102 ; son titre exact reste à reporter depuis l'article.

Le document scientifique a été corrigé en conséquence (suppression du chiffre, références d'Uvah et Coaker 1984, Varis 1991 et Finch et al. 2003 et 2012). Autres affirmations du même document non vérifiées dans cette mission : « baisse d'infestation de 80 % » des galles racinaires attribuée à l'INRAE et au Rodale Institute, et plusieurs références de la bibliographie (par exemple Lotter 2003, Thoyer 2018, Garnier et Navas 2012).

## Sources ouvertes pendant cette mission

- Uvah, I. I. I. et Coaker, T. H. (1984). Effect of mixed cropping on some insect pests of carrots and onions. *Entomologia Experimentalis et Applicata* 36(2), 159-167. Résumé : https://agris.fao.org/search/en/records/65deb2ea0f3e94b9e5d08c14
- Varis, A.-L. (1991). Effect of intercropping carrots and onions on damage caused by the carrot fly. *Agricultural and Food Science* 63(5). https://doaj.org/article/9c235cee4b05474d904ce1c3ff777c59
- Finch, S. et Collier, R. H. (2003). Insects can see clearly now the weeds have gone. *Biologist* 50(3). https://warwick.ac.uk/fac/sci/lifesci/wcc/research/croppests/ipm/companionplanting/biologist_jun03.pdf
- Université de Warwick, page sur le compagnonnage (liste des travaux de Finch et Collier). https://warwick.ac.uk/fac/sci/lifesci/wcc/research/croppests/ipm/companionplanting/
- Finch, S., Billiald, H. et Collier, R. H. (2003). Companion planting, do aromatic plants disrupt host-plant finding by the cabbage root fly and the onion fly more effectively than non-aromatic plants? *Entomologia Experimentalis et Applicata* 109, 183-195. https://wiki.pestinfo.org/wiki/Entomologia_Experimentalis_et_Applicata_(2003)_109,_183-195
- Finch, S. et Collier, R. H. (2012). The influence of host and non-host companion plants on the behaviour of pest insects in field crops. *Entomologia Experimentalis et Applicata* 142, 87-96. Résumé : https://wrap.warwick.ac.uk/id/eprint/41914
- Hooks, C. R. R., Wang, K.-H., Ploeg, A. et McSorley, R. (2010). Using marigold (Tagetes spp.) as a cover crop to protect crops from plant-parasitic nematodes. *Applied Soil Ecology* 46(3), 307-320. Aperçu : https://www.sciencedirect.com/science/article/abs/pii/S092913931000168X
- Krueger, R., Dover, K. E., McSorley, R. et Wang, K.-H., University of Florida IFAS Extension, ENY-056, Marigolds (Tagetes spp.) for Nematode Management. https://edis.ifas.ufl.edu/publication/NG045/pdf
- Conboy, N. J. A. et al. (2019). Companion planting with French marigolds protects tomato plants from glasshouse whiteflies through the emission of airborne limonene. *PLoS ONE* 14(3). https://eprints.ncl.ac.uk/255491
- Zhang, C., Postma, J. A., York, L. M. et Lynch, J. P. (2014). Root foraging elicits niche complementarity-dependent yield advantage in the ancient three sisters polyculture. *Annals of Botany*. https://plantscience.psu.edu/research/labs/roots/publications/overviews/root-foraging-elicits-niche-complementarity-dependent-yield-advantage-in-the-ancient-201cthree-sisters201d-maize-bean-squash-polyculture
- Postma, J. A. et Lynch, J. P. (2012). Complementarity in root architecture for nutrient uptake in ancient maize/bean and maize/bean/squash polycultures. *Annals of Botany* 110, 521-534. https://plantscience.psu.edu/research/labs/roots/publications/overviews/complementarity-in-root-architecture-for-nutrient-uptake-in-ancient-maize-bean-and-maize-bean-squash-polycultures
- Revue sur le transfert d'azote sous terre des légumineuses vers les non-légumineuses (page ouverte sans métadonnées d'auteur). https://link.springer.com/doi/10.1007/s13593-016-0396-4
- Letourneau, D. K. et al. (2011). Does plant diversity benefit agroecosystems? A synthetic review. *Ecological Applications* 21(1), 9-21. Résumé : https://research.usfq.edu.ec/en/publications/does-plant-diversity-benefit-agroecosystems-a-synthetic-review/
- Albrecht, M. et al. (2020). The effectiveness of flower strips and hedgerows on pest control, pollination services and crop yield. *Ecology Letters* 23(10), 1488-1498. https://research.wur.nl/en/publications/the-effectiveness-of-flower-strips-and-hedgerows-on-pest-control-/
- Griffiths-Lee, J., Nicholls, B. et Goulson, D. (2023). Companion planting to attract pollinators increases the yield and quality of strawberry fruit in gardens and allotments. *Ecological Entomology*. https://sussex.figshare.com/articles/journal_contribution/Companion_planting_to_attract_pollinators_increases_the_yield_and_quality_of_strawberry_fruit_in_gardens_and_allotments/23476607
- Mutisya, S. et al. (2016). Synergistic effects of agronet covers and companion cropping on reducing whitefly infestation and improving yield of open field-grown tomatoes. *Agronomy* 6(3), 42. https://www.mdpi.com/2073-4395/6/3/42
- Bomford, M. K. (2004). Yield, pest density, and tomato flavor effects of companion planting in garden-scale studies incorporating tomato, basil, and Brussels sprout. Thèse, West Virginia University. https://orgprints.org/6614/
- Hamada, D. et al. (2021). Biological control with essential oil of Foeniculum vulgare Mill. *European Journal of Science and Technology*, n° spécial 28, 52-55. https://dergipark.org.tr/tr/download/article-file/1920556
- University of Minnesota Extension, Companion planting in home gardens. https://extension.umn.edu/gardening-minnesota/companion-planting-home-gardens
- University of Minnesota, Berries, bees, and borage (2018). https://fruit.umn.edu/content/berries-bees-borage
- UMass Extension, Companion planting in the vegetable garden. https://www.umass.edu/agriculture-food-environment/home-lawn-garden/fact-sheets/companion-planting-in-vegetable-garden
- UMass Extension, Late blight on tomatoes and potatoes. https://www.umass.edu/agriculture-food-environment/home-lawn-garden/fact-sheets/late-blight-on-tomatoes-potatoes
- Chalker-Scott, L. (Washington State University Extension), Weeding out the pseudoscience in companion planting, Garden Professors. https://gardenprofessors.com/weeding-out-the-pseudoscience-in-companion-planting/
- Royal Horticultural Society, Carrot fly. https://rhs.org.uk/science/science-blogs/science/may-2018/carrot-fly-how-to-protect-your-crop
- Royal Horticultural Society, Flea beetles on brassicas and allied plants. https://rhs.org.uk/biodiversity/flea-beetles-on-brassicas-and-allied-plants
- Royal Horticultural Society, How to grow parsnips. https://www.rhs.org.uk/advice/grow-your-own/vegetables/parsnips
- GRAB, Maraîchage Bio Infos n°102, bilan des essais 2019. https://www.grab.fr/wp-content/uploads/2020/06/Bilan-des-essais-du-GRAB-en-mara%C3%AEchage-en-2019-et-programme-2020.pdf
- FREDON Hauts-de-France, Associer les plantes pour mieux les protéger. https://fredon.fr/hauts-de-france/sites/default/files/fiches%20techniques/Fiches%20jardiner%20au%20naturel/Associer%20les%20plantes%20pour%20mieux%20les%20prot%C3%A9ger.pdf
- Ask Extension, Incompatible garden plants (question 389142). https://ask.extension.org/kb/faq.php?id=389142
