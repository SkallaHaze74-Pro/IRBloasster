package com.skallahaze.irbloasster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
fun StatusBadge(
    text: String,
    positive: Boolean,
    modifier: Modifier = Modifier
) {
    val background = if (positive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = if (positive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = background
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (positive) MaterialTheme.colorScheme.primary else foreground)
            )
            Spacer(Modifier.width(7.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = foreground)
        }
    }
}

@Composable
fun RemoteButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    hapticsEnabled: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    val container = if (emphasized) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier
            .height(58.dp)
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .clickable(enabled = enabled) {
                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        shape = MaterialTheme.shapes.medium,
        color = if (enabled) container else container.copy(alpha = 0.45f),
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.45f),
        tonalElevation = if (emphasized) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun HoldRemoteButton(
    label: String,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    hapticsEnabled: Boolean = true
) {
    val currentAction by rememberUpdatedState(onRepeat)
    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = modifier
            .height(64.dp)
            .semantics {
                role = Role.Button
                contentDescription = "$label, gedrückt halten zum Wiederholen"
            }
            .pointerInput(enabled, hapticsEnabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentAction()
                        val repeatJob = launch {
                            delay(360)
                            while (isActive) {
                                currentAction()
                                delay(115)
                            }
                        }
                        tryAwaitRelease()
                        repeatJob.cancel()
                    }
                )
            },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (enabled) 1f else 0.45f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = if (enabled) 1f else 0.45f),
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun DPad(
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onOk: () -> Unit,
    hapticsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.35f)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(16.dp)
    ) {
        RoundKey("↑", onUp, Modifier.align(Alignment.TopCenter), hapticsEnabled)
        RoundKey("↓", onDown, Modifier.align(Alignment.BottomCenter), hapticsEnabled)
        RoundKey("←", onLeft, Modifier.align(Alignment.CenterStart), hapticsEnabled)
        RoundKey("→", onRight, Modifier.align(Alignment.CenterEnd), hapticsEnabled)
        Surface(
            modifier = Modifier
                .size(92.dp)
                .align(Alignment.Center)
                .clickable {
                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onOk()
                },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 7.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("OK", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RoundKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    hapticsEnabled: Boolean
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = modifier
            .size(70.dp)
            .clickable {
                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
fun Touchpad(
    onMove: (Float, Float) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onMove(dragAmount.x * 1.55f, dragAmount.y * 1.55f)
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Touchpad", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "Wischen zum Bewegen · Tippen für Klick",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun InfoPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondaryContainer
) {
    Surface(modifier = modifier, shape = CircleShape, color = color) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
