package fr.pilmil.sillon.ui.garden

import fr.pilmil.sillon.data.FrenchFrostCatalog
import fr.pilmil.sillon.model.Climate
import kotlinx.datetime.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/*
 * Ciel de la scène du potager : phase de la journée (nuit, aube, jour, crépuscule) calculée hors ligne.
 * Aucun appel réseau, aucune permission : le lieu vient du département déjà choisi pour les dates de gelée, et
 * l'heure de l'horloge du téléphone. Tout le fichier est du calcul pur (pas de Compose, pas d'horloge système),
 * donc testable à l'identique sur Android et iOS.
 *
 * Formules : déclinaison solaire et équation du temps approchées par une sinusoïde selon le jour de l'année
 * (erreur de l'ordre de la minute à quelques minutes, sans conséquence pour un décor). L'aube et le crépuscule
 * sont la période où le Soleil est entre -6° (fin de la nuit civile) et +6° (début de la lumière franche).
 */

/** Les quatre moments du ciel. */
enum class SkyPhase { NIGHT, DAWN, DAY, DUSK }

/**
 * Lieu approximatif, en degrés ; la latitude est positive au nord, la longitude positive à l'est.
 * [standardOffsetMinutes] est le décalage UTC du fuseau du lieu en heure d'hiver : il sert à savoir si l'horloge du
 * téléphone est bien celle du lieu.
 */
data class SkyPlace(val latitude: Double, val longitude: Double, val standardOffsetMinutes: Int = 60) {
    companion object {
        /** Centre approximatif de la France métropolitaine, utilisé tant qu'aucun département n'est choisi. */
        val Metropole = SkyPlace(46.5, 2.5)

        // Centre approximatif de chaque région (le catalogue de gelées donne la région de chaque département).
        private val REGIONS = mapOf(
            "Auvergne-Rhône-Alpes" to SkyPlace(45.5, 4.8),
            "Bourgogne-Franche-Comté" to SkyPlace(47.2, 5.0),
            "Bretagne" to SkyPlace(48.2, -2.9),
            "Centre-Val de Loire" to SkyPlace(47.5, 1.7),
            "Corse" to SkyPlace(42.1, 9.1),
            "Grand Est" to SkyPlace(48.7, 5.6),
            "Hauts-de-France" to SkyPlace(50.1, 2.8),
            "Île-de-France" to SkyPlace(48.7, 2.5),
            "Normandie" to SkyPlace(49.1, 0.2),
            "Nouvelle-Aquitaine" to SkyPlace(45.1, 0.4),
            "Occitanie" to SkyPlace(43.7, 2.1),
            "Pays de la Loire" to SkyPlace(47.5, -0.8),
            "Provence-Alpes-Côte d'Azur" to SkyPlace(43.9, 6.0),
        )

        // Outre-mer, par code du catalogue.
        private val OVERSEAS = mapOf(
            "971" to SkyPlace(16.2, -61.5, -240), // Guadeloupe
            "972" to SkyPlace(14.6, -61.0, -240), // Martinique
            "973" to SkyPlace(4.0, -53.0, -180), // Guyane
            "974" to SkyPlace(-21.1, 55.5, 240), // La Réunion
            "976" to SkyPlace(-12.8, 45.2, 180), // Mayotte
            "977" to SkyPlace(18.1, -63.0, -240), // Saint-Martin
            "978" to SkyPlace(17.9, -62.8, -240), // Saint-Barthélemy
            "987" to SkyPlace(-17.7, -149.4, -600), // Polynésie française (Tahiti)
        )

        /** Lieu approximatif d'un département du catalogue ; [Metropole] si le code est absent ou inconnu. */
        fun forDepartment(code: String?): SkyPlace {
            val department = code?.let(FrenchFrostCatalog::getByCode) ?: return Metropole
            return if (department.climate == Climate.TROPICAL) {
                OVERSEAS[department.code] ?: Metropole
            } else {
                REGIONS[department.region] ?: Metropole
            }
        }
    }
}

