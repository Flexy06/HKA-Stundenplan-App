package de.flexy.stundenplan.desktop

import de.flexy.stundenplan.data.Lecture
import de.flexy.stundenplan.data.LectureFilter
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Erzeugt eine iCalendar-Datei (RFC 5545) für Outlook, Google Kalender & Co. */
object IcsExport {
    private val zone = ZoneId.of("Europe/Berlin")
    private val utc = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    fun build(lectures: List<Lecture>, semester: String): String {
        val stamp = ZonedDateTime.now(ZoneOffset.UTC).format(utc)
        val sb = StringBuilder()
        sb.line("BEGIN:VCALENDAR")
        sb.line("VERSION:2.0")
        sb.line("PRODID:-//flexy//HKA Stundenplan//DE")
        sb.line("CALSCALE:GREGORIAN")
        sb.line("METHOD:PUBLISH")
        sb.line("X-WR-CALNAME:" + esc("Stundenplan $semester"))
        for (l in lectures) {
            sb.line("BEGIN:VEVENT")
            sb.line("UID:" + esc(LectureFilter.key(l).hashCode().toUInt().toString(16) + "-" + l.start.toString().filter { it.isDigit() } + "@hka-stundenplan"))
            sb.line("DTSTAMP:$stamp")
            sb.line("DTSTART:" + l.start.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).format(utc))
            sb.line("DTEND:" + l.end.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).format(utc))
            sb.line("SUMMARY:" + esc(l.title))
            if (l.location.isNotBlank()) sb.line("LOCATION:" + esc(l.location))
            val desc = listOf(l.code, l.note).filter { it.isNotBlank() }.joinToString("\n")
            if (desc.isNotBlank()) sb.line("DESCRIPTION:" + esc(desc))
            sb.line("END:VEVENT")
        }
        sb.line("END:VCALENDAR")
        return sb.toString()
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r", "").replace("\n", "\\n")

    /** Zeilen > 75 Bytes werden gefaltet (RFC 5545 §3.1). */
    private fun StringBuilder.line(text: String) {
        var rest = text
        var first = true
        while (rest.toByteArray(Charsets.UTF_8).size > (if (first) 75 else 74)) {
            var cut = minOf(rest.length, if (first) 75 else 74)
            while (rest.substring(0, cut).toByteArray(Charsets.UTF_8).size > (if (first) 75 else 74)) cut--
            if (cut > 0 && rest[cut - 1].isHighSurrogate()) cut--
            append(if (first) "" else " ").append(rest, 0, cut).append("\r\n")
            rest = rest.substring(cut)
            first = false
        }
        append(if (first) "" else " ").append(rest).append("\r\n")
    }
}
