# HKA Stundenplan

> Inoffizielle Open-Source-App – kein Angebot der Hochschule Karlsruhe.

Android-App (Kotlin, Jetpack Compose, Material 3 Expressive), die den Stundenplan
eines Studiensemesters aus **Raumzeit** der Hochschule Karlsruhe anzeigt.
Standard: `ELTB.1.A` – änderbar in den Einstellungen.

## Datenquelle
Keine HTML-Scraperei: Raumzeit hat eine öffentliche REST-API ohne Login.

- `GET https://raumzeit.hka-iwi.de/api/v1/timetables/public/ELTB.1.A` mit `Accept: text/calendar`
  → iCal mit jedem Einzeltermin (Ausfälle schon entfernt, Verlegungen eingerechnet)
- dieselbe URL mit `Accept: application/json` → Rohdaten; daraus liest die App die **Ausfälle**,
  um sie als „Entfällt“ anzuzeigen.

Doppelte Einträge (z. B. „Mathematik“ + „Mathematik/Höhere Mathematik 1“ zur selben Zeit
im selben Raum) werden zusammengefasst.

## Features
- Tagesansicht zum Wischen + **Wochenansicht** (Raster mit Zeitachse), Umschalten oben rechts
- „Als Nächstes“-Karte auf der Heute-Seite, laufende Veranstaltung mit Fortschrittsbalken
- Ist heute alles vorbei, startet die App direkt beim nächsten Uni-Tag („Morgen“)
- **Räume antippen** → Campuskarte (Gebäude-Umrisse aus OpenStreetMap, in der App enthalten: `assets/campus_map.txt`, komplett offline)
  mit markiertem Gebäude, Stockwerk und Navigation per Google Maps (Rad / zu Fuß)
- **Erinnerungen** 10/15/30 min vor Beginn, mit Raum und „Navigation“-Button
- **Homescreen-Widget** mit den nächsten Terminen (Material-You-Farben)
- Ausfälle durchgestrichen (abschaltbar), Module ausblenden (z. B. fremde Laborgruppen)
- Offline-Cache, Pull-to-Refresh, Auto-Refresh wenn Stand > 30 min alt
- Dynamic Color (Material You)

Gebäude-Koordinaten: `data/Campus.kt` (bei Bedarf ergänzen).

## Bauen
In Android Studio öffnen → Run auf dem Pixel. Gleiche Gradle/AGP/Kotlin-Versionen wie VolleyHub.

## Installieren
Neueste APK unter **[Releases](../../releases/latest)** herunterladen und auf dem Handy öffnen
(einmalig „Installation aus unbekannten Quellen“ erlauben).

## APK-Build über GitHub Actions
Jeder Push auf `main` startet **Actions → Build APK** und veröffentlicht das APK als Release.
Manuell: Actions → Build APK → „Run workflow“.

Signierung (optional, für update-fähige APKs) über Repository-Secrets:
`SIGNING_KEYSTORE_BASE64` (Keystore als Base64), `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
Ohne Secrets wird mit einem Debug-Schlüssel signiert. Lokal: `signing/stundenplan.jks` + `signing/signing.properties`
(beides per `.gitignore` ausgeschlossen).

## Lizenz & Daten
- Code: MIT-Lizenz, siehe [LICENSE](LICENSE)
- Stundenplandaten: öffentliche API von [Raumzeit](https://raumzeit.hka-iwi.de) der HKA
- Kartendaten: © [OpenStreetMap](https://www.openstreetmap.org/copyright)-Mitwirkende, verfügbar unter der ODbL
