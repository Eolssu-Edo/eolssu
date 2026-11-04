package com.ssafy.eolssu.platform.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExternalBrowserSelectionTest {
    @Test fun appBrowserDefaultFallsBackToSupportedExternalBrowser() {
        assertEquals("com.android.chrome", selectExternalBrowserPackage(
            defaultPackage = "com.nhn.android.search",
            customTabsPackages = listOf("com.android.chrome", "com.sec.android.app.sbrowser"),
            canOpenPackage = { it in setOf("com.android.chrome", "com.sec.android.app.sbrowser") },
        ))
    }

    @Test fun eachServiceProviderIsCheckedWithoutDependingOnGlobalWebHandlers() {
        val checked = mutableListOf<String>()
        val selected = selectExternalBrowserPackage(
            defaultPackage = "com.nhn.android.search",
            customTabsPackages = listOf("service.only", "com.android.chrome", "com.android.chrome"),
            canOpenPackage = { candidate ->
                checked += candidate
                candidate == "com.android.chrome"
            },
        )
        assertEquals("com.android.chrome", selected)
        assertEquals(listOf("service.only", "com.android.chrome"), checked)
    }

    @Test fun supportedDefaultBrowserWinsEvenWhenListedLater() {
        assertEquals("com.sec.android.app.sbrowser", selectExternalBrowserPackage(
            defaultPackage = "com.sec.android.app.sbrowser",
            customTabsPackages = listOf("com.android.chrome", "com.sec.android.app.sbrowser"),
            canOpenPackage = { it in setOf("com.android.chrome", "com.sec.android.app.sbrowser") },
        ))
    }

    @Test fun serviceProviderWithoutHttpsHandlerCannotBeSelected() {
        assertEquals("com.android.chrome", selectExternalBrowserPackage(
            defaultPackage = "service.only",
            customTabsPackages = listOf("service.only", "com.android.chrome"),
            canOpenPackage = { it == "com.android.chrome" },
        ))
    }

    @Test fun missingCompatibleBrowserDoesNotFallBackToAppBrowser() {
        assertNull(selectExternalBrowserPackage(
            defaultPackage = "com.nhn.android.search",
            customTabsPackages = emptyList(),
            canOpenPackage = { it == "com.nhn.android.search" },
        ))
        assertNull(selectExternalBrowserPackage(
            defaultPackage = null,
            customTabsPackages = listOf("service.only"),
            canOpenPackage = { false },
        ))
    }
}
