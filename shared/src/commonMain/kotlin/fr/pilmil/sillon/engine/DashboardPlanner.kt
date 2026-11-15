package fr.pilmil.sillon.engine

import fr.pilmil.sillon.model.Climate
import fr.pilmil.sillon.model.FrostDate
import fr.pilmil.sillon.model.Garden
import fr.pilmil.sillon.model.GrowingEnvironment
import fr.pilmil.sillon.model.LightSource
import fr.pilmil.sillon.model.LocalWeather
import fr.pilmil.sillon.model.daysBetween
import fr.pilmil.sillon.model.plusDays
import kotlinx.datetime.LocalDate

/** Où en est-on par rapport à la date repère des dernières gelées. */
sealed interface FrostStatus {
    /** La prochaine date repère est dans [days] jours. */
    data class Upcoming(val date: LocalDate, val days: Long) : FrostStatus

    /** La date repère de cette saison est passée depuis [days] jours. */
    data class Passed(val date: LocalDate, val days: Long) : FrostStatus

    /** Mode intérieur : culture hors gel toute l'année. */
    data object FrostFree : FrostStatus
}

/**
 * Ce que l'accueil affiche : les travaux de la quinzaine calculés sur la date de gelée,
 * ou, en morte-saison, les premiers travaux à venir et leur date.
 */
data class DashboardPlan(
    val frost: FrostStatus,
    val sowIndoors: List<CalendarTask>,
    val sowOutdoors: List<CalendarTask>,
    val transplant: List<CalendarTask>,
    val harvest: List<CalendarTask>,
    val gardenHarvest: List<HarvestTask>,
    /** Rempli seulement quand rien n'est à faire cette quinzaine : date des prochains travaux. */
    val nextWorkFrom: LocalDate?,
    /** Rappel contextuel d'arrosage / lumière pour les modes Balcon et Intérieur. */
    val wateringReminderNote: String? = null,
    val climate: Climate = Climate.TEMPERATE,
    /** Repère d'altitude (décalage de gelée en tempéré, fraîcheur en tropical). */
    val altitudeNote: String? = null,
) {
    val isEmpty: Boolean
        get() = sowIndoors.isEmpty() && sowOutdoors.isEmpty() && transplant.isEmpty() && harvest.isEmpty() && gardenHarvest.isEmpty()
}

object DashboardPlanner {

    /** Une quinzaine : +/- 7 jours autour de la date idéale. */
    const val TOLERANCE_DAYS = 7

    /** Au-delà, la date repère « passée » n'intéresse plus : on compte jusqu'à celle de l'an prochain. */
    const val PASSED_RELEVANCE_DAYS = 90L

    private const val LOOKAHEAD_DAYS = 200
    const val TEMPERATE_ALTITUDE_THRESHOLD_M = 200
    const val TROPICAL_ALTITUDE_THRESHOLD_M = 500

    /** Décalage en jours de la dernière gelée en climat tempéré : ~3,5 j par 100 m au-dessus de 200 m. */
    fun temperateFrostShiftDays(altitudeMeters: Int): Int {
        if (altitudeMeters <= TEMPERATE_ALTITUDE_THRESHOLD_M) return 0
        return ((altitudeMeters - TEMPERATE_ALTITUDE_THRESHOLD_M) * 35) / 1000
    }

    /** Date repère ajustée par l'altitude en climat tempéré (inchangée sous les tropiques). */
    fun effectiveFrostDate(frostDate: FrostDate, altitudeMeters: Int, climate: Climate): FrostDate {
        if (climate != Climate.TEMPERATE) return frostDate
        val shift = temperateFrostShiftDays(altitudeMeters)
        if (shift <= 0) return frostDate
        val shifted = safeDate(frostDate, 2026).plusDays(shift.toLong())
        return FrostDate(shifted.monthNumber, shifted.dayOfMonth)
    }

    fun altitudeNoteFor(altitudeMeters: Int, climate: Climate): String? = when {
        climate == Climate.TEMPERATE && altitudeMeters > TEMPERATE_ALTITUDE_THRESHOLD_M -> {
            val shift = temperateFrostShiftDays(altitudeMeters)
            "Altitude $altitudeMeters m : dernière gelée repère décalée de +$shift j par rapport au repère de plaine."
        }
        climate == Climate.TROPICAL && altitudeMeters >= TROPICAL_ALTITUDE_THRESHOLD_M -> {
            val coolingTenths = (altitudeMeters * 65) / 1000
            val whole = coolingTenths / 10
            val tenths = coolingTenths % 10
            "Altitude $altitudeMeters m : climat plus frais (env. -$whole,$tenths °C vs littoral), favorable au chouchou, choux et salades ; les cultures très chaudes mûrissent plus lentement."
        }
        else -> null
    }

    fun frostStatus(frostDate: FrostDate, today: LocalDate): FrostStatus {
        val thisYear = safeDate(frostDate, today.year)
        val days = daysBetween(today, thisYear)
        return when {
            days >= 0 -> FrostStatus.Upcoming(thisYear, days)
            -days <= PASSED_RELEVANCE_DAYS -> FrostStatus.Passed(thisYear, -days)
            else -> {
                val next = safeDate(frostDate, today.year + 1)
                FrostStatus.Upcoming(next, daysBetween(today, next))
            }
        }
    }

