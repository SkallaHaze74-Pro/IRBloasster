# SmartIR TV Lab 1.2.0

## Ziel

SmartIR TV Lab liest die tatsächlich aktiven Hardware-, Funktions- und Einstellungswerte des eigenen LG OLED55B19LA aus. Die neue Laborstufe arbeitet **read-only-first** und trennt normale Fernbedienung, Diagnose, Kalibrierung und riskante Servicefunktionen klar voneinander.

## Android-TV-Labor

Die APK enthält neben der normalen SmartIR-Oberfläche einen zweiten Launcher-Einstieg **SmartIR TV Lab**. Beide Einträge gehören zum selben Paket:

```text
com.skallahaze.irbloasster
```

Der Scanner liest:

- Modell, Firmware, SDK und Boardtyp
- SoC, RAM, eMMC, Display- und OLED-Modultyp
- Auflösung und Ausgabe-Bildrate
- HDR, Dolby Vision, VRR, ISF, WiSA und OLED-Orbit-Unterstützung
- optischen Ausgang, Kopfhörer/Line-out, Tuner- und DVR-Fähigkeiten
- ausgewählte Bild-, Ton-, Netzwerk-, CEC- und allgemeine Einstellungen
- vorhandene sichere offizielle LG-Apps
- optional einen read-only External-PQ-Snapshot

Seriennummern, MAC/IP-Adressen, Client-Keys, Zertifikate, DRM-, Widevine-, ESN-/VSN- und Kontodaten werden aus dem exportierten Bericht ausgeschlossen.

## Kalibrierassistent

Der Assistent besitzt getrennte Profile:

- SDR · dunkler Raum
- SDR · heller Raum
- HDR / Dolby Vision
- Gaming / VRR

Er vergleicht die gelesenen Werte und weist unter anderem auf Energiespar-Automatiken, auffällige Helligkeits-/Kontrast-/Farbwerte, Schwarzpegel, Weiß-Clipping, HDR-Trennung und Gaming-Pipeline hin.

Die erste Version ist bewusst regelbasiert. Sie behauptet keine echte Farbmessung. Eine Smartphone-Kamera kann später wiederholbare Vergleichsaufnahmen liefern, ersetzt aber kein Colorimeter. White Balance, LUT und External-PQ dürfen erst nach validierter Messung, vollständigem Vorher-Backup und getesteter Rücksetzung beschrieben werden.

## webOS-TV-Companion

Unter `webos-tv-lab/` liegt eine vollständig offline arbeitende webOS-App mit:

- PLUGE-Schwarzpegel
- Near-Black-Stufen
- Weiß-Clipping
- Graustufenrampe
- Farbbalken
- Geometrie-/Overscan-Raster
- Vollflächen Schwarz, Weiß, Rot, Grün und Blau

Die App enthält keine Netzwerkaufrufe und schreibt keine TV-Einstellungen.

### Paketieren und installieren

Voraussetzungen: Developer Mode am TV, Key Server und die bereits eingerichtete webOS-CLI.

```bash
cd ~/IRBloasster
ares-package webos-tv-lab
```

Danach die erzeugte IPK installieren:

```bash
ares-install --device smartirtv com.skallahaze.smartir.tvlab_1.0.0_all.ipk
```

Starten:

```bash
ares-launch --device smartirtv com.skallahaze.smartir.tvlab
```

## Sicherheitsgrenzen

Nicht als normale SmartIR-Funktion freigegeben werden:

- Service Reset oder Factory Reset
- Modell-, Panel- oder Tool-Optionen
- White Balance, LUT, NVRAM oder EDID-Schreibzugriffe
- OLED-Schutz- oder Kompensationsflags
- DRM-, Widevine-, Schlüssel- oder Geräteidentitätsdaten
- `ssap://externalpq/setExternalPqData`

Der read-only PQ-Snapshot ist ein Analyse-/Backup-Artefakt, keine getestete Rückschreibdatei.
