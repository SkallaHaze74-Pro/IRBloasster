package com.skallahaze.irbloasster.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.skallahaze.irbloasster.data.SettingsRepository
import java.text.DateFormat
import java.util.Date

@Composable
internal fun BackupSection(
    settings: SettingsRepository,
    onImported: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        runCatching {
            val payload = settings.exportBackup()
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(payload)
            } ?: error("Die Zieldatei konnte nicht geöffnet werden")
        }.onSuccess {
            onMessage("SmartIR-Backup gespeichert.")
        }.onFailure { error ->
            onMessage("Backup fehlgeschlagen: ${error.message ?: "unbekannter Fehler"}")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        runCatching {
            val payload = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                reader.readText()
            } ?: error("Die Backup-Datei konnte nicht geöffnet werden")
            settings.importBackup(payload)
        }.onSuccess { result ->
            onImported()
            onMessage(result.message)
        }.onFailure { error ->
            onMessage("Import fehlgeschlagen: ${error.message ?: "ungültige Backup-Datei"}")
        }
    }

    SectionCard {
        Text("Backup & Datenübertragung", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Sichert TV-IP, TV-MAC, Theme, Tastenvibration, Auto-Connect, Sony-Modus und den bekannten TV-Zertifikat-Fingerabdruck als portable JSON-Datei.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Der geheime webOS-Client-Key bleibt aus Sicherheitsgründen im Android-Keystore und wird nicht exportiert. Nach einer vollständigen Deinstallation muss der LG-TV deshalb einmal neu gekoppelt werden.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = {
                    exportLauncher.launch("SmartIR-backup-${System.currentTimeMillis()}.json")
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Exportieren")
            }
            OutlinedButton(
                onClick = {
                    importLauncher.launch(arrayOf("application/json", "text/plain"))
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Importieren")
            }
        }

        if (settings.lastBackupEpochMillis > 0L) {
            Spacer(Modifier.height(10.dp))
            InfoLine("Letztes Backup", formatTimestamp(settings.lastBackupEpochMillis))
        }
        if (settings.lastImportEpochMillis > 0L) {
            InfoLine("Letzter Import", formatTimestamp(settings.lastImportEpochMillis))
        }
    }
}

private fun formatTimestamp(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))
