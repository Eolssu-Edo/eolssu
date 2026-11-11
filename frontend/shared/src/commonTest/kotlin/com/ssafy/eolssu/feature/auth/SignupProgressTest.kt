package com.ssafy.eolssu.feature.auth

import com.ssafy.eolssu.feature.auth.SignupStep.Consent
import com.ssafy.eolssu.feature.auth.SignupStep.EmailVerification
import com.ssafy.eolssu.feature.auth.SignupStep.Password
import com.ssafy.eolssu.feature.auth.SignupStep.Profile
import com.ssafy.eolssu.feature.auth.SignupStepStatus.Current
import com.ssafy.eolssu.feature.auth.SignupStepStatus.Done
import com.ssafy.eolssu.feature.auth.SignupStepStatus.Upcoming
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SignupProgressTest {
    @Test
    fun emailSignupHasFourStepsInNavigationOrder() {
        assertEquals(listOf(Consent, EmailVerification, Password, Profile), SignupPath.Email.steps)
    }

    @Test
    fun socialSignupSkipsEmailVerificationAndPassword() {
        assertEquals(listOf(Consent, Profile), SignupPath.Social.steps)
    }

    @Test
    fun eachEmailStepShowsItsOwnNumberAndStatuses() {
        val expected = mapOf(
            Consent to listOf(Current, Upcoming, Upcoming, Upcoming),
            EmailVerification to listOf(Done, Current, Upcoming, Upcoming),
            Password to listOf(Done, Done, Current, Upcoming),
            Profile to listOf(Done, Done, Done, Current),
        )
        SignupPath.Email.steps.forEachIndexed { index, step ->
            val progress = SignupProgress(SignupPath.Email, step)
            assertEquals(index + 1, progress.number)
            assertEquals(4, progress.total)
            assertEquals(expected.getValue(step), progress.steps.map(progress::statusOf), "$step")
        }
    }

    @Test
    fun socialProfileIsTheLastOfTwoSteps() {
        val progress = SignupProgress(SignupPath.Social, Profile)
        assertEquals(2, progress.number)
        assertEquals(2, progress.total)
        assertEquals(listOf(Done, Current), progress.steps.map(progress::statusOf))
        assertNull(progress.next)
    }

    @Test
    fun nextStepFollowsThePath() {
        assertEquals(EmailVerification, SignupProgress(SignupPath.Email, Consent).next)
        assertEquals(Profile, SignupProgress(SignupPath.Email, Password).next)
        assertEquals(Profile, SignupProgress(SignupPath.Social, Consent).next)
        assertNull(SignupProgress(SignupPath.Email, Profile).next)
    }

    @Test
    fun firstStepShownWithoutHistoryIsFresh() {
        val transitions = SignupStepTransitions()
        assertEquals(SignupStepEntry.Fresh, transitions.enter(SignupProgress(SignupPath.Email, Consent)))
    }

    @Test
    fun movingForwardStartsFromThePreviousStep() {
        val transitions = SignupStepTransitions()
        transitions.enter(SignupProgress(SignupPath.Email, Consent))
        assertEquals(SignupStepEntry.Moved(fromIndex = 0), transitions.enter(SignupProgress(SignupPath.Email, EmailVerification)))
        assertEquals(SignupStepEntry.Moved(fromIndex = 1), transitions.enter(SignupProgress(SignupPath.Email, Password)))
    }

    @Test
    fun goingBackStartsFromTheLaterStep() {
        val transitions = SignupStepTransitions()
        transitions.enter(SignupProgress(SignupPath.Email, Consent))
        transitions.enter(SignupProgress(SignupPath.Email, EmailVerification))
        assertEquals(SignupStepEntry.Moved(fromIndex = 1), transitions.enter(SignupProgress(SignupPath.Email, Consent)))
    }

    @Test
    fun redrawingTheSameStepDoesNotMove() {
        val transitions = SignupStepTransitions()
        transitions.enter(SignupProgress(SignupPath.Email, Password))
        assertEquals(SignupStepEntry.Same, transitions.enter(SignupProgress(SignupPath.Email, Password)))
    }

    @Test
    fun socialProfileAfterConsentMovesOneStep() {
        val transitions = SignupStepTransitions()
        transitions.enter(SignupProgress(SignupPath.Social, Consent))
        assertEquals(SignupStepEntry.Moved(fromIndex = 0), transitions.enter(SignupProgress(SignupPath.Social, Profile)))
    }

    @Test
    fun newSignupAfterFinishingOrSwitchingPathIsFresh() {
        val transitions = SignupStepTransitions()
        transitions.enter(SignupProgress(SignupPath.Email, Profile))
        // 지난 가입의 마지막 단계에서 새 가입의 첫 단계로 거꾸로 달리지 않는다.
        assertEquals(SignupStepEntry.Fresh, transitions.enter(SignupProgress(SignupPath.Email, Consent)))
        assertEquals(SignupStepEntry.Fresh, transitions.enter(SignupProgress(SignupPath.Social, Consent)))
    }

    @Test
    fun passwordResetIsEmailVerificationThenPassword() {
        assertEquals(listOf(EmailVerification, Password), SignupPath.PasswordReset.steps)
        val verify = SignupProgress(SignupPath.PasswordReset, EmailVerification)
        assertEquals(1, verify.number)
        assertEquals(2, verify.total)
        assertEquals(Password, verify.next)
        val password = SignupProgress(SignupPath.PasswordReset, Password)
        assertEquals(2, password.number)
        assertEquals(listOf(Done, Current), password.steps.map(password::statusOf))
        assertNull(password.next)
    }

    @Test
    fun passwordResetMovesBetweenItsTwoStepsAndStartsFreshAfterSignup() {
        val transitions = SignupStepTransitions()
        transitions.enter(SignupProgress(SignupPath.Email, EmailVerification))
        // 가입 중 보던 이메일 인증 단계에서 비밀번호 찾기로 와도 이어 움직이지 않는다.
        assertEquals(SignupStepEntry.Fresh, transitions.enter(SignupProgress(SignupPath.PasswordReset, EmailVerification)))
        assertEquals(SignupStepEntry.Moved(fromIndex = 0), transitions.enter(SignupProgress(SignupPath.PasswordReset, Password)))
        assertEquals(SignupStepEntry.Moved(fromIndex = 1), transitions.enter(SignupProgress(SignupPath.PasswordReset, EmailVerification)))
    }

    @Test
    fun stepOutsideThePathIsRejected() {
        assertFailsWith<IllegalArgumentException> { SignupProgress(SignupPath.PasswordReset, Consent) }
        assertFailsWith<IllegalArgumentException> { SignupProgress(SignupPath.Social, Password) }
        assertFailsWith<IllegalArgumentException> { SignupProgress(SignupPath.Social, Consent).statusOf(EmailVerification) }
    }
}
