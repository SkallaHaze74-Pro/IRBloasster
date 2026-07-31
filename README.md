# SmartIR – Living Room Controller

SmartIR ist eine eigenständige Android-Fernbedienung für den **LG OLED55B19LA** und den **Sony STR-DB870**. Sie verbindet lokale LG-webOS-Steuerung mit dem integrierten IR-Blaster des Xiaomi 15T Pro. Ein LG-Cloudkonto ist für die lokale TV-Steuerung nicht erforderlich.

Der Paketname bleibt dauerhaft:

```text
com.skallahaze.irbloasster
```

Dadurch können spätere Builds als Update installiert werden.

## Aktueller Stand: 1.1

- eigenständig entwickelte Material-3-Oberfläche mit Hell-/Dunkelmodus
- große sofa- und einhandtaugliche Tasten
- LG-TV-Fernbedienung über NEC 32 Bit bei 38 kHz
- lokale webOS-Kopplung über WebSocket/SSAP
- bevorzugt sichere Verbindung über WSS-Port 3001
- lokaler WS-Port 3000 als Kompatibilitäts-Fallback
- SSDP-Suche nach webOS-Fernsehern im Heimnetz
- Android-Keystore-verschlüsselter TV-Client-Key
- Trust-on-first-use-Zertifikat-Fingerabdruck für den eigenen TV
- automatische Wiederverbindung
- Wake-on-LAN und LG-IR-Fallback
- Live-Status für Lautstärke, Mute, Power und aktive App
- Apps und externe Eingänge abrufen, starten beziehungsweise umschalten
- Magic-Remote-Pointer, D-Pad, Touchpad, Klick und Scrollen
- TV-Texteingabe und Enter
- Sony-SIRC-Testprofil für 12/15/20 Bit bei 40 kHz
- Sony Command Mode AV1 und AV2
- Szenen: Fernsehen, Heimkino, Musik und Alles aus
- Diagnose-Labor für eigene NEC-, SIRC- und SSAP-Tests
- Unit-Tests für NEC-/SIRC-Bitreihenfolge, Pulsbreiten und Frame-Timing
- automatischer GitHub-Actions-Test und APK-Build

## Wichtige Testgrenze

Der Android-Code, die Protokollencoder und der APK-Build werden automatisiert getestet. Pairing, Wake-on-LAN, Pointer-Verhalten und einzelne IR-Kommandos müssen zusätzlich am realen TV beziehungsweise Receiver bestätigt werden. Das Sony-Profil wird deshalb in der App als **Testprofil** bezeichnet, bis die Tasten am STR-DB870 geprüft sind.

Die vollständige Prüfliste liegt unter [`docs/HARDWARE_TEST_CHECKLIST.md`](docs/HARDWARE_TEST_CHECKLIST.md).

## Installation über GitHub Actions

Unter **Actions → SmartIR Android APK** den neuesten erfolgreichen Lauf öffnen und das Artefakt **SmartIR-v1.1-debug** laden. Nach dem Entpacken kann die enthaltene `app-debug.apk` installiert werden.

## Bauen in Termux

```bash
pkg install openjdk-17 git
git clone https://github.com/SkallaHaze74-Pro/IRBloasster.git
cd IRBloasster
chmod +x gradlew
./gradlew --no-daemon clean testDebugUnitTest assembleDebug
```

Die APK liegt danach hier:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## LG TV verbinden

1. TV und Smartphone mit demselben lokalen Netzwerk verbinden.
2. In SmartIR **Setup** öffnen.
3. **TV suchen** drücken oder die TV-IP manuell eintragen.
4. **Verbinden** drücken.
5. Die Kopplungsabfrage am Fernseher bestätigen.
6. Für Wake-on-LAN zusätzlich die MAC-Adresse des TVs speichern.

Der vom eigenen TV ausgegebene Client-Key wird lokal verschlüsselt gespeichert. Ändert sich der gespeicherte TV-Zertifikat-Fingerabdruck, blockiert SmartIR die sichere Verbindung, bis die Kopplung bewusst zurückgesetzt wird.

## LG-Funktionen

### Infrarot

- Power Toggle sowie getrenntes Ein-/Ausschalten
- Lautstärke und Mute
- Sender und Eingang
- D-Pad, OK, Home, Zurück, Einstellungen, Info, Guide und Exit
- Mediensteuerung
- Ziffern 0–9
- Farbtasten

### webOS

- Lautstärke direkt setzen und Live-Status lesen
- Mute setzen und lesen
- Sender und Mediensteuerung
- installierte Apps laden und starten
- externe Eingänge laden und umschalten
- aktive App und Power-Status lesen
- Bildschirmtastatur
- Magic-Remote-Pointer
- Diagnosekonsole für eigene `ssap://`-Befehle

## Sony AV1 / AV2

Der STR-DB870 verwendet typischerweise AV1. Reagiert er nicht oder wurde sein Command Mode geändert, kann unter Setup AV2 getestet werden. Einzelne Tasten und Geräteadressen gelten erst nach dem Test am echten Receiver als bestätigt.

## Projektstruktur

```text
app/src/main/java/com/skallahaze/irbloasster/
├── MainActivity.kt
├── data/SettingsRepository.kt
├── ir/
│   ├── ConsumerIrSender.kt
│   ├── LG_OLED55B1.kt
│   ├── Nec.kt
│   ├── Sirc.kt
│   └── Sony_STR_DB870.kt
├── ui/
│   ├── HomeScreen.kt
│   ├── SettingsScreen.kt
│   ├── SonyRemoteScreen.kt
│   ├── TvRemoteScreen.kt
│   ├── UiComponents.kt
│   └── theme/Theme.kt
└── webos/
    ├── SsdpDiscovery.kt
    ├── WakeOnLan.kt
    └── WebOsClient.kt
```

## Analyse und Herkunft

Die bereinigte technische LG-ThinQ-Analyse ist unter [`docs/LG_THINQ_ANALYSIS.md`](docs/LG_THINQ_ANALYSIS.md) dokumentiert. Das Repository enthält ausschließlich eigenständig geschriebenen Quellcode und beschreibende Interoperabilitätsnotizen.

Es werden keine LG- oder Sony-APK-Dateien, Kontodaten, Cloud-Schlüssel, Zertifikate, proprietären Schriftdateien, Bilder oder UEI-Codeset-Datenbanken veröffentlicht.
