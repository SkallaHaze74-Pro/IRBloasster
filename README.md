# SmartIR – Living Room Controller

Eine eigenständige Android-Fernbedienung für das Wohnzimmer. SmartIR steuert den **LG OLED55B19LA** und den **Sony STR-DB870** über den integrierten IR-Blaster des Xiaomi 15T Pro. Der LG TV kann zusätzlich lokal über **webOS WebSocket/SSAP** verbunden werden.

## Funktionen

- ThinQ-inspirierte, eigenständig entwickelte Oberfläche mit Hell-/Dunkelmodus
- Vollständige LG-TV-Fernbedienung: Power, Lautstärke, Kanal, Navigation, Home, Eingang, Medien, Ziffern und Farbtasten
- Lokale LG-webOS-Kopplung ohne Cloudkonto
- Magic-Remote-Touchpad, Scrollen und Bildschirmtastatur über webOS
- webOS-Liveinformationen wie Lautstärke, aktive App, Modell und Power-Status
- Sony STR-DB870: Power, Lautstärke, Eingänge, Sound Fields, Receiver-Menü, Subwoofer und Tuner
- Sony Command Mode AV1 und AV2
- Szenen: Fernsehen, Heimkino, Musik und Alles aus
- Protokoll-Labor für eigene NEC-, SIRC- und SSAP-Tests
- Automatischer GitHub-Actions-Build mit installierbarer Debug-APK

## Unterstützte Protokolle

- LG TV: NEC 32 Bit bei 38 kHz
- Sony Receiver: SIRC 12 Bit (AV1) oder 15 Bit (AV2) bei 40 kHz
- LG Netzwerk: webOS WebSocket auf Port 3000 mit SSAP-Befehlen

## Bauen in Termux

```bash
pkg install openjdk-17 git

git clone https://github.com/SkallaHaze74-Pro/IRBloasster.git
cd IRBloasster
chmod +x gradlew
./gradlew --no-daemon assembleDebug
```

Die APK liegt danach hier:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## LG TV über WLAN verbinden

1. TV und Smartphone müssen im selben Heimnetz sein.
2. In SmartIR unter **Setup** die IP-Adresse des LG TVs eintragen.
3. **Verbinden** drücken.
4. Die Kopplungsabfrage am Fernseher bestätigen.
5. Der webOS-Client-Key wird lokal auf dem Smartphone gespeichert.

IR funktioniert weiterhin als Rückfallebene, wenn keine Netzwerkverbindung besteht.

## Sony AV1 / AV2

Der STR-DB870 verwendet normalerweise **AV1**. Reagiert der Receiver nicht und wurde sein Command Mode geändert, in SmartIR unter Setup **AV2** auswählen.

## Projektstruktur

```text
app/src/main/java/com/skallahaze/irbloasster/
├── MainActivity.kt
├── data/SettingsRepository.kt
├── ir/
│   ├── ConsumerIrSender.kt
│   ├── LG_OLED55B1.kt
│   ├── Nec.kt
│   ├── Sirc.kt
│   └── Sony_STR_DB870.kt
├── ui/
│   ├── HomeScreen.kt
│   ├── SettingsScreen.kt
│   ├── SonyRemoteScreen.kt
│   ├── TvRemoteScreen.kt
│   ├── UiComponents.kt
│   └── theme/Theme.kt
└── webos/WebOsClient.kt
```

SmartIR ist ein unabhängiges Open-Source-Projekt und enthält keine LG- oder Sony-Appdaten, Kontodaten oder proprietären Schriftdateien.
