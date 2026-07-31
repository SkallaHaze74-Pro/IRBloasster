# SmartIR – Living Room Controller

SmartIR ist eine eigenständig entwickelte Android-Fernbedienung für den **LG OLED55B19LA** und ein **Sony-SIRC-Testprofil**. Sie verbindet lokale LG-webOS-Steuerung mit dem integrierten IR-Blaster des Xiaomi 15T Pro. Ein LG-Cloudkonto ist für die lokale TV-Steuerung nicht erforderlich.

Der Paketname bleibt dauerhaft:

```text
com.skallahaze.irbloasster
```

## Aktueller Stand: 1.1.1-alpha1

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
- Sony-SIRC-Testprofil für 12/15/20 Bit bei 40 kHz
- Sony Command Mode AV1 und AV2
- Szenen: Fernsehen, Heimkino, Musik und Alles aus
- Diagnose-Labor für eigene NEC-, SIRC- und SSAP-Tests
- Unit-Tests, Android-Lint und automatischer APK-Build
- SHA-256-Prüfsumme neben jeder erzeugten Test-APK

## Wichtige Testgrenze

Android-Code, Protokollencoder, Lint und APK-Build werden automatisiert geprüft. Pairing, Wake-on-LAN, Pointer-Verhalten sowie einzelne IR-Kommandos müssen zusätzlich am realen TV beziehungsweise Sony-Gerät bestätigt werden. Das Sony-Profil bleibt deshalb ausdrücklich ein **Testprofil**, bis das genaue Modell und die reagierenden Tasten am Gerät bestätigt sind.

Die Prüfliste liegt unter [`docs/HARDWARE_TEST_CHECKLIST.md`](docs/HARDWARE_TEST_CHECKLIST.md).

## Installation über GitHub Actions

Unter **Actions → SmartIR Android APK** den neuesten erfolgreichen Lauf öffnen und das Artefakt **SmartIR-v1.1.1-debug** laden. Es enthält:

```text
SmartIR-v1.1.1-debug.apk
SmartIR-v1.1.1-debug.apk.sha256
```

## Bauen in Termux

```bash
pkg install openjdk-17 git
git clone https://github.com/SkallaHaze74-Pro/IRBloasster.git
cd IRBloasster
chmod +x gradlew
./gradlew --no-daemon clean testDebugUnitTest lintDebug assembleDebug
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
6. Für Wake-on-LAN zusätzlich die TV-MAC-Adresse speichern.

Der vom eigenen TV ausgegebene Client-Key wird lokal verschlüsselt gespeichert. Ändert sich der gespeicherte TV-Zertifikat-Fingerabdruck, blockiert SmartIR die sichere Verbindung, bis die Kopplung bewusst zurückgesetzt wird.

## LG-Funktionen

### Infrarot

- Power Toggle sowie getrenntes Ein-/Ausschalten
- Lautstärke, Mute, Sender und Eingang
- D-Pad, OK, Home, Zurück, Einstellungen, Info, Guide und Exit
- Mediensteuerung, Ziffern 0–9 und Farbtasten

### webOS

- Lautstärke direkt setzen und Live-Status lesen
- Mute setzen und lesen
- Sender und Mediensteuerung
- installierte Apps laden und starten
- externe Eingänge laden und umschalten
- aktive App und Power-Status lesen
- Bildschirmtastatur und Magic-Remote-Pointer
- Diagnosekonsole für eigene `ssap://`-Befehle

## Sony AV1 / AV2

Das enthaltene Sony-Profil ist ein testbarer SIRC-Kandidat. Reagiert ein Gerät nicht, kann unter Setup zwischen AV1 und AV2 gewechselt und im Testlabor ein eigener 12-, 15- oder 20-Bit-Code gesendet werden. Einzelne Tasten und Geräteadressen gelten erst nach dem Test am echten Gerät als bestätigt.

## Analyse und Herkunft

Die bereinigte technische LG-ThinQ-Analyse liegt unter [`docs/LG_THINQ_ANALYSIS.md`](docs/LG_THINQ_ANALYSIS.md). Das Repository enthält ausschließlich eigenständig geschriebenen Quellcode und beschreibende Interoperabilitätsnotizen.

Es werden keine LG- oder Sony-APK-Dateien, Kontodaten, Cloud-Schlüssel, Zertifikate, proprietären Schriftdateien, Bilder oder UEI-Codeset-Datenbanken veröffentlicht.