    fun wateringNoteFor(garden: Garden): String? {
        val envNote = when (garden.environment) {
            GrowingEnvironment.OUTDOOR -> null
            GrowingEnvironment.BALCONY -> {
                val parts = mutableListOf<String>()
                garden.orientation?.let { parts.add("Orientation ${it.frenchLabel}") }
                garden.windExposure?.let { parts.add("vent ${it.frenchLabel.lowercase()}") }
                val prefix = if (parts.isNotEmpty()) "${parts.joinToString(", ")} · " else ""
                if (garden.covered) {
                    "${prefix}Balcon couvert : la pluie n'arrose pas vos contenants. Surveillez l'humidité du terreau régulièrement."
                } else {
                    "${prefix}Culture en pots sur balcon : le substrat sèche plus vite qu'en pleine terre, surtout au vent."
                }
            }
            GrowingEnvironment.INDOOR -> {
                val lightInfo = when (garden.lightSource) {
                    LightSource.GROW_LIGHT -> garden.growLightHoursPerDay?.let { "Lampe horticole ($it h/j) · " } ?: "Lampe horticole · "
                    LightSource.WINDOW -> garden.orientation?.let { "Fenêtre (${it.frenchLabel}) · " } ?: "Rebord de fenêtre · "
                    null -> ""
                }
                "${lightInfo}En intérieur, arrosez modérément sans laisser d'eau stagner dans les coupelles."
            }
        }
        val weatherPart = when (garden.localWeather) {
            LocalWeather.NORMAL -> null
            LocalWeather.DRY_HOT -> if (garden.mulched) {
                "Temps sec et chaud : le paillage limite l'évaporation, arrosez au pied le matin ou le soir."
            } else {
                "Temps sec et chaud sans paillage : arrosez plus fréquemment au pied et paillez si possible pour garder l'humidité."
            }
            LocalWeather.RAINY -> "Temps pluvieux : suspendez l'arrosage en extérieur et surveillez le drainage (risque de mildiou sous paillis dense)."
        }
        val mulchPart = if (garden.mulched && garden.localWeather == LocalWeather.NORMAL && garden.environment == GrowingEnvironment.OUTDOOR) {
            "Sol paillé : conserve l'humidité plus longtemps, espacez les arrosages et vérifiez sous le paillis avant d'arroser."
        } else {
            null
        }
        return listOfNotNull(envNote, weatherPart, mulchPart).joinToString(" ").ifEmpty { null }
    }

    fun plan(garden: Garden, today: LocalDate, engine: FrostEngine = FrostEngine()): DashboardPlan {
        if (garden.environment == GrowingEnvironment.INDOOR) {
            val indoorTasks = engine.getIndoorYearRoundTasks(today, TOLERANCE_DAYS)
            val gardenHarvest = engine.getGardenHarvestTasks(garden, today)
            return DashboardPlan(
                frost = FrostStatus.FrostFree,
                sowIndoors = indoorTasks,
                sowOutdoors = emptyList(),
                transplant = emptyList(),
                harvest = emptyList(),
                gardenHarvest = gardenHarvest,
                nextWorkFrom = null,
                wateringReminderNote = wateringNoteFor(garden),
            )
        }

        val frost = effectiveFrostDate(garden.frostDate, garden.altitudeMeters, engine.climate)
        val sowIndoors = engine.getSowIndoorsTasks(frost, today, TOLERANCE_DAYS)
        val sowOutdoors = engine.getSowOutdoorsTasks(frost, today, TOLERANCE_DAYS)
        val transplant = engine.getTransplantTasks(frost, today, TOLERANCE_DAYS)
        val harvest = engine.getEstimatedCatalogHarvestTasks(frost, today)
        val gardenHarvest = engine.getGardenHarvestTasks(garden, today)
        val nothing = sowIndoors.isEmpty() && sowOutdoors.isEmpty() && transplant.isEmpty() && harvest.isEmpty() && gardenHarvest.isEmpty()
        return DashboardPlan(
            frost = frostStatus(frost, today),
            sowIndoors = sowIndoors,
            sowOutdoors = sowOutdoors,
            transplant = transplant,
            harvest = harvest,
            gardenHarvest = gardenHarvest,
            nextWorkFrom = if (nothing) nextWorkDate(frost, today, engine) else null,
            wateringReminderNote = wateringNoteFor(garden),
            climate = engine.climate,
            altitudeNote = altitudeNoteFor(garden.altitudeMeters, engine.climate),
        )
    }

    /** Premier jour où un semis ou une plantation s'ouvre, dans les mois qui viennent. */
    fun nextWorkDate(frost: FrostDate, today: LocalDate, engine: FrostEngine = FrostEngine()): LocalDate? {
        for (offset in 1..LOOKAHEAD_DAYS) {
            val day = today.plusDays(offset.toLong())
            val hasWork = engine.getSowIndoorsTasks(frost, day, TOLERANCE_DAYS).isNotEmpty() ||
                engine.getSowOutdoorsTasks(frost, day, TOLERANCE_DAYS).isNotEmpty() ||
                engine.getTransplantTasks(frost, day, TOLERANCE_DAYS).isNotEmpty()
            if (hasWork) return day
        }
        return null
    }

    /** Le 29 février n'existe pas tous les ans : on retombe sur le 28. */
    private fun safeDate(frostDate: FrostDate, year: Int): LocalDate =
        runCatching { frostDate.toLocalDate(year) }.getOrElse { LocalDate(year, frostDate.month, 28) }
}
