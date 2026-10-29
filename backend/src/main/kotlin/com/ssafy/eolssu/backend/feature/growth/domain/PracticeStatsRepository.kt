package com.ssafy.eolssu.backend.feature.growth.domain

import java.time.LocalDate

interface PracticeStatsRepository {
    /** 회원의 날짜별(구간 시작 시각의 KST 날짜) 인정 시간 합(초). 연습하지 않은 날은 없다. */
    fun dailyPracticeSeconds(userId: Long, range: DateRange): Map<LocalDate, Long>

    /** 회원의 날짜별(세션 시작 시각의 KST 날짜) `DONE` 세션 수와 점수 합. */
    fun dailyDoneSessions(userId: Long, range: DateRange): Map<LocalDate, SessionTotals>

    /** [lastDay]까지 인정 시간 합이 [minSeconds] 이상인 날, 최근 날부터. */
    fun practiceDaysUpTo(userId: Long, lastDay: LocalDate, minSeconds: Long): List<LocalDate>

    /** 전체 곡 연주(첫 음~끝 음, `DONE`, 평가 있음)의 곡별 최고 총점. 최근에 연주한 곡 먼저. */
    fun bestFullSongScores(userId: Long): List<SongBest>

    /** 가장 최근 전체 곡 연주. */
    fun latestFullSongPlay(userId: Long): FullSongPlay?

    /** [play]와 곡·악보 버전·채점 모델·BPM이 같은 전체 곡 연주 중 가장 이른 것. */
    fun firstFullSongPlayLike(userId: Long, play: FullSongPlay): FullSongPlay?
}
