package com.skallahaze.irbloasster.webos

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

enum class TvSignalMode(
    val reportValue: String,
    val displayName: String,
) {
    UNKNOWN("UNKNOWN", "Unbekannt"),
    SDR_DARK("SDR_DARK", "SDR dunkel"),
    SDR_BRIGHT("SDR_BRIGHT", "SDR hell"),
    HLG_HDR("HLG_HDR", "HLG HDR"),
    HDR10("HDR10", "HDR10"),
    DOLBY_VISION("DOLBY_VISION", "Dolby Vision"),
    GAME_HDR("GAME_HDR", "Gaming HDR"),
}

data class TvOptimizationItem(
    val title: String,
    val detail: String,
)

/**
 * Conservative, measurement-aware recommendations for the LG B1 scan data.
 *
 * These rules never write settings. They only compare the read-only scan with
 * safe picture-mode baselines and deliberately avoid copying panel-specific
 * white-balance, CMS, LUT or service-menu values between televisions.
 */
object TvOptimizationAdvisor {
    fun inferredSignalFamily(state: DeepLabState): String {
        val picture = pictureValues(state)
        val oled = picture.intValue("backlight")
        val contrast = picture.intValue("contrast")
        val brightness = picture.intValue("brightness")

        return when {
            oled != null && oled >= 95 && contrast != null && contrast >= 95 && brightness == 50 -> {
                "HDR_LIKE"
            }

            oled != null || contrast != null || brightness != null -> "SDR_LIKE"
            else -> "UNKNOWN"
        }
    }

    fun activeApp(state: DeepLabState): String = state.liveStatus
        .firstOrNull { it.key == "foregroundApp.appId" }
        ?.value
        .orEmpty()

    fun advice(
        signalMode: TvSignalMode,
        state: DeepLabState,
    ): List<TvOptimizationItem> {
        val picture = pictureValues(state)
        val sound = categoryValues(state, "sound")
        val oled = picture.intValue("backlight")
        val brightness = picture.intValue("brightness")
        val contrast = picture.intValue("contrast")
        val color = picture.intValue("color")
        val energySaving = picture["energySaving"]?.lowercase(Locale.ROOT)
        val soundOutput = sound["soundOutput"]
        val eArc = sound["eArcSupport"]?.lowercase(Locale.ROOT)

        return buildList {
            if (signalMode == TvSignalMode.UNKNOWN) {
                add(
                    TvOptimizationItem(
                        title = "Signalmodus festlegen",
                        detail = "Vor dem Export HLG HDR, HDR10, Dolby Vision, Gaming oder SDR auswählen. Der TV blendet HLG als „HLG HDR“ und HDR10 als „HDR“ ein.",
                    ),
                )
            }

            when (signalMode) {
                TvSignalMode.HLG_HDR,
                TvSignalMode.HDR10,
                -> {
                    addHdrBaseline(oled, brightness, contrast, color, energySaving)
                    add(
                        TvOptimizationItem(
                            title = "Bildmodus für Genauigkeit",
                            detail = "Filmmaker Mode oder Kino verwenden. Farbtemperatur Warm 50 und Farbraum Auto sind die sichere neutrale Ausgangsbasis.",
                        ),
                    )
                    add(
                        TvOptimizationItem(
                            title = "Tone Mapping bewusst wählen",
                            detail = "Dynamic Tone Mapping aus liefert die genauere HDR-Abbildung. Ein erhöht die wahrgenommene Helligkeit, verändert aber die Vorlage. HGiG nur für korrekt eingerichtetes Gaming verwenden.",
                        ),
                    )
                }

                TvSignalMode.DOLBY_VISION -> {
                    addHdrBaseline(oled, brightness, contrast, color, energySaving)
                    add(
                        TvOptimizationItem(
                            title = "Dolby-Vision-Modus",
                            detail = "Kino ist die genauere dunkle-Raum-Basis; Kino Home ist heller. Keine fremden White-Balance- oder LUT-Werte übernehmen.",
                        ),
                    )
                }

                TvSignalMode.GAME_HDR -> {
                    addHdrBaseline(oled, brightness, contrast, color, energySaving)
                    add(
                        TvOptimizationItem(
                            title = "Gaming-Pipeline",
                            detail = "Game Optimizer, ALLM und VRR verwenden. HGiG wählen, wenn die HDR-Kalibrierung der Konsole anschließend neu durchgeführt wird.",
                        ),
                    )
                    add(
                        TvOptimizationItem(
                            title = "Bildverbesserer reduzieren",
                            detail = "TruMotion, Rauschfilter und unnötige Nachschärfung für niedrige Eingabeverzögerung deaktivieren.",
                        ),
                    )
                }

                TvSignalMode.SDR_DARK -> {
                    addSdrBaseline(
                        roomLabel = "dunklen Raum",
                        preferredOledRange = "20–40 als Startbereich",
                        oled = oled,
                        brightness = brightness,
                        contrast = contrast,
                        color = color,
                        energySaving = energySaving,
                    )
                }

                TvSignalMode.SDR_BRIGHT -> {
                    addSdrBaseline(
                        roomLabel = "hellen Raum",
                        preferredOledRange = "60–80 als Startbereich",
                        oled = oled,
                        brightness = brightness,
                        contrast = contrast,
                        color = color,
                        energySaving = energySaving,
                    )
                }

                TvSignalMode.UNKNOWN -> Unit
            }

            if (soundOutput == "tv_speaker" && eArc == "on") {
                add(
                    TvOptimizationItem(
                        title = "Tonweg prüfen",
                        detail = "eARC ist eingeschaltet, der Ton läuft aber aktuell über die TV-Lautsprecher. Für Heimkino den gewünschten Ausgang bewusst in den normalen Toneinstellungen wählen.",
                    ),
                )
            }

            add(
                TvOptimizationItem(
                    title = "Keine Panelwerte blind kopieren",
                    detail = "White Balance, CMS, 1D/3D-LUT, Panel-/Tool-Optionen und Service-Menüwerte sind gerätespezifisch. Ohne Messgerät, Backup und Rollback nicht schreiben.",
                ),
            )
        }
    }

