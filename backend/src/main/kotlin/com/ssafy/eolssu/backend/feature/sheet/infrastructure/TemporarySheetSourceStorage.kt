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

/** One backend instance owns the spool. Never used for generated audio or exposed to app clients. */
class TemporarySheetSourceStorage(val directory: Path, private val baseUrl: String, private val clock: Clock) : SheetSourceStorage, AutoCloseable {
    data class Readable(val path: Path, val contentType: String)
    private data class Entry(val path: Path, val contentType: String, val expires: Instant, var deleting: Boolean=false)
    private data class Ticket(val key: String, val expires: Instant)
    private val entries=mutableMapOf<String,Entry>()
    private val tickets=mutableMapOf<String,Ticket>()
    private val uploads=mutableMapOf<Path,Instant>()
    override val available = true
    init {
        require(!Files.isSymbolicLink(directory)) { "Temporary source directory must not be a symbolic link" }
        Files.createDirectories(directory)
        if(Files.getFileStore(directory).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(directory,PosixFilePermissions.fromString("rwx------"))
    }
    @Synchronized override fun put(key: String, file: Path, contentType: String) {
        check(key !in entries)
        val path=try { Files.createTempFile(directory,"source-",".tmp") }
            catch(e: Exception) { throw SheetSourceStorageException("Temporary source creation failed",e) }
        val entry=Entry(path,contentType,clock.instant().plus(RETENTION))
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
    }
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
    companion object { val RETENTION: Duration=Duration.ofMinutes(30) }
}
