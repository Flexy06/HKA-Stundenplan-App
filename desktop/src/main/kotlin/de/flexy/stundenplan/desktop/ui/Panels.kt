package de.flexy.stundenplan.desktop.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.flexy.stundenplan.data.Campus
import de.flexy.stundenplan.data.Lecture
import de.flexy.stundenplan.data.LectureFilter
import de.flexy.stundenplan.data.Meal
import de.flexy.stundenplan.data.MensaLine
import de.flexy.stundenplan.data.MensaSource
import de.flexy.stundenplan.data.Rhythm
import de.flexy.stundenplan.data.RoomInfo
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

internal val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_LONG = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)
private val DATE_SHORT = DateTimeFormatter.ofPattern("EE, dd.MM.", Locale.GERMAN)

fun openUrl(url: String) {
    runCatching {
        if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().browse(java.net.URI(url))
    }
}

/* ------------------------------------------------------------------ Tagesliste */

@Composable
fun DayList(
    date: LocalDate,
    today: LocalDate,
    lectures: List<Lecture>,
    mensa: List<MensaLine>?,
    now: LocalDateTime,
    selected: Lecture?,
    onClick: (Lecture) -> Unit,
    onRoomClick: (RoomInfo, String) -> Unit,
) {
    val active = lectures.filter { !it.cancelled }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            val rel = when (ChronoUnit.DAYS.between(today, date)) {
                0L -> "Heute"
                1L -> "Morgen"
                -1L -> "Gestern"
                else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN)
            }
            Column(Modifier.padding(bottom = 4.dp)) {
                Text(rel, style = MaterialTheme.typography.headlineMedium)
                val sub = buildString {
                    append(date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)))
                    if (active.isNotEmpty()) {
                        append(" · ${active.size} ${if (active.size == 1) "Termin" else "Termine"} · ")
                        append(active.minOf { it.start }.format(TIME) + "–" + active.maxOf { it.end }.format(TIME))
                    }
                }
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (lectures.isEmpty()) {
            item {
                Text(
                    if (date.dayOfWeek.value >= 6) "Wochenende – keine Veranstaltungen" else "Keine Veranstaltungen",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp),
                )
            }
            if (mensa != null) item { MensaCard(mensa) }
        } else {
            var lastEnd: LocalDateTime? = null
            var mensaPlaced = mensa == null
            for (l in lectures) {
                if (!l.cancelled) {
                    val prev = lastEnd
                    if (prev != null) {
                        val gap = Duration.between(prev, l.start).toMinutes()
                        if (gap >= 30) item { GapRow(gap) }
                    }
                    if (!mensaPlaced && !l.start.toLocalTime().isBefore(java.time.LocalTime.of(12, 30))) {
                        mensaPlaced = true
                        item { MensaCard(mensa!!) }
                    }
                    lastEnd = if (prev == null || l.end.isAfter(prev)) l.end else prev
                }
                item { LectureRow(l, now, l == selected, onClick = { onClick(l) }, onRoomClick = { onRoomClick(it, l.title) }) }
            }
            if (!mensaPlaced && mensa != null) item { MensaCard(mensa) }
        }
    }
}

