package fr.pilmil.sillon.engine

import fr.pilmil.sillon.data.DiseaseCatalog
import fr.pilmil.sillon.model.BotanicalFamily
import fr.pilmil.sillon.model.Disease
import fr.pilmil.sillon.model.PlantOrgan
import fr.pilmil.sillon.model.Parcel
import fr.pilmil.sillon.model.ParcelSlot
import fr.pilmil.sillon.model.Vegetable
import fr.pilmil.sillon.model.daysBetween
import fr.pilmil.sillon.model.epochMillisToLocalDate
import kotlinx.datetime.LocalDate
import kotlin.math.min

data class DiagnosticMatch(
    val disease: Disease,
    val score: Int,
    val confidencePercent: Int,
    val organMatched: Boolean,
    val familyMatched: Boolean,
    val matchingSymptoms: List<String>,
    val organicRemedies: List<String>,
    val preventiveMeasures: List<String>,
)

/** Saison météorologique de l'hémisphère nord, pour situer une plantation. */
enum class Season {
    SPRING,
    SUMMER,
    AUTUMN,
    WINTER,
    ;

    companion object {
        fun of(date: LocalDate): Season = when (date.monthNumber) {
            3, 4, 5 -> SPRING
            6, 7, 8 -> SUMMER
            9, 10, 11 -> AUTUMN
            else -> WINTER
        }
    }
}

/**
 * Ce que l'on sait de la plante quand le diagnostic part du potager : légume, famille, date de mise en place,
 * stade, saison et emplacement. [season] est null hors climat tempéré, où les saisons ne se découpent pas ainsi.
 */
data class DiagnosticContext(
    val parcelId: String,
    val parcelName: String,
    val row: Int,
    val col: Int,
    val vegetable: Vegetable,
    val plantedOn: LocalDate?,
    val today: LocalDate,
    val season: Season?,
    val growthStage: GrowthStage?,
) {
    val family: BotanicalFamily get() = vegetable.family

    val daysSincePlanting: Int?
        get() = plantedOn?.let { daysBetween(it, today).toInt().coerceAtLeast(0) }

    companion object {
        fun from(
            parcel: Parcel,
            slot: ParcelSlot,
            vegetable: Vegetable,
            today: LocalDate,
            temperateClimate: Boolean = true,
        ): DiagnosticContext {
            val planted = slot.plantedDate?.let(::epochMillisToLocalDate)
            val stage = planted?.let {
                GrowthEngine.growth(
                    daysBetween(it, today).toInt(),
                    vegetable.daysToHarvest,
                    vegetable.harvestDurationWeeks,
                ).stage
            }
            return DiagnosticContext(
                parcelId = parcel.id,
                parcelName = parcel.name,
                row = slot.row,
                col = slot.col,
                vegetable = vegetable,
                plantedOn = planted,
                today = today,
                season = if (temperateClimate) Season.of(today) else null,
                growthStage = stage,
            )
        }
    }
}

/**
 * Causes classées pour une plante. [others] ne contient que des causes moins fréquentes sur cette plante :
 * elles restent toutes consultables, rien n'est supprimé.
 */
data class RankedCauses(val likely: List<Disease>, val others: List<Disease>)