/**
 * Lever et coucher du Soleil, en minutes depuis minuit à l'heure locale du téléphone.
 * Peut sortir de [0 ; 1440[ pour un lieu éloigné de son fuseau.
 */
data class SunTimes(val sunriseMinute: Double, val sunsetMinute: Double)

/**
 * Ciel à un instant donné. [progress] va de 0 à 1 à l'intérieur de [phase]. [sunArc] va de 0 (lever) à 1 (coucher)
 * quand le Soleil est au-dessus de l'horizon, sinon null. [nightArc] va de 0 (début de nuit) à 1 (fin de nuit) et sert
 * à promener la lune.
 */
data class SkyState(
    val phase: SkyPhase,
    val progress: Float,
    val sunArc: Float?,
    val nightArc: Float,
) {
    /** Lumière du jour, de 0 (nuit pleine) à 1 (jour plein), continue d'une phase à l'autre. */
    val daylight: Float
        get() = when (phase) {
            SkyPhase.NIGHT -> 0f
            SkyPhase.DAWN -> smooth(progress)
            SkyPhase.DAY -> 1f
            SkyPhase.DUSK -> 1f - smooth(progress)
        }

    /** Complément de [daylight] : sert à assombrir le décor. */
    val night: Float get() = 1f - daylight

    /** Teinte chaude : nulle de nuit et de jour, maximale au milieu de l'aube et du crépuscule. */
    val warmth: Float
        get() = when (phase) {
            SkyPhase.DAWN, SkyPhase.DUSK -> sin(PI * progress).toFloat()
            else -> 0f
        }

    /** Visibilité des étoiles et de la lune : pleine la nuit, fondue dans la seconde moitié du crépuscule et la première de l'aube. */
    val starAlpha: Float
        get() = when (phase) {
            SkyPhase.NIGHT -> 1f
            SkyPhase.DUSK -> ((progress - 0.5f) * 2f).coerceIn(0f, 1f)
            SkyPhase.DAWN -> ((0.5f - progress) * 2f).coerceIn(0f, 1f)
            SkyPhase.DAY -> 0f
        }
}

