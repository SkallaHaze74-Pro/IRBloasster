# SmartIR Audio Mix – Root-free Test

Ziel: normales TV-/HDMI-Bild und TV-Ton weiterlaufen lassen und zusätzlich eine zweite Musikquelle über den LG-TV ausgeben, ohne Root und ohne A2DP/Sound-Share als Bildquelle zu verwenden.

## Architektur

- TV/HDMI bleibt die erste Medienquelle.
- Die Android-App stellt eine ausgewählte Audiodatei nur im lokalen Netzwerk per HTTP bereit (oder verwendet eine direkte HTTP(S)-Audio-URL).
- Die webOS-App `com.skallahaze.smartir.audiobridge` spielt diese zweite Quelle.
- Die Bridge ruft `luna://com.webos.service.audio/tv/mixDigitalSoundOutput` mit `{ "mix": true }` auf.
- Der Musikpegel wird in der Bridge separat über den Web-Media-Pegel und zusätzlich `com.webos.audio/media/setVolume` geregelt.
- Der TV-Regler bleibt in SmartIR separat vorhanden.

## Einmalige Installation der TV-Bridge

Im bereits geklonten IRBloasster-Repo in Termux:

```bash
cd ~/IRBloasster
git pull
bash tools/install-audio-bridge.sh smartirtv
```

Alternativ manuell:

```bash
mkdir -p build-webos
ares-package webos-audio-bridge -o build-webos
ares-install -d smartirtv build-webos/*.ipk
```

## Android-Test

1. SmartIR 1.3.0 installieren.
2. Launcher **SmartIR Audio Mix** öffnen.
3. Mit dem LG-TV verbinden.
4. Eine lokale MP3/AAC/FLAC-Datei wählen oder eine direkte Audio-URL eintragen.
5. TV-Pegel und Musikpegel einstellen.
6. **Mix starten**.
7. Falls die Bridge sichtbar wird, wieder auf die gewünschte TV-/HDMI-Quelle wechseln.
8. Prüfen, ob die Musik danach weiterläuft.

### Positives Ergebnis

Wenn Bild + TV-Ton bleiben und die Musik weiterläuft, ist der Root-free Parallelpfad bestätigt. Danach kann die Bridge weiter versteckt/automatisiert und die Bedienung in die Hauptoberfläche integriert werden.

### Negatives Ergebnis

Wenn die Musik beim Zurückschalten sofort stoppt, suspendiert die Retail-webOS-App den Web-Media-Stream. Dann ist dieser Root-free Weg als dauerhafte Lösung nicht ausreichend. Die bisherigen Analyse-Ergebnisse bleiben trotzdem nutzbar: Ein zukünftiger Root-/Homebrew-Weg kann dieselbe SmartIR-Oberfläche verwenden und den Transport später durch den echten `AMIXER4`-/A2DP-Pfad ersetzen.

## Datenschutz / Netzwerk

- Lokale Dateien werden nicht hochgeladen.
- SmartIR kopiert die gewählte Datei nur temporär in den App-Cache.
- Der HTTP-Server bindet einen zufälligen Port im lokalen Netzwerk und wird beim Schließen von SmartIR Audio Mix beendet.

## Bekannte Einschränkungen

- Systemweite Audioaufnahme von Spotify/YouTube/etc. ist in diesem ersten Root-free Test noch nicht aktiviert.
- Einige Streaming-Dienste erlauben Android Audio Playback Capture grundsätzlich nicht oder schützen DRM-Audio.
- Die tatsächliche Hintergrund-Audiofähigkeit muss auf dem LG OLED B1 mit der aktuellen Firmware praktisch bestätigt werden.
