package fr.pilmil.sillon

import fr.pilmil.sillon.data.FrenchFrostCatalog
import fr.pilmil.sillon.data.SillonPrefs
import fr.pilmil.sillon.engine.CompanionEngine
import fr.pilmil.sillon.engine.RotationEngine
import fr.pilmil.sillon.model.BotanicalFamily
import fr.pilmil.sillon.model.Climate
import fr.pilmil.sillon.model.CropHistoryEntry
import fr.pilmil.sillon.model.FrostDate
import fr.pilmil.sillon.model.Parcel
import fr.pilmil.sillon.model.ParcelSlot
import fr.pilmil.sillon.model.ParcelType
import fr.pilmil.sillon.model.daysBetween
import fr.pilmil.sillon.model.formatDayMonthFr
import fr.pilmil.sillon.model.minusDays
import fr.pilmil.sillon.model.minusWeeks
import fr.pilmil.sillon.model.plusDays
import fr.pilmil.sillon.model.plusWeeks
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnginesAndCatalogEdgeCasesTest {

    @Test
    fun companionEngineReturnsNullForUnknownIdsAndScoresOrthogonalVsDiagonal() {
        val engine = CompanionEngine()
        assertNull(engine.evaluatePair("inconnu", "tomate"))
        assertNull(engine.evaluatePair("tomate", "inconnu"))

        // Carré 2x2 :
        // (0,0)=carotte  (0,1)=poireau   -> synergie orthogonale (+3)
        // (1,0)=vide     (1,1)=vide
        val orthoSynergy = Parcel(
            id = "p1",
            name = "P1",
            type = ParcelType.RAISED_BED_SQUARES,
            rows = 2,
            cols = 2,
            slots = listOf(ParcelSlot(0, 0, "carotte"), ParcelSlot(0, 1, "poireau")),
        )
        assertEquals(3, engine.evaluateParcel(orthoSynergy).companionScore)

        // Diagonale : (0,0)=carotte, (1,1)=poireau -> synergie diagonale (+2)
        val diagSynergy = orthoSynergy.copy(
            slots = listOf(ParcelSlot(0, 0, "carotte"), ParcelSlot(1, 1, "poireau")),
        )
        assertEquals(2, engine.evaluateParcel(diagSynergy).companionScore)

        // Antagonisme orthogonal : tomate + pomme_de_terre -> -6
        val orthoAntagonism = orthoSynergy.copy(
            slots = listOf(ParcelSlot(0, 0, "tomate"), ParcelSlot(0, 1, "pomme_de_terre")),
        )
        assertEquals(-6, engine.evaluateParcel(orthoAntagonism).companionScore)

        // Antagonisme diagonal : tomate + pomme_de_terre -> -4
        val diagAntagonism = orthoSynergy.copy(
            slots = listOf(ParcelSlot(0, 0, "tomate"), ParcelSlot(1, 1, "pomme_de_terre")),
        )
        assertEquals(-4, engine.evaluateParcel(diagAntagonism).companionScore)
    }

    @Test
    fun rotationEngineSkipsContainerParcelsAndSuggestsSuccessorsForAllNutrientLevels() {
        val engine = RotationEngine()
        val history = listOf(
            CropHistoryEntry("p_pot", 0, 0, 2025, BotanicalFamily.SOLANACEAE, "tomate"),
        )
        for (type in listOf(ParcelType.CONTAINER, ParcelType.WINDOWSILL, ParcelType.GROW_LIGHT_SHELF)) {
            val containerParcel = Parcel(
                id = "p_pot",
                name = "Pot",
                type = type,
                rows = 1,
                cols = 1,
                slots = listOf(ParcelSlot(0, 0, "tomate")),
            )
            assertTrue(engine.evaluateParcel(containerParcel, 2026, history).isEmpty())
        }

        assertEquals(
            listOf(BotanicalFamily.FABACEAE, BotanicalFamily.APIACEAE, BotanicalFamily.ALLIACEAE),
            engine.getSuggestedSuccessorFamilies(BotanicalFamily.SOLANACEAE),
        )
        assertEquals(
            listOf(BotanicalFamily.ALLIACEAE, BotanicalFamily.FABACEAE, BotanicalFamily.ASTERACEAE),
            engine.getSuggestedSuccessorFamilies(BotanicalFamily.APIACEAE),
        )
        assertEquals(
            listOf(BotanicalFamily.FABACEAE, BotanicalFamily.SOLANACEAE, BotanicalFamily.BRASSICACEAE),
            engine.getSuggestedSuccessorFamilies(BotanicalFamily.ALLIACEAE),
        )
        assertEquals(
            listOf(BotanicalFamily.SOLANACEAE, BotanicalFamily.BRASSICACEAE, BotanicalFamily.CUCURBITACEAE),
            engine.getSuggestedSuccessorFamilies(BotanicalFamily.FABACEAE),
        )
    }

    @Test
    fun frenchFrostCatalogNormalizesCodesAndFallsBackToDefault() {
        assertEquals("Ain", FrenchFrostCatalog.getByCode("1")?.name)
        assertEquals("Corse-du-Sud", FrenchFrostCatalog.getByCode(" 2a ")?.name)
        assertEquals(FrostDate(4, 25), FrenchFrostCatalog.getFrostDate("1"))
        assertEquals(FrenchFrostCatalog.DEFAULT_FROST_DATE, FrenchFrostCatalog.getFrostDate("ZZ"))
    }

    @Test
    fun dateCompatHelpersAndFrenchMonthFormatting() {
        val base = LocalDate(2026, 5, 15)
        assertEquals(LocalDate(2026, 5, 22), base.plusDays(7))
        assertEquals(LocalDate(2026, 5, 8), base.minusDays(7))
        assertEquals(LocalDate(2026, 5, 29), base.plusWeeks(2))
        assertEquals(LocalDate(2026, 5, 1), base.minusWeeks(2))
        assertEquals(14L, daysBetween(base, base.plusWeeks(2)))
        assertEquals("15 mai", formatDayMonthFr(base))
        assertEquals("1 janvier", formatDayMonthFr(LocalDate(2026, 1, 1)))
        assertEquals("31 décembre", formatDayMonthFr(LocalDate(2026, 12, 31)))
    }

    @Test
    fun sillonPrefsDerivesDepartmentAndClimate() {
        val prefs = object : SillonPrefs {
            override var onboarded = false
            override var departmentCode: String? = null
            override var sowingReminders = false
            override var smartViewfinderEnabled = false
            override var dashboardVisits = 0
        }
        assertNull(prefs.department)
        assertEquals(Climate.TEMPERATE, prefs.climate)

        prefs.departmentCode = "974"
        assertEquals("La Réunion", prefs.department?.name)
        assertEquals(Climate.TROPICAL, prefs.climate)

        prefs.departmentCode = "75"
        assertEquals("Paris", prefs.department?.name)
        assertEquals(Climate.TEMPERATE, prefs.climate)
    }
}
