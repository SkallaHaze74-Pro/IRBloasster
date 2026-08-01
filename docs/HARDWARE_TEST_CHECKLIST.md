# SmartIR – Hardware-Testcheckliste

Diese Checkliste trennt Software-/Build-Prüfungen von den Funktionen, die nur am realen **LG OLED55B19LA.DEUQJP** und **Sony STR-DB870 CEL** bestätigt werden können.

## 1. Installation und Datensicherung

- [ ] GitHub-Actions-Artefakt `SmartIR-v1.1.6-debug` laden
- [ ] vor einer möglichen Deinstallation in der alten App **Setup → Backup & Datenübertragung → Exportieren** verwenden
- [ ] APK zunächst als Update installieren beziehungsweise mit `adb install -r` aktualisieren
- [ ] App startet ohne Absturz
- [ ] Paket bleibt `com.skallahaze.irbloasster`
- [ ] vorhandene Theme-, TV-IP-, MAC- und Auto-Connect-Einstellungen bleiben beim normalen Update erhalten
- [ ] bei einer Neuinstallation das JSON-Backup importieren
- [ ] LG-TV nach kompletter Deinstallation bei Bedarf einmal neu koppeln
- [ ] Hell-/Dunkelmodus funktioniert
- [ ] Xiaomi erkennt den integrierten IR-Blaster
- [ ] IR-Tastendruck erzeugt keinen Berechtigungsfehler

### 1.1 Backup-Funktion

- [ ] JSON-Backup über den Android-Dateidialog exportieren
- [ ] Datei enthält TV-IP, TV-MAC, Theme, Haptik, Auto-Connect und Sony-Modus
- [ ] Datei enthält keinen webOS-Client-Key
- [ ] Backup in einer frischen Installation importieren
- [ ] TV-IP und MAC erscheinen wieder im Setup
- [ ] Theme und Tastenvibration werden wiederhergestellt
- [ ] Hinweis zur erneuten LG-Kopplung erscheint, wenn vorher ein Client-Key vorhanden war
- [ ] vorhandenes funktionierendes Pairing wird durch einen Import nicht überschrieben

## 2. LG OLED55B19LA.DEUQJP per IR

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
- [ ] ältere Pairingwerte werden automatisch in `smart_ir_secure.xml` migriert
- [ ] Zertifikat-Fingerabdruck wird angezeigt
- [ ] Neustart der App verbindet automatisch erneut
- [ ] Trennen funktioniert
- [ ] Pairing zurücksetzen löscht Client-Key und Fingerabdruck

### 3.1 Exaktes Anschlussprofil

Das Gerät ist als `OLED55B19LA.DEUQJP` gespeichert. Die Anschlussbeschriftung bestätigt HDMI 3 als eARC/ARC und HDMI 3/4 als 4K/120-Ports.

- [ ] Gerätekarte zeigt Produktcode `OLED55B19LA.DEUQJP`
- [ ] HDMI 1 schaltet auf `HDMI_1`
- [ ] HDMI 2 schaltet auf `HDMI_2`
- [ ] HDMI 3 eARC schaltet auf `HDMI_3`
- [ ] HDMI 4 4K/120 schaltet auf `HDMI_4`
- [ ] vom TV geladene Eingangsliste erscheint zusätzlich
- [ ] angeschlossene Eingänge werden richtig als verbunden markiert
- [ ] „TV-Status, Apps und Eingänge neu laden“ aktualisiert alle Listen

### 3.2 Live-Status, Apps und SSAP

- [ ] Lautstärke wird gelesen
- [ ] Mute wird gelesen und geschaltet
- [ ] Power-Status wird gelesen
- [ ] aktuelle App beziehungsweise aktueller Eingang wird angezeigt
- [ ] installierte Apps werden geladen
- [ ] angezeigte App-Schnellstarts starten die richtige TV-App
- [ ] HDMI-/AV-Eingänge werden geladen und umgeschaltet
- [ ] Sender + / −
- [ ] Mediensteuerung
- [ ] Texteingabe und Enter

### 3.3 Magic Remote

- [ ] Pointer-Socket meldet „bereit“
- [ ] D-Pad-Tasten funktionieren
- [ ] Touchpad-Bewegung ist sinnvoll skaliert
- [ ] Tippen/Klick funktioniert
- [ ] Scrollen funktioniert

