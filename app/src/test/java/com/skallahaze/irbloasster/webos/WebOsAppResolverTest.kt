package com.skallahaze.irbloasster.webos

import org.junit.Assert.assertEquals
import org.junit.Test

class WebOsAppResolverTest {
    @Test
    fun usesInstalledTwitchAppIdWhenTvReportsIt() {
        val apps = listOf(
            WebOsApp(id = "youtube.leanback.v4", title = "YouTube"),
            WebOsApp(id = "vendor.region.twitch", title = "Twitch"),
        )

        assertEquals("vendor.region.twitch", WebOsAppResolver.twitchAppId(apps))
    }

    @Test
    fun detectsTwitchByIdEvenWhenTitleIsLocalized() {
        val apps = listOf(WebOsApp(id = "com.example.twitch.lg", title = "Live-Streaming"))

        assertEquals("com.example.twitch.lg", WebOsAppResolver.twitchAppId(apps))
    }

    @Test
    fun fallsBackToStandardWebOsTwitchIdBeforeAppListLoads() {
        assertEquals("twitch", WebOsAppResolver.twitchAppId(emptyList()))
    }
}
