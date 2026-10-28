package com.ssafy.eolssu.backend.common.error

import org.springframework.http.HttpStatus

/** API 명세의 오류 result.errorCode. 클라이언트는 message가 아니라 이 값으로 분기한다. */
enum class ErrorCode(val status: HttpStatus, val message: String) {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않아요"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않아요"),
    EMAIL_NOT_VERIFIED(HttpStatus.UNAUTHORIZED, "이메일 인증이 필요해요. 다시 인증해 주세요"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "허용되지 않은 요청이에요"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 정보를 찾을 수 없어요"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 메서드예요"),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "지원하지 않는 응답 형식이에요"),
    RECORDING_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "녹음 파일 크기가 너무 커요"),
    // 녹음 업로드(PRA-02, docs/specs/recording-file.md '오류 응답')
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "연습 세션을 찾을 수 없어요"),
    SESSION_NOT_RECORDING(HttpStatus.CONFLICT, "이미 녹음을 올린 연습이에요. 다시 하려면 새로 연습해 주세요"),
    SESSION_EXPIRED(HttpStatus.GONE, "녹음을 올릴 수 있는 시간이 지났어요. 다시 연습해 주세요"),
    RECORDING_UNSUPPORTED_FORMAT(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 녹음 형식이에요"),
    RECORDING_INVALID_DURATION(HttpStatus.UNPROCESSABLE_CONTENT, "녹음 길이를 확인할 수 없거나 너무 길어요"),
    RECORDING_STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "녹음을 저장하지 못했어요. 잠시 후 다시 올려 주세요"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식이에요"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했어요"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "같은 요청 키로 다른 내용이 이미 저장됐어요"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일이에요. 로그인해 주세요. 탈퇴 대기 중이면 로그인 시 탈퇴가 취소돼요"),
    TERMS_AGREEMENT_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT, "필수 약관에 동의해 주세요"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청 횟수를 초과했어요. 잠시 후 다시 시도해 주세요"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 처리할 수 없어요. 잠시 후 다시 시도해 주세요"),
    VERIFICATION_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증번호가 일치하지 않아요"),
    VERIFICATION_EXPIRED(HttpStatus.GONE, "인증번호가 만료됐어요. 새 번호를 받아 주세요"),
    STATE_CONFLICT(HttpStatus.CONFLICT, "지금 상태에서는 처리할 수 없어요. 상태를 다시 확인해 주세요"),
}
