package com.ssafy.eolssu.backend.feature.sheet.infrastructure

import com.ssafy.eolssu.backend.common.storage.S3ObjectStore
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorage
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorageException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.Instant
import org.springframework.boot.context.properties.ConfigurationProperties
import software.amazon.awssdk.core.exception.SdkException

/** 내 악보 전용 버킷. 원본은 `sheet-sources/`, 음원(481)은 `sheet-audio/` 아래에 둔다. */
class S3SheetSourceStorage(private val store: S3ObjectStore) : SheetSourceStorage, AutoCloseable {
    override val available = true

    override fun put(key: String, file: Path, contentType: String) = call("put") { store.put(key, file, contentType) }

    override fun delete(key: String) = call("delete") { store.delete(key) }

    override fun readUrl(key: String, ttl: Duration): String = store.readUrl(key, ttl)

    override fun downloadUrl(key: String, ttl: Duration, fileName: String): String = store.readUrl(key,ttl,fileName)

    override fun downloadUrlBefore(key: String, ttl: Duration, fileName: String, deadline: Instant): String? =
        store.readUrlBefore(key,ttl,fileName,deadline)

    override fun close() = store.close()

    private fun call(operation: String, block: () -> Unit) {
        try {
            block()
        } catch (e: SdkException) {
            throw SheetSourceStorageException("Sheet storage $operation failed", e)
        }
    }

    companion object {
        /** 재시도를 포함한 호출 하나의 상한. 20MiB 업로드를 Nginx 기본 60초 안에 503으로 끝낸다(녹음 저장소와 같다). */
        val CALL_TIMEOUT: Duration = Duration.ofSeconds(40)

        fun create(properties: SheetStorageProperties, maxAttempts: Int = 3, callTimeout: Duration = CALL_TIMEOUT) = S3SheetSourceStorage(
            S3ObjectStore.create(
                properties.endpoint, properties.region, properties.accessKeyId, properties.secretAccessKey, properties.bucket,
                maxAttempts, callTimeout,
            ),
        )
    }
}

/** 개발·테스트용. 키를 디렉터리 아래 경로로 쓴다. 서명 URL은 만들지 않는다. */
class LocalSheetSourceStorage(private val directory: Path) : SheetSourceStorage {
    override val available = true

    override fun put(key: String, file: Path, contentType: String) {
        io { Files.copy(file, target(key).also { Files.createDirectories(it.parent) }, StandardCopyOption.REPLACE_EXISTING) }
    }

    override fun delete(key: String) {
        io { Files.deleteIfExists(target(key)) }
    }

    override fun readUrl(key: String, ttl: Duration): String = throw SheetSourceStorageException("Local sheet storage has no read URL")

    override fun download(key: String,target: Path) { io { Files.copy(target(key),target,StandardCopyOption.REPLACE_EXISTING) } }

    private fun target(key: String): Path {
        val path = directory.resolve(key).normalize()
        if (!path.startsWith(directory.normalize())) throw SheetSourceStorageException("Sheet storage key leaves the directory")
        return path
    }

    private fun io(block: () -> Unit) {
        try {
            block()
        } catch (e: IOException) {
            throw SheetSourceStorageException("Local sheet storage failed", e)
        }
    }
}

/** 연결값이 없을 때. 업로드는 받기 전에 503으로 답한다. */
class UnavailableSheetSourceStorage : SheetSourceStorage {
    override val available = false

    override fun put(key: String, file: Path, contentType: String): Unit = throw SheetSourceStorageException("Sheet storage is not configured")

    override fun delete(key: String): Unit = throw SheetSourceStorageException("Sheet storage is not configured")

    override fun readUrl(key: String, ttl: Duration): String = throw SheetSourceStorageException("Sheet storage is not configured")
}

/** 키는 서버 `.env`의 비밀값(`SHEET_STORAGE_*`)으로 주입하고 저장소에 커밋하지 않는다. */
@ConfigurationProperties("eolssu.sheet-storage")
data class SheetStorageProperties(
    val type: String = "s3",
    val localDirectory: String = "",
    val endpoint: String = "",
    val bucket: String = "",
    val accessKeyId: String = "",
    val secretAccessKey: String = "",
    val region: String = "auto",
) {
    val isConfigured: Boolean
        get() = listOf(endpoint, bucket, accessKeyId, secretAccessKey, region).none { it.isBlank() }

    // 비밀 키가 설정 출력·로그에 찍히지 않게 한다.
    override fun toString() = "SheetStorageProperties(type=$type, configured=$isConfigured)"
}
