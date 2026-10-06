package de.flexy.stundenplan.desktop

import de.flexy.stundenplan.data.Rhythm
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Ordner für Einstellungen und Offline-Daten: %APPDATA%\HKA-Stundenplan */
object AppDirs {
    val root: File = File(
        System.getenv("APPDATA") ?: (System.getProperty("user.home") + File.separator + ".config"),
        "HKA-Stundenplan",
    ).apply { mkdirs() }

    fun file(name: String) = File(root, name)
}

data class Settings(
    val semester: String = "ELTB.1.A",
    val hidden: Set<String> = emptySet(),
    val rhythms: Map<String, Rhythm> = emptyMap(),
    val skipped: Set<String> = emptySet(),
    val showCancelled: Boolean = true,
    val weekView: Boolean = true,
    val showMensa: Boolean = true,
    /** Minuten vor Beginn, 0 = aus */
    val reminderMinutes: Int = 10,
    val notifyChanges: Boolean = true,
    val autostart: Boolean = false,
    val lastUpdated: Long = 0L,
)

/** Speichert die Einstellungen als JSON-Datei. */
object SettingsStore {
    private val file = AppDirs.file("settings.json")

    fun load(): Settings {
        if (!file.exists()) return Settings()
        return runCatching {
            val o = JSONObject(file.readText())
            Settings(
                semester = o.optString("semester", "ELTB.1.A"),
                hidden = o.optJSONArray("hidden").toStringSet(),
                rhythms = o.optJSONObject("rhythms")?.let { r ->
                    r.keys().asSequence().toList().mapNotNull { k: String ->
                        runCatching { k to Rhythm.valueOf(r.getString(k)) }.getOrNull()
                    }.toMap()
                }.orEmpty(),
                skipped = o.optJSONArray("skipped").toStringSet(),
                showCancelled = o.optBoolean("showCancelled", true),
                weekView = o.optBoolean("weekView", true),
                showMensa = o.optBoolean("showMensa", true),
                reminderMinutes = o.optInt("reminderMinutes", 10),
                notifyChanges = o.optBoolean("notifyChanges", true),
                autostart = o.optBoolean("autostart", false),
                lastUpdated = o.optLong("lastUpdated", 0L),
            )
        }.getOrDefault(Settings())
    }

    fun save(s: Settings) {
        val o = JSONObject()
            .put("semester", s.semester)
            .put("hidden", JSONArray(s.hidden.toList()))
            .put("rhythms", JSONObject().apply { s.rhythms.forEach { (k, v) -> put(k, v.name) } })
            .put("skipped", JSONArray(s.skipped.toList()))
            .put("showCancelled", s.showCancelled)
            .put("weekView", s.weekView)
            .put("showMensa", s.showMensa)
            .put("reminderMinutes", s.reminderMinutes)
            .put("notifyChanges", s.notifyChanges)
            .put("autostart", s.autostart)
            .put("lastUpdated", s.lastUpdated)
        runCatching { file.writeText(o.toString(2)) }
    }

    private fun JSONArray?.toStringSet(): Set<String> =
        if (this == null) emptySet() else (0 until length()).map { getString(it) }.toSet()
}
