package de.flexy.stundenplan.data

/** Gebäude der HKA mit Koordinaten (Gebäudemitte, Quelle: OpenStreetMap). */
data class Building(
    val code: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val onMainCampus: Boolean = true,
)

/** Ein aufgelöster Raum, z.B. "N-213" → Gebäude N, 2. OG. */
data class RoomInfo(
    val raw: String,
    val building: Building,
    val room: String,
    val floor: String?,
) {
    val title: String get() = raw
    val subtitle: String
        get() = listOfNotNull(building.name, floor).joinToString(" · ")
}

object Campus {
    const val LAGEPLAN_URL =
        "https://www.h-ka.de/fileadmin/Hochschule_Karlsruhe_HKA/Informationsmaterialien/HKA_Lageplan_A4.pdf"

    /** Mittelpunkt des Campus Moltkestraße. */
    const val CENTER_LAT = 49.01600
    const val CENTER_LON = 8.39080

    val buildings: Map<String, Building> = listOf(
        Building("A", "Gebäude A", 49.01580, 8.39166),
        Building("B", "Gebäude B · Architektur und Bauwesen", 49.01699, 8.39142),
        Building("E", "Gebäude E · Informatik und Wirtschaftsinformatik", 49.01505, 8.39007),
        Building("F", "Gebäude F · AStA", 49.01561, 8.39012),
        Building("K", "Gebäude K · Wirtschaftswissenschaften", 49.01324, 8.39208),
        Building("LB", "Labors Bauwesen", 49.01673, 8.39234),
        Building("LI", "Gebäude LI · Bibliothek & Hörsäle", 49.01564, 8.38937),
        Building("M", "Gebäude M · EIT & Maschinenbau", 49.01618, 8.39017),
        Building("N", "Gebäude N · Elektro- und Informationstechnik", 49.01687, 8.39032),
        Building("R", "Gebäude R · Verwaltung", 49.01502, 8.39237),
        Building("SH", "Steinbeis-Haus", 49.01742, 8.39239),
        Building("HO", "Außenstelle Hoffstraße", 49.01234, 8.38556, onMainCampus = false),
        Building("AM", "Außenstelle Amalienstraße", 49.00958, 8.38851, onMainCampus = false),
    ).associateBy { it.code }

    private val SPECIAL_ROOMS = mapOf(
        "HE" to "Hörsaal Elektrotechnik",
        "HB" to "Hörsaal Bauwesen",
    )

    /** Alle Räume einer Ortsangabe wie "N-213, N-217". Unbekanntes (z.B. "Exkursion") → leer. */
    fun parseRooms(location: String): List<RoomInfo> =
        location.split(',', ';', '/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull(::parseRoom)

    fun parseRoom(raw: String): RoomInfo? {
        val m = Regex("^([A-Za-z]{1,3})\\s*-\\s*(.+)$").find(raw.trim()) ?: return null
        val building = buildings[m.groupValues[1].uppercase()] ?: return null
        val room = m.groupValues[2].trim()
        val floor = when {
            SPECIAL_ROOMS.containsKey(room.uppercase()) -> SPECIAL_ROOMS[room.uppercase()]
            room.startsWith("U", ignoreCase = true) && room.drop(1).firstOrNull()?.isDigit() == true -> "Untergeschoss"
            room.length >= 3 && room[0].isDigit() && room[1].isDigit() -> {
                val f = room[0].digitToInt()
                if (f == 0) "Erdgeschoss" else "$f. OG"
            }
            else -> null
        }
        return RoomInfo(raw.trim(), building, room, floor)
    }
}
