package fr.pilmil.sillon.ui.garden

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime

/**
 * Heure locale du téléphone telle que le ciel la lit : date, minute du jour et décalage du fuseau (heure d'été comprise).
 * Passer un [SkyClock] fixe à la scène fige le ciel, par exemple pour des captures d'écran reproductibles.
 */
data class SkyClock(val date: LocalDate, val minuteOfDay: Int, val utcOffsetMinutes: Int) {
    companion object {
        /** Ciel figé à [date] et [hour]:[minute], avec le décalage [utcOffsetMinutes] (60 par défaut, heure d'hiver en France). */
        fun fixed(date: LocalDate, hour: Int, minute: Int = 0, utcOffsetMinutes: Int = 60) =
            SkyClock(date, hour * 60 + minute, utcOffsetMinutes)

        /** Même chose à partir d'une date et heure locales. */
        fun of(dateTime: LocalDateTime, utcOffsetMinutes: Int = 60) =
            SkyClock(dateTime.date, dateTime.hour * 60 + dateTime.minute, utcOffsetMinutes)
    }
}

private const val MINUTE_MS = 60_000L

/** Lit l'horloge et le fuseau du téléphone, sans rien demander de plus au système. */
internal fun currentSkyClock(): SkyClock {
    val now = Clock.System.now()
    val zone = TimeZone.currentSystemDefault()
    val local = now.toLocalDateTime(zone)
    return SkyClock(local.date, local.hour * 60 + local.minute, zone.offsetAt(now).totalSeconds / 60)
}

/**
 * Horloge du ciel : lue à l'ouverture puis relue au changement de minute, sans boucle d'animation (un seul réveil
 * par minute, tant que la scène est affichée). Un [override] non nul fige le ciel et arrête la relecture.
 */
@Composable
internal fun rememberSkyClock(override: SkyClock?): SkyClock {
    var clock by remember { mutableStateOf(override ?: currentSkyClock()) }
    LaunchedEffect(override) {
        if (override != null) {
            clock = override
        } else {
            while (true) {
                clock = currentSkyClock()
                delay(MINUTE_MS - Clock.System.now().toEpochMilliseconds() % MINUTE_MS)
            }
        }
    }
    return clock
}
