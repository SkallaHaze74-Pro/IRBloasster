package com.skallahaze.irbloasster.webos

internal object WebOsAppResolver {
    const val TWITCH_FALLBACK_ID = "twitch"

    fun twitchAppId(apps: List<WebOsApp>): String =
        apps.firstOrNull { app ->
            app.title.contains("twitch", ignoreCase = true) ||
                app.id.contains("twitch", ignoreCase = true)
        }?.id ?: TWITCH_FALLBACK_ID
}
