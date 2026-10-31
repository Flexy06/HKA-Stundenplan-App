package de.flexy.stundenplan.data

import java.time.LocalDate

/** Ernährungsfilter für den Speiseplan. */
enum class Diet { ALL, VEGETARIAN, VEGAN }

/** Ein Gericht, das zu einem Favoriten-Stichwort passt. */
data class FavoriteHit(val date: LocalDate, val line: String, val meal: Meal, val keyword: String)

object MensaFavorites {

    /** Nur Gerichte, die zur Ernährungsform passen; leere Linien fallen weg. "Vegetarisch" schließt vegane Gerichte ein. */
    fun filter(lines: List<MensaLine>, diet: Diet): List<MensaLine> {
        if (diet == Diet.ALL) return lines
        return lines.mapNotNull { line ->
            val meals = line.meals.filter { if (diet == Diet.VEGAN) it.vegan else it.vegan || it.vegetarian }
            if (meals.isEmpty()) null else MensaLine(line.name, meals)
        }
    }

    /** Das erste passende Stichwort (Groß-/Kleinschreibung egal), sonst null. */
    fun match(meal: Meal, favorites: Collection<String>): String? {
        val name = meal.name.lowercase()
        return favorites.firstOrNull { it.isNotBlank() && name.contains(it.trim().lowercase()) }
    }

    /** Alle Favoriten-Treffer ab [from], nach Datum sortiert. Jedes Gericht wird nur einmal gemeldet. */
    fun hits(
        menu: Map<LocalDate, List<MensaLine>>,
        favorites: Collection<String>,
        diet: Diet,
        from: LocalDate,
    ): List<FavoriteHit> {
        if (favorites.none { it.isNotBlank() }) return emptyList()
        val out = ArrayList<FavoriteHit>()
        for ((date, lines) in menu.toSortedMap()) {
            if (date.isBefore(from)) continue
            val seen = HashSet<String>()
            for (line in filter(lines, diet)) for (meal in line.meals) {
                val kw = match(meal, favorites) ?: continue
                if (seen.add(meal.name)) out += FavoriteHit(date, line.name, meal, kw)
            }
        }
        return out
    }

    /** Stabiler Schlüssel, um dieselbe Meldung nicht mehrfach zu senden. */
    fun key(hit: FavoriteHit) = "${hit.date}|${hit.meal.name}"
}
