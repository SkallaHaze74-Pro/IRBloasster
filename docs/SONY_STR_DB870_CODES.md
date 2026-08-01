# Sony STR-DB870 CEL – SIRC-Profil und Teststand

## Exakte Gerätebestätigung

Die neuen Fotos bestätigen nicht nur das Modell, sondern auch die konkrete Gerätevariante:

- Modell: **Sony STR-DB870**
- Area Code: **CEL**
- Rückseitenkennung: **4-233-630-21 CEL**
- Netzversorgung: **230 V AC, 50/60 Hz**
- Leistungsaufnahme: **230 W**
- Vorderseite: **QS**-Kennzeichnung

Die sichtbare Seriennummer wird bewusst weder in der App noch in diesem öffentlichen Repository gespeichert oder veröffentlicht.

Die CE-, N50- und QS-Markierungen bestätigen beziehungsweise beschreiben die konkrete Ausführung, liefern nach derzeitigem Kenntnisstand aber keine zusätzliche IR-Codefamilie. Für die Fernbedienungszuordnung ist vor allem der Sony-Area-Code **CEL** entscheidend.

## Originalfernbedienung und Command Mode

Sonys offizielle Bedienungsanleitung ordnet dem **STR-DB870 mit Area Code CEL** die Fernbedienung **RM-U305A** zu. Die zuvor ebenfalls betrachtete RM-PP505 gehört zu anderen Länder-/Regionsvarianten.

Die Anleitung kennzeichnet außerdem die Receiver-Einstellung **COMMAND MODE** mit der Ausnahme „Except for STR-DB870 area code CEL“. Für genau dieses Gerät ist der normale Receiver-Command-Mode daher nicht umschaltbar.

SmartIR 1.1.4 setzt deshalb:

- normaler Sony-Betrieb: **AV1 fest**
- kein AV1/AV2-Schalter mehr in der Sony-Fernbedienung
- alte gespeicherte AV2-Einstellungen werden automatisch auf AV1 zurückgesetzt
- AV2 bleibt ausschließlich im freien SIRC-Rohcode-Labor für technische Diagnose erhalten

## Protokoll

- Sony **SIRC / SIRCS**
- Trägerfrequenz: **40 kHz**
- Übertragung: drei identische Frames
- Basis-Receiverfamilie: Geräteadresse 16, 12 Bit
- Tunerfamilie: Geräteadresse 13, 12 Bit
- moderne DSP-/Menüfamilie: Geräteadresse 144, 15 Bit

Wichtig: Nicht jeder Befehl des Receivers ist ein 12-Bit-Code. Die neuere DSP-/Menüfamilie verwendet bereits in AV1 die Geräteadresse 144 und damit 15 Bit.

## Quellenlage und Vertrauensstufen

Das Profil kombiniert:

1. **Sony-Bedienungsanleitung und Supportseite** – Modell, Area-Code-Zuordnung, RM-U305A, vorhandene Funktionen und die CEL-Ausnahme beim COMMAND MODE.
2. **Gelernte RM-PP505-Signale** aus dem offenen `hifiremote/deviceupgrades`-Datensatz – weiterhin nützliche Kandidaten für Eingänge und die moderne DSP-/Menüfamilie, aber nicht als Beweis für die mitgelieferte CEL-Fernbedienung.
3. **Bekannte Sony-Receiver-SIRC-Zuordnungen** – Ergänzungen und ausdrücklich gekennzeichnete Alternativen.

Auch gut belegte Zahlenwerte gelten erst dann als hardwarebestätigt, wenn der konkrete STR-DB870 CEL reagiert hat.

## Hauptprofil

### Power und Lautstärke

| Funktion | Command | AV1-Adresse | Bits |
|---|---:|---:|---:|
| Power Toggle | 21 | 16 | 12 |
| Power On | 46 | 16 | 12 |
| Power Off | 47 | 16 | 12 |
| Volume + | 18 | 16 | 12 |
| Volume − | 19 | 16 | 12 |
| Mute | 20 | 16 | 12 |
| Sleep | 96 | 16 | 12 |

### Eingänge

| Funktion | Command | AV1-Adresse | Hinweis |
|---|---:|---:|---|
| PHONO | 32 | 16 | Sony-Receiver-Zuordnung |
| TUNER | 33 | 16 | Sony-Receiver-Zuordnung |
| VIDEO 1 | 34 | 16 | RM-PP505-gelernter Kandidat |
| VIDEO 2 | 30 | 16 | RM-PP505-gelernter Kandidat |
| VIDEO 3 | 66 | 16 | RM-PP505-gelernter Kandidat |
| TV/SAT | 106 | 16 | RM-PP505-gelernter Kandidat |
| DVD/LD | 125 | 16 | Hauptkandidat; 107 bleibt als Alternative |
| MD/TAPE | 105 | 16 | Hauptkandidat; 35 bleibt als Alternative |
| CD/SACD | 37 | 16 | RM-PP505-gelernter Kandidat |
| AUX | 29 | 16 | RM-PP505-gelernter Kandidat |
| MULTI/2CH A.DIRECT | 73 | 16 | Funktionskandidat passend zur RM-U305A-Tastenfamilie |
| MULTI CH diskret | 114 | 16 | zusätzlicher Sony-Receiver-Kandidat |

