package com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence

import java.time.LocalDateTime
import org.apache.ibatis.annotations.*

data class SheetAudioRow(
    val sheetId: Long, val userId: Long, val generation: String, val scoreVersion: Int,
    val instrumentId: Long, val withJangdan: Boolean, val bpm: Int, val status: String,
    val failureCode: String?, val requestJson: String, val resultJson: String?,
    val acceptedAt: LocalDateTime, val startedAt: LocalDateTime?, val finishedAt: LocalDateTime?, val deleting: Boolean,
    val resultExpiresAt: LocalDateTime?,
)
data class AudioRate(val windowAt: LocalDateTime, val requestCount: Int)
data class AudioInstrument(val id: Long, val code: String)
data class ExpiringAudio(val sheetId: Long, val userId: Long)

@Mapper
interface SheetAudioMapper {
    @Select("SELECT id FROM user_sheets WHERE user_id=#{userId}") fun userSheets(userId: Long): List<Long>
    @Delete("DELETE FROM sheet_audio_requests WHERE user_id=#{userId}") fun deleteRate(userId: Long): Int
    @Select("SELECT id FROM users WHERE id=#{userId} AND status='ACTIVE' FOR UPDATE")
    fun lockUser(userId: Long): Long?
    @Select("SELECT id FROM user_sheets WHERE id=#{sheetId} AND user_id=#{userId} FOR UPDATE")
    fun lockSheet(@Param("sheetId") sheetId: Long, @Param("userId") userId: Long): Long?
    @Select("SELECT * FROM sheet_audios WHERE sheet_id=#{sheetId} AND user_id=#{userId}")
    fun find(@Param("sheetId") sheetId: Long, @Param("userId") userId: Long): SheetAudioRow?
    @Select("SELECT id,code FROM instruments WHERE id=#{id} AND is_active=TRUE")
    fun instrument(id: Long): AudioInstrument?
    @Select("SELECT COUNT(*) FROM sheet_audios WHERE user_id=#{userId} AND status IN ('QUEUED','RUNNING')")
    fun active(userId: Long): Int
    @Select("SELECT window_at,request_count FROM sheet_audio_requests WHERE user_id=#{userId}")
    fun rate(userId: Long): AudioRate?
    @Insert("""INSERT INTO sheet_audio_requests(user_id,window_at,request_count) VALUES(#{userId},#{at},#{count})
        ON DUPLICATE KEY UPDATE window_at=#{at},request_count=#{count}""")
    fun rateUpdate(@Param("userId") userId: Long,@Param("at") at: LocalDateTime,@Param("count") count: Int): Int
    @Insert("""INSERT INTO sheet_audios(sheet_id,user_id,generation,score_version,instrument_id,with_jangdan,bpm,status,request_json,accepted_at)
        VALUES(#{sheetId},#{userId},#{generation},#{scoreVersion},#{instrumentId},#{withJangdan},#{bpm},'QUEUED',#{requestJson},#{acceptedAt})
        ON DUPLICATE KEY UPDATE generation=#{generation},score_version=#{scoreVersion},instrument_id=#{instrumentId},
        with_jangdan=#{withJangdan},bpm=#{bpm},status='QUEUED',failure_code=NULL,request_json=#{requestJson},
        accepted_at=#{acceptedAt},started_at=NULL,finished_at=NULL""")
    fun enqueue(row: SheetAudioRow): Int
    @Select("SELECT * FROM sheet_audios WHERE status='QUEUED' AND deleting=FALSE ORDER BY accepted_at,sheet_id LIMIT 1 FOR UPDATE SKIP LOCKED")
    fun next(): SheetAudioRow?
    @Select("SELECT COUNT(*) FROM sheet_audios WHERE status='RUNNING'") fun running(): Int
    @Update("UPDATE sheet_audios SET status='RUNNING',started_at=#{at} WHERE sheet_id=#{id} AND status='QUEUED'")
    fun start(@Param("id") id: Long,@Param("at") at: LocalDateTime): Int
    @Update("UPDATE sheet_audios SET status='QUEUED',started_at=NULL WHERE sheet_id=#{id} AND generation=#{generation} AND status='RUNNING'")
    fun requeue(@Param("id") id: Long,@Param("generation") generation: String): Int
    @Select("SELECT * FROM sheet_audios WHERE status='RUNNING' AND started_at<#{before}")
    fun staleRunning(before: LocalDateTime): List<SheetAudioRow>
    @Update("""UPDATE sheet_audios SET status='FAILED',failure_code=#{code},finished_at=#{at}
        WHERE sheet_id=#{id} AND generation=#{generation} AND status='RUNNING'""")
    fun fail(@Param("id") id: Long,@Param("generation") generation: String,@Param("code") code: String,@Param("at") at: LocalDateTime): Int
    @Update("""UPDATE sheet_audios SET status='DONE',failure_code=NULL,result_json=#{result},
        result_expires_at=#{expiresAt},finished_at=#{at}
        WHERE sheet_id=#{id} AND generation=#{generation} AND status='RUNNING' AND deleting=FALSE""")
    fun done(@Param("id") id: Long,@Param("generation") generation: String,@Param("result") result: String,
        @Param("at") at: LocalDateTime,@Param("expiresAt") expiresAt: LocalDateTime): Int
    @Select("""SELECT sheet_id,user_id FROM sheet_audios WHERE result_expires_at<=#{at}
        ORDER BY result_expires_at,sheet_id LIMIT 100""")
    fun expiredResults(at: LocalDateTime): List<ExpiringAudio>
    @Update("""UPDATE sheet_audios SET
        failure_code=CASE WHEN status='DONE' THEN 'AUDIO_EXPIRED' ELSE failure_code END,
        status=CASE WHEN status='DONE' THEN 'FAILED' ELSE status END,
        result_json=NULL,result_expires_at=NULL WHERE sheet_id=#{id} AND result_expires_at<=#{at}""")
    fun expireResult(@Param("id") id: Long,@Param("at") at: LocalDateTime): Int
    @Update("UPDATE sheet_audios SET status='FAILED',failure_code='SERVER_ERROR',finished_at=#{at} WHERE status='RUNNING'")
    fun recover(at: LocalDateTime): Int
    @Update("UPDATE sheet_audios SET deleting=TRUE,status='FAILED',failure_code='SERVER_ERROR' WHERE sheet_id=#{id}")
    fun cancel(id: Long): Int
    @Update("UPDATE sheet_audios SET deleting=FALSE,result_json=NULL,result_expires_at=NULL WHERE sheet_id=#{sheetId} AND user_id=#{userId} AND deleting=TRUE")
    fun release(@Param("sheetId") sheetId: Long,@Param("userId") userId: Long): Int
    @Update("UPDATE sheet_audios SET deleting=FALSE,result_json=NULL,result_expires_at=NULL WHERE deleting=TRUE") fun releaseAll(): Int
    @Insert("INSERT INTO sheet_audio_objects(object_key,sheet_id,generation,state) VALUES(#{key},#{sheetId},#{generation},'PENDING')")
    fun objectPending(@Param("key") key: String,@Param("sheetId") sheetId: Long,@Param("generation") generation: String): Int
    @Update("UPDATE sheet_audio_objects SET state='RETIRED' WHERE sheet_id=#{sheetId} AND state='CURRENT'")
    fun retireCurrent(sheetId: Long): Int
    @Update("UPDATE sheet_audio_objects SET state=#{state} WHERE generation=#{generation} AND state='PENDING'")
    fun resolveObjects(@Param("generation") generation: String,@Param("state") state: String): Int
    @Update("UPDATE sheet_audio_objects SET state='RETIRED' WHERE state='PENDING'") fun recoverObjects(): Int
    @Select("SELECT object_key FROM sheet_audio_objects WHERE state='RETIRED' LIMIT 100") fun retired(): List<String>
    @Delete("DELETE FROM sheet_audio_objects WHERE object_key=#{key} AND state='RETIRED'") fun forget(key: String): Int
    @Select("SELECT object_key FROM sheet_audio_objects WHERE sheet_id=#{id} AND state='RETIRED'") fun retiredForSheet(id: Long): List<String>
    @Select("SELECT COUNT(*) FROM sheet_audio_objects WHERE sheet_id=#{id} AND state='PENDING'") fun pending(id: Long): Int
}
