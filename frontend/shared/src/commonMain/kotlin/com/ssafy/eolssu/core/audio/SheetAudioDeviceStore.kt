package com.ssafy.eolssu.core.audio

import com.ssafy.eolssu.core.network.ensureAccountSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.util.sha256
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Generation settings and identity stay with the downloaded file when the server link expires. */
data class DeviceSheetAudio(
    val localFile: LocalAudioFile,
    val instrumentId: String,
    val bpm: Int,
    val withJangdan: Boolean,
    val sha256: String,
    val durationMs: Long,
)

data class SheetAudioDownload(
    val url: String,
    val byteSize: Long,
    val sha256: String,
    val durationMs: Long,
    val instrumentId: String,
    val bpm: Int,
    val withJangdan: Boolean,
)

/** The most recently downloaded M4A for each account and sheet. */
interface SheetAudioDeviceStore {
    val available: Boolean
    suspend fun find(accountId: String, sheetId: String, scoreVersion: String): DeviceSheetAudio?
    suspend fun save(accountId: String, sheetId: String, scoreVersion: String, download: SheetAudioDownload): DeviceSheetAudio?
    suspend fun delete(accountId: String, sheetId: String)
}

object UnavailableSheetAudioDeviceStore : SheetAudioDeviceStore {
    override val available = false
    override suspend fun find(accountId: String, sheetId: String, scoreVersion: String): DeviceSheetAudio? = null
    override suspend fun save(accountId: String, sheetId: String, scoreVersion: String, download: SheetAudioDownload): DeviceSheetAudio? = null
    override suspend fun delete(accountId: String, sheetId: String) = Unit
}

