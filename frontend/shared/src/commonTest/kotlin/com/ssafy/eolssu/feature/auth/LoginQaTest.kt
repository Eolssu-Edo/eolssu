package com.ssafy.eolssu.feature.auth

import com.ssafy.eolssu.core.diagnostics.*
import com.ssafy.eolssu.data.user.*
import com.ssafy.eolssu.testing.runImmediately
import kotlin.test.*

class LoginQaTest {
    @AfterTest fun cleanup() { UserActionLog.sink = null }
    @Test fun validationSuccessFailureAndLockUseActualResultWithoutCredentials() {
        val events = mutableListOf<UserActionEvent>()
        UserActionLog.sink = { events += it }
        var response: LoginResult = LoginResult.WrongCredentials
        var calls = 0
        val repository = object : AuthRepository by SampleAuthRepository({ 0 }) {
            override suspend fun logIn(email: String, password: String): LoginResult { calls++; return response }
        }
        val vm = LoginViewModel(repository)
        assertFalse(runImmediately { vm.logIn() })
        assertEquals(0, calls)
        assertEquals(QaOutcome.INVALID, events.last().outcome)
        vm.changeEmail("qa1051-canary@example.invalid")
        vm.changePassword("qa1051-private-password")
        for ((result, outcome) in listOf(LoginResult.WrongCredentials to QaOutcome.FAILED,
            LoginResult.Locked(null) to QaOutcome.LOCKED, LoginResult.Unavailable to QaOutcome.UNAVAILABLE,
            LoginResult.Success(false) to QaOutcome.READY)) {
            response = result
            assertEquals(result is LoginResult.Success, runImmediately { vm.logIn() })
            assertEquals(outcome, events.last { it.kind == QaKind.LOGIN_RESULT }.outcome)
        }
        val encoded = events.joinToString { it.encode() }
        assertFalse(encoded.contains("qa1051-canary"))
        assertFalse(encoded.contains("qa1051-private-password"))
        assertFalse(encoded.contains("length"))
        assertEquals(4, calls)
    }
}
