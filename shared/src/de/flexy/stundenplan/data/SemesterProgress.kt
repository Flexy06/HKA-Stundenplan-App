package de.flexy.stundenplan.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** "Woche [week] von [totalWeeks]" des Semesters, abgeleitet aus dem ersten und letzten Termin im Plan. */
data class SemesterProgress(val week: Int, val totalWeeks: Int) {
    val fraction: Float get() = week.toFloat() / totalWeeks
    val label: String get() = "Woche $week/$totalWeeks"

    companion object {
        /** null, wenn der Plan zu kurz ist oder [today] außerhalb des Zeitraums liegt (z.B. Semesterferien). */
        fun of(lectures: List<Lecture>, today: LocalDate): SemesterProgress? {
            val dates = lectures.filter { !it.cancelled }.map { it.date }
            val first = dates.minOrNull() ?: return null
            val last = dates.maxOrNull() ?: return null
            val firstMonday = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val lastMonday = last.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val total = ChronoUnit.WEEKS.between(firstMonday, lastMonday).toInt() + 1
            if (total < 2) return null
            val week = ChronoUnit.WEEKS.between(firstMonday, today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))).toInt() + 1
            if (week < 1 || week > total) return null
            return SemesterProgress(week, total)
        }
    }
}
