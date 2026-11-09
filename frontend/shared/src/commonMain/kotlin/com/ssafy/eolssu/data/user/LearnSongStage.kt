package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.Song
import kotlinx.coroutines.CancellationException

/*
 * 기초부터 배우기 8단계 '악보와 곡'의 자동 통과(S15P31E102-611, 결정은 docs/plans/S15P31E102-611.md '8단계').
 * 곡 전체 연주의 AI 연주 평가 점수를 그대로 쓴다. 기준은 서버 단계 상세의 통과 기준 예시이며 팀 확정 사항이 아니다.
 */

/** 8단계 통과 기준 예시: 곡 전체 음정·박자 각 70점 이상. */
const val LEARN_SONG_PASS_SCORE = 70

/** 기초부터 배우기에서 곡으로 통과를 판정하는 단계. */
const val LEARN_SONG_STAGE = 8

/** 곡 전체 연주 결과로 8단계를 판정한 결과. */
enum class LearnSongPass {
    /** 이번 결과로 8단계를 완료로 남겼다. */
    Passed,

    /** 이미 완료한 단계다. 다시 남기지 않는다. */
    AlreadyDone,

    /** 8단계 곡이지만 점수가 기준에 못 미친다. */
    BelowScore,

    /** 예시 결과, 악기를 모르는 곡, 그 악기 8단계 곡이 아닌 곡. */
    NotApplicable,

    /** 단계 목록·내용을 받지 못했거나 완료를 남기지 못했다. */
    Failed,
}

/**
 * 곡 전체 연주 결과를 보고 그 곡의 악기 트랙 8단계를 완료로 남긴다. 완료 표시는 사용자가 누를 때와 같은 LRN-03을 쓴다.
 *
 * @param instrumentCode 곡의 서버 악기 ID → 악기 code(`DANSO` 등). 모르면 null. 악기 목록(PRA 카탈로그)에서 찾는다.
 */
class LearnSongStage(
    private val progress: LearnProgressRepository,
    private val instrumentCode: suspend (instrumentId: String) -> String?,
) {
    /** [result]는 [song]을 처음부터 끝까지 연주한 결과여야 한다(구간 연주 결과는 넘기지 않는다). */
    suspend fun judge(result: EvaluationResult, song: Song): LearnSongPass {
        if (result.sample || result.songId != song.id) return LearnSongPass.NotApplicable
        val instrumentId = song.instrumentId ?: return LearnSongPass.NotApplicable
        return try {
            val trackId = instrumentCode(instrumentId)?.let(::learnTrackOf) ?: return LearnSongPass.NotApplicable
            val track = progress.track(trackId) ?: return LearnSongPass.Failed
            val stage = track.items.firstOrNull { it.seq == LEARN_SONG_STAGE } ?: return LearnSongPass.NotApplicable
            val detail = progress.lesson(stage.lessonId) ?: return LearnSongPass.Failed
            when {
                detail.songs.none { it.title == song.title } -> LearnSongPass.NotApplicable
                !result.passesSongStage() -> LearnSongPass.BelowScore
                stage.completed -> LearnSongPass.AlreadyDone
                progress.setCompleted(stage.lessonId, true) -> LearnSongPass.Passed
                else -> LearnSongPass.Failed
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            LearnSongPass.Failed
        }
    }

    /** [song]의 기초부터 배우기 트랙(`GuideInstrument.id`). 통과 뒤 '다음 단계로'가 그 악기의 다음 단계를 열 때 쓴다. 모르면 null. */
    suspend fun trackOf(song: Song): String? = try {
        song.instrumentId?.let { instrumentCode(it) }?.let(::learnTrackOf)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

/** 음정·박자 점수가 모두 [LEARN_SONG_PASS_SCORE] 이상인지. 점수가 없는 항목이 있으면 통과가 아니다. */
internal fun EvaluationResult.passesSongStage(): Boolean {
    val scores = metrics.associate { it.metric to it.score }
    return listOf(EvaluationMetric.Pitch, EvaluationMetric.Rhythm).all { (scores[it] ?: return false) >= LEARN_SONG_PASS_SCORE }
}

/** 서버 악기 code → 학습 화면 트랙 식별자(`GuideInstrument.id`). 기초부터 배우기 악기가 아니면 null. */
internal fun learnTrackOf(code: String): String? = when (code) {
    "DANSO" -> "danso"
    "SOGEUM" -> "sogeum"
    "JEONGAK_DAEGEUM" -> "daegeum-jeongak"
    "SANJO_DAEGEUM" -> "daegeum-sanjo"
    else -> null
}
