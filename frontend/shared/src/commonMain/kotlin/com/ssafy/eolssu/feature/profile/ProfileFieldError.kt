package com.ssafy.eolssu.feature.profile

import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_error_password_too_long
import eolssu.shared.generated.resources.my_error_current_password_wrong
import eolssu.shared.generated.resources.my_error_inquiry_content_empty
import eolssu.shared.generated.resources.my_error_inquiry_title_empty
import eolssu.shared.generated.resources.my_error_inquiry_unavailable
import eolssu.shared.generated.resources.my_error_nickname_empty
import eolssu.shared.generated.resources.my_error_nickname_too_long
import eolssu.shared.generated.resources.my_error_password_empty
import eolssu.shared.generated.resources.my_error_password_mismatch
import eolssu.shared.generated.resources.my_error_password_rule
import eolssu.shared.generated.resources.my_error_password_same_as_current
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

    /** 닉네임이 [com.ssafy.eolssu.core.model.NicknameRule.MAX_LENGTH]자를 넘었다. 입력하는 동안에도 바로 보인다. */
    NicknameTooLong(Res.string.my_error_nickname_too_long),
    PasswordEmpty(Res.string.my_error_password_empty),
    PasswordWrong(Res.string.my_error_password_wrong),

    /** 비밀번호 변경(MY-03)의 현재 비밀번호. 탈퇴(MY-04)는 칸이 하나라 [PasswordWrong]을 쓴다. */
    CurrentPasswordWrong(Res.string.my_error_current_password_wrong),

    /** 새 비밀번호가 조건을 덜 채웠다. 화면은 문장 대신 가입과 같은 체크리스트로 보인다. */
    PasswordRule(Res.string.my_error_password_rule),

    /** 새 비밀번호가 조건은 채웠지만 64자를 넘었다. 가입과 같은 문구를 쓴다. */
    PasswordTooLong(Res.string.auth_error_password_too_long),

    /** 새 비밀번호가 현재 비밀번호와 같다. 서버도 거절하므로(400) 보내기 전에 새 비밀번호 칸에서 알린다. */
    PasswordSameAsCurrent(Res.string.my_error_password_same_as_current),
    PasswordMismatch(Res.string.my_error_password_mismatch),

    /** 탈퇴(MY-04) 간편 로그인 재인증 증명이 만료·재사용됐다(10분·1회). 다시 확인한다. */
    ProofExpired(Res.string.my_withdraw_proof_expired),

    /** 문의하기(S15P31E102-1240)의 빈 제목·빈 내용과, 메일을 보내지 못한 서버 응답(503 등). */
    InquiryTitleEmpty(Res.string.my_error_inquiry_title_empty),
    InquiryContentEmpty(Res.string.my_error_inquiry_content_empty),
    InquiryUnavailable(Res.string.my_error_inquiry_unavailable),
}
