package de.flexy.stundenplan.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.flexy.stundenplan.data.Lecture
import de.flexy.stundenplan.data.Rhythm
import de.flexy.stundenplan.data.RoomInfo
import de.flexy.stundenplan.desktop.AppModel
import de.flexy.stundenplan.desktop.Autostart
import de.flexy.stundenplan.desktop.Repository
import de.flexy.stundenplan.desktop.UiState
import de.flexy.stundenplan.desktop.smartStartDay
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale

/** Navigation, die das Fenster (Tastatur) von außen auslösen kann. */
class NavController {
    var step: (Int) -> Unit = {}
    var today: () -> Unit = {}
    var closeOverlay: () -> Boolean = { false }
    /** true, solange ein Eingabefeld offen ist (Pfeiltasten nicht abfangen) */
    var typing: () -> Boolean = { false }
}

@Composable
fun App(model: AppModel, nav: NavController) {
    val state by model.state.collectAsState()
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay(30_000)
            value = LocalDateTime.now()
        }
    }
    val today = now.toLocalDate()

    var focus by remember { mutableStateOf(LocalDate.now()) }
    var autoFocused by remember { mutableStateOf(false) }
    LaunchedEffect(state.visible) {
        if (!autoFocused && state.visible.isNotEmpty()) {
            autoFocused = true
            focus = smartStartDay(state.visible, LocalDateTime.now())
        }
    }

    var selected by remember { mutableStateOf<Lecture?>(null) }
    var campus by remember { mutableStateOf<Pair<RoomInfo, String?>?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    val week = state.settings.weekView
    nav.step = { dir -> focus = if (week) focus.plusWeeks(dir.toLong()) else focus.plusDays(dir.toLong()) }
    nav.today = { focus = LocalDate.now() }
    nav.closeOverlay = {
        when {
            campus != null -> { campus = null; true }
            showSettings -> { showSettings = false; true }
            selected != null -> { selected = null; true }
            else -> false
        }
    }
    nav.typing = { showSettings }
    val openRoom: (RoomInfo, String?) -> Unit = { room, title -> campus = room to title }

    // Surface setzt die Textfarbe (onSurface) – sonst wäre Text im Dunkelmodus schwarz
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxSize(),
    ) { Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(state, focus, week, onStep = nav.step, onToday = nav.today,
                onWeek = model::setWeekView, onRefresh = { model.refresh() }, onSettings = { showSettings = true },
                onMini = { model.setMiniWindow(!state.settings.miniWindow) })
            HorizontalDivider()
            Row(Modifier.weight(1f)) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when {
                        state.loading && state.all.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        week -> WeekPage(
                            weekStart = mondayOf(focus),
                            today = today,
                            byDay = state.byDay,
                            now = now,
                            bottomPadding = 0.dp,
                            onClick = { selected = it },
                            onDayClick = { d ->
                                focus = d
                                model.setWeekView(false)
                            },
                        )
                        else -> DayList(
                            date = focus,
                            today = today,
                            lectures = state.byDay[focus].orEmpty(),
                            mensa = if (state.settings.showMensa && !focus.isBefore(today)) state.mensa[focus] else null,
                            now = now,
                            selected = selected,
                            onClick = { selected = it },
                            onRoomClick = { r, t -> openRoom(r, t) },
                        )
                    }
                }
                VerticalDivider()
                Box(Modifier.width(360.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                    val sel = selected
                    if (sel != null) {
                        DetailPanel(
                            lecture = sel,
                            upcoming = state.visible.filter { it.title == sel.title && !it.cancelled && it.end.isAfter(now) && it != sel }.take(6),
                            rhythm = state.settings.rhythms[sel.title] ?: Rhythm.WEEKLY,
                            onRhythm = { model.setRhythm(sel.title, it) },
                            onSkip = {
                                model.setSkipped(sel, true)
                                selected = null
                            },
                            onRoomClick = { openRoom(it, sel.title) },
                            onClose = { selected = null },
                        )
                    } else {
                        OverviewPanel(
                            nextUp = state.visible.firstOrNull { !it.cancelled && it.start.isAfter(now) },
                            now = now,
                            mensaToday = if (state.settings.showMensa) state.mensa[if (week) today else focus] else null,
                            onClick = { selected = it },
                            onRoomClick = { r, t -> openRoom(r, t) },
                        )
                    }
                }
            }
            StatusBar(state, onDismiss = model::clearMessage)
        }

        if (showSettings) SettingsOverlay(state, model, onClose = { showSettings = false })
        campus?.let { (room, title) -> CampusMapOverlay(room, title, Repository.campusMap, onClose = { campus = null }) }
    } }
}

