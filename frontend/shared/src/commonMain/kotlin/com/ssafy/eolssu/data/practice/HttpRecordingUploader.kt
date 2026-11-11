package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.diagnostics.EvaluationTiming
import com.ssafy.eolssu.core.network.NetworkResult
import com.ssafy.eolssu.core.network.sendCatching
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.InputProvider
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * 녹음 업로드(PRA-02 `POST /api/practice-sessions/{sessionId}/recording`, 파일 파트 `audio`)를 앱 공통 HTTP 클라이언트로 보낸다.
 * 응답 코드는 [UploadOutcome]의 대응대로 나누고, 401(토큰 갱신도 실패)은 다시 로그인한 뒤 보낼 수 있게 [UploadOutcome.Unreachable]로 둔다.
 * 파일은 요청마다 새로 열어, 401 뒤 같은 요청을 다시 보낼 때도 처음부터 읽는다.
 *
 * @param fileSize 녹음 파일 크기(바이트). 없으면 null. 테스트는 가짜 크기를 넣는다.
 * @param openFile 녹음 파일을 처음부터 읽는다. 테스트는 메모리의 바이트를 넣는다.
 */
class HttpRecordingUploader(
    private val client: HttpClient,
    private val fileSize: (String) -> Long? = { SystemFileSystem.metadataOrNull(Path(it))?.size },
    private val openFile: (String) -> kotlinx.io.Source = { SystemFileSystem.source(Path(it)).buffered() },
) : RecordingUploader {
    override suspend fun upload(sessionId: String, path: String): UploadOutcome =
        withTimeoutOrNull(UPLOAD_TIMEOUT_MILLIS) {
            val outcome = uploadOnce(sessionId, path)
            if (outcome != UploadOutcome.TimedOut && outcome != UploadOutcome.ConnectionFailed) {
                return@withTimeoutOrNull outcome
            }
            delay(RETRY_DELAY_MILLIS)
            uploadOnce(sessionId, path)
        } ?: UploadOutcome.TimedOut

    private suspend fun uploadOnce(sessionId: String, path: String): UploadOutcome {
        val size = fileSize(path)
        val timing = currentCoroutineContext()[EvaluationTiming]
        val sent = client.sendCatching {
            submitFormWithBinaryData(
                url = "/api/practice-sessions/$sessionId/recording",
                formData = formData {
                    append(
                        "audio",
                        InputProvider(size) { openFile(path) },
                        Headers.build {
                            append(HttpHeaders.ContentType, "audio/mp4")
                            append(HttpHeaders.ContentDisposition, "filename=\"recording.m4a\"")
                        },
                    )
                },
            ) {
                timing?.let { header(EvaluationTiming.HEADER, it.id) }
                timeout { requestTimeoutMillis = UPLOAD_TIMEOUT_MILLIS }
            }
        }
        return when (sent) {
            NetworkResult.TimedOut -> UploadOutcome.TimedOut
            NetworkResult.ConnectionFailed -> UploadOutcome.ConnectionFailed
            is NetworkResult.Received -> when (sent.response.status.value) {
                202, 409 -> UploadOutcome.Uploaded
                401 -> UploadOutcome.Unreachable
                408 -> UploadOutcome.TimedOut
                410 -> UploadOutcome.Expired
                in 400..499 -> UploadOutcome.Rejected
                in 500..599 -> UploadOutcome.ServerError
                else -> UploadOutcome.Unreachable
            }
        }
    }

    private companion object {
        /** 20MiB 전송에 기본 30초보다 시간을 더 주며, 자동 재시도까지 이 한도 안에서 끝낸다. */
        const val UPLOAD_TIMEOUT_MILLIS = 120_000L
        const val RETRY_DELAY_MILLIS = 1_000L
    }
}
