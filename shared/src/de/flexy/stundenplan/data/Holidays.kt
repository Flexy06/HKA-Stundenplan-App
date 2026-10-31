package de.flexy.stundenplan.data

import java.time.DayOfWeek
import java.time.LocalDate

/** Gesetzliche Feiertage in Baden-Württemberg (Standort der HKA) inkl. Brückentagen. */
object Holidays {

    /** Ostersonntag (Gaußsche Osterformel, gregorianischer Kalender). */
    fun easter(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }

    fun forYear(year: Int): Map<LocalDate, String> {
        val easter = easter(year)
        return linkedMapOf(
            LocalDate.of(year, 1, 1) to "Neujahr",
            LocalDate.of(year, 1, 6) to "Heilige Drei Könige",
            easter.minusDays(2) to "Karfreitag",
            easter.plusDays(1) to "Ostermontag",
            LocalDate.of(year, 5, 1) to "Tag der Arbeit",
            easter.plusDays(39) to "Christi Himmelfahrt",
            easter.plusDays(50) to "Pfingstmontag",
            easter.plusDays(60) to "Fronleichnam",
            LocalDate.of(year, 10, 3) to "Tag der Deutschen Einheit",
            LocalDate.of(year, 11, 1) to "Allerheiligen",
            LocalDate.of(year, 12, 25) to "1. Weihnachtstag",
            LocalDate.of(year, 12, 26) to "2. Weihnachtstag",
        )
    }

    /** Name des Feiertags oder null. */
    fun nameOf(date: LocalDate): String? = forYear(date.year)[date]

    /** Freitag nach einem Donnerstags-Feiertag bzw. Montag vor einem Dienstags-Feiertag. */
    fun isBridgeDay(date: LocalDate): Boolean {
        if (nameOf(date) != null) return false
        return when (date.dayOfWeek) {
            DayOfWeek.FRIDAY -> nameOf(date.minusDays(1)) != null
            DayOfWeek.MONDAY -> nameOf(date.plusDays(1)) != null
            else -> false
        }
    }
}
