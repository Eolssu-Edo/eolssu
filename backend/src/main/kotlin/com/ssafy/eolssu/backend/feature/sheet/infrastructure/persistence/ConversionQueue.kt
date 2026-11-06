package com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.sheet.infrastructure.BuiltRecognition
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.apache.ibatis.annotations.*
import org.springframework.context.annotation.Profile
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

data class ConversionJob(
    val sheetId: Long, val userId: Long, val title: String, val notation: String,
    val instrument: String, val jangdan: String?, val bpm: Int?, val hwangPitch: String?,
    val status: String, val attempt: Int, val failureCause: String?, val timeoutCount: Int,
    val acceptedAt: LocalDateTime,
)
data class ConversionFile(val objectKey: String, val contentType: String)
data class ConversionRetry(val status: String)
data class ConversionJangdan(val id: Long,val defaultBpm: Int)

private const val JOB_SELECT = """SELECT c.sheet_id,c.user_id,s.title,c.notation,i.code AS instrument,j.name AS jangdan,c.bpm,c.hwang_pitch,
    c.status,c.attempt,c.failure_cause,c.timeout_count,c.accepted_at
    FROM sheet_conversions c JOIN user_sheets s ON s.id=c.sheet_id JOIN instruments i ON i.id=c.instrument_id
    LEFT JOIN jangdans j ON j.id=c.jangdan_id"""

@Mapper
interface ConversionWorkerMapper {
    @Select("$JOB_SELECT WHERE c.status='QUEUED' ORDER BY c.accepted_at,c.sheet_id LIMIT 1 FOR UPDATE SKIP LOCKED")
    fun next(): ConversionJob?
    @Select("SELECT COUNT(*) FROM sheet_conversions WHERE status='RUNNING'") fun running(): Int
    @Select("$JOB_SELECT WHERE c.sheet_id=#{sheetId} AND c.user_id=#{userId} FOR UPDATE")
    fun owned(@Param("userId") userId: Long,@Param("sheetId") sheetId: Long): ConversionJob?
    @Select("SELECT id FROM users WHERE id=#{userId} AND status='ACTIVE' FOR UPDATE") fun lockUser(userId: Long): Long?
    @Select("SELECT COUNT(*) FROM sheet_conversions WHERE user_id=#{userId} AND status IN ('QUEUED','RUNNING') AND sheet_id != #{sheetId}")
    fun otherActive(@Param("userId") userId: Long,@Param("sheetId") sheetId: Long): Int
    @Update("UPDATE sheet_conversions SET status='RUNNING',stage='OMR',started_at=#{now} WHERE sheet_id=#{sheetId} AND status='QUEUED'")
    fun start(@Param("sheetId") sheetId: Long,@Param("now") now: LocalDateTime): Int
    @Update("""UPDATE sheet_conversions SET status='FAILED',stage=NULL,failure_code=#{code},failure_cause=#{cause},
        timeout_count=timeout_count+CASE WHEN #{cause}='TIMEOUT' THEN 1 ELSE 0 END,finished_at=#{now}
        WHERE sheet_id=#{sheetId} AND attempt=#{attempt} AND status='RUNNING'""")
    fun fail(@Param("sheetId") sheetId: Long,@Param("attempt") attempt: Int,@Param("code") code: String,@Param("cause") cause: String,@Param("now") now: LocalDateTime): Int
    @Update("""UPDATE sheet_conversions SET status='FAILED',stage=NULL,failure_code='SERVER_ERROR',failure_cause='SERVER_ERROR',
        finished_at=#{now} WHERE status IN ('QUEUED','RUNNING')""") fun recover(now: LocalDateTime): Int
    @Select("SELECT object_key,content_type FROM sheet_source_files WHERE sheet_id=#{sheetId} ORDER BY file_idx")
    fun files(sheetId: Long): List<ConversionFile>
    @Select("SELECT id,default_bpm FROM jangdans WHERE name=#{name} LIMIT 1") fun jangdan(name: String): ConversionJangdan?
    @Select("SELECT sheet_id FROM sheet_conversions WHERE sheet_id=#{sheetId} AND attempt=#{attempt} AND status='RUNNING' FOR UPDATE")
    fun lockRunning(@Param("sheetId") sheetId: Long,@Param("attempt") attempt: Int): Long?
    @Update("UPDATE sheet_conversions SET stage='BUILDING_SCORE',processed_page_count=page_count WHERE sheet_id=#{sheetId} AND attempt=#{attempt} AND status='RUNNING'")
    fun building(@Param("sheetId") sheetId: Long,@Param("attempt") attempt: Int): Int
    @Update("""UPDATE user_sheets SET score_json=#{result.scoreJson},draft_json=#{result.draftJson},draft_revision=#{revision},
        draft_base_version=#{baseVersion},draft_saved_at=#{draftAt},bpm=#{result.bpm},bpm_unit=#{result.bpmUnit},jangdan_id=#{jangdanId},
        column_count=#{result.columnCount},note_count=#{result.noteCount},pending_cell_count=#{result.pendingCellCount},status='NEEDS_REVIEW',updated_at=#{now}
        WHERE id=#{sheetId}""")
    fun saveScore(@Param("sheetId") sheetId: Long,@Param("result") result: BuiltRecognition,@Param("jangdanId") jangdanId: Long?,
        @Param("revision") revision: Int,@Param("baseVersion") baseVersion: Int?,@Param("draftAt") draftAt: LocalDateTime?,@Param("now") now: LocalDateTime): Int
    @Delete("DELETE FROM sheet_source_files WHERE object_key=#{key}") fun forgetSource(key: String): Int
    @Select("SELECT f.object_key,f.content_type FROM sheet_source_files f JOIN sheet_conversions c ON c.sheet_id=f.sheet_id WHERE c.status IN ('DONE','FAILED') AND f.object_key LIKE 'sheet-import-temp/%'")
    fun finishedSources(): List<ConversionFile>
    @Update("UPDATE sheet_conversions SET status='FAILED',stage=NULL,failure_code='TIMEOUT',failure_cause='TIMEOUT',finished_at=#{now} WHERE status IN ('QUEUED','RUNNING') AND accepted_at<=#{before}")
    fun expire(@Param("now") now: LocalDateTime,@Param("before") before: LocalDateTime): Int
    @Update("UPDATE sheet_conversions SET status='DONE',stage=NULL,result_json=#{resultJson},finished_at=#{now} WHERE sheet_id=#{sheetId}")
    fun finish(@Param("sheetId") sheetId: Long,@Param("resultJson") resultJson: String,@Param("now") now: LocalDateTime): Int
}

