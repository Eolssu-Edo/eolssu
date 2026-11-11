package com.ssafy.eolssu.backend.feature.sheet.application

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.feature.sheet.domain.*
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.*
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.*
import com.ssafy.eolssu.backend.feature.song.domain.*
import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper

class SheetAudioRejection(val code: ErrorCode): RuntimeException(code.name)
data class AcceptedSheetAudio(val reused: Boolean,val body: SheetAudioView)
private class AudioSnapshot(val scoreVersion: Int,val instrumentId: Long,val bpm: Int,val requestJson: String,val reusable: SheetAudioRow?)

/** Owns immutable generation snapshots and keeps a successful download until its fixed deadline. */
class SheetAudios(
    private val mapper: SheetAudioMapper,private val sheets: UserSheetRepository,private val scores: SheetScores,
    private val jangdans: SheetAudioJangdanResolver,private val storage: SheetSourceStorage,
    private val client: PlaybackClient,private val json: JsonMapper,private val clock: Clock,manager: PlatformTransactionManager,
    private val staleRunningAfter: Duration=Duration.ofMinutes(10),
) {
    private val transaction=TransactionTemplate(manager)
    /** Touched only by the sequential worker. A restart simply starts the wait again. */
    private var busy: Pair<String,java.time.Instant>?=null
    /** Covers the whole API deletion, including sheets that have never requested audio. One backend instance owns the worker. */
    private val deletions=java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    fun request(userId: Long,sheetId: Long,instrumentId: Long?,withJangdan: Boolean,bpm: Int?): AcceptedSheetAudio {
        // Keep the rate receipt even when the later request is rejected (e.g. already running).
        transaction.executeWithoutResult {
            if(mapper.lockUser(userId)==null) reject(ErrorCode.RESOURCE_NOT_FOUND)
            val now=clock.instant().utc()
            val rate=mapper.rate(userId)
            val within=rate!=null && Duration.between(rate.windowAt,now)<Duration.ofMinutes(1)
            if(within && rate!!.requestCount>=10) reject(ErrorCode.TOO_MANY_REQUESTS)
            mapper.rateUpdate(userId,if(within) rate!!.windowAt else now,if(within) rate!!.requestCount+1 else 1)
        }
        // Playback HTTP calls stay outside the transaction: they must not hold the member
        // and sheet locks or a pooled connection for their whole duration.
        val first=checkNotNull(transaction.execute { prepare(userId,sheetId,instrumentId,withJangdan,bpm) })
        val accepted=first.reusable?.let { it to true } ?: run {
            if(!client.available || !storage.available) reject(ErrorCode.AUDIO_UNAVAILABLE)
            try { client.estimate(first.requestJson) } catch(e: PlaybackFailure) {
                reject(when(e.reason) { "EXPORT_TOO_LONG" -> ErrorCode.EXPORT_TOO_LONG; "INVALID_REQUEST" -> ErrorCode.STATE_CONFLICT; else -> ErrorCode.AUDIO_UNAVAILABLE })
            }
            checkNotNull(transaction.execute {
                val current=prepare(userId,sheetId,instrumentId,withJangdan,bpm)
                current.reusable?.let { return@execute it to true }
                // The estimate only vouches for the snapshot it measured.
                if(current.scoreVersion!=first.scoreVersion || current.requestJson!=first.requestJson) reject(ErrorCode.STATE_CONFLICT)
                val row=SheetAudioRow(sheetId,userId,UUID.randomUUID().toString(),current.scoreVersion,current.instrumentId,withJangdan,current.bpm,
                    "QUEUED",null,current.requestJson,null,clock.instant().utc(),null,null,false,null)
                mapper.enqueue(row)
                row to false
            })
        }
        return AcceptedSheetAudio(accepted.second,view(mapper.find(sheetId,userId) ?: accepted.first,sheets.find(sheetId,userId) ?: reject(ErrorCode.RESOURCE_NOT_FOUND)))
    }

    /** Validates under the member and sheet locks. Must run in a transaction and do no external IO. */
    private fun prepare(userId: Long,sheetId: Long,instrumentId: Long?,withJangdan: Boolean,bpm: Int?): AudioSnapshot {
        if(mapper.lockUser(userId)==null || mapper.lockSheet(sheetId,userId)==null) reject(ErrorCode.RESOURCE_NOT_FOUND)
        if(sheetId in deletions) reject(ErrorCode.STATE_CONFLICT)
        val sheet=sheets.find(sheetId,userId) ?: reject(ErrorCode.RESOURCE_NOT_FOUND)
        val instrument=mapper.instrument(instrumentId ?: 1) ?: reject(ErrorCode.RESOURCE_NOT_FOUND)
        if(instrument.code !in setOf("DANSO","SOGEUM","JEONGAK_DAEGEUM","SANJO_DAEGEUM")) reject(ErrorCode.RESOURCE_NOT_FOUND)
        val speed=bpm ?: sheet.bpm
        if(speed !in 30..160 || withJangdan && sheet.jangdanId==null) reject(ErrorCode.INVALID_REQUEST)
        if(sheet.status!=SheetStatus.EVALUABLE || sheet.pendingCellCount!=0 || sheet.scoreJson==null ||
            sheet.imported?.conversionStatus?.let { it!=SheetImportStatus.DONE }==true) reject(ErrorCode.STATE_CONFLICT)
        scores.timeline(sheet.scoreJson,sheet.scoreVersion,sheet.noteCount,speed,sheet.bpmUnit) ?: reject(ErrorCode.STATE_CONFLICT)
        val old=mapper.find(sheetId,userId)
        if(old?.deleting==true) reject(ErrorCode.STATE_CONFLICT)
        if(old?.status=="DONE" && old.resultJson!=null && downloadDeadline(old)!=null &&
            old.scoreVersion==sheet.scoreVersion && old.instrumentId==instrument.id && old.withJangdan==withJangdan && old.bpm==speed)
            return AudioSnapshot(sheet.scoreVersion,instrument.id,speed,"",old)
        if(mapper.active(userId)>0) reject(ErrorCode.AUDIO_IN_PROGRESS)
        val jangdan=if(withJangdan) jangdans.resolve(sheet) ?: reject(ErrorCode.INVALID_REQUEST) else null
        val request=json.writeValueAsString(mapOf("score" to json.readTree(sheet.scoreJson),"instrument" to instrument.code.lowercase().replace('_','-'),
            "bpm" to speed,"withJangdan" to withJangdan,"jangdan" to jangdan))
        return AudioSnapshot(sheet.scoreVersion,instrument.id,speed,request,null)
    }

    fun get(userId: Long,sheetId: Long): SheetAudioView {
        val sheet=sheets.find(sheetId,userId) ?: reject(ErrorCode.RESOURCE_NOT_FOUND)
        val row=mapper.find(sheetId,userId) ?: reject(ErrorCode.RESOURCE_NOT_FOUND)
        return view(row,sheet)
    }

    private fun view(row: SheetAudioRow,sheet: UserSheet): SheetAudioView {
        val deadline=downloadDeadline(row)
        val result=row.resultJson?.takeIf { deadline!=null }?.let(json::readTree)
        val file=result?.path("audio")?.takeIf { it.isObject }?.let { item ->
            val expires=checkNotNull(deadline)
            val name=item.path("fileName").asString()
            val ttl=Duration.ofSeconds(Duration.between(clock.instant(),expires).seconds)
            if(ttl.isZero || ttl.isNegative) null else {
                val url=try { storage.downloadUrlBefore(item.path("key").asString(),ttl,name,expires) }
                    catch(_: Exception) { reject(ErrorCode.AUDIO_UNAVAILABLE) }
                val performance = result.path("performance").takeIf {
                row.status == "DONE" && row.scoreVersion == sheet.scoreVersion && it.isObject &&
                    it.path("timeline").isObject && it.path("sourceCommonSha256").isString
            }?.let { SheetAudioPerformanceView(it.path("sourceCommonSha256").asString(), it.path("timeline")) }
            url?.let { SheetAudioFileView(it,expires,"audio/mp4",item.path("byteSize").longValue(),name,
                    item.path("sha256").asString(),result.path("durationMs").longValue(),performance) }
            }
        }
        // A retained file keeps its own version while a newer request runs or fails.
        val fileVersion=result?.path("scoreVersion")?.takeIf { it.isIntegralNumber }?.intValue() ?: row.scoreVersion
        val expired=row.status=="DONE" && file==null
        return SheetAudioView(row.sheetId.toString(),if(expired) "FAILED" else row.status,row.scoreVersion.toString(),sheet.scoreVersion.toString(),fileVersion!=sheet.scoreVersion,
            row.instrumentId.toString(),row.withJangdan,row.bpm,if(expired) "AUDIO_EXPIRED" else row.failureCode,
            3000.takeIf { row.status in setOf("QUEUED","RUNNING") },file,null)
    }

    // A legacy result without a deadline must not receive a new link. Its objects retire on replacement or deletion.
    private fun downloadDeadline(row: SheetAudioRow): Instant? =
        row.resultExpiresAt?.takeUnless { row.deleting }?.toInstant(ZoneOffset.UTC)
            ?.takeIf { Duration.between(clock.instant(),it).seconds>0 }

    fun recover() {
        // A row can only outlive its sheet's deletion if that deletion stopped; nothing is deleting after a restart.
        transaction.executeWithoutResult { mapper.recover(clock.instant().utc()); mapper.recoverObjects(); mapper.releaseAll() }
        cleanup()
    }

    fun runOne() {
        failStale()
        cleanup()
        val job=try { transaction.execute {
            if(mapper.running()!=0) return@execute null
            val next=mapper.next() ?: return@execute null
            if(mapper.start(next.sheetId,clock.instant().utc())!=1) return@execute null
            next
        } } catch(_: org.springframework.dao.DuplicateKeyException) { null } ?: return
        var published=false
        try {
            val generated=client.generate(job.requestJson)
            if(generated.m4a.size>15*1024*1024) throw PlaybackFailure("TOO_LARGE")
            if(generated.durationMs !in 1..600000 || generated.midi.size>1024*1024) throw PlaybackFailure("SERVER_ERROR")
            val audioKey="sheet-audio/${job.userId}/${job.sheetId}/${job.generation}/audio.m4a"
            val registered=transaction.execute {
                mapper.lockSheet(job.sheetId,job.userId) ?: return@execute false
                val current=mapper.find(job.sheetId,job.userId)
                if(current?.generation!=job.generation || current.status!="RUNNING" || current.deleting) return@execute false
                mapper.objectPending(audioKey,job.sheetId,job.generation)
                true
            }==true
            if(!registered) return
            val audio=Files.createTempFile("sheet-audio-",".m4a")
            try {
                Files.write(audio,generated.m4a)
                storage.put(audioKey,audio,"audio/mp4")
            } finally { Files.deleteIfExists(audio) }
            val title=(sheets.find(job.sheetId,job.userId)?.title ?: "악보").map { if(it.code<32 || it in "\\/:*?\"<>|") '_' else it }.joinToString("").trim().ifBlank { "악보" }
            val audioHash = generated.m4a.sheetAudioSha256()
            val resultFields = mutableMapOf<String, Any>("scoreVersion" to job.scoreVersion, "durationMs" to generated.durationMs,
                "audio" to mapOf("key" to audioKey, "byteSize" to generated.m4a.size, "sha256" to audioHash, "fileName" to "$title.m4a"))
            generated.performance?.takeIf {
                it.path("scoreVersion").asInt() == job.scoreVersion && it.path("audioSha256").asString() == audioHash
            }?.let {
                resultFields["performance"] = SheetAudioPerformanceView(it.path("sourceSha256").asString(), it)
            }
            val result = json.writeValueAsString(resultFields)
            published=transaction.execute {
                mapper.lockSheet(job.sheetId,job.userId) ?: return@execute false
                val completed=clock.instant().truncatedTo(ChronoUnit.MICROS)
                if(mapper.done(job.sheetId,job.generation,result,completed.utc(),completed.plus(DOWNLOAD_WINDOW).utc())!=1) return@execute false
                mapper.retireCurrent(job.sheetId); mapper.resolveObjects(job.generation,"CURRENT")
                true
            }==true
        } catch(e: PlaybackFailure) {
            // Only an earlier render still running (e.g. across a restart) answers busy; it ends within its time limit.
            if(e.reason=="BUSY" && waitForGenerator(job)) { mapper.requeue(job.sheetId,job.generation); return }
            val reason=if(e.reason=="BUSY") "SERVER_ERROR" else e.reason
            log.warn("Sheet audio generation failed: sheet={} generation={} reason={} cause={}",job.sheetId,job.generation,reason,e.reason)
            mapper.fail(job.sheetId,job.generation,reason,clock.instant().utc())
        } catch(e: Exception) {
            // Type only: storage errors can carry object keys.
            log.error("Sheet audio generation failed: sheet={} generation={} reason=SERVER_ERROR cause={}",job.sheetId,job.generation,e.javaClass.simpleName)
            mapper.fail(job.sheetId,job.generation,"SERVER_ERROR",clock.instant().utc())
        } finally {
            if(!published) mapper.resolveObjects(job.generation,"RETIRED")
            cleanup()
        }
    }

    /** Mark deletion before external IO so an in-flight result can never become current. */
    fun beginDeletion(userId: Long,sheetId: Long): Boolean {
        if(sheets.find(sheetId,userId)==null) return false
        if(!deletions.add(sheetId)) throw SheetSourceStorageException("Sheet deletion is already in progress")
        return true
    }

    fun endDeletion(sheetId: Long) { deletions.remove(sheetId) }

    fun delete(userId: Long,sheetId: Long) {
        val found=transaction.execute {
            if(mapper.lockSheet(sheetId,userId)==null) return@execute false
            mapper.cancel(sheetId); mapper.retireCurrent(sheetId)
            true
        }
        if(found!=true) return
        mapper.retiredForSheet(sheetId).forEach { key -> storage.delete(key); mapper.forget(key) }
        if(mapper.pending(sheetId)>0) throw SheetSourceStorageException("Audio upload is finishing")
    }

    /** A failed sheet deletion keeps the sheet. Its old files are retired, so only a new generation can follow. */
    fun release(userId: Long,sheetId: Long) { mapper.release(sheetId,userId) }

    fun deleteUser(userId: Long) { mapper.userSheets(userId).forEach { delete(userId,it) }; mapper.deleteRate(userId) }

    fun cleanup() {
        val now=clock.instant().utc()
        for(candidate in mapper.expiredResults(now)) {
            transaction.executeWithoutResult {
                mapper.lockSheet(candidate.sheetId,candidate.userId) ?: return@executeWithoutResult
                // Recheck the deadline under the sheet lock: publishing might have replaced the candidate.
                if(mapper.expireResult(candidate.sheetId,now)>0) mapper.retireCurrent(candidate.sheetId)
            }
        }
        for(key in mapper.retired()) try { storage.delete(key); mapper.forget(key) } catch(_: Exception) { break /* durable retry next tick */ }
    }
    private fun waitForGenerator(job: SheetAudioRow): Boolean {
        val now=clock.instant()
        val since=busy?.takeIf { it.first==job.generation }?.second ?: now.also {
            busy=job.generation to now
            log.warn("Sheet audio generator busy, waiting: sheet={} generation={}",job.sheetId,job.generation)
        }
        return Duration.between(since,now)<staleRunningAfter
    }

    /** Recovers a job whose failure could not be recorded, which would otherwise hold the only slot until restart. */
    private fun failStale() {
        val now=clock.instant()
        transaction.executeWithoutResult {
            mapper.staleRunning(now.minus(staleRunningAfter).utc()).forEach { job ->
                log.warn("Sheet audio generation abandoned: sheet={} generation={} reason=SERVER_ERROR",job.sheetId,job.generation)
                mapper.fail(job.sheetId,job.generation,"SERVER_ERROR",now.utc())
                mapper.resolveObjects(job.generation,"RETIRED")
            }
        }
    }
    private fun reject(code: ErrorCode): Nothing=throw SheetAudioRejection(code)
    private fun Instant.utc()=LocalDateTime.ofInstant(this,ZoneOffset.UTC)
    private companion object {
        val DOWNLOAD_WINDOW=Duration.ofMinutes(10)
        val log=LoggerFactory.getLogger(SheetAudios::class.java)
    }
}
