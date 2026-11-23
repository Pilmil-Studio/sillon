package fr.pilmil.sillon.engine

import fr.pilmil.sillon.data.DiseaseCatalog
import fr.pilmil.sillon.data.VegetableCatalog
import fr.pilmil.sillon.model.BotanicalFamily
import fr.pilmil.sillon.model.CropHistoryEntry
import fr.pilmil.sillon.model.DiagnosticJournalEntry
import fr.pilmil.sillon.model.Disease
import fr.pilmil.sillon.model.NutrientRequirement
import fr.pilmil.sillon.model.Parcel
import fr.pilmil.sillon.model.Vegetable

enum class RotationSeverity(val frenchLabel: String) {
    CONFLICT_ERROR("Conflit de rotation majeur (même famille)"),
    EXHAUSTION_WARNING("Avertissement : succession de cultures exigeantes"),
    BENEFICIAL("Succession agronomique bénéfique"),

    /** Une maladie qui persiste dans la terre a été notée ici et la famille du candidat est concernée. */
    SOIL_DISEASE("Maladie persistante notée ici"),
}

/**
 * Avertissement pour un emplacement : [disease] a été notée en [notedYear] et ses [families] sont à éviter
 * jusqu'en [untilYear] compris. [source] est la référence courte de la durée (le détail est dans la documentation).
 */
data class SoilDiseaseWarning(
    val disease: Disease,
    val notedYear: Int,
    val families: List<BotanicalFamily>,
    val untilYear: Int,
    val source: String,
)

data class RotationCheckResult(
    val parcelId: String,
    val row: Int,
    val col: Int,
    val candidate: Vegetable,
    val targetYear: Int,
    val conflictingEntry: CropHistoryEntry?,
    val yearsElapsed: Int?,
    val requiredPauseYears: Int,
    val severity: RotationSeverity,
    val message: String,
)

