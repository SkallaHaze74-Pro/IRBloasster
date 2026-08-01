package com.skallahaze.irbloasster.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartIrBackupCodecTest {
    @Test
    fun roundTripPreservesPortableSettingsAndExcludesClientKey() {
        val original = SmartIrBackupSnapshot(
            exportedAtEpochMillis = 1_785_570_000_000L,
            appVersionName = "1.1.5",
            packageName = "com.skallahaze.irbloasster",
            themePreference = ThemePreference.DARK.name,
            hapticsEnabled = true,
            autoConnect = true,
            webOsHost = "192.168.178.42",
            webOsMac = "AA:BB:CC:DD:EE:FF",
            sonyMode = "AV1",
            webOsCertificateFingerprint = "AA:11:BB:22",
            webOsClientKeyWasPresent = true,
        )

        val encoded = SmartIrBackupCodec.encode(original)
        val decoded = SmartIrBackupCodec.decode(encoded)

        assertEquals(original, decoded)
        assertTrue(encoded.contains("\"webOsClientKeyIncluded\": false"))
        assertFalse(encoded.contains("client-key-secret"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsForeignJsonFiles() {
        SmartIrBackupCodec.decode("""{"format":"other-app","schemaVersion":1,"settings":{}}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsFutureSchemaVersions() {
        SmartIrBackupCodec.decode(
            """{"format":"smartir-settings-backup","schemaVersion":99,"settings":{}}""",
        )
    }
}
