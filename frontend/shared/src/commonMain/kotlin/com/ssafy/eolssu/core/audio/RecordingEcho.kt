package com.ssafy.eolssu.core.audio

/** 장단 보정은 녹음과 별개로 실패할 수 있다. 차단 시에는 마이크 원본을 보존한다. */
enum class RecordingEchoStatus {
    Off, Measuring, Ready, Active,
    NotQuiet, Unreliable, Changed, Unavailable,
}

/** 실제 준비 장단 묶음에 맞춘 표시 숫자. 마지막 묶음의 판정이 끝날 때까지 1을 유지한다. */
data class EchoCalibrationProgress(val completed: Int, val total: Int, val countIn: Int = 3)
