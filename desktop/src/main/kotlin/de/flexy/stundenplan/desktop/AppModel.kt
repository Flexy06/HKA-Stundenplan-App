package de.flexy.stundenplan.desktop

import de.flexy.stundenplan.data.ChangeDetector
import de.flexy.stundenplan.data.Lecture
import de.flexy.stundenplan.data.LectureFilter
import de.flexy.stundenplan.data.MensaLine
import de.flexy.stundenplan.data.Rhythm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class UiState(
    val settings: Settings = Settings(),
    val all: List<Lecture> = emptyList(),
    val mensa: Map<LocalDate, List<MensaLine>> = emptyMap(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val message: String? = null,
) {
    val filter: LectureFilter get() = LectureFilter(settings.hidden, settings.rhythms, settings.skipped)

    val visible: List<Lecture> by lazy {
        val f = filter
        all.filter { f.shows(it) && (settings.showCancelled || !it.cancelled) }
    }

    val byDay: Map<LocalDate, List<Lecture>> by lazy { visible.groupBy { it.date } }

    val modules: List<String> by lazy {
        all.filter { !it.cancelled }.map { it.title }.distinct().sortedBy { it.lowercase() }
    }
}

/**
 * Zustand + Logik der Windows-App (entspricht dem ViewModel der Android-App),
 * inkl. Hintergrund-Abgleich und Erinnerungen.
 */
class AppModel(private val scope: CoroutineScope, private val notify: (title: String, text: String) -> Unit) {

    private val _state = MutableStateFlow(UiState(settings = SettingsStore.load()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var refreshJob: Job? = null
    private val reminded = HashSet<String>()

    init {
        scope.launch {
            val sem = _state.value.settings.semester
            val cached = Repository.loadCached(sem)
            _state.update { it.copy(all = cached.orEmpty(), loading = cached == null) }
            refresh(silent = true)
        }
        // Abgleich alle 3 Stunden (meldet Änderungen)
        scope.launch {
            while (true) {
                delay(3 * 60 * 60 * 1000L)
                refresh(silent = true)
            }
        }
        // Erinnerungen vor Vorlesungen
        scope.launch {
            while (true) {
                delay(30_000)
                checkReminders()
            }
        }
    }

    fun refresh(silent: Boolean = false) {
        if (refreshJob?.isActive == true) return
        val sem = _state.value.settings.semester
        refreshJob = scope.launch {
            _state.update { it.copy(refreshing = true, message = null) }
            loadMensa()
            try {
                val before = _state.value.all
                val fresh = Repository.fetch(sem)
                val changes = if (before.isNotEmpty()) {
                    ChangeDetector.diff(before, fresh, _state.value.filter, LocalDateTime.now())
                } else emptyList()
                updateSettings { it.copy(lastUpdated = System.currentTimeMillis()) }
                _state.update { it.copy(all = fresh, loading = false, refreshing = false) }
                if (changes.isNotEmpty()) {
                    val text = changes.take(4).joinToString("\n") { it.text }
                    if (_state.value.settings.notifyChanges) {
                        notify(if (changes.size == 1) "Stundenplan geändert" else "Stundenplan: ${changes.size} Änderungen", text)
                    }
                    _state.update { it.copy(message = changes.first().text) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = when (e) {
                    is java.net.UnknownHostException -> "Keine Internetverbindung – zeige gespeicherten Stand."
                    is java.net.SocketTimeoutException -> "Raumzeit antwortet nicht – zeige gespeicherten Stand."
                    else -> e.message ?: "Aktualisieren fehlgeschlagen."
                }
                _state.update { it.copy(loading = false, refreshing = false, message = if (silent && it.all.isNotEmpty()) null else msg) }
            }
        }
    }

    private fun loadMensa(force: Boolean = false) {
        if (!_state.value.settings.showMensa) return
        scope.launch {
            val data = runCatching { Repository.loadMensa(force) }.getOrDefault(emptyMap())
            if (data.isNotEmpty()) _state.update { it.copy(mensa = data) }
        }
    }

    private fun checkReminders() {
        val s = _state.value
        val minutes = s.settings.reminderMinutes
        if (minutes <= 0) return
        val now = LocalDateTime.now()
        val time = DateTimeFormatter.ofPattern("HH:mm")
        for (l in s.visible) {
            if (l.cancelled) continue
            val until = Duration.between(now, l.start).toMinutes()
            if (until in 0..minutes.toLong()) {
                val key = LectureFilter.key(l)
                if (reminded.add(key)) {
                    val room = if (l.location.isNotBlank()) " · ${l.location}" else ""
                    notify(
                        if (until <= 1) "${l.title} beginnt jetzt" else "${l.title} in $until min",
                        "${l.start.format(time)}–${l.end.format(time)}$room",
                    )
                }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun updateSettings(change: (Settings) -> Settings) {
        val next = change(_state.value.settings)
        SettingsStore.save(next)
        _state.update { it.copy(settings = next) }
    }

    fun setSemester(value: String) {
        val sem = value.trim().uppercase()
        if (sem.isEmpty() || sem == _state.value.settings.semester) return
        updateSettings { it.copy(semester = sem) }
        refreshJob?.cancel()
        scope.launch {
            val cached = Repository.loadCached(sem)
            _state.update { it.copy(all = cached.orEmpty(), loading = cached == null) }
            refresh()
        }
    }

    fun setModuleVisible(title: String, visible: Boolean) =
        updateSettings { it.copy(hidden = if (visible) it.hidden - title else it.hidden + title) }

    fun setRhythm(title: String, rhythm: Rhythm) = updateSettings {
        it.copy(rhythms = if (rhythm == Rhythm.WEEKLY) it.rhythms - title else it.rhythms + (title to rhythm))
    }

    fun setSkipped(lecture: Lecture, skip: Boolean) = updateSettings {
        val k = LectureFilter.key(lecture)
        it.copy(skipped = if (skip) it.skipped + k else it.skipped - k)
    }

    fun clearSkipped() = updateSettings { it.copy(skipped = emptySet()) }
    fun setShowCancelled(v: Boolean) = updateSettings { it.copy(showCancelled = v) }
    fun setWeekView(v: Boolean) = updateSettings { it.copy(weekView = v) }
    fun setReminderMinutes(v: Int) = updateSettings { it.copy(reminderMinutes = v) }
    fun setNotifyChanges(v: Boolean) = updateSettings { it.copy(notifyChanges = v) }

    fun setThemeMode(v: String) = updateSettings { it.copy(themeMode = v) }
    fun setMiniWindow(v: Boolean) = updateSettings { it.copy(miniWindow = v) }

    fun saveWindow(x: Int, y: Int, w: Int, h: Int, maximized: Boolean) {
        val s = _state.value.settings
        if (maximized) {
            if (!s.winMax) updateSettings { it.copy(winMax = true) }
        } else if (s.winMax || s.winX != x || s.winY != y || s.winW != w || s.winH != h) {
            updateSettings { it.copy(winX = x, winY = y, winW = w, winH = h, winMax = false) }
        }
    }

    fun saveMiniPosition(x: Int, y: Int) {
        val s = _state.value.settings
        if (s.miniX != x || s.miniY != y) updateSettings { it.copy(miniX = x, miniY = y) }
    }

    /** Schreibt die sichtbaren (gefilterten) Termine als .ics in den Downloads-Ordner. */
    fun exportIcs(): java.io.File? {
        val s = _state.value
        val lectures = s.visible.filter { !it.cancelled }
        if (lectures.isEmpty()) {
            _state.update { it.copy(message = "Keine Termine zum Exportieren.") }
            return null
        }
        return runCatching {
            val home = java.io.File(System.getProperty("user.home"))
            val dir = java.io.File(home, "Downloads").takeIf { it.isDirectory } ?: home
            val file = java.io.File(dir, "Stundenplan-${s.settings.semester.replace(Regex("[^A-Za-z0-9._-]"), "_")}.ics")
            file.writeText(IcsExport.build(lectures, s.settings.semester), Charsets.UTF_8)
            _state.update { it.copy(message = "${lectures.size} Termine exportiert: ${file.absolutePath}") }
            file
        }.getOrElse { e ->
            _state.update { it.copy(message = "Export fehlgeschlagen: ${e.message}") }
            null
        }
    }

    fun setShowMensa(v: Boolean) {
        updateSettings { it.copy(showMensa = v) }
        if (v) loadMensa()
    }

    fun setAutostart(v: Boolean) {
        if (Autostart.set(v)) updateSettings { it.copy(autostart = v) }
        else _state.update { it.copy(message = "Autostart ließ sich nicht ändern (nur in der installierten App möglich).") }
    }
}

/**
 * Welcher Tag beim Öffnen gezeigt wird: heute, solange heute noch etwas ansteht –
 * sonst der nächste Tag mit Veranstaltungen (höchstens 3 Tage voraus).
 */
fun smartStartDay(lectures: List<Lecture>, now: LocalDateTime): LocalDate {
    val today = now.toLocalDate()
    val active = lectures.filter { !it.cancelled }
    if (active.any { it.date == today && it.end.isAfter(now) }) return today
    val next = active.firstOrNull { it.start.isAfter(now) } ?: return today
    return if (ChronoUnit.DAYS.between(today, next.date) <= 3) next.date else today
}
