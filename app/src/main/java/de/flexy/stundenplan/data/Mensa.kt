package de.flexy.stundenplan.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.temporal.IsoFields

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
        const val URL = MensaSource.URL
    }
}
