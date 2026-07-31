# Technische Erkenntnisse aus der bereitgestellten LG-ThinQ-App

Analysierte App: LG ThinQ 5.1.32310, Paket `com.lgeha.nuts`.

Die Untersuchung wurde ausschließlich genutzt, um Bedienbereiche und lokale Protokoll-Endpunkte zu verstehen. Es wurden keine Kontodaten, Schlüssel, LG-Assets oder proprietären Implementierungen in dieses Projekt übernommen.

## Nachweisbare lokale webOS-Bereiche

In den bereitgestellten APK-Dateien waren unter anderem folgende SSAP-URIs auffindbar:

- `ssap://audio/getStatus`
- `ssap://audio/setMute`
- `ssap://audio/setVolume`
- `ssap://audio/volumeUp`
- `ssap://audio/volumeDown`
- `ssap://tv/getExternalInputList`
- `ssap://tv/switchInput`
- `ssap://tv/channelUp`
- `ssap://tv/channelDown`
- `ssap://tv/getCurrentChannel`
- `ssap://com.webos.applicationManager/listApps`
- `ssap://com.webos.applicationManager/getForegroundAppInfo`
- `ssap://system.launcher/launch`
- `ssap://com.webos.service.networkinput/getPointerInputSocket`
- `ssap://com.webos.service.ime/insertText`
- `ssap://system/turnOff`
- `ssap://system/getSystemInfo`

## Eigenständige Umsetzung

`WebOsClient.kt` implementiert eine kleine, eigene SSAP-Schicht über OkHttp-WebSockets:

- Registrierung und Pairing-Prompt
- Speicherung des vom TV ausgegebenen Client-Schlüssels
- Request-, Response- und Subscription-Verarbeitung
- Pointer-WebSocket mit Button-, Move-, Click- und Scroll-Nachrichten
- Live-Abonnements für Audio- und Vordergrund-App-Status

Die Oberfläche und Grafiken dieses Projekts sind vollständig neu erstellt.
