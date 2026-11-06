package com.ssafy.eolssu.backend.feature.sheet.infrastructure

import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorage
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorageException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory

/** One backend instance owns the spool. Never used for generated audio or exposed to app clients. */
class TemporarySheetSourceStorage(val directory: Path, private val baseUrl: String, private val clock: Clock,
    val multipartDirectory: Path=directory.resolveSibling("${directory.fileName}-multipart")) : SheetSourceStorage, AutoCloseable {
    data class Readable(val path: Path, val contentType: String)
    private data class Entry(val path: Path, val contentType: String, val expires: Instant, var deleting: Boolean=false)
    private data class Ticket(val key: String, val expires: Instant)
    private val entries=mutableMapOf<String,Entry>()
    private val tickets=mutableMapOf<String,Ticket>()
    private val uploads=mutableMapOf<Path,Instant>()
    private val multipartSeen=mutableMapOf<Path,Instant>()
    override val available = true
    // Bean creation precedes the web server, so this cannot delete a request in flight the way reset() could.
    init { listOf(directory,multipartDirectory).forEach(::preparePrivate);deleteAbandonedMultipart() }
    private fun preparePrivate(path: Path) {
        require(!Files.isSymbolicLink(path)) { "Temporary source directory must not be a symbolic link" }
        Files.createDirectories(path)
        if(Files.getFileStore(path).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rwx------"))
    }
    @Synchronized override fun put(key: String, file: Path, contentType: String) {
        check(key !in entries)
        val path=try { Files.createTempFile(directory,"source-",".tmp") }
            catch(e: Exception) { throw SheetSourceStorageException("Temporary source creation failed",e) }
        val entry=Entry(path,contentType,uploads[file] ?: clock.instant().plus(RETENTION))
        entries[key]=entry
        try { Files.copy(file,path,java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
        catch(e: Exception) {
            entry.deleting=true
            runCatching { delete(key) }
            throw SheetSourceStorageException("Temporary source copy failed",e)
        }
    }
    /** Request inspection copies share the same deadline and restart cleanup as queued originals. */
    @Synchronized fun createUpload(): Path = try {
        Files.createTempFile(directory,"upload-",".tmp").also { uploads[it]=clock.instant().plus(RETENTION) }
    } catch(e: Exception) { throw SheetSourceStorageException("Temporary upload creation failed",e) }
    @Synchronized fun releaseUpload(path: Path) {
        if(path !in uploads) return
        try { Files.deleteIfExists(path); uploads.remove(path) }
        catch(_: Exception) { uploads[path]=clock.instant() }
    }
    @Synchronized override fun delete(key: String) {
        tickets.entries.removeIf { it.value.key==key }
        val entry=entries[key] ?: return // Legacy R2 keys are never treated as local paths.
        entry.deleting=true
        try { Files.deleteIfExists(entry.path); entries.remove(key) }
        catch(e: Exception) { throw SheetSourceStorageException("Temporary source cleanup failed",e) }
    }
    @Synchronized override fun readUrl(key: String, ttl: Duration): String {
        val now=clock.instant()
        val entry=entries[key]?.takeIf { !it.deleting && now<it.expires && Files.isRegularFile(it.path,NOFOLLOW_LINKS) }
            ?: throw SheetSourceStorageException("Temporary source no longer exists")
        require(!ttl.isNegative && !ttl.isZero)
        tickets.entries.removeIf { now>=it.value.expires }
        val token=UUID.randomUUID().toString()+UUID.randomUUID().toString()
        tickets[token]=Ticket(key,minOf(entry.expires,now.plus(minOf(ttl,Duration.ofMinutes(15)))))
        return baseUrl.trimEnd('/')+"/"+token
    }
    @Synchronized fun readable(token: String): Readable? {
        val now=clock.instant()
        val ticket=tickets[token]?.takeIf { now<it.expires } ?: return null
        val entry=entries[ticket.key]?.takeIf { !it.deleting && now<it.expires && Files.isRegularFile(it.path,NOFOLLOW_LINKS) } ?: return null
        return Readable(entry.path,entry.contentType)
    }
    @Synchronized fun purgeExpired() {
        val now=clock.instant()
        uploads.filterValues { now>=it }.keys.toList().forEach(::releaseUpload)
        entries.filterValues { it.deleting || now>=it.expires }.keys.toList().forEach { runCatching { delete(it) } }
        tickets.entries.removeIf { now>=it.value.expires }
        runCatching { purgeMultipart(now) }.onFailure { warnMultipartCleanup() }
    }
    /** The container names these files, so their age counts from the first sweep that sees them. */
    private fun purgeMultipart(now: Instant) {
        val present=multipartFiles()
        multipartSeen.keys.retainAll(present.toSet())
        present.forEach { path ->
            if(now<multipartSeen.getOrPut(path) { now.plus(RETENTION) }) return@forEach
            runCatching { Files.deleteIfExists(path);multipartSeen.remove(path) }.onFailure { warnMultipartCleanup() }
        }
    }
    private fun deleteAbandonedMultipart() {
        runCatching { multipartFiles().forEach { path -> runCatching { Files.deleteIfExists(path) }.onFailure { warnMultipartCleanup() } } }
            .onFailure { warnMultipartCleanup() }
    }
    private fun warnMultipartCleanup()=log.warn("Temporary multipart cleanup will be retried")
    private fun multipartFiles(): List<Path> =
        Files.list(multipartDirectory).use { paths -> paths.filter { Files.isRegularFile(it,NOFOLLOW_LINKS) }.toList() }
    /** Called before enabling conversion workers on startup. Does not follow symlinks. */
    @Synchronized fun reset() {
        Files.list(directory).use { paths -> paths.forEach { path ->
            require(!Files.isDirectory(path,NOFOLLOW_LINKS)) { "Unexpected directory in temporary source spool" }
            Files.deleteIfExists(path)
        } }
        entries.clear();tickets.clear();uploads.clear()
    }
    @Synchronized override fun close() {
        entries.keys.toList().forEach { runCatching { delete(it) } }
        uploads.keys.toList().forEach(::releaseUpload)
    }
    companion object {
        val RETENTION: Duration=Duration.ofMinutes(30)
        private val log=LoggerFactory.getLogger(TemporarySheetSourceStorage::class.java)
    }
}
