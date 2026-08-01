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
import androidx.compose.ui.text.font.FontWeight
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
                eyebrow = "SONY STR-DB870",
                title = "Receiver-Fernbedienung",
                subtitle = "SIRC · 40 kHz · Command Mode ${mode.title}",
            )
        }

        item {
            SectionCard {
                Text(
                    "Gerätemodell bestätigt",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Das Typenschild bestätigt den Sony STR-DB870. Sony führte je nach Region die Fernbedienungen RM-U305A oder RM-PP505. Die App enthält jetzt die vollständige bekannte Tastenfamilie sowie alternative ältere Sony-Codes. Die Seriennummer wird nicht gespeichert oder veröffentlicht.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Die Zuordnung ist quellengestützt, aber erst nach deinem echten Tastentest hardwarebestätigt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        item {
            SectionCard {
                Text("Receiver Command Mode", style = MaterialTheme.typography.titleMedium)
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
                Spacer(Modifier.height(6.dp))
                Text(
                    "AV1 ist die Werkseinstellung. AV2 nur wählen, wenn der Command Mode am Receiver umgestellt wurde oder AV1 gar nicht reagiert.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SectionTitle("Power und Master-Lautstärke") }
        item {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RemoteKey(
                        "Power Toggle",
                        "⏻",
                        { onSony(Sony_STR_DB870.POWER) },
                        Modifier.weight(1f),
                        primary = true,
                    )
                    RemoteKey(
                        "Diskret ein",
                        "ON",
                        { onSony(Sony_STR_DB870.POWER_ON) },
                        Modifier.weight(1f),
                    )
                    RemoteKey(
                        "Diskret aus",
                        "OFF",
                        { onSony(Sony_STR_DB870.POWER_OFF) },
                        Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
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
                        modifier = Modifier.weight(1.35f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RemoteKey(
                            "Stumm",
                            "MUTE",
                            { onSony(Sony_STR_DB870.MUTE) },
                            Modifier.fillMaxWidth(),
                        )
                        RemoteKey(
                            "Sleep Timer",
                            "SLEEP",
                            { onSony(Sony_STR_DB870.SLEEP) },
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        item { SectionTitle("Eingänge des STR-DB870") }
        item {
            SectionCard {
                CommandGrid(Sony_STR_DB870.INPUTS, onSony)
            }
        }

        item { SectionTitle("Klangfeld und Betriebsart") }
        item {
            SectionCard {
                CommandGrid(Sony_STR_DB870.SOUND_CONTROLS, onSony)
                Spacer(Modifier.height(10.dp))
                ActionGrid(
                    actions = listOf(
                        "Subwoofer +" to { onSony(Sony_STR_DB870.SUBWOOFER_UP) },
                        "Subwoofer −" to { onSony(Sony_STR_DB870.SUBWOOFER_DOWN) },
                        "Test Tone" to { onSony(Sony_STR_DB870.TEST_TONE) },
                    ),
                )
            }
        }

        item { SectionTitle("Receiver-Menü") }
        item {
            SectionCard {
                RemoteKey(
                    "Hauptmenü öffnen",
                    "MENU",
                    { onSony(Sony_STR_DB870.MAIN_MENU) },
                    Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DPad(
                        onUp = { onSony(Sony_STR_DB870.MENU_UP) },
                        onDown = { onSony(Sony_STR_DB870.MENU_DOWN) },
                        onLeft = { onSony(Sony_STR_DB870.MENU_LEFT) },
                        onRight = { onSony(Sony_STR_DB870.MENU_RIGHT) },
                        onOk = { onSony(Sony_STR_DB870.MENU_SELECT) },
                    )
                }
            }
        }

        item { SectionTitle("Tuner") }
        item {
            SectionCard {
                CommandGrid(Sony_STR_DB870.TUNER_CONTROLS, onSony)
            }
        }

        item { SectionTitle("Alternative Sony-Codes") }
        item {
            SectionCard {
                Text(
                    "Nur verwenden, wenn die gleichnamige Haupttaste nicht reagiert. Diese Varianten stammen aus älteren Sony-Receiver-Familien oder abweichenden Geräteadressen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                CommandGrid(Sony_STR_DB870.FALLBACK_CODES, onSony)
            }
        }

        item {
            SectionCard {
                Text("Empfohlene Testreihenfolge", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "1. AV1 + Power Toggle  •  2. Lauter/Leiser  •  3. Mute  •  4. TV/SAT und DVD/LD  •  5. A.F.D. und 2CH  •  6. Menü. Erst wenn AV1 komplett schweigt, AV2 testen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
