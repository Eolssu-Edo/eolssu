package com.ssafy.eolssu.backend.feature.practice.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.feature.practice.application.RecordingUploadResult
import com.ssafy.eolssu.backend.feature.practice.application.UploadRecording
import jakarta.validation.constraints.Positive
import java.nio.file.Files
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/practice-sessions")
@Profile("db")
class RecordingUploadController(private val uploadRecording: UploadRecording) {
    /**
     * API 명세 `POST /practice-sessions/{sessionId}/recording`(PRA-02 녹음 업로드). 파일 파트 이름은 `audio`.
     * 판정·오류 코드는 docs/specs/recording-file.md를 따른다. `ended_at`은 서버 시각이라 앱이 보낸 `endedAt`은 읽지 않는다.
     */
    @PostMapping("/{sessionId}/recording", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable @Positive sessionId: Long,
        @RequestPart("audio") audio: MultipartFile,
    ): ResponseEntity<*> {
        // ffprobe는 경로로 읽으므로 임시 파일에 옮기고, 요청이 끝나면 지운다.
        val file = Files.createTempFile("recording-", ".m4a")
        val result = try {
            audio.transferTo(file)
            uploadRecording.upload(requireNotNull(jwt.subject).toLong(), sessionId, file, audio.size)
        } finally {
            Files.deleteIfExists(file)
        }
        val error = when (result) {
            RecordingUploadResult.ACCEPTED -> return ResponseEntity.status(HttpStatus.ACCEPTED).body(RecordingUploadResponse(RecordingUploadAccepted(sessionId.toString())))
            RecordingUploadResult.SESSION_NOT_FOUND -> ErrorCode.SESSION_NOT_FOUND
            RecordingUploadResult.SESSION_EXPIRED -> ErrorCode.SESSION_EXPIRED
            RecordingUploadResult.SESSION_NOT_RECORDING -> ErrorCode.SESSION_NOT_RECORDING
            RecordingUploadResult.TOO_LARGE -> ErrorCode.RECORDING_TOO_LARGE
            RecordingUploadResult.UNSUPPORTED_FORMAT -> ErrorCode.RECORDING_UNSUPPORTED_FORMAT
            RecordingUploadResult.INVALID_DURATION -> ErrorCode.RECORDING_INVALID_DURATION
            RecordingUploadResult.STORAGE_UNAVAILABLE -> ErrorCode.RECORDING_STORAGE_UNAVAILABLE
        }
        return ResponseEntity.status(error.status).body(ErrorResponse.of(error))
    }
}

data class RecordingUploadResponse(
    val result: RecordingUploadAccepted,
    val httpStatus: String = "ACCEPTED",
    @get:JsonProperty("isSuccess") val isSuccess: Boolean = true,
    val message: String = "연주 녹음 업로드·비동기 분석 접수 성공",
    val code: Int = 202,
)

/** 분석은 비동기라 평가 ID는 아직 없다. 앱은 `pollAfterMs` 뒤 상태 조회(PRA-03)를 부른다. */
data class RecordingUploadAccepted(
    val sessionId: String,
    val status: String = "PENDING",
    val evaluationId: String? = null,
    val pollAfterMs: Int = 2_000,
)
