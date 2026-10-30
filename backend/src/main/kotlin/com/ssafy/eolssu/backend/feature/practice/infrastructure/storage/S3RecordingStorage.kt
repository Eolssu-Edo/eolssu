package com.ssafy.eolssu.backend.feature.practice.infrastructure.storage

import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorage
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorageException
import java.net.URI
import java.nio.file.Path
import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner

/**
 * S3 호환 저장소. 업체 전용 기능 없이 엔드포인트·키 설정만으로 바꿀 수 있게 표준 S3 API만 쓴다(처음 쓰는 업체는 Cloudflare R2).
 * 예외 메시지에 서명 URL·키를 싣지 않는다.
 */
class S3RecordingStorage(
    private val client: S3Client,
    private val presigner: S3Presigner,
    private val bucket: String,
) : RecordingStorage, AutoCloseable {
    override fun put(key: String, file: Path) {
        storageCall("put") {
            client.putObject({ it.bucket(bucket).key(key).contentType(CONTENT_TYPE) }, RequestBody.fromFile(file))
        }
    }

    override fun delete(key: String) {
        storageCall("delete") { client.deleteObject { it.bucket(bucket).key(key) } }
    }

    // 서명은 로컬 계산이라 네트워크를 쓰지 않는다. R2의 서명 URL은 S3 API 도메인에서만 동작한다.
    override fun readUrl(key: String, ttl: Duration): String =
        presigner.presignGetObject { it.signatureDuration(ttl).getObjectRequest { request -> request.bucket(bucket).key(key) } }
            .url().toString()

    // Spring이 빈을 내릴 때 부른다. 연결 풀 스레드를 정리한다.
    override fun close() {
        client.close()
        presigner.close()
    }

    private fun storageCall(operation: String, call: () -> Unit) {
        try {
            call()
        } catch (e: SdkException) {
            throw RecordingStorageException("Recording storage $operation failed", e)
        }
    }

    companion object {
        private const val CONTENT_TYPE = "audio/mp4"

        /**
         * 재시도를 포함한 저장소 호출 하나의 상한. ffprobe 상한(10초)과 더해도 Nginx `proxy_read_timeout` 기본값(60초)보다
         * 짧아야 느린 저장소가 Nginx 504가 아니라 503(`RECORDING_STORAGE_UNAVAILABLE`)으로 끝난다.
         */
        val CALL_TIMEOUT: Duration = Duration.ofSeconds(40)

        fun create(properties: RecordingStorageProperties, maxAttempts: Int = 3, callTimeout: Duration = CALL_TIMEOUT): S3RecordingStorage {
            val endpoint = URI(properties.endpoint)
            val region = Region.of(properties.region)
            val credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKeyId, properties.secretAccessKey))
            // 경로 방식(엔드포인트/버킷/키)은 R2와 다른 S3 호환 업체가 모두 받는다.
            val s3Configuration = S3Configuration.builder().pathStyleAccessEnabled(true).build()
            val client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Configuration)
                // SDK 기본값(모든 요청에 CRC 체크섬)은 S3 호환 업체마다 지원이 달라, 필요한 요청에만 쓴다.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .overrideConfiguration {
                    it.retryStrategy { retry -> retry.maxAttempts(maxAttempts) }
                    it.apiCallTimeout(callTimeout)
                }
                .build()
            val presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Configuration)
                .build()
            return S3RecordingStorage(client, presigner, properties.bucket)
        }
    }
}

/** 원본 저장소 연결값. 키는 서버 `.env`의 비밀값으로 주입하고 저장소에 커밋하지 않는다. */
@ConfigurationProperties("eolssu.recording-storage")
data class RecordingStorageProperties(
    val type: String = "s3",
    val localDirectory: String = "",
    val localReadBaseUrl: String = "",
    val localSigningSecret: String = "",
    val endpoint: String = "",
    val bucket: String = "",
    val accessKeyId: String = "",
    val secretAccessKey: String = "",
    val region: String = "auto",
) {
    val isConfigured: Boolean
        get() = listOf(endpoint, bucket, accessKeyId, secretAccessKey, region).none { it.isBlank() }

    // 비밀 키가 설정 출력·로그에 찍히지 않게 한다.
    override fun toString() = "RecordingStorageProperties(type=$type, configured=$isConfigured)"
}

/** 연결값이 없을 때(로컬·테스트 서버). 업로드는 503으로 답하고 원본을 저장하지 않는다. */
class UnavailableRecordingStorage : RecordingStorage {
    override fun put(key: String, file: Path): Unit = throw RecordingStorageException("Recording storage is not configured")

    override fun delete(key: String): Unit = throw RecordingStorageException("Recording storage is not configured")

    override fun readUrl(key: String, ttl: Duration): String = throw RecordingStorageException("Recording storage is not configured")
}
