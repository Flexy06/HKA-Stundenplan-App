package de.flexy.stundenplan.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.runtime.saveable.rememberSaveable
import de.flexy.stundenplan.data.RoomInfo
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.flexy.stundenplan.data.Lecture
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.util.Locale

private const val PAGE_COUNT = 2 * 400 + 1
private const val CENTER = 400
private const val WEEK_COUNT = 2 * 60 + 1
private const val WEEK_CENTER = 60
private val GERMAN = Locale.GERMAN
internal val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun TimetableScreen(vm: TimetableViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var today by remember { mutableStateOf(LocalDate.now()) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var showSettings by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Lecture?>(null) }
    var campusTarget by remember { mutableStateOf<Pair<RoomInfo, String?>?>(null) }
    // Gemeinsamer "Fokus"-Tag für Tages- und Wochenansicht
    var focusDate by rememberSaveable { mutableStateOf(today.toEpochDay()) }

    // "Jetzt" alle 30 s neu, damit laufende Vorlesungen & Fortschritt stimmen
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay(30_000)
            value = LocalDateTime.now()
        }
    }

    val byDay = remember(state.visible) { state.visible.groupBy { it.date } }

    // Wenn heute alles vorbei ist, direkt den nächsten Uni-Tag zeigen (beim Start & nach längerer Pause)
    var jumpToken by remember { mutableIntStateOf(0) }
    var autoFocusDone by rememberSaveable { mutableStateOf(false) }
    var pausedAt by remember { mutableLongStateOf(0L) }
    LifecycleResumeEffect(Unit) {
        val awayLong = pausedAt > 0 && System.currentTimeMillis() - pausedAt > 10 * 60 * 1000L
        if (awayLong || LocalDate.now() != today) {
            today = LocalDate.now()
            autoFocusDone = false
        }
        onPauseOrDispose { pausedAt = System.currentTimeMillis() }
    }
    LaunchedEffect(state.visible, autoFocusDone) {
        if (autoFocusDone || state.visible.isEmpty()) return@LaunchedEffect
        autoFocusDone = true
        val target = smartStartDay(state.visible, LocalDateTime.now())
        if (target.toEpochDay() != focusDate) {
            focusDate = target.toEpochDay()
            jumpToken++
        }
    }
    val openRoom: (RoomInfo, String?) -> Unit = { room, title ->
        vm.loadCampusMap()
        campusTarget = room to title
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    campusTarget?.let { (room, title) ->
        CampusMapScreen(
            room = room,
            lectureTitle = title,
            map = state.campusMap,
            loading = state.campusMapLoading,
            error = state.campusMapError,
            onRetry = { vm.loadCampusMap(force = true) },
            onBack = { campusTarget = null },
        )
        return
    }

    val todayMonday = mondayOf(today)
    val weekMode = state.weekView
    // Pro Ansicht ein eigener Pager, der beim Umschalten am Fokus-Tag startet
    val pager = key(weekMode, today, jumpToken) {
        val focus = LocalDate.ofEpochDay(focusDate)
        rememberPagerState(
            initialPage = if (weekMode) {
                (WEEK_CENTER + ChronoUnit.WEEKS.between(todayMonday, mondayOf(focus))).toInt().coerceIn(0, WEEK_COUNT - 1)
            } else {
                (CENTER + ChronoUnit.DAYS.between(today, focus)).toInt().coerceIn(0, PAGE_COUNT - 1)
            }
        ) { if (weekMode) WEEK_COUNT else PAGE_COUNT }
    }

    val selected = if (weekMode) {
        todayMonday.plusWeeks((pager.currentPage - WEEK_CENTER).toLong())
    } else {
        today.plusDays((pager.currentPage - CENTER).toLong())
    }
    LaunchedEffect(selected, weekMode) {
        // In der Wochenansicht den Tag nur verschieben, wenn er nicht mehr in der Woche liegt
        val current = LocalDate.ofEpochDay(focusDate)
        if (!weekMode || mondayOf(current) != mondayOf(selected)) {
            focusDate = (if (weekMode && mondayOf(today) == selected) today else selected).toEpochDay()
        }
    }

    fun goTo(date: LocalDate) {
        scope.launch {
            val page = if (weekMode) {
                (WEEK_CENTER + ChronoUnit.WEEKS.between(todayMonday, mondayOf(date))).toInt().coerceIn(0, WEEK_COUNT - 1)
            } else {
                (CENTER + ChronoUnit.DAYS.between(today, date)).toInt().coerceIn(0, PAGE_COUNT - 1)
            }
            pager.animateScrollToPage(page)
        }
    }

    val isAtToday = if (state.weekView) mondayOf(selected) == todayMonday else selected == today
    val subtitle = if (state.weekView) {
        val end = selected.plusDays(6)
        "${state.semester} · KW ${selected.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)} · " +
            selected.format(DateTimeFormatter.ofPattern("d.M.")) + "–" + end.format(DateTimeFormatter.ofPattern("d.M."))
    } else {
        "${state.semester} · KW ${selected.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Stundenplan", style = MaterialTheme.typography.titleLarge)
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    AnimatedVisibility(visible = !isAtToday) {
                        FilledTonalButton(onClick = { goTo(today) }) { Text("Heute") }
                    }
                    IconButton(onClick = { vm.setWeekView(!state.weekView) }) {
                        Icon(
                            if (state.weekView) Icons.Rounded.ViewDay else Icons.Rounded.CalendarViewWeek,
                            contentDescription = if (state.weekView) "Tagesansicht" else "Wochenansicht",
                        )
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Einstellungen")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(top = padding.calculateTopPadding())
                .fillMaxSize()
        ) {
            if (!state.weekView) {
                WeekStrip(
                    selected = selected,
                    today = today,
                    hasEvents = { d -> byDay[d]?.any { !it.cancelled } == true },
                    onSelect = ::goTo,
                )
            }

            if (state.initialLoading && state.all.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ContainedLoadingIndicator(modifier = Modifier.size(72.dp))
                }
            } else if (state.weekView) {
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = vm::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                    ) { page ->
                        WeekPage(
                            weekStart = todayMonday.plusWeeks((page - WEEK_CENTER).toLong()),
                            today = today,
                            byDay = byDay,
                            now = now,
                            bottomPadding = padding.calculateBottomPadding(),
                            onClick = { detail = it },
                            onDayClick = { d ->
                                focusDate = d.toEpochDay()
                                vm.setWeekView(false)
                            },
                        )
                    }
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = vm::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                    ) { page ->
                        val date = today.plusDays((page - CENTER).toLong())
                        DayPage(
                            date = date,
                            today = today,
                            lectures = byDay[date].orEmpty(),
                            nextUp = if (date == today) {
                                state.visible.firstOrNull { !it.cancelled && it.date == today && it.start.isAfter(now) }
                            } else null,
                            now = now,
                            bottomPadding = padding.calculateBottomPadding(),
                            onClick = { detail = it },
                            onRoomClick = { room, title -> openRoom(room, title) },
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsSheet(state = state, vm = vm, onDismiss = { showSettings = false })
    }
    detail?.let { lecture ->
        LectureSheet(
            lecture = lecture,
            upcoming = state.all.filter { it.title == lecture.title && !it.cancelled && it.end.isAfter(now) && it !== lecture }.take(6),
            onRoomClick = { room ->
                detail = null
                openRoom(room, lecture.title)
            },
            onDismiss = { detail = null },
        )
    }
}

