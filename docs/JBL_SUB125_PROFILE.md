# JBL Simply Cinema SUB125 – Profil, Steuerung und Klickdiagnose

## Bestätigte Gerätevariante

Die Fotos bestätigen:

- Hersteller: JBL
- Modell: `SUB125`
- System: `SCS125`
- Bauart: aktiver Bassreflex-Subwoofer
- Netz: AC 230 V, 50 Hz
- Leistungsaufnahme: 160 W
- Sicherung: 1 A / 250 V
- eingebauter Verstärker: 75 W RMS
- Tieftöner: 8 Zoll

Die sichtbare Seriennummer wird weder in der App noch im öffentlichen Repository gespeichert.

## Fernsteuerung

Der SUB125 besitzt keinen eigenen IR-Empfänger und keine eigene Fernbedienung. SmartIR steuert ihn daher nur indirekt über den Sony STR-DB870:

- Subwoofer +
- Subwoofer −
- Test Tone
- A.F.D.
- 2CH / OFF
- Receiver-Menü und Lautsprecherpegel

Der mechanische Regler `Subwoofer Level` am JBL bleibt die Grundeinstellung.

## Auto/On-Verhalten

Im Modus `AUTO` schaltet der Subwoofer bei anliegendem Audiosignal ein und nach ungefähr 20 Minuten ohne Signal wieder in Standby. Im Modus `ON` bleibt der Verstärker eingeschaltet, solange der Hauptschalter an ist.

Empfohlene Ausgangseinstellung:

```text
Auto/On: AUTO
Subwoofer Level: ungefähr mittig
Power: ON
```

Danach den Pegel möglichst am Sony feinjustieren.

## Klickgeräusch

Ein einzelnes Klacken unmittelbar beim Einschalten oder beim Aufwachen aus Auto-Standby kann durch den internen Netz-/Auto-On-Schaltvorgang verursacht werden und ist allein noch kein Fehler.

Nicht normal sind:

- mehrfaches oder rhythmisches Klackern
- ständiges Umschalten zwischen roter und grüner LED
- Ton setzt zusammen mit dem Klacken aus
- verbrannter Geruch
- ungewöhnlich starke Wärme
- Sicherung löst aus
- lautes mechanisches Anschlagen bei Bassimpulsen

In diesen Fällen:

1. Subwoofer ausschalten.
2. Netzstecker ziehen.
3. Cinch-/Lautsprecherkabel prüfen.
4. Nicht geöffnet weiterbetreiben; im Inneren liegt Netzspannung an und Kondensatoren können Ladung halten.
5. Von einer qualifizierten Audio-/Elektronikwerkstatt prüfen lassen.

## Sicherer Kurztest ohne Öffnen

1. Receiver ausschalten und Signalkabel am SUB125 abziehen.
2. JBL-Level niedrig bis mittig stellen.
3. `Auto/On` testweise auf `ON` stellen.
4. Hauptschalter einschalten.
5. Beobachten:
   - genau ein Klacken, grüne LED stabil: wahrscheinlich normaler Einschaltvorgang
   - wiederholtes Klackern oder LED-Flackern ohne Eingangssignal: Netzteil/Relais/Auto-On-Schaltung prüfen lassen
6. Danach wieder ausschalten, Kabel anschließen und `AUTO` testen.

Wenn das Klacken nur im `AUTO`-Modus bei sehr leisem Basssignal auftritt, kann die Auto-Erkennung an der Schaltschwelle liegen. Dann den Subwoofer-Ausgangspegel am Sony etwas erhöhen und den mechanischen JBL-Level-Regler entsprechend etwas zurücknehmen.

## Quellen

- JBL SUB125/125A Service Manual: Bedienfunktionen, Auto/On, LED-Zustände, 75-W-RMS-Verstärker, 8-Zoll-Treiber und 1-A-Sicherung der europäischen 230-V-Version.
- JBL SCS125/SUB125 Bedienungsunterlagen: Auto-Einschalten bei Signal und Standby nach ungefähr 20 Minuten ohne Signal.

Die App enthält keine JBL-Firmware, keine proprietären Grafiken und keine Seriennummer.
