# SmartIR – Hardware-Testcheckliste

Diese Checkliste trennt Software-/Build-Prüfungen von den Funktionen, die nur am realen **LG OLED55B19LA** und **Sony STR-DB870** bestätigt werden können.

## 1. Installation

- [ ] GitHub-Actions-Artefakt `SmartIR-v1.1.3-debug` laden
- [ ] APK installieren beziehungsweise mit `adb install -r` aktualisieren
- [ ] App startet ohne Absturz
- [ ] Paket bleibt `com.skallahaze.irbloasster`
- [ ] Hell-/Dunkelmodus funktioniert
- [ ] Xiaomi erkennt den integrierten IR-Blaster
- [ ] IR-Tastendruck erzeugt keinen Berechtigungsfehler

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

## 4. Sony STR-DB870 – Modell bestätigt, Codes praktisch prüfen

Das Typenschild bestätigt den **STR-DB870**. Die Seriennummer wird nicht dokumentiert. Sony nennt je nach Region die Fernbedienung RM-U305A oder RM-PP505. AV1 ist die Werkseinstellung.

Die vollständige Codeübersicht steht unter [`SONY_STR_DB870_CODES.md`](SONY_STR_DB870_CODES.md).

### 4.1 Schnelltest in AV1

Smartphone direkt auf das IR-Empfangsfenster an der Vorderseite des Receivers richten.

- [ ] Command Mode **AV1** auswählen
- [ ] Power Toggle
- [ ] Power On
- [ ] Power Off
- [ ] Lautstärke +
- [ ] Lautstärke −
- [ ] Mute
- [ ] Sleep

Wenn diese Basisgruppe vollständig reagiert, AV1 beibehalten. AV2 muss dann nicht getestet werden.

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

Diese Hauptcodes verwenden die moderne Sony-Geräteadresse 144 und 15 Bit bereits in AV1.

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

Falls eine einzelne Klangtaste nicht reagiert, die gleichnamige Legacy-Variante im Abschnitt „Alternative Sony-Codes“ testen.

### 4.4 Receiver-Menü

- [ ] Main Menu öffnet das Receiver-Menü
- [ ] Hoch
- [ ] Runter
- [ ] Links
- [ ] Rechts
- [ ] Enter/Exec
- [ ] längerer Tastendruck beziehungsweise Wiederholung ist ausreichend

### 4.5 Tuner

- [ ] Preset +
- [ ] Preset −
- [ ] Tuning +
- [ ] Tuning −
- [ ] FM Mode
- [ ] Direct Tuning

### 4.6 AV2 nur bei Bedarf

Nur testen, wenn die Basisgruppe in AV1 komplett ohne Reaktion bleibt oder der Receiver nachweislich auf AV2 umgestellt wurde.

- [ ] Command Mode AV2 auswählen
- [ ] Power, Lautstärke und Mute erneut testen
- [ ] TV/SAT und DVD/LD testen
- [ ] A.F.D. und Main Menu testen
- [ ] funktionierenden Modus dokumentieren

AV2 verwendet bei diesem Profil die jeweilige AV1-Geräteadresse plus 32 und einen 15-Bit-SIRC-Frame.

### 4.7 Rohcode-Labor

- [ ] bei einer nicht reagierenden Taste Command, Adresse und 12/15/20 Bit variieren
- [ ] funktionierende Kombination exakt notieren
- [ ] App-Fehlermeldung beziehungsweise letzte Aktion notieren
- [ ] keine unbestätigten Werte als verifiziert markieren

Beispiele:

```text
Power AV1: Command 21, Adresse 16, 12 Bit
Power AV2: Command 21, Adresse 48, 15 Bit
Main Menu AV1: Command 119, Adresse 144, 15 Bit
Main Menu AV2: Command 119, Adresse 176, 15 Bit
```

## 5. Szenen

- [ ] Fernsehen: TV wecken, Sony einschalten, TV/SAT wählen
- [ ] Heimkino: TV wecken, Sony einschalten, DVD/LD wählen
- [ ] Musik: Sony einschalten, CD/SACD wählen, 2CH/OFF senden
- [ ] Alles aus
- [ ] Verzögerungen zwischen TV und Sony sind ausreichend
- [ ] Netzwerkbefehle verwenden bei Verbindung den TV
- [ ] IR-/Wake-on-LAN-Fallback funktioniert

## 6. Fehlerbericht

Bei einem Problem bitte festhalten:

```text
App-Version:
Android-Version:
Smartphone:
Gerät: LG OLED55B19LA oder Sony STR-DB870
TV-IP (letztes Oktett darf geschwärzt werden):
Verbindungsstatus:
betroffene Taste/Funktion:
AV1 oder AV2:
Command / Adresse / Bits, falls Rohcode:
IR oder webOS:
Anzeige im Diagnosebereich:
letzte geschwärzte SSAP-Antwort:
```

Client-Key, Zertifikate, WLAN-Passwörter, Kontodaten und die Seriennummer des Receivers niemals in einen öffentlichen Fehlerbericht kopieren.
