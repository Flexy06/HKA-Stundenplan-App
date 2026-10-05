package de.flexy.stundenplan.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Holt den Stundenplan über die öffentliche REST-API von Raumzeit (HKA)
 * und hält eine Offline-Kopie im App-Speicher.
 */
class TimetableRepository(context: Context) {

    companion object {
        const val DEFAULT_SEMESTER = "ELTB.1.A"
        private const val BASE = "https://raumzeit.hka-iwi.de/api/v1/timetables/public/"
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var semester: String
        get() = prefs.getString("semester", DEFAULT_SEMESTER) ?: DEFAULT_SEMESTER
        set(value) = prefs.edit().putString("semester", value).apply()

    var hiddenModules: Set<String>
        get() = prefs.getStringSet("hidden", emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet("hidden", value).apply()

    /** 14-tägiger Rhythmus pro Modul (gespeichert als "Titel\u0001EVEN_WEEKS"). */
    var rhythms: Map<String, Rhythm>
        get() = prefs.getStringSet("rhythms", emptySet()).orEmpty().mapNotNull { e ->
            val title = e.substringBefore('\u0001')
            val r = runCatching { Rhythm.valueOf(e.substringAfter('\u0001')) }.getOrNull()
            if (r == null || r == Rhythm.WEEKLY) null else title to r
        }.toMap()
        set(value) = prefs.edit().putStringSet(
            "rhythms",
            value.filterValues { it != Rhythm.WEEKLY }.map { (t, r) -> "$t\u0001${r.name}" }.toSet(),
        ).apply()

    /** Einzeln ausgeblendete Termine ("Titel|Startzeit"). */
    var skipped: Set<String>
        get() = prefs.getStringSet("skipped", emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet("skipped", value).apply()

    val filter: LectureFilter get() = LectureFilter(hiddenModules, rhythms, skipped)

    /** Tagesvorschau: 0 = aus, 1 = am Vorabend (20 Uhr), 2 = morgens (7 Uhr). */
    var digestMode: Int
        get() = prefs.getInt("digestMode", 0)
        set(value) = prefs.edit().putInt("digestMode", value).apply()

    var showCancelled: Boolean
        get() = prefs.getBoolean("showCancelled", true)
        set(value) = prefs.edit().putBoolean("showCancelled", value).apply()

    /** Minuten vor Beginn für die Erinnerung, 0 = aus. */
    var reminderMinutes: Int
        get() = prefs.getInt("reminderMinutes", 0)
        set(value) = prefs.edit().putInt("reminderMinutes", value).apply()

    var showMensa: Boolean
        get() = prefs.getBoolean("showMensa", true)
        set(value) = prefs.edit().putBoolean("showMensa", value).apply()

    /** Benachrichtigung bei Ausfällen/Raumänderungen (Hintergrund-Abgleich). */
    var notifyChanges: Boolean
        get() = prefs.getBoolean("notifyChanges", true)
        set(value) = prefs.edit().putBoolean("notifyChanges", value).apply()

    /** "day" oder "week" */
    var viewMode: String
        get() = prefs.getString("viewMode", "day") ?: "day"
        set(value) = prefs.edit().putString("viewMode", value).apply()

    fun lastUpdated(sem: String): Long = prefs.getLong("updated_${key(sem)}", 0L)

    /** Offline-Kopie laden (null, wenn es noch keine gibt). */
    suspend fun loadCached(sem: String): List<Lecture>? = withContext(Dispatchers.IO) { loadCachedSync(sem) }

    /** Synchrone Variante für Widget & Erinnerungen (liest nur lokale Dateien). */
    fun loadCachedSync(sem: String = semester): List<Lecture>? {
        val ics = icsFile(sem)
        if (!ics.exists()) return null
        return runCatching {
            build(ics.readText(), jsonFile(sem).takeIf { it.exists() }?.readText())
        }.getOrNull()
    }

    /** Termine, die tatsächlich stattfinden und nicht ausgeblendet sind. */
    fun upcomingActive(now: java.time.LocalDateTime = java.time.LocalDateTime.now()): List<Lecture> {
        val f = filter
        return loadCachedSync().orEmpty().filter { !it.cancelled && f.shows(it) && it.end.isAfter(now) }
    }

    /** Frisch vom Server laden und Cache aktualisieren. */
    suspend fun fetch(sem: String): List<Lecture> = withContext(Dispatchers.IO) {
        val url = BASE + Uri.encode(sem)
        val ics = download(url, "text/calendar", sem)
        // Ausfälle sind "nice to have" – wenn das JSON scheitert, trotzdem weitermachen
        val json = runCatching { download(url, "application/json", sem) }.getOrNull()

        val lectures = build(ics, json)
        icsFile(sem).writeText(ics)
        if (json != null) jsonFile(sem).writeText(json)
        prefs.edit().putLong("updated_${key(sem)}", System.currentTimeMillis()).apply()
        lectures
    }

    private fun build(ics: String, json: String?): List<Lecture> {
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
    private fun dedupe(list: List<Lecture>): List<Lecture> =
        list.groupBy { Triple(it.start, it.end, it.location.lowercase()) }
            .values
            .map { same -> same.maxBy { it.title.length } }

    private fun download(url: String, accept: String, sem: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "Stundenplan-App (Android)")
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

    private fun key(sem: String) = sem.replace(Regex("[^A-Za-z0-9._-]"), "_")
    private fun icsFile(sem: String) = File(appContext.filesDir, "timetable_${key(sem)}.ics")
    private fun jsonFile(sem: String) = File(appContext.filesDir, "timetable_${key(sem)}.json")
}
