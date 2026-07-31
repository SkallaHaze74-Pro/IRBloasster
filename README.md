# Living Room Controller

Android-Fernbedienung für das Wohnzimmer mit einem **Xiaomi 15T Pro**, einem **LG OLED55B19LA** und einem **Sony HT-RT3**.

Der Paketname bleibt `com.skallahaze.irbloasster`, damit spätere Builds die vorhandene App aktualisieren können. Der sichtbare App-Name ist **Living Room Controller**.

## Funktionen

### LG TV

- Infrarot-Fallback mit NEC-Profil für Power, Lautstärke, Sender, Navigation und Medien
- lokale webOS-Verbindung per WebSocket/SSAP
- Pairing mit Bestätigung am Fernseher und gespeichertem Client-Schlüssel
- Lautstärke- und Mute-Status
- HDMI-/Eingangsliste und direkte Auswahl
- installierte TV-Apps auflisten und starten
- Magic-Remote-Touchpad und Bildschirmtexteingabe
- SSDP-Suche im lokalen WLAN
- Wake-on-LAN über eine hinterlegte MAC-Adresse

### Sony HT-RT3

- Sony-SIRC mit 12-, 15- und 20-Bit-Encoder
- Profiltester mit einem 15-Bit-Soundbar-Kandidaten und 12-Bit-Fallback
- Power, Lautstärke, Mute, Eingang und Klangfunktionen
- langes Drücken für wiederholte Lautstärkebefehle

### Gemeinsame Funktionen

- Szenen: Fernsehen, Heimkino, Gaming, Musik und Alles aus
- eigenes Material-3-Premium-Design in Hell und Dunkel
- sichtbarer Verbindungs- und Gerätestatus
- haptische Rückmeldung
- Diagnoseprotokoll und kopierbarer Export
- IR-Labor für eigene NEC- und SIRC-Hex-Codes
- kein Absturz, wenn ein Android-Gerät keinen IR-Blaster besitzt

## Datenschutz und Herkunft

Die App verwendet keine LG-Kontodaten, keine Cloud-Anmeldung und keine kopierten proprietären LG-Ressourcen. Die Bedienstruktur ist eigenständig umgesetzt. Die lokalen webOS-Befehlsbereiche wurden durch Analyse der vom Eigentümer bereitgestellten LG-ThinQ-App und durch dokumentierte webOS-Schnittstellen nachvollzogen.

## Bauen

```bash
./gradlew testDebugUnitTest assembleDebug
```

Die Debug-APK liegt danach unter:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions baut bei jedem Push automatisch eine frische Debug-APK als Artifact.

## Erste Einrichtung

1. Handy und LG TV ins gleiche WLAN bringen.
2. In der App unter Einstellungen die TV-IP eintragen oder über **Suchen** finden.
3. **Verbinden** drücken und die Kopplung am Fernseher bestätigen.
4. Für Wake-on-LAN zusätzlich die TV-MAC-Adresse speichern.
5. Beim Sony HT-RT3 zuerst das 15-Bit-Profil mit Power, Lauter und Stumm testen. Falls es nicht reagiert, das 12-Bit-Fallback wählen.

## Hinweis zu Sony-Codes

Das HT-RT3-Profil ist als testbarer Kandidat gekennzeichnet, weil Sony mehrere SIRC-Gerätefamilien verwendet. Die App protokolliert jeden Test und lässt Profile sowie einzelne Rohcodes ohne Neubau der App prüfen.
