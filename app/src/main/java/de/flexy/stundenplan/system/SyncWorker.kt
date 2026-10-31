package de.flexy.stundenplan.system

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.flexy.stundenplan.MainActivity
import de.flexy.stundenplan.R
import de.flexy.stundenplan.data.ChangeDetector
import de.flexy.stundenplan.data.MensaFavorites
import de.flexy.stundenplan.data.MensaRepository
import de.flexy.stundenplan.data.ScheduleChange
import de.flexy.stundenplan.data.TimetableRepository
import de.flexy.stundenplan.widget.NextLectureWidget
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Gleicht den Stundenplan alle paar Stunden im Hintergrund ab, hält Widget & Erinnerungen aktuell
 * und meldet Änderungen (Ausfall, Raum, Verlegung) per Benachrichtigung.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val repo = TimetableRepository(ctx)
        val sem = repo.semester
        val old = repo.loadCachedSync(sem)
        val fresh = try {
            repo.fetch(sem)
        } catch (e: Exception) {
            return if (runAttemptCount < 2) Result.retry() else Result.success()
        }
        if (old != null && repo.notifyChanges) {
            val changes = ChangeDetector.diff(old, fresh, repo.filter, LocalDateTime.now())
            if (changes.isNotEmpty()) ChangeNotifier.notify(ctx, changes)
        }
        runCatching { Reminders.reschedule(ctx) }
        runCatching { NextLectureWidget.updateAll(ctx) }
        runCatching { FavoriteNotifier.check(ctx, repo) }
        return Result.success()
    }

    companion object {
        private const val NAME = "timetable_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

object ChangeNotifier {
    private const val CHANNEL = "schedule_changes"
    private const val ID = 9001

    fun notify(context: Context, changes: List<ScheduleChange>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Stundenplan-Änderungen", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Ausfälle, Raumänderungen und verlegte Termine"
                    }
                )
            }
        }
        val open = PendingIntent.getActivity(
            context, 2,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (changes.size == 1) "Stundenplan geändert" else "Stundenplan: ${changes.size} Änderungen"
        val style = NotificationCompat.InboxStyle()
        changes.take(6).forEach { style.addLine(it.text) }
        if (changes.size > 6) style.setSummaryText("+${changes.size - 6} weitere")
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_lecture)
            .setContentTitle(title)
            .setContentText(changes.first().text)
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID, notification)
        } catch (_: SecurityException) {
        }
    }
}

/** Meldet Lieblingsgerichte der Mensa – heute (bis 13 Uhr) und morgen, jedes Gericht nur einmal. */
object FavoriteNotifier {
    private const val CHANNEL = "mensa_favorites"
    private const val ID = 9002

    suspend fun check(context: Context, repo: TimetableRepository) {
        val favorites = repo.mensaFavorites
        if (!repo.notifyFavorites || !repo.showMensa || favorites.isEmpty()) return
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val menu = MensaRepository(context).load()
        val notified = repo.notifiedFavorites
        val hits = MensaFavorites.hits(menu, favorites, repo.mensaDiet, today)
            .filter { it.date == today.plusDays(1) || (it.date == today && now.hour < 13) }
            .filter { MensaFavorites.key(it) !in notified }
        if (hits.isEmpty()) return
        send(context, hits.map { h ->
            val day = when (h.date) {
                today -> "Heute"
                else -> "Morgen"
            }
            "$day: ${h.meal.name}" + if (h.meal.price.isNotBlank()) " · ${h.meal.price}" else ""
        })
        // Alte Einträge verwerfen, damit die Liste nicht wächst
        repo.notifiedFavorites = notified.filter { it.substringBefore('|') >= today.toString() }.toSet() +
            hits.map(MensaFavorites::key)
    }

    private fun send(context: Context, lines: List<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Mensa-Favoriten", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Wenn ein Lieblingsgericht auf dem Speiseplan steht"
                    }
                )
            }
        }
        val open = PendingIntent.getActivity(
            context, 3,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val style = NotificationCompat.InboxStyle()
        lines.take(6).forEach { style.addLine(it) }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_lecture)
            .setContentTitle(if (lines.size == 1) "Lieblingsgericht in der Mensa" else "${lines.size} Lieblingsgerichte in der Mensa")
            .setContentText(lines.first())
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID, notification)
        } catch (_: SecurityException) {
        }
    }
}