@Composable
private fun GapRow(minutes: Long) {
    Row(Modifier.fillMaxWidth().padding(start = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Text(
            "  Pause · ${formatDuration(minutes)}  ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.width(24.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    }
}

@Composable
fun LectureRow(lecture: Lecture, now: LocalDateTime, selected: Boolean, onClick: () -> Unit, onRoomClick: (RoomInfo) -> Unit) {
    val colors = moduleColors(lecture.title)
    val cs = MaterialTheme.colorScheme
    val running = !lecture.cancelled && !now.isBefore(lecture.start) && now.isBefore(lecture.end)
    val past = !lecture.cancelled && !now.isBefore(lecture.end)
    val content = if (lecture.cancelled) cs.onSurfaceVariant else colors.onContainer

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(52.dp).fillMaxHeight().padding(vertical = 14.dp), horizontalAlignment = Alignment.End) {
            Text(lecture.start.format(TIME), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = if (running) cs.primary else cs.onSurface)
            Spacer(Modifier.weight(1f))
            Text(lecture.end.format(TIME), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Card(
            onClick = onClick,
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = if (lecture.cancelled) cs.surfaceContainerLow else colors.container,
                contentColor = content,
            ),
            border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, colors.accent) else null,
            modifier = Modifier.weight(1f).alpha(if (past) 0.55f else 1f),
        ) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(
                    Modifier.padding(start = 10.dp, top = 14.dp, bottom = 14.dp).width(5.dp).fillMaxHeight()
                        .background(if (lecture.cancelled) cs.error else colors.accent, RoundedCornerShape(50))
                )
                Column(Modifier.padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
                    if (running) {
                        Chip("Läuft · noch ${formatDuration(Duration.between(now, lecture.end).toMinutes() + 1)}", cs.primary, cs.onPrimary)
                        Spacer(Modifier.height(6.dp))
                    }
                    if (lecture.cancelled) {
                        Chip(if (lecture.note.isNotBlank()) "Entfällt · ${lecture.note}" else "Entfällt", cs.errorContainer, cs.onErrorContainer, Icons.Rounded.EventBusy)
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        lecture.title, style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (lecture.cancelled) TextDecoration.LineThrough else null,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (lecture.location.isNotBlank()) {
                            RoomChip(lecture.location, content, onRoomClick)
                            Spacer(Modifier.width(10.dp))
                        }
                        if (lecture.code.isNotBlank() && lecture.code != lecture.title) {
                            Text(lecture.code, style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.7f), maxLines = 1)
                        }
                    }
                    if (running) {
                        val total = lecture.duration.toMinutes().coerceAtLeast(1)
                        val done = Duration.between(lecture.start, now).toMinutes()
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { (done.toFloat() / total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.accent,
                            trackColor = content.copy(alpha = 0.15f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Chip(text: String, bg: Color, fg: Color, icon: ImageVector? = null) {
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RoomChip(location: String, color: Color, onRoomClick: (RoomInfo) -> Unit) {
    val room = remember(location) { Campus.parseRooms(location).firstOrNull() }
    if (room == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Place, null, Modifier.size(16.dp), tint = color)
            Spacer(Modifier.width(4.dp))
            Text(location, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
        }
    } else {
        Surface(
            onClick = { onRoomClick(room) },
            shape = RoundedCornerShape(50),
            color = color.copy(alpha = 0.12f),
            contentColor = color,
        ) {
            Row(Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Place, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(location, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/* ------------------------------------------------------------------ Mensa */

@Composable
fun MensaCard(lines: List<MensaLine>, modifier: Modifier = Modifier, startExpanded: Boolean = false) {
    var expanded by remember { mutableStateOf(startExpanded) }
    val cs = MaterialTheme.colorScheme
    Card(
        onClick = { expanded = !expanded },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer),
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Restaurant, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Mensa Moltke", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
            }
            Spacer(Modifier.height(8.dp))
            if (!expanded) {
                lines.take(4).forEach { line ->
                    val meal = line.meals.first()
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(line.name, style = MaterialTheme.typography.labelMedium, color = cs.onTertiaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.width(100.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        MealText(meal, 1, Modifier.weight(1f))
                        Text(meal.price, style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    lines.forEach { line ->
                        Column {
                            Text(line.name.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            line.meals.forEach { meal ->
                                Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                                    MealText(meal, 3, Modifier.weight(1f))
                                    Spacer(Modifier.width(8.dp))
                                    Text(meal.price, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                    TextButton(onClick = { openUrl(MensaSource.URL) }, contentPadding = PaddingValues(0.dp)) {
                        Text("Speiseplan auf sw-ka.de öffnen")
                    }
                }
            }
        }
    }
}

@Composable
private fun MealText(meal: Meal, maxLines: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (meal.vegan || meal.vegetarian) {
            Icon(Icons.Rounded.Eco, if (meal.vegan) "vegan" else "vegetarisch", Modifier.size(14.dp),
                tint = if (meal.vegan) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(4.dp))
        }
        Text(meal.name, style = MaterialTheme.typography.bodyMedium, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/* ------------------------------------------------------------------ Seitenleiste */

@Composable
fun OverviewPanel(nextUp: Lecture?, now: LocalDateTime, mensaToday: List<MensaLine>?, onClick: (Lecture) -> Unit, onRoomClick: (RoomInfo, String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (nextUp != null) {
            val minutes = Duration.between(now, nextUp.start).toMinutes() + 1
            val whenText = when (nextUp.date) {
                now.toLocalDate() -> "in ${formatDuration(minutes)}"
                now.toLocalDate().plusDays(1) -> "morgen um ${nextUp.start.format(TIME)}"
                else -> nextUp.start.format(DATE_SHORT) + " " + nextUp.start.format(TIME)
            }
            Card(
                onClick = { onClick(nextUp) },
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Als Nächstes · $whenText", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(nextUp.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${nextUp.start.format(TIME)}–${nextUp.end.format(TIME)}", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(12.dp))
                        if (nextUp.location.isNotBlank()) RoomChip(nextUp.location, cs.onPrimaryContainer) { onRoomClick(it, nextUp.title) }
                    }
                }
            }
        } else {
            Text("Keine anstehenden Termine", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
        }
        if (mensaToday != null) MensaCard(mensaToday, startExpanded = true)
    }
}

@Composable
fun DetailPanel(
    lecture: Lecture,
    upcoming: List<Lecture>,
    rhythm: Rhythm,
    onRhythm: (Rhythm) -> Unit,
    onSkip: () -> Unit,
    onRoomClick: (RoomInfo) -> Unit,
    onClose: () -> Unit,
) {
    val colors = moduleColors(lecture.title)
    val cs = MaterialTheme.colorScheme
    val rooms = remember(lecture.location) { Campus.parseRooms(lecture.location) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 10.dp).size(width = 32.dp, height = 6.dp).background(colors.accent, RoundedCornerShape(50)))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Schließen") }
        }
        Text(lecture.title, style = MaterialTheme.typography.headlineSmall)
        if (lecture.code.isNotBlank()) Text(lecture.code, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        if (lecture.cancelled) {
            Spacer(Modifier.height(8.dp))
            Chip(if (lecture.note.isNotBlank()) "Entfällt · ${lecture.note}" else "Entfällt", cs.errorContainer, cs.onErrorContainer, Icons.Rounded.EventBusy)
        }
        Spacer(Modifier.height(12.dp))
        InfoLine(Icons.Rounded.Schedule, lecture.start.format(DATE_LONG),
            "${lecture.start.format(TIME)} – ${lecture.end.format(TIME)} Uhr (${formatDuration(lecture.duration.toMinutes())})")
        if (rooms.isEmpty() && lecture.location.isNotBlank()) InfoLine(Icons.Rounded.Place, lecture.location, "Ort")
        for (room in rooms) {
            Surface(onClick = { onRoomClick(room) }, shape = MaterialTheme.shapes.medium, color = Color.Transparent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InfoLine(Icons.Rounded.Place, "Raum ${room.title}", room.subtitle, Modifier.weight(1f))
                    Icon(Icons.Rounded.Map, "Campuskarte", Modifier.padding(end = 8.dp))
                }
            }
        }

        if (!lecture.cancelled) {
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("Wie oft hast du das?", style = MaterialTheme.typography.titleMedium)
            Text(
                "Raumzeit trägt z.B. Labore wöchentlich ein, auch wenn deine Gruppe nur alle 2 Wochen dran ist.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val withThis = LectureFilter.biweeklyFor(lecture, true)
            val withoutThis = LectureFilter.biweeklyFor(lecture, false)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = rhythm == Rhythm.WEEKLY, onClick = { onRhythm(Rhythm.WEEKLY) }, label = { Text("Jede Woche") })
                FilterChip(selected = rhythm == withThis, onClick = { onRhythm(withThis) }, label = { Text("Alle 2 Wochen – mit diesem Termin") })
                FilterChip(selected = rhythm == withoutThis, onClick = { onRhythm(withoutThis) }, label = { Text("Alle 2 Wochen – ohne diesen") })
            }
            if (rhythm != Rhythm.WEEKLY) {
                val even = rhythm == Rhythm.EVEN_WEEKS
                Text(
                    "Wird nur in ${if (even) "geraden" else "ungeraden"} Kalenderwochen angezeigt (dieser Termin: KW ${LectureFilter.weekOf(lecture.date)}).",
                    style = MaterialTheme.typography.bodySmall, color = cs.primary, modifier = Modifier.padding(top = 4.dp),
                )
            }
            TextButton(onClick = onSkip, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Rounded.EventBusy, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Nur diesen einen Termin ausblenden")
            }
        }

        if (upcoming.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("Nächste Termine", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            upcoming.forEach { u ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(u.start.format(DATE_SHORT), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(92.dp))
                    Text("${u.start.format(TIME)}–${u.end.format(TIME)}", style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.width(96.dp))
                    Text(u.location, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun InfoLine(icon: ImageVector, headline: String, supporting: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.padding(horizontal = 8.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(headline, style = MaterialTheme.typography.bodyLarge)
            Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
