package de.flexy.stundenplan.data

/**
 * Genaue Lage einzelner Räume auf dem Campus (statt nur der Gebäudemitte).
 * Positionen aus der Campuskarte abgelesen – nach und nach ergänzen.
 * Schlüssel: Gebäudekürzel + "-" + Raum, in Großbuchstaben (z.B. "N-012").
 */
object RoomSpots {
    private val spots: Map<String, GeoPoint> = mapOf(
        // Gebäude N, Erdgeschoss
        "N-002" to GeoPoint(49.016730, 8.390128), // Südwest-Ecke
        "N-012" to GeoPoint(49.016996, 8.390516), // Nordost-Ecke
        "N-013" to GeoPoint(49.016734, 8.390488), // Südost-Ecke
        // Gebäude N, 2. OG
        "N-214" to GeoPoint(49.016746, 8.390532), // Ostseite, südlicher Teil
    )

    /** Hörsaal Elektrotechnik (HE) im Südteil von Gebäude LI. */
    val HE = GeoPoint(49.014957, 8.389391)

    fun find(buildingCode: String, room: String): GeoPoint? {
        val r = room.trim().uppercase()
        if (r == "HE") return HE
        return spots["${buildingCode.uppercase()}-$r"]
    }
}
