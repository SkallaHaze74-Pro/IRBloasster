# SmartIR / IRBloasster

SmartIR ist ein Android-Living-Room-Controller für LG webOS TV + Sony-Receiver mit IR-, Netzwerk-, TV-Lab- und Audio-Experimenten.

## SmartIR 1.4.0 – Live Audio Mix

Der Audio-Mix-Test kann Medien-Audio des Android-Handys live erfassen und ohne vorherige MP3-Datei direkt im lokalen WLAN zur webOS Audio Bridge schicken. Der TV-Teil verwendet Web Audio und einen eigenen Gain für den SmartIR-Musikkanal.

Wichtig: Android-Apps dürfen die interne Audioaufnahme selbst blockieren; insbesondere DRM-/Streaming-Inhalte können daher stumm bleiben. Der echte Bluetooth/A2DP-Pfad (`AMIXER4`) bleibt das bevorzugte spätere Backend, sobald die LG-Audio-Policy per UMI/Root steuerbar ist.

Details: `docs/AUDIO_MIX_ROOTFREE.md`

## Build in Termux

```bash
cd ~/IRBloasster
bash tools/build-termux.sh
```

Debug-APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## webOS Audio Bridge installieren

```bash
cd ~/IRBloasster
bash tools/install-audio-bridge.sh smartirtv
```

## Sicherheit

SmartIR verändert keine LG-Firmware und schreibt im TV-Lab/Expert-Bereich keine Panel-, NVRAM-, EDID-, DRM- oder Secure-Boot-Werte. Root-/Policy-Experimente werden getrennt vom normalen Controller behandelt.
