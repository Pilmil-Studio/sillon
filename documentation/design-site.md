# Diagnostic du site vitrine (novembre 2026)

Pages : `docs/index.html`, `docs/confidentialite.html`. CSS en ligne dans un bloc `<style>` par page. Thèmes clair et sombre via `prefers-color-scheme`. Mesures faites avec Chrome headless, fenêtre de 360 px et de 1280 px, contrastes calculés (WCAG 2.x).

Constat avant reprise :
1. Galerie « L'application en images » : conteneur `display:flex; overflow-x:auto`, images de 210 px de large. À 1280 px, 8 captures (1 890 px avec les écarts) dans 1 060 px : défilement horizontal avec barre visible, les captures hors cadre sont en `loading="lazy"` et restent vides tant qu'on ne défile pas.
2. Les images de la galerie sont étirées par l'alignement flex par défaut : les six `.webp` (540 x 960, ratio 0,5625) sont rendues en 210 x 453 au lieu de 210 x 373, soit 21 % de hauteur en trop (déformation).
3. Attributs `width`/`height` : les six `.webp` déclarent 210 x 373 (ratio 0,5630, juste). Les deux PNG (`parcelles_perspective.png`, `diagnostic.png`, 1080 x 2340, ratio 0,4615) déclarent `width="210"` sans `height` : pas de réservation de hauteur, décalage de mise en page au chargement.
4. Les deux formats de capture n'ont pas le même ratio (0,5625 et 0,4615) : une grille doit laisser chaque image garder sa hauteur propre.
5. Simulateur : boutons d'horizon de 26 px de haut (65 à 82 px de large), boutons « Feuille » et « Tige / collet » d'environ 35 px, onglets du bas de 42 px. Tous sous 44 px. Contrastes du simulateur calculés : de 5,6 à 6,8, soit au-dessus de 4,5. Vert du simulateur `#2c6e49`, hors des variables de la page (`#2F6B4F`).
6. Contrastes de la page : texte `--fg` sur fond 13,75 (clair) et 15,92 (sombre) ; texte atténué `--muted` 5,73 (clair) et 8,26 (sombre) ; étiquette verte en capitales `--accent` 5,77 (clair) et 8,67 (sombre) ; blanc sur `--accent-solid` 6,29 ; texte atténué sur carte 4,84 (clair), 7,53 (sombre). Aucune valeur sous 4,5.
7. Zones tactiles : liens de l'en-tête 24 px de haut, liens du pied de page 20 px de haut, boutons d'appel à l'action 57 px.
8. Aucune valeur de couleur, de rayon, d'espacement ou de taille de police n'est portée par une variable en dehors des 8 couleurs de `:root` : 5 rayons (20, 28, 32, 999 px, 14 px), 3 ombres, une dizaine de tailles de police en `rem` libres. Largeur de ligne : `.doc` 46 em et `.sub` 38 em, soit environ 80 à 90 caractères par ligne à 18 px.
9. Pas de lien d'évitement, pas de règle `@media print`. `:focus-visible` défini globalement ; les boutons du simulateur n'ont pas de contour propre. Une seule règle `prefers-reduced-motion` (défilement doux).
10. Pas de débordement horizontal de la page à 360 px (largeur de contenu 345 px + barre de 15 px dans la mesure par iframe, hors galerie qui défile dans son conteneur). En-tête : deux liens, sans débordement ; l'ajout futur d'un troisième lien (branche `audit-novembre`) passe à la ligne grâce à `flex-wrap`.

## Après reprise

1. Corrigé. Sur ordinateur, la galerie est une grille de 4 colonnes (8 captures, 2 rangées, aucun défilement ni barre). Sous 640 px elle reste un bandeau horizontal avec accroche au défilement (`mandatory`), images de `min(62vw,220px)`, sans cadre vide. `loading="lazy"` conservé.
2. Corrigé. Les images ne sont plus étirées (`align-items:start`, `height:auto`). 210 x 453 devient 245 x 434 (ratio 0,5625) pour les `.webp`.
3. Corrigé. Attributs mis aux dimensions réelles des fichiers : 540 x 960 pour les six `.webp`, 1080 x 2340 pour les deux PNG (hauteur ajoutée). Les ratios restent différents d'un format à l'autre, chaque image garde le sien : les hauteurs d'une rangée ne sont pas égales.
4. Corrigé. Boutons d'horizon, boutons d'organe, onglets et curseur : 44 px de haut (mesuré). Vert du simulateur remplacé par `var(--accent-solid)`. Clics sur les trois onglets, les horizons et les organes vérifiés.
5. Aucun contraste à corriger (valeurs du constat 6). Fond des cartes passé de dégradé à aplat : texte atténué sur carte 5,08 (clair) et 7,75 (sombre).
6. Corrigé. Liens de l'en-tête 44 px de haut ; liens du pied de page : zone cliquable de 45 px, sans chevauchement.
7. Corrigé en partie. Rayons, ombres (2), espacements et tailles de police passent par des variables ; les styles en ligne du simulateur (rayons de 10 à 14 px, tailles de 10 à 17 px) et des deux `style` hérités de la page (marges) restent en ligne. Longueur de ligne : `.sub` 60ch, `.doc` 40em.
8. Corrigé. Lien d'évitement, `:focus-visible` sans changement de rayon, focus du simulateur à l'intérieur du cadre, règle `@media print`. Le seul mouvement (défilement doux) respecte `prefers-reduced-motion`. Le texte « Aller au contenu » est le seul texte ajouté.
9. Vérifié. Défilement horizontal de la page à 360 px : `scrollWidth` 360 pour les deux pages. Le bouton principal n'a plus de dégradé (aplat).
10. Non traité : les lignes de `docs/index.html` et `docs/confidentialite.html` modifiées par `origin/audit-novembre` (méta Open Graph, lien « Sources », phrase sous le bouton, section « fabrication ») et leurs voisines. Le test à 360 px avec le lien « Sources » ajouté n'a pas pu être fait (le fichier n'est pas fusionné).
