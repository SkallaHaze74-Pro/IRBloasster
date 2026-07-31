package com.skallahaze.irbloasster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.skallahaze.irbloasster.data.SettingsRepository
import com.skallahaze.irbloasster.ir.ConsumerIrSender
import com.skallahaze.irbloasster.ui.SmartIrApp
import com.skallahaze.irbloasster.ui.theme.IRTheme
import com.skallahaze.irbloasster.webos.WebOsClient

class MainActivity : ComponentActivity() {
    private lateinit var webOsClient: WebOsClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settings = SettingsRepository(this)
        val irSender = ConsumerIrSender(this)
        webOsClient = WebOsClient(settings)

        setContent {
            IRTheme(preference = settings.themePreference) {
                SmartIrApp(
                    ir = irSender,
                    settings = settings,
                    webOs = webOsClient,
                )
            }
        }
    }

    override fun onDestroy() {
        webOsClient.close()
        super.onDestroy()
    }
}
