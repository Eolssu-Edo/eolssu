package com.ssafy.eolssu.app.navigation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 홈과 마이페이지 사이에서만 최근 연주 선조회 작업을 공유한다. */
internal class RecentPracticePrefetch(private val scope: CoroutineScope) {
    private var request: Job? = null

    fun start(load: suspend () -> Unit) {
        if (request?.isActive == true) return
        request = scope.launch { load() }
    }

    fun onDestinationChanged(route: String?) {
        if (route != EolssuDestination.HOME && route != ProfileDestination.MY_PAGE) cancel()
    }

    fun cancel() {
        request?.cancel()
        request = null
    }
}
