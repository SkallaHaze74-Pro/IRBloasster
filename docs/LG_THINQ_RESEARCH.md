# LG ThinQ – technische Auswertung für LivingRoom Controller

## Analysierte Quelle

- App: LG ThinQ
- Android-Paket: `com.lgeha.nuts`
- Version: `5.1.32310`
- Versionscode: `51002010`
- Export: Base-APK plus ARM64- und XXHDPI-Splits

Der Export enthielt laut Metadaten keine App-Datenverzeichnisse. Verwendet wurden ausschließlich Programm- und Ressourcenstrukturen zur Protokoll- und UI-Analyse.

## Im DEX sichtbare lokale webOS-Endpunkte

Unter anderem wurden folgende SSAP-Ziele im Programm gefunden:

```text
ssap://audio/getVolume
ssap://audio/setVolume
ssap://audio/setMute
ssap://audio/volumeUp
ssap://audio/volumeDown
ssap://com.webos.applicationManager/getForegroundAppInfo
ssap://com.webos.applicationManager/listApps
ssap://com.webos.service.ime/insertText
ssap://com.webos.service.ime/deleteCharacters
ssap://com.webos.service.ime/sendEnterKey
ssap://com.webos.service.networkinput/getPointerInputSocket
ssap://com.webos.service.tvpower/power/getPowerState
ssap://system/turnOff
ssap://tv/channelUp
ssap://tv/channelDown
ssap://tv/getChannelList
ssap://tv/getCurrentChannel
ssap://tv/getChannelProgramInfo
ssap://tv/getExternalInputList
ssap://tv/switchInput
ssap://media.controls/play
ssap://media.controls/pause
ssap://media.controls/stop
ssap://media.controls/fastForward
ssap://media.controls/rewind
ssap://system.launcher/launch
ssap://system.launcher/open
ssap://system.launcher/close
ssap://pairing/setPin
ssap://system/getSystemInfo
```

Zusätzlich waren Wake-on-LAN-Begriffe und Schalter wie `wolwowlOnOff`, `powerOnWol` und `SUPPORT_WOL` sichtbar.

## Pairing und Socket

Die öffentliche Connect-SDK-Referenz bestätigt den Ablauf:

1. `wss://TV-IP:3001`
2. `hello`
3. `register` mit Berechtigungsmanifest und optional gespeichertem `client-key`
4. Pairing-Bestätigung am TV
5. Antwort vom Typ `registered` mit `client-key`
6. normale `request`- beziehungsweise `subscribe`-Nachrichten

Der Pointer-Socket verwendet Textframes wie:

```text
type:click

```

```text
type:button
name:HOME

```

```text
type:move
dx:12
dy:-4
down:0

```

LivingRoom Controller setzt diesen Ablauf mit OkHttp-WebSockets eigenständig um. Für Port 3001 wird ein lokaler Zertifikat-Fingerabdruck nach dem Trust-on-first-use-Prinzip gespeichert.

## IR- und QuickSet-Befunde

Im ARM64-Paket befindet sich eine UEI-QuickSet-Komponente. Exportierte JNI-Namen deuten unter anderem auf Gerätearten-, Marken-, Modell- und Codeset-Abfragen sowie Test- und Ergebnisschritte hin. Diese Befunde erklären den Universalfernbedienungs-Assistenten der Original-App.

Nicht übernommen wurden:

- UEI-Binärbibliothek
- UEI-Codeset-Datenbank
- Zugangsdaten oder Online-Endpunkte
- LG-Ressourcen oder Schriften

Stattdessen enthält das Projekt einen eigenständigen NEC- und Sony-SIRC-Encoder sowie einen bewusst manuellen Code-Testbereich.

## Designbefunde

Das Paket enthält eine umfangreiche Karten-, Status-, Bottom-Sheet- und Geräte-Dashboard-Struktur sowie LG-Smart-UI-Schriften. Das neue Design übernimmt nur allgemeine Bedienprinzipien wie große Touchflächen, klare Gerätekarten, Statuspunkte und Dark/Light-Unterstützung. Farben, Vektorgrafiken und Compose-Komponenten sind eigenständig erstellt.

## Grenzen

- Eine APK-Analyse ersetzt keinen Test am konkreten Fernseher.
- webOS-Funktionen und Berechtigungen können je nach TV-Modell und Firmware variieren.
- Die genaue MAC-Adresse muss der Nutzer selbst eintragen.
- Das bereitgestellte Material belegt kein eindeutiges Sony-Modellprofil. Sony-Befehle bleiben deshalb test- und editierbar.
