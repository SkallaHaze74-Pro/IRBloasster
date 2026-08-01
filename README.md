# SmartIR – Living Room Controller

SmartIR ist eine eigenständig entwickelte Android-Fernbedienung für den **LG OLED55B19LA.DEUQJP** und den **Sony STR-DB870 CEL**. Sie verbindet lokale LG-webOS-Steuerung mit dem integrierten IR-Blaster des Xiaomi 15T Pro. Ein LG-Cloudkonto ist für die lokale TV-Steuerung nicht erforderlich.

Der Paketname bleibt dauerhaft:

```text
com.skallahaze.irbloasster
```

Dadurch können spätere Builds mit derselben Signatur als Update installiert werden.

## Aktueller Stand: 1.1.6

- Material-3-Oberfläche mit Hell-/Dunkelmodus und eigenem SmartIR-App-Icon
- große sofa- und einhandtaugliche Tasten
- LG-TV-Fernbedienung über NEC 32 Bit bei 38 kHz
- lokale webOS-Kopplung über WebSocket/SSAP
- bevorzugt sichere Verbindung über WSS-Port 3001
- lokaler WS-Port 3000 als Kompatibilitäts-Fallback
- SSDP-Suche nach webOS-Fernsehern im Heimnetz
- Android-Keystore-verschlüsselter TV-Client-Key
- Trust-on-first-use-Zertifikat-Fingerabdruck für den eigenen TV
- automatische Wiederverbindung und Wake-on-LAN
- Live-Status für Lautstärke, Mute, Power und aktive App
- Apps und externe Eingänge abrufen, starten beziehungsweise umschalten
- Magic-Remote-Pointer, D-Pad, Touchpad, Klick und Scrollen
- TV-Texteingabe und Enter
- Android-IR-Berechtigung `TRANSMIT_IR` und Xiaomi-kompatibler Systemdienst-Fallback
- exakt bestätigter LG-Produktcode **OLED55B19LA.DEUQJP**
- sichtbares LG-Geräteprofil mit Fertigung 09/2021, Anschlussplan und Leistungsdaten
- direkte webOS-Tasten für HDMI 1–4
- HDMI 3 als eARC/ARC- und 4K/120-Port gekennzeichnet
- HDMI 4 als 4K/120-Port gekennzeichnet
- dynamische Liste aller vom TV gemeldeten Eingänge
- dynamische Schnellstartliste installierter TV-Apps
- Status-, App- und Eingangsaktualisierung direkt auf der TV-Seite
- exakt bestätigtes Sony-Modell **STR-DB870**, Area Code **CEL**
- für CEL bestätigte Originalfernbedienung **RM-U305A**
- normaler Sony-Betrieb fest auf **AV1**
- Sony-SIRC-Profil für 12/15 Bit bei 40 kHz und drei Wiederholungsframes
- Szenen: Fernsehen, Heimkino, Musik und Alles aus
- Diagnose-Labor für eigene NEC-, SIRC- und SSAP-Tests
- konkretere IR-Systemfehler statt einer pauschalen Fehlermeldung
- **portabler JSON-Export und -Import für App-Einstellungen**
- automatische Migration älterer webOS-Pairingwerte in einen getrennten sicheren Speicher
- Android-Cloud-/Geräteübertragung nur für nicht geheime Einstellungen
- Unit-Tests für Backupformat, LG-Profil, NEC, SIRC und Sony-Profil
- Android-Lint und automatischer APK-Build
- SHA-256-Prüfsumme neben jeder erzeugten Test-APK

## Datensicherung und Update-Schutz

Unter **Setup → Backup & Datenübertragung** kann SmartIR eine portable JSON-Datei exportieren und später wieder importieren. Gesichert werden unter anderem:

- TV-IP beziehungsweise Hostname
- TV-MAC-Adresse
- Theme
- Tastenvibration
- Auto-Connect
- Sony-Modus und dessen Migration
- bekannter TV-Zertifikat-Fingerabdruck

Der geheime webOS-Client-Key bleibt absichtlich im Android-Keystore und wird nicht als Klartextdatei exportiert. Nach einer vollständigen Deinstallation muss der LG-TV daher einmal neu gekoppelt werden; die übrigen Einstellungen können direkt wiederhergestellt werden.

SmartIR trennt:

```text
smart_ir_settings.xml
smart_ir_secure.xml
```

Android Auto Backup und die direkte Geräteübertragung dürfen nur die nicht geheime Einstellungsdatei sichern. Ausführliche Hinweise stehen unter [`docs/DATA_BACKUP_AND_MIGRATION.md`](docs/DATA_BACKUP_AND_MIGRATION.md).

## LG OLED55B19LA.DEUQJP

Das Typenschildfoto bestätigt die konkrete TV-Variante:

```text
Modell: OLED55B19LA
Produktcode: OLED55B19LA.DEUQJP
Serie: B1
Fertigung: 09/2021
Montage: Polen
Netzversorgung: AC 100–240 V, 50/60 Hz
Nenn-/Maximalangabe: 343 W
Typische Leistungsaufnahme: 104 W
```

Die Anschlussbeschriftung bestätigt:

```text
HDMI 3: eARC/ARC und bis 4K/120
HDMI 4: bis 4K/120
```

LG dokumentiert für den OLED55B19LA 2 HDMI-2.1- und 2 HDMI-2.0-Ports, webOS 6.0, 120 Hz, VRR, ALLM, G-Sync, FreeSync, HGiG und Wi-Fi TV On.

