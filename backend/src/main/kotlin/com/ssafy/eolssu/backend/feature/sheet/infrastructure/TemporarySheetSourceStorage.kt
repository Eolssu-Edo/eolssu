package com.ssafy.eolssu.backend.feature.sheet.infrastructure

import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorage
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorageException
import com.ssafy.eolssu.backend.feature.sheet.domain.TEMP_SHEET_SOURCE_PREFIX
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.HexFormat
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory

/** One backend instance owns the spool. Never used for generated audio or exposed to app clients. */
class TemporarySheetSourceStorage(val directory: Path, private val baseUrl: String, private val clock: Clock,
    val multipartDirectory: Path=directory.resolveSibling("${directory.fileName}-multipart"),
    private val sharedDirectory: Path? = null,
) : SheetSourceStorage, AutoCloseable {
    data class Readable(val path: Path, val contentType: String)
    private data class Entry(val path: Path, val contentType: String, val expires: Instant, var deleting: Boolean=false)
    private data class Ticket(val key: String, val expires: Instant)
    private val entries=mutableMapOf<String,Entry>()
    private val tickets=mutableMapOf<String,Ticket>()
    private val uploads=mutableMapOf<Path,Instant>()
    private val multipartSeen=mutableMapOf<Path,Instant>()
    private val lastWarnings=mutableMapOf<String,Instant>()
    override val available = true
    // Bean creation precedes the web server, so startup cleanup can never delete a request in flight.
    init {
        listOf(directory,multipartDirectory).forEach(::preparePrivate)
        deleteAbandonedSources();deleteAbandonedMultipart()
    }
    private fun preparePrivate(path: Path) {
        require(!Files.isSymbolicLink(path)) { "Temporary source directory must not be a symbolic link" }
        Files.createDirectories(path)
        if(Files.getFileStore(path).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rwx------"))
    }
    @Synchronized override fun put(key: String, file: Path, contentType: String) {
        check(key !in entries)
        val path=try {
            sharedPath(key)?.also { require(it.parent == directory); Files.createFile(it) }
                ?: Files.createTempFile(directory,"source-",".tmp")
        }
            catch(e: Exception) { throw SheetSourceStorageException("Temporary source creation failed",e) }
        val entry=Entry(path,contentType,uploads[file] ?: clock.instant().plus(RETENTION))
        entries[key]=entry
        try {
            Files.copy(file,path,java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            if (sharedDirectory != null) Files.setLastModifiedTime(path, FileTime.from(entry.expires.minus(RETENTION)))
        }
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
        catch(e: Exception) { uploads[path]=clock.instant();warnSource(e) }
    }
    @Synchronized override fun delete(key: String) {
        tickets.entries.removeIf { it.value.key==key }
        val entry=entries[key]
        if (entry == null) {
            try { sharedPath(key)?.let(Files::deleteIfExists) }
            catch (e: Exception) { throw SheetSourceStorageException("Temporary source cleanup failed", e) }
            return
        }
        entry.deleting=true
        try { Files.deleteIfExists(entry.path); entries.remove(key) }
        catch(e: Exception) { throw SheetSourceStorageException("Temporary source cleanup failed",e) }
    }
    private fun sharedPath(key: String): Path? {
        val root = sharedDirectory ?: return null
        val slot = key.removePrefix(TEMP_SHEET_SOURCE_PREFIX).substringBefore('/')
        if (!key.startsWith("$TEMP_SHEET_SOURCE_PREFIX$slot/") || slot !in SLOTS) return null
        val name = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.toByteArray()))
        return root.resolve(slot).resolve("source-$name.tmp")
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
        // Expired entries keep their record until delete() so the worker can tell expiry from a lost file.
        entries.values.filter { !it.deleting && now>=it.expires }.forEach { entry ->
            runCatching { Files.deleteIfExists(entry.path) }.onFailure(::warnSource)
        }
        entries.filterValues { it.deleting }.keys.toList().forEach { key -> runCatching { delete(key) }.onFailure(::warnSource) }
        tickets.entries.removeIf { now>=it.value.expires }
        runCatching { purgeMultipart(now) }.onFailure(::warnMultipart)
        runCatching { purgePeerSources(now) }.onFailure(::warnSource)
    }
    // A force-stopped peer can leave unindexed bytes behind. The active slot still enforces their deadline.
    private fun purgePeerSources(now: Instant) {
        val root = sharedDirectory ?: return
        val peer = root.resolve(SLOTS.single { it != directory.fileName.toString() })
        if (!Files.isDirectory(peer, NOFOLLOW_LINKS)) return
        Files.list(peer).use { paths ->
            paths.filter { it.fileName.toString().startsWith("source-") && Files.isRegularFile(it, NOFOLLOW_LINKS) }
                .forEach { path ->
                    try {
                        if (Files.getLastModifiedTime(path, NOFOLLOW_LINKS).toInstant().plus(RETENTION) <= now)
                            Files.deleteIfExists(path)
                    } catch (_: java.nio.file.NoSuchFileException) {
                        // The owning slot already removed the source.
                    }
                }
        }
    }
    @Synchronized override fun expired(key: String)=entries[key]?.let { clock.instant()>=it.expires } ?: false
    /** The container names these files, so their age counts from the first sweep that sees them. */
    private fun purgeMultipart(now: Instant) {
        if(!Files.isDirectory(multipartDirectory,NOFOLLOW_LINKS)) preparePrivate(multipartDirectory) // Tomcat cannot recreate it.
        val present=multipartFiles()
        multipartSeen.keys.retainAll(present.toSet())
        present.forEach { path ->
            if(now<multipartSeen.getOrPut(path) { now.plus(RETENTION) }) return@forEach
            runCatching { Files.deleteIfExists(path);multipartSeen.remove(path) }.onFailure(::warnMultipart)
        }
    }
    private fun deleteAbandonedMultipart() {
        runCatching { multipartFiles().forEach { path -> runCatching { Files.deleteIfExists(path) }.onFailure(::warnMultipart) } }
            .onFailure(::warnMultipart)
    }
    private fun warnSource(cause: Throwable)=warnCleanup("source",cause)
    private fun warnMultipart(cause: Throwable)=warnCleanup("multipart",cause)
    /** The sweep repeats every second, so a lasting failure logs once per interval for each area. */
    private fun warnCleanup(area: String,cause: Throwable) {
        val now=clock.instant()
        if(lastWarnings[area]?.let { now<it.plus(WARNING_INTERVAL) }==true) return
        lastWarnings[area]=now
        log.warn("Temporary {} cleanup failed and will be retried",area,cause)
    }
    private fun multipartFiles(): List<Path> =
        Files.list(multipartDirectory).use { paths -> paths.filter { Files.isRegularFile(it,NOFOLLOW_LINKS) }.toList() }
    /** Does not follow symlinks. */
    private fun deleteAbandonedSources() {
        Files.list(directory).use { paths -> paths.forEach { path ->
            require(!Files.isDirectory(path,NOFOLLOW_LINKS)) { "Unexpected directory in temporary source spool" }
            Files.deleteIfExists(path)
        } }
    }
    @Synchronized override fun close() {
        entries.keys.toList().forEach { runCatching { delete(it) } }
        uploads.keys.toList().forEach(::releaseUpload)
    }
    companion object {
        val RETENTION: Duration=Duration.ofMinutes(30)
        private val SLOTS = setOf("backend", "backend-green")
        private val WARNING_INTERVAL: Duration=Duration.ofMinutes(1)
        private val log=LoggerFactory.getLogger(TemporarySheetSourceStorage::class.java)
    }
}