class RotationEngine(
    private val catalogMap: Map<String, Vegetable> = VegetableCatalog.BY_ID,
) {

    /**
     * Vérifie la conformité de la rotation pour un légume candidat sur un emplacement précis.
     */
    fun checkRotation(
        candidate: Vegetable,
        parcelId: String,
        row: Int,
        col: Int,
        targetYear: Int,
        history: List<CropHistoryEntry>,
        journal: List<DiagnosticJournalEntry> = emptyList(),
    ): List<RotationCheckResult> {
        val results = mutableListOf<RotationCheckResult>()

        // Filtrer l'historique sur cet emplacement précis avant l'année cible
        val slotHistory = history.filter {
            it.parcelId == parcelId && it.row == row && it.col == col && it.year < targetYear
        }.sortedByDescending { it.year }

        val pauseYears = candidate.family.recommendedPauseYears

        // 1. Vérification de la pause obligatoire entre mêmes familles botaniques
        for (entry in slotHistory) {
            val yearsElapsed = targetYear - entry.year
            if (entry.family == candidate.family && yearsElapsed < pauseYears) {
                results.add(
                    RotationCheckResult(
                        parcelId = parcelId,
                        row = row,
                        col = col,
                        candidate = candidate,
                        targetYear = targetYear,
                        conflictingEntry = entry,
                        yearsElapsed = yearsElapsed,
                        requiredPauseYears = pauseYears,
                        severity = RotationSeverity.CONFLICT_ERROR,
                        message = "Conflit de rotation : la famille des ${candidate.family.frenchName} (${entry.vegetableId}) " +
                            "a déjà été cultivée à cet emplacement il y a $yearsElapsed an(s) (en ${entry.year}). " +
                            "Un délai d'au moins $pauseYears ans est requis pour prévenir l'accumulation de maladies cryptogamiques et la fatigue du sol.",
                    )
                )
            }
        }

        // 2. Vérification de la succession nutritive par rapport à l'année précédente immédiate (N-1)
        val lastYearEntry = slotHistory.find { it.year == targetYear - 1 }
        if (lastYearEntry != null) {
            val previousReq = lastYearEntry.family.nutrientRequirement
            val candidateReq = candidate.family.nutrientRequirement

            if (candidateReq == NutrientRequirement.HEAVY && previousReq == NutrientRequirement.HEAVY) {
                results.add(
                    RotationCheckResult(
                        parcelId = parcelId,
                        row = row,
                        col = col,
                        candidate = candidate,
                        targetYear = targetYear,
                        conflictingEntry = lastYearEntry,
                        yearsElapsed = 1,
                        requiredPauseYears = pauseYears,
                        severity = RotationSeverity.EXHAUSTION_WARNING,
                        message = "Succession gourmande : ${candidate.name} (exigeant) succède directement à une autre culture exigeante " +
                            "(${lastYearEntry.family.frenchName} en ${lastYearEntry.year}). Risque d'épuisement minéral du sol sans apport substantiel de compost ou engrais vert.",
                    )
                )
            } else if (candidateReq == NutrientRequirement.HEAVY && previousReq == NutrientRequirement.LIGHT_FIXER) {
                results.add(
                    RotationCheckResult(
                        parcelId = parcelId,
                        row = row,
                        col = col,
                        candidate = candidate,
                        targetYear = targetYear,
                        conflictingEntry = lastYearEntry,
                        yearsElapsed = 1,
                        requiredPauseYears = pauseYears,
                        severity = RotationSeverity.BENEFICIAL,
                        message = "Excellente succession : ${candidate.name} (exigeant) profite des réserves d'azote fixées par la légumineuse précédente (${lastYearEntry.family.frenchName}).",
                    )
                )
            }
        }

        // 3. Maladies persistantes notées dans le journal de cet emplacement.
        for (warning in soilWarnings(parcelId, row, col, targetYear, journal)) {
            if (candidate.family !in warning.families) continue
            results.add(
                RotationCheckResult(
                    parcelId = parcelId,
                    row = row,
                    col = col,
                    candidate = candidate,
                    targetYear = targetYear,
                    conflictingEntry = null,
                    yearsElapsed = targetYear - warning.notedYear,
                    requiredPauseYears = warning.untilYear - warning.notedYear,
                    severity = RotationSeverity.SOIL_DISEASE,
                    message = "${warning.disease.name} noté ici en ${warning.notedYear} : " +
                        "évitez les ${candidate.family.frenchName} jusqu'en ${warning.untilYear}.",
                )
            )
        }

        return results
    }

    /**
     * Avertissements de l'emplacement pour l'année [targetYear], d'après les maladies notées au journal.
     * Seules comptent les entrées dont la maladie est connue et porte une durée sourcée ([Disease.soilCarryover]) ;
     * une cause restée incertaine n'en produit aucune. L'avertissement commence l'année suivant la note et dure
     * [fr.pilmil.sillon.model.SoilCarryover.years] ans. Il ne dépend pas de l'issue du suivi : un plant guéri
     * laisse la même terre. Si la maladie est notée plusieurs fois, la note la plus récente fait foi.
     */
    fun soilWarnings(
        parcelId: String,
        row: Int,
        col: Int,
        targetYear: Int,
        journal: List<DiagnosticJournalEntry>,
        diseaseById: (String) -> Disease? = DiseaseCatalog::getById,
    ): List<SoilDiseaseWarning> =
        journal
            .filter { it.parcelId == parcelId && it.row == row && it.col == col }
            .sortedByDescending { it.noticedOn }
            .distinctBy { it.diseaseId }
            .mapNotNull { entry ->
                val disease = entry.diseaseId?.let(diseaseById) ?: return@mapNotNull null
                val carryover = disease.soilCarryover ?: return@mapNotNull null
                val notedYear = entry.noticedOn.year
                val untilYear = notedYear + carryover.years
                if (targetYear <= notedYear || targetYear > untilYear) return@mapNotNull null
                SoilDiseaseWarning(disease, notedYear, carryover.families, untilYear, carryover.source)
            }

    /**
     * Évalue l'ensemble des cases d'une parcelle pour une année donnée par rapport à l'historique.
     */
    fun evaluateParcel(
        parcel: Parcel,
        targetYear: Int,
        history: List<CropHistoryEntry>,
        customCatalog: Map<String, Vegetable> = catalogMap,
        journal: List<DiagnosticJournalEntry> = emptyList(),
    ): List<RotationCheckResult> {
        if (parcel.type.isContainerBased) return emptyList()
        val issues = mutableListOf<RotationCheckResult>()

        for (slot in parcel.slots) {
            val vegId = slot.vegetableId ?: continue
            val candidate = customCatalog[vegId] ?: continue

            val slotResults = checkRotation(
                candidate = candidate,
                parcelId = parcel.id,
                row = slot.row,
                col = slot.col,
                targetYear = targetYear,
                history = history,
                journal = journal,
            )
            issues.addAll(slotResults)
        }

        return issues
    }

    /**
     * Suggère des familles botaniques recommandées après une famille donnée.
     */
    fun getSuggestedSuccessorFamilies(previousFamily: BotanicalFamily): List<BotanicalFamily> {
        return when (previousFamily.nutrientRequirement) {
            NutrientRequirement.HEAVY -> listOf(
                BotanicalFamily.FABACEAE,
                BotanicalFamily.APIACEAE,
                BotanicalFamily.ALLIACEAE,
            )
            NutrientRequirement.MEDIUM -> listOf(
                BotanicalFamily.ALLIACEAE,
                BotanicalFamily.FABACEAE,
                BotanicalFamily.ASTERACEAE,
            )
            NutrientRequirement.LIGHT -> listOf(
                BotanicalFamily.FABACEAE,
                BotanicalFamily.SOLANACEAE,
                BotanicalFamily.BRASSICACEAE,
            )
            NutrientRequirement.LIGHT_FIXER -> listOf(
                BotanicalFamily.SOLANACEAE,
                BotanicalFamily.BRASSICACEAE,
                BotanicalFamily.CUCURBITACEAE,
            )
        }
    }
}
