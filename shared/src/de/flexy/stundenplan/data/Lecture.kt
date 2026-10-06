package de.flexy.stundenplan.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** Ein einzelner Termin im Stundenplan (bereits aufgelöst, keine Wiederholungsregeln mehr). */
data class Lecture(
    val id: String,
    val title: String,
    val code: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val location: String,
    val cancelled: Boolean = false,
    val note: String = "",
) {
    val date: LocalDate get() = start.toLocalDate()
    val duration: Duration get() = Duration.between(start, end)
}
