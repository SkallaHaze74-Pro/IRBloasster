package com.skallahaze.irbloasster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skallahaze.irbloasster.ui.LivingRoomApp
import com.skallahaze.irbloasster.ui.theme.LivingRoomTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LivingRoomTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val controller: LivingRoomViewModel = viewModel()
                    LivingRoomApp(controller)
                }
            }
        }
    }
}
