# Changelog

## 1.4.0

- **Live-Audio vom Handy** ergänzt: keine lokale MP3-Datei mehr nötig
- Android `AudioPlaybackCapture` + `MediaProjection` nimmt erlaubtes Medien-Audio live auf; es wird kein Video oder Audiomitschnitt gespeichert
- PCM16/Stereo/48 kHz wird nur im lokalen WLAN über einen kleinen WebSocket-Server an den LG gestreamt
- webOS Audio Bridge spielt den Live-PCM-Strom über Web Audio (`ScriptProcessorNode` + eigener `GainNode`)
- Hintergrundmusik-Regler bleibt lokal auf dem SmartIR-Musikkanal und darf den LG-TV-/Masterpegel nicht verändern
- Bridge puffert nur kurz und verwirft alte Frames bei Verzögerung, statt große Latenz aufzubauen
- Datei/HTTP-URL bleibt als Diagnose-/Fallbackpfad erhalten
- Android weist sichtbar darauf hin, dass Streaming-/DRM-Apps interne Audioaufnahme blockieren können
- Bluetooth/A2DP `AMIXER4` bleibt das bevorzugte spätere Backend, sobald die LG-Policy per UMI/Root umgangen werden kann
- webOS Audio Bridge auf 0.3.0 angehoben

## 1.3.1

- LG-B1-Testbefund korrigiert: `com.webos.audio/media/setVolume` wirkt auf der Retail-Firmware auf den TV-/Masterpfad und ist daher **kein** separater Musikregler
- Musiklautstärke der Audio Bridge auf einen lokalen Web-Audio-`GainNode` umgestellt; der Musikregler darf den TV-Pegel damit nicht mehr verändern
- Root-free Persistenztest nutzt Web Audio zuerst und HTML-Audio nur noch als Fallback
- Bridge prüft den HTTP-Stream direkt und zeigt Lade-/Decode-/Playback-Fehler getrennt an
- UI benennt den ersten Regler ehrlich als LG Master / TV; echter `ADEC1`-only TV-Regler bleibt UMI-/Root-Ziel
- webOS Audio Bridge auf 0.2.0 angehoben

## 1.3.0

- experimentellen **SmartIR Audio Mix** als eigenen Launcher ergänzt
- Root-free Dual-Media-Test für LG webOS ergänzt
- lokale Audiodateien werden ausschließlich im Heimnetz vom Handy per HTTP mit Byte-Range-Unterstützung bereitgestellt
- direkte HTTP(S)-Audio-URLs können alternativ als Musikquelle verwendet werden
- TV- und Musikpegel erhalten getrennte Regler in der Audio-Mix-Oberfläche
- webOS-Begleit-App `com.skallahaze.smartir.audiobridge` ergänzt
- Audio Bridge nutzt `mixDigitalSoundOutput(true)` und einen eigenen Media-Pegel für Hintergrundmusik
- Termux-Installer `tools/install-audio-bridge.sh` ergänzt
- Root/A2DP-Pfad bleibt als späteres Upgrade vorgesehen; der Root-free Test verändert keine Systemdateien

## 1.1.1

- eigenes adaptives und monochromes SmartIR-App-Icon ergänzt
- GitHub Actions auf Node-24-kompatible Hauptversionen aktualisiert
- Gradle-Cache explizit auf den offenen Basic-Provider gesetzt
- Android-Lint in die automatische Prüfung aufgenommen
- APK erhält einen eindeutigen Dateinamen und eine SHA-256-Prüfsumme
- Android-24-kompatible Theme-Ressourcen ergänzt
- Sony-Bezeichnung in der Projektdokumentation bewusst als unbestätigtes Testprofil präzisiert

## 1.1.0-alpha1

- LG-webOS-Suche, Pairing-Härtung und Wake-on-LAN ergänzt
- sichere WSS-Verbindung mit lokalem Zertifikat-Fingerabdruck ergänzt
- Sony-SIRC-Pulsbreitencodierung korrigiert
- Protokolltests und Hardware-Testcheckliste ergänzt
