package com.ssafy.eolssu.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler

/**
 * 화면 안에서 먼저 처리할 뒤로 가기(패널·대화상자 닫기, 미저장 확인)와 떠나기 전에 경고할 미저장 변경을 모아 둔다.
 * Web 브라우저 뒤로 가기·새로고침·탭 닫기는 화면의 `BackHandler`를 거치지 않으므로, Web 진입점이 이것을
 * [LocalScreenBackRequests]로 내려 주고 브라우저 이벤트에서 [current]·[hasUnsavedChanges]를 묻는다.
 * Android·iOS는 내려 주지 않아 등록 함수가 아무것도 하지 않는다.
 *
 * 뒤로 가기는 가장 나중에 켜진 것이 먼저 받는다. 화면 위에 연 패널·대화상자가 화면보다 나중에 켜지므로 먼저 닫힌다.
 */
class ScreenBackRequests {
    private val backs = mutableListOf<Entry>()
    private val unsaved = mutableListOf<Entry>()

    /** 브라우저 뒤로 가기를 받을 동작. 없으면 화면 이동 그래프가 한 화면 뒤로 간다. */
    fun current(): (() -> Unit)? = backs.lastOrNull()?.let { entry -> { entry.back() } }

    /** 떠나면 사라질 변경이 있는지. 등록한 화면에 그때그때 묻는다. */
    fun hasUnsavedChanges(): Boolean = unsaved.any { it.unsaved() }

    /** [onBack]을 맨 위에 올리고, 내릴 때 부를 함수를 돌려준다. */
    fun push(onBack: () -> Unit): () -> Unit = add(backs, Entry(back = onBack))

    /** 떠나기 전에 [hasUnsavedChanges]로 물을 화면을 더하고, 뺄 때 부를 함수를 돌려준다. */
    fun watchUnsaved(hasUnsavedChanges: () -> Boolean): () -> Unit = add(unsaved, Entry(unsaved = hasUnsavedChanges))

    private fun add(list: MutableList<Entry>, entry: Entry): () -> Unit {
        list += entry
        return { list.remove(entry) }
    }

    // 같은 람다를 두 번 올려도 따로 내리도록 항목마다 객체를 만든다.
    private class Entry(val back: () -> Unit = {}, val unsaved: () -> Boolean = { false })
}

/** Web에서만 내려 준다. 다른 플랫폼은 null이다. */
val LocalScreenBackRequests = staticCompositionLocalOf<ScreenBackRequests?> { null }

/**
 * 기기 뒤로 가기·Web Escape 키([BackHandler])와 Web 브라우저 뒤로 가기를 함께 받는다.
 * 패널 닫기처럼 화면을 떠나지 않고 처리할 뒤로 가기에 쓴다.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("DEPRECATION") // ui-backhandler 1.12의 BackHandler. 대신할 NavigationEventHandler는 아직 의존성에 없다.
@Composable
fun ScreenBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
    BrowserBackHandler(enabled = enabled, onBack = onBack)
}

/**
 * Web 브라우저 뒤로 가기만 받는다. 자기 창에서 기기 뒤로 가기를 먼저 받는 대화상자·하단 시트나,
 * 기기 뒤로 가기와 조건이 다른 화면이 쓴다.
 */
@Composable
fun BrowserBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val requests = LocalScreenBackRequests.current ?: return
    val currentOnBack by rememberUpdatedState(onBack)
    DisposableEffect(requests, enabled) {
        val remove = if (enabled) requests.push { currentOnBack() } else null
        onDispose { remove?.invoke() }
    }
}

/**
 * Web에서 새로고침·탭 닫기·다른 주소로 이동할 때 [hasUnsavedChanges]가 true면 브라우저의 떠나기 확인을 띄운다.
 * 브라우저가 떠날 때 한 번 물으므로 값은 상태가 아닌 함수로 받는다.
 */
@Composable
fun UnsavedChangesWarning(hasUnsavedChanges: () -> Boolean) {
    val requests = LocalScreenBackRequests.current ?: return
    val current by rememberUpdatedState(hasUnsavedChanges)
    DisposableEffect(requests) {
        val remove = requests.watchUnsaved { current() }
        onDispose(remove)
    }
}
