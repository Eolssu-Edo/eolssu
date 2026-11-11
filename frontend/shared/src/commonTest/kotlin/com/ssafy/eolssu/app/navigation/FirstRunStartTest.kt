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
        assertEquals(AuthDestination.LOGIN, startDestination(false, sessionExpired = true))
    }

    @Test
    fun signedOutCompletedDeviceStartsAtLoginWithoutExpiredNotice() {
        // 로그아웃 뒤 다시 켜면 로그인에서 시작하고, 만료 안내는 세션 만료일 때만 띄운다.
        assertEquals(AuthDestination.LOGIN, startDestination(true, signedIn = false))
        assertTrue(startNeedsLogin(firstRunCompleted = true, sessionExpired = false, signedIn = false))
        assertTrue(startNeedsLogin(firstRunCompleted = true, sessionExpired = true, signedIn = false))
        assertFalse(startNeedsLogin(firstRunCompleted = true, sessionExpired = false, signedIn = true))
        // 처음 켠 기기도 마이크 안내 없이 로그인에서 시작한다(마이크 권한은 녹음을 시작할 때 받는다).
        assertEquals(AuthDestination.LOGIN, startDestination(false, signedIn = false))
        assertTrue(startNeedsLogin(firstRunCompleted = false, sessionExpired = false, signedIn = false))
    }

    // TEMP S15P31E102-309: QA 옵션 제거 시 아래 두 검사도 제거한다.
    @Test
    fun qaFirstLaunchSkipsOnlyConsentWithoutPersistingCompletion() {
        val repository = FirstRunRepository(FakeKeyValueStore())
        assertEquals(AuthDestination.LOGIN, startDestination(repository.isCompleted(), qaSkipConsent = true))
        assertFalse(repository.isCompleted())
        assertEquals(AuthDestination.LOGIN, startDestination(repository.isCompleted()))
    }

    @Test
    fun qaCompletedLaunchStillStartsAtHome() {
        assertEquals(EolssuDestination.HOME, startDestination(true, qaSkipConsent = true))
    }

    @Test
    fun firstLaunchStartsAtLoginWithoutMicNotice() {
        val repository = FirstRunRepository(FakeKeyValueStore())

        assertFalse(repository.isCompleted())
        assertEquals(AuthDestination.LOGIN, startDestination(repository.isCompleted()))
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
