package com.ssafy.eolssu.core.audio

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.util.sha256
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/** Rebuildable public model audio files, independent of any screen's native player. */
class ModelAudioFileCache(
    private val directory: Path,
    engine: HttpClientEngine,
    private val io: CoroutineDispatcher,
    private val allowDebugLoopback: Boolean = false,
    private val maxFileBytes: Long = 32L * 1024 * 1024,
    private val maxFiles: Int = 4,
    private val downloadTimeoutMs: Long = 10_000,
) {
    // Public playback redirects need no member cookies, Authorization, or token refresh.
    private val client = HttpClient(engine) {
        followRedirects = false
        install(HttpTimeout) { connectTimeoutMillis = 5_000; requestTimeoutMillis = downloadTimeoutMs }
    }
    private val files = SystemFileSystem
    private val mutex = Mutex()
    private val access = linkedMapOf<String, Unit>()

    init {
        require(maxFileBytes in 1..32L * 1024 * 1024 && maxFiles in 1..4 && downloadTimeoutMs > 0)
    }

    /** null keeps the existing remote playback path. Caller cancellation must still stop preparation. */
    suspend fun fileFor(url: String, hash: String): LocalAudioFile? {
        if (!HASH.matches(hash) || !validUrl(url)) return null
        return try {
            mutex.withLock {
                withContext(io) {
                    val target = Path(directory, "$hash.audio")
                    val partial = Path(directory, "download.part")
                    if (files.metadataOrNull(directory)?.isDirectory == true) files.delete(partial, mustExist = false)
                    val metadata = files.metadataOrNull(target)
                    if (metadata?.isRegularFile == true && metadata.size in 1..maxFileBytes &&
                        digest(read(target)) == hash
                    ) {
                        retain(target)
                        return@withContext LocalAudioFile(target.toString())
                    }
                    files.delete(target, mustExist = false)
                    val bytes = withTimeoutOrNull(downloadTimeoutMs) { download(url) } ?: return@withContext null
                    if (bytes.size.toLong() !in 1..maxFileBytes || digest(bytes) != hash) return@withContext null
                    files.createDirectories(directory)
                    try {
                        files.sink(partial).buffered().use { it.write(bytes) }
                        ensureActive()
                        files.atomicMove(partial, target)
                        retain(target)
                        LocalAudioFile(target.toString())
                    } finally {
                        files.delete(partial, mustExist = false)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun download(url: String, redirects: Int = 0): ByteArray? =
        client.prepareGet(url).execute { response ->
            if (response.status.value in setOf(301, 302, 303, 307, 308)) {
                val next = response.headers[HttpHeaders.Location]
                if (redirects >= 3 || next == null || !validUrl(next)) null else download(next, redirects + 1)
            } else if (response.status != HttpStatusCode.OK ||
                (response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0) > maxFileBytes
            ) null else response.bodyAsChannel().readBuffer(maxFileBytes + 1).readByteArray()
        }

    private fun validUrl(value: String): Boolean {
        if (value.any { it.isWhitespace() || it.code < 32 } || '#' in value) return false
        val url = runCatching { Url(value) }.getOrNull() ?: return false
        val loopback = allowDebugLoopback && url.protocol.name == "http" && url.host == "127.0.0.1"
        return (url.protocol.name == "https" || loopback) && url.host.isNotBlank() && url.user == null && url.password == null
    }

    private fun retain(current: Path) {
        access.remove(current.name)
        access[current.name] = Unit
        val managed = files.list(directory).filter { NAME.matches(it.name) }
        val oldest = managed.filter { it != current }.sortedBy { access.keys.indexOf(it.name) }
        for (path in oldest.take((managed.size - maxFiles).coerceAtLeast(0))) {
            files.delete(path, mustExist = false)
            access.remove(path.name)
        }
    }

    private fun read(path: Path): ByteArray = files.source(path).buffered().use { it.readByteArray() }
    private fun digest(bytes: ByteArray): String = sha256(bytes).toHexString()

    private companion object {
        val HASH = Regex("[a-f0-9]{64}")
        val NAME = Regex("[a-f0-9]{64}\\.audio")
    }
}
