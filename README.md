# SmartIR – Living Room Controller

SmartIR ist eine eigenständig entwickelte Android-Fernbedienung für den **LG OLED55B19LA.DEUQJP**, den **Sony STR-DB870 CEL** und den daran angeschlossenen **JBL Simply Cinema SUB125**. Sie verbindet lokale LG-webOS-Steuerung mit dem integrierten IR-Blaster des Xiaomi 15T Pro.

Der Paketname bleibt dauerhaft:

```text
com.skallahaze.irbloasster
```

## Aktueller Stand: 1.1.9

### LG OLED55B19LA.DEUQJP

- LG-NEC-IR-Fernbedienung bei 38 kHz
- lokale webOS-Kopplung über WSS 3001, mit WS 3000 als Fallback
- SSDP-TV-Suche, sichere Client-Key-Speicherung und Zertifikat-Fingerabdruck
- Wake-on-LAN, Auto-Reconnect, Lautstärke-, Mute-, Power- und App-Status
- Magic-Remote-Touchpad, D-Pad, Pointer, Klick, Scrollen und TV-Tastatur
- direkte HDMI-1- bis HDMI-4-Auswahl
- HDMI 3 als eARC/ARC und 4K/120, HDMI 4 als 4K/120
- installierte Apps und echte TV-Eingänge dynamisch laden
- Schnellstarts für Live TV, YouTube, Netflix und **Twitch**
- Twitch-App-ID wird bevorzugt direkt aus der TV-App-Liste übernommen

### Sony STR-DB870 CEL

- Sony-SIRC bei 40 kHz, AV1 als normaler CEL-Betrieb
- Power, Lautstärke, Mute, Eingänge und Receiver-Menü
- A.F.D., 2CH/OFF, Klangfelder, Test Tone und Subwoofer-Kandidaten
- Tunerfunktionen und getrennte Legacy-/Alternativcodes
- Originalfernbedienung der CEL-Variante: RM-U305A

### JBL Simply Cinema SUB125

Der SUB125 besitzt keinen eigenen IR-Empfänger. SmartIR zeigt deshalb auf der Sony-Seite eine eigene Karte **„JBL SUB125 · indirekt über Sony“** mit:

- Subwoofer + / −
- Test Tone
- A.F.D.
- 2CH / OFF
- technischem Geräteprofil
- Auto/On-Hinweisen
- Sicherheits- und Klickdiagnose

Das vollständige Profil liegt unter [`docs/JBL_SUB125_PROFILE.md`](docs/JBL_SUB125_PROFILE.md).

### Backup und Datenschutz

Unter **Setup → Backup & Datenübertragung** kann SmartIR eine portable JSON-Datei exportieren und importieren. Übertragen werden TV-IP/Hostname, TV-MAC, Theme, Haptik, Auto-Connect und weitere nicht geheime Einstellungen.

Der geheime webOS-Client-Key bleibt im Android-Keystore und wird nicht in das portable Backup geschrieben. Seriennummern werden weder in der App noch im öffentlichen Repository gespeichert.

## Installation über GitHub Actions

Unter **Actions → SmartIR Android APK** den neuesten erfolgreichen Lauf auf dem Branch **main** öffnen und das Artefakt laden:

```text
SmartIR-v1.1.9-debug.apk
SmartIR-v1.1.9-debug.apk.sha256
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
7. Vor einer nötigen Deinstallation ein Backup exportieren.

## JBL-Klickgeräusch

Ein einzelnes Klacken unmittelbar beim Einschalten oder Auto-Wake kann vom internen Schaltvorgang kommen. Wiederholtes Klackern, LED-Flackern, Tonaussetzer, verbrannter Geruch oder starke Wärme sind nicht normal. In diesem Fall ausschalten, Netzstecker ziehen und nicht geöffnet weiterbetreiben.

## Dokumentation

- [`docs/DEVICE_INVENTORY.md`](docs/DEVICE_INVENTORY.md)
- [`docs/LG_OLED55B19LA_DEVICE_PROFILE.md`](docs/LG_OLED55B19LA_DEVICE_PROFILE.md)
- [`docs/SONY_STR_DB870_CODES.md`](docs/SONY_STR_DB870_CODES.md)
- [`docs/JBL_SUB125_PROFILE.md`](docs/JBL_SUB125_PROFILE.md)
- [`docs/DATA_BACKUP_AND_MIGRATION.md`](docs/DATA_BACKUP_AND_MIGRATION.md)
- [`docs/HARDWARE_TEST_CHECKLIST.md`](docs/HARDWARE_TEST_CHECKLIST.md)
- [`docs/LG_THINQ_ANALYSIS.md`](docs/LG_THINQ_ANALYSIS.md)

## Signatur und Updates

GitHub-Actions-Debug-APKs können unterschiedliche Debug-Signaturen besitzen. Für garantiert installierbare Updates ohne Deinstallation wird später ein dauerhaftes Release-Keystore benötigt. Der Paketname bleibt bereits unverändert.

## Herkunft

Das Repository enthält ausschließlich eigenständig geschriebenen Quellcode und beschreibende Interoperabilitätsnotizen. Es werden keine Original-APKs, Kontodaten, Cloud-Schlüssel, Zertifikate, proprietären Grafiken, Seriennummern oder vollständigen Codeset-Datenbanken veröffentlicht.
