package de.flexy.stundenplan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.flexy.stundenplan.data.Campus
import de.flexy.stundenplan.data.CampusMapData
import de.flexy.stundenplan.data.GeoPoint
import de.flexy.stundenplan.data.MapBuilding
import de.flexy.stundenplan.data.RoomInfo
import de.flexy.stundenplan.system.Navigation
import kotlin.math.cos

/** Vollbild-Campuskarte mit markiertem Zielgebäude und Navigations-Buttons. */
@Composable
fun CampusMapScreen(
    room: RoomInfo,
    lectureTitle: String?,
    map: CampusMapData?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val building = room.building

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Raum ${room.title}", style = MaterialTheme.typography.titleLarge)
                        if (lectureTitle != null) {
                            Text(
                                lectureTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when {
                        map != null && building.onMainCampus -> CampusCanvas(map, building.code, building.lat, building.lon, room.spot)
                        map != null -> OffCampusHint(room)
                        loading -> Box(contentAlignment = Alignment.Center) {
                            ContainedLoadingIndicator(Modifier.size(64.dp))
                        }
                        else -> Column(
                            Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(error ?: "Karte nicht verfügbar", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Die Kartendaten sind in der App enthalten – bitte App neu installieren, falls das bestehen bleibt.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            FilledTonalButton(onClick = onRetry) { Text("Erneut versuchen") }
                        }
                    }
                }
            }

            // Info + Aktionen
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(building.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull("Raum ${room.room}", room.floor).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { Navigation.start(context, room, Navigation.Mode.BIKE) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.DirectionsBike, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Mit dem Rad")
                    }
                    FilledTonalButton(
                        onClick = { Navigation.start(context, room, Navigation.Mode.WALK) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Zu Fuß")
                    }
                }
                TextButton(
                    onClick = { Navigation.openUrl(context, Campus.LAGEPLAN_URL) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Offizieller Lageplan (PDF)")
                }
            }
        }
    }
}

@Composable
private fun OffCampusHint(room: RoomInfo) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.MyLocation, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(room.building.name, style = MaterialTheme.typography.titleMedium)
        Text(
            "liegt außerhalb des Campus Moltkestraße – starte einfach die Navigation.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/* ---------------------------------------------------------------------------------------------- */

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
private fun CampusCanvas(map: CampusMapData, targetCode: String, targetLat: Double, targetLon: Double, spot: GeoPoint? = null) {
    val cs = MaterialTheme.colorScheme
    val proj = remember { Projection(Campus.CENTER_LAT, Campus.CENTER_LON) }
    val measurer = rememberTextMeasurer()

    val target = remember(map, targetCode) { map.buildings.firstOrNull { it.isHka && it.name == targetCode } }
    val targetCenter = remember(target, spot) { spot ?: target?.let(::centroid) ?: GeoPoint(targetLat, targetLon) }

    var size by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(0f) } // px pro Meter; 0 = noch nicht initialisiert
    var offset by remember { mutableStateOf(Offset.Zero) }

    fun focusTarget() {
        if (size.width == 0) return
        // ca. 260 m Breite sichtbar, Ziel mittig
        scale = size.width / (if (spot != null) 160f else 260f)
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
