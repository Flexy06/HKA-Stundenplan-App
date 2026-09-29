package de.flexy.stundenplan.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Minimaler iCalendar-Parser für den Raumzeit-Export.
 * Raumzeit liefert jedes Vorkommen als eigenes VEVENT (keine RRULEs),
 * Ausfälle sind bereits entfernt und Verlegungen bereits eingerechnet.
 */
object IcsParser {
    private val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun parse(ics: String): List<Lecture> {
        // Zeilenfaltung (RFC 5545) auflösen
        val lines = ics.replace("\r\n ", "").replace("\r\n\t", "").replace("\n ", "")
            .split('\n').map { it.trimEnd('\r') }

        val result = ArrayList<Lecture>()
        var props: MutableMap<String, String>? = null
        for (line in lines) {
            when {
                line == "BEGIN:VEVENT" -> props = HashMap()
                line == "END:VEVENT" -> {
                    props?.let { p -> toLecture(p)?.let(result::add) }
                    props = null
                }
                props != null -> {
                    val colon = line.indexOf(':')
                    if (colon <= 0) continue
                    val head = line.substring(0, colon)
                    val name = head.substringBefore(';').uppercase()
                    props[name] = line.substring(colon + 1)
                }
            }
        }
        return result
    }

    private fun toLecture(p: Map<String, String>): Lecture? {
        val start = parseDate(p["DTSTART"] ?: return null) ?: return null
        val end = p["DTEND"]?.let(::parseDate) ?: start.plusMinutes(90)
        val title = unescape(p["SUMMARY"].orEmpty()).normalizeSpaces()
        return Lecture(
            id = p["UID"].orEmpty(),
            title = title.ifEmpty { "(ohne Titel)" },
            code = unescape(p["DESCRIPTION"].orEmpty()).normalizeSpaces(),
            start = start,
            end = end,
            location = unescape(p["LOCATION"].orEmpty()).normalizeSpaces(),
            cancelled = p["STATUS"].equals("CANCELLED", ignoreCase = true),
        )
    }

    private fun parseDate(value: String): LocalDateTime? = runCatching {
        val v = value.trim()
        when {
            v.endsWith("Z") -> LocalDateTime.parse(v.dropLast(1), DATE_TIME)
                .atOffset(ZoneOffset.UTC).atZoneSameInstant(BERLIN).toLocalDateTime()
            v.length == 8 -> LocalDate.parse(v, DATE).atStartOfDay()
            else -> LocalDateTime.parse(v, DATE_TIME)
        }
    }.getOrNull()

    private fun unescape(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    else -> sb.append(n)
                }
                i += 2
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }
}

internal fun String.normalizeSpaces(): String = trim().replace(Regex("\\s+"), " ")
