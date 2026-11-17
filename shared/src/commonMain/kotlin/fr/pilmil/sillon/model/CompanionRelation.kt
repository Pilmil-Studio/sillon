package fr.pilmil.sillon.model

import kotlinx.serialization.Serializable

@Serializable
enum class CompanionBenefitType(val frenchLabel: String) {
    PEST_REPELLENT("Répulsif / Confusion olfactive"),
    NITROGEN_FIXATION("Fixation symbiotique de l'azote"),
    POLLINATOR_ATTRACTOR("Attraction des pollinisateurs et auxiliaires"),
    GROWTH_STIMULATOR("Stimulation de croissance et vigueur"),
    SHADE_OR_GROUND_COVER("Couverture du sol et maintien de l'humidité"),
    PHYSICAL_SUPPORT("Support physique / tuteurage naturel"),
    DYNAMIC_ACCUMULATOR("Accumulation dynamique de minéraux"),
    GENERAL_SYNERGY("Synergie agronomique générale"),
}

@Serializable
enum class CompanionRiskType(val frenchLabel: String) {
    ALLELOPATHY("Allélopathie négative / inhibition"),
    SHARED_DISEASE_OR_PEST("Sensibilité aux mêmes maladies ou ravageurs"),
    NUTRIENT_COMPETITION("Concurrence nutritive sévère"),
    CANOPY_OR_ROOT_SHADOW("Compétition racinaire ou ombrage excessif"),
    FLAVOR_DEGRADATION("Altération de la saveur"),
    GENERAL_ANTAGONISM("Antagonisme agronomique général"),
}

/**
 * Ce que l'on sait vraiment d'une relation de compagnonnage. L'ordre va du mieux établi au moins établi :
 * [ordinal] sert à retenir la relation la mieux étayée quand deux plantes se citent mutuellement.
 */
@Serializable
enum class EvidenceLevel(val frenchLabel: String) {
    ETAYE("Étayé par essais"),
    PLAUSIBLE("Plausible, peu étudié"),
    TRADITION("Tradition de jardinage"),
}

/** Une phrase pour les trois niveaux, à afficher là où l'on parle de compagnonnage. */
const val EVIDENCE_EXPLANATION: String =
    "Étayé par essais : des essais au jardin ou en culture ont observé l'effet. " +
        "Plausible, peu étudié : l'idée tient la route, mais personne ne l'a vraiment mesurée. " +
        "Tradition de jardinage : transmise depuis longtemps, sans preuve, à tester chez vous !"

@Serializable
data class CompanionRelation(
    val targetPlantId: String,
    val rationale: String,
    val evidence: EvidenceLevel,
    /** Auteurs et année, ou organisme : renseignée pour les relations étayées. */
    val source: String? = null,
    val benefitType: CompanionBenefitType? = null,
    val riskType: CompanionRiskType? = null,
) {
    constructor(
        targetPlantId: String,
        rationale: String,
        benefitType: CompanionBenefitType,
        evidence: EvidenceLevel,
        source: String? = null,
    ) : this(targetPlantId, rationale, evidence, source, benefitType = benefitType, riskType = null)

    constructor(
        targetPlantId: String,
        rationale: String,
        riskType: CompanionRiskType,
        evidence: EvidenceLevel,
        source: String? = null,
    ) : this(targetPlantId, rationale, evidence, source, benefitType = null, riskType = riskType)

    val isBeneficial: Boolean get() = benefitType != null
    val isHarmful: Boolean get() = riskType != null

    /**
     * Phrase de la fiche d'un légume pour cette relation, vers [targetName]. Une simple tradition ou une
     * idée plausible n'est pas présentée comme un fait ; le niveau de preuve est écrit en toutes lettres.
     * [fromTheirSheet] : la relation est décrite sur la fiche de l'autre plante, son texte parle donc d'elle.
     */
    fun noteFor(targetName: String, fromTheirSheet: Boolean = false): String {
        val detail = if (fromTheirSheet) "décrit sur sa fiche" else "« ${rationale.trimEnd('.')} »"
        val body = when {
            isHarmful && evidence == EvidenceLevel.TRADITION -> "la tradition déconseille ce voisinage ($detail)"
            isHarmful && evidence == EvidenceLevel.PLAUSIBLE -> "voisinage à éviter par prudence ($detail)"
            isHarmful && fromTheirSheet -> "voisinage à éviter ($detail)"
            evidence == EvidenceLevel.TRADITION -> "selon la tradition, voisinage favorable ($detail)"
            fromTheirSheet -> "voisinage favorable ($detail)"
            else -> rationale.trimEnd('.')
        }
        return "$targetName : $body. ${evidence.frenchLabel}" + (source?.let { " ($it)" } ?: "")
    }
}

/** La relation la mieux étayée de la liste, ou null. */
fun List<CompanionRelation>.strongest(): CompanionRelation? = minByOrNull { it.evidence.ordinal }

/**
 * Relations favorables entre deux plantes, dans les deux sens : A vers B ou B vers A.
 * Les données ne sont pas dupliquées, la lecture est symétrique.
 */
fun Vegetable.goodRelationsWith(other: Vegetable): List<CompanionRelation> =
    goodCompanions.filter { it.targetPlantId == other.id } + other.goodCompanions.filter { it.targetPlantId == id }

/** Relations défavorables entre deux plantes, dans les deux sens. */
fun Vegetable.badRelationsWith(other: Vegetable): List<CompanionRelation> =
    badCompanions.filter { it.targetPlantId == other.id } + other.badCompanions.filter { it.targetPlantId == id }

/** Plantes du [catalog] dont la fiche déclare une relation favorable vers cette plante, sans que celle-ci ne la déclare en retour. */
fun Vegetable.incomingGood(catalog: List<Vegetable>): List<Pair<Vegetable, CompanionRelation>> =
    catalog.mapNotNull { other ->
        if (other.id == id || goodCompanions.any { it.targetPlantId == other.id }) {
            null
        } else {
            other.goodCompanions.firstOrNull { it.targetPlantId == id }?.let { other to it }
        }
    }

/** Plantes du [catalog] dont la fiche déclare une relation défavorable vers cette plante, sans que celle-ci ne la déclare en retour. */
fun Vegetable.incomingBad(catalog: List<Vegetable>): List<Pair<Vegetable, CompanionRelation>> =
    catalog.mapNotNull { other ->
        if (other.id == id || badCompanions.any { it.targetPlantId == other.id }) {
            null
        } else {
            other.badCompanions.firstOrNull { it.targetPlantId == id }?.let { other to it }
        }
    }
