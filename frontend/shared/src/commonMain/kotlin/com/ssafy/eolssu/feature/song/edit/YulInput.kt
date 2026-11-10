package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Yul

/**
 * 율명 입력판의 음역 단계. [octave]는 공통 악보 `events[].octave`와 같다(배탁성 -2 … 중청성 2).
 * 저장 글자는 공통 악보의 한글 표기(`하배`·`배`·`청`·`중청` + 율명)를 그대로 쓴다.
 */
enum class YulRegister(val octave: Int) {
    BaeTak(-2), Tak(-1), Base(0), Cheong(1), JungCheong(2);

    /** '청': 탁 쪽이나 기본이면 청성, 청성이면 중청성, 중청성이면 기본으로 돌아간다. 탁·청이 함께 걸리지 않는다. */
    fun higher(): YulRegister = when (this) {
        Cheong -> JungCheong
        JungCheong -> Base
        else -> Cheong
    }

    /** '탁': 청 쪽이나 기본이면 탁성, 탁성이면 배탁성, 배탁성이면 기본으로 돌아간다. */
    fun lower(): YulRegister = when (this) {
        Tak -> BaeTak
        BaeTak -> Base
        else -> Tak
    }

    companion object {
        fun of(octave: Int): YulRegister = entries.first { it.octave == octave }
    }
}

/** 기본 12율(황 → 응). 율명 입력판은 이 순서로 가로로 늘어놓는다. */
val BaseYuls: List<Yul> = Yul.entries.take(12)

/**
 * 한글 율명 표기('청태' 등)를 읽은 결과. 율명·음역이나 지속·쉼으로 나눈다.
 */
sealed interface YulTextInput {
    data class Pitch(val base: Yul, val octave: Int) : YulTextInput
    data object Hold : YulTextInput
    data object Rest : YulTextInput

    /** 음역 글자만 있고 율명이 아직 없다('청', '중청'). 입력 중이라 오류로 보지 않는다. */
    data object Incomplete : YulTextInput

    /** 율명으로 읽을 수 없다. 가까운 율명으로 바꾸지 않는다. */
    data object Unknown : YulTextInput
}

/**
 * '태'·'청태'·'중청태'·'탁태'(배태)·'배탁태'(하배태)·'태주'를 읽는다. 앞뒤·사이 공백은 무시한다.
 * 음역 앞말은 긴 것부터 맞춘다: `중청`·`배탁`·`하배`가 `청`·`배`·`탁`보다 먼저다. '중'은 율명(중려)이므로 '중청' 뒤에 율명이 없으면 미완성이다.
 */
fun parseYulText(text: String): YulTextInput {
    val value = text.filterNot { it.isWhitespace() }
    if (value.isEmpty()) return YulTextInput.Incomplete
    when (value) {
        "ㅡ", "-", "지속" -> return YulTextInput.Hold
        "△", "쉼" -> return YulTextInput.Rest
    }
    val (octave, rest) = RegisterPrefixes.firstOrNull { value.startsWith(it.first) }
        ?.let { it.second to value.removePrefix(it.first) } ?: (0 to value)
    if (rest.isEmpty()) return YulTextInput.Incomplete
    val base = BaseYuls.firstOrNull { it.hangul == rest || it.fullName == rest } ?: return YulTextInput.Unknown
    return YulTextInput.Pitch(base, octave)
}

private val RegisterPrefixes = listOf("중청" to 2, "배탁" to -2, "하배" to -2, "청" to 1, "탁" to -1, "배" to -1)

/**
 * 고른 정간에 적을 음. 서버 문서([SheetDraft.original])는 공통 악보 음([ScoreNote.SourcePitch])으로,
 * 예시 악보는 기존 율명([Yul])에 있으면 그 율명으로 적는다. 두 경우 모두 저장 글자는 같은 한글 율명이다.
 */
fun SheetDraft.pitchNote(base: Yul, octave: Int): ScoreNote {
    val source = ScoreNote.SourcePitch(base.hangul, octave)
    if (original != null) return source
    return Yul.fromHangul(source.hangul)?.let(ScoreNote::Pitch) ?: source
}

/** [available]은 서버에 등록된 (율명, 옥타브). null이면 목록을 받지 못해 서버 저장 때 확인한다. */
fun isAvailablePitch(available: Set<Pair<String, Int>>?, base: Yul, octave: Int): Boolean =
    available == null || (base.hangul to octave) in available

/** 정간 음의 (기본 율명, 옥타브). 지속·쉼·빈 자리는 null. 입력판에서 고른 정간의 율명 자판을 표시할 때 쓴다. */
fun ScoreNote.pitchKey(): Pair<Yul, Int>? = when (this) {
    is ScoreNote.SourcePitch -> baseYul to octave
    is ScoreNote.Pitch -> (parseYulText(yul.hangul) as? YulTextInput.Pitch)?.let { it.base to it.octave }
    else -> null
}
