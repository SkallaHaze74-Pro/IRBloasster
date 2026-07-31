# LG ThinQ – technische Analyse für SmartIR / Living Room Controller

## Zweck und Abgrenzung

Diese Notiz hält die Ergebnisse der Untersuchung des vom Eigentümer bereitgestellten LG-ThinQ-App-Pakets fest. Ziel ist eine eigenständige Android-Fernbedienung für den eigenen LG webOS-TV und den Sony STR-DB870.

Die LG-App dient ausschließlich als technische Referenz für Bedienabläufe, Protokollflächen und UI-Prinzipien. Das Repository enthält keine LG-APK, Kontodaten, Cloud-Schlüssel, Zertifikate, Schriftdateien, Bilder, UEI-Codeset-Datenbanken oder proprietären Quelltexte.

## 1. Geprüftes Material

- App: LG ThinQ
- Paket: `com.lgeha.nuts`
- Version: `5.1.32310`
- Version Code: `51002010`
- Basis-APK plus ARM64- und XXHDPI-Splits
- Paketinhalt: `base.apk`, `split_config.arm64_v8a.apk`, `split_config.xxhdpi.apk`
- keine gesicherten App-Daten oder persönlichen ThinQ-Nutzerdaten
- App-Manager-Backup ohne Verschlüsselung
- SHA-256-Prüfsummen der Begleitdateien wurden lokal bestätigt

## 2. Technischer Aufbau

Das untersuchte Paket ist eine vollständige Smart-Home-Plattform und keine reine Fernbedienung. Gefunden wurden unter anderem:

- 20 DEX-Dateien
- Android-/Kotlin-Code und Webmodule
- lokale LG-webOS-Steuerung über Connect-SDK-/SSAP-Komponenten
- IR-Unterstützung und Geräteprofil-Testabläufe
- native ARM64-Bibliotheken
- Matter-, Casting-, Bluetooth-, BLE-, WLAN-, mDNS-, UWB- und Audio-Komponenten

Für SmartIR werden nur die für lokale TV-Interoperabilität und eigene IR-Profile relevanten Konzepte verwendet.

## 3. Lokale LG-webOS-Steuerung

Die wichtigste Erkenntnis ist eine vollständige lokale webOS-Fernbedienungsfläche über WebSocket/SSAP. Dadurch kann SmartIR neben IR auch Rückmeldungen vom Fernseher erhalten.

### Gefundene Funktionsgruppen

- Lautstärke erhöhen, senken und direkt setzen
- Mute setzen und Live-Status abonnieren
- Sender hoch/runter sowie Sender- und Programminformationen
- externe Eingänge abrufen und direkt umschalten
- installierte Apps und Startpunkte abrufen
- Apps starten und den Vordergrundstatus lesen
- Mediensteuerung
- Systeminformationen und Power-Status
- Magic-Remote-Pointer-Socket
- Bildschirmtastatur und Texteingabe

### Relevante SSAP-Endpunkte

```text
ssap://audio/getVolume
ssap://audio/setMute
ssap://audio/setVolume
ssap://audio/volumeUp
ssap://audio/volumeDown
ssap://tv/channelUp
ssap://tv/channelDown
ssap://tv/getExternalInputList
ssap://tv/switchInput
ssap://media.controls/play
ssap://media.controls/pause
ssap://media.controls/stop
ssap://media.controls/rewind
ssap://media.controls/fastForward
ssap://com.webos.applicationManager/listApps
ssap://com.webos.applicationManager/getForegroundAppInfo
ssap://system.launcher/launch
ssap://system/getSystemInfo
ssap://system/turnOff
ssap://com.webos.service.tvpower/power/getPowerState
ssap://com.webos.service.networkinput/getPointerInputSocket
ssap://com.webos.service.ime/insertText
ssap://com.webos.service.ime/deleteCharacters
ssap://com.webos.service.ime/sendEnterKey
```

### Magic Remote / Pointer

Der separate Pointer-WebSocket unterstützt:

- D-Pad-Tasten und OK
- Touchpad-Bewegung
- Klick
- Scrollen
- Home, Back, Info und weitere Remote-Tasten
- Texteingabe vom Smartphone

### Erkennung, Pairing und Wiederverbindung

Aus der Analyse wurde für SmartIR folgende eigenständige Architektur abgeleitet:

