package de.flexy.stundenplan.desktop

import de.flexy.stundenplan.data.CampusMapData
import de.flexy.stundenplan.data.CampusMapParser
import de.flexy.stundenplan.data.Lecture
import de.flexy.stundenplan.data.MensaLine
import de.flexy.stundenplan.data.MensaParser
import de.flexy.stundenplan.data.MensaSource
import de.flexy.stundenplan.data.TimetableBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.time.LocalDate
import java.time.temporal.IsoFields

/** Holt Stundenplan (Raumzeit), Mensa-Speiseplan und Campuskarte – mit Offline-Cache. */
object Repository {
    private const val BASE = "https://raumzeit.hka-iwi.de/api/v1/timetables/public/"

    private fun key(sem: String) = sem.replace(Regex("[^A-Za-z0-9._-]"), "_")
    private fun icsFile(sem: String) = AppDirs.file("timetable_${key(sem)}.ics")
    private fun jsonFile(sem: String) = AppDirs.file("timetable_${key(sem)}.json")

    suspend fun loadCached(sem: String): List<Lecture>? = withContext(Dispatchers.IO) {
        val ics = icsFile(sem)
        if (!ics.exists()) return@withContext null
        runCatching {
            TimetableBuilder.build(ics.readText(), jsonFile(sem).takeIf { it.exists() }?.readText())
        }.getOrNull()
    }

    suspend fun fetch(sem: String): List<Lecture> = withContext(Dispatchers.IO) {
        val url = BASE + URLEncoder.encode(sem, "UTF-8").replace("+", "%20")
        val ics = download(url, "text/calendar", sem)
        val json = runCatching { download(url, "application/json", sem) }.getOrNull()
        val lectures = TimetableBuilder.build(ics, json)
        icsFile(sem).writeText(ics)
        if (json != null) jsonFile(sem).writeText(json)
        lectures
    }

    suspend fun loadMensa(force: Boolean = false): Map<LocalDate, List<MensaLine>> = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val result = LinkedHashMap<LocalDate, List<MensaLine>>()
        for (week in listOf(today, today.plusWeeks(1))) {
            val kw = week.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            val year = week.get(IsoFields.WEEK_BASED_YEAR)
            val file = AppDirs.file("mensa_${year}_$kw.html")
            val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < 3 * 60 * 60 * 1000L
            val html = if (fresh && !force) {
                file.readText()
            } else {
                try {
                    get(MensaSource.weekUrl(kw)).also { if (it.contains("canteen-day-nav")) file.writeText(it) }
                } catch (e: Exception) {
                    if (file.exists()) file.readText() else null
                }
            } ?: continue
            runCatching { result.putAll(MensaParser.parse(html)) }
        }
        result
    }

    val campusMap: CampusMapData by lazy {
        val text = Repository::class.java.getResourceAsStream("/campus_map.txt")
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        CampusMapParser.parse(text)
    }

    private fun get(url: String): String = download(url, "text/html", "")

    private fun download(url: String, accept: String, sem: String): String {
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "Stundenplan-App (Windows)")
        }
        return try {
            when (val code = conn.responseCode) {
                200 -> conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                400 -> throw IOException("Für „$sem“ gibt es aktuell keinen Stundenplan.")
                423 -> throw IOException("Der Stundenplan für „$sem“ ist noch nicht freigegeben.")
                else -> throw IOException("Server antwortet mit Fehler $code.")
            }
        } finally {
            conn.disconnect()
        }
    }
}
