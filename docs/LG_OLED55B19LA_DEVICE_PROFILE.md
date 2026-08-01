# LG OLED55B19LA.DEUQJP – exaktes Geräteprofil

## Zweck

Dieses Dokument hält die technischen Daten des konkret fotografierten Fernsehers dauerhaft für SmartIR fest. Es enthält nur Angaben, die für Interoperabilität, Anschlusswahl, Diagnose und Wartung nützlich sind.

**Die Seriennummer wird aus Datenschutzgründen nicht in das öffentliche Repository übernommen.** Sie ist für IR- und webOS-Steuerung nicht erforderlich.

## Vom Typenschild bestätigt

| Merkmal | Wert |
|---|---|
| Hersteller | LG Electronics |
| Modell | `OLED55B19LA` |
| vollständiger Produktcode | `OLED55B19LA.DEUQJP` |
| Serie | B1, Modelljahr 2021 |
| Bildschirmgröße | 55 Zoll |
| Netzversorgung | AC 100–240 V, 50/60 Hz |
| Nenn-/Maximalangabe am Typenschild | 343 W |
| typische Leistungsaufnahme am Typenschild | 104 W |
| Fertigungsdatum | 09/2021 |
| Montage | Polen |

## Offiziell dokumentierte Fähigkeiten

LG dokumentiert für den OLED55B19LA unter anderem:

- 4K-OLED-Panel mit 3840 × 2160 Pixeln
- native 120-Hz-Klasse
- α7 Gen4 4K AI-Prozessor
- webOS 6.0
- 2 HDMI-2.1- und 2 HDMI-2.0-Eingänge
- eARC auf HDMI 3
- VRR, ALLM, Nvidia G-Sync, AMD FreeSync und HGiG
- WLAN 802.11ac, Bluetooth 5.0 und LAN
- Mobile TV On / Wi-Fi TV On
- optischer Digitalausgang und Kopfhörer/Line-out
- Magic Remote MR21

Offizielle Produktseite: https://www.lg.com/de/tvs-und-soundbars/oled/oled55b19la/

## Anschlussprofil des konkreten Geräts

Die Anschlussbeschriftung am Gerät bestätigt, dass HDMI 3 und HDMI 4 für 4K bei 120 Hz vorgesehen sind. HDMI 3 ist zusätzlich der eARC/ARC-Port.

| Port | SmartIR inputId | Profil |
|---|---|---|
| HDMI 1 | `HDMI_1` | bis 4K/60, HDMI 2.0 |
| HDMI 2 | `HDMI_2` | bis 4K/60, HDMI 2.0 |
| HDMI 3 | `HDMI_3` | eARC/ARC, HDMI 2.1, bis 4K/120 |
| HDMI 4 | `HDMI_4` | HDMI 2.1, bis 4K/120 |

SmartIR 1.1.6 bietet dafür direkte webOS-Schaltflächen. Zusätzlich wird die vom Fernseher selbst gelieferte Eingangsliste angezeigt, damit abweichende Namen oder IDs erkannt werden können.

## Steuerwege in SmartIR

### IR-Fallback

- LG-NEC, 32 Bit, 38 kHz
- Power, Lautstärke, Mute, Sender, Eingang und Navigation
- Medien-, Ziffern- und Farbtasten

### Lokales webOS

- SSDP-Suche im lokalen Netzwerk
- WSS-Port 3001 mit lokal gespeichertem Zertifikat-Fingerabdruck
- WS-Port 3000 als Kompatibilitäts-Fallback
- verschlüsselte Speicherung des Client-Keys im Android Keystore
- Status für Lautstärke, Mute, Power und aktive App
- Apps und externe Eingänge laden und starten
- direkte HDMI-1- bis HDMI-4-Auswahl
- Magic-Remote-Pointer, Klick und Scrollen
- Bildschirmtastatur
- Wake-on-LAN

## Verbesserungen aus dem exakten Profil

1. Direkte Tasten für HDMI 3 eARC und HDMI 4 4K/120.
2. Dynamische Anzeige aller vom TV gemeldeten Eingänge.
3. Dynamische Anzeige installierter TV-Apps.
4. Schaltfläche zum erneuten Laden von Status, Apps und Eingängen.
5. Sichtbare Gerätekarte mit Produktcode, Fertigung, HDMI-Belegung und Leistungsdaten.
6. Unit-Tests für Produktcode, eARC-Port und HDMI-2.1-/120-Hz-Zuordnung.
7. Portabler Einstellungs-Export für TV-IP, TV-MAC, Theme und Auto-Connect.

## Datenschutz

Nicht im Repository gespeichert werden:

- Seriennummer
- MAC-Adresse
- TV-IP
- webOS-Client-Key
- Zertifikate
- WLAN-Daten
- LG-Kontodaten

MAC-Adresse, IP, Client-Key und Zertifikat-Fingerabdruck werden bei Verwendung ausschließlich lokal in der App gespeichert. Der Client-Key wird über den Android Keystore verschlüsselt und absichtlich nicht in das portable JSON-Backup übernommen.