### DSP, Klang und Receiver-Menü

Diese Gruppe verwendet in AV1 die Adresse **144** und 15 Bit.

| Funktion | Command | AV1-Adresse |
|---|---:|---:|
| A.F.D. | 71 | 144 |
| 2CH / OFF | 65 | 144 |
| Mode + | 110 | 144 |
| Mode − | 111 | 144 |
| Input Mode | 48 | 144 |
| Night Mode | 32 | 144 |
| EQ / Tone | 76 | 144 |
| Audio Split | 100 | 144 |
| Main Menu | 119 | 144 |
| Menü hoch | 120 | 144 |
| Menü runter | 121 | 144 |
| Menü links | 122 | 144 |
| Menü rechts | 123 | 144 |

`Enter/Exec` verwendet Command 12 auf der Basisadresse 16. `Test Tone` wird zuerst als Command 74 auf Adresse 16 getestet; eine Adresse-144-Variante ist zusätzlich als Fallback vorhanden.

### Tuner

| Funktion | Command | AV1-Adresse | Bits |
|---|---:|---:|---:|
| Preset + | 16 | 13 | 12 |
| Preset − | 17 | 13 | 12 |
| Tuning + | 18 | 13 | 12 |
| Tuning − | 19 | 13 | 12 |
| FM Mode | 33 | 13 | 12 |
| Direct Tuning | 83 | 13 | 12 |

## Alternative Codes in der App

Diese Codes werden getrennt angezeigt und nicht automatisch statt des Hauptprofils verwendet:

| Alternative | Command | AV1-Adresse | Zweck |
|---|---:|---:|---|
| DVD/LD alt | 107 | 16 | ältere Sony-Receiver-Zuordnung |
| TAPE/MD alt | 35 | 16 | generische ältere Zuordnung |
| 2CH Legacy | 8 | 18 | ältere Sound-Field-Familie |
| Sound Field + Legacy | 54 | 18 | ältere Sound-Field-Familie |
| Sound Field − Legacy | 55 | 18 | ältere Sound-Field-Familie |
| Effect Off Legacy | 93 | 18 | ältere Sound-Field-Familie |
| Woofer + Legacy | 86 | 18 | ältere Sound-Field-Familie |
| Woofer − Legacy | 87 | 18 | ältere Sound-Field-Familie |
| Test Tone Device 144 | 74 | 144 | alternative moderne Geräteadresse |

## AV2 nur als Rohcode-Diagnose

Die normale Fernbedienung und alle Szenen verwenden beim CEL-Gerät AV1. Für einen kontrollierten Protokolltest kann das Labor weiterhin eine rechnerische AV2-Variante senden, etwa:

```text
Power AV1: Command 21, Adresse 16, 12 Bit
Power AV2-Diagnose: Command 21, Adresse 48, 15 Bit
Main Menu AV1: Command 119, Adresse 144, 15 Bit
Main Menu AV2-Diagnose: Command 119, Adresse 176, 15 Bit
```

Diese AV2-Diagnose ist kein normaler Gerätemodus des fotografierten CEL-Receivers.

## Empfohlene Hardwareprüfung

1. Smartphone direkt auf das IR-Empfangsfenster an der Vorderseite richten.
2. Power Toggle, Volume +, Volume − und Mute testen.
3. TV/SAT, DVD/LD, CD/SACD und TUNER testen.
4. A.F.D., 2CH/OFF und Mode +/− testen.
5. Main Menu und Pfeiltasten testen.
6. Reagiert nur eine einzelne Taste nicht, die gleichnamige Alternative testen.
7. AV2 nur im Rohcode-Labor untersuchen, falls ein technischer Vergleich nötig ist.
8. Funktionierende beziehungsweise falsche Tasten dokumentieren, damit das Profil ohne Raten finalisiert werden kann.

## Quellen

- Sony Support, STR-DB870: regional unterschiedliche mitgelieferte Fernbedienungen.
- Sony STR-DB870/DB1070 Operating Instructions: CEL-Zuordnung zur RM-U305A, Funktionsumfang und Ausnahme beim Receiver-COMMAND-MODE.
- `hifiremote/deviceupgrades`: gelernte RM-PP505-Signale der eng verwandten Sony-Receiver-Generation.
- Sony-Receiver-SIRC-Datenbank von HiFi-Remote: Gerätefamilien 13, 16, 18 und 144 sowie diagnostische +32-Varianten.

Die App enthält nur rekonstruierte numerische Interoperabilitätszuordnungen. Es werden keine Sony-Handbuchseiten, proprietären Grafiken, Original-Firmware oder personenbezogenen Gerätedaten ins Repository übernommen.
