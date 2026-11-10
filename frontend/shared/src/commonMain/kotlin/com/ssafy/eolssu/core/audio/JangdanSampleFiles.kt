package com.ssafy.eolssu.core.audio

import com.ssafy.eolssu.core.network.ApiJson
import eolssu.shared.generated.resources.Res
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.util.sha256
import io.ktor.utils.io.readBuffer
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable

/** A verified cached M4A and its decoded length in frames (from the bundled manifest). */
data class JangdanSampleFile(val path: Path, val frames: Int)

/**
 * App-wide cache of the reviewed jangdan M4As, shared by platforms that read files through kotlinx-io.
 * Only complete files whose size and SHA-256 match the bundled manifest are published; anything else is deleted.
 */
class JangdanSampleFiles(
    private val directory: Path,
    private val client: HttpClient,
    private val io: CoroutineDispatcher,
    private val manifest: suspend () -> ByteArray = { Res.readBytes("files/jangdan/manifest.json") },
) {
    @Serializable
    private data class Manifest(val assets: List<Asset>)

    @Serializable
    private data class Asset(val file: String, val sha256: String, val bytes: Int, val frames: Int) {
        fun matches(data: ByteArray): Boolean = data.size == bytes && sha256(data).toHexString() == sha256
    }

    private val files = SystemFileSystem
    private val mutex = Mutex()
    private var assets: Map<String, Asset>? = null

    suspend fun fileFor(sample: JangdanSample): JangdanSampleFile = mutex.withLock {
        val asset = assetFor(sample)
        withContext(io) {
            val target = Path(directory, "${asset.sha256}.m4a")
            if (files.exists(target)) {
                if (asset.matches(read(target))) return@withContext JangdanSampleFile(target, asset.frames)
                files.delete(target)
            }
            val bytes = client.prepareGet("/api/jangdan-samples/${sample.file}/${asset.sha256}").execute { response ->
                check(response.status == HttpStatusCode.OK) { "Jangdan sample unavailable" }
                response.bodyAsChannel().readBuffer(asset.bytes + 1L).readByteArray()
            }
            check(asset.matches(bytes)) { "Invalid jangdan sample" }
            files.createDirectories(directory)
            val partial = Path(directory, "jangdan-${Random.nextLong().toULong()}.part")
            try {
                files.sink(partial).buffered().use { it.write(bytes) }
                ensureActive()
                files.atomicMove(partial, target)
                JangdanSampleFile(target, asset.frames)
            } finally {
                files.delete(partial, mustExist = false)
            }
        }
    }

    private fun read(path: Path): ByteArray = files.source(path).buffered().use { it.readByteArray() }

    private suspend fun assetFor(sample: JangdanSample): Asset {
        val byName = assets ?: ApiJson.decodeFromString<Manifest>(manifest().decodeToString()).assets.let { rows ->
            require(rows.all { it.bytes in 1..128 * 1024 && it.frames in 1..88_200 && SHA256.matches(it.sha256) })
            rows.associateBy { it.file }.also { mapping ->
                require(mapping.size == rows.size)
                assets = mapping
            }
        }
        return checkNotNull(byName["${sample.file}.m4a"])
    }

    private companion object {
        val SHA256 = Regex("[0-9a-f]{64}")
    }
}
