# Changelog

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