/* ---------------------------------------------------------------------------------------------- */

@Composable
private fun WeekStrip(
    selected: LocalDate,
    today: LocalDate,
    hasEvents: (LocalDate) -> Boolean,
    onSelect: (LocalDate) -> Unit,
) {
    val monday = selected.with(DayOfWeek.MONDAY)
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSelect(selected.minusWeeks(1)) }) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "Vorige Woche")
            }
            Text(
                monday.month.getDisplayName(TextStyle.FULL_STANDALONE, GERMAN) + " " + monday.year,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onSelect(selected.plusWeeks(1)) }) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Nächste Woche")
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (i in 0 until 7) {
                val d = monday.plusDays(i.toLong())
                DayPill(
                    date = d,
                    isSelected = d == selected,
                    isToday = d == today,
                    hasEvents = hasEvents(d),
                    onClick = { onSelect(d) },
                )
            }
        }
    }
}

@Composable
private fun DayPill(date: LocalDate, isSelected: Boolean, isToday: Boolean, hasEvents: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        when {
            isSelected -> cs.primary
            isToday -> cs.primaryContainer
            else -> Color.Transparent
        },
        label = "pillBg",
    )
    val fg = when {
        isSelected -> cs.onPrimary
        isToday -> cs.onPrimaryContainer
        date.dayOfWeek.value >= 6 -> cs.onSurfaceVariant.copy(alpha = 0.6f)
        else -> cs.onSurface
    }
    val shape = if (isSelected) MaterialShapes.Cookie9Sided.toShape() else CircleShape

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Text(
            date.dayOfWeek.getDisplayName(TextStyle.SHORT, GERMAN).take(2),
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) cs.primary else cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .size(42.dp)
                .clip(shape)
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected || isToday) FontWeight.ExtraBold else FontWeight.Medium,
                color = fg,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (hasEvents) cs.primary.copy(alpha = if (isSelected) 1f else 0.55f) else Color.Transparent)
        )
    }
}

