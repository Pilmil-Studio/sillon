package fr.pilmil.sillon

import fr.pilmil.sillon.engine.AmbientLightCategory
import fr.pilmil.sillon.engine.DiagnosticEngine
import fr.pilmil.sillon.engine.LightEstimator
import fr.pilmil.sillon.model.BotanicalFamily
import fr.pilmil.sillon.model.PlantOrgan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticAndLightEdgeCasesTest {

    private val engine = DiagnosticEngine()

    @Test
    fun diagnoseWithoutCriteriaReturnsEmptyList() {
        assertTrue(engine.diagnose().isEmpty())
        assertTrue(engine.diagnose(symptoms = listOf("   ", "")).isEmpty())
    }

    @Test
    fun diagnoseNormalizesAccentsCaseAndIgnoresShortTokens() {
        // "tâches brunes huileuses" avec majuscules, accents et mots courts ("de", "au", "la")
        val normalized = engine.diagnose(
            organ = PlantOrgan.LEAF,
            symptoms = listOf("  TÂCHES BRUNES HUILEUSES de la  "),
            family = BotanicalFamily.SOLANACEAE,
        )
        assertTrue(normalized.isNotEmpty())
        assertEquals("mildiou", normalized.first().disease.id)

        // Uniquement des mots de < 3 lettres : aucun bonus textuel, mais organe+famille restent scorés
        val shortOnly = engine.diagnose(
            organ = PlantOrgan.LEAF,
            symptoms = listOf("de au la"),
            family = BotanicalFamily.SOLANACEAE,
        )
        assertTrue(shortOnly.all { it.matchingSymptoms.isEmpty() })
    }

    @Test
    fun diagnoseCapsConfidenceAt98DeduplicatesSymptomsAndSortsDescending() {
        val results = engine.diagnose(
            organ = PlantOrgan.LEAF,
            symptoms = listOf(
                "Taches brunes d'aspect huileux sur le dessus des feuilles",
                "taches brunes huileux",
                "Duvet blanc grisâtre au revers des feuilles",
                "Nécrose et dessèchement rapide",
            ),
            family = BotanicalFamily.SOLANACEAE,
        )
        assertTrue(results.isNotEmpty())
        val top = results.first()
        assertEquals("mildiou", top.disease.id)
        assertEquals(98, top.confidencePercent)
        assertEquals(top.matchingSymptoms.distinct(), top.matchingSymptoms)
        assertEquals(results.sortedByDescending { it.score }, results)
    }

    @Test
    fun diagnosePenalizesMismatchedOrganAndFamily() {
        // Cul noir touche uniquement FRUIT et SOLANACEAE : sur ROOT + APIACEAE il reçoit -25 -30 = -55 et doit être exclu
        val results = engine.diagnose(
            organ = PlantOrgan.ROOT,
            family = BotanicalFamily.APIACEAE,
        )
        assertTrue(results.none { it.disease.id == "cul_noir" })
    }

    @Test
    fun lightEstimatorHandlesNegativeValuesAndExactThresholds() {
        assertEquals(AmbientLightCategory.LOW, LightEstimator.classifyLux(-50f))
        assertEquals("0 lx · ${AmbientLightCategory.LOW.frenchLabel}", LightEstimator.formatSummary(-50f))
        assertEquals(AmbientLightCategory.LOW, LightEstimator.classifyLux(0f))
        assertEquals(AmbientLightCategory.LOW, LightEstimator.classifyLux(999.9f))
        assertEquals(AmbientLightCategory.MEDIUM, LightEstimator.classifyLux(1_000f))
        assertEquals(AmbientLightCategory.MEDIUM, LightEstimator.classifyLux(4_999.9f))
        assertEquals(AmbientLightCategory.BRIGHT_INDIRECT, LightEstimator.classifyLux(5_000f))
        assertEquals(AmbientLightCategory.BRIGHT_INDIRECT, LightEstimator.classifyLux(14_999.9f))
        assertEquals(AmbientLightCategory.DIRECT_SUN, LightEstimator.classifyLux(15_000f))
    }
}
