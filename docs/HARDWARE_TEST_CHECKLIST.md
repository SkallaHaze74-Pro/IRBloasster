# SmartIR – Hardware-Testcheckliste

Diese Checkliste trennt Software-/Build-Prüfungen von den Funktionen, die nur am realen LG OLED55B19LA und Sony STR-DB870 bestätigt werden können.

## 1. Installation

- [ ] GitHub-Actions-Artefakt `SmartIR-v1.1-debug` laden
- [ ] APK installieren beziehungsweise mit `adb install -r` aktualisieren
- [ ] App startet ohne Absturz
- [ ] Paket bleibt `com.skallahaze.irbloasster`
- [ ] Hell-/Dunkelmodus funktioniert
- [ ] Xiaomi erkennt den integrierten IR-Blaster

## 2. LG-TV per IR

Handy mit der IR-Sendediode auf den Fernseher richten.

- [ ] Power Toggle
- [ ] Power On
- [ ] Power Off
- [ ] Lautstärke + / −
- [ ] Mute
- [ ] Kanal + / −
- [ ] Eingang
- [ ] D-Pad und OK
- [ ] Home / Zurück / Einstellungen / Info / Guide / Exit
- [ ] Play / Pause / Stop / Spulen
- [ ] Ziffern 0–9
- [ ] Rot / Grün / Gelb / Blau

Abweichende Tasten im Protokoll-Labor notieren und erst nach Bestätigung in das feste Geräteprofil übernehmen.

## 3. LG-TV im Netzwerk

TV und Smartphone müssen im selben lokalen Netzwerk sein.

- [ ] Unter Setup „TV suchen“ drücken
- [ ] LG OLED55B19LA wird per SSDP angezeigt
- [ ] alternativ TV-IP manuell eintragen
- [ ] „Verbinden“ drücken
- [ ] Pairing-Abfrage am TV bestätigen
- [ ] Status wechselt auf verbunden
- [ ] Client-Key wird Android-Keystore-verschlüsselt gespeichert
- [ ] Zertifikat-Fingerabdruck wird angezeigt
- [ ] Neustart der App verbindet automatisch erneut
- [ ] Trennen funktioniert
- [ ] Pairing zurücksetzen löscht Client-Key und Fingerabdruck

### Live-Status und SSAP

- [ ] Lautstärke wird gelesen
- [ ] Mute wird gelesen und geschaltet
- [ ] Power-Status wird gelesen
- [ ] aktuelle App beziehungsweise aktueller Eingang wird angezeigt
- [ ] installierte Apps werden geladen und können gestartet werden
- [ ] HDMI-/AV-Eingänge werden geladen und umgeschaltet
- [ ] Sender + / −
- [ ] Mediensteuerung
- [ ] Texteingabe und Enter

### Magic Remote

- [ ] Pointer-Socket meldet „bereit“
- [ ] D-Pad-Tasten funktionieren
- [ ] Touchpad-Bewegung ist sinnvoll skaliert
- [ ] Tippen/Klick funktioniert
- [ ] Scrollen funktioniert

### Wake-on-LAN

- [ ] TV-MAC-Adresse speichern
- [ ] „TV wecken“ im normalen Standby testen
- [ ] „TV wecken“ nach längerem Standby testen
- [ ] TV-Einstellung „TV über WLAN/Mobilgerät einschalten“ bei Bedarf aktivieren
- [ ] automatisches Verbinden nach dem Aufwecken prüfen
- [ ] IR-Fallback prüfen, wenn Wake-on-LAN nicht verfügbar ist

## 4. Sony STR-DB870 – SIRC-Testprofil

Das Sony-Profil ist bis zum echten Gerätetest ausdrücklich ein Kandidatenprofil.

### AV1

- [ ] Command Mode AV1 auswählen
- [ ] Power Toggle
- [ ] Power On / Off
- [ ] Lautstärke + / −
- [ ] Mute
- [ ] TV/SAT
- [ ] DVD/LD
- [ ] Video 1 / 2 / 3
- [ ] CD/SACD
- [ ] Tuner
- [ ] Tape/MD
- [ ] Phono
- [ ] Multi Channel
- [ ] 2CH Stereo
- [ ] Sound Field + / −
- [ ] Subwoofer + / −
- [ ] Receiver-Menü und Select/Enter
- [ ] Tuner Preset / Tuning

### AV2

Nur testen, wenn AV1 nicht reagiert oder der Receiver auf AV2 eingestellt wurde.

- [ ] Command Mode AV2 auswählen
- [ ] Power, Lautstärke und Mute erneut testen
- [ ] Eingänge testen
- [ ] funktionierenden Modus dokumentieren

### Rohcode-Labor

- [ ] bei einer nicht reagierenden Taste Command, Adresse und 12/15/20 Bit variieren
- [ ] funktionierende Kombination notieren
- [ ] keine unbestätigten Werte als verifiziert markieren

## 5. Szenen

- [ ] Fernsehen
- [ ] Heimkino
- [ ] Musik
- [ ] Alles aus
- [ ] Verzögerungen zwischen TV und Sony sind ausreichend
- [ ] Netzwerkbefehle verwenden bei Verbindung den TV
- [ ] IR-/Wake-on-LAN-Fallback funktioniert

## 6. Fehlerbericht

Bei einem Problem bitte festhalten:

```text
App-Version:
Android-Version:
Gerät:
TV-IP (letztes Oktett darf geschwärzt werden):
Verbindungsstatus:
betroffene Taste/Funktion:
AV1 oder AV2:
IR oder webOS:
Anzeige im Diagnosebereich:
letzte geschwärzte SSAP-Antwort:
```

Client-Key, Zertifikate, WLAN-Passwörter und Kontodaten niemals in einen öffentlichen Fehlerbericht kopieren.
