package com.ssafy.eolssu.platform.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExternalBrowserSelectionTest {
    @Test fun appBrowserDefaultFallsBackToSupportedExternalBrowser() {
        assertEquals("com.android.chrome", selectExternalBrowserPackage(
            defaultPackage = "com.nhn.android.search",
            customTabsPackages = listOf("com.android.chrome", "com.sec.android.app.sbrowser"),
            viewHandlerPackages = setOf("com.nhn.android.search", "com.android.chrome", "com.sec.android.app.sbrowser"),
        ))
    }

    @Test fun supportedDefaultBrowserWinsEvenWhenListedLater() {
        assertEquals("com.sec.android.app.sbrowser", selectExternalBrowserPackage(
            defaultPackage = "com.sec.android.app.sbrowser",
            customTabsPackages = listOf("com.android.chrome", "com.sec.android.app.sbrowser"),
            viewHandlerPackages = setOf("com.android.chrome", "com.sec.android.app.sbrowser"),
        ))
    }

    @Test fun serviceProviderWithoutHttpsHandlerCannotBeSelected() {
        assertEquals("com.android.chrome", selectExternalBrowserPackage(
            defaultPackage = "service.only",
            customTabsPackages = listOf("service.only", "com.android.chrome"),
            viewHandlerPackages = setOf("com.android.chrome"),
        ))
    }

    @Test fun missingCompatibleBrowserDoesNotFallBackToAppBrowser() {
        assertNull(selectExternalBrowserPackage(
            defaultPackage = "com.nhn.android.search",
            customTabsPackages = emptyList(),
            viewHandlerPackages = setOf("com.nhn.android.search"),
        ))
        assertNull(selectExternalBrowserPackage(
            defaultPackage = null,
            customTabsPackages = listOf("service.only"),
            viewHandlerPackages = emptySet(),
        ))
    }
}
