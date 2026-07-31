# LivingRoom Controller

Eine unabhängige Android-Fernbedienung für ein Wohnzimmer-Setup mit **LG webOS TV** und einem **Sony-Gerät per Infrarot**. Das Projekt kombiniert lokale WLAN-Steuerung, Wake-on-LAN und den integrierten Consumer-IR-Blaster eines Android-Geräts.

Der Android-Paketname bleibt bewusst `com.skallahaze.irbloasster`, damit bestehende Installationen später aktualisiert werden können.

## Funktionen

### LG webOS über das lokale Netzwerk

- SSDP-Suche nach `urn:lge-com:service:webos-second-screen:1`
- verschlüsselte WebSocket-Verbindung über Port 3001
- Pairing-Bestätigung am TV und dauerhafte Speicherung des `client-key`
- Trust-on-first-use für den lokalen TV-Zertifikat-Fingerabdruck
- Lautstärke, Mute, Sender und Mediensteuerung
- Eingänge auflisten und direkt umschalten
- installierte TV-Apps auflisten und starten
- aktuelle Lautstärke, aktive App und Power-Status abonnieren
- Texteingabe über die Smartphone-Tastatur
- Magic-Remote-Pointer-Socket mit D-Pad, Klick, Bewegung und Scrollen
- Diagnoseprotokoll direkt in der App

### Einschalten und Offline-Fallback

- Wake-on-LAN über gespeicherte TV-MAC-Adresse
- LG NEC-IR-Fallback für zentrale Tasten
- IR-Blaster ist optional; die App startet auch auf Geräten ohne IR

### Sony SIRC Code Lab

- 12-, 15- und 20-Bit-SIRC
- 40-kHz-Träger, LSB-first, drei Wiederholungsframes
- editierbare Adresse, Befehle und Bitlänge
- manuelles Testen einzelner Codes
- gespeichertes Testprofil für Power, Lautstärke und Mute

Die voreingestellten Sony-Werte sind ausdrücklich ein **Testprofil**. Das analysierte LG-Paket enthält kein verifiziertes Profil für das konkrete Sony-Gerät des Nutzers. Daher gibt es keinen automatischen Vollbereichs-Scan und keine Behauptung, dass die Standardwerte garantiert passen.

### Szenen

- Filmabend
- Gaming
- Nur TV
- Alles aus

Szenen können Wake-on-LAN/IR, webOS-Verbindung, Eingangswahl und Sony-IR kombinieren.

## Sicherheit und Datenschutz

- Die TV-Steuerung bleibt im lokalen Netzwerk.
- Es werden keine LG-Kontodaten benötigt.
- Der webOS-`client-key` wird nur in den lokalen Android-Einstellungen gespeichert.
- Beim ersten TLS-Kontakt wird der Zertifikat-Fingerabdruck gespeichert. Ändert er sich später, verweigert die App die Verbindung, bis das Pairing im Setup bewusst zurückgesetzt wird.
- Die App enthält keine LG-Grafiken, LG-Schriftdateien, UEI-QuickSet-Datenbank oder proprietären Binärdateien.

## Bauen

GitHub Actions erzeugt bei jedem Push auf `livingroom-v1` beziehungsweise `main` eine Debug-APK:

1. Repository öffnen
2. **Actions** auswählen
3. Lauf **Android APK** öffnen
4. Artifact **LivingRoomController-debug** laden

Lokaler Build mit JDK 17, Android SDK 36 und Gradle 8.11.1:

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

Die APK liegt danach unter:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Erster Start

1. Smartphone und LG-TV mit demselben WLAN verbinden.
2. Unter **Setup** nach TVs suchen oder die TV-IP manuell eintragen.
3. **Verbinden** drücken und die Abfrage am Fernseher bestätigen.
4. Für Wake-on-LAN die MAC-Adresse des TVs eintragen.
5. Sony-Tasten im **Code Lab** einzeln testen und passende Werte speichern.

## Technische Grundlage

Die implementierten webOS-Abläufe orientieren sich an den im bereitgestellten LG-ThinQ-Paket sichtbaren SSAP-Endpunkten und an der öffentlich verfügbaren, unter Apache 2.0 lizenzierten Connect-SDK-Referenz. Die Implementierung in diesem Repository wurde eigenständig in Kotlin/OkHttp geschrieben.

Details: [`docs/LG_THINQ_RESEARCH.md`](docs/LG_THINQ_RESEARCH.md)

## Status

`1.0.0-alpha1`: vollständige erste Hybrid-Fernbedienung. Der Quellcode und die Protokolltests können automatisiert gebaut werden. Pairing, Wake-on-LAN und die tatsächlichen IR-Codes müssen zusätzlich am konkreten TV beziehungsweise Sony-Gerät getestet werden.
