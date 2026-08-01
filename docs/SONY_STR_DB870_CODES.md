# Sony STR-DB870 – SIRC-Profil und Teststand

## Gerätebestätigung

Das Typenschildfoto bestätigt das Modell **Sony STR-DB870**. Die sichtbare Seriennummer wird bewusst weder in der App noch in diesem Repository gespeichert oder veröffentlicht.

Sony nennt für den STR-DB870 je nach Verkaufsregion zwei mitgelieferte Fernbedienungen:

- **RM-U305A**
- **RM-PP505**

Das fotografierte Gerät ist eine 230-V-Ausführung und damit sehr wahrscheinlich eine europäische Variante. Der genaue Sony-Area-Code ist auf dem Foto jedoch nicht sichtbar, deshalb bleibt die regionale Fernbedienungszuordnung eine begründete Vermutung und keine bestätigte Eigenschaft.

## Protokoll

- Sony **SIRC / SIRCS**
- Trägerfrequenz: **40 kHz**
- Übertragung: drei identische Frames
- AV1: werkseitiger Receiver-Command-Mode
- AV2: Geräteadresse des AV1-Codes plus 32, als 15-Bit-Frame

Wichtig: Nicht jeder Befehl des Receivers ist ein 12-Bit-Code. Die neuere DSP-/Menüfamilie verwendet bereits in AV1 die Geräteadresse 144 und damit 15 Bit. Genau das war im älteren SmartIR-Profil noch falsch modelliert.

## Quellenlage und Vertrauensstufen

Das Profil kombiniert drei Arten von Informationen:

1. **Sony-Bedienungsanleitung und Supportseite** – Modell, regionale Fernbedienungen, vorhandene Tasten/Funktionen und AV1/AV2.
2. **Gelernte RM-PP505-Signale** aus dem offenen `hifiremote/deviceupgrades`-Datensatz – besonders wertvoll für die Eingänge und die moderne DSP-/Menüfamilie.
3. **Bekannte Sony-Receiver-SIRC-Zuordnungen** – als Ergänzung und als ausdrücklich gekennzeichnete Alternativen.

Auch gut belegte Codes gelten erst dann als hardwarebestätigt, wenn sie am konkreten STR-DB870 reagiert haben.

## Hauptprofil

### Power und Lautstärke

| Funktion | Command | AV1-Adresse | AV1-Bits | AV2-Adresse | AV2-Bits |
|---|---:|---:|---:|---:|---:|
| Power Toggle | 21 | 16 | 12 | 48 | 15 |
| Power On | 46 | 16 | 12 | 48 | 15 |
| Power Off | 47 | 16 | 12 | 48 | 15 |
| Volume + | 18 | 16 | 12 | 48 | 15 |
| Volume − | 19 | 16 | 12 | 48 | 15 |
| Mute | 20 | 16 | 12 | 48 | 15 |
| Sleep | 96 | 16 | 12 | 48 | 15 |

### Eingänge

| Funktion | Command | AV1-Adresse | Hinweis |
|---|---:|---:|---|
| PHONO | 32 | 16 | Sony Receiver Standard |
| TUNER | 33 | 16 | Sony Receiver Standard |
| VIDEO 1 | 34 | 16 | RM-PP505 gelernt |
| VIDEO 2 | 30 | 16 | RM-PP505 gelernt |
| VIDEO 3 | 66 | 16 | RM-PP505 gelernt |
| TV/SAT | 106 | 16 | RM-PP505 gelernt |
| DVD/LD | 125 | 16 | RM-PP505 gelernt; 107 bleibt als ältere Alternative |
| MD/TAPE | 105 | 16 | RM-PP505 gelernt; 35 bleibt als ältere Alternative |
| CD/SACD | 37 | 16 | RM-PP505 gelernt |
| AUX | 29 | 16 | RM-PP505 gelernt |
| MULTI/2CH A.DIRECT | 73 | 16 | RM-PP505 gelernt |
| MULTI CH diskret | 114 | 16 | zusätzlicher Sony-Receiver-Kandidat |

### DSP, Klang und Receiver-Menü

Diese Gruppe verwendet in AV1 die Adresse **144** und in AV2 die Adresse **176**. Beide Varianten werden als 15-Bit-SIRC gesendet.

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

`Enter/Exec` verwendet Command 12 auf der Basisadresse 16. `Test Tone` wird zuerst als Command 74 auf Adresse 16 getestet; eine Adresse-144-Variante ist zusätzlich im Fallback-Bereich enthalten.

### Tuner

| Funktion | Command | AV1-Adresse | AV2-Adresse |
|---|---:|---:|---:|
| Preset + | 16 | 13 | 45 |
| Preset − | 17 | 13 | 45 |
| Tuning + | 18 | 13 | 45 |
| Tuning − | 19 | 13 | 45 |
| FM Mode | 33 | 13 | 45 |
| Direct Tuning | 83 | 13 | 45 |

## Alternative Codes in der App

SmartIR zeigt diese Codes getrennt als **Alternative Sony-Codes** an. Sie werden nicht automatisch statt des Hauptprofils verwendet:

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

## Empfohlene Hardwareprüfung

1. Smartphone direkt auf das IR-Empfangsfenster des Receivers richten.
2. **AV1** auswählen.
3. Power Toggle, Volume +, Volume − und Mute testen.
4. TV/SAT, DVD/LD, CD/SACD und TUNER testen.
5. A.F.D., 2CH/OFF und Mode +/− testen.
6. Main Menu und Pfeiltasten testen.
7. Erst wenn AV1 vollständig ohne Reaktion bleibt, denselben Ablauf in **AV2** wiederholen.
8. Reagiert nur eine einzelne Taste nicht, die gleichnamige Alternative testen.
9. Funktionierende beziehungsweise falsche Tasten dokumentieren, damit das Profil ohne Raten finalisiert werden kann.

## Quellen

- Sony Support, STR-DB870: regionale Fernbedienungen RM-PP505 und RM-U305A.
- Sony STR-DB870/DB1070 Operating Instructions: Funktionsumfang der Fernbedienung und Command Mode AV1/AV2.
- `hifiremote/deviceupgrades`: gelernte RM-PP505-Signale der eng verwandten Sony-Receiver-Generation.
- Sony-Receiver-SIRC-Datenbank von HiFi-Remote: Gerätefamilien 13/45, 16/48, 18/50 und 144/176.

Die App enthält nur die daraus rekonstruierte numerische Interoperabilitätszuordnung. Es werden keine Sony-Handbuchseiten, proprietären Grafiken, Original-Firmware oder personenbezogenen Gerätedaten ins Repository übernommen.
