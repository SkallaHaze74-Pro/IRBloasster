# LG OLED55B19LA – Stock Factorywin expert path

## Ziel

Tool Options und White Balance sollen auf dem eigenen LG OLED55B19LA erreichbar sein, ohne eine modifizierte Firmware zu flashen und ohne den Homebrew-Dienst aus seiner Sandbox zu zwingen.

Die Firmware 03.53.45 enthält bereits die versteckte, vertrauenswürdige native LG-App:

```text
com.webos.app.factorywin
Title: QML Factorywin
visible: false
trustLevel: trusted
```

SmartIR 1.2.5 öffnet diese Stock-App ausschließlich über den autorisierten Second-Screen-Launcher. Es ersetzt keine LG-Systemdatei, umgeht kein Passwort und implementiert keine Factory-/PQ-Schreibmethode.

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

Die LG-App kann Änderungen über ihre Factory-Setter-API beziehungsweise `config/setConfigs` weiterreichen. SmartIR ruft diese Schreibwege nicht auf.

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

SmartIR 1.2.5 implementiert keinen dieser Schreib- oder Resetaufrufe.

## SmartIR Expert Service 1.2.5

Der zusätzliche Android-Einstieg bietet:

1. sichere Verbindung über die bereits vorhandene SmartIR-webOS-Kopplung;
2. read-only Vorprüfung von Modell, Firmware, Panelklasse, HDR-Fähigkeiten und Basis-Bildwerten;
3. anonymisierten JSON-Export der Vorprüfung;
4. doppelte Nutzerbestätigung;
5. bewusstes Öffnen von `EZ-ADJUST` oder `IN-START` über die originale LG-App;
6. eine feste Sperrliste, die Factory-, White-Balance-, NVRAM-, EDID-, DRM- und External-PQ-Schreibwege nicht implementiert.

## Warum zunächst kein Root nötig ist

Der Zugriff auf die Stock-Factorywin-Oberfläche erfolgt über den normalen LG-Anwendungsstarter. Root ist dafür nicht erforderlich. Root bleibt nur für spätere vollständige gerätespezifische Backups oder kontrollierte Automatisierung interessant. Eine modifizierte LG-Firmware ist für diesen ersten Schritt weder nötig noch erwünscht.

## Arbeitsregel

Vor jeder manuellen Änderung:

- read-only JSON sichern;
- jeden sichtbaren Ursprungswert fotografieren;
- nur einen Wert zur Zeit ändern;
- keine Panel-, PMIC-, VCOM-, Modell-, Schlüssel-, DRM-, EDID- oder Reset-Option berühren;
- White Balance nur mit einem geeigneten Messgerät verändern;
- ohne eindeutig getesteten Rückweg nicht speichern.
