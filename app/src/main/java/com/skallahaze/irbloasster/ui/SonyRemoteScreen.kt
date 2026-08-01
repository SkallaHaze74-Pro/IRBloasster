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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skallahaze.irbloasster.ir.SonyCommand
import com.skallahaze.irbloasster.ir.Sony_STR_DB870

@Composable
internal fun SonyRemoteScreen(
    onSony: (SonyCommand) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeader(
                eyebrow = "SONY STR-DB870 · CEL",
                title = "Receiver-Fernbedienung",
                subtitle = "RM-U305A · SIRC 40 kHz · AV1 fest",
            )
        }

        item {
            SectionCard {
                Text(
                    "Exakte Gerätevariante bestätigt",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Die Rückseite bestätigt den Sony STR-DB870 mit Area Code CEL und der Kennzeichnung ${Sony_STR_DB870.REAR_PANEL_MARKING}. Für diese Variante nennt Sony die Fernbedienung ${Sony_STR_DB870.SUPPLIED_REMOTE}.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Beim CEL-Modell ist der Receiver-Command-Mode nicht umschaltbar. SmartIR sendet im normalen Betrieb deshalb immer AV1. AV2 bleibt nur im Rohcode-Labor für Diagnosezwecke verfügbar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Die Seriennummer wird bewusst weder in der App noch im öffentlichen Repository gespeichert.",
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
                    "1. Power Toggle  •  2. Lauter/Leiser  •  3. Mute  •  4. TV/SAT und DVD/LD  •  5. A.F.D. und 2CH  •  6. Menü. SmartIR verwendet bei diesem CEL-Gerät automatisch AV1.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
