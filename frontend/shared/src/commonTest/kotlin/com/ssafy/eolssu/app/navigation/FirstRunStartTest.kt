package com.ssafy.eolssu.app.navigation

import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.user.FirstRunRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 첫 실행 표시(`FirstRunRepository`)와 앱 시작 화면. */
class FirstRunStartTest {
    @Test
    fun firstLaunchStartsAtConsent() {
        val repository = FirstRunRepository(FakeKeyValueStore())

        assertFalse(repository.isCompleted())
        assertEquals(AuthDestination.CONSENT, startDestination(repository.isCompleted()))
    }

    @Test
    fun completedFlagSurvivesRelaunchAndStartsAtHome() {
        val store = FakeKeyValueStore()
        FirstRunRepository(store).markCompleted()

        // 앱을 다시 켜면 저장소는 남고 Repository는 새로 만들어진다.
        val relaunched = FirstRunRepository(store)

        assertTrue(relaunched.isCompleted())
        assertEquals(EolssuDestination.HOME, startDestination(relaunched.isCompleted()))
    }

    @Test
    fun unrelatedValueDoesNotCountAsCompleted() {
        val store = FakeKeyValueStore()
        store.putString("first_run_completed", "false")

        assertFalse(FirstRunRepository(store).isCompleted())
    }
}
