package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.JangdanSample
import com.ssafy.eolssu.core.network.ApiJson
import eolssu.shared.generated.resources.Res
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readBuffer
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable

/** App-wide cache. Only a complete, verified WAV is published for SoundPool. */
internal class JangdanSampleCache(
    private val directory: File,
    private val client: HttpClient,
    private val manifest: suspend () -> ByteArray = { Res.readBytes("files/jangdan/manifest.json") },
) {
    @Serializable
    private data class Manifest(val assets: List<Asset>)

    @Serializable
    private data class Asset(val file: String, val sha256: String, val frames: Int) {
        val bytes: Int get() = 44 + frames * 2

        fun matches(data: ByteArray): Boolean =
            data.size == bytes && MessageDigest.getInstance("SHA-256").digest(data)
                .joinToString("") { "%02x".format(it) } == sha256
    }

    private val mutex = Mutex()
    private var assets: Map<String, Asset>? = null

    suspend fun fileFor(sample: JangdanSample): File = mutex.withLock {
        withContext(Dispatchers.IO) {
            val asset = assetFor(sample)
            val target = File(directory, "${asset.sha256}.wav")
            if (target.isFile && target.length() == asset.bytes.toLong() && asset.matches(target.readBytes())) {
                return@withContext target
            }
            check(!target.exists() || target.delete())
            val bytes = client.prepareGet("/api/jangdan-samples/${sample.file}/${asset.sha256}").execute { response ->
                check(response.status == HttpStatusCode.OK) { "Jangdan sample unavailable" }
                response.bodyAsChannel().readBuffer(asset.bytes + 1L).readByteArray()
            }
            check(asset.matches(bytes)) { "Invalid jangdan sample" }
            check(directory.isDirectory || directory.mkdirs())
            val partial = File.createTempFile("jangdan-", ".part", directory)
            try {
                partial.writeBytes(bytes)
                ensureActive()
                check(partial.renameTo(target))
                target
            } finally {
                partial.delete()
            }
        }
    }

    private suspend fun assetFor(sample: JangdanSample): Asset {
        val byName = assets ?: ApiJson.decodeFromString<Manifest>(manifest().decodeToString()).assets.let { rows ->
            require(rows.all { it.frames in 1..88_200 && it.sha256.matches(Regex("[0-9a-f]{64}")) })
            rows.associateBy { it.file }.also { mapping ->
                require(mapping.size == rows.size)
                assets = mapping
            }
        }
        return checkNotNull(byName["${sample.file}.wav"])
    }
}
