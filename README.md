# SmartIR – Living Room Controller

SmartIR ist eine eigenständig entwickelte Android-Fernbedienung für den **LG OLED55B19LA.DEUQJP**, den **Sony STR-DB870 CEL** und den daran angeschlossenen **JBL Simply Cinema SUB125**. Sie verbindet lokale LG-webOS-Steuerung mit dem integrierten IR-Blaster des Xiaomi 15T Pro.

Der Paketname bleibt dauerhaft:

```text
com.skallahaze.irbloasster
```

## Aktueller Entwicklungsstand: 1.5.2

### APK-Cleanup 1.5.2

Der frühere separate **SmartIR Expert Service** wurde aus der installierten Android-App entfernt. Dazu gehören Launcher, Activity und der nur dafür benötigte Expert-WebSocket-Client. Ein alter, unbenutzter NEC-Helfer wurde ebenfalls entfernt.

Wichtig: Die dabei gewonnenen **LG-/webOS-Forschungsergebnisse bleiben vollständig im Repository**. Firmware-Funde, Factorywin-Startparameter, Read-only-Payloads, TV-Lab, Geräteprofil, Root-Forschung und Kalibrierungsnotizen wurden nicht gelöscht. So bleibt SmartIR schlanker, ohne spätere TV-Forschung zu verlieren.

### SmartIR Live Audio Mix

SmartIR kann erlaubtes Medien-Audio des Android-Handys live erfassen und ohne vorherige MP3-Datei direkt im lokalen WLAN zur webOS Audio Bridge schicken. Die Bridge spielt Live-PCM über Web Audio und besitzt für den SmartIR-Musikkanal einen eigenen Gain-Regler.

Die TV-Bridge 0.6.1 verwendet ein hochauflösendes, rein vektorbasiertes Neon/Rainbow-Overlay. Ein echter Web-Audio-`AnalyserNode` steuert den Equalizer im Takt der laufenden Musik. Dadurch sind keine großen Grafik- oder Videoassets nötig.

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

## Bauen in Termux

```bash
pkg install openjdk-17 git
cd ~/IRBloasster
git fetch origin
bash tools/build-termux.sh
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

## Erhaltene TV-Forschung

- [`docs/LG_FACTORYWIN_EXPERT_PATH.md`](docs/LG_FACTORYWIN_EXPERT_PATH.md) – archivierte Factorywin-/Service-Erkenntnisse; nicht mehr Teil der APK
- [`docs/LG_TV_LAB_READONLY_PAYLOADS.json`](docs/LG_TV_LAB_READONLY_PAYLOADS.json)
- [`docs/LG_FIRMWARE_03_53_31_VS_03_53_45_FINDINGS.md`](docs/LG_FIRMWARE_03_53_31_VS_03_53_45_FINDINGS.md)
- [`docs/LG_OLED55B19LA_DEVICE_PROFILE.md`](docs/LG_OLED55B19LA_DEVICE_PROFILE.md)
- [`docs/LG_ROOT_RESEARCH_03_53_45.md`](docs/LG_ROOT_RESEARCH_03_53_45.md)
- [`docs/LG_THINQ_ANALYSIS.md`](docs/LG_THINQ_ANALYSIS.md)
- [`docs/TV_LAB_AND_CALIBRATION.md`](docs/TV_LAB_AND_CALIBRATION.md)
- [`webos-tv-lab/`](webos-tv-lab/)

## Weitere Dokumentation

- [`docs/AUDIO_MIX_ROOTFREE.md`](docs/AUDIO_MIX_ROOTFREE.md)
- [`docs/DEVICE_INVENTORY.md`](docs/DEVICE_INVENTORY.md)
- [`docs/SONY_STR_DB870_CODES.md`](docs/SONY_STR_DB870_CODES.md)
- [`docs/JBL_SUB125_PROFILE.md`](docs/JBL_SUB125_PROFILE.md)
- [`docs/DATA_BACKUP_AND_MIGRATION.md`](docs/DATA_BACKUP_AND_MIGRATION.md)

## Signatur und Updates

Für lokale stabile Updates verwendet SmartIR das dauerhaft gespeicherte Release-Keystore unter `~/.smartir`. CI-Artefakte besitzen dagegen absichtlich eine temporäre Signatur und sind nicht für Updates über die lokale Installation gedacht.

## Herkunft

Das Repository enthält ausschließlich eigenständig geschriebenen Quellcode und beschreibende Interoperabilitätsnotizen. Es werden keine Original-APKs, Kontodaten, Cloud-Schlüssel, Zertifikate, proprietären Grafiken oder Seriennummern veröffentlicht.
