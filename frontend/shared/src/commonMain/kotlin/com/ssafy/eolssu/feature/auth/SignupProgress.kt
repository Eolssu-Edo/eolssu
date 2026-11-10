package com.ssafy.eolssu.feature.auth

import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_signup_step_consent
import eolssu.shared.generated.resources.auth_signup_step_email
import eolssu.shared.generated.resources.auth_signup_step_password
import eolssu.shared.generated.resources.auth_signup_step_profile
import org.jetbrains.compose.resources.StringResource
import kotlin.math.abs

/** 회원가입 단계. 가입 화면 하나가 한 단계다. */
enum class SignupStep(val label: StringResource) {
    /** AUTH-01 약관 동의. */
    Consent(Res.string.auth_signup_step_consent),

    /** AUTH-06 이메일 인증. */
    EmailVerification(Res.string.auth_signup_step_email),

    /** AUTH-08 비밀번호 설정. */
    Password(Res.string.auth_signup_step_password),

    /** AUTH-07 프로필 설정(닉네임·악기). */
    Profile(Res.string.auth_signup_step_profile),
}

/**
 * 가입 경로별로 거치는 단계. 화면 이동 그래프(`app/navigation/AuthNavigation`)의 실제 순서와 같다.
 * 간편 로그인 가입은 제공자가 이메일을 확인하므로 이메일 인증·비밀번호 단계가 없다.
 */
enum class SignupPath(val steps: List<SignupStep>) {
    Email(listOf(SignupStep.Consent, SignupStep.EmailVerification, SignupStep.Password, SignupStep.Profile)),
    Social(listOf(SignupStep.Consent, SignupStep.Profile)),
}

/** 단계 표시에서 한 단계의 상태. */
enum class SignupStepStatus { Done, Current, Upcoming }

/**
 * [path]의 [current] 단계에 있을 때의 진행 상태. 각 가입 화면이 자기 단계를 넘기므로,
 * 앞뒤로 이동하거나 다시 방문해도 지금 떠 있는 화면과 표시가 어긋나지 않는다.
 */
data class SignupProgress(val path: SignupPath, val current: SignupStep) {
    init {
        require(current in path.steps) { "$current is not a step of $path signup" }
    }

    val steps: List<SignupStep> get() = path.steps

    /** 1부터 세는 지금 단계 번호. */
    val number: Int get() = steps.indexOf(current) + 1

    val total: Int get() = steps.size

    /** 다음 단계. 마지막 단계면 없다. */
    val next: SignupStep? get() = steps.getOrNull(number)

    /** 0부터 세는 지금 단계 자리. */
    val index: Int get() = number - 1

    fun statusOf(step: SignupStep): SignupStepStatus {
        val index = steps.indexOf(step)
        require(index >= 0) { "$step is not a step of $path signup" }
        return when {
            index < this.index -> SignupStepStatus.Done
            index == this.index -> SignupStepStatus.Current
            else -> SignupStepStatus.Upcoming
        }
    }
}

/** 단계 화면에 들어올 때 단계 표시가 어떻게 나타날지. */
sealed interface SignupStepEntry {
    /** 가입을 새로 시작했거나 앱을 다시 켜서 직전 단계를 모른다. 움직임 없이 지금 단계에 도착 표시만 한다. */
    data object Fresh : SignupStepEntry

    /** 화면 회전처럼 같은 단계를 다시 그린다. 움직이지 않는다. */
    data object Same : SignupStepEntry

    /** 바로 앞(다음 단계로 이동)이나 바로 뒤(뒤로 가기) 단계에서 왔다. [fromIndex] 자리에서 지금 자리로 움직인다. */
    data class Moved(val fromIndex: Int) : SignupStepEntry
}

/**
 * 직전에 보인 가입 단계를 기억해, 새로 그리는 단계 표시가 어디에서 출발할지 정한다.
 * 가입 화면은 단계마다 따로 그려지므로 화면끼리 이 기억을 나눠 쓴다. 기억은 앱이 떠 있는 동안만 남아,
 * 앱을 다시 켜거나 웹을 새로고침하면 [SignupStepEntry.Fresh]로 지금 단계를 바로 보인다(표시는 항상 지금 화면의 단계다).
 */
class SignupStepTransitions {
    private var last: SignupProgress? = null

    /** [progress] 화면이 처음 그려질 때 한 번 부른다. 들어온 방식을 돌려주고 [progress]를 직전 단계로 기억한다. */
    fun enter(progress: SignupProgress): SignupStepEntry {
        val previous = last
        last = progress
        return when {
            previous == null || previous.path != progress.path -> SignupStepEntry.Fresh
            previous.current == progress.current -> SignupStepEntry.Same
            // 가입 화면은 한 단계씩만 오간다. 멀리 떨어진 단계(예: 지난 가입의 마지막 단계)에서 온 것은 새 가입으로 본다.
            abs(previous.index - progress.index) == 1 -> SignupStepEntry.Moved(previous.index)
            else -> SignupStepEntry.Fresh
        }
    }

    companion object {
        /** 가입 화면들이 함께 쓰는 기억. */
        val Shared = SignupStepTransitions()
    }
}
