package de.flexy.stundenplan.data

import java.time.LocalDate
import java.time.temporal.IsoFields

/** Wie oft ein Modul tatsächlich stattfindet (Raumzeit trägt z.B. Labore oft wöchentlich ein). */
enum class Rhythm { WEEKLY, EVEN_WEEKS, ODD_WEEKS }

/** Persönliche Filter: ausgeblendete Module, 14-tägiger Rhythmus, einzeln ausgeblendete Termine. */
data class LectureFilter(
    val hiddenModules: Set<String> = emptySet(),
    val rhythms: Map<String, Rhythm> = emptyMap(),
    val skipped: Set<String> = emptySet(),
) {
    fun shows(l: Lecture): Boolean {
        if (l.title in hiddenModules) return false
        if (key(l) in skipped) return false
        return when (rhythms[l.title]) {
            Rhythm.EVEN_WEEKS -> isEvenWeek(l.date)
            Rhythm.ODD_WEEKS -> !isEvenWeek(l.date)
            else -> true
        }
    }

    companion object {
        fun key(l: Lecture) = "${l.title}|${l.start}"
        fun weekOf(d: LocalDate) = d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        fun isEvenWeek(d: LocalDate) = weekOf(d) % 2 == 0

        /** 14-tägig so einstellen, dass [l] stattfindet (with = true) bzw. ausfällt (with = false). */
        fun biweeklyFor(l: Lecture, with: Boolean): Rhythm {
            val even = isEvenWeek(l.date)
            return if (even == with) Rhythm.EVEN_WEEKS else Rhythm.ODD_WEEKS
        }
    }
}