private fun smooth(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private const val DEG = PI / 180.0
private const val MINUTES_PER_DAY = 1440.0
private const val NEVER = -1.0 // le Soleil n'atteint jamais cette hauteur ce jour-là
private const val HORIZON_ELEVATION = -0.833 // bord supérieur du disque et réfraction
private const val CIVIL_TWILIGHT_ELEVATION = -6.0
private const val GOLDEN_ELEVATION = 6.0

/** Déclinaison du Soleil en degrés, selon le jour de l'année (1 à 366). */
private fun declinationDegrees(dayOfYear: Int): Double = -23.44 * cos(2.0 * PI / 365.0 * (dayOfYear + 10))

/** Équation du temps en minutes (temps solaire vrai moins temps solaire moyen). */
private fun equationOfTimeMinutes(dayOfYear: Int): Double {
    val b = 2.0 * PI * (dayOfYear - 81) / 365.0
    return 9.87 * sin(2.0 * b) - 7.53 * cos(b) - 1.5 * sin(b)
}

/**
 * Durée en minutes entre le midi solaire et l'instant où le Soleil atteint la hauteur [elevationDegrees]
 * (le même écart avant et après midi). [NEVER] si le Soleil n'y monte pas, 720 s'il y reste toute la journée.
 */
private fun halfSpanMinutes(latitude: Double, declination: Double, elevationDegrees: Double): Double {
    val c = (sin(elevationDegrees * DEG) - sin(latitude * DEG) * sin(declination * DEG)) /
        (cos(latitude * DEG) * cos(declination * DEG))
    return when {
        c > 1.0 -> NEVER
        c < -1.0 -> MINUTES_PER_DAY / 2.0
        else -> 4.0 * acos(c) / DEG
    }
}

/**
 * Longitude à utiliser avec le fuseau du téléphone. Si l'horloge du téléphone n'est pas celle du lieu (jardin en
 * outre-mer, téléphone en métropole, ou l'inverse), on garde la latitude du lieu mais on prend le méridien du fuseau
 * du téléphone : le ciel reste cohérent avec l'heure affichée plutôt que décalé de plusieurs heures.
 */
private fun effectiveLongitude(place: SkyPlace, utcOffsetMinutes: Int): Double {
    val sameClock = utcOffsetMinutes == place.standardOffsetMinutes || utcOffsetMinutes == place.standardOffsetMinutes + 60
    return if (sameClock) place.longitude else utcOffsetMinutes / 4.0
}

private fun solarNoonMinute(date: LocalDate, place: SkyPlace, utcOffsetMinutes: Int): Double =
    MINUTES_PER_DAY / 2.0 - 4.0 * effectiveLongitude(place, utcOffsetMinutes) -
        equationOfTimeMinutes(date.dayOfYear) + utcOffsetMinutes

/**
 * Lever et coucher du Soleil ce jour-là, ou null en jour polaire ou nuit polaire.
 * [utcOffsetMinutes] est le décalage du fuseau du téléphone à cette date (heure d'été comprise).
 */
fun sunTimes(date: LocalDate, place: SkyPlace = SkyPlace.Metropole, utcOffsetMinutes: Int = 60): SunTimes? {
    val half = halfSpanMinutes(place.latitude, declinationDegrees(date.dayOfYear), HORIZON_ELEVATION)
    if (half <= 0.0 || half >= MINUTES_PER_DAY / 2.0) return null
    val noon = solarNoonMinute(date, place, utcOffsetMinutes)
    return SunTimes(noon - half, noon + half)
}

/**
 * Phase du ciel à [minuteOfDay] (minutes depuis minuit, heure locale du téléphone) le [date], pour un [place] et
 * le décalage [utcOffsetMinutes] du fuseau du téléphone. Sans département choisi, [SkyPlace.Metropole] s'applique.
 */
fun skyPhaseAt(
    minuteOfDay: Int,
    date: LocalDate,
    place: SkyPlace = SkyPlace.Metropole,
    utcOffsetMinutes: Int = 60,
): SkyState {
    val declination = declinationDegrees(date.dayOfYear)
    val noon = solarNoonMinute(date, place, utcOffsetMinutes)
    val rise = halfSpanMinutes(place.latitude, declination, HORIZON_ELEVATION)
    val day = halfSpanMinutes(place.latitude, declination, GOLDEN_ELEVATION)
    val civil = max(halfSpanMinutes(place.latitude, declination, CIVIL_TWILIGHT_ELEVATION), 0.0)

    // Écart au midi solaire, ramené dans [-720 ; 720[ : négatif le matin, positif le soir.
    val raw = minuteOfDay - noon
    val d = raw - MINUTES_PER_DAY * floor((raw + MINUTES_PER_DAY / 2.0) / MINUTES_PER_DAY)
    val a = abs(d)

    val sunArc = if (rise > 0.0 && a <= rise) ((d + rise) / (2.0 * rise)).toFloat().coerceIn(0f, 1f) else null

    if (day >= 0.0 && a <= day) {
        val progress = if (day > 0.0) ((d + day) / (2.0 * day)).toFloat() else 0.5f
        return SkyState(SkyPhase.DAY, progress.coerceIn(0f, 1f), sunArc, 0f)
    }
    if (a <= civil) {
        val inner = max(day, 0.0)
        val span = civil - inner
        return if (d < 0.0) {
            SkyState(SkyPhase.DAWN, ((d + civil) / span).toFloat().coerceIn(0f, 1f), sunArc, 1f)
        } else {
            SkyState(SkyPhase.DUSK, ((d - inner) / span).toFloat().coerceIn(0f, 1f), sunArc, 0f)
        }
    }
    val nightLength = MINUTES_PER_DAY - 2.0 * civil
    val elapsed = if (d > 0.0) d - civil else d + MINUTES_PER_DAY - civil
    val progress = (elapsed / nightLength).toFloat().coerceIn(0f, 1f)
    return SkyState(SkyPhase.NIGHT, progress, null, progress)
}
