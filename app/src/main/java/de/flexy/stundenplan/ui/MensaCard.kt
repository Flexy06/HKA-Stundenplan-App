package de.flexy.stundenplan.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.remember
import de.flexy.stundenplan.data.Diet
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.flexy.stundenplan.data.Meal
import de.flexy.stundenplan.data.MensaFavorites
import de.flexy.stundenplan.data.MensaLine
import de.flexy.stundenplan.data.MensaRepository
import de.flexy.stundenplan.system.Navigation

/** Speiseplan der Mensa Moltke als Karte in der Mittagspause. Antippen klappt alle Gerichte aus. */
@Composable
fun MensaCard(
    lines: List<MensaLine>,
    favorites: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
    onWeek: (() -> Unit)? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current

    Card(
        onClick = { expanded = !expanded },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer),
        modifier = modifier.fillMaxWidth().padding(start = 64.dp).animateContentSize(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Restaurant, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Mensa Moltke", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
            }
            Spacer(Modifier.height(8.dp))
            if (!expanded) {
                // Kurzfassung: pro Linie das Hauptgericht
                lines.take(4).forEach { line ->
                    val meal = line.meals.firstOrNull { MensaFavorites.match(it, favorites) != null } ?: line.meals.first()
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            line.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onTertiaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.width(92.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        MealText(meal, favorites, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(meal.price, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (lines.size > 4 || lines.any { it.meals.size > 1 }) {
                    Text(
                        "Alle Gerichte anzeigen",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    lines.forEach { line ->
                        Column {
                            Text(line.name.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            line.meals.forEach { meal ->
                                Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.Top) {
                                    MealText(meal, favorites, maxLines = 3, modifier = Modifier.weight(1f))
                                    Spacer(Modifier.width(8.dp))
                                    Text(meal.price, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                    Text(
                        "Studierendenpreise · Quelle: Studierendenwerk Karlsruhe",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onTertiaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    androidx.compose.material3.TextButton(
                        onClick = { Navigation.openUrl(context, MensaRepository.URL) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) { Text("Speiseplan auf sw-ka.de öffnen") }
                    if (onWeek != null) {
                        androidx.compose.material3.TextButton(
                            onClick = onWeek,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) { Text("Ganze Woche ansehen") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MealText(meal: Meal, favorites: Set<String>, maxLines: Int, modifier: Modifier = Modifier) {
    val favorite = MensaFavorites.match(meal, favorites) != null
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (favorite) {
            Icon(
                Icons.Rounded.Star,
                contentDescription = "Favorit",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.width(4.dp))
        }
        if (meal.vegan || meal.vegetarian) {
            Icon(
                Icons.Rounded.Eco,
                contentDescription = if (meal.vegan) "vegan" else "vegetarisch",
                modifier = Modifier.size(14.dp),
                tint = if (meal.vegan) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            meal.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (favorite) FontWeight.Bold else null,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val WEEK_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

/** Speiseplan aller geladenen Tage (aktuelle und nächste Woche), Favoriten markiert, optional nur Treffer. */
@Composable
fun MensaWeekSheet(
    menu: Map<LocalDate, List<MensaLine>>,
    diet: Diet,
    favorites: Set<String>,
    onDismiss: () -> Unit,
) {
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    val days = remember(menu, diet, favorites, onlyFavorites) {
        menu.toSortedMap().filterKeys { !it.isBefore(today) }.mapNotNull { (date, lines) ->
            var shown = MensaFavorites.filter(lines, diet)
            if (onlyFavorites) {
                shown = shown.mapNotNull { line ->
                    val meals = line.meals.filter { MensaFavorites.match(it, favorites) != null }
                    if (meals.isEmpty()) null else MensaLine(line.name, meals)
                }
            }
            if (shown.isEmpty()) null else date to shown
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("Mensa Moltke", style = MaterialTheme.typography.headlineSmall)
                if (favorites.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FilterChip(
                        selected = onlyFavorites,
                        onClick = { onlyFavorites = !onlyFavorites },
                        label = { Text("Nur Favoriten") },
                        leadingIcon = { Icon(Icons.Rounded.Star, null, Modifier.size(16.dp)) },
                    )
                }
            }
            if (days.isEmpty()) {
                item {
                    Text(
                        if (onlyFavorites) "Keine Favoriten auf dem Plan." else "Noch kein Speiseplan geladen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(days, key = { it.first.toEpochDay() }) { (date, lines) ->
                val rel = when (date) {
                    today -> "Heute · "
                    today.plusDays(1) -> "Morgen · "
                    else -> ""
                }
                Column {
                    Text(rel + date.format(WEEK_DAY), style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        lines.forEach { line ->
                            Column {
                                Text(
                                    line.name.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                line.meals.forEach { meal ->
                                    Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.Top) {
                                        MealText(meal, favorites, maxLines = 3, modifier = Modifier.weight(1f))
                                        Spacer(Modifier.width(8.dp))
                                        Text(meal.price, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