/** 큐 선점과 상태 갱신만 짧은 트랜잭션으로 묶는다. 외부 인식 호출은 이 클래스 밖에서 수행한다. */
@Component
@Profile("db | ops")
class ConversionQueue(private val mapper: ConversionWorkerMapper, manager: PlatformTransactionManager,private val json: JsonMapper) {
    private val transaction=TransactionTemplate(manager)
    fun claim(now: Instant): ConversionJob? = try {
        transaction.execute {
            if(mapper.running()!=0) return@execute null
            val job=mapper.next() ?: return@execute null
            if(mapper.start(job.sheetId,now.utc())!=1) return@execute null
            job
        }
    } catch (_: DuplicateKeyException) { null } // 다른 프로세스가 먼저 전역 RUNNING 슬롯을 차지했다.
    fun files(sheetId: Long)=mapper.files(sheetId)
    fun jangdan(name: String)=mapper.jangdan(name)
    fun building(job: ConversionJob)=mapper.building(job.sheetId,job.attempt)==1
    fun complete(job: ConversionJob,result: BuiltRecognition,now: Instant): Boolean = transaction.execute {
        if(mapper.lockRunning(job.sheetId,job.attempt)==null) return@execute false
        val draft=result.draftJson!=null
        val jangdanId=mapper.jangdan(result.jangdanName)?.id
        val draftJson=result.draftJson?.let { raw ->
            val node=json.readTree(raw) as ObjectNode
            if(jangdanId==null) node.putNull("jangdanId") else node.put("jangdanId",jangdanId.toString())
            json.writeValueAsString(node)
        }
        mapper.saveScore(job.sheetId,result.copy(draftJson=draftJson),jangdanId,if(draft) 1 else 0,if(draft) 1 else null,now.utc().takeIf { draft },now.utc())
        val details=json.readTree(result.resultJson) as ObjectNode
        details.putArray("pages")
        mapper.finish(job.sheetId,json.writeValueAsString(details),now.utc())
        true
    }==true
    fun fail(sheetId: Long,attempt: Int,cause: String,now: Instant) {
        val code=when(cause){ "ENGINE_ERROR" -> "SERVER_ERROR"; else -> cause }
        mapper.fail(sheetId,attempt,code,cause,now.utc())
    }
    fun recover(now: Instant)=mapper.recover(now.utc())
    fun forgetSource(key: String)=mapper.forgetSource(key)
    fun finishedSources()=mapper.finishedSources()
    fun expire(now: Instant)=mapper.expire(now.utc(),now.minus(com.ssafy.eolssu.backend.feature.sheet.infrastructure.TemporarySheetSourceStorage.RETENTION).utc())
    fun retry(userId: Long,sheetId: Long): ConversionRetry = checkNotNull(transaction.execute {
        if(mapper.lockUser(userId)==null) return@execute ConversionRetry("NOT_FOUND")
        val job=mapper.owned(userId,sheetId) ?: return@execute ConversionRetry("NOT_FOUND")
        ConversionRetry("STATE_CONFLICT")
    })
    private fun Instant.utc()=LocalDateTime.ofInstant(this,ZoneOffset.UTC)
}
