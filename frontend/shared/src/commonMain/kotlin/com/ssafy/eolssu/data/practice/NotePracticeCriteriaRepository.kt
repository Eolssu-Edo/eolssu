package com.ssafy.eolssu.data.practice

import com.ssafy.eolssu.core.network.apiResponse
import com.ssafy.eolssu.core.network.sendOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

/**
 * 한 음씩 연습 판정 기준(PRA-06, `docs/specs/note-practice-criteria.md`). 값은 서버 설정 그대로이며 앱이 고치지 않는다.
 *
 * @property pitchToleranceCent |측정 − 목표|가 이 값 이하면 맞은 음(cent).
 * @property holdMs 허용 범위 안에서 이어져야 하는 시간(ms).
 * @property minConfidence 이보다 낮은 분석 결과는 판정에서 뺀다(0~1).
 * @property minLevelDbfs 이보다 작으면 소리가 작다고 안내한다(dBFS). 안내 화면은 아직 없다.
 * @property octaveMustMatch true면 같은 율명의 다른 옥타브는 틀린 음.
 * @property firstNoteTuning 첫 음에서 잰 편차를 뒤 음 목표에 더하는지(`tuning` FIRST_NOTE).
 * @property maxTuningOffsetCent 첫 음 편차가 이 값을 넘으면 조율 보정을 하지 않는다(cent).
 * @property skipSigimsae 시김새가 붙은 음은 판정 없이 넘기는지(`sigimsaeMode` SKIP). 아니면 주음만 판정한다.
 * @property hintAfterMs 한 음에서 이 시간이 지나면 도움말을 권한다(ms). 안내 화면은 아직 없다.
 */
data class NotePracticeCriteria(
    val criteriaVersion: String,
    val pitchToleranceCent: Double,
    val holdMs: Int,
    val minConfidence: Double,
    val minLevelDbfs: Double,
    val octaveMustMatch: Boolean,
    val firstNoteTuning: Boolean,
    val maxTuningOffsetCent: Double,
    val skipSigimsae: Boolean,
    val hintAfterMs: Int,
)

/** 기준 조회 결과. */
sealed interface NotePracticeCriteriaLoad {
    data class Found(val criteria: NotePracticeCriteria) : NotePracticeCriteriaLoad

    /** 404: 기준이 없는 악기다. 앱은 한 음씩 연습을 내놓지 않는다. */
    data object Unsupported : NotePracticeCriteriaLoad

    /** 연결 실패·서버 오류·알 수 없는 응답. */
    data object Failed : NotePracticeCriteriaLoad
}

interface NotePracticeCriteriaRepository {
    suspend fun criteria(instrumentId: String): NotePracticeCriteriaLoad
}

/** `GET /api/note-practice/criteria?instrumentId=`. 로그인 없이 부르는 공개 조회다. */
class HttpNotePracticeCriteriaRepository(private val client: HttpClient) : NotePracticeCriteriaRepository {
    override suspend fun criteria(instrumentId: String): NotePracticeCriteriaLoad {
        val response = client.sendOrNull { get("/api/note-practice/criteria") { parameter("instrumentId", instrumentId) } }
            ?: return NotePracticeCriteriaLoad.Failed
        if (response.status == HttpStatusCode.NotFound) return NotePracticeCriteriaLoad.Unsupported
        if (response.status != HttpStatusCode.OK) return NotePracticeCriteriaLoad.Failed
        val dto = response.apiResponse<CriteriaDto>()?.takeIf { it.isSuccess }?.result ?: return NotePracticeCriteriaLoad.Failed
        return dto.toCriteria()?.let { NotePracticeCriteriaLoad.Found(it) } ?: NotePracticeCriteriaLoad.Failed
    }

    @Serializable
    private class CriteriaDto(
        val criteriaVersion: String,
        val pitchToleranceCent: Double,
        val holdMs: Int,
        val minConfidence: Double,
        val minLevelDbfs: Double,
        val octaveMustMatch: Boolean,
        val tuning: String,
        val maxTuningOffsetCent: Double,
        val sigimsaeMode: String,
        val hintAfterMs: Int,
    ) {
        /** 서버가 거르는 범위를 벗어났거나 모르는 값이면 판정에 쓰지 않는다. */
        fun toCriteria(): NotePracticeCriteria? {
            val firstNote = when (tuning) { "FIRST_NOTE" -> true; "NONE" -> false; else -> return null }
            val skip = when (sigimsaeMode) { "SKIP" -> true; "MAIN_PITCH" -> false; else -> return null }
            if (criteriaVersion.isBlank() || !pitchToleranceCent.isFinite() || pitchToleranceCent < 0 || holdMs < 0 ||
                minConfidence !in 0.0..1.0 || !maxTuningOffsetCent.isFinite() || maxTuningOffsetCent < 0 || hintAfterMs < 0
            ) return null
            return NotePracticeCriteria(criteriaVersion, pitchToleranceCent, holdMs, minConfidence, minLevelDbfs, octaveMustMatch,
                firstNote, maxTuningOffsetCent, skip, hintAfterMs)
        }
    }
}
