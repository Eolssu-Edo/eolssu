package com.ssafy.eolssu.feature.profile

import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.PracticeRecord
import com.ssafy.eolssu.data.user.LearnProgressRepository

/**
 * 완료 표시는 순서와 무관하므로 완료 수와 아직 완료하지 않은 첫 단계를 따로 둔다. 프로필 뱃지는 [instrument] 모양과 [nextStage]를 보여 준다.
 * [instrument]는 가장 최근에 기초 배우기를 완료 표시한 악기다(S15P31E102-759).
 */
data class MyLearningProgress(val completedStages: Set<Int>, val instrument: Instrument) {
    val totalStages: Int get() = LearnProgressRepository.STAGE_COUNT
    val completedCount: Int get() = (1..totalStages).count { it in completedStages }
    val nextStage: Int? get() = (1..totalStages).firstOrNull { it !in completedStages }
}

/**
 * '최근 연주 돌아보기' 카드 내용. 연습 기록(REC-02) 목록에서 만든다.
 *
 * @property latest 가장 최근 연주. 기록이 없으면 null.
 * @property weekCount 이번 주(월~일) 연주 수.
 * @property weekBest 이번 주 최고 점수. 이번 주 기록이 없으면 null.
 */
data class MyRecentPractice(val latest: PracticeRecord?, val weekCount: Int, val weekBest: Int?) {
    companion object {
        /** [records]는 저장소의 최신순 목록이다. 순서가 어긋나도 녹음 시각이 가장 늦은 기록을 고른다. */
        fun from(records: List<PracticeRecord>, today: KoreanDateTime): MyRecentPractice {
            val week = records.filter { it.recordedAt.isSameWeek(today) }
            return MyRecentPractice(
                latest = records.maxByOrNull { it.recordedAtMillis },
                weekCount = week.size,
                weekBest = week.maxOfOrNull { it.score },
            )
        }
    }
}

/** 카드에 기록 대신 보일 상태. 받은 기록이 있으면 받는 중·실패여도 기록을 보인다. */
enum class MyRecentPracticeStatus { Shown, Loading, Failed, Empty, Unavailable }

internal fun myRecentPracticeStatus(available: Boolean, hasRecords: Boolean, loading: Boolean, failed: Boolean): MyRecentPracticeStatus = when {
    !available -> MyRecentPracticeStatus.Unavailable
    hasRecords -> MyRecentPracticeStatus.Shown
    loading -> MyRecentPracticeStatus.Loading
    failed -> MyRecentPracticeStatus.Failed
    else -> MyRecentPracticeStatus.Empty
}

/** 가장 최근에 기초 배우기를 한 악기의 진도. 완료 기록이 없으면 연습 악기 [instrument]의 진도다. 받지 못하면 null. */
internal suspend fun myLearningProgress(instrument: Instrument, repository: LearnProgressRepository): MyLearningProgress? =
    myLearningProgress({ instrument }, repository)

/** [myLearningProgress]와 같되, 연습 악기는 완료 기록이 없을 때만 [instrument]로 기다려 받는다(내 정보보다 진도를 먼저 받는다). */
internal suspend fun myLearningProgress(instrument: suspend () -> Instrument?, repository: LearnProgressRepository): MyLearningProgress? =
    repository.recentProgress(fallback = instrument)?.let { MyLearningProgress(it.stages, it.instrument) }

/** MY-01 위에 띄우는 확인 창. */
sealed interface MyPageDialog {
    /** 간편 로그인 연결 해제 확인. 이메일 로그인은 남아 있어 언제든 해제할 수 있다. */
    data class Unlink(val provider: SocialProvider) : MyPageDialog

    data object LogOut : MyPageDialog
}
