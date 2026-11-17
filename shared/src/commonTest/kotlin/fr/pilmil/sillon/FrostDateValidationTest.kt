package fr.pilmil.sillon

import fr.pilmil.sillon.model.FrostDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FrostDateValidationTest {

    @Test
    fun leapDayIsAccepted() {
        assertTrue(FrostDate.isValid(2, 29))
        assertFalse(FrostDate.isValid(2, 30))
    }

    @Test
    fun thirtyDayMonthsRefuseThe31st() {
        for (month in listOf(4, 6, 9, 11)) {
            assertTrue(FrostDate.isValid(month, 30), "30/$month")
            assertFalse(FrostDate.isValid(month, 31), "31/$month")
        }
    }

    @Test
    fun thirtyOneDayMonthsAccept31() {
        for (month in listOf(1, 3, 5, 7, 8, 10, 12)) {
            assertTrue(FrostDate.isValid(month, 31), "31/$month")
            assertEquals(31, FrostDate.maxDayOfMonth(month))
        }
    }

    @Test
    fun outOfRangeValuesAreRefused() {
        assertFalse(FrostDate.isValid(0, 10))
        assertFalse(FrostDate.isValid(13, 10))
        assertFalse(FrostDate.isValid(5, 0))
        assertFalse(FrostDate.isValid(5, 32))
    }
}
