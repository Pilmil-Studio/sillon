package fr.pilmil.sillon

import fr.pilmil.sillon.data.FrenchFrostCatalog
import fr.pilmil.sillon.engine.DashboardPlanner
import fr.pilmil.sillon.engine.FrostEngine
import fr.pilmil.sillon.engine.FrostStatus
import fr.pilmil.sillon.model.Climate
import fr.pilmil.sillon.model.FrostDate
import fr.pilmil.sillon.model.Garden
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CustomFrostEngineTest {

    private val today = LocalDate(2026, 4, 1)

    private fun gardenOf(code: String) =
        Garden(id = "g", name = "Test", frostDate = FrenchFrostCatalog.getFrostDate(code))

    @Test
    fun departmentModeGivesTheSamePlanWhetherOrNotCustomDatesAreStored() {
        listOf("29", "75", "05", "13", "2A").forEach { code ->
            val plain = gardenOf(code)
            val withStoredCustom = plain.copy(customFrostDate = FrostDate(7, 1), useCustomFrost = false)
            assertEquals(plain.frostDate, withStoredCustom.activeFrostDate, code)
            assertEquals(DashboardPlanner.plan(plain, today), DashboardPlanner.plan(withStoredCustom, today), code)
        }
    }

    @Test
    fun customDatesReplaceTheDepartmentDateInThePlan() {
        val garden = gardenOf("13").copy(customFrostDate = FrostDate(6, 10), useCustomFrost = true)
        val plan = DashboardPlanner.plan(garden, today)
        assertEquals(FrostStatus.Upcoming(LocalDate(2026, 6, 10), 70), plan.frost)

        val sameDateAsDepartment = Garden(id = "g", name = "Test", frostDate = FrostDate(6, 10))
        assertEquals(DashboardPlanner.plan(sameDateAsDepartment, today), plan)
    }

    @Test
    fun switchingBackRestoresTheDepartmentPlan() {
        val department = gardenOf("29")
        val custom = department.copy(customFrostDate = FrostDate(6, 10), useCustomFrost = true)
        val back = custom.copy(useCustomFrost = false)
        assertEquals(DashboardPlanner.plan(department, today), DashboardPlanner.plan(back, today))
        assertEquals(FrostDate(6, 10), back.customFrostDate)
    }

    @Test
    fun customModeWithoutADateFallsBackToTheDepartment() {
        val garden = gardenOf("29").copy(useCustomFrost = true)
        assertEquals(garden.frostDate, garden.activeFrostDate)
        assertEquals(Climate.TROPICAL, garden.climateFor(Climate.TROPICAL))
    }

    @Test
    fun customDatesMakeTheClimateTemperate() {
        val garden = gardenOf("974").copy(customFrostDate = FrostDate(3, 15), useCustomFrost = true)
        assertEquals(Climate.TEMPERATE, garden.climateFor(Climate.TROPICAL))
        assertEquals(Climate.TROPICAL, garden.copy(useCustomFrost = false).climateFor(Climate.TROPICAL))
    }

    @Test
    fun leapDayAnchorFallsOnFebruary28InACommonYear() {
        val engine = FrostEngine()
        assertEquals(LocalDate(2028, 2, 29), engine.getLastFrostDate(FrostDate(2, 29), 2028))
        assertEquals(LocalDate(2027, 2, 28), engine.getLastFrostDate(FrostDate(2, 29), 2027))
        assertEquals(LocalDate(2026, 4, 30), engine.getLastFrostDate(FrostDate(4, 30), 2026))
        assertEquals(LocalDate(2026, 4, 30), engine.getLastFrostDate(FrostDate(4, 31), 2026))
    }

    @Test
    fun planWithALeapDayDateDoesNotFail() {
        val garden = gardenOf("75").copy(customFrostDate = FrostDate(2, 29), useCustomFrost = true)
        val plan = DashboardPlanner.plan(garden, LocalDate(2026, 2, 10))
        assertTrue(plan.frost is FrostStatus.Upcoming)
    }
}
