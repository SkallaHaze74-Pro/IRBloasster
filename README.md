# SmartIR – Living Room Controller

SmartIR ist eine eigenständig entwickelte Android-Fernbedienung für den **LG OLED55B19LA** und den **Sony STR-DB870**. Sie verbindet lokale LG-webOS-Steuerung mit dem integrierten IR-Blaster des Xiaomi 15T Pro. Ein LG-Cloudkonto ist für die lokale TV-Steuerung nicht erforderlich.

Der Paketname bleibt dauerhaft:

```text
com.skallahaze.irbloasster
```

Dadurch können spätere Builds mit derselben Signatur als Update installiert werden.

## Aktueller Stand: 1.1.3

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
- bestätigtes Gerätemodell **Sony STR-DB870**
- Sony-SIRC-Profil für 12/15 Bit bei 40 kHz und drei Wiederholungsframes
- Sony Command Mode AV1 und AV2
- vollständige bekannte STR-DB870/RM-U305A/RM-PP505-Tastenfamilie
- moderne Sony-DSP-/Menücodes auf Geräteadresse 144/176
- getrennte alternative Sony-Codes für ältere Gerätefamilien
- Szenen: Fernsehen, Heimkino, Musik und Alles aus
- Diagnose-Labor für eigene NEC-, SIRC- und SSAP-Tests
- konkrete IR-Systemfehler statt einer pauschalen Fehlermeldung
- Unit-Tests, Android-Lint und automatischer APK-Build
- SHA-256-Prüfsumme neben jeder erzeugten Test-APK

## Sony STR-DB870

Das Gerätemodell wurde durch das Typenschildfoto bestätigt. Die sichtbare Seriennummer wird weder in der App noch im Repository gespeichert.

Sony führt für den STR-DB870 je nach Region die Fernbedienungen **RM-U305A** und **RM-PP505**. AV1 ist die Werkseinstellung des Receiver-Command-Modes. AV2 wird nur benötigt, wenn der Receiver entsprechend umgestellt wurde.

Das bisherige generische Sony-Profil wurde für 1.1.3 grundlegend korrigiert:

- DVD/LD verwendet zuerst den gelernten Command 125; Command 107 bleibt als Alternative
- MD/TAPE verwendet zuerst Command 105; Command 35 bleibt als Alternative
- AUX und Sleep wurden ergänzt
- A.F.D., 2CH/OFF, Mode +/−, Input Mode, Night Mode, EQ/Tone und Audio Split wurden ergänzt
- Main Menu und Pfeiltasten verwenden korrekt die 15-Bit-Geräteadresse 144 in AV1
- AV2 verschiebt die jeweilige Geräteadresse um 32 und verwendet 15 Bit
- Power On/Off, Eingänge, Tuner, Subwoofer- und Legacy-Varianten sind direkt testbar

Die vollständige Tabelle mit Command, Adresse, Bitlänge, Quellenlage und Testreihenfolge steht unter [`docs/SONY_STR_DB870_CODES.md`](docs/SONY_STR_DB870_CODES.md).

### Wichtige Testgrenze

Das Modell und der vorgesehene Funktionsumfang sind bestätigt; die numerischen IR-Zuordnungen sind quellengestützte Kandidaten. Jede Taste gilt erst nach Reaktion am konkreten Receiver als hardwarebestätigt. SmartIR kennzeichnet Alternativen deshalb getrennt und schaltet nicht unbemerkt zwischen verschiedenen Codes um.

## Installation über GitHub Actions

Unter **Actions → SmartIR Android APK** den neuesten erfolgreichen Lauf auf dem Branch **main** öffnen und das Artefakt **SmartIR-v1.1.3-debug** laden. Es enthält:

```text
SmartIR-v1.1.3-debug.apk
SmartIR-v1.1.3-debug.apk.sha256
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

## Hardware-Test

Die vollständige Schritt-für-Schritt-Prüfliste liegt unter [`docs/HARDWARE_TEST_CHECKLIST.md`](docs/HARDWARE_TEST_CHECKLIST.md). Für den Sony zuerst AV1 mit Power, Volume und Mute prüfen; AV2 erst testen, wenn AV1 vollständig ohne Reaktion bleibt.

## Signatur und Updates

GitHub-Actions-Debug-APKs werden mit einer temporären Debug-Signatur gebaut. Für garantiert installierbare Updates ohne Deinstallation muss später ein dauerhaftes Release-Keystore sicher als GitHub-Secret eingerichtet werden; der Paketname allein reicht nicht aus.

## Analyse und Herkunft

Die bereinigte technische LG-ThinQ-Analyse liegt unter [`docs/LG_THINQ_ANALYSIS.md`](docs/LG_THINQ_ANALYSIS.md). Das Repository enthält ausschließlich eigenständig geschriebenen Quellcode und beschreibende Interoperabilitätsnotizen.

Es werden keine LG- oder Sony-APK-Dateien, Kontodaten, Cloud-Schlüssel, Zertifikate, proprietären Schriftdateien, Bilder, Seriennummern oder vollständigen UEI-Codeset-Datenbanken veröffentlicht.
