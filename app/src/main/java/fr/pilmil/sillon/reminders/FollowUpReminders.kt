package fr.pilmil.sillon.reminders

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.pilmil.sillon.MainActivity
import fr.pilmil.sillon.R
import fr.pilmil.sillon.data.GardenFile
import fr.pilmil.sillon.data.GardenRepository
import fr.pilmil.sillon.engine.DiagnosticJournal
import fr.pilmil.sillon.model.Garden
import fr.pilmil.sillon.model.todayLocalDate
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Rappels « Revérifier » des problèmes notés au potager : un rappel unique par entrée ouverte qui a une date
 * de revérification future, à 9 h ce jour-là. L'ensemble se recale depuis le potager à chaque changement du journal
 * (une entrée close, une date changée ou une case vidée annule son rappel). Rien n'est envoyé sans autorisation
 * de notification, et le suivi reste visible dans l'appli dans tous les cas.
 */
object FollowUpReminders {

    private const val TAG = "suivi-diagnostic"
    private const val KEY_ENTRY = "entry"
    const val CHANNEL_ID = "suivi"
    private val SEND_AT: LocalTime = LocalTime.of(9, 0)

    fun sync(context: Context, garden: Garden) {
        val manager = WorkManager.getInstance(context)
        manager.cancelAllWorkByTag(TAG)
        val now = LocalDateTime.now()
        val today = todayLocalDate()
        garden.diagnosticJournal.filter { it.isOpen }.forEach { entry ->
            val due = entry.followUpOn ?: return@forEach
            if (due <= today) return@forEach
            val at = LocalDate.of(due.year, due.monthNumber, due.dayOfMonth).atTime(SEND_AT)
            val request = OneTimeWorkRequestBuilder<FollowUpWorker>()
                .setInitialDelay(Duration.between(now, at).toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putString(KEY_ENTRY, entry.id).build())
                .addTag(TAG)
                .build()
            manager.enqueueUniqueWork("$TAG-${entry.id}", ExistingWorkPolicy.REPLACE, request)
        }
    }

    internal fun entryId(data: Data): String? = data.getString(KEY_ENTRY)

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.followup_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = context.getString(R.string.followup_channel_desc)
        manager.createNotificationChannel(channel)
    }
}

class FollowUpWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @SuppressLint("MissingPermission") // Vérifiée par SowingReminders.canNotify.
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!SowingReminders.canNotify(context)) return Result.success()
        val id = FollowUpReminders.entryId(inputData) ?: return Result.success()
        // Lecture seule : sans potager lisible, ou si l'entrée est close ou déplacée entre-temps, pas de rappel.
        val garden = (GardenRepository(context).readGarden() as? GardenFile.Found)?.garden ?: return Result.success()
        val entry = DiagnosticJournal.due(garden, todayLocalDate()).firstOrNull { it.id == id } ?: return Result.success()

        FollowUpReminders.ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, FollowUpReminders.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.followup_title))
            .setContentText(context.getString(R.string.followup_text, DiagnosticJournal.vegetableName(entry)))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_BASE + (id.hashCode() and 0xFFFF), notification)
        return Result.success()
    }

    private companion object {
        const val NOTIFICATION_BASE = 100
    }
}