/** One metadata pointer per sheet. A failed download cannot replace a previously saved file. */
internal class FileSheetAudioDeviceStore(
    private val root: Path,
    engine: HttpClientEngine,
    private val io: CoroutineDispatcher,
    private val allowDebugLoopback: Boolean = false,
    private val prepareRoot: () -> Unit = {},
) : SheetAudioDeviceStore {
    override val available = true
    private val files = SystemFileSystem
    private val mutex = Mutex()
    // The signed link authorizes this request. This client installs neither cookies nor token refresh.
    private val client = HttpClient(engine) {
        followRedirects = false
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 60_000
        }
    }

    override suspend fun find(accountId: String, sheetId: String, scoreVersion: String): DeviceSheetAudio? = stored {
        val directory = sheetDirectory(accountId, sheetId) ?: return@stored null
        val current = readMetadata(directory)
        removeUnused(directory, current)
        current?.takeIf { it.scoreVersion == scoreVersion }?.playable(directory)
    }

    override suspend fun save(
        accountId: String,
        sheetId: String,
        scoreVersion: String,
        download: SheetAudioDownload,
    ): DeviceSheetAudio? = stored {
        val directory = sheetDirectory(accountId, sheetId) ?: return@stored null
        val metadata = SavedAudio(
            scoreVersion, download.instrumentId, download.bpm, download.withJangdan,
            download.sha256, download.durationMs, download.byteSize,
        )
        if (!metadata.valid() || !isDownloadUrl(download.url)) return@stored null
        val previous = readMetadata(directory)
        removeUnused(directory, previous)
        if (previous != null && previous.scoreVersion.toInt() > metadata.scoreVersion.toInt()) return@stored null
        if (metadata == previous) previous.playable(directory)?.let { return@stored it }

        val bytes = client.prepareGet(download.url).execute { response ->
            if (response.status != HttpStatusCode.OK || response.contentType()?.withoutParameters() != M4A) {
                return@execute null
            }
            response.bodyAsChannel().readBuffer(metadata.byteSize + 1).readByteArray()
        } ?: return@stored null
        if (bytes.size.toLong() != metadata.byteSize ||
            bytes.size < 12 || bytes.decodeToString(4, 8) != "ftyp" || sha256(bytes).toHexString() != metadata.sha256
        ) return@stored null

        ensureAccountSession()
        files.createDirectories(directory)
        prepareRoot()
        val target = metadata.audioPath(directory)
        val audioPart = Path(directory, "audio.part")
        val metadataPart = Path(directory, "metadata.part")
        var published = false
        try {
            files.sink(audioPart).buffered().use { it.write(bytes) }
            ensureAccountSession()
            files.atomicMove(audioPart, target)
            files.sink(metadataPart).buffered().use { it.write(Json.encodeToString(metadata).encodeToByteArray()) }
            ensureAccountSession()
            files.atomicMove(metadataPart, Path(directory, METADATA_FILE))
            published = true
            removeUnused(directory, metadata)
            metadata.playable(directory)
        } finally {
            remove(audioPart)
            remove(metadataPart)
            if (!published && previous?.sha256 != metadata.sha256) remove(target)
        }
    }

    override suspend fun delete(accountId: String, sheetId: String) {
        stored {
            val directory = sheetDirectory(accountId, sheetId) ?: return@stored
            remove(Path(directory, METADATA_FILE))
            removeUnused(directory, current = null)
            remove(directory)
        }
    }

    private suspend fun <T> stored(action: suspend () -> T): T? = try {
        mutex.withLock {
            withContext(io) {
                ensureAccountSession()
                action().also { ensureAccountSession() }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun sheetDirectory(accountId: String, sheetId: String): Path? =
        if (ID.matches(accountId) && ID.matches(sheetId)) Path(root, accountId, sheetId) else null

    private fun isDownloadUrl(value: String): Boolean {
        val url = runCatching { URLBuilder(value) }.getOrNull() ?: return false
        val debugLoopback = allowDebugLoopback && url.protocol.name == "http" && url.host == "127.0.0.1"
        return (url.protocol.name == "https" || debugLoopback) && url.host.isNotBlank() &&
            url.user == null && url.password == null && url.fragment.isEmpty()
    }

    private fun readMetadata(directory: Path): SavedAudio? {
        val path = Path(directory, METADATA_FILE)
        val file = files.metadataOrNull(path)?.takeIf { it.isRegularFile && it.size in 1..1024 } ?: return null
        val text = files.source(path).buffered().use { it.readByteArray(file.size.toInt()).decodeToString() }
        return runCatching { Json.decodeFromString<SavedAudio>(text) }.getOrNull()?.takeIf { it.valid() }
    }

    private fun removeUnused(directory: Path, current: SavedAudio?) {
        if (files.metadataOrNull(directory)?.isDirectory != true) return
        val keep = current?.audioPath(directory)
        files.list(directory).filter {
            it != keep && (it.name == "audio.part" || it.name == "metadata.part" || AUDIO_NAME.matches(it.name))
        }.forEach(::remove)
    }

    private fun remove(path: Path) {
        runCatching { files.delete(path, mustExist = false) }
    }

    private fun SavedAudio.playable(directory: Path): DeviceSheetAudio? {
        val path = audioPath(directory)
        val file = files.metadataOrNull(path) ?: return null
        if (!file.isRegularFile || file.size != byteSize) return null
        return DeviceSheetAudio(LocalAudioFile(path.toString()), instrumentId, bpm, withJangdan, sha256, durationMs)
    }

    @Serializable
    private data class SavedAudio(
        val scoreVersion: String,
        val instrumentId: String,
        val bpm: Int,
        val withJangdan: Boolean,
        val sha256: String,
        val durationMs: Long,
        val byteSize: Long,
    ) {
        fun valid(): Boolean = ID.matches(scoreVersion) && scoreVersion.toIntOrNull() != null && ID.matches(instrumentId) &&
            bpm in 30..160 && SHA256.matches(sha256) && durationMs in 1..600_000 && byteSize in 12..15L * 1024 * 1024

        fun audioPath(directory: Path): Path = Path(directory, "$sha256.m4a")
    }

    private companion object {
        const val METADATA_FILE = "metadata.json"
        val ID = Regex("[1-9][0-9]{0,18}")
        val SHA256 = Regex("[a-f0-9]{64}")
        val AUDIO_NAME = Regex("[a-f0-9]{64}\\.m4a")
        val M4A = ContentType("audio", "mp4")
    }
}