/* ---------------------------------------------------------------------------------------------- */

private sealed interface Row_ {
    data class Item(val lecture: Lecture) : Row_
    data class Gap(val minutes: Long) : Row_
}

@Composable
private fun DayPage(
    date: LocalDate,
    today: LocalDate,
    lectures: List<Lecture>,
    nextUp: Lecture?,
    now: LocalDateTime,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onClick: (Lecture) -> Unit,
    onRoomClick: (RoomInfo, String?) -> Unit,
) {
    val rows = remember(lectures) { buildRows(lectures) }
    val active = lectures.filter { !it.cancelled }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + bottomPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            DayHeader(date, today, active)
        }
        // Heute: nächster Termin (auch wenn er erst an einem späteren Tag ist)
        val running = active.any { !now.isBefore(it.start) && now.isBefore(it.end) }
        if (nextUp != null && !running) {
            item(key = "next") {
                NextUpCard(
                    lecture = nextUp,
                    now = now,
                    onClick = { onClick(nextUp) },
                    onRoomClick = { onRoomClick(it, nextUp.title) },
                )
            }
        }
        if (lectures.isEmpty()) {
            item(key = "empty") { EmptyDay(date) }
        } else {
            items(rows.size, key = { i ->
                when (val r = rows[i]) {
                    is Row_.Item -> "$i|${r.lecture.id}"
                    is Row_.Gap -> "$i|gap"
                }
            }) { i ->
                val r = rows[i]
                when (r) {
                    is Row_.Item -> LectureCard(
                        r.lecture, now,
                        onClick = { onClick(r.lecture) },
                        onRoomClick = { room -> onRoomClick(room, r.lecture.title) },
                    )
                    is Row_.Gap -> GapRow(r.minutes)
                }
            }
        }
    }
}

private fun buildRows(lectures: List<Lecture>): List<Row_> {
    val out = ArrayList<Row_>()
    var lastEnd: LocalDateTime? = null
    for (l in lectures) {
        if (!l.cancelled) {
            val prev = lastEnd
            if (prev != null) {
                val gap = Duration.between(prev, l.start).toMinutes()
                if (gap >= 30) out += Row_.Gap(gap)
            }
            lastEnd = if (prev == null || l.end.isAfter(prev)) l.end else prev
        }
        out += Row_.Item(l)
    }
    return out
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate, active: List<Lecture>) {
    val rel = when (ChronoUnit.DAYS.between(today, date)) {
        0L -> "Heute"
        1L -> "Morgen"
        -1L -> "Gestern"
        else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, GERMAN)
    }
    Column(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
        Text(rel, style = MaterialTheme.typography.headlineMedium)
        val sub = buildString {
            append(date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", GERMAN)))
            if (active.isNotEmpty()) {
                append(" · ")
                append(if (active.size == 1) "1 Termin" else "${active.size} Termine")
                append(" · ")
                append(active.minOf { it.start }.format(TIME))
                append("–")
                append(active.maxOf { it.end }.format(TIME))
            }
        }
        Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyDay(date: LocalDate) {
    val weekend = date.dayOfWeek.value >= 6
    Column(
        Modifier.fillMaxWidth().padding(top = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = MaterialShapes.SoftBurst.toShape(),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.size(120.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    if (weekend) "WE" else "frei",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (weekend) "Wochenende" else "Keine Veranstaltungen",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            "Nach unten ziehen zum Aktualisieren",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GapRow(minutes: Long) {
    Row(
        Modifier.fillMaxWidth().padding(start = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Text(
            "  Pause · ${formatDuration(minutes)}  ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .width(24.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

internal fun formatDuration(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "$m min"
        m == 0L -> "$h h"
        else -> "$h h $m min"
    }
}

/**
 * Welcher Tag beim Öffnen gezeigt wird: heute, solange heute noch etwas ansteht –
 * sonst der nächste Tag mit Veranstaltungen (z.B. abends schon "Morgen").
 */
internal fun smartStartDay(lectures: List<Lecture>, now: LocalDateTime): LocalDate {
    val today = now.toLocalDate()
    val active = lectures.filter { !it.cancelled }
    if (active.any { it.date == today && it.end.isAfter(now) }) return today
    val next = active.firstOrNull { it.start.isAfter(now) } ?: return today
    // Nicht über Ferien hinweg springen – höchstens bis zum nächsten Montag
    return if (ChronoUnit.DAYS.between(today, next.date) <= 3) next.date else today
}
