package com.ssafy.eolssu.backend.feature.practice.infrastructure.storage

import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorage
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorageException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

/** 웹 루트와 분리된 backend 전용 디렉터리. 읽기 URL의 서명은 로그에 남기지 않는다. */
class LocalRecordingStorage(
    root: Path,
    private val readBaseUrl: String,
    signingSecret: String,
    private val clock: Clock = Clock.systemUTC(),
) : RecordingStorage {
    private val root = root.toAbsolutePath().normalize()
    private val secret = signingSecret.toByteArray(Charsets.UTF_8)

    init {
        require(secret.size >= 32) { "Local recording signing secret must contain at least 32 UTF-8 bytes" }
        val uri = java.net.URI(readBaseUrl)
        require(uri.scheme in setOf("http", "https") && uri.host != null && uri.rawQuery == null && uri.rawFragment == null && uri.userInfo == null)
        Files.createDirectories(this.root)
        require(!Files.isSymbolicLink(this.root) && this.root.toRealPath() == this.root) { "Recording root must not contain symlinks" }
    }

    override fun put(key: String, file: Path) = call {
        val target = path(key)
        Files.createDirectories(target.parent)
        check(!Files.isSymbolicLink(target.parent))
        check(!Files.exists(target, NOFOLLOW_LINKS)) { "Recording already exists" }
        val temporary = Files.createTempFile(target.parent, ".upload-", ".tmp")
        try {
            Files.copy(file, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            Files.setLastModifiedTime(temporary, FileTime.from(clock.instant()))
            // 같은 파일시스템의 하드링크를 배타적으로 만든다. ATOMIC_MOVE는 OS에 따라 기존 파일을 덮어쓸 수 있다.
            Files.createLink(target, temporary)
        } finally {
            Files.deleteIfExists(temporary)
        }
        Unit
    }

    override fun delete(key: String) = call { Files.deleteIfExists(path(key)); Unit }

    override fun readUrl(key: String, ttl: Duration): String {
        path(key)
        require(!ttl.isNegative && !ttl.isZero && ttl <= Duration.ofMinutes(15))
        val expires = clock.instant().plus(ttl).epochSecond
        return "${readBaseUrl.trimEnd('/')}?key=$key&expires=$expires&signature=${signature(key, expires)}"
    }

    /** 검증 실패는 없는 객체와 동일하게 처리해 파일 존재 여부를 노출하지 않는다. */
    fun readable(key: String, expires: Long, signature: String): Path? = runCatching {
        val now = clock.instant()
        if (expires <= now.epochSecond || expires > now.plusSeconds(900).epochSecond) return null
        val expected = signature(key, expires).toByteArray(Charsets.US_ASCII)
        if (!MessageDigest.isEqual(expected, signature.toByteArray(Charsets.US_ASCII))) return null
        val file = path(key)
        if (!Files.isRegularFile(file, NOFOLLOW_LINKS)) return null
        if (!Files.getLastModifiedTime(file, NOFOLLOW_LINKS).toInstant().plus(RETENTION).isAfter(now)) return null
        file
    }.getOrNull()

    @Scheduled(cron = "0 15 * * * *", zone = "Asia/Seoul")
    fun deleteExpired() {
        // 링크는 따라가지 않는다. 고아 업로드 임시파일은 1시간 뒤 별도로 제거한다.
        Files.walk(root).use { files ->
            files.filter { Files.isRegularFile(it, NOFOLLOW_LINKS) }.forEach { file ->
                try {
                    val age = if (file.fileName.toString().startsWith(".upload-")) Duration.ofHours(1) else RETENTION
                    if (!Files.getLastModifiedTime(file, NOFOLLOW_LINKS).toInstant().plus(age).isAfter(clock.instant())) Files.deleteIfExists(file)
                } catch (_: Exception) {
                    log.warn("Local recording expiry cleanup failed")
                }
            }
        }
    }

    private fun path(key: String): Path {
        require(KEY.matches(key)) { "Invalid recording key" }
        val result = root.resolve(key).normalize()
        var parent: Path? = result
        while (parent != root && parent != null) {
            require(!Files.isSymbolicLink(parent)) { "Recording symlinks are forbidden" }
            parent = parent.parent
        }
        require(result.startsWith(root))
        return result
    }

    private fun signature(key: String, expires: Long): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret, "HmacSHA256")) }
            .doFinal("GET\n$key\n$expires".toByteArray(Charsets.UTF_8)),
    )

    private fun call(action: () -> Unit) {
        try { action() } catch (e: Exception) { throw RecordingStorageException("Local recording storage operation failed", e) }
    }

    companion object {
        val RETENTION: Duration = Duration.ofDays(30)
        private val KEY = Regex("recordings/[1-9][0-9]*/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.m4a")
        private val log = LoggerFactory.getLogger(LocalRecordingStorage::class.java)
    }
}
