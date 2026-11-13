package com.ssafy.eolssu.platform.audio

import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.ssafy.eolssu.core.audio.JangdanVolumeLimit
import com.ssafy.eolssu.core.audio.evaluationJangdanGain
import kotlin.math.log10
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
    // 표준 Android는 최대 단계를 0dB 이하로 준다. 일부 기기(Galaxy S24+, SDK 37)는 0~1 진폭을 주므로 dB로 바꾼다.
    val amplitude = volumeDb(max) > 0f
    fun read(step: Int) = volumeDb(step).let { if (amplitude) 20 * log10(it) else it }
    fun at(ratio: Float) = read((max * ratio).roundToInt())
    return evaluationJangdanGain(read(index), at(KNEE_RATIO), at(CEILING_RATIO), read(max))
}

/** 미디어 볼륨과 스피커 볼륨 곡선을 타격마다 읽는다. Android 9 미만이나 읽기 실패면 제한하지 않는다. */
class AndroidJangdanVolumeLimit(context: Context) : JangdanVolumeLimit {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private var logged: Pair<Int, Float>? = null

    override fun gain(): Float {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || audio == null) return 1f
        return runCatching {
            val index = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val db = { step: Int -> audio.getStreamVolumeDb(AudioManager.STREAM_MUSIC, step, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) }
            androidEvaluationGain(index, max, db).also { gain ->
                // 실기기 확인용(S15P31E102-878). 디버그 빌드에서 단계나 배율이 바뀔 때만 남긴다.
                if (debuggable && logged != index to gain) {
                    logged = index to gain
                    Log.d("EolssuJangdanVolume", "index=$index/$max db=${db(index)} knee=${db((max * KNEE_RATIO).roundToInt())} " +
                        "ceiling=${db((max * CEILING_RATIO).roundToInt())} top=${db(max)} gain=$gain")
                }
            }
        }.getOrDefault(1f)
    }
}
