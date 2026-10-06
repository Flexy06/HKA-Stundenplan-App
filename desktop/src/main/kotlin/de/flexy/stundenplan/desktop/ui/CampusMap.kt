package de.flexy.stundenplan.desktop.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.flexy.stundenplan.data.Building
import de.flexy.stundenplan.data.Campus
import de.flexy.stundenplan.data.CampusMapData
import de.flexy.stundenplan.data.GeoPoint
import de.flexy.stundenplan.data.MapBuilding
import de.flexy.stundenplan.data.RoomInfo
import kotlin.math.cos

private fun mapsRoute(b: Building, mode: String) =
    "https://www.google.com/maps/dir/?api=1&destination=${b.lat},${b.lon}&travelmode=$mode"

/** Campuskarte als Overlay über dem Fenster (Esc/Klick daneben schließt). */
@Composable
fun CampusMapOverlay(room: RoomInfo, lectureTitle: String?, map: CampusMapData, onClose: () -> Unit) {
    val building = room.building
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxSize().padding(40.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        ) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Raum ${room.title}", style = MaterialTheme.typography.titleLarge)
                        Text(
                            listOfNotNull(lectureTitle, building.name, room.floor).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Schließen") }
                }
                Spacer(Modifier.size(12.dp))
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    if (building.onMainCampus) {
                        CampusCanvas(map, building.code, building.lat, building.lon)
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${building.name} liegt außerhalb des Campus Moltkestraße – nutze die Route.",
                                style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Spacer(Modifier.size(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { openUrl(mapsRoute(building, "bicycling")) }) {
                        Icon(Icons.AutoMirrored.Rounded.DirectionsBike, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Route mit dem Rad")
                    }
                    FilledTonalButton(onClick = { openUrl(mapsRoute(building, "walking")) }) {
                        Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Zu Fuß")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { openUrl(Campus.LAGEPLAN_URL) }) {
                        Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Offizieller Lageplan (PDF)")
                    }
                }
            }
        }
    }
}

/** Einfache Projektion: Längen-/Breitengrad → Meter relativ zum Campusmittelpunkt. */
private class Projection(private val lat0: Double, private val lon0: Double) {
    private val mPerLat = 111_132.0
    private val mPerLon = 111_320.0 * cos(Math.toRadians(lat0))
    fun x(p: GeoPoint) = ((p.lon - lon0) * mPerLon).toFloat()
    fun y(p: GeoPoint) = (-(p.lat - lat0) * mPerLat).toFloat()
    fun x(lon: Double) = ((lon - lon0) * mPerLon).toFloat()
    fun y(lat: Double) = (-(lat - lat0) * mPerLat).toFloat()
}

private fun centroid(b: MapBuilding): GeoPoint? {
    val pts = b.rings.flatten()
    if (pts.isEmpty()) return null
    return GeoPoint(pts.sumOf { it.lat } / pts.size, pts.sumOf { it.lon } / pts.size)
}

private fun label(b: MapBuilding): String? = b.name.takeIf { b.isHka && it.isNotEmpty() }

