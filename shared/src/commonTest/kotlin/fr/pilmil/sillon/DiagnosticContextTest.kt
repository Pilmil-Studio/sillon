package fr.pilmil.sillon

import fr.pilmil.sillon.data.DiseaseCatalog
import fr.pilmil.sillon.data.VegetableCatalog
import fr.pilmil.sillon.engine.DiagnosticContext
import fr.pilmil.sillon.engine.DiagnosticEngine
import fr.pilmil.sillon.engine.Season
import fr.pilmil.sillon.model.Parcel
import fr.pilmil.sillon.model.ParcelSlot
import fr.pilmil.sillon.model.ParcelType
import fr.pilmil.sillon.model.PlantOrgan
import fr.pilmil.sillon.model.Vegetable
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagnosticContextTest {

    private val engine = DiagnosticEngine()
    private val planted = 1_778_414_400_000L // 10 mai 2026, midi UTC

    private fun contextFor(vegetable: Vegetable, plantedDate: Long? = planted, today: LocalDate = LocalDate(2026, 5, 20)): DiagnosticContext {
        val slot = ParcelSlot(row = 1, col = 2, vegetableId = vegetable.id, plantedDate = plantedDate)
        val parcel = Parcel("p1", "Carré 1", ParcelType.RAISED_BED_SQUARES, 3, 3, listOf(slot))
        return DiagnosticContext.from(parcel, slot, vegetable, today)
    }

    private fun ids(list: List<fr.pilmil.sillon.model.Disease>) = list.map { it.id }

    @Test
    fun doryphoreIsLikelyOnSolanaceaeAndSetAsideOnOtherFamilies() {
        val onPotato = engine.rankCauses(contextFor(VegetableCatalog.POMME_DE_TERRE))
        assertTrue("doryphore" in ids(onPotato.likely))
        assertFalse("doryphore" in ids(onPotato.others))

        val onCarrot = engine.rankCauses(contextFor(VegetableCatalog.CAROTTE))
        assertFalse("doryphore" in ids(onCarrot.likely))
        assertTrue("doryphore" in ids(onCarrot.others))
    }

    @Test
    fun pierideIsLikelyOnBrassicaceaeOnly() {
        assertTrue("pieride_chou" in ids(engine.rankCauses(contextFor(VegetableCatalog.CHOU_POMME)).likely))
        assertTrue("pieride_chou" in ids(engine.rankCauses(contextFor(VegetableCatalog.TOMATE)).others))
    }

    @Test
    fun noCauseIsEverRemoved() {
        for (vegetable in VegetableCatalog.ALL_VEGETABLES) {
            val ranked = engine.rankCauses(contextFor(vegetable))
            assertEquals(
                DiseaseCatalog.ALL_DISEASES.map { it.id }.toSet(),
                (ids(ranked.likely) + ids(ranked.others)).toSet(),
                "causes perdues pour ${vegetable.id}",
            )
        }
    }

    @Test
    fun organFilterStillAppliesToBothLists() {
        val ranked = engine.rankCauses(contextFor(VegetableCatalog.TOMATE), organ = PlantOrgan.ROOT)
        assertTrue((ranked.likely + ranked.others).all { PlantOrgan.ROOT in it.affectedOrgans })
        assertTrue((ranked.likely + ranked.others).isNotEmpty())
    }

    @Test
    fun specificCausesComeBeforeBroadOnes() {
        val likely = engine.rankCauses(contextFor(VegetableCatalog.POMME_DE_TERRE)).likely
        val firstBroad = likely.indexOfFirst { it.targetFamilies.isEmpty() }
        val lastSpecific = likely.indexOfLast { it.targetFamilies.isNotEmpty() }
        assertTrue(firstBroad > lastSpecific, "les causes visant la famille doivent passer avant les causes larges")
    }

    @Test
    fun standaloneDoctorKeepsEveryCauseAsLikely() {
        val ranked = engine.rankCauses(context = null)
        assertEquals(DiseaseCatalog.ALL_DISEASES, ranked.likely)
        assertTrue(ranked.others.isEmpty())
    }

    @Test
    fun dampingOffOnlyForSeedlingsAndYoungPlants() {
        // Tomate plantée le 10 mai : le 20 mai (10 jours) c'est un jeune plant, le 15 septembre c'est une plante adulte.
        val young = contextFor(VegetableCatalog.TOMATE, today = LocalDate(2026, 5, 20))
        val adult = contextFor(VegetableCatalog.TOMATE, today = LocalDate(2026, 8, 20))
        assertTrue("fonte_des_semis" in ids(engine.rankCauses(young).likely))
        assertTrue("fonte_des_semis" in ids(engine.rankCauses(adult).others))
    }

    @Test
    fun unknownPlantingDateDoesNotDemoteSeedlingCauses() {
        val context = contextFor(VegetableCatalog.TOMATE, plantedDate = null)
        assertNull(context.plantedOn)
        assertNull(context.daysSincePlanting)
        assertTrue("fonte_des_semis" in ids(engine.rankCauses(context).likely))
    }

    @Test
    fun contextCarriesPlantFamilyEmplacementAndSeason() {
        val context = contextFor(VegetableCatalog.TOMATE)
        assertEquals(VegetableCatalog.TOMATE.family, context.family)
        assertEquals("p1", context.parcelId)
        assertEquals(1, context.row)
        assertEquals(2, context.col)
        assertEquals(Season.SPRING, context.season)
        assertEquals(10, context.daysSincePlanting)
        assertNotNull(context.growthStage)
    }

    @Test
    fun tropicalClimateHasNoSeason() {
        val slot = ParcelSlot(0, 0, "tomate", planted)
        val parcel = Parcel("p1", "Carré", ParcelType.RAISED_BED_SQUARES, 1, 1, listOf(slot))
        val context = DiagnosticContext.from(parcel, slot, VegetableCatalog.TOMATE, LocalDate(2026, 5, 20), temperateClimate = false)
        assertNull(context.season)
    }

    @Test
    fun seasonsFollowMeteorologicalMonths() {
        assertEquals(Season.WINTER, Season.of(LocalDate(2026, 12, 1)))
        assertEquals(Season.WINTER, Season.of(LocalDate(2026, 2, 28)))
        assertEquals(Season.SPRING, Season.of(LocalDate(2026, 3, 1)))
        assertEquals(Season.SUMMER, Season.of(LocalDate(2026, 8, 31)))
        assertEquals(Season.AUTUMN, Season.of(LocalDate(2026, 11, 30)))
    }
}
