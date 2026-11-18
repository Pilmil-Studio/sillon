package fr.pilmil.sillon

import fr.pilmil.sillon.model.formatDayMonth
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** Le formatage reçoit motif et noms de mois (valeurs fabriquées, lues dans les ressources par l'interface). */
class DayMonthFormatTest {

    private fun months(prefix: String) = (1..12).map { "$prefix$it" }

    @Test
    fun everyMonthInEveryPattern() {
        val patterns = mapOf(
            "%1\$d %2\$s" to { d: Int, m: String -> "$d $m" },
            "%2\$s %1\$d" to { d: Int, m: String -> "$m $d" },
            "%1\$d. %2\$s" to { d: Int, m: String -> "$d. $m" },
            "%1\$d de %2\$s" to { d: Int, m: String -> "$d de $m" },
        )
        val names = months("m")
        for ((pattern, expected) in patterns) {
            for (month in 1..12) {
                assertEquals(expected(15, "m$month"), formatDayMonth(LocalDate(2026, month, 15), names, pattern))
            }
        }
    }

    @Test
    fun leapDayAndRealNames() {
        val fr = listOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre")
        assertEquals("29 février", formatDayMonth(LocalDate(2028, 2, 29), fr, "%1\$d %2\$s"))
        assertEquals("1 janvier", formatDayMonth(LocalDate(2026, 1, 1), fr, "%1\$d %2\$s"))
        assertEquals("31 décembre", formatDayMonth(LocalDate(2026, 12, 31), fr, "%1\$d %2\$s"))
    }
}