    fun enrichReport(
        report: String,
        signalMode: TvSignalMode,
        state: DeepLabState,
    ): String {
        val root = JSONObject(report)
        root.put("schemaVersion", 3)
        root.put(
            "signalContext",
            JSONObject()
                .put("declaredMode", signalMode.reportValue)
                .put("declaredDisplayName", signalMode.displayName)
                .put(
                    "declarationSource",
                    if (signalMode == TvSignalMode.UNKNOWN) {
                        "not_declared"
                    } else {
                        "user_confirmed_tv_overlay"
                    },
                )
                .put("inferredFamily", inferredSignalFamily(state))
                .put("activeApp", activeApp(state)),
        )
        root.put(
            "optimizationAdvice",
            JSONArray().also { array ->
                advice(signalMode, state).forEach { item ->
                    array.put(
                        JSONObject()
                            .put("title", item.title)
                            .put("detail", item.detail),
                    )
                }
            },
        )
        return root.toString(2)
    }

    private fun MutableList<TvOptimizationItem>.addHdrBaseline(
        oled: Int?,
        brightness: Int?,
        contrast: Int?,
        color: Int?,
        energySaving: String?,
    ) {
        if (oled == 100 && brightness == 50 && contrast == 100) {
            add(
                TvOptimizationItem(
                    title = "HDR-Grundwerte passen",
                    detail = "OLED-Licht 100, Helligkeit 50 und Kontrast 100 entsprechen einer sinnvollen HDR-Testbasis.",
                ),
            )
        } else {
            add(
                TvOptimizationItem(
                    title = "HDR-Grundwerte prüfen",
                    detail = "Als sichere Testbasis OLED-Licht 100, Helligkeit 50 und Kontrast 100 verwenden; Änderungen nur im normalen Bildmenü.",
                ),
            )
        }

        when {
            color != null && color > 55 -> add(
                TvOptimizationItem(
                    title = "Farbe wahrscheinlich zu kräftig",
                    detail = "Der Scan zeigt Farbe $color. Für eine neutrale Basis zunächst 50 testen und mit Farbbalken sowie Hauttönen vergleichen.",
                ),
            )

            color == 50 -> add(
                TvOptimizationItem(
                    title = "Farbregler neutral",
                    detail = "Farbe 50 ist eine gute Ausgangsbasis; Feinkorrekturen benötigen ein Messgerät.",
                ),
            )
        }

        if (energySaving != null && energySaving != "off") {
            add(
                TvOptimizationItem(
                    title = "Energiesparen deaktivieren",
                    detail = "Für reproduzierbare HDR-Tests Energiesparen ausschalten. OLED-Schutzfunktionen dabei nicht deaktivieren.",
                ),
            )
        }
    }

    private fun MutableList<TvOptimizationItem>.addSdrBaseline(
        roomLabel: String,
        preferredOledRange: String,
        oled: Int?,
        brightness: Int?,
        contrast: Int?,
        color: Int?,
        energySaving: String?,
    ) {
        add(
            TvOptimizationItem(
                title = "SDR-Basis für den $roomLabel",
                detail = "ISF Experte oder Filmmaker verwenden: OLED-Licht $preferredOledRange, Kontrast etwa 85, Helligkeit 50, Farbe 50, Warm 50 und Farbraum Auto.",
            ),
        )

        if (brightness != null && brightness < 47) {
            add(
                TvOptimizationItem(
                    title = "Schatten könnten absaufen",
                    detail = "Der Scan zeigt Helligkeit $brightness. Mit PLUGE/Near-Black zuerst 50 testen und nur dann in Einzelschritten anpassen.",
                ),
            )
        }

        if (contrast != null && contrast > 90) {
            add(
                TvOptimizationItem(
                    title = "SDR-Kontrast kontrollieren",
                    detail = "Kontrast $contrast ist für eine neutrale SDR-Basis hoch. Etwa 85 testen und Weiß-Clipping vergleichen.",
                ),
            )
        }

        if (color != null && color > 55) {
            add(
                TvOptimizationItem(
                    title = "SDR-Farbe reduzieren",
                    detail = "Farbe $color kann übersättigen. 50 ist die sichere Ausgangsbasis.",
                ),
            )
        }

        if (oled != null) {
            add(
                TvOptimizationItem(
                    title = "OLED-Licht ist Raumabhängig",
                    detail = "Aktuell wird $oled gelesen. Den Regler nur an die Raumhelligkeit anpassen; er ist kein Schwarzwertregler.",
                ),
            )
        }

        if (energySaving != null && energySaving != "off") {
            add(
                TvOptimizationItem(
                    title = "Konstante Helligkeit für Vergleiche",
                    detail = "Energiesparen für Mess- und Vergleichsläufe ausschalten; normale OLED-Schutzfunktionen eingeschaltet lassen.",
                ),
            )
        }
    }

    private fun pictureValues(state: DeepLabState): Map<String, String> =
        categoryValues(state, "picture")

    private fun categoryValues(
        state: DeepLabState,
        category: String,
    ): Map<String, String> = state.settings[category]
        .orEmpty()
        .associate { it.key.substringAfterLast('.') to it.value }

    private fun Map<String, String>.intValue(key: String): Int? =
        get(key)?.toIntOrNull()
}
