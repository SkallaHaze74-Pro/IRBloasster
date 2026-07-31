package com.skallahaze.irbloasster

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skallahaze.irbloasster.ui.LivingRoomApp
import com.skallahaze.irbloasster.ui.theme.LivingRoomTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestLocalNetworkPermissionIfNeeded()

        setContent {
            LivingRoomTheme {
                val viewModel: LivingRoomViewModel = viewModel()
                val state = viewModel.uiState.collectAsStateWithLifecycle().value
                LivingRoomApp(state = state, viewModel = viewModel)
            }
        }
    }

    private fun requestLocalNetworkPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES),
                LOCAL_NETWORK_PERMISSION_REQUEST
            )
        }
    }

    private companion object {
        const val LOCAL_NETWORK_PERMISSION_REQUEST = 4101
    }
}
