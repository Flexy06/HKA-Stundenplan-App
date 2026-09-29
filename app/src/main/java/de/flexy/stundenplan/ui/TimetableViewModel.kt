package de.flexy.stundenplan.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.flexy.stundenplan.data.CampusMapData
import de.flexy.stundenplan.data.CampusMapRepository
import de.flexy.stundenplan.data.Lecture
import de.flexy.stundenplan.system.Reminders
import de.flexy.stundenplan.widget.NextLectureWidget
import de.flexy.stundenplan.data.TimetableRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val semester: String = TimetableRepository.DEFAULT_SEMESTER,
    val all: List<Lecture> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val showCancelled: Boolean = true,
    val refreshing: Boolean = false,
    val initialLoading: Boolean = true,
    val error: String? = null,
    val lastUpdated: Long = 0L,
    val reminderMinutes: Int = 0,
    val weekView: Boolean = false,
    val campusMap: CampusMapData? = null,
    val campusMapLoading: Boolean = false,
    val campusMapError: String? = null,
) {
    val visible: List<Lecture> by lazy {
        all.filter { it.title !in hidden && (showCancelled || !it.cancelled) }
    }

    /** Alle Modulnamen (für die Ein-/Ausblenden-Liste). */
    val modules: List<String> by lazy {
        all.filter { !it.cancelled }.map { it.title }.distinct().sortedBy { it.lowercase() }
    }
}

class TimetableViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TimetableRepository(app)
    private val campusRepo = CampusMapRepository(app)

    private val _state = MutableStateFlow(
        UiState(
            semester = repo.semester,
            hidden = repo.hiddenModules,
            showCancelled = repo.showCancelled,
            lastUpdated = repo.lastUpdated(repo.semester),
            reminderMinutes = repo.reminderMinutes,
            weekView = repo.viewMode == "week",
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    init {
        loadSemester(repo.semester)
    }

    private fun loadSemester(sem: String) {
        viewModelScope.launch {
            val cached = repo.loadCached(sem)
            _state.update {
                it.copy(
                    semester = sem,
                    all = cached ?: emptyList(),
                    initialLoading = cached == null,
                    lastUpdated = repo.lastUpdated(sem),
                    error = null,
                )
            }
            if (cached != null) onDataChanged()
            refresh()
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        val sem = _state.value.semester
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            try {
                val fresh = repo.fetch(sem)
                _state.update {
                    it.copy(all = fresh, lastUpdated = repo.lastUpdated(sem), refreshing = false, initialLoading = false)
                }
                onDataChanged()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = when (e) {
                    is java.net.UnknownHostException -> "Keine Internetverbindung – zeige gespeicherten Stand."
                    is java.net.SocketTimeoutException -> "Raumzeit antwortet nicht – zeige gespeicherten Stand."
                    else -> e.message ?: "Aktualisieren fehlgeschlagen."
                }
                _state.update { it.copy(refreshing = false, initialLoading = false, error = msg) }
            }
        }
    }

    /** Beim Zurückkehren in die App aktualisieren, wenn der Stand älter als 30 min ist. */
    fun refreshIfStale() {
        val s = _state.value
        if (s.initialLoading) return
        if (System.currentTimeMillis() - s.lastUpdated > 30 * 60 * 1000L) refresh()
    }

    fun setSemester(value: String) {
        val sem = value.trim().uppercase()
        if (sem.isEmpty() || sem == _state.value.semester) return
        repo.semester = sem
        refreshJob?.cancel()
        loadSemester(sem)
    }

    fun setModuleVisible(title: String, visible: Boolean) {
        val next = if (visible) _state.value.hidden - title else _state.value.hidden + title
        repo.hiddenModules = next
        _state.update { it.copy(hidden = next) }
        onDataChanged()
    }

    fun setShowCancelled(value: Boolean) {
        repo.showCancelled = value
        _state.update { it.copy(showCancelled = value) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun setReminderMinutes(minutes: Int) {
        repo.reminderMinutes = minutes
        _state.update { it.copy(reminderMinutes = minutes) }
        Reminders.reschedule(getApplication())
    }

    fun setWeekView(week: Boolean) {
        repo.viewMode = if (week) "week" else "day"
        _state.update { it.copy(weekView = week) }
    }

    /** Widget & Erinnerungen an neue Daten/Einstellungen anpassen. */
    private fun onDataChanged() {
        val app = getApplication<Application>()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { Reminders.reschedule(app) }
            runCatching { NextLectureWidget.updateAll(app) }
        }
    }

    fun loadCampusMap(force: Boolean = false) {
        val s = _state.value
        if (s.campusMapLoading || (s.campusMap != null && !force)) return
        viewModelScope.launch {
            _state.update { it.copy(campusMapLoading = true, campusMapError = null) }
            try {
                val map = campusRepo.load(force)
                _state.update { it.copy(campusMap = map, campusMapLoading = false) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(campusMapLoading = false, campusMapError = "Karte konnte nicht geladen werden.") }
            }
        }
    }
}