@Composable
private fun TopBar(
    state: UiState,
    focus: LocalDate,
    week: Boolean,
    onStep: (Int) -> Unit,
    onToday: () -> Unit,
    onWeek: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onMini: () -> Unit,
) {
    val monday = mondayOf(focus)
    val label = if (week) {
        val sunday = monday.plusDays(6)
        "KW ${focus.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)} · " +
            monday.format(DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN)) + " – " +
            sunday.format(DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN))
    } else {
        focus.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)) +
            " · KW ${focus.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(170.dp)) {
            Text("Stundenplan", style = MaterialTheme.typography.titleLarge)
            Text(state.settings.semester, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onStep(-1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Zurück") }
        FilledTonalButton(onClick = onToday) { Text("Heute") }
        IconButton(onClick = { onStep(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Weiter") }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(selected = !week, onClick = { onWeek(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Tag") }
            SegmentedButton(selected = week, onClick = { onWeek(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Woche") }
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (state.refreshing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, "Aktualisieren") }
        }
        IconButton(onClick = onMini) {
            Icon(Icons.Rounded.PictureInPictureAlt, if (state.settings.miniWindow) "Mini-Fenster schließen" else "Mini-Fenster (immer im Vordergrund)",
                tint = if (state.settings.miniWindow) MaterialTheme.colorScheme.primary else LocalContentColor.current)
        }
        IconButton(onClick = onSettings) { Icon(Icons.Rounded.Tune, "Einstellungen") }
    }
}

@Composable
private fun StatusBar(state: UiState, onDismiss: () -> Unit) {
    val updated = if (state.settings.lastUpdated > 0) {
        Instant.ofEpochMilli(state.settings.lastUpdated).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd.MM. HH:mm"))
    } else "noch nie"
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val msg = state.message
            if (msg != null) {
                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = onDismiss) { Text("OK") }
            } else {
                Text("Zuletzt aktualisiert: $updated · Quelle: raumzeit.hka-iwi.de · ←/→ blättern, Strg+R aktualisieren",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
            }
        }
    }
}

/* ------------------------------------------------------------------ Einstellungen */

@Composable
private fun SettingsOverlay(state: UiState, model: AppModel, onClose: () -> Unit) {
    var semester by remember { mutableStateOf(state.settings.semester) }
    val s = state.settings
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.width(560.dp).fillMaxHeight().padding(vertical = 32.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        ) {
            LazyColumn(Modifier.fillMaxSize().padding(24.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Einstellungen", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Schließen") }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = semester, onValueChange = { semester = it },
                            label = { Text("Studiensemester") }, placeholder = { Text("z.B. ELTB.1.A") },
                            singleLine = true, modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = { model.setSemester(semester) },
                            enabled = semester.isNotBlank() && semester.trim().uppercase() != s.semester,
                        ) { Text("Laden") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text("Darstellung", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(6.dp))
                        val modes = listOf("system" to "Wie Windows", "light" to "Hell", "dark" to "Dunkel")
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            modes.forEachIndexed { i, (key, label) ->
                                SegmentedButton(
                                    selected = s.themeMode == key,
                                    onClick = { model.setThemeMode(key) },
                                    shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                                ) { Text(label) }
                            }
                        }
                    }
                    SwitchRow("Mini-Fenster", "Kleines Fenster mit laufender und nächster Vorlesung, immer im Vordergrund (Strg+M)",
                        s.miniWindow, model::setMiniWindow)
                    SwitchRow("Ausfälle anzeigen", "Abgesagte Termine durchgestrichen zeigen", s.showCancelled, model::setShowCancelled)
                    SwitchRow("Mensa-Speiseplan", "Essen der Mensa Moltke anzeigen", s.showMensa, model::setShowMensa)
                    SwitchRow("Bei Änderungen benachrichtigen", "Ausfälle, Raumänderungen, Verlegungen (Abgleich alle 3 h)", s.notifyChanges, model::setNotifyChanges)
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text("Erinnerung vor Vorlesungen", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (m in listOf(0, 10, 15, 30)) {
                                FilterChip(selected = s.reminderMinutes == m, onClick = { model.setReminderMinutes(m) },
                                    label = { Text(if (m == 0) "Aus" else "$m min") })
                            }
                        }
                    }
                    if (Autostart.isWindows) {
                        SwitchRow(
                            "Mit Windows starten",
                            if (Autostart.available) "Startet unsichtbar im Infobereich, damit Erinnerungen ankommen"
                            else "Nur in der installierten App verfügbar",
                            s.autostart,
                        ) { model.setAutostart(it) }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Module", style = MaterialTheme.typography.titleMedium)
                    Text("Blende aus, was dich nicht betrifft. Rhythmus (14-tägig) stellst du per Klick auf einen Termin ein.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                }
                items(state.modules) { title ->
                    val c = moduleColors(title)
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(c.accent, RoundedCornerShape(50)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.bodyLarge)
                            s.rhythms[title]?.let { r ->
                                Text("Alle 2 Wochen · " + if (r == Rhythm.EVEN_WEEKS) "gerade KW" else "ungerade KW",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Switch(checked = title !in s.hidden, onCheckedChange = { model.setModuleVisible(title, it) })
                    }
                }
                item {
                    if (s.skipped.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${s.skipped.size} einzeln ausgeblendete Termine", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = model::clearSkipped) { Text("Wieder einblenden") }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text("Kalender-Export", style = MaterialTheme.typography.titleMedium)
                    Text("Speichert deinen gefilterten Stundenplan als .ics-Datei im Downloads-Ordner – zum Import in Outlook, Google Kalender oder Apple Kalender.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = { model.exportIcs()?.let { showInExplorer(it) } }) {
                        Icon(Icons.Rounded.Event, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Als .ics exportieren")
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text(
                        "Tastenkürzel: ←/→ blättern · Strg+T heute · Strg+R/F5 aktualisieren · Strg+M Mini-Fenster · Esc schließen",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Daten: raumzeit.hka-iwi.de · Mensa: sw-ka.de · Karte: © OpenStreetMap-Mitwirkende\n" +
                            "Inoffizielle Open-Source-App – kein Angebot der Hochschule Karlsruhe.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Öffnet den Explorer mit markierter Datei (Windows), sonst den Ordner. */
private fun showInExplorer(file: java.io.File) {
    runCatching {
        if (System.getProperty("os.name").lowercase().contains("win")) {
            ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
        } else {
            java.awt.Desktop.getDesktop().open(file.parentFile)
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
