# SmartIR Audio Mix – Root-free / Live-Audio

## Ziel

SmartIR soll TV/HDMI-Bild und TV-Ton weiterlaufen lassen und zusätzlich einen eigenen Hintergrundmusik-Kanal einblenden. Der LG-B1-Test hat gezeigt, dass die Retail-Firmware beim normalen Bluetooth-Sound-Share `ADEC1` abklemmt und beim Zurückschalten A2DP trennt. Deshalb testet SmartIR ohne Root einen separaten Web-Audio-Pfad.

## 1.4.0 Live-Audio

Der bevorzugte Root-free Test braucht keine MP3-Datei mehr:

1. SmartIR Audio Mix öffnen und mit dem LG verbinden.
2. `Live-Capture starten` drücken.
3. Androids Systemdialog für Audio-/Bildschirmaufnahme bestätigen. SmartIR speichert kein Video und keinen Audiomitschnitt.
4. Warten, bis `LIVE` und eine `ws://.../live`-Adresse angezeigt werden.
5. `LIVE Mix starten` drücken.
6. Musik-App auf dem Handy starten bzw. weiterlaufen lassen.
7. Am LG wieder TV/HDMI wählen.
8. Prüfen, ob die Hintergrundmusik weiterläuft und der Musikregler nur SmartIR-Musik verändert.

Technik: Android `AudioPlaybackCapture` liefert PCM16 Stereo mit 48 kHz. Ein LAN-only WebSocket-Server auf dem Handy schickt kleine PCM-Blöcke an die webOS Audio Bridge. Dort werden die Frames mit Web Audio (`ScriptProcessorNode`) abgespielt und durch einen eigenen `GainNode` geregelt. `mixDigitalSoundOutput(true)` bleibt als LG-Mix-Flag aktiv.

## Datei/URL-Fallback

Eine lokale MP3/AAC/M4A-Datei oder direkte HTTP(S)-Audio-URL kann weiterhin als Diagnosepfad verwendet werden. Für den normalen Gebrauch ist Live-Audio vorgesehen.

## Einschränkungen

- Android erlaubt Playback-Capture nur für Audio, das die abspielende App zur Aufnahme freigibt. Streaming-/DRM-Apps können die interne Aufnahme blockieren.
- Der obere `LG Master / TV`-Regler ist noch kein echter `ADEC1`-only Regler. Auf der aktuellen Retail-Firmware wirkt der normale LG-Master grundsätzlich auf den Lautsprecherausgang.
- Ein echter Bluetooth-Backend-Pfad (`A2DP -> AMIXER4`) bleibt das Ziel, sobald UMI/Audio-Policy mit ausreichenden Rechten bzw. Root steuerbar ist.

## Einmalige Installation der TV-Bridge

```bash
cd ~/IRBloasster
bash tools/install-audio-bridge.sh smartirtv
```

## Erfolgskriterium

Erfolg bedeutet: TV/HDMI bleibt sichtbar und hörbar, SmartIR-Live-Musik bleibt zusätzlich hörbar, und der Hintergrundmusik-Regler verändert nicht den TV-Ton.

## Datenschutz / Netzwerk

- Live-Audio wird nicht als Datei gespeichert.
- PCM-Daten werden nur während der aktiven Capture-Sitzung im lokalen WLAN übertragen.
- Beim Stoppen von Live-Capture wird der lokale WebSocket-Server beendet.
