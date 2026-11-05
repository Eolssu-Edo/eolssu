package com.ssafy.eolssu.backend.feature.practice.infrastructure.storage

import com.ssafy.eolssu.backend.common.storage.S3ObjectStore
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorage
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorageException
import java.nio.file.Path
import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties
import software.amazon.awssdk.core.exception.SdkException

/** 녹음 원본 저장소(PRA-02). 버킷 접근은 [S3ObjectStore]가 하고, 여기서는 녹음 형식과 예외만 정한다. */
class S3RecordingStorage(private val store: S3ObjectStore) : RecordingStorage, AutoCloseable {
    override fun put(key: String, file: Path) {
        storageCall("put") { store.put(key, file, CONTENT_TYPE) }
    }

    override fun delete(key: String) {
        storageCall("delete") { store.delete(key) }
    }

    override fun readUrl(key: String, ttl: Duration): String = store.readUrl(key, ttl)

    override fun close() = store.close()

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

        fun create(properties: RecordingStorageProperties, maxAttempts: Int = 3, callTimeout: Duration = CALL_TIMEOUT) = S3RecordingStorage(
            S3ObjectStore.create(
                properties.endpoint, properties.region, properties.accessKeyId, properties.secretAccessKey, properties.bucket,
                maxAttempts, callTimeout,
            ),
        )
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
