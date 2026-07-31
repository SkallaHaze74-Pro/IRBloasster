# LG ThinQ 5.1.32310 – technische Bestandsaufnahme

Diese Datei dokumentiert die für den **Living Room Controller** verwendete Interoperabilitätsanalyse. Sie enthält keine LG-APK, keine Split-APK, keine Schrift, keine Grafik, keinen privaten Schlüssel und keine persönliche App-Datenbank.

## Geprüftes Material

| Feld | Wert |
|---|---|
| App | LG ThinQ |
| Paket | `com.lgeha.nuts` |
| Version | `5.1.32310` |
| Version Code | `51002010` |
| Basis-APK | `base.apk` |
| Splits | `split_config.arm64_v8a.apk`, `split_config.xxhdpi.apk` |
| Instruction Set | ARM64 |
| App-Daten im Backup | keine (`data_dirs: []`) |
| Backup-Verschlüsselung | keine (`crypto: none`) |
| Prüfsummenverfahren | SHA-256 |

## Reproduzierbare Prüfsummen

```text
9d3cb67f444560b389071fbda53be0cb885a431c749ca7249788d35f7cfc38ea  source.tar.gz.0
b0b9bb1ff6347529d258287b66a239bd2675ddfa1b05468733687b95d6106e77  info_v5.am.json
fd67f958e6a4fc34121de55d1049db2d2bf2d90d356858969af35f9a6d160dd9  meta_v5.am.json
7e05cc4a42afdd6f4edadd817e97e9ed2a37cfcfa89718680b3a34f45b49b565  misc.am.tsv
72d5ae917342d666c128fc2f9e357cead3277d75c34cfe5e1e93afe69fcdf521  standalone/base APK
```

Die bereitgestellte Standalone-APK und die `base.apk` aus dem App-Manager-Archiv waren bytegleich.

## Größen und Struktur

- `base.apk`: 230,227,935 Byte
- ARM64-Split: 81,087,467 Byte
- XXHDPI-Split: 5,924,193 Byte
- 20 DEX-Dateien
- 135,924 Class Definitions
- 974,944 Method IDs
- 874,664 Field IDs
- 944,915 String IDs
- 4,514 Dateien unter `res/` in der Basis-APK
- 1,044 Dateien im XXHDPI-Ressourcen-Split
- 34 native ARM64-`.so`-Bibliotheken

Die Werte sind eine statische Bestandsaufnahme der bereitgestellten Version und keine Aussage über später veröffentlichte ThinQ-Versionen.

## Für die TV-Steuerung relevante Bereiche

Gefundene Namensräume beziehungsweise Komponenten umfassen insbesondere:

```text
com.lge.conv.thingstv
com.lge.lms.things.service.smarttv
com.connectsdk
com.connectsdk3
com.lge.conv.thingstv.tvplugin.controller.TVPluginRemoteController
```

Die Analyse bestätigte eine lokale webOS-Steuerungsfläche mit:

- SSDP-Erkennung über den webOS-Second-Screen-Dienst
- WebSocket-Pairing und dauerhaftem `client-key`
- SSAP-Anfragen und Abonnements
- Lautstärke, Mute, Sender, Medien, Eingänge und Apps
- Pointer-WebSocket für Bewegung, Klick, Scrollen und Remote-Tasten
- Remote-Tastatur und Texteingabe
- lokale Power-/Statusabfragen

Die vollständige statisch gefundene Liste von 113 URI-Strings steht in [`LG_THINQ_SSAP_ENDPOINTS.txt`](LG_THINQ_SSAP_ENDPOINTS.txt). Das Vorhandensein eines URI-Strings bedeutet nicht automatisch, dass jeder Fernseher oder jede Firmware den Dienst freigibt.

## In Version 0.2.0 eigenständig umgesetzt

- SSDP-Suche und manuelle IP-Auswahl
- `hello`/`register`-Pairing
- lokale, mit Android Keystore/AES-GCM geschützte Speicherung des TV-Client-Keys
- `ws://:3000` mit Fallback auf `wss://:3001`
- Trust-on-first-use für das lokale WSS-Zertifikat
- SSAP-Request-/Subscribe-Verarbeitung
- Lautstärke, Mute, Sender, Medien, Power-Off
- Apps und Eingänge laden und starten/wechseln
- Pointer-Socket mit Klick, Tasten, Bewegung und Scrollen
- webOS-Texteingabe
- Wake-on-LAN
- eigener LG-NEC- und Sony-SIRC-IR-Kern
- Szenen, Diagnoselog und Premium-Compose-Oberfläche

## Nicht übernommen

- LG-Quellcode oder dekompilierte Methoden
- LG-ThinQ-APK beziehungsweise Split-APKs
- LG-Schriften, Bilder, Icons, Animationen oder Layout-Ressourcen
- LG-/ThinQ-Cloud-Schlüssel, Zertifikate oder Kontodaten
- UEI-QuickSet-Codepakete oder Zugangsdaten
- verschlüsselte DEX-Assets

Das Repository enthält ausschließlich eigenständig erstellten Quellcode, technische Hashes, öffentliche Interoperabilitätsinformationen und die aus der statischen Analyse abgeleitete Endpunkt-Inventarliste.

## Verifikation am realen Gerät

Folgende Punkte können nur mit dem eigenen LG-TV beziehungsweise Sony-Gerät bestätigt werden:

- tatsächlich freigegebene SSAP-Berechtigungen
- Pairing-Dialog und Wiederverbindung auf dem konkreten webOS-Build
- Wake-on-LAN aus verschiedenen Standby-Zuständen
- Pointer-Geschwindigkeit
- modellabhängige LG-IR-Codes
- korrektes Sony-SIRC-Kandidatenprofil

Dafür dient [`DEVICE_TEST_CHECKLIST.md`](DEVICE_TEST_CHECKLIST.md).
