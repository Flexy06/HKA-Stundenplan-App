package de.flexy.stundenplan.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import de.flexy.stundenplan.data.Campus
import de.flexy.stundenplan.data.RoomInfo
import de.flexy.stundenplan.system.Navigation
import de.flexy.stundenplan.system.Reminders
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.flexy.stundenplan.data.Lecture
import de.flexy.stundenplan.ui.theme.moduleColors
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_LONG: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)
private val DATE_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("EE, dd.MM.", Locale.GERMAN)
private val UPDATED: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM. 'um' HH:mm", Locale.GERMAN)

@Composable
fun LectureCard(
    lecture: Lecture,
    now: LocalDateTime,
    onClick: () -> Unit,
    onRoomClick: (RoomInfo) -> Unit,
) {
    val colors = moduleColors(lecture.title)
    val cs = MaterialTheme.colorScheme
    val running = !lecture.cancelled && !now.isBefore(lecture.start) && now.isBefore(lecture.end)
    val past = !lecture.cancelled && !now.isBefore(lecture.end)

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // Zeitspalte
        Column(
            Modifier.width(52.dp).fillMaxHeight().padding(top = 14.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                lecture.start.format(TIME),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (running) cs.primary else cs.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                lecture.end.format(TIME),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))

        val container = when {
            lecture.cancelled -> cs.surfaceContainerLow
            else -> colors.container
        }
        val content = when {
            lecture.cancelled -> cs.onSurfaceVariant
            else -> colors.onContainer
        }
        Card(
            onClick = onClick,
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
            modifier = Modifier
                .weight(1f)
                .then(if (past) Modifier.alpha(0.55f) else Modifier),
        ) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(
                    Modifier
                        .padding(start = 10.dp, top = 14.dp, bottom = 14.dp)
                        .width(5.dp)
                        .fillMaxHeight()
                        .background(
                            if (lecture.cancelled) cs.error else colors.accent,
                            RoundedCornerShape(50),
                        )
                )
                Column(Modifier.padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
                    if (running) {
                        StatusChip(
                            "Läuft · noch ${formatDuration(Duration.between(now, lecture.end).toMinutes() + 1)}",
                            cs.primary, cs.onPrimary,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (lecture.cancelled) {
                        StatusChip("Entfällt", cs.errorContainer, cs.onErrorContainer, Icons.Rounded.EventBusy)
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        lecture.title,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (lecture.cancelled) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (lecture.location.isNotBlank()) {
                            RoomChip(
                                location = lecture.location,
                                color = content,
                                onRoomClick = onRoomClick,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        if (lecture.code.isNotBlank() && lecture.code != lecture.title) {
                            Text(
                                lecture.code,
                                style = MaterialTheme.typography.labelMedium,
                                color = content.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
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
private fun StatusChip(
    text: String,
    bg: androidx.compose.ui.graphics.Color,
    fg: androidx.compose.ui.graphics.Color,
    icon: ImageVector? = null,
) {
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

/* ---------------------------------------------------------------------------------------------- */

/** Raum-Angabe als Chip; antippen öffnet die Campuskarte (nur bei erkannten Räumen). */
@Composable
fun RoomChip(
    location: String,
    color: androidx.compose.ui.graphics.Color,
    onRoomClick: (RoomInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val room = remember(location) { Campus.parseRooms(location).firstOrNull() }
    if (room == null) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Place, null, Modifier.size(16.dp), tint = color)
            Spacer(Modifier.width(4.dp))
            Text(location, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        Surface(
            onClick = { onRoomClick(room) },
            shape = RoundedCornerShape(50),
            color = color.copy(alpha = 0.12f),
            contentColor = color,
            modifier = modifier,
        ) {
            Row(
                Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Place, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(location, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** "Als Nächstes"-Karte auf der Heute-Seite. */
@Composable
fun NextUpCard(lecture: Lecture, now: LocalDateTime, onClick: () -> Unit, onRoomClick: (RoomInfo) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val minutes = Duration.between(now, lecture.start).toMinutes() + 1
    val sameDay = lecture.date == now.toLocalDate()
    val whenText = when {
        sameDay -> "in ${formatDuration(minutes)}"
        lecture.date == now.toLocalDate().plusDays(1) -> "morgen um ${lecture.start.format(TIME)}"
        else -> lecture.start.format(DATE_SHORT) + " " + lecture.start.format(TIME)
    }
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                if (sameDay) "Als Nächstes · $whenText" else "Nächster Termin · $whenText",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(lecture.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${lecture.start.format(TIME)}–${lecture.end.format(TIME)}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.width(12.dp))
                if (lecture.location.isNotBlank()) {
                    RoomChip(lecture.location, cs.onPrimaryContainer, onRoomClick)
                }
            }
        }
    }
}

@Composable
fun LectureSheet(
    lecture: Lecture,
    upcoming: List<Lecture>,
    onRoomClick: (RoomInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = moduleColors(lecture.title)
    val context = LocalContext.current
    val rooms = remember(lecture.location) { Campus.parseRooms(lecture.location) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Box(
                    Modifier
                        .size(width = 40.dp, height = 6.dp)
                        .background(colors.accent, RoundedCornerShape(50))
                )
                Spacer(Modifier.height(12.dp))
                Text(lecture.title, style = MaterialTheme.typography.headlineSmall)
                if (lecture.code.isNotBlank()) {
                    Text(
                        lecture.code,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (lecture.cancelled) {
                    Spacer(Modifier.height(8.dp))
                    StatusChip(
                        if (lecture.note.isNotBlank()) "Entfällt · ${lecture.note}" else "Entfällt",
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer,
                        Icons.Rounded.EventBusy,
                    )
                }
            }
            item {
                InfoRow(Icons.Rounded.Schedule, lecture.start.format(DATE_LONG),
                    "${lecture.start.format(TIME)} – ${lecture.end.format(TIME)} Uhr (${formatDuration(lecture.duration.toMinutes())})")
                if (rooms.isEmpty() && lecture.location.isNotBlank()) {
                    InfoRow(Icons.Rounded.Place, lecture.location, "Ort")
                }
                for (room in rooms) {
                    InfoRow(
                        Icons.Rounded.Place, "Raum ${room.title}", room.subtitle,
                        onClick = { onRoomClick(room) },
                        trailing = { Icon(Icons.Rounded.Map, contentDescription = "Auf Campuskarte zeigen") },
                    )
                }
            }
            rooms.firstOrNull()?.let { room ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { onRoomClick(room) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Map, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Campuskarte")
                        }
                        FilledTonalButton(
                            onClick = { Navigation.start(context, room.building, Navigation.Mode.BIKE) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Navigation, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Navigation")
                        }
                    }
                }
            }
            if (upcoming.isNotEmpty()) {
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Nächste Termine", style = MaterialTheme.typography.titleMedium)
                }
                items(upcoming) { u ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(u.start.format(DATE_SHORT), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
                        Text(
                            "${u.start.format(TIME)}–${u.end.format(TIME)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(96.dp),
                        )
                        Text(
                            u.location,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    headline: String,
    supporting: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = { Text(supporting) },
        leadingContent = { Icon(icon, null) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = if (onClick != null) Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick) else Modifier,
    )
}

/* ---------------------------------------------------------------------------------------------- */

@Composable
fun SettingsSheet(state: UiState, vm: TimetableViewModel, onDismiss: () -> Unit) {
    var semester by remember { mutableStateOf(state.semester) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        ) {
            item {
                Text("Einstellungen", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = semester,
                        onValueChange = { semester = it },
                        label = { Text("Studiensemester") },
                        placeholder = { Text("z.B. ELTB.1.A") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { vm.setSemester(semester) }),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = { vm.setSemester(semester) },
                        enabled = semester.trim().uppercase() != state.semester && semester.isNotBlank(),
                    ) { Text("Laden") }
                }
                Spacer(Modifier.height(8.dp))
                SwitchRow("Ausfälle anzeigen", "Abgesagte Termine durchgestrichen zeigen", state.showCancelled) {
                    vm.setShowCancelled(it)
                }
                ReminderSettings(state.reminderMinutes, vm::setReminderMinutes)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Module", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Blende aus, was dich nicht betrifft (z.B. andere Laborgruppen).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }
            items(state.modules) { title ->
                val c = moduleColors(title)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(12.dp).background(c.accent, RoundedCornerShape(50)))
                    Spacer(Modifier.width(12.dp))
                    Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(
                        checked = title !in state.hidden,
                        onCheckedChange = { vm.setModuleVisible(title, it) },
                    )
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                val updated = if (state.lastUpdated > 0) {
                    java.time.Instant.ofEpochMilli(state.lastUpdated)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(UPDATED)
                } else "noch nie"
                Text(
                    "Zuletzt aktualisiert: $updated\nQuelle: raumzeit.hka-iwi.de (öffentliche API)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
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

@Composable
private fun ReminderSettings(current: Int, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onChange(pending)
    }
    fun choose(minutes: Int) {
        val needsPermission = minutes > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pending = minutes
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onChange(minutes)
        }
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Erinnerung vor Vorlesungen", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Benachrichtigung mit Raum und Navigations-Button",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (m in listOf(0, 10, 15, 30)) {
                FilterChip(
                    selected = current == m,
                    onClick = { choose(m) },
                    label = { Text(if (m == 0) "Aus" else "$m min") },
                )
            }
        }
        if (current > 0 && !Reminders.canScheduleExact(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Für minutengenaue Erinnerungen „Wecker & Erinnerungen“ erlauben.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))
                    )
                }
            }) { Text("Erlauben") }
        }
    }
}
