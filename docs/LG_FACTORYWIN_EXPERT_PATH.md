# LG OLED55B19LA – Stock Factorywin research archive

> Archivierter Forschungsstand. Der frühere Android-Einstieg **SmartIR Expert Service** wurde ab SmartIR 1.5.2 aus der installierten App entfernt. Die technischen LG-/webOS-Erkenntnisse bleiben hier erhalten, weil sie für spätere TV-Forschung, Diagnose und kontrollierte Read-only-Werkzeuge nützlich sein können.

## Ziel der ursprünglichen Untersuchung

Tool Options und White Balance sollten auf dem eigenen LG OLED55B19LA nachvollziehbar sein, ohne eine modifizierte Firmware zu flashen und ohne den Homebrew-Dienst aus seiner Sandbox zu zwingen.

Die Firmware 03.53.45 enthält bereits die versteckte, vertrauenswürdige native LG-App:

```text
com.webos.app.factorywin
Title: QML Factorywin
visible: false
trustLevel: trusted
```

Der frühere SmartIR-Prototyp öffnete diese Stock-App ausschließlich über den autorisierten Second-Screen-Launcher. Er ersetzte keine LG-Systemdatei, umging kein Passwort und implementierte keine Factory-/PQ-Schreibmethode. Dieser Android-Prototyp ist nicht mehr Teil der SmartIR-APK.

## Bestätigte Startparameter

Die Firmwaredateien `main.qml` und `SystemController.qml` akzeptieren beziehungsweise verarbeiten folgende Factorywin-Startparameter:

```text
irKey = ezAdjust
irKey = inStart
```

`ezAdjust` öffnet das LG-Menü für Werksabgleiche. `inStart` öffnet System- und Diagnoseinformationen. Die originale Passwortabfrage bleibt aktiv.

## Inhalte von EZ-ADJUST

`EzadjustMenu.qml` enthält auf dieser Firmware:

- ToolOPT1 – Product / Panel
- ToolOPT2 – Power
- ToolOPT3 – PQ / Sound
- ToolOPT4 – Etc
- ToolOPT5 – Jack ID / Key status
- ToolOPT6 – Energy / Country
- Area Option
- ADC Calibration
- White Balance
- 22 Point WB
- V-com
- External Input Adjust

## Servicewege aus der LG-App

### Tool Options

`ToolOptionController.qml` liest über:

```text
com.webos.service.factorymanager/getFactoryOpt
com.webos.service.config/getConfigs
```

Die LG-App kann Änderungen über ihre Factory-Setter-API beziehungsweise `config/setConfigs` weiterreichen. Der entfernte SmartIR-Expert-Prototyp rief diese Schreibwege nicht auf.

Die Tool-Option-Datenbank bestätigt unter anderem Felder für Panelgröße/-typ/-hersteller, PMIC, Dolby Vision, VRR, FreeSync, Audio, Anschlüsse, Länder-/Energieoptionen und Schlüsselstatus. Viele davon sind hardware-, panel-, lizenz- oder geräteidentitätsgebunden und dürfen nicht durch Raten verändert werden.

### White Balance

`WhiteBalanceController.qml` und `WhiteBalance22Controller.qml` lesen über:

```text
com.webos.service.pqcontroller/getWhiteBalance
com.webos.service.pqcontroller/getNpointWB
```

Die LG-App besitzt außerdem Schreib- und Resetpfade:

```text
com.webos.service.pqcontroller/setWhiteBalance
com.webos.service.pqcontroller/setNpointWB
com.webos.service.pqcontroller/setWbPattern
com.webos.service.factorymanager/resetWhiteBalance
```

Diese Schreib- oder Resetaufrufe sind nicht Teil der normalen SmartIR-App.

## Historischer SmartIR-Expert-Prototyp

Der inzwischen entfernte Android-Einstieg hatte ausschließlich folgende Aufgaben:

1. Verbindung über die bereits vorhandene SmartIR-webOS-Kopplung;
2. read-only Vorprüfung von Modell, Firmware, Panelklasse, HDR-Fähigkeiten und Basis-Bildwerten;
3. anonymisierten JSON-Export der Vorprüfung;
4. doppelte Nutzerbestätigung;
5. bewusstes Öffnen von `EZ-ADJUST` oder `IN-START` über die originale LG-App;
6. feste Sperrliste für Factory-, White-Balance-, NVRAM-, EDID-, DRM- und External-PQ-Schreibwege.

Die APK braucht diese Oberfläche nicht mehr. Die breitere Read-only-Forschung bleibt separat erhalten, insbesondere in:

- `docs/LG_TV_LAB_READONLY_PAYLOADS.json`
- `docs/LG_FIRMWARE_03_53_31_VS_03_53_45_FINDINGS.md`
- `docs/LG_OLED55B19LA_DEVICE_PROFILE.md`
- `docs/LG_ROOT_RESEARCH_03_53_45.md`
- `docs/LG_THINQ_ANALYSIS.md`
- `docs/TV_LAB_AND_CALIBRATION.md`
- `webos-tv-lab/`

## Warum diese Daten erhalten bleiben

Die bestätigten App-IDs, Read-only-Servicewege, Firmware-Funde und Gerätefähigkeiten sind unabhängig von der entfernten Expert-Service-Oberfläche nützlich. Sie können später für ein neues, klar getrenntes TV-Diagnose-/Laborwerkzeug wiederverwendet werden, ohne den alten Android-Launcher in SmartIR mitzuschleppen.

## Arbeitsregel für spätere TV-Forschung

Vor jeder manuellen Änderung:

- read-only Daten sichern;
- jeden sichtbaren Ursprungswert dokumentieren;
- nur einen Wert zur Zeit ändern;
- keine Panel-, PMIC-, VCOM-, Modell-, Schlüssel-, DRM-, EDID- oder Reset-Option berühren;
- White Balance nur mit einem geeigneten Messgerät verändern;
- ohne eindeutig getesteten Rückweg nicht speichern.
