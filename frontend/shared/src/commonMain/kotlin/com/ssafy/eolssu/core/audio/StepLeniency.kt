package com.ssafy.eolssu.core.audio

/**
 * 한 음씩 판정을 악기에 맞춰 늦추는 앱 쪽 규칙(S15P31E102-666). 서버 기준(PRA-06) 위에 더하며 서버 값은 바꾸지 않는다.
 * 연습 화면의 한 음씩과 기초 배우기의 직접 불기 판정이 같은 규칙을 쓴다(S15P31E102-857).
 *
 * @property gapMs 유지 중 마지막으로 맞은 창부터 이 시간 안의 끊김(높이 없음·미확정·범위 밖)은 유지 시간을 다시 세지 않는다.
 * @property gate 녹음기의 높이 추정 문턱([AudioRecorder.pitchGate]). 듣는 동안만 바꾸고 멈추면 되돌린다.
 * @property wideFirstNote 첫 음 조율 전에는 이웃 음과 헷갈리지 않는 범위에서 조율 상한까지 넓게 받는다.
 */
data class StepLeniency(val gapMs: Int = 0, val gate: PitchGate = PitchGate.Default, val wideFirstNote: Boolean = false) {

    /**
     * 지금 맞음으로 보는 센트 범위. 보통은 ±[toleranceCent]다. [widen](첫 음 조율 전)이고 [wideFirstNote]이면 목표 바로 위·아래 이웃 음까지
     * 거리([neighborCents], 목표 대비 센트)의 절반과 조율 상한([capCent]) 중 작은 값까지 넓힌다(이웃이 없으면 조율 상한).
     * 허용 센트보다 좁히지는 않는다.
     */
    fun window(toleranceCent: Double, widen: Boolean, capCent: Double, neighborCents: List<Double>): ClosedFloatingPointRange<Double> {
        if (!wideFirstNote || !widen) return -toleranceCent..toleranceCent
        val up = neighborCents.filter { it > 1 }.minOrNull()?.let { minOf(capCent, it / 2) } ?: capCent
        val down = neighborCents.filter { it < -1 }.maxOrNull()?.let { minOf(capCent, -it / 2) } ?: capCent
        return -maxOf(toleranceCent, down)..maxOf(toleranceCent, up)
    }

    companion object {
        val Strict = StepLeniency()

        /** 단소: 김 소리·작은 소리로 높이가 잠깐 비어도 이어 가고, 높이 문턱을 낮추고, 첫 음을 넓게 받는다. */
        val Danso = StepLeniency(gapMs = 100, gate = PitchGate.Lenient, wideFirstNote = true)

        /** 악기 code(`DANSO` 등)에 맞는 규칙. 단소만 완화하고 나머지 악기는 서버 기준 그대로다. */
        fun forInstrument(code: String?): StepLeniency = if (code == "DANSO") Danso else Strict
    }
}
