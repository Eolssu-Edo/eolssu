package com.ssafy.eolssu.feature.practice

/** 연습 화면 제목 줄의 보기 버튼 종류(S15P31E102-1037). */
enum class PracticeOptionKind {
    /** 혼자 연습의 ‘한 음씩’. */
    Step,

    /** AI 연주 평가의 ‘퍼펙트 스코어’(실시간 채점 색). */
    LiveMarks,

    /** ‘운지법’(운지법과 함께 보기). */
    Fingering,

    /** ‘한글 표기’(율명 아래 한글 읽는 법). */
    Hangul,
}

/** 보기 버튼 하나: 종류, 켜짐(초록), 누를 수 있는지. */
data class PracticeOptionButton(val kind: PracticeOptionKind, val on: Boolean, val enabled: Boolean = true)

/**
 * 제목 줄에 왼쪽부터 놓을 보기 버튼. 모든 연습 모드가 같은 모양·순서를 쓰고 맨 왼쪽 한 칸만 모드를 따른다.
 * - AI 연주 평가(혼자 연습이 아님): ‘퍼펙트 스코어’. 실시간 채점 색은 평가에서만 칠한다(S15P31E102-980).
 * - 혼자 연습·내 악보·구간 연습: 서버에 판정 기준이 있으면 ‘한 음씩’.
 * - 그 뒤는 ‘운지법’·‘한글 표기’. [fingering]이 false(운지법과 함께 보기를 쓰지 않는 태블릿 배치)면 ‘운지법’을 뺀다.
 */
fun PracticeUiState.optionButtons(fingering: Boolean): List<PracticeOptionButton> = buildList {
    if (!solo) add(PracticeOptionButton(PracticeOptionKind.LiveMarks, settings.liveMarks))
    else if (step.available) add(PracticeOptionButton(PracticeOptionKind.Step, step.on, enabled = stepToggleEnabled))
    if (fingering) add(PracticeOptionButton(PracticeOptionKind.Fingering, fingeringView))
    add(PracticeOptionButton(PracticeOptionKind.Hangul, settings.showHangul))
}
