package fr.pilmil.sillon.shared

import fr.pilmil.sillon.engine.DiagnosticJournal
import fr.pilmil.sillon.data.DiseaseCatalog
import fr.pilmil.sillon.model.Garden
import platform.Foundation.NSDateComponents
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * Rappels « Revérifier » d'un problème noté au potager, sur iOS (UNUserNotificationCenter, hors-ligne).
 * Un rappel par entrée ouverte qui a une date de revérification future, à 9 h ce jour-là.
 * L'ensemble est recalculé depuis le potager à chaque changement : rien n'est gardé à part.
 */
object IosFollowUpReminders {
    private const val PREFIX = "fr.pilmil.sillon.followup."
    private const val HOUR = 9

    fun requestAuthorization(onResult: (Boolean) -> Unit) {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { granted, _ -> onResult(granted) }
    }

    fun sync(garden: Garden, today: kotlinx.datetime.LocalDate) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.getPendingNotificationRequestsWithCompletionHandler { requests ->
            val old = requests.orEmpty().mapNotNull { (it as? UNNotificationRequest)?.identifier }.filter { it.startsWith(PREFIX) }
            center.removePendingNotificationRequestsWithIdentifiers(old)
            garden.diagnosticJournal
                .filter { it.isOpen }
                .forEach { entry ->
                    val due = entry.followUpOn ?: return@forEach
                    if (due > today) schedule(center, entry.id, due, DiagnosticJournal.vegetableName(entry), entry.diseaseId)
                }
        }
    }

    private fun schedule(center: UNUserNotificationCenter, id: String, on: kotlinx.datetime.LocalDate, plant: String, diseaseId: String?) {
        val strings = textFor(NSLocale.preferredLanguages.firstOrNull() as? String)
        val content = UNMutableNotificationContent().apply {
            setTitle(strings.first)
            setBody(strings.second.replace("%s", plant + (diseaseId?.let { DiseaseCatalog.getById(it)?.name }?.let { " ($it)" } ?: "")))
            setSound(UNNotificationSound.defaultSound())
        }
        val components = NSDateComponents().apply {
            year = on.year.toLong()
            month = on.monthNumber.toLong()
            day = on.dayOfMonth.toLong()
            hour = HOUR.toLong()
            minute = 0
        }
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(PREFIX + id, content, trigger), withCompletionHandler = null)
    }

    /** Titre et corps (avec %s pour la plante) dans la langue du téléphone, anglais par défaut. */
    private fun textFor(language: String?): Pair<String, String> = when (language?.take(2)) {
        "fr" -> "Revérifier au potager" to "%s : ça va mieux ?"
        "de" -> "Noch einmal nachsehen" to "%s: geht es besser?"
        "es" -> "Revisar en el huerto" to "%s: ¿va mejor?"
        "it" -> "Ricontrollare nell’orto" to "%s: va meglio?"
        "nl" -> "Nog eens kijken in de moestuin" to "%s: gaat het beter?"
        else -> "Time for a second look" to "%s: is it getting better?"
    }
}
