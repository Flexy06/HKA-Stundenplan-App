package de.flexy.stundenplan.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.flexy.stundenplan.data.Lecture
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HM = DateTimeFormatter.ofPattern("HH:mm")

/** Laufende und nächste Veranstaltung. */
data class NowNext(val current: Lecture?, val next: Lecture?)

fun nowNext(visible: List<Lecture>, now: LocalDateTime): NowNext {
    val active = visible.filter { !it.cancelled }
    return NowNext(
        current = active.firstOrNull { !it.start.isAfter(now) && it.end.isAfter(now) },
        next = active.firstOrNull { it.start.isAfter(now) },
    )
}

/** Kurztext für den Tooltip im Infobereich. */
fun trayTooltip(visible: List<Lecture>, now: LocalDateTime): String {
    val (cur, next) = nowNext(visible, now)
    val lines = mutableListOf("HKA Stundenplan")
    if (cur != null) {
        lines += "Jetzt: ${cur.title} bis ${cur.end.format(HM)}" + room(cur)
    }
    if (next != null) lines += "Nächste: ${next.title} " + whenText(next, now) + room(next)
    // Windows begrenzt Tooltips auf 127 Zeichen
    return lines.joinToString("\n").take(127)
}

private fun room(l: Lecture) = if (l.location.isNotBlank()) " · ${l.location.substringBefore(',')}" else ""

private fun whenText(l: Lecture, now: LocalDateTime): String {
    val mins = Duration.between(now, l.start).toMinutes()
    return when {
        l.date == now.toLocalDate() && mins < 60 -> "in ${mins.coerceAtLeast(1)} min"
        l.date == now.toLocalDate() -> "um ${l.start.format(HM)}"
        l.date == now.toLocalDate().plusDays(1) -> "morgen ${l.start.format(HM)}"
        else -> l.start.format(DateTimeFormatter.ofPattern("EE d.M. HH:mm", Locale.GERMAN))
    }
}

/** Inhalt des kleinen Fensters, das immer im Vordergrund bleibt. */
@Composable
fun MiniView(visible: List<Lecture>, now: LocalDateTime, onOpen: () -> Unit) {
    val (cur, next) = nowNext(visible, now)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxSize().clickable(onClick = onOpen),
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            if (cur == null && next == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Keine anstehenden Termine", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }
            if (cur != null) {
                MiniEntry(cur, "Jetzt · noch ${formatDuration(Duration.between(now, cur.end).toMinutes().coerceAtLeast(1))}")
                val total = Duration.between(cur.start, cur.end).toMinutes().coerceAtLeast(1)
                val done = Duration.between(cur.start, now).toMinutes()
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (done.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = moduleColors(cur.title).accent,
                )
                Spacer(Modifier.height(10.dp))
            }
            if (next != null) MiniEntry(next, "Als Nächstes · " + whenText(next, now))
        }
    }
}

@Composable
private fun MiniEntry(l: Lecture, label: String) {
    val c = moduleColors(l.title)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(40.dp).background(c.accent, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Text(l.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${l.start.format(HM)}–${l.end.format(HM)}" + room(l),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
