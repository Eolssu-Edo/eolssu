package com.ssafy.eolssu.backend.feature.record.domain

import java.time.LocalDateTime

/** 회원 본인 세션 하나의 기록 정보. 기록을 지운 세션은 만들지 않는다. 시각은 UTC. */
data class RecordSession(
    val sessionId: Long,
    val songId: Long,
    val songTitle: String,
    val noteCount: Int,
    val scoreVersion: Int,
    val startNoteIdx: Int,
    val endNoteIdx: Int,
    val bpm: Int,
    val bpmUnit: String,
    val status: String,
    val startedAt: LocalDateTime,
    val recordingDurationMs: Int?,
    /** 평가 총점. 분석이 끝나지 않았으면 null. */
    val totalScore: Int?,
    val savedAt: LocalDateTime?,
    val memo: String?,
    /** 녹음 원본이 있다(PRA-03 `recordingAvailable`과 같은 정의). 업로드 후 지난 시간은 보지 않는다. */
    val recordingAvailable: Boolean = false,
    /** 기록 상세를 처음 연 시각. 비어 있으면 목록의 NEW 표시다. */
    val viewedAt: LocalDateTime? = null,
) {
    /** 첫 음부터 끝 음까지 연주했다. 성장 집계의 전체 곡 연주와 같은 정의다. */
    val isFullSong: Boolean
        get() = startNoteIdx == 0 && endNoteIdx == noteCount - 1
}

/** 곡별 기록 묶음(REC-02 아코디언 머리). */
data class SongRecordSummary(
    val songId: Long,
    val title: String,
    val recordCount: Int,
    /** 그 곡 기록 중 가장 최근 저장 시각. */
    val lastSavedAt: LocalDateTime,
    val bestScore: Int,
)

/** 곡 안 기록 정렬. 같은 시각이면 세션 ID 순이다. */
enum class RecordSort { NEWEST, OLDEST }

/** 구간 첫·끝 음표의 각(0부터). */
data class ColumnRange(val start: Int, val end: Int)

/** 악보 버전의 음표 순번별 각(0부터). 지금 곡 악보 버전과 다르면 null. */
fun interface ScoreColumns {
    fun columns(songId: Long, scoreVersion: Int): List<Int>?
}

/** 세션 구간의 각. 악보 버전이 다르거나 음표 순번이 악보를 벗어나면 null이다(잘못된 칸을 보여 주지 않는다). */
fun ScoreColumns.rangeOf(session: RecordSession): ColumnRange? {
    val columns = columns(session.songId, session.scoreVersion) ?: return null
    val start = columns.getOrNull(session.startNoteIdx) ?: return null
    val end = columns.getOrNull(session.endNoteIdx) ?: return null
    return ColumnRange(start, end)
}

interface PracticeRecords {
    /** 본인의 지우지 않은 세션 상태를 잠그고 읽는다. 없거나 다른 회원의 세션, 기록을 지운 세션이면 null. */
    fun lockStatus(sessionId: Long, userId: Long): String?

    /** 본인의 지우지 않은 세션. 없거나 다른 회원의 세션, 기록을 지운 세션이면 null. */
    fun findSession(sessionId: Long, userId: Long): RecordSession?

    /** DONE이고 지우지 않은 본인 세션을 기록으로 저장한다. 처음 저장할 때만 [savedAt]을 넣고, [memo]가 [MemoChange.Keep]이 아니면 메모를 바꾼다. */
    fun save(sessionId: Long, userId: Long, savedAt: LocalDateTime, memo: MemoChange)

    /** 기록이 있는 곡들. 최근에 저장한 곡이 앞이고, 같으면 곡 ID가 큰 곡이 앞이다. */
    fun songSummaries(userId: Long): List<SongRecordSummary>

    /** 한 곡의 기록을 [sort] 순으로 [offset]부터 [limit]개. */
    fun songRecords(userId: Long, songId: Long, sort: RecordSort, offset: Long, limit: Int): List<RecordSession>
}
