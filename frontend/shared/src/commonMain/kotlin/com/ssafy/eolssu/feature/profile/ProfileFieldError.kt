package com.ssafy.eolssu.feature.profile

import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.my_error_current_password_wrong
import eolssu.shared.generated.resources.my_error_nickname_empty
import eolssu.shared.generated.resources.my_error_password_empty
import eolssu.shared.generated.resources.my_error_password_mismatch
import eolssu.shared.generated.resources.my_error_password_rule
import eolssu.shared.generated.resources.my_error_password_wrong
import eolssu.shared.generated.resources.my_withdraw_proof_expired
import org.jetbrains.compose.resources.StringResource
import eolssu.shared.generated.resources.member_api_unavailable
import eolssu.shared.generated.resources.member_api_session_expired
import eolssu.shared.generated.resources.member_api_rate_limited
import eolssu.shared.generated.resources.member_api_invalid

/** 마이페이지 입력칸 아래에 띄우는 오류. */
enum class ProfileFieldError(val message: StringResource) {
    Unavailable(Res.string.member_api_unavailable),
    Unauthenticated(Res.string.member_api_session_expired),
    RateLimited(Res.string.member_api_rate_limited),
    InvalidInput(Res.string.member_api_invalid),
    NicknameEmpty(Res.string.my_error_nickname_empty),
    PasswordEmpty(Res.string.my_error_password_empty),
    PasswordWrong(Res.string.my_error_password_wrong),

    /** 비밀번호 변경(MY-03)의 현재 비밀번호. 탈퇴(MY-04)는 칸이 하나라 [PasswordWrong]을 쓴다. */
    CurrentPasswordWrong(Res.string.my_error_current_password_wrong),
    PasswordRule(Res.string.my_error_password_rule),
    PasswordMismatch(Res.string.my_error_password_mismatch),

    /** 탈퇴(MY-04) 간편 로그인 재인증 증명이 만료·재사용됐다(10분·1회). 다시 확인한다. */
    ProofExpired(Res.string.my_withdraw_proof_expired),
}
