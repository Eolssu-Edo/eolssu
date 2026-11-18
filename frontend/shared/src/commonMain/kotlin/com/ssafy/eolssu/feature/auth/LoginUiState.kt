package com.ssafy.eolssu.feature.auth

import com.ssafy.eolssu.core.model.KoreanDateTime

/**
 * AUTH-04 이메일 로그인 입력 상태. 서버 실패 문구([error])는 비밀번호 칸 아래에, 로그인 결과 안내는 [notice] 창으로 띄운다.
 * @property emailError·passwordError 서버에 보내기 전 검사(빈 칸·이메일 형식)의 오류. 해당 칸 아래에 보인다.
 * @property rememberEmail '아이디 기억하기' 체크. 기억한 이메일이 있으면 켜진 채로 시작한다.
 * @property keepSignedIn '자동 로그인' 체크. 마지막 선택을 따르고, 고른 적이 없으면 켜져 있다.
 * @property loggingIn 서버 응답을 기다리는 중. 로그인 버튼에 도는 표시를 보이고 다시 누를 수 없다.
 * @property lockedUntil 잠김 안내의 '[HH:MM]부터 다시 시도' 시각. 서버가 알려 주지 않았으면 null.
 */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: FieldError? = null,
    val passwordError: FieldError? = null,
    val error: FieldError? = null,
    val rememberEmail: Boolean = false,
    val keepSignedIn: Boolean = true,
    val loggingIn: Boolean = false,
    val notice: LoginNotice? = null,
    val lockedUntil: KoreanDateTime? = null,
)

/**
 * 다시 로그인할 수 있는 시각(ms)을 한국 시각으로 바꾼다. 초가 남아 있으면 다음 분으로 올려,
 * 안내한 시각에 시도했는데 아직 잠겨 있는 일이 없게 한다.
 */
fun lockRetryTime(retryAvailableAtMillis: Long?): KoreanDateTime? = retryAvailableAtMillis?.let {
    val minute = 60_000L
    KoreanDateTime.fromEpochMillis(it.floorDiv(minute) * minute + if (it.mod(minute) > 0) minute else 0)
}

/** 잠김 안내의 'HH:MM'(24시간제, 두 자리). */
fun clockLabel(time: KoreanDateTime): String =
    "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

/** 로그인 결과 안내 창. */
enum class LoginNotice {
    /** 같은 이메일로 15분 안에 5번 실패해 15분 동안 막혔다. */
    Locked,

    /** 탈퇴 대기 계정으로 로그인해 탈퇴가 취소됐다. 확인을 누르면 홈으로 간다. */
    WithdrawalCancelled,

    /** 마이페이지에서 탈퇴를 신청하고 로그아웃되어 왔다(S15P31E102-768). 확인을 누르면 닫힌다. */
    Withdrawn,
}
