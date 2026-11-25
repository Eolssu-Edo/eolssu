package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.JangdanVolumeLimit

/**
 * 웹 평가 녹음 중 장단 배율. 브라우저는 기기 볼륨을 읽지 못해 Android·iOS처럼 볼륨에 따라 줄이지 못하므로,
 * 평가 녹음 중에는 늘 약 1/10(-20dB)로 줄여 스피커 장단이 녹음에 섞이는 것(S15P31E102-878)을 줄인다(2026-11-24 사용자 결정, S15P31E102-1262).
 * 연습·듣기의 장단은 바꾸지 않는다.
 */
internal val WebJangdanVolumeLimit = JangdanVolumeLimit { WEB_EVALUATION_JANGDAN_GAIN }

internal const val WEB_EVALUATION_JANGDAN_GAIN = 0.1f
