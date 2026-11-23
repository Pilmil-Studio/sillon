package fr.pilmil.sillon

import fr.pilmil.sillon.data.DiseaseCatalog
import fr.pilmil.sillon.engine.LeafSpotQuestion
import fr.pilmil.sillon.engine.LeafSpotTriage
import fr.pilmil.sillon.engine.TriageAnswer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeafSpotTriageTest {

    private val leads = listOf("mildiou" to 0.36f, "alternariose" to 0.32f, "autre" to 0.2f)

    @Test
    fun triageIsNeededOnlyWhenBothAreTopTwoAndCloseTogether() {
        assertTrue(LeafSpotTriage.needsTriage(leads))
        assertTrue(LeafSpotTriage.needsTriage(listOf("alternariose" to 0.3f, "mildiou" to 0.28f)))
        assertFalse(LeafSpotTriage.needsTriage(listOf("mildiou" to 0.55f, "alternariose" to 0.25f)))
        assertFalse(LeafSpotTriage.needsTriage(listOf("mildiou" to 0.36f, "autre" to 0.33f, "alternariose" to 0.3f)))
        assertFalse(LeafSpotTriage.needsTriage(listOf("mildiou" to 0.5f)))
        assertFalse(LeafSpotTriage.needsTriage(emptyList()))
    }

    @Test
    fun cluesPointToTheDiseaseWhoseSheetDescribesThem() {
        assertEquals("mildiou", LeafSpotTriage.target(LeafSpotQuestion.WHITE_FUZZ_OR_OILY))
        assertEquals("alternariose", LeafSpotTriage.target(LeafSpotQuestion.CONCENTRIC_RINGS))
    }

    @Test
    fun cluesAreStillWrittenInTheCatalogSheets() {
        val mildiou = DiseaseCatalog.MILDIOU.symptoms.joinToString(" ").lowercase()
        val alternariose = DiseaseCatalog.ALTERNARIOSE.symptoms.joinToString(" ").lowercase()
        assertTrue("duvet" in mildiou && "huileux" in mildiou)
        assertTrue("concentriques" in alternariose && "cible" in alternariose)
    }

    @Test
    fun yesOnWhiteFuzzFavoursMildewAndKeepsTheCombinedMass() {
        val (m, a) = LeafSpotTriage.combine(0.3f, 0.3f, mapOf(LeafSpotQuestion.WHITE_FUZZ_OR_OILY to TriageAnswer.YES))
        assertTrue(m > a)
        assertEquals(0.6f, m + a, 1e-5f)
        assertEquals(0.45f, m, 1e-5f) // 3 : 1 sur une masse de 0,6
    }

    @Test
    fun yesOnTargetRingsFavoursEarlyBlight() {
        val (m, a) = LeafSpotTriage.combine(0.3f, 0.3f, mapOf(LeafSpotQuestion.CONCENTRIC_RINGS to TriageAnswer.YES))
        assertTrue(a > m)
    }

    @Test
    fun noIsWeakerThanYesAndUnknownChangesNothing() {
        val no = LeafSpotTriage.combine(0.3f, 0.3f, mapOf(LeafSpotQuestion.CONCENTRIC_RINGS to TriageAnswer.NO))
        assertTrue(no.first > no.second)
        val yes = LeafSpotTriage.combine(0.3f, 0.3f, mapOf(LeafSpotQuestion.CONCENTRIC_RINGS to TriageAnswer.YES))
        assertTrue(Math.abs(yes.first - yes.second) > Math.abs(no.first - no.second))
        val unknown = LeafSpotTriage.combine(0.36f, 0.32f, mapOf(
            LeafSpotQuestion.WHITE_FUZZ_OR_OILY to TriageAnswer.UNKNOWN,
            LeafSpotQuestion.CONCENTRIC_RINGS to TriageAnswer.UNKNOWN,
        ))
        assertEquals(0.36f, unknown.first, 1e-5f)
        assertEquals(0.32f, unknown.second, 1e-5f)
    }

    @Test
    fun twoAgreeingAnswersAddUp() {
        val (m, a) = LeafSpotTriage.combine(0.3f, 0.3f, mapOf(
            LeafSpotQuestion.WHITE_FUZZ_OR_OILY to TriageAnswer.YES,
            LeafSpotQuestion.CONCENTRIC_RINGS to TriageAnswer.NO,
        ))
        assertTrue(m / (m + a) > 0.8f)
    }

    @Test
    fun updatedLeadsAreSortedAndOtherLeadsUntouched() {
        val updated = LeafSpotTriage.updatedLeads(leads, mapOf(LeafSpotQuestion.CONCENTRIC_RINGS to TriageAnswer.YES))
        assertEquals("alternariose", updated.first().first)
        assertEquals(0.2f, updated.first { it.first == "autre" }.second, 0f)
        assertEquals(leads, LeafSpotTriage.updatedLeads(leads, emptyMap()).sortedByDescending { it.second })
    }

    @Test
    fun withoutBothLeadsNothingChanges() {
        val only = listOf("oidium" to 0.4f)
        assertEquals(only, LeafSpotTriage.updatedLeads(only, mapOf(LeafSpotQuestion.CONCENTRIC_RINGS to TriageAnswer.YES)))
    }
}
