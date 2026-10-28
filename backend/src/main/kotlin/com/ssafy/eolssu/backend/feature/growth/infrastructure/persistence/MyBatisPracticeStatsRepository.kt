package com.ssafy.eolssu.backend.feature.growth.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.growth.domain.DateRange
import com.ssafy.eolssu.backend.feature.growth.domain.FullSongPlay
import com.ssafy.eolssu.backend.feature.growth.domain.PracticeStatsRepository
import com.ssafy.eolssu.backend.feature.growth.domain.SessionTotals
import com.ssafy.eolssu.backend.feature.growth.domain.SongBest
import java.time.LocalDate
import java.time.LocalDateTime

/** DB 시각은 UTC로 저장한다. KST 날짜의 시작(00:00 KST)은 UTC로 전날 15:00이다. */
class MyBatisPracticeStatsRepository(private val mapper: PracticeStatsMapper) : PracticeStatsRepository {
    override fun dailyPracticeSeconds(userId: Long, range: DateRange) =
        mapper.sumCountedSecondsByDay(userId, startOf(range.start), startOf(range.endInclusive.plusDays(1)))
            .associate { it.day to it.total }

    override fun dailyDoneSessions(userId: Long, range: DateRange) =
        mapper.sumDoneSessionsByDay(userId, startOf(range.start), startOf(range.endInclusive.plusDays(1)))
            .associate { it.day to SessionTotals(it.doneCount, it.scoredCount, it.scoreSum) }

    override fun practiceDaysUpTo(userId: Long, lastDay: LocalDate, minSeconds: Long) =
        mapper.findPracticeDays(userId, startOf(lastDay.plusDays(1)), minSeconds)

    override fun bestFullSongScores(userId: Long) =
        mapper.findBestFullSongScores(userId).map { SongBest(it.songId, it.title, it.bestTotalScore) }

    override fun latestFullSongPlay(userId: Long) = mapper.findLatestFullSongPlay(userId)?.toPlay()

    override fun firstFullSongPlayLike(userId: Long, play: FullSongPlay) =
        mapper.findFirstFullSongPlayLike(userId, play.songId, play.scoreVersion, play.modelVersion, play.bpm, play.bpmUnit)?.toPlay()

    private fun startOf(kstDay: LocalDate): LocalDateTime = kstDay.atStartOfDay().minusHours(KST_OFFSET_HOURS)

    private companion object {
        // KST는 서머타임이 없어 늘 UTC+9다.
        const val KST_OFFSET_HOURS = 9L
    }
}
