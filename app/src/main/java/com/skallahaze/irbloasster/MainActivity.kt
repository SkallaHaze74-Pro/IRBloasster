package com.skallahaze.irbloasster

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.skallahaze.irbloasster.data.SettingsRepository
import com.skallahaze.irbloasster.ir.ConsumerIrSender
import com.skallahaze.irbloasster.ui.SmartIrApp
import com.skallahaze.irbloasster.ui.theme.IRTheme
import com.skallahaze.irbloasster.webos.WebOsClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var webOsClient: WebOsClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNearbyWifiPermissionIfNeeded()

        val settings = SettingsRepository(this)
        val irSender = ConsumerIrSender(this)
        webOsClient = WebOsClient(this, settings)

        setContent {
            IRTheme(preference = settings.themePreference) {
                SmartIrApp(
                    ir = irSender,
                    settings = settings,
                    webOs = webOsClient,
                )
            }
        }

        if (settings.autoConnect && settings.webOsHost.isNotBlank()) {
            lifecycleScope.launch {
                delay(500L)
                webOsClient.connect()
            }
        }
    }

    override fun onDestroy() {
        webOsClient.close()
        super.onDestroy()
    }

    private fun requestNearbyWifiPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES),
                NEARBY_WIFI_REQUEST,
            )
        }
    }

    private companion object {
        const val NEARBY_WIFI_REQUEST = 4101
    }
}
