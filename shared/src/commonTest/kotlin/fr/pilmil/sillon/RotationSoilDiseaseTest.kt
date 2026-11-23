package fr.pilmil.sillon

import fr.pilmil.sillon.data.DiseaseCatalog
import fr.pilmil.sillon.data.VegetableCatalog
import fr.pilmil.sillon.engine.RotationEngine
import fr.pilmil.sillon.engine.RotationSeverity
import fr.pilmil.sillon.model.DiagnosticJournalEntry
import fr.pilmil.sillon.model.JournalStatus
import fr.pilmil.sillon.model.Parcel
import fr.pilmil.sillon.model.ParcelSlot
import fr.pilmil.sillon.model.ParcelType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RotationSoilDiseaseTest {

    private val engine = RotationEngine()

    private fun note(diseaseId: String?, year: Int = 2026, status: JournalStatus = JournalStatus.OPEN, row: Int = 0, col: Int = 0) =
        DiagnosticJournalEntry(
            id = "e-$diseaseId-$year-$row-$col",
            parcelId = "p1",
            row = row,
            col = col,
            vegetableId = "tomate",
            diseaseId = diseaseId,
            noticedOn = LocalDate(year, 8, 15),
            status = status,
        )

    private fun soilIssues(candidateId: String, year: Int, journal: List<DiagnosticJournalEntry>) =
        engine.checkRotation(VegetableCatalog.getById(candidateId)!!, "p1", 0, 0, year, emptyList(), journal)
            .filter { it.severity == RotationSeverity.SOIL_DISEASE }

    @Test
    fun mildewNotedIn2026WarnsTomatoAndPotatoIn2027() {
        val journal = listOf(note("mildiou"))
        assertTrue(soilIssues("tomate", 2027, journal).isNotEmpty())
        assertTrue(soilIssues("pomme_de_terre", 2027, journal).isNotEmpty())
        val message = soilIssues("tomate", 2027, journal).single().message
        assertTrue("Mildiou" in message && "2026" in message && "2029" in message, message)
    }

    @Test
    fun noWarningInTheYearOfTheNoteItself() {
        assertTrue(soilIssues("tomate", 2026, listOf(note("mildiou"))).isEmpty())
    }

    @Test
    fun warningLastsExactlyTheSourcedNumberOfYears() {
        val years = DiseaseCatalog.MILDIOU.soilCarryover!!.years
        val journal = listOf(note("mildiou"))
        assertTrue(soilIssues("tomate", 2026 + years, journal).isNotEmpty())
        assertTrue(soilIssues("tomate", 2026 + years + 1, journal).isEmpty())
    }

    @Test
    fun otherFamiliesAreNotWarned() {
        val journal = listOf(note("mildiou"))
        assertTrue(soilIssues("carotte", 2027, journal).isEmpty())
        assertTrue(soilIssues("haricot_nain", 2027, journal).isEmpty())
    }

    @Test
    fun uncertainCauseAndDiseasesWithoutSourcedDurationNeverWarn() {
        assertTrue(soilIssues("tomate", 2027, listOf(note(null))).isEmpty())
        assertNull(DiseaseCatalog.ANTHRACNOSE.soilCarryover)
        assertTrue(soilIssues("haricot_nain", 2027, listOf(note("anthracnose"))).isEmpty())
        assertTrue(soilIssues("tomate", 2027, listOf(note("pucerons"))).isEmpty())
    }

    @Test
    fun warningDoesNotDependOnHowTheFollowUpEnded() {
        for (status in JournalStatus.entries) {
            assertTrue(soilIssues("tomate", 2027, listOf(note("alternariose", status = status))).isNotEmpty(), "statut $status")
        }
    }

    @Test
    fun onlyTheSameSlotIsConcerned() {
        val journal = listOf(note("mildiou", col = 1))
        assertTrue(soilIssues("tomate", 2027, journal).isEmpty())
        assertEquals(1, engine.soilWarnings("p1", 0, 1, 2027, journal).size)
    }

    @Test
    fun mostRecentNoteOfTheSameDiseaseExtendsTheWindow() {
        val journal = listOf(note("alternariose", 2026), note("alternariose", 2027))
        val warnings = engine.soilWarnings("p1", 0, 0, 2029, journal)
        assertEquals(1, warnings.size)
        assertEquals(2027, warnings.single().notedYear)
        assertEquals(2029, warnings.single().untilYear)
    }

    @Test
    fun everyCarryoverCarriesASourceAndAPositiveDuration() {
        val withCarryover = DiseaseCatalog.ALL_DISEASES.filter { it.soilCarryover != null }
        assertTrue(withCarryover.map { it.id }.containsAll(listOf("mildiou", "alternariose")))
        for (disease in withCarryover) {
            val carryover = disease.soilCarryover!!
            assertTrue(carryover.years in 1..10, disease.id)
            assertTrue(carryover.source.isNotBlank(), disease.id)
            assertTrue(carryover.families.isNotEmpty(), disease.id)
        }
    }

    @Test
    fun evaluateParcelReportsTheSoilWarningOfOccupiedSlots() {
        val parcel = Parcel("p1", "Carré", ParcelType.RAISED_BED_SQUARES, 2, 2, listOf(ParcelSlot(0, 0, "tomate", null)))
        val issues = engine.evaluateParcel(parcel, 2027, emptyList(), journal = listOf(note("mildiou")))
        assertNotNull(issues.firstOrNull { it.severity == RotationSeverity.SOIL_DISEASE })
        assertTrue(engine.evaluateParcel(parcel, 2027, emptyList()).none { it.severity == RotationSeverity.SOIL_DISEASE })
    }
}
