package fr.pilmil.sillon.model

import kotlinx.serialization.Serializable

@Serializable
enum class PlantOrgan(val frenchLabel: String) {
    LEAF("Feuille"),
    STEM("Tige"),
    FRUIT("Fruit"),
    ROOT("Racine"),
    FLOWER("Fleur"),
    SEEDLING("Jeune pousse / Semis"),
}

@Serializable
enum class PathogenType(val frenchLabel: String) {
    FUNGUS("Maladie cryptogamique (champignon)"),
    PEST("Ravageur (insecte, mollusque, acarien)"),
    PHYSIOLOGICAL("Désordre physiologique / abiotique"),
    BACTERIA("Bactérie"),
    VIRUS("Virus"),
}

/**
 * Ce qu'un problème laisse dans la terre d'un carré. Ne se renseigne qu'avec une durée sourcée :
 * une maladie sans valeur sourcée n'a pas de [SoilCarryover] et n'alimente aucun avertissement de rotation.
 *
 * @property families familles à éviter à l'emplacement où le problème a été noté.
 * @property years nombre d'années à laisser passer, comptées à partir de l'année de la note.
 * @property source référence courte ; le détail et les réserves sont dans docs/scientific-foundations.md.
 */
@Serializable
data class SoilCarryover(
    val families: List<BotanicalFamily>,
    val years: Int,
    val source: String,
)

@Serializable
data class Disease(
    val id: String,
    val name: String,
    val latinName: String? = null,
    val pathogenType: PathogenType,
    val affectedOrgans: List<PlantOrgan>,
    val symptoms: List<String>,
    val causes: List<String>,
    val organicRemedies: List<String>,
    val targetFamilies: List<BotanicalFamily> = emptyList(),
    val preventiveMeasures: List<String> = emptyList(),
    val soilCarryover: SoilCarryover? = null,
    /** Vrai pour un mal qui ne touche que les semis et jeunes plants (fonte des semis). */
    val seedlingStageOnly: Boolean = false,
)
