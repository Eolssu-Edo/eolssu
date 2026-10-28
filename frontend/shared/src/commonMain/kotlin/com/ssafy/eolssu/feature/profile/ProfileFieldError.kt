package com.ssafy.eolssu.feature.profile

import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.my_error_nickname_empty
import eolssu.shared.generated.resources.my_error_password_empty
import eolssu.shared.generated.resources.my_error_password_mismatch
import eolssu.shared.generated.resources.my_error_password_rule
import eolssu.shared.generated.resources.my_error_password_wrong
import org.jetbrains.compose.resources.StringResource

/** 마이페이지 입력칸 아래에 띄우는 오류. */
enum class ProfileFieldError(val message: StringResource) {
    NicknameEmpty(Res.string.my_error_nickname_empty),
    PasswordEmpty(Res.string.my_error_password_empty),
    PasswordWrong(Res.string.my_error_password_wrong),
    PasswordRule(Res.string.my_error_password_rule),
    PasswordMismatch(Res.string.my_error_password_mismatch),
}
