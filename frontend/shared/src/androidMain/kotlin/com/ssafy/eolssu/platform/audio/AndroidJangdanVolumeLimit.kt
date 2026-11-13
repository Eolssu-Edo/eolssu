package com.ssafy.eolssu.platform.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.ssafy.eolssu.core.audio.JangdanVolumeLimit
import com.ssafy.eolssu.core.audio.evaluationJangdanGain
import kotlin.math.roundToInt

/** 갤럭시 측정(S15P31E102-878): 단소는 미디어 볼륨 50/150 이하에서 장단을 끈 것과 같은 점수. 60은 더 크게 듣고 싶은 사용자를 위한 상한. */
private const val KNEE_RATIO = 40f / 150
private const val CEILING_RATIO = 60f / 150

/**
 * 지금 단계 [index]·최대 단계 [max]와 기기 볼륨 곡선 [volumeDb]로 평가 중 장단 배율을 정한다.
 * 문턱·상한은 최대 단계에 대한 비율이라 단계 수가 다른 기기도 같은 비율이 된다.
 */
internal fun androidEvaluationGain(index: Int, max: Int, volumeDb: (Int) -> Float): Float {
    if (max <= 0 || index !in 0..max) return 1f
    fun at(ratio: Float) = volumeDb((max * ratio).roundToInt())
    return evaluationJangdanGain(volumeDb(index), at(KNEE_RATIO), at(CEILING_RATIO), volumeDb(max))
}

/** 미디어 볼륨과 스피커 볼륨 곡선을 타격마다 읽는다. Android 9 미만이나 읽기 실패면 제한하지 않는다. */
class AndroidJangdanVolumeLimit(context: Context) : JangdanVolumeLimit {
    private val audio = context.getSystemService(AudioManager::class.java)

    override fun gain(): Float {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || audio == null) return 1f
        return runCatching {
            androidEvaluationGain(audio.getStreamVolume(AudioManager.STREAM_MUSIC), audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) {
                audio.getStreamVolumeDb(AudioManager.STREAM_MUSIC, it, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
            }
        }.getOrDefault(1f)
    }
}