### Neue LG-Funktionen

- Gerätekarte mit Produktcode, Fertigung, Display-, HDMI- und Leistungsdaten
- direkte Tasten für HDMI 1, HDMI 2, HDMI 3 eARC und HDMI 4 4K/120
- zusätzlich die echte, vom Fernseher gemeldete Eingangsliste
- installierte Apps dynamisch laden und starten
- TV-Status, Apps und Eingänge manuell neu laden
- Unit-Tests für Produktcode, eARC-Port und HDMI-2.1-/120-Hz-Zuordnung

Das vollständige Geräteprofil liegt unter [`docs/LG_OLED55B19LA_DEVICE_PROFILE.md`](docs/LG_OLED55B19LA_DEVICE_PROFILE.md).

**Die sichtbare LG-Seriennummer wird weder in der App noch im öffentlichen Repository gespeichert.** Sie ist für IR- und webOS-Steuerung nicht erforderlich.

## Sony STR-DB870 CEL

Die Gerätefotos bestätigen:

```text
Modell: STR-DB870
Area Code: CEL
Rückseitenkennung: 4-233-630-21 CEL
Netzversorgung: 230 V AC, 50/60 Hz
Leistungsaufnahme: 230 W
Originalfernbedienung laut Sony: RM-U305A
```

Sonys Bedienungsanleitung ordnet dem STR-DB870 mit Area Code CEL die RM-U305A zu. SmartIR sendet die festen Sony-Tasten und Szenen im normalen AV1-Profil. AV2 bleibt ausschließlich im freien Protokoll-Labor für kontrollierte SIRC-Diagnose verfügbar.

Die sichtbare Sony-Seriennummer wird weder in der App noch im öffentlichen Repository gespeichert.

Die vollständige Tabelle mit Command, Adresse, Bitlänge, Quellenlage und Testreihenfolge steht unter [`docs/SONY_STR_DB870_CODES.md`](docs/SONY_STR_DB870_CODES.md).

## Installation über GitHub Actions

Unter **Actions → SmartIR Android APK** den neuesten erfolgreichen Lauf auf dem Branch **main** öffnen und das Artefakt **SmartIR-v1.1.6-debug** laden. Es enthält:

```text
SmartIR-v1.1.6-debug.apk
SmartIR-v1.1.6-debug.apk.sha256
```

## Bauen in Termux

```bash
pkg install openjdk-17 git
git clone https://github.com/SkallaHaze74-Pro/IRBloasster.git
cd IRBloasster
chmod +x gradlew
./gradlew --no-daemon --stacktrace clean testDebugUnitTest lintDebug assembleDebug
```

Die APK liegt danach unter:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## LG TV verbinden

1. TV und Smartphone mit demselben lokalen Netzwerk verbinden.
2. In SmartIR **Setup** öffnen.
3. **TV suchen** drücken oder die TV-IP manuell eintragen.
4. **Verbinden** drücken.
5. Die Kopplungsabfrage am Fernseher bestätigen.
6. Für Wake-on-LAN zusätzlich die MAC-Adresse speichern.
7. Vor einer nötigen Deinstallation unter **Backup & Datenübertragung** exportieren.

Der vom eigenen TV ausgegebene Client-Key wird lokal verschlüsselt gespeichert. Ändert sich der gespeicherte TV-Zertifikat-Fingerabdruck, blockiert SmartIR die sichere Verbindung, bis die Kopplung bewusst zurückgesetzt wird.

## Dokumentation

- [`docs/DEVICE_INVENTORY.md`](docs/DEVICE_INVENTORY.md) – bereinigtes Geräte- und Quelleninventar
- [`docs/LG_OLED55B19LA_DEVICE_PROFILE.md`](docs/LG_OLED55B19LA_DEVICE_PROFILE.md) – exaktes TV- und Anschlussprofil
- [`docs/DATA_BACKUP_AND_MIGRATION.md`](docs/DATA_BACKUP_AND_MIGRATION.md) – Backup, Migration und Sicherheitsgrenzen
- [`docs/SONY_STR_DB870_CODES.md`](docs/SONY_STR_DB870_CODES.md) – Sony-SIRC-Profil
- [`docs/HARDWARE_TEST_CHECKLIST.md`](docs/HARDWARE_TEST_CHECKLIST.md) – praktische Prüfliste
- [`docs/LG_THINQ_ANALYSIS.md`](docs/LG_THINQ_ANALYSIS.md) – bereinigte ThinQ-Interoperabilitätsanalyse

## Signatur und Updates

GitHub-Actions-Debug-APKs können mit unterschiedlichen Debug-Signaturen gebaut werden. Für garantiert installierbare Updates ohne Deinstallation muss später ein dauerhaftes Release-Keystore sicher als GitHub-Secret eingerichtet werden. Vor jeder notwendigen Deinstallation sollte das portable SmartIR-Backup exportiert werden.

## Datenschutz und Herkunft

Das Repository enthält ausschließlich eigenständig geschriebenen Quellcode und beschreibende Interoperabilitätsnotizen. Es werden keine LG- oder Sony-APK-Dateien, Kontodaten, Cloud-Schlüssel, Zertifikate, proprietären Schriftdateien, Bilder, Seriennummern oder vollständigen UEI-Codeset-Datenbanken veröffentlicht.
