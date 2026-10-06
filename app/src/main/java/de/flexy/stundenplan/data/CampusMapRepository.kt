package de.flexy.stundenplan.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        cached ?: CampusMapParser.parse(assets.open("campus_map.txt").bufferedReader().use { it.readText() }).also { cached = it }
    }

}
