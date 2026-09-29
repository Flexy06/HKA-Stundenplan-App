package de.flexy.stundenplan.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GeoPoint(val lat: Double, val lon: Double)

/** [name] ist das Gebäudekürzel (z.B. "N", "LI", "SH") – leer bei fremden/unbenannten Gebäuden. */
data class MapBuilding(val name: String, val isHka: Boolean, val rings: List<List<GeoPoint>>)

/** [kind]: "major" (Straße) oder "minor" (Fuß-/Radweg). */
data class MapWay(val kind: String, val points: List<GeoPoint>)

data class CampusMapData(val buildings: List<MapBuilding>, val ways: List<MapWay>, val green: List<List<GeoPoint>>)

/**
 * Campuskarte aus der App (assets/campus_map.txt), vorab aus OpenStreetMap extrahiert
 * (© OpenStreetMap-Mitwirkende, ODbL). Funktioniert komplett offline.
 *
 * Format: Abschnitte "#B" (Gebäude), "#G" (Grünflächen), "#R" (Straßen), "#W" (Wege).
 * Gebäudezeilen: "H:KÜRZEL|punkte" (HKA), "H|punkte" (HKA ohne Kürzel) oder "B|punkte" (sonstige).
 * Punkte: "dLat,dLon" in 1e-5 Grad; der erste Punkt relativ zu [BASE_LAT]/[BASE_LON], alle weiteren relativ zum Vorgänger.
 */
class CampusMapRepository(context: Context) {

    private val assets = context.applicationContext.assets

    @Volatile
    private var cached: CampusMapData? = null

    @Suppress("UNUSED_PARAMETER")
    suspend fun load(forceRefresh: Boolean = false): CampusMapData = withContext(Dispatchers.Default) {
        cached ?: parse(assets.open("campus_map.txt").bufferedReader().use { it.readText() }).also { cached = it }
    }

    companion object {
        private const val BASE_LAT = 49.0122
        private const val BASE_LON = 8.3875

        fun parse(text: String): CampusMapData {
            val buildings = ArrayList<MapBuilding>()
            val ways = ArrayList<MapWay>()
            val green = ArrayList<List<GeoPoint>>()
            var section = ""
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                if (line.startsWith("#")) {
                    section = line.substring(1)
                    continue
                }
                when (section) {
                    "B" -> {
                        val bar = line.indexOf('|')
                        if (bar < 0) continue
                        val head = line.substring(0, bar)
                        val isHka = head.startsWith("H")
                        val name = head.substringAfter(':', "")
                        val pts = points(line.substring(bar + 1))
                        if (pts.size >= 3) buildings += MapBuilding(name, isHka, listOf(pts))
                    }
                    "G" -> points(line).takeIf { it.size >= 3 }?.let { green += it }
                    "R" -> points(line).takeIf { it.size >= 2 }?.let { ways += MapWay("major", it) }
                    "W" -> points(line).takeIf { it.size >= 2 }?.let { ways += MapWay("minor", it) }
                }
            }
            return CampusMapData(buildings, ways, green)
        }

        private fun points(s: String): List<GeoPoint> {
            val out = ArrayList<GeoPoint>()
            var a = 0
            var b = 0
            for ((i, pair) in s.trim().split(' ').withIndex()) {
                val comma = pair.indexOf(',')
                if (comma < 0) continue
                val da = pair.substring(0, comma).toIntOrNull() ?: continue
                val db = pair.substring(comma + 1).toIntOrNull() ?: continue
                if (i == 0) { a = da; b = db } else { a += da; b += db }
                out += GeoPoint(BASE_LAT + a / 1e5, BASE_LON + b / 1e5)
            }
            return out
        }
    }
}
