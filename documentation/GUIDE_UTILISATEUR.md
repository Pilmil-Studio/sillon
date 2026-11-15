# Guide utilisateur — Sillon

Sillon est un compagnon de potager et de diagnostic phytosanitaire 100 % hors-ligne. Il adapte son calendrier de semis à votre environnement, votre département (métropole et outre-mer) et votre altitude, sans aucun compte ni connexion Internet.

---

## 1. Premier lancement (`OnboardingScreen`)

Un assistant en **3 étapes** configure votre jardin :
1. **Environnement** : **Pleine terre**, **Balcon / Terrasse** ou **Intérieur**.
2. **Localisation** : sélection du département ou territoire (dont les DOM/TOM comme La Réunion `974`, `971`, `972`, `973`, `976`, `987`, etc.) afin d'ajuster les fenêtres de semis aux dates de gelées ou au climat tropical.
3. **Rappel hebdomadaire** : activation optionnelle d'une notification locale le samedi matin.

---

## 2. Navigation principale (`SillonScreen`)

L'interface s'organise autour de **5 onglets** en bas d'écran :

### 1. Aujourd'hui (`DashboardScreen`)
Vue synthétique des tâches du moment :
- **4 horizons balayables** (`HorizontalPager`) : cette semaine, ce mois, prochaine saison, vue annuelle.
- **Repère climatique** : compte à rebours avant/après les dernières gelées (climat tempéré) ou repère de saison sèche/humide (climat tropical).
- **Conseils contextuels** : ajustement lié à l'**altitude** et notes d'**arrosage** calculées selon le paillage et la météo locale saisie.

### 2. Parcelles (`GardenScreen`)
Gestion spatiale et temporelle de vos cultures :
- **Deux modes d'affichage** :
  - **Perspective** (`GardenScene` / `PlantArt`) : vue isométrique en relief avec rendu stylisé des plants selon leur stade.
  - **À plat** : grille 2D classique pour l'édition rapide.
- **Contenants et volumes** : indication des volumes adaptés (pots/jardinières en mode balcon ou intérieur).
- **Rotation des cultures & historique pluriannuel** : alertes automatiques en cas de succession risquée sur un même emplacement, et consultation de l'historique des cultures précédentes par parcelle/slot.
- **Voisinage** (`NeighborhoodCard`) : vérification des associations favorables ou défavorables entre plantes voisines.

### 3. Plantes (`CatalogScreen`)
Encyclopédie locale des espèces et variétés :
- **Recherche textuelle** et **filtres** par famille botanique ou compatibilité intérieur/balcon.
- **Variétés anciennes et semences reproductibles** identifiées dans le catalogue.
- Fiches détaillées : espacement, profondeur, durée de germination, besoins en eau, ravageurs fréquents et associations.

### 4. Diagnostic (`DoctorScreen`)
Identification des problèmes de culture hors-ligne :
- **Diagnostic guidé (clé dichotomique)** : filtrage par organe touché (feuille, tige, fruit, racine) et symptômes observés.
- **Diagnostic photo IA embarqué** (`PhotoDiagnosisCard` + `SmartCameraViewfinder`) : viseur temps réel vérifiant la netteté et la présence de feuillage, suivi d'une classification locale sur 13 classes (maladies, carences, ravageurs) et d'un triage abiotique (stress hydrique, coup de soleil, gel).

### 5. Réglages (`SettingsScreen`)
Paramétrage complet et portabilité des données :
- Modification de l'**environnement**, du **département**, de l'**altitude** et des dates de gelées personnalisées.
- Saisie du **paillage** et de la **météo locale** pour affiner les conseils d'arrosage.
- Calibration via le **capteur de luminosité** (lux) en mode intérieur.
- Gestion des **rappels** locaux.
- **Sauvegarde et export** :
  - Export/import chiffré au format **`.pilmil`** (coffre-fort protégé par mot de passe).
  - Export **JSON** et **CSV** pour archivage ou tableur.
