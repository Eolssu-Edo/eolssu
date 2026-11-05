package com.ssafy.eolssu.feature.profile

import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.data.user.LearnProgressRepository

/** 완료 표시는 순서와 무관하므로 완료 수와 아직 완료하지 않은 첫 단계를 따로 보여 준다. */
data class MyLearningProgress(val completedStages: Set<Int>) {
    val totalStages: Int get() = LearnProgressRepository.STAGE_COUNT
    val completedCount: Int get() = (1..totalStages).count { it in completedStages }
    val nextStage: Int? get() = (1..totalStages).firstOrNull { it !in completedStages }
}

/** 연습 악기의 학습 진도(서버). 받지 못하면 null. */
internal suspend fun myLearningProgress(instrument: Instrument, repository: LearnProgressRepository): MyLearningProgress? =
    repository.doneStages(instrument)?.let(::MyLearningProgress)

/** MY-01 위에 띄우는 확인 창. */
sealed interface MyPageDialog {
    /** 간편 로그인 연결 해제 확인. 이메일 로그인은 남아 있어 언제든 해제할 수 있다. */
    data class Unlink(val provider: SocialProvider) : MyPageDialog

    data object LogOut : MyPageDialog
}