### 3.4 Wake-on-LAN

- [ ] TV-MAC-Adresse speichern
- [ ] „TV wecken“ im normalen Standby testen
- [ ] „TV wecken“ nach längerem Standby testen
- [ ] TV-Einstellung „TV über WLAN/Mobilgerät einschalten“ bei Bedarf aktivieren
- [ ] automatisches Verbinden nach dem Aufwecken prüfen
- [ ] IR-Fallback prüfen, wenn Wake-on-LAN nicht verfügbar ist

## 4. Sony STR-DB870 CEL – Geräteprofil praktisch prüfen

Die Rückseitenfotos bestätigen:

```text
Modell: STR-DB870
Area Code: CEL
Rückseitenkennung: 4-233-630-21 CEL
Originalfernbedienung laut Sony: RM-U305A
Normaler Command Mode: AV1 fest
```

Die Seriennummer wird nicht dokumentiert. Die vollständige Codeübersicht steht unter [`SONY_STR_DB870_CODES.md`](SONY_STR_DB870_CODES.md).

### 4.1 Schnelltest in AV1

- [ ] Power Toggle
- [ ] Power On
- [ ] Power Off
- [ ] Lautstärke +
- [ ] Lautstärke −
- [ ] Mute
- [ ] Sleep

### 4.2 Eingänge

- [ ] TV/SAT
- [ ] DVD/LD – Hauptcode 125
- [ ] VIDEO 1
- [ ] VIDEO 2
- [ ] VIDEO 3
- [ ] CD/SACD
- [ ] TUNER
- [ ] MD/TAPE – Hauptcode 105
- [ ] PHONO
- [ ] AUX
- [ ] MULTI/2CH A.DIRECT
- [ ] MULTI CH diskret

Falls nur DVD/LD beziehungsweise MD/TAPE nicht reagieren:

- [ ] DVD/LD (alt) – Command 107 testen
- [ ] TAPE/MD (alt) – Command 35 testen

### 4.3 Klangfeld und Receiver-Betriebsarten

- [ ] A.F.D.
- [ ] 2CH/OFF
- [ ] Mode +
- [ ] Mode −
- [ ] Input Mode
- [ ] Night Mode
- [ ] EQ/Tone
- [ ] Audio Split
- [ ] Subwoofer +
- [ ] Subwoofer −
- [ ] Test Tone

### 4.4 Receiver-Menü und Tuner

- [ ] Main Menu
- [ ] Hoch / Runter / Links / Rechts
- [ ] Enter/Exec
- [ ] Preset + / −
- [ ] Tuning + / −
- [ ] FM Mode
- [ ] Direct Tuning

### 4.5 AV2 nur im Rohcode-Labor

- [ ] Power AV1 testen: Command 21, Adresse 16, 12 Bit
- [ ] optional Power AV2-Diagnose testen: Command 21, Adresse 48, 15 Bit
- [ ] Ergebnisse ausdrücklich als Diagnose dokumentieren

## 5. Szenen

- [ ] Fernsehen: TV wecken, Sony einschalten, TV/SAT wählen
- [ ] Heimkino: TV wecken, Sony einschalten, DVD/LD wählen
- [ ] Musik: Sony einschalten, CD/SACD wählen, 2CH/OFF senden
- [ ] Alles aus
- [ ] Verzögerungen zwischen TV und Sony sind ausreichend
- [ ] Netzwerkbefehle verwenden bei Verbindung den TV
- [ ] IR-/Wake-on-LAN-Fallback funktioniert

## 6. Fehlerbericht

```text
App-Version:
Android-Version:
Smartphone:
Gerät: LG OLED55B19LA.DEUQJP oder Sony STR-DB870 CEL
TV-IP (letztes Oktett darf geschwärzt werden):
Verbindungsstatus:
betroffene Taste/Funktion:
Command / Adresse / Bits, falls Rohcode:
IR oder webOS:
Anzeige im Diagnosebereich:
letzte geschwärzte SSAP-Antwort:
Backup exportiert: ja/nein
```

Client-Key, WLAN-Passwörter, Kontodaten und Geräte-Seriennummern niemals in einen öffentlichen Fehlerbericht kopieren.
