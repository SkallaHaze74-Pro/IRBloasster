# SmartIR – Datensicherung und Migration

SmartIR 1.1.5 führt eine ausdrückliche Backup- und Wiederherstellungsfunktion ein, damit Einstellungen beim Wechsel zwischen Test-APKs, bei einer Neuinstallation oder beim Gerätewechsel nicht unnötig verloren gehen.

## Was bei einem normalen Update erhalten bleibt

Solange dieselbe App mit demselben Paketnamen und derselben Signatur aktualisiert wird, bleiben die Android-App-Daten automatisch erhalten:

- Theme
- Tastenvibration
- Auto-Connect
- TV-IP beziehungsweise Hostname
- TV-MAC-Adresse
- Sony-Modus beziehungsweise dessen Migration
- webOS-Pairingdaten im lokalen Android-Keystore

Der Paketname bleibt dauerhaft:

```text
com.skallahaze.irbloasster
```

Wichtig: GitHub-Actions-Debug-APKs können unterschiedliche Debug-Signaturen besitzen. Erzwingt Android deshalb eine Deinstallation, würden lokale App-Daten gelöscht. Vor einer solchen Deinstallation sollte immer ein manuelles SmartIR-Backup exportiert werden.

## Manuelles portables Backup

Unter **Setup → Backup & Datenübertragung** stehen zwei Schaltflächen zur Verfügung:

- **Exportieren** erstellt eine JSON-Datei über den Android-Dateidialog.
- **Importieren** stellt eine zuvor exportierte JSON-Datei wieder her.

Gesichert werden:

- Backup-Schema und Erstellungszeit
- SmartIR-Version und Paketname
- Theme
- Tastenvibration
- Auto-Connect
- TV-IP beziehungsweise Hostname
- TV-MAC-Adresse
- Sony-Modus
- bekannter webOS-Zertifikat-Fingerabdruck
- Information, ob zum Exportzeitpunkt ein webOS-Client-Key vorhanden war

## Sicherheitsgrenze

Der geheime webOS-Client-Key wird **nicht** in das portable JSON-Backup geschrieben. Er ist an den Android-Keystore des Geräts gebunden und soll nicht als Klartextdatei herumliegen.

Nach einer vollständigen Deinstallation oder einem Gerätewechsel gilt daher:

1. SmartIR-Backup importieren.
2. TV-IP und MAC sind wieder vorhanden.
3. Den LG-TV einmal neu koppeln.
4. Der neue Client-Key wird erneut verschlüsselt im Android-Keystore gespeichert.

Ein vorhandenes funktionierendes Pairing wird bei einem Import nicht überschrieben.

## Interne Trennung

SmartIR verwendet zwei getrennte Shared-Preferences-Dateien:

```text
smart_ir_settings.xml
smart_ir_secure.xml
```

`smart_ir_settings.xml` enthält portable, nicht geheime Einstellungen. `smart_ir_secure.xml` enthält den verschlüsselten webOS-Client-Key und den zugehörigen Zertifikat-Fingerabdruck.

Beim ersten Start nach dem Update migriert SmartIR ältere Pairingwerte automatisch aus der bisherigen gemeinsamen Einstellungsdatei in den getrennten sicheren Speicher.

## Android Auto Backup und Geräteübertragung

Die Manifest- und XML-Regeln erlauben Android, nur `smart_ir_settings.xml` über Cloud-Backup beziehungsweise direkte Geräteübertragung zu sichern. `smart_ir_secure.xml` wird ausgeschlossen.

Damit können nicht geheime Einstellungen automatisch wiederkehren, während gerätegebundene Schlüssel nicht in ein unpassendes Keystore-Umfeld kopiert werden.

## JSON-Format

Das portable Format besitzt eine ausdrücklich versionierte Struktur:

```json
{
  "format": "smartir-settings-backup",
  "schemaVersion": 1,
  "exportedAtEpochMillis": 0,
  "app": {
    "packageName": "com.skallahaze.irbloasster",
    "versionName": "1.1.5"
  },
  "settings": {
    "themePreference": "SYSTEM",
    "hapticsEnabled": true,
    "autoConnect": true,
    "webOsHost": "",
    "webOsMac": "",
    "sonyMode": "AV1",
    "webOsCertificateFingerprint": ""
  },
  "security": {
    "webOsClientKeyIncluded": false,
    "webOsClientKeyWasPresent": false
  }
}
```

Zukünftige Formatänderungen müssen die `schemaVersion` erhöhen und bestehende Versionen weiterhin bewusst migrieren oder mit einer klaren Fehlermeldung ablehnen.

## Private Gerätedaten

Seriennummern, WLAN-Passwörter, Cloud-Token und Original-APKs gehören nicht in das öffentliche Repository und nicht in das portable Einstellungsbackup. Für private Eigentums- oder Reparaturunterlagen sollte eine getrennte, lokal gespeicherte Datei verwendet werden.

## Empfohlener Ablauf vor jeder Test-APK

1. In der bestehenden App **Setup → Backup & Datenübertragung → Exportieren** öffnen.
2. Die JSON-Datei im Download- oder Dokumente-Ordner speichern.
3. Die neue APK zunächst als Update installieren.
4. Nur bei Signaturkonflikt die alte App deinstallieren.
5. Neue App installieren und das JSON-Backup importieren.
6. Falls nötig, den LG-TV einmal neu koppeln.
