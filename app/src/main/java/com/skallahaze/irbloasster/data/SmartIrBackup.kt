package com.skallahaze.irbloasster.data

import org.json.JSONObject

data class SmartIrBackupSnapshot(
    val schemaVersion: Int = SmartIrBackupCodec.CURRENT_SCHEMA_VERSION,
    val exportedAtEpochMillis: Long,
    val appVersionName: String,
    val packageName: String,
    val themePreference: String,
    val hapticsEnabled: Boolean,
    val autoConnect: Boolean,
    val webOsHost: String,
    val webOsMac: String,
    val sonyMode: String,
    val webOsCertificateFingerprint: String,
    val webOsClientKeyWasPresent: Boolean,
)

data class SmartIrImportResult(
    val message: String,
    val tvRepairingRecommended: Boolean,
)

object SmartIrBackupCodec {
    const val CURRENT_SCHEMA_VERSION = 1
    private const val FORMAT = "smartir-settings-backup"

    fun encode(snapshot: SmartIrBackupSnapshot): String {
        return JSONObject().apply {
            put("format", FORMAT)
            put("schemaVersion", snapshot.schemaVersion)
            put("exportedAtEpochMillis", snapshot.exportedAtEpochMillis)
            put(
                "app",
                JSONObject().apply {
                    put("packageName", snapshot.packageName)
                    put("versionName", snapshot.appVersionName)
                },
            )
            put(
                "settings",
                JSONObject().apply {
                    put("themePreference", snapshot.themePreference)
                    put("hapticsEnabled", snapshot.hapticsEnabled)
                    put("autoConnect", snapshot.autoConnect)
                    put("webOsHost", snapshot.webOsHost)
                    put("webOsMac", snapshot.webOsMac)
                    put("sonyMode", snapshot.sonyMode)
                    put("webOsCertificateFingerprint", snapshot.webOsCertificateFingerprint)
                },
            )
            put(
                "security",
                JSONObject().apply {
                    put("webOsClientKeyIncluded", false)
                    put("webOsClientKeyWasPresent", snapshot.webOsClientKeyWasPresent)
                    put(
                        "note",
                        "The webOS client key is device-bound and intentionally excluded. Pair the TV again after a full reinstall.",
                    )
                },
            )
        }.toString(2)
    }

    fun decode(payload: String): SmartIrBackupSnapshot {
        val root = JSONObject(payload)
        require(root.optString("format") == FORMAT) {
            "Die Datei ist kein SmartIR-Backup"
        }

        val schemaVersion = root.optInt("schemaVersion", -1)
        require(schemaVersion in 1..CURRENT_SCHEMA_VERSION) {
            "Nicht unterstützte SmartIR-Backup-Version: $schemaVersion"
        }

        val app = root.optJSONObject("app") ?: JSONObject()
        val settings = root.optJSONObject("settings")
            ?: error("Im Backup fehlt der Einstellungsbereich")
        val security = root.optJSONObject("security") ?: JSONObject()

        return SmartIrBackupSnapshot(
            schemaVersion = schemaVersion,
            exportedAtEpochMillis = root.optLong("exportedAtEpochMillis", 0L),
            appVersionName = app.optString("versionName"),
            packageName = app.optString("packageName"),
            themePreference = settings.optString("themePreference", ThemePreference.SYSTEM.name),
            hapticsEnabled = settings.optBoolean("hapticsEnabled", true),
            autoConnect = settings.optBoolean("autoConnect", true),
            webOsHost = settings.optString("webOsHost"),
            webOsMac = settings.optString("webOsMac"),
            sonyMode = settings.optString("sonyMode", "AV1"),
            webOsCertificateFingerprint = settings.optString("webOsCertificateFingerprint"),
            webOsClientKeyWasPresent = security.optBoolean("webOsClientKeyWasPresent", false),
        )
    }
}
