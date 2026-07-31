package com.skallahaze.irbloasster.ui

import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skallahaze.irbloasster.ir.LG_OLED55B1
import com.skallahaze.irbloasster.ir.Sony_STR_DB870
import com.skallahaze.irbloasster.ir.ConsumerIrSender

@Composable
fun HomeScreen() {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "IRBloasster Demo")
        Button(onClick = { ConsumerIrSender.transmit(LG_OLED55B1.FREQ, LG_OLED55B1.POWER_ON) }) {
            Text("TV Power")
        }
        Button(onClick = { ConsumerIrSender.transmit(Sony_STR_DB870.FREQ, Sony_STR_DB870.POWER_ON) }) {
            Text("Receiver Power")
        }
    }
}
