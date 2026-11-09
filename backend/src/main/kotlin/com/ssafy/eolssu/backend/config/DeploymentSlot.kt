package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.sheet.domain.TEMP_SHEET_SOURCE_PREFIX
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class DeploymentSlot(
    @Value("\${eolssu.deployment.slot:}") private val name: String,
    @Value("\${eolssu.deployment.directory:/var/lib/eolssu/deployment}") private val directory: Path,
    @Value("\${eolssu.deployment.route-file:/etc/eolssu/backend-route/active.conf}") private val route: Path = Path.of("/etc/eolssu/backend-route/active.conf"),
) : AutoCloseable {
    private val peer = when (name) {
        "" -> null
        "backend" -> "backend-green"
        "backend-green" -> "backend"
        else -> error("Invalid backend deployment slot")
    }
    private val lifetime = peer?.let {
        channel(name).also {
            try {
                checkNotNull(it.tryLock()) { "Backend deployment slot is already running" }
            } catch (failure: Exception) {
                it.close()
                throw failure
            }
        }
    }
    val sourcePrefix = peer?.let { "$TEMP_SHEET_SOURCE_PREFIX$name/" }
    val sourceDirectory = peer?.let { directory.resolve("sheet-sources") }
    val sourceSpool = sourceDirectory?.resolve(name)
    val sourceReadUrl = peer?.let { "http://$name:8080/api/internal/sheet-imports" }

    // Startup recovery can touch shared jobs only while the other slot is absent.
    fun recoverIfAlone(recover: () -> Unit) {
        val other = peer
        if (other == null) {
            recover()
            return
        }
        channel(other).use { channel ->
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            lock?.use { recover() }
        }
    }

    fun selected(): Boolean {
        if (peer == null) return true
        return try {
            Files.readString(route).trim() == "set \$backend http://$name:8080;"
        } catch (_: IOException) {
            false
        }
    }

    private fun channel(slot: String) = FileChannel.open(directory.resolve("$slot.lock"), CREATE, WRITE)
    override fun close() { lifetime?.close() }
}
