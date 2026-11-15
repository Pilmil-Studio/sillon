package fr.pilmil.sillon

import fr.pilmil.sillon.data.VegetableCatalog
import fr.pilmil.sillon.engine.DashboardPlanner
import fr.pilmil.sillon.engine.FrostEngine
import fr.pilmil.sillon.engine.FrostStatus
import fr.pilmil.sillon.engine.GardeningAction
import fr.pilmil.sillon.model.Climate
import fr.pilmil.sillon.model.FrostDate
import fr.pilmil.sillon.model.Garden
import fr.pilmil.sillon.model.GrowingEnvironment
import fr.pilmil.sillon.model.LightSource
import fr.pilmil.sillon.model.LocalWeather
import fr.pilmil.sillon.model.Orientation
import fr.pilmil.sillon.model.Parcel
import fr.pilmil.sillon.model.ParcelSlot
import fr.pilmil.sillon.model.ParcelType
import fr.pilmil.sillon.model.plusDays
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrostAndPlannerEdgeCasesTest {

    private val frost = FrostDate(5, 15)

    @Test
    fun isWithinWindowHandlesExactBoundariesBeforeAndAfter() {
        val engine = FrostEngine()
        val target = LocalDate(2026, 5, 15)
        assertTrue(engine.isWithinWindow(target, LocalDate(2026, 5, 8), toleranceDays = 7))
        assertTrue(engine.isWithinWindow(target, LocalDate(2026, 5, 22), toleranceDays = 7))
        assertFalse(engine.isWithinWindow(target, LocalDate(2026, 5, 7), toleranceDays = 7))
        assertFalse(engine.isWithinWindow(target, LocalDate(2026, 5, 23), toleranceDays = 7))
    }

    @Test
    fun indoorYearRoundTasksFilterAndBuildWindow() {
        val engine = FrostEngine()
        val today = LocalDate(2026, 1, 15)
        val tasks = engine.getIndoorYearRoundTasks(today, toleranceDays = 5)
        assertTrue(tasks.isNotEmpty())
        assertTrue(tasks.all { it.vegetable.indoorSuitable })
        assertTrue(tasks.all { it.action == GardeningAction.SOW_INDOORS })
        assertTrue(tasks.all { it.targetDate == today && it.daysUntilTarget == 0L })
        assertTrue(tasks.all { it.windowStart == LocalDate(2026, 1, 10) && it.windowEnd == LocalDate(2026, 1, 20) })
    }

    @Test
    fun gardenHarvestTasksHandleBoundariesMissingDataAndSorting() {
        val engine = FrostEngine()
        val planted = LocalDate(2026, 4, 1)
        val plantedMillis = planted.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

        // Radis : daysToHarvest = 21..35, harvestDurationWeeks = 2 (fin à J+49)
        // Laitue : daysToHarvest = 45..65, harvestDurationWeeks = 3 (fin à J+86)
        val radis = VegetableCatalog.RADIS
        val laitue = VegetableCatalog.LAITUE
        val radisStart = planted.plusDays(radis.daysToHarvest.first.toLong())
        val radisEnd = planted.plusDays((radis.daysToHarvest.last + radis.harvestDurationWeeks * 7).toLong())

        val garden = Garden(
            id = "g",
            name = "Test",
            frostDate = frost,
            parcels = listOf(
                Parcel(
                    id = "p1",
                    name = "Carré",
                    type = ParcelType.RAISED_BED_SQUARES,
                    rows = 2,
                    cols = 2,
                    slots = listOf(
                        ParcelSlot(0, 0, "radis", plantedMillis),
                        ParcelSlot(0, 1, "laitue", plantedMillis),
                        ParcelSlot(1, 0, "tomate", null), // sans date -> ignoré
                        ParcelSlot(1, 1, "inexistant", plantedMillis), // inconnu -> ignoré
                    ),
                ),
            ),
        )

        // Avant le début de récolte du radis
        assertTrue(engine.getGardenHarvestTasks(garden, radisStart.plusDays(-1)).isEmpty())

        // Exactement au début de récolte du radis
        val atStart = engine.getGardenHarvestTasks(garden, radisStart)
        assertEquals(listOf("radis"), atStart.map { it.vegetable.id })

        // Quand les deux sont récoltables, trié par daysRemainingInHarvest croissant (radis d'abord)
        val bothActiveDate = planted.plusDays(laitue.daysToHarvest.first.toLong())
        if (bothActiveDate <= radisEnd) {
            val both = engine.getGardenHarvestTasks(garden, bothActiveDate)
            assertEquals(listOf("radis", "laitue"), both.map { it.vegetable.id })
        }

        // Exactement au dernier jour de récolte du radis
        val atRadisEnd = engine.getGardenHarvestTasks(garden, radisEnd)
        assertTrue(atRadisEnd.any { it.vegetable.id == "radis" && it.daysRemainingInHarvest == 0L })

        // Un jour après la fin du radis : seul laitue reste
        val afterRadisEnd = engine.getGardenHarvestTasks(garden, radisEnd.plusDays(1))
        assertEquals(listOf("laitue"), afterRadisEnd.map { it.vegetable.id })
    }

    @Test
    fun estimatedCatalogHarvestTasksUseTransplantOrSowOutdoorsAndSortByName() {
        val vegTransplant = VegetableCatalog.TOMATE.copy(
            id = "v_transplant",
            name = "Zucchetto",
            transplantWeeksAfterFrost = 1,
            sowOutdoorsWeeksAfterFrost = 10, // ignoré car transplantWeeksAfterFrost est prioritaire
            daysToHarvest = 20..30,
            harvestDurationWeeks = 2,
        )
        val vegDirect = VegetableCatalog.RADIS.copy(
            id = "v_direct",
            name = "Arroche",
            transplantWeeksAfterFrost = null,
            sowOutdoorsWeeksAfterFrost = 1,
            daysToHarvest = 20..30,
            harvestDurationWeeks = 2,
        )
        val vegNeither = VegetableCatalog.BASILIC.copy(
            id = "v_neither",
            name = "Orphelin",
            transplantWeeksAfterFrost = null,
            sowOutdoorsWeeksAfterFrost = null,
        )
        val engine = FrostEngine(defaultCatalog = listOf(vegTransplant, vegDirect, vegNeither))
        // 15 mai + 1 semaine = 22 mai ; + 25 jours = 16 juin (dans la fenêtre 20..44 jours)
        val tasks = engine.getEstimatedCatalogHarvestTasks(frost, LocalDate(2026, 6, 16))
        assertEquals(listOf("Arroche", "Zucchetto"), tasks.map { it.vegetable.name })
        assertTrue(tasks.all { it.action == GardeningAction.HARVEST })
    }

    @Test
    fun frostStatusHandlesExactDayZeroNinetyDayThresholdAndLeapDay() {
        assertEquals(FrostStatus.Upcoming(LocalDate(2026, 5, 15), 0), DashboardPlanner.frostStatus(frost, LocalDate(2026, 5, 15)))
        assertEquals(
            FrostStatus.Passed(LocalDate(2026, 5, 15), 90),
            DashboardPlanner.frostStatus(frost, LocalDate(2026, 5, 15).plusDays(90)),
        )
        val day91 = DashboardPlanner.frostStatus(frost, LocalDate(2026, 5, 15).plusDays(91))
        assertTrue(day91 is FrostStatus.Upcoming)
        assertEquals(LocalDate(2027, 5, 15), day91.date)

        // 29 février sur une année non bissextile (2026) -> repli au 28 février
        val leapFrost = FrostDate(2, 29)
        assertEquals(FrostStatus.Upcoming(LocalDate(2026, 2, 28), 10), DashboardPlanner.frostStatus(leapFrost, LocalDate(2026, 2, 18)))
    }

    @Test
    fun effectiveFrostDateAndAltitudeNotesAcrossClimates() {
        // 400 m -> (400 - 200) * 35 / 1000 = 7 jours ; 25 avril + 7 j = 2 mai
        assertEquals(FrostDate(5, 2), DashboardPlanner.effectiveFrostDate(FrostDate(4, 25), 400, Climate.TEMPERATE))
        // Sous les tropiques, pas de décalage de date même à 1200 m
        assertEquals(FrostDate(6, 1), DashboardPlanner.effectiveFrostDate(FrostDate(6, 1), 1200, Climate.TROPICAL))

        assertNull(DashboardPlanner.altitudeNoteFor(200, Climate.TEMPERATE))
        assertNull(DashboardPlanner.altitudeNoteFor(499, Climate.TROPICAL))
        val tropical500 = DashboardPlanner.altitudeNoteFor(500, Climate.TROPICAL)
        assertNotNull(tropical500)
        assertTrue(tropical500.contains("-3,2 °C"))
    }

    @Test
    fun wateringNoteCoversOutdoorBalconyAndIndoorVariants() {
        val base = Garden(id = "g", name = "Test", frostDate = frost)
        assertNull(DashboardPlanner.wateringNoteFor(base))

        val outdoorMulched = base.copy(mulched = true, localWeather = LocalWeather.NORMAL)
        assertTrue(DashboardPlanner.wateringNoteFor(outdoorMulched)!!.contains("Sol paillé"))

        val outdoorDryMulched = base.copy(mulched = true, localWeather = LocalWeather.DRY_HOT)
        assertTrue(DashboardPlanner.wateringNoteFor(outdoorDryMulched)!!.contains("le paillage limite l'évaporation"))

        val uncoveredBalcony = base.copy(environment = GrowingEnvironment.BALCONY, covered = false)
        assertTrue(DashboardPlanner.wateringNoteFor(uncoveredBalcony)!!.contains("sèche plus vite"))

        val indoorWindow = base.copy(
            environment = GrowingEnvironment.INDOOR,
            lightSource = LightSource.WINDOW,
            orientation = Orientation.E,
        )
        val windowNote = DashboardPlanner.wateringNoteFor(indoorWindow)!!
        assertTrue(windowNote.contains("Fenêtre (Est)"))

        val indoorNoSource = base.copy(environment = GrowingEnvironment.INDOOR, lightSource = null)
        assertTrue(DashboardPlanner.wateringNoteFor(indoorNoSource)!!.startsWith("En intérieur"))
    }

    @Test
    fun nextWorkDateReturnsNullWhenCatalogIsEmpty() {
        val emptyEngine = FrostEngine(defaultCatalog = emptyList())
        assertNull(DashboardPlanner.nextWorkDate(frost, LocalDate(2026, 1, 1), emptyEngine))
    }
}
