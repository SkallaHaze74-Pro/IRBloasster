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

- Hersteller: LG
- Modell: **OLED55B19LA**
- Plattform: webOS
- lokale Steuerung: WebSocket / SSAP
- sichere Verbindung: bevorzugt WSS Port 3001
- Kompatibilitäts-Fallback: WS Port 3000
- IR-Profil: LG NEC 32 Bit bei 38 kHz
- Wake-on-LAN: vorgesehen, benötigt TV-MAC und passende Standby-Einstellung

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

Die auf den Gerätefotos sichtbare Seriennummer wird absichtlich nicht in diesem öffentlichen Repository, in der App oder in Diagnoseausgaben gespeichert.

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

## Projektidentität

- Repository: `SkallaHaze74-Pro/IRBloasster`
- App-Name: SmartIR
- Android-Paket: `com.skallahaze.irbloasster`
- Hauptbranch: `main`
- automatische Prüfung: Unit-Tests, Android-Lint und Debug-APK-Build

## Öffentliche und private Daten

### Öffentlich im Repository zulässig

- Modelle und Area Codes
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
