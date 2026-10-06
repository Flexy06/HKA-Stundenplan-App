package de.flexy.stundenplan.data

/** Baut aus iCal-Export (+ optional JSON für Ausfälle) die fertige Terminliste. */
object TimetableBuilder {
    fun build(ics: String, json: String?): List<Lecture> {
        val events = dedupe(IcsParser.parse(ics))
        val cancelled = json?.let { runCatching { CancellationParser.parse(it) }.getOrNull() }
            .orEmpty()
            // Falls der Termin doch (z.B. über eine andere Gruppe) stattfindet, nicht als Ausfall zeigen
            .filter { c ->
                events.none {
                    it.start == c.start && (
                        it.title.equals(c.title, ignoreCase = true) ||
                            (c.location.isNotBlank() && it.location.equals(c.location, ignoreCase = true))
                        )
                }
            }
            .let(::dedupe)
        return (events + cancelled).sortedWith(compareBy<Lecture>({ it.start }, { it.cancelled }, { it.title }))
    }

    /**
     * Raumzeit listet manche Veranstaltungen doppelt (z.B. "Mathematik" und
     * "Mathematik/Höhere Mathematik 1" zur selben Zeit im selben Raum).
     * Wir behalten jeweils den aussagekräftigsten Titel.
     */
    fun dedupe(list: List<Lecture>): List<Lecture> =
        list.groupBy { Triple(it.start, it.end, it.location.lowercase()) }
            .values
            .map { same -> same.maxBy { it.title.length } }
}
