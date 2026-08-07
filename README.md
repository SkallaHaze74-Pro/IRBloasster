# SmartIR – Living Room Controller

SmartIR ist eine eigenständig entwickelte Android-Fernbedienung für den **LG OLED55B19LA.DEUQJP**, den **Sony STR-DB870 CEL** und den daran angeschlossenen **JBL Simply Cinema SUB125**. Sie verbindet lokale LG-webOS-Steuerung mit dem integrierten IR-Blaster des Xiaomi 15T Pro.

Der Paketname bleibt dauerhaft:

```text
com.skallahaze.irbloasster
```

## Aktueller Entwicklungsstand: 1.4.0

### SmartIR Live Audio Mix

SmartIR kann erlaubtes Medien-Audio des Android-Handys live erfassen und ohne vorherige MP3-Datei direkt im lokalen WLAN zur webOS Audio Bridge schicken. Die Bridge spielt Live-PCM über Web Audio und besitzt für den SmartIR-Musikkanal einen eigenen Gain-Regler.

Die TV-Bridge 0.4.0 verwendet ein hochauflösendes, rein vektorbasiertes Neon/Rainbow-Overlay. Ein echter Web-Audio-`AnalyserNode` steuert den Equalizer im Takt der laufenden Musik. Dadurch sind keine großen Grafik- oder Videoassets nötig.

Android-Apps dürfen Playback-Capture selbst blockieren; Streaming-/DRM-Inhalte können deshalb stumm bleiben. Der echte Bluetooth/A2DP-Pfad (`AMIXER4`) bleibt das bevorzugte spätere Backend, sobald die LG-Audio-Policy per UMI/Root steuerbar ist.

Die früheren Android-TV-Lab-/Bildschirm-Testoberflächen sind aus der normalen APK entfernt. Die historischen Analyse-Dokumente bleiben nur im Repository erhalten und belegen keinen Speicher in der installierten App.

Details: [`docs/AUDIO_MIX_ROOTFREE.md`](docs/AUDIO_MIX_ROOTFREE.md)

### LG OLED55B19LA.DEUQJP

- LG-NEC-IR-Fernbedienung bei 38 kHz
- lokale webOS-Kopplung über WSS 3001, mit WS 3000 als Fallback
- SSDP-TV-Suche, sichere Client-Key-Speicherung und Zertifikat-Fingerabdruck
- Wake-on-LAN, Auto-Reconnect, Lautstärke-, Mute-, Power- und App-Status
- Magic-Remote-Touchpad, D-Pad, Pointer, Klick, Scrollen und TV-Tastatur
- direkte HDMI-1- bis HDMI-4-Auswahl
- installierte Apps und echte TV-Eingänge dynamisch laden
- Live-Audio-Mix über lokale SmartIR Audio Bridge

### Sony STR-DB870 CEL

- Sony-SIRC bei 40 kHz, AV1 als normaler CEL-Betrieb
- Power, Lautstärke, Mute, Eingänge und Receiver-Menü
- A.F.D., 2CH/OFF, Klangfelder, Test Tone und Subwoofer-Kandidaten
- Tunerfunktionen und getrennte Legacy-/Alternativcodes
- Originalfernbedienung der CEL-Variante: RM-U305A

### JBL Simply Cinema SUB125

Der SUB125 besitzt keinen eigenen IR-Empfänger. SmartIR steuert relevante Funktionen indirekt über den Sony-Receiver.

### Backup und Datenschutz

Unter **Setup → Backup & Datenübertragung** kann SmartIR eine portable JSON-Datei exportieren und importieren. Der geheime webOS-Client-Key bleibt im Android-Keystore und wird nicht in das portable Backup geschrieben.

Live Audio speichert weder Video noch einen Audiomitschnitt. PCM-Daten werden nur während der laufenden Sitzung im lokalen WLAN zum TV übertragen.

## Installation über GitHub Actions

Unter **Actions → SmartIR Android APK** den neuesten erfolgreichen Lauf öffnen und das Artefakt laden:

```text
SmartIR-v1.4.0-debug
```

## Bauen in Termux

```bash
pkg install openjdk-17 git
cd ~/IRBloasster
git fetch origin
bash tools/build-termux.sh
```

Die APK liegt danach unter:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Audio Bridge auf dem LG installieren

Bei eingerichteter webOS Developer-Mode-Verbindung:

```bash
cd ~/IRBloasster
bash tools/install-audio-bridge.sh smartirtv
```

App-ID:

```text
com.skallahaze.smartir.audiobridge
```

## LG TV verbinden

1. TV und Smartphone mit demselben lokalen Netzwerk verbinden.
2. In SmartIR **Setup** öffnen.
3. **TV suchen** drücken oder die TV-IP manuell eintragen.
4. **Verbinden** drücken.
5. Die Kopplungsabfrage am Fernseher bestätigen.
6. Für Wake-on-LAN zusätzlich die MAC-Adresse speichern.

## Dokumentation

- [`docs/AUDIO_MIX_ROOTFREE.md`](docs/AUDIO_MIX_ROOTFREE.md)
- [`docs/DEVICE_INVENTORY.md`](docs/DEVICE_INVENTORY.md)
- [`docs/LG_OLED55B19LA_DEVICE_PROFILE.md`](docs/LG_OLED55B19LA_DEVICE_PROFILE.md)
- [`docs/LG_FIRMWARE_03_53_31_VS_03_53_45_FINDINGS.md`](docs/LG_FIRMWARE_03_53_31_VS_03_53_45_FINDINGS.md)
- [`docs/SONY_STR_DB870_CODES.md`](docs/SONY_STR_DB870_CODES.md)
- [`docs/JBL_SUB125_PROFILE.md`](docs/JBL_SUB125_PROFILE.md)
- [`docs/DATA_BACKUP_AND_MIGRATION.md`](docs/DATA_BACKUP_AND_MIGRATION.md)

## Signatur und Updates

GitHub-Actions-Debug-APKs können unterschiedliche Debug-Signaturen besitzen. Für garantiert installierbare Updates ohne Deinstallation wird später ein dauerhaftes Release-Keystore benötigt. Der Paketname bleibt bereits unverändert.

## Herkunft

Das Repository enthält ausschließlich eigenständig geschriebenen Quellcode und beschreibende Interoperabilitätsnotizen. Es werden keine Original-APKs, Kontodaten, Cloud-Schlüssel, Zertifikate, proprietären Grafiken oder Seriennummern veröffentlicht.
