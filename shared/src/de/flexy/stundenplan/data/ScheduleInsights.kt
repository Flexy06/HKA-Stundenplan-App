package de.flexy.stundenplan.data

import java.time.Duration

/** Auswertungen über eine Terminliste, die UI-unabhängig sind (und damit testbar). */
object ScheduleInsights {

    /**
     * Termine, die sich zeitlich mit einem anderen (stattfindenden) Termin überschneiden –
     * z.B. wenn zwei Laborgruppen oder zwei Wahlfächer parallel liegen.
     * Ausfälle zählen nicht; direkt aneinander anschließende Termine (Ende = Beginn) auch nicht.
     */
    fun conflicts(lectures: List<Lecture>): Set<Lecture> {
        val active = lectures.filter { !it.cancelled }.sortedBy { it.start }
        if (active.size < 2) return emptySet()
        val out = HashSet<Lecture>()
        // Nach Beginn sortiert: ab dem ersten Termin, der nicht mehr in [a] hineinreicht, kann keiner mehr überlappen
        for (i in active.indices) {
            val a = active[i]
            for (j in i + 1 until active.size) {
                val b = active[j]
                if (!b.start.isBefore(a.end)) break
                out += a
                out += b
            }
        }
        return out
    }

    /** Gesamtdauer aller stattfindenden Termine in Minuten (Überschneidungen zählen nur einmal). */
    fun busyMinutes(lectures: List<Lecture>): Long {
        val active = lectures.filter { !it.cancelled }.sortedBy { it.start }
        var total = 0L
        var curStart = active.firstOrNull()?.start ?: return 0
        var curEnd = active.first().end
        for (l in active.drop(1)) {
            if (l.start.isAfter(curEnd)) {
                total += Duration.between(curStart, curEnd).toMinutes()
                curStart = l.start
                curEnd = l.end
            } else if (l.end.isAfter(curEnd)) {
                curEnd = l.end
            }
        }
        total += Duration.between(curStart, curEnd).toMinutes()
        return total
    }
}
