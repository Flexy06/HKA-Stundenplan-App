package de.flexy.stundenplan.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/**
 * Liest ausgefallene Termine aus dem JSON-Export von Raumzeit
 * (/api/v1/timetables/public/{semester}, Accept: application/json).
 * Im iCal-Export fehlen Ausfälle einfach – hier holen wir sie zurück,
 * damit die App "Entfällt" anzeigen kann.
 */
object CancellationParser {

    fun parse(json: String): List<Lecture> {
        val arr = JSONArray(json)
        val seen = HashSet<String>()
        val out = ArrayList<Lecture>()
        for (i in 0 until arr.length()) {
            val entry = arr.optJSONObject(i) ?: continue
            val groups = entry.optJSONArray("group")
            val sources: List<JSONObject> =
                if (groups != null && groups.length() > 0) {
                    (0 until groups.length()).mapNotNull { groups.optJSONObject(it) }
                } else listOf(entry)

            for (g in sources) {
                val cancellations = g.optJSONArray("cancellations") ?: continue
                for (k in 0 until cancellations.length()) {
                    val c = cancellations.optJSONObject(k) ?: continue
                    val ts = c.optString("timestamp")
                    if (ts.isEmpty()) continue
                    val start = runCatching { LocalDateTime.parse(ts) }.getOrNull() ?: continue
                    val title = g.optString("longName").normalizeSpaces()
                        .ifEmpty { g.optString("name").normalizeSpaces() }
                    // Dieselbe Absage taucht oft mehrfach auf (mehrere Studiengänge) → deduplizieren
                    if (!seen.add("$title|$start")) continue

                    val minutes = (g.optInt("endTime") - g.optInt("startTime")).takeIf { it > 0 } ?: 90
                    val rooms = g.optJSONArray("rooms")?.let { r ->
                        (0 until r.length()).joinToString(", ") { r.optString(it) }
                    }.orEmpty()

                    out += Lecture(
                        id = "cancel_${c.optString("timetableEntryId")}_$ts",
                        title = title,
                        code = g.optString("name").normalizeSpaces(),
                        start = start,
                        end = start.plusMinutes(minutes.toLong()),
                        location = rooms,
                        cancelled = true,
                        note = c.optString("reason").normalizeSpaces(),
                    )
                }
            }
        }
        return out
    }
}
