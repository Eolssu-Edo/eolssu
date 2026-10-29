package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorCode.*
import com.ssafy.eolssu.backend.feature.auth.api.*
import com.ssafy.eolssu.backend.feature.user.api.*
import com.ssafy.eolssu.backend.feature.term.api.TermsResult
import com.ssafy.eolssu.backend.feature.growth.api.*
import com.ssafy.eolssu.backend.feature.practice.api.*
import com.ssafy.eolssu.backend.feature.analysis.api.RetryResult
import com.ssafy.eolssu.backend.feature.song.api.*
import io.swagger.v3.oas.models.PathItem.HttpMethod

/** ResponseEntity<*>의 응답 타입 소실을 보완하는 문서 계약. 요청 DTO/매핑은 springdoc이 실제 컨트롤러에서 읽는다. */
internal data class ApiContract(
    val method: HttpMethod,
    val path: String,
    val id: String,
    val tag: String,
    val summary: String,
    val description: String,
    val result: Class<*>?,
    val statuses: List<Int> = listOf(200),
    val errors: List<ErrorCode> = emptyList(),
    val security: String? = "accessCookie",
    val setsCookies: Boolean = false,
)

internal val apiContracts = listOf(
    ApiContract(HttpMethod.GET,"/api/songs","listSongs","곡","게시 곡 목록", "게시·미삭제 곡과 원본 공통 악보, 장단을 ID 순으로 반환합니다.", SongListResult::class.java,security=null),
    ApiContract(HttpMethod.GET,"/api/songs/{songId}","getSong","곡","게시 곡 상세", "숫자 곡 ID와 악보 버전·원본 음표 식별자를 보존합니다.", SongItem::class.java,errors=listOf(RESOURCE_NOT_FOUND),security=null),
    ApiContract(HttpMethod.POST,"/api/auth/sign-in","signIn","인증","이메일 로그인",
        "이메일·비밀번호로 로그인하고 access_token/refresh_token HttpOnly 쿠키를 발급합니다. 15분 내 5회 실패 시 15분 잠금. 탈퇴 대기는 로그인 성공 시에만 취소합니다.",
        SignedIn::class.java,errors=listOf(INVALID_CREDENTIALS,TOO_MANY_REQUESTS,SERVICE_UNAVAILABLE),security=null,setsCookies=true),
    ApiContract(HttpMethod.POST,"/api/auth/sign-up","signUp","인증","이메일 회원가입",
        "SIGN_UP 인증 완료 토큰(30분)을 1회 소비합니다. 비밀번호는 영문·숫자·특수문자 포함 8~128자, 필수 약관에 동의해야 합니다. 성공 시 로그인 쿠키를 발급합니다.",
        SignedUp::class.java,listOf(201),listOf(EMAIL_NOT_VERIFIED,EMAIL_ALREADY_EXISTS,TERMS_AGREEMENT_REQUIRED,SERVICE_UNAVAILABLE),null,true),
    ApiContract(HttpMethod.POST,"/api/auth/refresh","refreshTokens","인증","로그인 토큰 재발급",
        "access_token 없이 refresh_token 쿠키로 회전합니다. 재사용된 ROTATED 토큰은 해당 회원의 refresh를 전부 폐기합니다. 요청 본문은 없습니다.",
        Refreshed::class.java,errors=listOf(UNAUTHENTICATED,SERVICE_UNAVAILABLE),security="refreshCookie",setsCookies=true),
    ApiContract(HttpMethod.POST,"/api/auth/sign-out","signOut","인증","로그아웃",
        "현재 기기의 refresh를 폐기하고 로그인 쿠키를 지웁니다. refresh_token 쿠키는 선택입니다. 요청 본문은 없습니다.",
        SignedOut::class.java,setsCookies=true),
    ApiContract(HttpMethod.POST,"/api/auth/email-verifications","sendEmailVerification","인증","이메일 인증번호 발송",
        "SIGN_UP·PASSWORD_RESET·ACCOUNT_MERGE 목적. 번호 3분, 재발송 60초, KST 하루 목적 합산 10회. 재설정은 DB 등록 주소로만 보내며 미가입/메일 장애도 동일한 202로 숨깁니다.",
        SentVerification::class.java,listOf(202),listOf(EMAIL_ALREADY_EXISTS,TOO_MANY_REQUESTS,SERVICE_UNAVAILABLE),null),
    ApiContract(HttpMethod.POST,"/api/auth/email-verifications/{verificationId}/confirm","confirmEmailVerification","인증","이메일 인증번호 확인",
        "6자리 문자열 인증번호를 확인해 30분 유효한 verificationToken을 발급합니다. 오답은 최대 5회이며 같은 번호를 다시 확인할 수 없습니다.",
        VerifiedEmail::class.java,errors=listOf(RESOURCE_NOT_FOUND,VERIFICATION_EXPIRED,TOO_MANY_REQUESTS,VERIFICATION_CODE_MISMATCH,SERVICE_UNAVAILABLE),security=null),
    ApiContract(HttpMethod.POST,"/api/auth/password-reset","resetPassword","인증","비밀번호 재설정",
        "PASSWORD_RESET 토큰으로 비밀번호를 바꾸고 refresh를 전부 폐기합니다. 자동 로그인·탈퇴 취소 없이 204 빈 본문. 동일 비밀번호는 400, 무효 증명은 401. 기존 실패 잠금 카운터는 유지합니다.",
        null,listOf(204),listOf(EMAIL_NOT_VERIFIED),null),
    ApiContract(HttpMethod.GET,"/api/terms","listTerms","약관","시행 중인 약관 목록",
        "code별 현재 시행 버전 1개를 반환합니다. 시행 시각이 같으면 더 큰 ID를 선택합니다.",TermsResult::class.java,security=null),
    ApiContract(HttpMethod.GET,"/api/users/me","getMyInfo","회원","내 정보와 학습 설정 조회",
        "회원 정보·선택 악기·기본 악보 표기·연결된 소셜 제공자를 반환합니다.",MyInfoResult::class.java),
    ApiContract(HttpMethod.PATCH,"/api/users/me","changeNickname","회원","닉네임 수정",
        "공백이 아닌 최대 60자 닉네임을 저장합니다.",MyProfileResult::class.java),
    ApiContract(HttpMethod.PATCH,"/api/users/me/preferences","changePreferences","회원","악기와 악보 표기 설정",
        "instrumentId 또는 notationMode를 하나 이상 보냅니다. 활성 악기만 선택할 수 있고 표기는 HANJA·HANGUL·SOLFEGE입니다.",
        PreferencesSaved::class.java,errors=listOf(UNPROCESSABLE_INPUT)),
    ApiContract(HttpMethod.PATCH,"/api/users/me/password","changePassword","회원","로그인한 회원의 비밀번호 변경",
        "현재 비밀번호를 확인합니다(실패 403, 15분 5회 실패 시 15분 잠금). 새 비밀번호는 영문·숫자·특수문자 포함 8~128자이며 현재 값과 달라야 합니다. refresh 전부 폐기 후 현재 기기만 재발급합니다.",
        PasswordChanged::class.java,errors=listOf(CURRENT_PASSWORD_MISMATCH,TOO_MANY_REQUESTS,SERVICE_UNAVAILABLE),setsCookies=true),
    ApiContract(HttpMethod.DELETE,"/api/users/me","withdraw","회원","탈퇴 요청",
        "현재 비밀번호를 확인해 탈퇴 대기로 전환합니다. 30일 뒤 삭제하며 로그인 성공 시만 취소합니다. refresh를 전부 폐기하고 쿠키를 지웁니다. 비밀번호 변경과 실패 잠금을 공유합니다.",
        Withdrawn::class.java,errors=listOf(CURRENT_PASSWORD_MISMATCH,TOO_MANY_REQUESTS),setsCookies=true),
    ApiContract(HttpMethod.GET,"/api/users/me/growth/summary","growthSummary","성장","기간별 연습 요약",
        "from/to 양 끝 포함, 최대 366일·연도 1000~9999. 연습 횟수/초/일수·점수·이전 기간·곡별 최고점 등을 반환합니다. 미구현 미션 값은 null입니다.",SummaryResult::class.java),
    ApiContract(HttpMethod.GET,"/api/users/me/growth/calendar","growthCalendar","성장","날짜별 연습 기록",
        "from/to 양 끝 포함, 최대 366일·연도 1000~9999의 날짜별 연습 초와 횟수를 반환합니다.",CalendarResult::class.java),
    ApiContract(HttpMethod.POST,"/api/users/me/score-views","recordScoreView","성장","악보 화면 체류 기록",
        "Idempotency-Key로 1회 저장합니다(신규 201, 같은 내용 재요청 200, 다른 내용 409). songId는 JSON 문자열, 시각은 ISO8601, 초는 JSON 정수입니다.",
        RecordedScoreView::class.java,listOf(201,200),listOf(IDEMPOTENCY_CONFLICT,RESOURCE_NOT_FOUND)),
    ApiContract(HttpMethod.POST,"/api/practice-sessions","createPracticeSession","연습","연습 세션 생성",
        "Idempotency-Key 필수. 전체/구간/미션 연습을 생성합니다. bpm은 20~240, 음표 범위는 시작 0 이상·끝 포함이며 곡/상태 조건을 검사합니다.",
        CreatedSession::class.java,listOf(201),listOf(RESOURCE_NOT_FOUND,STATE_CONFLICT,IDEMPOTENCY_CONFLICT)),
    ApiContract(HttpMethod.GET,"/api/practice-sessions/{sessionId}","getPracticeSession","연습","연습 세션과 분석 상태 조회",
        "본인 세션만 조회합니다. RECORDING·PENDING·DONE·FAILED 상태와 실패 사유/재시도 가능 여부를 반환합니다. 초과 PENDING은 TIMEOUT으로 정리됩니다.",
        PracticeSessionResult::class.java,errors=listOf(RESOURCE_NOT_FOUND)),
    ApiContract(HttpMethod.POST,"/api/practice-sessions/{sessionId}/recording","uploadRecording","연습","녹음 업로드와 분석 접수",
        "multipart audio 1개(MP4 컨테이너 AAC/m4a), 20MiB 이하·600초(패딩 포함 601초 미만). 샘플레이트/채널/비트레이트로 거부하지 않습니다. Spring 앞 계층은 25MiB. endedAt/Idempotency-Key는 받지 않으며 종료 시각은 서버가 기록합니다.",
        RecordingUploadAccepted::class.java,listOf(202),listOf(SESSION_NOT_FOUND,SESSION_NOT_RECORDING,SESSION_EXPIRED,RECORDING_TOO_LARGE,RECORDING_UNSUPPORTED_FORMAT,RECORDING_INVALID_DURATION,RECORDING_STORAGE_UNAVAILABLE)),
    ApiContract(HttpMethod.POST,"/api/practice-sessions/{sessionId}/retry","retryAnalysis","연습","실패한 분석 재시도",
        "Idempotency-Key 필수. TIMEOUT·SERVER_ERROR의 본인 녹음을 재분석합니다. 이미 PENDING이면 새 시도 없이 동일한 202, 재녹음 필요/완료/업로드 전 상태는 409입니다. 요청 본문은 없습니다.",
        RetryResult::class.java,listOf(202),listOf(RESOURCE_NOT_FOUND,STATE_CONFLICT,SERVICE_UNAVAILABLE)),
)
