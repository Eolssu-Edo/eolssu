package com.ssafy.eolssu.feature.result.component

import androidx.compose.runtime.Composable
import com.ssafy.eolssu.core.ui.component.chaseProgress
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive
import kotlin.math.exp

/*
 * 연주 분석 대기 화면의 진행률(S15P31E102-777). 녹음 전송만으로 100%를 채우면 서버 분석을 기다리는 몇 초 동안 100%에 멈춰
 * 보이므로, 전송과 분석 대기를 한 막대에 나눠 담고 100%는 분석 완료(평가 ID 수신)에만 보인다.
 * 상태 조회 API는 `PENDING`·`DONE`·`FAILED`만 알려 주므로, 분석 대기 구간은 경과 시간으로 상한 아래에서 천천히 다가간다.
 */

/** 녹음 전송이 전체 진행에서 차지하는 몫. 나머지는 서버 분석 대기다. */
internal const val UploadShare = 0.3f

/** 분석 완료를 확인하기 전 진행률 상한. 완료 전에는 100%를 보이지 않는다. */
internal const val PendingProgressCap = 0.99f

/** 분석 대기 곡선의 시간 상수. QA 실측(전송 뒤 완료 확인까지 약 3~4초, S15P31E102-763)에 맞춰 이때 남은 거리의 63%를 채운다. */
internal const val AnalysisTimeConstantMillis = 4_000.0

/**
 * 전체 진행률(0~1). 전송 중에는 전송 비율 [upload]만큼 [UploadShare]까지, 다 보낸 뒤에는 분석을 기다린 시간 [analyzingMillis]에 따라
 * [PendingProgressCap] 아래로 다가간다. [done](분석 완료)일 때만 1이다.
 */
internal fun analysisProgress(upload: Float, analyzingMillis: Double, done: Boolean): Float = when {
    done -> 1f
    upload < 1f -> upload.coerceIn(0f, 1f) * UploadShare
    else -> {
        val waited = 1 - exp(-analyzingMillis.coerceAtLeast(0.0) / AnalysisTimeConstantMillis)
        (UploadShare + (PendingProgressCap - UploadShare) * waited).toFloat().coerceAtMost(PendingProgressCap)
    }
}

/**
 * 화면에 보일 진행률. 전송 비율 [upload]와 분석 대기 경과를 프레임마다 [analysisProgress]로 합치고 [chaseProgress]로 부드럽게 따라간다.
 * 분석이 끝났으면([done]) 따라가지 않고 바로 1이라, 완료와 같은 프레임에 100%가 되고 결과 화면으로 넘어간다.
 * [upload]가 null(이 화면에서 녹음을 보내지 않음·보내지 못함)이면 진행률을 모르므로 null이다.
 */
@Composable
internal fun rememberAnalysisProgress(upload: Float?, done: Boolean): Float? {
    var shown by remember { mutableFloatStateOf(0f) }
    val latest by rememberUpdatedState(upload)
    val active = upload != null
    LaunchedEffect(active) {
        if (!active) {
            shown = 0f
            return@LaunchedEffect
        }
        var analyzingMillis = 0.0
        var last = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now ->
                val dt = (now - last) / 1_000_000_000f
                last = now
                val sent = latest ?: return@withFrameNanos
                // 다시 보내면 전송 비율이 0부터 오므로 분석 대기 시간도 처음부터 센다.
                analyzingMillis = if (sent >= 1f) analyzingMillis + dt * 1_000.0 else 0.0
                shown = chaseProgress(shown, analysisProgress(sent, analyzingMillis, done = false), dt)
            }
        }
    }
    return when {
        !active -> null
        done -> 1f
        else -> shown
    }
}
