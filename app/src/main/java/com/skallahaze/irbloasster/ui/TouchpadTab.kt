package com.skallahaze.irbloasster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.skallahaze.irbloasster.LivingRoomViewModel

@Composable
fun TouchpadTab(
    viewModel: LivingRoomViewModel,
    modifier: Modifier = Modifier
) {
    val status by viewModel.tvStatus.collectAsState()
    var text by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionCard(
            title = "Magic Touchpad",
            subtitle = "Wischen bewegt den Zeiger, Tippen bestätigt"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    text = if (status.pointerConnected) "Touchpad verbunden" else "Nicht verbunden",
                    active = status.pointerConnected
                )
                OutlinedButton(onClick = viewModel::connectPointer) {
                    Icon(Icons.Rounded.Link, contentDescription = null)
                    Text("Verbinden", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(32.dp)
                )
                .pointerInput(status.pointerConnected) {
                    detectTapGestures(onTap = { viewModel.pointerClick() })
                }
                .pointerInput(status.pointerConnected) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        viewModel.pointerMove(
                            dx = dragAmount.x * 1.35f,
                            dy = dragAmount.y * 1.35f
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.Swipe,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "TOUCHPAD",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    text = "Tippen = OK\nWischen = Zeiger bewegen",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RepeatRemoteButton(
                label = "Scroll hoch",
                icon = Icons.Rounded.KeyboardArrowUp,
                onRepeat = { viewModel.pointerScroll(0f, -4f) },
                modifier = Modifier.weight(1f)
            )
            RepeatRemoteButton(
                label = "Scroll runter",
                icon = Icons.Rounded.KeyboardArrowDown,
                onRepeat = { viewModel.pointerScroll(0f, 4f) },
                modifier = Modifier.weight(1f)
            )
        }

        SectionCard(
            title = "TV-Tastatur",
            subtitle = "Text wird direkt über den lokalen webOS-Kanal gesendet"
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Text für den Fernseher") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.sendText(text)
                        text = ""
                    },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Senden")
                }
                OutlinedButton(
                    onClick = viewModel::deleteText,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Backspace, null)
                }
                OutlinedButton(
                    onClick = viewModel::sendEnter,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.KeyboardReturn, null)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RemoteActionButton("Home", Icons.Rounded.Home, { viewModel.pointerButton("HOME") }, Modifier.weight(1f))
            RemoteActionButton("Zurück", Icons.Rounded.ArrowBack, { viewModel.pointerButton("BACK") }, Modifier.weight(1f))
            RemoteActionButton("Links", Icons.Rounded.KeyboardArrowLeft, { viewModel.pointerButton("LEFT") }, Modifier.weight(1f))
            RemoteActionButton("OK", Icons.Rounded.CheckCircle, viewModel::pointerClick, Modifier.weight(1f), true)
            RemoteActionButton("Rechts", Icons.Rounded.KeyboardArrowRight, { viewModel.pointerButton("RIGHT") }, Modifier.weight(1f))
        }
    }
}
