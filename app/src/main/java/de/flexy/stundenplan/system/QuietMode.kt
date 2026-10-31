package de.flexy.stundenplan.system

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import de.flexy.stundenplan.data.QuietTimes
import de.flexy.stundenplan.data.TimetableRepository
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Stellt das Handy während der Vorlesungen auf "Nur Priorität" (Nicht stören) und danach zurück.
 * Es ist immer nur EIN Alarm gesetzt – für den nächsten Wechsel. Wir fassen nur an, was wir selbst
 * gesetzt haben: Hat der Nutzer "Nicht stören" schon selbst eingeschaltet oder ändert es währenddessen,
 * bleibt es unangetastet.
 */
object QuietMode {
    private const val REQUEST_ALARM = 4712

    fun hasAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    /** Ist-Zustand herstellen (stumm oder laut) und den nächsten Wechsel planen. Idempotent. */
    fun sync(context: Context) {
        val repo = TimetableRepository(context)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = alarmIntent(context)
        am.cancel(pending)
        if (!repo.quietInLectures || !hasAccess(context)) {
            restore(context, repo)
            return
        }
        // Kleiner Vorlauf, damit ein Alarm exakt zur Grenze schon auf der neuen Seite landet
        val now = LocalDateTime.now().plusSeconds(5)
        val blocks = QuietTimes.blocks(repo.upcomingActive(now))
        if (QuietTimes.isQuiet(blocks, now)) silence(context, repo) else restore(context, repo)

        val next = QuietTimes.next(blocks, now) ?: return
        val triggerAt = next.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun silence(context: Context, repo: TimetableRepository) {
        if (repo.quietActive) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        // Nur eingreifen, wenn gerade alles erlaubt ist – sonst hat der Nutzer es schon selbst eingestellt
        if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        repo.quietActive = true
    }

    private fun restore(context: Context, repo: TimetableRepository) {
        if (!repo.quietActive) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm != null && hasAccess(context) &&
            nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
        ) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
        repo.quietActive = false
    }

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_ALARM,
            Intent(context, QuietReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

class QuietReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        QuietMode.sync(context)
    }
}
