package de.flexy.stundenplan.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.temporal.IsoFields

data class Meal(
    val name: String,
    val price: String,
    val vegan: Boolean,
    val vegetarian: Boolean,
)

/** Eine Ausgabelinie, z.B. "Wahlessen 1" mit ihren Gerichten. */
data class MensaLine(val name: String, val meals: List<Meal>)

/**
 * Speiseplan der Mensa Moltke vom Studierendenwerk Karlsruhe (öffentliche Webseite, HTML).
 * Lädt aktuelle und nächste Woche und speichert sie für 3 Stunden bzw. offline.
 */
class MensaRepository(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "mensa").apply { mkdirs() }

    suspend fun load(force: Boolean = false): Map<LocalDate, List<MensaLine>> = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val result = LinkedHashMap<LocalDate, List<MensaLine>>()
        for (week in listOf(today, today.plusWeeks(1))) {
            val html = week(week, force) ?: continue
            runCatching { result.putAll(MensaParser.parse(html)) }
        }
        result
    }

    private fun week(date: LocalDate, force: Boolean): String? {
        val kw = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val year = date.get(IsoFields.WEEK_BASED_YEAR)
        val file = File(dir, "moltke_${year}_$kw.html")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < 3 * 60 * 60 * 1000L
        if (fresh && !force) return file.readText()
        return try {
            val html = download("$URL?kw=$kw")
            if (html.contains("canteen-day-nav")) file.writeText(html)
            html
        } catch (e: Exception) {
            if (file.exists()) file.readText() else null
        }
    }

    private fun download(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "Stundenplan-App (Android)")
        }
        try {
            if (conn.responseCode != 200) throw java.io.IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val URL = "https://www.sw-ka.de/de/hochschulgastronomie/speiseplan/mensa_moltke/"
    }
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
