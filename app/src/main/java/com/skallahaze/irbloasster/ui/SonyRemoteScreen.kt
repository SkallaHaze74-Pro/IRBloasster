package com.skallahaze.irbloasster.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skallahaze.irbloasster.ir.SonyCommand
import com.skallahaze.irbloasster.ir.SonyCommandMode
import com.skallahaze.irbloasster.ir.Sony_STR_DB870

@Composable
internal fun SonyRemoteScreen(
    mode: SonyCommandMode,
    onModeChange: (SonyCommandMode) -> Unit,
    onSony: (SonyCommand) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeader(
                eyebrow = "SONY RECEIVER",
                title = "STR-DB870",
                subtitle = "SIRC ${if (mode == SonyCommandMode.AV1) "12" else "15"} Bit · Command Mode ${mode.title}",
            )
        }

        item {
            SectionCard {
                Text("Fernbedienungsmodus", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SonyCommandMode.entries.forEach { item ->
                        FilterChip(
                            selected = mode == item,
                            onClick = { onModeChange(item) },
                            label = { Text(item.title) },
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RemoteKey(
                    "Power",
                    "⏻",
                    { onSony(Sony_STR_DB870.POWER) },
                    Modifier.weight(1f),
                    primary = true,
                )
                RemoteKey(
                    "Stumm",
                    "MUTE",
                    { onSony(Sony_STR_DB870.MUTE) },
                    Modifier.weight(1f),
                )
                RemoteKey(
                    "2CH",
                    "2CH",
                    { onSony(Sony_STR_DB870.MODE_2CH) },
                    Modifier.weight(1f),
                )
            }
        }

        item {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VerticalRocker(
                        title = "MASTER VOL",
                        onUp = { onSony(Sony_STR_DB870.VOLUME_UP) },
                        onDown = { onSony(Sony_STR_DB870.VOLUME_DOWN) },
                        modifier = Modifier.weight(1f),
                    )
                    Column(
                        modifier = Modifier.weight(1.4f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RemoteKey(
                            "Sound Field +",
                            "SF +",
                            { onSony(Sony_STR_DB870.SOUND_FIELD_NEXT) },
                            Modifier.fillMaxWidth(),
                        )
                        RemoteKey(
                            "Sound Field −",
                            "SF −",
                            { onSony(Sony_STR_DB870.SOUND_FIELD_PREVIOUS) },
                            Modifier.fillMaxWidth(),
                        )
                        RemoteKey(
                            "Effect aus",
                            "OFF",
                            { onSony(Sony_STR_DB870.EFFECT_OFF) },
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        item { SectionTitle("Eingänge") }
        item {
            SectionCard {
                CommandGrid(Sony_STR_DB870.INPUTS, onSony)
            }
        }

        item { SectionTitle("Receiver-Menü") }
        item {
            SectionCard {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DPad(
                        onUp = { onSony(Sony_STR_DB870.MENU_UP) },
                        onDown = { onSony(Sony_STR_DB870.MENU_DOWN) },
                        onLeft = { onSony(Sony_STR_DB870.MENU_LEFT) },
                        onRight = { onSony(Sony_STR_DB870.MENU_RIGHT) },
                        onOk = { onSony(Sony_STR_DB870.MENU_SELECT) },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RemoteKey(
                        "Enter",
                        "ENTER",
                        { onSony(Sony_STR_DB870.MENU_ENTER) },
                        Modifier.weight(1f),
                    )
                    RemoteKey(
                        "Test Tone",
                        "TEST",
                        { onSony(Sony_STR_DB870.TEST_TONE) },
                        Modifier.weight(1f),
                    )
                }
            }
        }

        item { SectionTitle("Subwoofer & Tuner") }
        item {
            SectionCard {
                ActionGrid(
                    actions = listOf(
                        "Subwoofer +" to { onSony(Sony_STR_DB870.SUBWOOFER_UP) },
                        "Subwoofer −" to { onSony(Sony_STR_DB870.SUBWOOFER_DOWN) },
                        "Preset +" to { onSony(Sony_STR_DB870.TUNER_PRESET_UP) },
                        "Preset −" to { onSony(Sony_STR_DB870.TUNER_PRESET_DOWN) },
                        "Tuning +" to { onSony(Sony_STR_DB870.TUNING_UP) },
                        "Tuning −" to { onSony(Sony_STR_DB870.TUNING_DOWN) },
                        "FM Mode" to { onSony(Sony_STR_DB870.FM_MODE) },
                    ),
                )
            }
        }
    }
}
