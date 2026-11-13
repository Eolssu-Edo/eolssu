package com.ssafy.eolssu.core.audio

/**
 * 한 음씩 판정을 악기에 맞춰 늦추는 앱 쪽 규칙(S15P31E102-666). 서버 기준(PRA-06) 위에 더하며 서버 값은 바꾸지 않는다.
 * 연습 화면의 한 음씩과 기초 배우기의 직접 불기 판정이 같은 규칙을 쓴다(S15P31E102-857).
 *
 * @property gapMs 유지 중 마지막으로 맞은 창부터 이 시간 안의 끊김(높이 없음·미확정·범위 밖)은 유지 시간을 다시 세지 않는다.
 * @property gate 녹음기의 높이 추정 문턱([AudioRecorder.pitchGate]). 듣는 동안만 바꾸고 멈추면 되돌린다.
 * @property wideFirstNote 첫 음 조율 전에는 이웃 음과 헷갈리지 않는 범위에서 조율 상한까지 넓게 받는다.
 * @property lowerOctave 옥타브를 따지는 판정에서도 목표보다 한 옥타브 낮은 소리를 같은 음으로 본다([foldLowerOctave]).
 *   대금은 악보보다 한 옥타브 낮게 나는 연주가 있어서다(S15P31E102-848·866). 높게 낸 소리는 그대로 다른 옥타브다.
 */
data class StepLeniency(
    val gapMs: Int = 0,
    val gate: PitchGate = PitchGate.Default,
    val wideFirstNote: Boolean = false,
    val lowerOctave: Boolean = false,
) {

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

    /**
     * 목표 대비 센트([cents], 옥타브를 접지 않은 값)를 [lowerOctave]에 맞춰 고친다. 한 옥타브 아래 근처(−1,800 이상 −600 미만)면
     * 1,200센트를 더해 목표 옥타브로 옮기고, 그 밖이거나 [lowerOctave]가 아니면 그대로 둔다.
     */
    fun foldLowerOctave(cents: Double): Double =
        if (lowerOctave && cents >= -1800 && cents < -600) cents + 1200 else cents

    companion object {
        val Strict = StepLeniency()

        /** 단소: 김 소리·작은 소리로 높이가 잠깐 비어도 이어 가고, 높이 문턱을 낮추고, 첫 음을 넓게 받는다. */
        val Danso = StepLeniency(gapMs = 100, gate = PitchGate.Lenient, wideFirstNote = true)

        /** 대금(정악·산조): 단소와 같은 완화에 더해 한 옥타브 낮게 낸 소리도 받는다(S15P31E102-866). */
        val Daegeum = Danso.copy(lowerOctave = true)

        /** 악기 code(`DANSO` 등)에 맞는 규칙. 단소·두 대금만 완화하고 소금 등 나머지 악기는 서버 기준 그대로다. */
        fun forInstrument(code: String?): StepLeniency = when (code) {
            "DANSO" -> Danso
            "JEONGAK_DAEGEUM", "SANJO_DAEGEUM" -> Daegeum
            else -> Strict
        }
    }
}
