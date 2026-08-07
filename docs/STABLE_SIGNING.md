# SmartIR – dauerhafte Update-Signatur

Android akzeptiert ein Update für `com.skallahaze.irbloasster` nur, wenn die neue APK mit demselben privaten Schlüssel signiert wurde wie die bereits installierte APK.

GitHub-Actions-Debug-Builds verwenden ohne hinterlegtes privates Keystore keinen dauerhaften Schlüssel und sind deshalb nur CI/Test-Artefakte.

Für den normalen persönlichen SmartIR-Betrieb erzeugt `tools/build-termux.sh` beim ersten Lauf automatisch einen privaten, dauerhaften Schlüssel unter:

```text
~/.smartir/smartir-release.jks
~/.smartir/signing.properties
```

Diese Dateien werden nicht ins Repository geschrieben. `.gitignore` schließt `*.jks` und `*.keystore` aus.

Danach wird jeder lokale Termux-Debug-Build mit genau diesem Schlüssel signiert. Solange der Ordner `~/.smartir` erhalten bleibt, können spätere APKs als normale Updates installiert werden, ohne SmartIR vorher zu deinstallieren.

## Einmaliger Wechsel

Ist aktuell noch eine APK mit einer anderen Signatur installiert, kann Android den Schlüssel nicht nachträglich austauschen. In diesem Fall ist genau ein letzter Wechsel nötig:

1. In SmartIR bei Bedarf Einstellungen/Backup exportieren.
2. Alte anders signierte SmartIR-Version deinstallieren.
3. Einmal `bash tools/build-termux.sh` ausführen.
4. Die erzeugte `SmartIR-v<version>-stable.apk` installieren.

Ab diesem Punkt werden nur noch APKs benutzt, die mit demselben privaten SmartIR-Schlüssel gebaut wurden.

## Schlüssel sichern

Der Ordner `~/.smartir` ist wichtiger als eine einzelne APK. Geht dieser private Schlüssel verloren, kann eine neue APK die bestehende Installation nicht mehr aktualisieren.

Den Schlüssel niemals in ein öffentliches Git-Repository hochladen oder öffentlich teilen.
