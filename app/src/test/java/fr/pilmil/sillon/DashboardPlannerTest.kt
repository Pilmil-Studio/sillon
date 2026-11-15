package fr.pilmil.sillon

import fr.pilmil.sillon.data.VegetableCatalog
import fr.pilmil.sillon.engine.DashboardPlanner
import fr.pilmil.sillon.engine.FrostEngine
import fr.pilmil.sillon.engine.FrostStatus
import fr.pilmil.sillon.model.Climate
import fr.pilmil.sillon.model.FrostDate
import fr.pilmil.sillon.model.Garden
import fr.pilmil.sillon.model.GrowingEnvironment
import fr.pilmil.sillon.model.LightSource
import fr.pilmil.sillon.model.LocalWeather
import fr.pilmil.sillon.model.Orientation
import fr.pilmil.sillon.model.WindExposure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.datetime.LocalDate

class DashboardPlannerTest {

    private val frost = FrostDate(5, 15)
    private val emptyGarden = Garden(id = "g", name = "Test", frostDate = frost)

    @Test
    fun frostAheadCountsDownThisYear() {
        val status = DashboardPlanner.frostStatus(frost, LocalDate(2026, 5, 1))
        assertEquals(FrostStatus.Upcoming(LocalDate(2026, 5, 15), 14), status)
    }

    @Test
    fun frostJustPassedIsShownAsPassed() {
        val status = DashboardPlanner.frostStatus(frost, LocalDate(2026, 6, 1))
        assertEquals(FrostStatus.Passed(LocalDate(2026, 5, 15), 17), status)
    }

    @Test
    fun frostLongPassedCountsDownToNextYear() {
        val status = DashboardPlanner.frostStatus(frost, LocalDate(2026, 11, 8))
        assertTrue(status is FrostStatus.Upcoming)
        assertEquals(LocalDate(2027, 5, 15), (status as FrostStatus.Upcoming).date)
    }

    @Test
    fun springDashboardListsTomatoSowing() {
        // 15 mai - 8 semaines = 20 mars : semis de tomates au chaud.
        val plan = DashboardPlanner.plan(emptyGarden, LocalDate(2026, 3, 20))
        assertTrue(plan.sowIndoors.any { it.vegetable.id == "tomate" })
        assertNull(plan.nextWorkFrom)
    }

    @Test
    fun novemberDashboardNeverSuggestsTomatoes() {
        val plan = DashboardPlanner.plan(emptyGarden, LocalDate(2026, 11, 8))
        assertTrue(plan.sowIndoors.none { it.vegetable.id == "tomate" })
    }

    @Test
    fun deadSeasonPointsToNextWork() {
        val plan = DashboardPlanner.plan(emptyGarden, LocalDate(2026, 12, 20))
        if (plan.isEmpty) {
            assertNotNull(plan.nextWorkFrom)
            assertTrue(plan.nextWorkFrom!! > LocalDate(2026, 12, 20))
        }
    }

    @Test
    fun balconyGardenKeepsFrostScheduleAndAddsWateringNote() {
        val balcony = emptyGarden.copy(
            environment = GrowingEnvironment.BALCONY,
            orientation = Orientation.S,
            windExposure = WindExposure.HIGH,
            covered = true,
        )
        val plan = DashboardPlanner.plan(balcony, LocalDate(2026, 3, 20))
        assertTrue(plan.sowIndoors.any { it.vegetable.id == "tomate" })
        assertNotNull(plan.wateringReminderNote)
        assertTrue(plan.wateringReminderNote!!.contains("Balcon couvert"))
        assertTrue(plan.wateringReminderNote!!.contains("Sud"))
    }

    @Test
    fun indoorGardenIsFrostFreeAndAllowsYearRoundSowingOfSuitableCrops() {
        val indoor = emptyGarden.copy(
            environment = GrowingEnvironment.INDOOR,
            lightSource = LightSource.GROW_LIGHT,
            growLightHoursPerDay = 14,
        )
        // Même en plein hiver (20 décembre), les semis d'intérieur sont ouverts
        val plan = DashboardPlanner.plan(indoor, LocalDate(2026, 12, 20))
        assertEquals(FrostStatus.FrostFree, plan.frost)
        assertFalse(plan.isEmpty)
        assertTrue(plan.sowIndoors.any { it.vegetable.id == "basilic" })
        assertTrue(plan.sowIndoors.any { it.vegetable.id == "radis" })
        assertTrue(plan.sowIndoors.none { it.vegetable.id == "pomme_de_terre" })
        assertTrue(plan.sowOutdoors.isEmpty())
        assertTrue(plan.transplant.isEmpty())
        assertNull(plan.nextWorkFrom)
        assertNotNull(plan.wateringReminderNote)
        assertTrue(plan.wateringReminderNote!!.contains("14 h/j"))
    }

    @Test
    fun altitudeShiftsTemperateFrostDate() {
        assertEquals(0, DashboardPlanner.temperateFrostShiftDays(0))
        assertEquals(0, DashboardPlanner.temperateFrostShiftDays(200))
        assertEquals(14, DashboardPlanner.temperateFrostShiftDays(600))
        val highGarden = emptyGarden.copy(altitudeMeters = 600)
        val plan = DashboardPlanner.plan(highGarden, LocalDate(2026, 5, 1))
        assertEquals(FrostStatus.Upcoming(LocalDate(2026, 5, 29), 28), plan.frost)
        assertNotNull(plan.altitudeNote)
        assertTrue(plan.altitudeNote!!.contains("+14 j"))
    }

    @Test
    fun tropicalAltitudeAddsCoolingNoteWithoutShiftingAnchor() {
        val reunionHigh = Garden(id = "g974h", name = "Salazie", frostDate = FrostDate(11, 1), altitudeMeters = 800)
        val plan = DashboardPlanner.plan(reunionHigh, LocalDate(2026, 11, 1), FrostEngine(climate = Climate.TROPICAL))
        assertEquals(FrostStatus.Upcoming(LocalDate(2026, 11, 1), 0), plan.frost)
        assertNotNull(plan.altitudeNote)
        assertTrue(plan.altitudeNote!!.contains("800 m"))
    }

    @Test
    fun wateringNoteAdaptsToMulchAndLocalWeather() {
        val dryNoMulch = emptyGarden.copy(mulched = false, localWeather = LocalWeather.DRY_HOT)
        val dryPlan = DashboardPlanner.plan(dryNoMulch, LocalDate(2026, 6, 1))
        assertNotNull(dryPlan.wateringReminderNote)
        assertTrue(dryPlan.wateringReminderNote!!.contains("sans paillage"))

        val rainy = emptyGarden.copy(mulched = true, localWeather = LocalWeather.RAINY)
        val rainyPlan = DashboardPlanner.plan(rainy, LocalDate(2026, 6, 1))
        assertNotNull(rainyPlan.wateringReminderNote)
        assertTrue(rainyPlan.wateringReminderNote!!.contains("pluvieux"))
    }

    @Test
    fun heirloomVarietiesArePopulatedForKeyCrops() {
        assertTrue(VegetableCatalog.getById("tomate")!!.heirloomVarieties.isNotEmpty())
        assertTrue(VegetableCatalog.getById("haricot_nain")!!.heirloomVarieties.isNotEmpty())
        assertTrue(VegetableCatalog.getById("carotte")!!.heirloomVarieties.isNotEmpty())
    }
}
