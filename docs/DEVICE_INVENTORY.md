# SmartIR – bereinigtes Geräte- und Quelleninventar

Dieses Dokument sichert die für Entwicklung, Tests und spätere Migration wichtigen technischen Daten. Personenbezogene oder unnötig eindeutige Daten werden nicht öffentlich gespeichert.

## Entwicklungsgerät

- Smartphone: **Xiaomi 15T Pro**
- Plattform: Android
- integrierter Consumer-IR-Sender
- Android-API: `ConsumerIrManager`
- benötigte Manifest-Berechtigung: `android.permission.TRANSMIT_IR`
- bevorzugter Entwicklungsweg: Termux, AndroidIDE und GitHub Actions

## LG Fernseher

- Hersteller: LG Electronics
- Modell: **OLED55B19LA**
- vollständiger Produktcode: **OLED55B19LA.DEUQJP**
- Serie: **B1**, Modelljahr 2021
- Display: **55 Zoll**, 3840 × 2160, 120-Hz-Klasse
- Plattform: **webOS 6.0**
- Fertigung: **09/2021**
- Montage: **Polen**
- Netzversorgung: **AC 100–240 V, 50/60 Hz**
- Nenn-/Maximalangabe am Typenschild: **343 W**
- typische Leistungsaufnahme am Typenschild: **104 W**
- lokale Steuerung: WebSocket / SSAP
- sichere Verbindung: bevorzugt WSS Port 3001
- Kompatibilitäts-Fallback: WS Port 3000
- IR-Profil: LG NEC 32 Bit bei 38 kHz
- Wake-on-LAN: vorgesehen, benötigt TV-MAC und passende Standby-Einstellung

### LG-Anschlussprofil

- HDMI 1: `HDMI_1`, bis 4K/60
- HDMI 2: `HDMI_2`, bis 4K/60
- HDMI 3: `HDMI_3`, eARC/ARC, HDMI 2.1, bis 4K/120
- HDMI 4: `HDMI_4`, HDMI 2.1, bis 4K/120
- insgesamt vier HDMI- und drei USB-Anschlüsse

## Sony Receiver

- Hersteller: Sony
- Modell: **STR-DB870**
- Area Code: **CEL**
- Rückseitenkennung: **4-233-630-21 CEL**
- Netzversorgung: **AC 230 V, 50/60 Hz**
- Leistungsaufnahme: **230 W**
- Serie/Frontmarkierung: **QS**
- von Sony für CEL zugeordnete Fernbedienung: **RM-U305A**
- IR-Protokoll: Sony SIRC/SIRCS bei 40 kHz
- normaler SmartIR-Modus: AV1
- AV2: nur Diagnose im freien Rohcode-Labor

Die auf den Gerätefotos sichtbaren Seriennummern werden absichtlich nicht in diesem öffentlichen Repository, in der App oder in Diagnoseausgaben gespeichert.

## Analysierte LG-ThinQ-Version

- App-Label: LG ThinQ
- Paketname: `com.lgeha.nuts`
- Version Name: **5.1.32310**
- Version Code: **51002010**
- Architektur: ARM64
- APK-Typ: Split-APK
- Hauptdatei: `base.apk`
- Splits:
  - `split_config.arm64_v8a.apk`
  - `split_config.xxhdpi.apk`
- Sicherungsformat: App-Manager Version 5, TAR-Z, SHA-256, nicht verschlüsselt
- App-Datenverzeichnisse im bereitgestellten Export: keine

Die originale LG-App, proprietäre Ressourcen, Zertifikate, Cloud-Schlüssel und Kontodaten werden nicht in SmartIR übernommen oder im Repository veröffentlicht. Gesichert werden ausschließlich eigene Implementierungen und beschreibende Interoperabilitätsnotizen.

## App-Datensicherung

SmartIR bietet einen versionierten JSON-Export und -Import für portable Einstellungen. Gesichert werden:

- TV-IP beziehungsweise Hostname
- TV-MAC-Adresse
- Theme
- Tastenvibration
- Auto-Connect
- Sony-Modus beziehungsweise dessen Migration
- bekannter TV-Zertifikat-Fingerabdruck

Nicht exportiert wird der geheime webOS-Client-Key. Er bleibt im Android-Keystore und erfordert nach einer vollständigen Deinstallation eine erneute Kopplung. Android Auto Backup darf nur die nicht geheime Datei `smart_ir_settings.xml` übertragen; geheime Werte liegen getrennt in `smart_ir_secure.xml`.

## Projektidentität

- Repository: `SkallaHaze74-Pro/IRBloasster`
- App-Name: SmartIR
- Android-Paket: `com.skallahaze.irbloasster`
- Hauptbranch: `main`
- automatische Prüfung: Unit-Tests, Android-Lint und Debug-APK-Build

## Öffentliche und private Daten

### Öffentlich im Repository zulässig

- Modelle, Produktcodes und Area Codes
- Protokollnamen, Frequenzen, Befehlszuordnungen und Prüfergebnisse
- anonymisierte Build- und Fehlerberichte
- technische Rückseitenkennungen ohne Seriennummer
- eigene App-Quelltexte und eigene Grafiken

### Nicht öffentlich speichern

- Seriennummern
- WLAN-Passwörter
- private IP-/MAC-Zuordnungen aus einem konkreten Heimnetz, sofern nicht bewusst anonymisiert
- webOS-Client-Key
- Kontodaten und Cloud-Token
- vollständige Original-APKs oder proprietäre LG-/Sony-Ressourcen

## Pflege

Neue bestätigte Geräteangaben sollen zuerst hier und anschließend in der passenden Code- oder Testdokumentation ergänzt werden. Unbestätigte IR-Zuordnungen bleiben als Kandidaten gekennzeichnet, bis das reale Gerät reagiert hat.