1. SSDP-Suche im lokalen Netzwerk
2. sichere Verbindung über `wss://TV-IP:3001`
3. lokaler Legacy-Fallback über `ws://TV-IP:3000`
4. Pairing-Bestätigung am Fernseher
5. Speicherung des vom eigenen TV ausgestellten Client-Keys
6. Android-Keystore-Verschlüsselung des Client-Keys
7. Trust-on-first-use-Fingerabdruck für das lokale TV-Zertifikat
8. automatisches Wiederverbinden
9. Live-Abonnements für Lautstärke, Mute, App und Power
10. Wake-on-LAN plus LG-IR-Fallback

## 4. IR-System und offene Profile

Die ThinQ-App enthält einen Geräteprofil-Testablauf: Hersteller und Modell wählen, mehrere Codesets testen, Erfolg bestätigen und das funktionierende Profil speichern.

SmartIR übernimmt nur dieses Bedienprinzip und verwendet einen eigenen offenen IR-Kern:

- LG/NEC 32 Bit bei 38 kHz
- Sony SIRC 12, 15 oder 20 Bit bei 40 kHz
- konfigurierbare Wiederholungen
- Diagnose- und Rohcode-Labor
- später erweiterbare JSON-Geräteprofile

Es werden keine UEI-Zugangsdaten oder proprietären Codeset-Pakete übernommen.

## 5. In SmartIR bereits umgesetzt

- eigenständige Compose-Oberfläche mit Hell-/Dunkelmodus
- getrennte Bereiche für LG-TV, Sony-Receiver und Setup
- LG-IR-Profil mit Navigation, Lautstärke, Sendern, Medien, Ziffern und Farbtasten
- Sony-SIRC-Testprofil mit AV1/AV2-Umschaltung
- korrekte NEC- und SIRC-Encoder
- Unit-Tests für Bitreihenfolge, Pulsbreiten und Wiederholungsperiode
- SSDP-Gerätesuche
- webOS-Pairing und Client-Key-Speicherung
- WSS 3001 mit Zertifikat-Fingerabdruck und WS-3000-Fallback
- automatische Wiederverbindung
- Apps, Eingänge und Live-Status
- Touchpad-/Pointer-Befehle und Texteingabe
- Wake-on-LAN
- Szenen für Fernsehen, Heimkino, Musik und Alles aus
- Diagnoseprotokoll mit Schwärzung des Client-Keys
- Rohcode-Labor für NEC, SIRC und SSAP
- automatischer GitHub-Actions-Test und APK-Build

## 6. Designprinzipien

Es werden keine LG-Grafiken oder Schriften kopiert. Eigenständig umgesetzt werden nur allgemeine Bedienprinzipien:

- ruhige dunkle beziehungsweise helle Grundfläche
- große abgerundete Gerätekarten
- klar sichtbarer Verbindungsstatus
- große sofa-taugliche Touch-Zonen
- Schnellaktionen und Szenen
- Bottom-Sheet-/Kartenlogik für Apps und Eingänge
- sichtbare Aktivzustände und Haptik
- TV/Sony als klare Gerätebereiche
- Touchpad und D-Pad
- einhändige Lautstärke- und Kanalsteuerung

## 7. Noch am echten Gerät zu bestätigen

Der Quellcode und der APK-Build sind automatisiert prüfbar; physische Gerätefunktionen können nur am realen TV beziehungsweise Receiver bestätigt werden:

- erste webOS-Pairing-Abfrage des LG OLED55B19LA
- WSS-Port 3001 und eventueller Port-3000-Fallback
- Wake-on-LAN im jeweiligen TV-Standby-Modus
- Pointer-Empfindlichkeit
- einzelne LG-IR-Codes
- Sony STR-DB870 Command Mode AV1/AV2
- einzelne Sony-SIRC-Kommandos und Geräteadressen

Bis zu dieser Geräteprüfung wird das Sony-Profil in der App ausdrücklich als Testprofil bezeichnet.

## 8. Geplante Erweiterungen

- bestätigte Geräteprofile exportieren/importieren
- geführter IR-Code-Testassistent
- frei editierbare Makros mit Verzögerungen und Bedingungen
- Favoriten für Apps und Eingänge
- Quick-Settings-Kachel
- Home-Screen-Widget
- Benachrichtigungs-Fernbedienung
- lokaler Profil-Editor und Erfolgsstatistik

## Ergebnis

Das bereitgestellte Material zeigt die Kombination aus lokaler webOS-Steuerung, Touchpad, Tastatur, Apps, Eingängen, Live-Status, IR-Profiltests und Automatisierung. SmartIR rekonstruiert diese Möglichkeiten eigenständig für die Geräte des Projektinhabers, ohne LG- oder Sony-Appdaten beziehungsweise geschützte Ressourcen zu veröffentlichen.
