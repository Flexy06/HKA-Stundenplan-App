package de.flexy.stundenplan.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
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
import de.flexy.stundenplan.data.MensaLine
import de.flexy.stundenplan.data.MensaRepository
import de.flexy.stundenplan.system.Navigation

/** Speiseplan der Mensa Moltke als Karte in der Mittagspause. Antippen klappt alle Gerichte aus. */
@Composable
fun MensaCard(lines: List<MensaLine>, modifier: Modifier = Modifier) {
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
                    val meal = line.meals.first()
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            line.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onTertiaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.width(92.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        MealText(meal, maxLines = 1, modifier = Modifier.weight(1f))
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
                                    MealText(meal, maxLines = 3, modifier = Modifier.weight(1f))
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
                }
            }
        }
    }
}

@Composable
private fun MealText(meal: Meal, maxLines: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (meal.vegan || meal.vegetarian) {
            Icon(
                Icons.Rounded.Eco,
                contentDescription = if (meal.vegan) "vegan" else "vegetarisch",
                modifier = Modifier.size(14.dp),
                tint = if (meal.vegan) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(meal.name, style = MaterialTheme.typography.bodyMedium, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}
