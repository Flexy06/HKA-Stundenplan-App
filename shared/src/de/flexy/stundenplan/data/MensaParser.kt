package de.flexy.stundenplan.data

import org.jsoup.Jsoup
import java.time.LocalDate

data class Meal(
    val name: String,
    val price: String,
    val vegan: Boolean,
    val vegetarian: Boolean,
)

/** Eine Ausgabelinie, z.B. "Wahlessen 1" mit ihren Gerichten. */
data class MensaLine(val name: String, val meals: List<Meal>)

object MensaSource {
    const val URL = "https://www.sw-ka.de/de/hochschulgastronomie/speiseplan/mensa_moltke/"
    fun weekUrl(kw: Int) = "$URL?kw=$kw"
}

object MensaParser {
    fun parse(html: String): Map<LocalDate, List<MensaLine>> {
        val doc = Jsoup.parse(html)
        val result = LinkedHashMap<LocalDate, List<MensaLine>>()
        for (nav in doc.select("ul.canteen-day-nav a[id^=canteen_day_nav_]")) {
            val date = runCatching { LocalDate.parse(nav.attr("rel")) }.getOrNull() ?: continue
            val idx = nav.id().removePrefix("canteen_day_nav_")
            val day = doc.getElementById("canteen_day_$idx") ?: continue
            val lines = day.select("tr.mensatype_rows").mapNotNull { row ->
                val lineName = row.attr("rel").ifBlank { row.selectFirst("td.mensatype")?.text().orEmpty() }.trim()
                val meals = row.select("table.meal-detail-table > tbody > tr").mapNotNull { tr ->
                    val title = tr.selectFirst("td.menu-title") ?: return@mapNotNull null
                    val name = (title.selectFirst("span.bg")?.text() ?: title.ownText()).trim()
                    if (name.isBlank() || name == "-") return@mapNotNull null
                    val icons = tr.select("td.mtd-icon img").map { it.attr("title").lowercase() }
                    Meal(
                        name = name,
                        price = tr.selectFirst("span.price_1")?.text()?.trim().orEmpty(),
                        vegan = icons.any { "vegan" in it },
                        vegetarian = icons.any { "vegetarisch" in it },
                    )
                }
                if (meals.isEmpty()) null else MensaLine(lineName, meals)
            }
            if (lines.isNotEmpty()) result[date] = lines
        }
        return result
    }
}
