package com.ssafy.eolssu.core.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ScreenBackRequestsTest {
    @Test fun latestOpenedBackTakesTheBrowserBackFirst() {
        val requests = ScreenBackRequests()
        val calls = mutableListOf<String>()
        assertNull(requests.current(), "등록이 없으면 화면 이동 그래프가 뒤로 간다")
        val removeEditor = requests.push { calls += "editor" }
        val removePanel = requests.push { calls += "panel" }
        requests.current()?.invoke()
        removePanel()
        requests.current()?.invoke()
        removeEditor()
        assertEquals(listOf("panel", "editor"), calls, "패널이 먼저 닫히고 그다음 편집기가 받는다")
        assertNull(requests.current())
    }

    @Test fun sameBackPushedTwiceIsRemovedOneAtATime() {
        val requests = ScreenBackRequests()
        val back = {}
        val first = requests.push(back)
        requests.push(back)
        first()
        assertTrue(requests.current() != null, "다른 등록은 남는다")
    }

    @Test fun unsavedWarningAsksEachWatcherWhenLeaving() {
        val requests = ScreenBackRequests()
        var dirty = false
        val remove = requests.watchUnsaved { dirty }
        assertFalse(requests.hasUnsavedChanges())
        dirty = true
        assertTrue(requests.hasUnsavedChanges(), "떠나는 순간의 값을 묻는다")
        remove()
        assertFalse(requests.hasUnsavedChanges(), "화면이 사라지면 묻지 않는다")
    }
}
