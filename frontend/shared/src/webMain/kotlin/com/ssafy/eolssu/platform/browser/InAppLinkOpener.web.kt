package com.ssafy.eolssu.core.browser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler

/** 기본 링크 열기와 같다. */
@Composable
actual fun rememberInAppLinkOpener(): (String) -> Unit {
    val handler = LocalUriHandler.current
    return remember(handler) { { url -> handler.openUri(url) } }
}
