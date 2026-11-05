package com.ssafy.eolssu.backend.feature.sheet.application

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.feature.sheet.domain.*
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.*
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.*
import com.ssafy.eolssu.backend.feature.song.domain.*
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper

class SheetAudioRejection(val code: ErrorCode): RuntimeException(code.name)
data class AcceptedSheetAudio(val reused: Boolean,val body: SheetAudioView)
private class AudioSnapshot(val scoreVersion: Int,val instrumentId: Long,val bpm: Int,val requestJson: String,val reusable: SheetAudioRow?)

/** Owns immutable generation snapshots and keeps the last successful pair during a retry. */
class SheetAudios(
    private val mapper: SheetAudioMapper,private val sheets: UserSheetRepository,private val scores: SheetScores,
    private val jangdans: SheetAudioJangdanResolver,private val storage: SheetSourceStorage,
    private val client: PlaybackClient,private val json: JsonMapper,private val clock: Clock,manager: PlatformTransactionManager,
    private val staleRunningAfter: Duration=Duration.ofMinutes(10),
) {
    private val transaction=TransactionTemplate(manager)
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
                    "QUEUED",null,current.requestJson,null,clock.instant().utc(),null,null,false)
                mapper.enqueue(row)
                row to false
            })
        }
        return AcceptedSheetAudio(accepted.second,view(mapper.find(sheetId,userId) ?: accepted.first,sheets.find(sheetId,userId) ?: reject(ErrorCode.RESOURCE_NOT_FOUND)))
    }

    /** Validates under the member and sheet locks. Must run in a transaction and do no external IO. */
    private fun prepare(userId: Long,sheetId: Long,instrumentId: Long?,withJangdan: Boolean,bpm: Int?): AudioSnapshot {
        if(mapper.lockUser(userId)==null || mapper.lockSheet(sheetId,userId)==null) reject(ErrorCode.RESOURCE_NOT_FOUND)
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
        if(old?.status=="DONE" && old.scoreVersion==sheet.scoreVersion && old.instrumentId==instrument.id && old.withJangdan==withJangdan && old.bpm==speed)
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
        val result=row.resultJson?.let(json::readTree)
        fun file(field: String): SheetAudioFileView? {
            // Files of a row being deleted are already retired, so they get no new link.
            val item=result?.takeUnless { row.deleting }?.path(field)?.takeIf { it.isObject } ?: return null
            // Calculate expiry before signing, so it cannot claim a later expiry than the URL.
            val expires=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(900)
            val url=try { storage.downloadUrl(item.path("key").asString(),Duration.ofMinutes(15),item.path("fileName").asString()) }
                catch(_: Exception) { reject(ErrorCode.AUDIO_UNAVAILABLE) }
            return SheetAudioFileView(url,expires,if(field=="audio") "audio/mp4" else "audio/midi",item.path("byteSize").longValue(),item.path("fileName").asString(),
                item.path("sha256").asString().takeIf { field=="audio" },result!!.path("durationMs").longValue().takeIf { field=="audio" })
        }
        // A retained file keeps its own version while a newer request runs or fails.
        val fileVersion=result?.path("scoreVersion")?.takeIf { it.isIntegralNumber }?.intValue() ?: row.scoreVersion
        return SheetAudioView(row.sheetId.toString(),row.status,row.scoreVersion.toString(),sheet.scoreVersion.toString(),fileVersion!=sheet.scoreVersion,
            row.instrumentId.toString(),row.withJangdan,row.bpm,row.failureCode,3000.takeIf { row.status in setOf("QUEUED","RUNNING") },file("audio"),file("midi"))
    }

    fun recover() { transaction.executeWithoutResult { mapper.recover(clock.instant().utc()); mapper.recoverObjects() }; cleanup() }

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
            val prefix="sheet-audio/${job.userId}/${job.sheetId}/${job.generation}"
            val audioKey="$prefix/audio.m4a"; val midiKey="$prefix/score.mid"
            val registered=transaction.execute {
                mapper.lockSheet(job.sheetId,job.userId) ?: return@execute false
                val current=mapper.find(job.sheetId,job.userId)
                if(current?.generation!=job.generation || current.status!="RUNNING" || current.deleting) return@execute false
                mapper.objectPending(audioKey,job.sheetId,job.generation); mapper.objectPending(midiKey,job.sheetId,job.generation)
                true
            }==true
            if(!registered) return
            val directory=Files.createTempDirectory("sheet-audio-")
            try {
                val audio=directory.resolve("audio.m4a"); val midi=directory.resolve("score.mid")
                Files.write(audio,generated.m4a); Files.write(midi,generated.midi)
                storage.put(audioKey,audio,"audio/mp4"); storage.put(midiKey,midi,"audio/midi")
            } finally { Files.deleteIfExists(directory.resolve("audio.m4a")); Files.deleteIfExists(directory.resolve("score.mid")); Files.deleteIfExists(directory) }
            val title=(sheets.find(job.sheetId,job.userId)?.title ?: "악보").map { if(it.code<32 || it in "\\/:*?\"<>|") '_' else it }.joinToString("").trim().ifBlank { "악보" }
            val result=json.writeValueAsString(mapOf("scoreVersion" to job.scoreVersion,"durationMs" to generated.durationMs,
                "audio" to mapOf("key" to audioKey,"byteSize" to generated.m4a.size,"sha256" to MessageDigest.getInstance("SHA-256").digest(generated.m4a).joinToString("") { "%02x".format(it) },"fileName" to "$title.m4a"),
                "midi" to mapOf("key" to midiKey,"byteSize" to generated.midi.size,"fileName" to "$title.mid")))
            published=transaction.execute {
                mapper.lockSheet(job.sheetId,job.userId) ?: return@execute false
                if(mapper.done(job.sheetId,job.generation,result,clock.instant().utc())!=1) return@execute false
                mapper.retireCurrent(job.sheetId); mapper.resolveObjects(job.generation,"CURRENT")
                true
            }==true
        } catch(e: PlaybackFailure) {
            // Only an earlier render still running (e.g. across a restart) answers busy; it ends within its time limit.
            if(e.reason=="BUSY") mapper.requeue(job.sheetId,job.generation)
            else {
                log.warn("Sheet audio generation failed: sheet={} generation={} reason={}",job.sheetId,job.generation,e.reason)
                mapper.fail(job.sheetId,job.generation,e.reason,clock.instant().utc())
            }
        } catch(e: Exception) {
            log.error("Sheet audio generation failed: sheet={} generation={} reason=SERVER_ERROR",job.sheetId,job.generation,e)
            mapper.fail(job.sheetId,job.generation,"SERVER_ERROR",clock.instant().utc())
        } finally {
            if(!published) mapper.resolveObjects(job.generation,"RETIRED")
            cleanup()
        }
    }

    /** Mark deletion before external IO so an in-flight result can never become current. */
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
        for(key in mapper.retired()) try { storage.delete(key); mapper.forget(key) } catch(_: Exception) { break /* durable retry next tick */ }
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
    private fun java.time.Instant.utc()=LocalDateTime.ofInstant(this,ZoneOffset.UTC)
    private companion object { val log=LoggerFactory.getLogger(SheetAudios::class.java) }
}
