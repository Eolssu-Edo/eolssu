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
    fun expiredSessionRedirectPreservesPendingAuthenticationAndCallbackReceivers() {
        for (route in listOf(AuthDestination.OAUTH, AuthDestination.SOCIAL_PROFILE)) {
            assertFalse(shouldRedirectExpiredSession(route))
        }
        assertTrue(shouldRedirectExpiredSession(EolssuDestination.MY_SONGS))
        assertTrue(shouldRedirectExpiredSession(ProfileDestination.MY_PAGE))
        assertTrue(shouldRedirectExpiredSession(ProfileDestination.LINK))
        assertTrue(shouldRedirectExpiredSession(ProfileDestination.WITHDRAW))
        assertTrue(shouldRedirectExpiredSession(AuthDestination.LOGIN))
    }

    @Test
    fun expiredSessionReturnsCompletedDeviceToLoginWithoutSkippingFirstRun() {
        assertEquals(AuthDestination.LOGIN, startDestination(true, sessionExpired = true))
        assertEquals(AuthDestination.LOGIN, startDestination(true, qaSkipConsent = true, sessionExpired = true))
        assertEquals(AuthDestination.MIC, startDestination(false, sessionExpired = true))
    }

    // TEMP S15P31E102-309: QA 옵션 제거 시 아래 두 검사도 제거한다.
    @Test
    fun qaFirstLaunchSkipsOnlyConsentWithoutPersistingCompletion() {
        val repository = FirstRunRepository(FakeKeyValueStore())
        assertEquals(AuthDestination.MIC, startDestination(repository.isCompleted(), qaSkipConsent = true))
        assertFalse(repository.isCompleted())
        assertEquals(AuthDestination.MIC, startDestination(repository.isCompleted()))
    }

    @Test
    fun qaCompletedLaunchStillStartsAtHome() {
        assertEquals(EolssuDestination.HOME, startDestination(true, qaSkipConsent = true))
    }

    @Test
    fun firstLaunchStartsAtMicNoticeBecauseConsentIsInSignup() {
        val repository = FirstRunRepository(FakeKeyValueStore())

        assertFalse(repository.isCompleted())
        assertEquals(AuthDestination.MIC, startDestination(repository.isCompleted()))
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
