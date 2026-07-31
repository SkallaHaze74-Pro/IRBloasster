# Living Room Controller

Eigenständige Android-Fernbedienung für einen **LG webOS TV** und ein **Sony Heimkino/Receiver**. Das Projekt kombiniert lokale WLAN-Steuerung, Magic-Remote-Touchpad, Smartphone-Tastatur, Wake-on-LAN und den integrierten Android-IR-Blaster.

Das Repository heißt historisch `IRBloasster`; App-Name und Projektname sind ab Version `0.2.0` **Living Room Controller**. Der Paketname bleibt `com.skallahaze.irbloasster`, damit spätere APKs als Update installiert werden können.

## Stand 0.2.0

### LG webOS

- SSDP-Suche nach `urn:lge-com:service:webos-second-screen:1`
- manuelle IP-Adresse als Ausweichmöglichkeit
- lokales Pairing per TV-Bestätigung
- verschlüsselte Speicherung des `client-key` mit Android Keystore/AES-GCM
- automatischer Fallback von `ws://TV:3000` auf `wss://TV:3001`
- Zertifikat-Fingerprint nach dem Trust-on-first-use-Prinzip
- Lautstärke, Mute, Sender, Wiedergabe und Ausschalten
- Live-Abonnements für Lautstärke, App, Sender und Power-State
- HDMI-/Eingangsliste sowie App-Liste
- App starten und Eingang wechseln
- Magic-Remote-Pointer: Bewegung, Klick, Scrollen und Tasten
- Texteingabe, Löschen und Enter über die Smartphone-Tastatur
- Wake-on-LAN sowie LG-NEC-IR-Fallback

### Sony IR

- eigener Sony-SIRC-Encoder für 12, 15 und 20 Bit
- 40-kHz-Übertragung mit Wiederholungen
- drei klar als **unbestätigt** markierte Kandidatenprofile
- Testablauf für Power, Lautstärke, Mute und Eingang
- Kandidatenwechsel direkt in der App

Die Sony-Codes müssen am tatsächlichen Gerät geprüft werden. Sie werden nicht als bereits verifiziert dargestellt.

### Szenen

- **Filmabend**
- **Gaming**
- **Alles aus**

Szenen können Wake-on-LAN, webOS, Eingangswechsel, Wartezeiten und Sony-IR kombinieren.

### Diagnose

- lokales Protokoll für SSDP, WebSocket, SSAP, Pointer, IR und Szenen
- automatische Schwärzung des TV-Client-Keys
- Pairing zurücksetzen und neu koppeln
- keine ThinQ-Anmeldung und keine LG-Cloud-Schlüssel

## Datenschutz und Herkunft

Die vom Nutzer bereitgestellte LG-ThinQ-App wurde ausschließlich als technische Referenz analysiert. **Nicht im Repository enthalten** sind:

- LG ThinQ APKs oder Split-APKs
- LG-Schriften, Bilder, Icons oder Layout-Ressourcen
- eingebettete API-Schlüssel, Zertifikate oder verschlüsselte DEX-Dateien
- Kontodaten, Geräte-Datenbanken oder persönliche ThinQ-Daten
- proprietäre UEI-QuickSet-Codepakete

Die App-Oberfläche, Netzwerklogik und IR-Encoder wurden eigenständig umgesetzt. Das webOS-Verhalten orientiert sich zusätzlich am öffentlich verfügbaren, Apache-2.0-lizenzierten LG Connect SDK; Details stehen in `THIRD_PARTY_NOTICES.md`.

Die reproduzierbare Bestandsaufnahme mit Hashes, Größen, DEX-Zahlen und Analysegrenzen steht in [`docs/LG_THINQ_ANALYSIS.md`](docs/LG_THINQ_ANALYSIS.md).

## Build

```bash
./gradlew :app:assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions baut nach jedem Push und Pull Request automatisch eine Debug-APK als Artifact.

## Erste Einrichtung

1. Handy und LG TV mit demselben Heimnetz verbinden.
2. App öffnen und auf **TVs suchen** tippen oder die TV-IP unter **Diagnose** eintragen.
3. **Verbinden** wählen und die Kopplungsanfrage am Fernseher bestätigen.
4. Für Wake-on-LAN die TV-MAC-Adresse eintragen.
5. Unter **TV** einen bevorzugten HDMI-Eingang auswählen.
6. Unter **Sony** die Kandidatenprofile am echten Gerät testen.

## Wichtige Testgrenzen

Der Quellcode und CI-Build können automatisiert geprüft werden. Diese Punkte benötigen echte Hardware:

- Pairing-Verhalten und erlaubte SSAP-Berechtigungen des konkreten LG-Modells
- Wake-on-LAN aus tiefem Standby
- Pointer-Geschwindigkeit
- LG-IR-Tasten je nach Modell
- passendes Sony-SIRC-Profil

Siehe `docs/DEVICE_TEST_CHECKLIST.md`.
