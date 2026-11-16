package fr.pilmil.sillon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import fr.pilmil.sillon.data.FrenchFrostCatalog
import fr.pilmil.sillon.model.Climate
import fr.pilmil.sillon.ui.garden.SignInk
import fr.pilmil.sillon.ui.garden.SkyClock
import fr.pilmil.sillon.ui.garden.SkyPhase
import fr.pilmil.sillon.ui.garden.SkyPlace
import fr.pilmil.sillon.ui.garden.SkyState
import fr.pilmil.sillon.ui.garden.signFace
import fr.pilmil.sillon.ui.garden.skyColorsFor
import fr.pilmil.sillon.ui.garden.skyPhaseAt
import fr.pilmil.sillon.ui.garden.sunTimes
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkyPhaseTest {

    private val paris = SkyPlace(48.85, 2.35)
    private val juneSolstice = LocalDate(2026, 6, 21)
    private val decemberSolstice = LocalDate(2026, 12, 21)
    private val marchEquinox = LocalDate(2026, 3, 20)
    private val septemberEquinox = LocalDate(2026, 9, 23)

    private fun hm(hour: Int, minute: Int = 0) = (hour * 60 + minute).toDouble()

    private fun dayLengthMinutes(date: LocalDate, place: SkyPlace, offset: Int): Double {
        val t = assertNotNull(sunTimes(date, place, offset))
        return t.sunsetMinute - t.sunriseMinute
    }

    @Test
    fun summerSolsticeSunTimesMatchParisAlmanac() {
        // Paris, 21 juin, heure d'été (UTC+2) : lever vers 5 h 47, coucher vers 21 h 58.
        val t = assertNotNull(sunTimes(juneSolstice, paris, 120))
        assertEquals(hm(5, 47), t.sunriseMinute, 8.0)
        assertEquals(hm(21, 58), t.sunsetMinute, 8.0)
    }

    @Test
    fun winterSolsticeSunTimesMatchParisAlmanac() {
        // Paris, 21 décembre, heure d'hiver (UTC+1) : lever vers 8 h 41, coucher vers 16 h 56.
        val t = assertNotNull(sunTimes(decemberSolstice, paris, 60))
        assertEquals(hm(8, 41), t.sunriseMinute, 8.0)
        assertEquals(hm(16, 56), t.sunsetMinute, 8.0)
    }

    @Test
    fun equinoxesHaveAboutTwelveHoursOfDaylight() {
        for (date in listOf(marchEquinox, septemberEquinox)) {
            for (place in listOf(SkyPlace.Metropole, paris, SkyPlace.forDepartment("974"))) {
                assertEquals(hm(12), dayLengthMinutes(date, place, 60), 15.0, "$date $place")
            }
        }
    }

    @Test
    fun metropolitanDaysAreLongerInSummerThanInWinter() {
        val summer = dayLengthMinutes(juneSolstice, SkyPlace.Metropole, 120)
        val winter = dayLengthMinutes(decemberSolstice, SkyPlace.Metropole, 60)
        assertTrue(summer > hm(15) && summer < hm(17), "été : $summer")
        assertTrue(winter > hm(8) && winter < hm(9, 30), "hiver : $winter")
    }

    @Test
    fun midnightIsNightWithMoonAndStarsAndNoSun() {
        for ((date, offset) in listOf(juneSolstice to 120, decemberSolstice to 60, marchEquinox to 60)) {
            val sky = skyPhaseAt(0, date, SkyPlace.Metropole, offset)
            assertEquals(SkyPhase.NIGHT, sky.phase, "$date")
            assertNull(sky.sunArc)
            assertEquals(1f, sky.starAlpha)
            assertEquals(0f, sky.daylight)
            assertEquals(0f, sky.warmth)
        }
    }

    @Test
    fun middayIsDayWithSunHighAndNoStars() {
        for ((date, offset) in listOf(juneSolstice to 120, decemberSolstice to 60, septemberEquinox to 120)) {
            val noon = if (offset == 120) hm(13, 50) else hm(12, 45)
            val sky = skyPhaseAt(noon.toInt(), date, SkyPlace.Metropole, offset)
            assertEquals(SkyPhase.DAY, sky.phase, "$date")
            val arc = assertNotNull(sky.sunArc)
            assertTrue(arc in 0.4f..0.6f, "arc $arc")
            assertEquals(0f, sky.starAlpha)
            assertEquals(1f, sky.daylight)
        }
    }

    @Test
    fun justBeforeSunriseIsDawnWithSunStillBelowHorizon() {
        val t = assertNotNull(sunTimes(juneSolstice, paris, 120))
        val sky = skyPhaseAt(floor(t.sunriseMinute).toInt() - 1, juneSolstice, paris, 120)
        assertEquals(SkyPhase.DAWN, sky.phase)
        assertNull(sky.sunArc)
    }

    @Test
    fun justAfterSunriseIsStillDawnButSunIsUp() {
        val t = assertNotNull(sunTimes(juneSolstice, paris, 120))
        val sky = skyPhaseAt(ceil(t.sunriseMinute).toInt() + 1, juneSolstice, paris, 120)
        assertEquals(SkyPhase.DAWN, sky.phase)
        val arc = assertNotNull(sky.sunArc)
        assertTrue(arc < 0.05f, "arc $arc")
        assertTrue(sky.warmth > 0.5f, "teinte chaude au lever : ${sky.warmth}")
    }

    @Test
    fun justAroundSunsetIsDusk() {
        val t = assertNotNull(sunTimes(juneSolstice, paris, 120))
        val before = skyPhaseAt(floor(t.sunsetMinute).toInt() - 1, juneSolstice, paris, 120)
        val after = skyPhaseAt(ceil(t.sunsetMinute).toInt() + 1, juneSolstice, paris, 120)
        assertEquals(SkyPhase.DUSK, before.phase)
        assertNotNull(before.sunArc)
        assertEquals(SkyPhase.DUSK, after.phase)
        assertNull(after.sunArc)
    }

    @Test
    fun twoHoursAroundSunriseInWinterAreNightThenDay() {
        val t = assertNotNull(sunTimes(decemberSolstice, paris, 60))
        assertEquals(SkyPhase.NIGHT, skyPhaseAt((t.sunriseMinute - 120).toInt(), decemberSolstice, paris, 60).phase)
        assertEquals(SkyPhase.DAY, skyPhaseAt((t.sunriseMinute + 120).toInt(), decemberSolstice, paris, 60).phase)
        assertEquals(SkyPhase.DAY, skyPhaseAt((t.sunsetMinute - 120).toInt(), decemberSolstice, paris, 60).phase)
        assertEquals(SkyPhase.NIGHT, skyPhaseAt((t.sunsetMinute + 120).toInt(), decemberSolstice, paris, 60).phase)
    }

    @Test
    fun theDayCyclesThroughFourPhasesInOrderWithoutJumps() {
        val cases = listOf(
            Triple(juneSolstice, SkyPlace.Metropole, 120),
            Triple(decemberSolstice, SkyPlace.Metropole, 60),
            Triple(marchEquinox, paris, 60),
            Triple(juneSolstice, SkyPlace.forDepartment("974"), 240),
            Triple(decemberSolstice, SkyPlace.forDepartment("987"), -600),
            Triple(septemberEquinox, SkyPlace.forDepartment("973"), -180),
        )
        val next = mapOf(
            SkyPhase.NIGHT to SkyPhase.DAWN,
            SkyPhase.DAWN to SkyPhase.DAY,
            SkyPhase.DAY to SkyPhase.DUSK,
            SkyPhase.DUSK to SkyPhase.NIGHT,
        )
        for ((date, place, offset) in cases) {
            var previous: SkyState? = null
            var changes = 0
            for (minute in 0 until 1440) {
                val sky = skyPhaseAt(minute, date, place, offset)
                assertTrue(sky.progress in 0f..1f, "progression $date $place $minute")
                assertTrue(sky.daylight in 0f..1f && sky.starAlpha in 0f..1f && sky.warmth in 0f..1f)
                val p = previous
                if (p != null) {
                    if (p.phase == sky.phase) {
                        assertTrue(sky.progress >= p.progress, "progression non décroissante $date $place $minute")
                    } else {
                        changes++
                        assertEquals(next[p.phase], sky.phase, "ordre des phases $date $place $minute")
                    }
                    assertTrue(abs(sky.daylight - p.daylight) < 0.08f, "saut de lumière $date $place $minute")
                }
                previous = sky
            }
            assertEquals(4, changes, "$date $place")
        }
    }

    @Test
    fun starsAndMoonOnlyShowWhenTheSkyIsDark() {
        for (minute in 0 until 1440) {
            val sky = skyPhaseAt(minute, juneSolstice, SkyPlace.Metropole, 120)
            if (sky.phase == SkyPhase.DAY) assertEquals(0f, sky.starAlpha, "jour $minute")
            if (sky.phase == SkyPhase.NIGHT) assertEquals(1f, sky.starAlpha, "nuit $minute")
        }
    }

    @Test
    fun overseasLatitudesGiveTropicalDayLengths() {
        val reunion = SkyPlace.forDepartment("974")
        assertTrue(reunion.latitude < -20.0 && reunion.latitude > -22.0)
        val reunionJune = dayLengthMinutes(juneSolstice, reunion, 240)
        val reunionDecember = dayLengthMinutes(decemberSolstice, reunion, 240)
        // Hémisphère sud : jours plus courts en juin, plus longs en décembre, et amplitude modeste près du tropique.
        assertTrue(reunionJune > hm(10, 30) && reunionJune < hm(11, 15), "juin : $reunionJune")
        assertTrue(reunionDecember > hm(13) && reunionDecember < hm(13, 45), "décembre : $reunionDecember")
        val guyane = SkyPlace.forDepartment("973")
        for (date in listOf(juneSolstice, decemberSolstice)) {
            assertEquals(hm(12), dayLengthMinutes(date, guyane, -180), 20.0, "$date")
        }
        val tahiti = SkyPlace.forDepartment("987")
        val t = assertNotNull(sunTimes(juneSolstice, tahiti, -600))
        assertEquals(hm(12), (t.sunriseMinute + t.sunsetMinute) / 2.0, 20.0)
    }

    @Test
    fun overseasGardenWithPhoneInAnotherTimeZoneStaysConsistentWithTheClock() {
        // Jardin à La Réunion, téléphone en métropole (UTC+1) : on garde la latitude mais pas la longitude du lieu,
        // sinon le midi solaire tomberait vers 9 h sur l'horloge du téléphone.
        val t = assertNotNull(sunTimes(juneSolstice, SkyPlace.forDepartment("974"), 60))
        assertEquals(hm(12), (t.sunriseMinute + t.sunsetMinute) / 2.0, 20.0)
        val metropole = skyPhaseAt(hm(12).toInt(), juneSolstice, SkyPlace.forDepartment("974"), 60)
        assertEquals(SkyPhase.DAY, metropole.phase)
    }

    @Test
    fun polarLatitudesDoNotBreakAnything() {
        val arctic = SkyPlace(80.0, 0.0)
        assertNull(sunTimes(juneSolstice, arctic, 0))
        assertNull(sunTimes(decemberSolstice, arctic, 0))
        assertEquals(SkyPhase.DAY, skyPhaseAt(0, juneSolstice, arctic, 0).phase)
        assertEquals(SkyPhase.NIGHT, skyPhaseAt(720, decemberSolstice, arctic, 0).phase)
        for (minute in 0 until 1440 step 30) {
            for (date in listOf(juneSolstice, decemberSolstice)) {
                val sky = skyPhaseAt(minute, date, arctic, 0)
                assertTrue(sky.progress in 0f..1f && sky.daylight in 0f..1f)
            }
        }
    }

    @Test
    fun placeComesFromTheDepartmentAlreadyChosenForFrostDates() {
        assertEquals(SkyPlace.Metropole, SkyPlace.forDepartment(null))
        assertEquals(SkyPlace.Metropole, SkyPlace.forDepartment("ZZ"))
        val corse = SkyPlace.forDepartment("2A")
        val nord = SkyPlace.forDepartment("59")
        assertTrue(nord.latitude - corse.latitude > 5.0, "le Nord est plus au nord que la Corse")
        assertEquals(SkyPlace.forDepartment("35"), SkyPlace.forDepartment(" 35 "))
        assertTrue(SkyPlace.forDepartment("35").longitude < 0.0, "la Bretagne est à l'ouest du méridien de Greenwich")
    }

    @Test
    fun everyCatalogDepartmentHasAnExplicitPlace() {
        for (dep in FrenchFrostCatalog.DEPARTMENTS) {
            val place = SkyPlace.forDepartment(dep.code)
            assertTrue(place != SkyPlace.Metropole, "lieu manquant pour ${dep.code} ${dep.name} (${dep.region})")
            if (dep.climate == Climate.TROPICAL) {
                assertTrue(abs(place.latitude) < 25.0, "${dep.code} ${place.latitude}")
            } else {
                assertTrue(place.latitude in 41.0..51.5 && place.longitude in -5.5..9.7, "${dep.code} $place")
            }
        }
    }

    @Test
    fun fixedClockBuildsTheMinuteOfTheDay() {
        val clock = SkyClock.fixed(juneSolstice, hour = 7, minute = 30, utcOffsetMinutes = 120)
        assertEquals(450, clock.minuteOfDay)
        assertEquals(120, clock.utcOffsetMinutes)
        assertEquals(juneSolstice, clock.date)
    }

    @Test
    fun skyColorsAreContinuousAcrossPhaseBoundaries() {
        fun top(phase: SkyPhase, progress: Float) = skyColorsFor(SkyState(phase, progress, null, 0f)).top
        assertClose(top(SkyPhase.NIGHT, 0.5f), top(SkyPhase.DAWN, 0f))
        assertClose(top(SkyPhase.DAWN, 1f), top(SkyPhase.DAY, 0.5f))
        assertClose(top(SkyPhase.DAY, 0.5f), top(SkyPhase.DUSK, 0f))
        assertClose(top(SkyPhase.DUSK, 1f), top(SkyPhase.NIGHT, 0.5f))
    }

    @Test
    fun signLabelKeepsAtLeastAaContrastAtAnyHour() {
        var night = 0f
        while (night <= 1f) {
            val ratio = contrast(SignInk, signFace(night))
            assertTrue(ratio >= 4.5f, "contraste $ratio à night=$night")
            night += 0.05f
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    private fun assertClose(expected: Color, actual: Color) {
        assertTrue(
            abs(expected.red - actual.red) < 0.01f && abs(expected.green - actual.green) < 0.01f &&
                abs(expected.blue - actual.blue) < 0.01f,
            "$expected != $actual",
        )
    }
}
