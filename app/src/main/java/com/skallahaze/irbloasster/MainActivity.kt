package com.skallahaze.irbloasster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.skallahaze.irbloasster.ui.theme.IRTheme
import com.skallahaze.irbloasster.ui.HomeScreen
import com.skallahaze.irbloasster.ir.ConsumerIrSender

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConsumerIrSender.init(this)
        setContent {
            IRTheme {
                HomeScreen()
            }
        }
    }
}
