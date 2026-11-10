package com.ssafy.eolssu.core.audio

/** 장단 보정은 녹음과 별개로 실패할 수 있다. 차단 시에는 마이크 원본을 보존한다. */
enum class RecordingEchoStatus {
    Off, Measuring, Ready, Active,
    NotQuiet, Unreliable, Changed, Unavailable,
}

data class EchoCalibrationProgress(val completed: Int, val total: Int)