@Composable
internal fun CampusCanvas(map: CampusMapData, targetCode: String, targetLat: Double, targetLon: Double) {
    val cs = MaterialTheme.colorScheme
    val proj = remember { Projection(Campus.CENTER_LAT, Campus.CENTER_LON) }
    val measurer = rememberTextMeasurer()

    val target = remember(map, targetCode) { map.buildings.firstOrNull { it.isHka && it.name == targetCode } }
    val targetCenter = remember(target) { target?.let(::centroid) ?: GeoPoint(targetLat, targetLon) }

    var size by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(0f) } // px pro Meter; 0 = noch nicht initialisiert
    var offset by remember { mutableStateOf(Offset.Zero) }

    fun focusTarget() {
        if (size.width == 0) return
        // ca. 260 m Breite sichtbar, Ziel mittig
        scale = size.width / 260f
        offset = Offset(
            size.width / 2f - proj.x(targetCenter) * scale,
            size.height / 2f - proj.y(targetCenter) * scale,
        )
    }
    LaunchedEffect(size, targetCenter) { if (scale == 0f) focusTarget() }

    val pulse by rememberInfiniteTransition(label = "pin").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
        label = "pulse",
    )

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.4f, 40f)
                        val factor = newScale / scale
                        offset = (offset - centroid) * factor + centroid + pan
                        scale = newScale
                    }
                }
                .pointerInput(Unit) {
                    // Mausrad: zoomen um den Mauszeiger
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val change = event.changes.first()
                                val dy = change.scrollDelta.y
                                if (dy != 0f && scale > 0f) {
                                    val newScale = (scale * if (dy < 0) 1.15f else 1f / 1.15f).coerceIn(0.4f, 40f)
                                    val factor = newScale / scale
                                    offset = (offset - change.position) * factor + change.position
                                    scale = newScale
                                    change.consume()
                                }
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { tap ->
                        val newScale = (scale * 2f).coerceAtMost(40f)
                        val factor = newScale / scale
                        offset = (offset - tap) * factor + tap
                        scale = newScale
                    })
                }
        ) {
            if (scale == 0f) return@Canvas
            fun pt(p: GeoPoint) = Offset(proj.x(p) * scale + offset.x, proj.y(p) * scale + offset.y)
            fun path(points: List<GeoPoint>, close: Boolean): Path = Path().apply {
                points.forEachIndexed { i, p -> val o = pt(p); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
                if (close) close()
            }

            // Grünflächen
            for (g in map.green) drawPath(path(g, true), color = cs.tertiaryContainer.copy(alpha = 0.45f))

            // Wege: erst breite Straßen, dann Fußwege
            for (w in map.ways) {
                val isMajor = w.kind == "major"
                drawPath(
                    path(w.points, false),
                    color = if (isMajor) cs.surfaceContainerHighest else cs.outlineVariant.copy(alpha = 0.7f),
                    style = Stroke(
                        width = (if (isMajor) 9f else 2.2f) * scale,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }

            // Gebäude
            for (b in map.buildings) {
                val isTarget = b === target
                val fill = when {
                    isTarget -> cs.primary
                    b.isHka -> cs.secondaryContainer
                    else -> cs.surfaceVariant
                }
                for (ring in b.rings) {
                    val p = path(ring, true)
                    drawPath(p, color = fill)
                    if (b.isHka) {
                        drawPath(p, color = if (isTarget) cs.primary else cs.outline.copy(alpha = 0.5f), style = Stroke(width = 1.2f.dp.toPx()))
                    }
                }
            }

            // Beschriftungen
            for (b in map.buildings) {
                val text = label(b) ?: continue
                val c = centroid(b) ?: continue
                val isTarget = b === target
                drawLabel(
                    measurer = measurer,
                    text = text,
                    at = pt(c),
                    color = if (isTarget) cs.onPrimary else cs.onSecondaryContainer,
                    size = if (isTarget) 18.sp else 14.sp,
                )
            }

            // Pin am Ziel
            val center = pt(targetCenter)
            val pinY = center.y - 34.dp.toPx()
            drawCircle(cs.primary.copy(alpha = (1f - pulse) * 0.35f), radius = (10 + 26 * pulse).dp.toPx(), center = center)
            drawLine(cs.primary, Offset(center.x, pinY), center, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(cs.primary, radius = 13.dp.toPx(), center = Offset(center.x, pinY))
            drawCircle(cs.onPrimary, radius = 5.dp.toPx(), center = Offset(center.x, pinY))
        }

        // Zurück zum Ziel
        FilledTonalIconButton(
            onClick = { focusTarget() },
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
        ) {
            Icon(Icons.Rounded.MyLocation, contentDescription = "Zum Gebäude")
        }
        Text(
            "© OpenStreetMap-Mitwirkende",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

private fun DrawScope.drawLabel(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    at: Offset,
    color: Color,
    size: androidx.compose.ui.unit.TextUnit,
) {
    val layout = measurer.measure(text, TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Bold))
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
}