class DiagnosticEngine(
    private val diseases: List<Disease> = DiseaseCatalog.ALL_DISEASES,
) {

    /**
     * Arbre de diagnostic multicritère combinant l'organe atteint, les symptômes observés
     * et la famille botanique de la plante pour identifier les affections probables et leurs traitements bio.
     */
    fun diagnose(
        organ: PlantOrgan? = null,
        symptoms: List<String> = emptyList(),
        family: BotanicalFamily? = null,
        catalog: List<Disease> = diseases,
    ): List<DiagnosticMatch> {
        val normalizedQueries = symptoms.map { normalize(it) }.filter { it.isNotBlank() }

        val matches = catalog.mapNotNull { disease ->
            var score = 0
            val matchingSymptoms = mutableListOf<String>()

            // 1. Évaluation de l'organe touché
            val organMatched = if (organ != null) {
                if (disease.affectedOrgans.contains(organ)) {
                    score += 30
                    true
                } else {
                    score -= 25 // Pénalité si l'organe n'est jamais touché par cette affection
                    false
                }
            } else {
                false
            }

            // 2. Évaluation de la famille botanique cible
            val familyMatched = if (family != null) {
                when {
                    disease.targetFamilies.contains(family) -> {
                        score += 35
                        true
                    }
                    disease.targetFamilies.isEmpty() -> {
                        // Ravageur ou pathogène généraliste (ex. limaces, pucerons, fonte des semis)
                        score += 15
                        true
                    }
                    else -> {
                        // Maladie hautement spécifique à d'autres familles
                        score -= 30
                        false
                    }
                }
            } else {
                false
            }

            // 3. Correspondance textuelle et sémantique des symptômes
            val allDiseaseText = buildString {
                append(disease.name).append(" ")
                disease.symptoms.forEach { append(it).append(" ") }
                disease.causes.forEach { append(it).append(" ") }
            }
            val normDiseaseText = normalize(allDiseaseText)

            for (query in normalizedQueries) {
                val queryTokens = query.split("\\s+".toRegex()).filter { it.length >= 3 }
                var matchedTokensCount = 0

                for (token in queryTokens) {
                    if (normDiseaseText.contains(token)) {
                        matchedTokensCount++
                    }
                }

                for (symptom in disease.symptoms) {
                    val normSymptom = normalize(symptom)
                    if (queryTokens.any { normSymptom.contains(it) } || normSymptom.contains(query)) {
                        if (!matchingSymptoms.contains(symptom)) {
                            matchingSymptoms.add(symptom)
                        }
                    }
                }

                score += matchedTokensCount * 15

                // Bonus pour correspondance exacte
                if (normDiseaseText.contains(query)) {
                    score += 25
                }
            }

            if (score > 0) {
                // Normalisation du score en pourcentage de confiance (plafond à 98% sans analyse labo)
                val confidence = min(98, score)
                DiagnosticMatch(
                    disease = disease,
                    score = score,
                    confidencePercent = confidence,
                    organMatched = organMatched,
                    familyMatched = familyMatched,
                    matchingSymptoms = matchingSymptoms,
                    organicRemedies = disease.organicRemedies,
                    preventiveMeasures = disease.preventiveMeasures,
                )
            } else {
                null
            }
        }

        return matches.sortedByDescending { it.score }
    }

    /**
     * Range les causes d'après la plante concernée. Sans [context] (écran docteur autonome), toutes les causes
     * de l'organe choisi sont probables, comme avant. Avec un contexte :
     * - une cause réservée à d'autres familles (doryphore, piéride...) passe dans [RankedCauses.others] ;
     * - la fonte des semis n'est probable que sur un semis ou un jeune plant ;
     * - les causes qui visent précisément la famille de la plante passent avant les causes à large spectre.
     * L'ordre du catalogue est conservé à égalité.
     */
    fun rankCauses(
        context: DiagnosticContext?,
        organ: PlantOrgan? = null,
        catalog: List<Disease> = diseases,
    ): RankedCauses {
        val byOrgan = if (organ == null) catalog else catalog.filter { organ in it.affectedOrgans }
        if (context == null) return RankedCauses(likely = byOrgan, others = emptyList())
        val (likely, others) = byOrgan.partition { isLikelyFor(it, context) }
        return RankedCauses(
            likely = likely.sortedBy { if (context.family in it.targetFamilies) 0 else 1 },
            others = others,
        )
    }

    private fun isLikelyFor(disease: Disease, context: DiagnosticContext): Boolean {
        if (disease.targetFamilies.isNotEmpty() && context.family !in disease.targetFamilies) return false
        val stage = context.growthStage
        if (disease.seedlingStageOnly && stage != null && stage != GrowthStage.SETTLING && stage != GrowthStage.YOUNG) {
            return false
        }
        return true
    }

    /**
     * Diagnostic rapide à partir d'une plante du potager.
     */
    fun diagnoseVegetable(
        vegetable: Vegetable,
        organ: PlantOrgan? = null,
        symptoms: List<String> = emptyList(),
    ): List<DiagnosticMatch> {
        return diagnose(
            organ = organ,
            symptoms = symptoms,
            family = vegetable.family,
        )
    }

    private fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input.lowercase().trim()) {
            sb.append(
                when (ch) {
                    'à', 'á', 'â', 'ã', 'ä', 'å' -> 'a'
                    'ç' -> 'c'
                    'è', 'é', 'ê', 'ë' -> 'e'
                    'ì', 'í', 'î', 'ï' -> 'i'
                    'ñ' -> 'n'
                    'ò', 'ó', 'ô', 'õ', 'ö' -> 'o'
                    'ù', 'ú', 'û', 'ü' -> 'u'
                    'ý', 'ÿ' -> 'y'
                    else -> ch
                },
            )
        }
        return sb.toString()
    }
}
