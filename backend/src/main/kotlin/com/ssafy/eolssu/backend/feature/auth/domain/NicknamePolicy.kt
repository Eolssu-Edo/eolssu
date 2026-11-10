package com.ssafy.eolssu.backend.feature.auth.domain

/** 가입·소셜 가입·닉네임 수정 공통 규칙: 공백이 아닌 최대 10자. 길이는 @Size와 같은 String.length(UTF-16) 기준이다. 기존 저장값은 검사하지 않는다. */
object NicknamePolicy {
    const val MAX_LENGTH = 10
}
