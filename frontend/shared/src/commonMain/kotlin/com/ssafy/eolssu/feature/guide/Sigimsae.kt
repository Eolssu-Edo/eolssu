package com.ssafy.eolssu.feature.guide

import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sig_accent
import eolssu.shared.generated.resources.sig_bend_down
import eolssu.shared.generated.resources.sig_bend_up
import eolssu.shared.generated.resources.sig_fermata
import eolssu.shared.generated.resources.sig_flow
import eolssu.shared.generated.resources.sig_flow_rep
import eolssu.shared.generated.resources.sig_len_double
import eolssu.shared.generated.resources.sig_len_half
import eolssu.shared.generated.resources.sig_naneuna
import eolssu.shared.generated.resources.sig_nanina
import eolssu.shared.generated.resources.sig_nanireunonireu
import eolssu.shared.generated.resources.sig_naniro
import eolssu.shared.generated.resources.sig_neonye
import eolssu.shared.generated.resources.sig_nerona
import eolssu.shared.generated.resources.sig_nerone
import eolssu.shared.generated.resources.sig_neuna
import eolssu.shared.generated.resources.sig_neunanina
import eolssu.shared.generated.resources.sig_neunareunani
import eolssu.shared.generated.resources.sig_neuneneu
import eolssu.shared.generated.resources.sig_neuni
import eolssu.shared.generated.resources.sig_neunireu
import eolssu.shared.generated.resources.sig_neuronireu
import eolssu.shared.generated.resources.sig_ni
import eolssu.shared.generated.resources.sig_nina
import eolssu.shared.generated.resources.sig_nina_dur
import eolssu.shared.generated.resources.sig_nineurani
import eolssu.shared.generated.resources.sig_nire
import eolssu.shared.generated.resources.sig_nirena
import eolssu.shared.generated.resources.sig_niro
import eolssu.shared.generated.resources.sig_nirona
import eolssu.shared.generated.resources.sig_niruni
import eolssu.shared.generated.resources.sig_no
import eolssu.shared.generated.resources.sig_none
import eolssu.shared.generated.resources.sig_noniro
import eolssu.shared.generated.resources.sig_nora
import eolssu.shared.generated.resources.sig_noraneuni
import eolssu.shared.generated.resources.sig_norino
import eolssu.shared.generated.resources.sig_push
import eolssu.shared.generated.resources.sig_repeat
import eolssu.shared.generated.resources.sig_ri
import eolssu.shared.generated.resources.sig_ro
import eolssu.shared.generated.resources.sig_roll
import eolssu.shared.generated.resources.sig_shake
import eolssu.shared.generated.resources.sig_shake_rep
import eolssu.shared.generated.resources.sig_staccato
import eolssu.shared.generated.resources.sig_vib
import eolssu.shared.generated.resources.sig_vib_long
import org.jetbrains.compose.resources.DrawableResource

/*
 * 정간보 쉽게 읽기 4번 시김새와 '더 많은 시김새' 쪽(와이어프레임 SIG_BASIC·SIG_MORE). 기초부터 배우기 9단계가 같은 목록을 쓴다.
 * 기호 그림: 우물사이(umulsai.com) jeongganbo-generator, MIT 라이선스(composeResources/files/licenses).
 */

/** 처음 배우는 시김새 하나. 목록에는 기호 · 이름 · 짧은 설명만 두고, 누르면 [figure]를 창에서 그린다. [chip]은 정악·민속악 구분. */
data class SigimsaeItem(
    val images: List<DrawableResource>,
    val name: String,
    val description: String,
    val figure: SigimsaeFigure,
    val chip: String? = null,
)

/** [hint]는 무리 전체에 붙는 그림 읽는 법이다(창의 범례 끝에 단다). */
data class SigimsaeGroup(val title: String, val items: List<SigimsaeItem>, val hint: String? = null)

/**
 * 부는 모양 그림(가로 시간 · 세로 음높이). SVG viewBox 좌표를 그대로 쓴다.
 * [unit]과 [Curve.vibrato]는 그림이 아니라 시김새 소리를 만들 때 음높이를 세는 데 쓴다.
 */
sealed interface SigimsaeFigure {
    /** 한 음 높이 차이가 viewBox에서 몇 단위인지. */
    val unit: Float

    /**
     * viewBox 72×38, 적힌 음 높이 y=24, 한 음 = 6. [curve]는 부는 소리 선(SVG path),
     * [tail]은 가늘어지며 흐려지는 꼬리(채운 도형), [end]는 적힌 길이의 끝(눈금을 긋고 적힌 음 점선도 여기까지).
     */
    data class Curve(
        val curve: String,
        val tail: String? = null,
        val end: Float? = null,
        override val unit: Float = 6f,
        val vibrato: Boolean = false,
    ) : SigimsaeFigure

    /** 본음 앞에 짧게 스치는 음들. [steps]는 본음에서 몇 음 위인지다(viewBox 72×48, 한 음 = 가는 점선 한 칸). */
    data class Grace(val steps: List<Int>) : SigimsaeFigure {
        override val unit: Float get() = GraceStep
    }
}

/** 앞에서 스치는 시김새 그림의 본음 높이와 한 음 간격. */
internal const val GraceBase = 42f
internal const val GraceStep = 11f

/**
 * 앞에서 스치는 시김새의 소리 선(SVG path)과 스치는 음의 점. 점에서 본음으로 부드럽게 미끄러져 내려와 길게 분다.
 * 굴리는 노니로([0, 1])는 본음 → 위 음 → 본음으로 둥글게 넘어간다.
 */
internal fun gracePath(steps: List<Int>): Pair<String, List<Pair<Float, Float>>> {
    val xs = steps.indices.map { 9f + it * 13f }
    val ys = steps.map { GraceBase - it * GraceStep }
    val last = xs.lastIndex
    val mx = xs[last] + 14f
    val d = StringBuilder("M${xs[0].svg()} ${ys[0].svg()}")
    for (i in 1..last) {
        d.append("C${(xs[i - 1] + 7).svg()} ${ys[i - 1].svg()} ${(xs[i] - 7).svg()} ${ys[i].svg()} ${xs[i].svg()} ${ys[i].svg()}")
    }
    d.append("C${(xs[last] + 7).svg()} ${ys[last].svg()} ${(mx - 7).svg()} ${GraceBase.svg()} ${mx.svg()} ${GraceBase.svg()}H68")
    return d.toString() to xs.zip(ys)
}

private fun Float.svg(): String = if (this % 1f == 0f) toInt().toString() else toString()

/** '더 많은 시김새'의 기호 · 이름 · 누르면 보이는 한 줄 설명. */
data class SigimsaeSymbol(val image: DrawableResource, val name: String, val description: String)

data class SigimsaeSymbolGroup(val title: String, val symbols: List<SigimsaeSymbol>)

val BasicSigimsae: List<SigimsaeGroup> = listOf(
    SigimsaeGroup(
        "음을 올리고 내려요",
        listOf(
            SigimsaeItem(listOf(Res.drawable.sig_bend_up), "추성", "끝을 올려요", SigimsaeFigure.Curve("M4 24H44C54 24 60 22 68 12"), chip = "정악"),
            SigimsaeItem(listOf(Res.drawable.sig_push), "미는표", "밑에서 밀어 올려요", SigimsaeFigure.Curve("M4 34C10 34 14 24 22 24H68"), chip = "민속악"),
            // 퇴성은 음높이 변화라 아래 음으로 짧고 분명하게 꺾이는 계단, 흘림표는 소리 끝 처리라 가늘어지다 사라지는 꼬리로 나눠 보인다
            // (교육용 구분이며 원기호를 재현한 것은 아니다). 퇴성 그림은 8만큼 또렷하게 꺾되 소리는 딱 한 음 아래다(unit 8)
            SigimsaeItem(listOf(Res.drawable.sig_bend_down), "퇴성", "아래 음으로 꺾어 내려요", SigimsaeFigure.Curve("M4 24H46L51 32H68", unit = 8f), chip = "정악"),
            SigimsaeItem(
                listOf(Res.drawable.sig_flow),
                "흘림표",
                "끝을 흐리며 흘려 내려요",
                SigimsaeFigure.Curve("M4 24H30", tail = "M30 23.4C44 23.4 56 28.6 68 36C56 29.4 44 24.6 30 24.6Z"),
                chip = "민속악",
            ),
        ),
    ),
    SigimsaeGroup(
        "음을 떨어요",
        listOf(
            SigimsaeItem(
                listOf(Res.drawable.sig_shake, Res.drawable.sig_vib),
                "요성·농음",
                "위아래로 떨어요",
                SigimsaeFigure.Curve("M4 24H14Q19 16 24 24T34 24T44 24T54 24T64 24H68", vibrato = true),
            ),
        ),
    ),
    SigimsaeGroup(
        "앞에서 스쳐요",
        listOf(
            SigimsaeItem(listOf(Res.drawable.sig_nire), "니레", "한 음 위를 스쳐요", SigimsaeFigure.Grace(listOf(1))),
            SigimsaeItem(listOf(Res.drawable.sig_nina), "니라", "두 음 위를 스쳐요", SigimsaeFigure.Grace(listOf(2))),
            SigimsaeItem(listOf(Res.drawable.sig_niro), "니로", "세 음 위를 스쳐요", SigimsaeFigure.Grace(listOf(3))),
            SigimsaeItem(listOf(Res.drawable.sig_noniro), "노니로", "본음·위 음을 굴려요", SigimsaeFigure.Grace(listOf(0, 1))),
        ),
        hint = "가는 줄 한 칸 = 한 음",
    ),
    SigimsaeGroup(
        "끊고 늘여요",
        listOf(
            SigimsaeItem(listOf(Res.drawable.sig_staccato), "끊는표", "짧게 끊어요", SigimsaeFigure.Curve("M4 24H16", end = 40f)),
            SigimsaeItem(listOf(Res.drawable.sig_fermata), "늘임표", "2~3배 늘여요", SigimsaeFigure.Curve("M4 24H68", end = 26f)),
        ),
    ),
)

/*
 * 한 줄 설명의 근거(위가 우선). 정리: docs/knowledge/domain/sigimsae-symbols.md
 *  ㉮ 국립국악원 『대금 정악보』 '대금 악상기호' 표: 겹흘림표 · 풀어내림표 · 떠이어표 · 노리노 · 느네느 · 나니로 · 나니르노니르 · 덧길이표 ·
 *     반길이표 · 네로나 · 니느라니 · 느나르나니 · 겹요성표 · 같은음표
 *  ㉯ 국립국어원 『한국 음악 점자 규정 해설』 제16~19항: 나머지(㉮와 뜻이 같다)
 *  ㉰ 우물사이 기호 사전의 소리 정의: 두 자료 어디에도 없는 노라느니 하나(사전 스스로 '추정'이라 적음)
 */
val MoreSigimsae: List<SigimsaeSymbolGroup> = listOf(
    SigimsaeSymbolGroup(
        "음 옆에 붙는 기호",
        listOf(
            SigimsaeSymbol(Res.drawable.sig_flow_rep, "겹흘림표", "소리 끝을 흘려 내리다가 다시 올려 쳐 내려요"),
            SigimsaeSymbol(Res.drawable.sig_vib_long, "풀어내림표", "높은 음에서 낮은 음으로 옮길 때 흔들며 차차 풀어 내려요"),
            SigimsaeSymbol(Res.drawable.sig_roll, "떠이어표", "특히 강하게 튀기며 굴려 내요"),
            SigimsaeSymbol(Res.drawable.sig_nanina, "나니나", "본음 · 한 음 위 · 본음을 같은 길이로 내요"),
            SigimsaeSymbol(Res.drawable.sig_naneuna, "나느나", "본음 · 한 음 아래 · 본음을 같은 길이로 내요"),
            SigimsaeSymbol(Res.drawable.sig_none, "노네", "한 음 아래 음을 짧게 먼저 내요"),
            SigimsaeSymbol(Res.drawable.sig_neonye, "너녜", "두 음 아래 음을 짧게 먼저 내요"),
            SigimsaeSymbol(Res.drawable.sig_norino, "노리노", "본음 · 두 음 위를 짧게, 본음을 길게 내요"),
            SigimsaeSymbol(Res.drawable.sig_nerone, "네로네", "본음 · 한 음 아래를 짧게, 본음을 길게 내요"),
            SigimsaeSymbol(Res.drawable.sig_neuneneu, "느네느", "본음 · 두 음 위를 짧게, 본음을 길게 내요"),
            SigimsaeSymbol(Res.drawable.sig_naniro, "나니로", "한 음 아래 · 한 음 위를 짧게, 본음을 길게 내요"),
            SigimsaeSymbol(Res.drawable.sig_neuronireu, "느로니르", "본음 · 한 음 아래 · 한 음 위를 짧게, 본음을 길게 내요"),
            SigimsaeSymbol(Res.drawable.sig_neunireu, "느니르", "한 음 아래를 짧게, 본음을 길게, 다시 한 음 아래를 짧게 내요"),
            SigimsaeSymbol(Res.drawable.sig_niruni, "니루니", "한 음 위를 짧게, 본음을 길게, 다시 한 음 위를 짧게 내요"),
            SigimsaeSymbol(Res.drawable.sig_nanireunonireu, "나니르노니르", "나니로(한 음 아래 · 한 음 위)와 로니로(두 음 아래 · 한 음 위)를 합쳐 꾸며요"),
            SigimsaeSymbol(Res.drawable.sig_accent, "특강표", "그 음을 특히 강하게 내요"),
            SigimsaeSymbol(Res.drawable.sig_len_double, "덧길이표", "적힌 길이보다 길게 내요"),
            SigimsaeSymbol(Res.drawable.sig_len_half, "반길이표", "적힌 길이보다 짧게 내고, 줄인 만큼 덧길이표 자리에서 늘려요"),
        ),
    ),
    SigimsaeSymbolGroup(
        "한 칸을 차지하는 기호",
        listOf(
            SigimsaeSymbol(Res.drawable.sig_no, "노", "한 음 아래 음을 칸 길이만큼 내요"),
            SigimsaeSymbol(Res.drawable.sig_ni, "니", "한 음 위 음을 칸 길이만큼 내요"),
            SigimsaeSymbol(Res.drawable.sig_ro, "로", "두 음 아래 음을 칸 길이만큼 내요"),
            SigimsaeSymbol(Res.drawable.sig_ri, "리", "두 음 위 음을 칸 길이만큼 내요"),
            SigimsaeSymbol(Res.drawable.sig_nina_dur, "니나", "한 음 위 · 본음을 칸 안에서 똑같이 나눠 내요"),
            SigimsaeSymbol(Res.drawable.sig_neuna, "느나", "한 음 아래 · 본음을 칸 안에서 똑같이 나눠 내요"),
            SigimsaeSymbol(Res.drawable.sig_nora, "노라", "한 음 아래 · 두 음 아래를 칸 안에서 나눠 내요"),
            SigimsaeSymbol(Res.drawable.sig_neuni, "느니", "한 음 위 · 두 음 위를 칸 안에서 나눠 내요"),
            SigimsaeSymbol(Res.drawable.sig_noraneuni, "노라느니", "한 음 아래 · 두 음 아래 · 한 음 위 · 두 음 위를 나눠 내요"),
            SigimsaeSymbol(Res.drawable.sig_nirena, "니레나", "두 음 위 · 한 음 위 · 본음을 칸 안에서 나눠 내요"),
            SigimsaeSymbol(Res.drawable.sig_nerona, "네로나", "한 음 · 두 음 · 세 음 아래를 칸 안에서 똑같이 나눠 내요"),
            SigimsaeSymbol(Res.drawable.sig_nirona, "니로나", "한 음 위 · 본음 · 한 음 아래를 칸 안에서 나눠 내요"),
            SigimsaeSymbol(Res.drawable.sig_nineurani, "니느라니", "한 음 위 · 본음 · 한 음 아래 · 본음을 나눠 내요"),
            SigimsaeSymbol(Res.drawable.sig_neunanina, "느나니나", "한 음 아래 · 본음 · 한 음 위 · 본음을 나눠 내요"),
            SigimsaeSymbol(Res.drawable.sig_neunareunani, "느나르나니", "한 음 위 · 두 음 위 · 한 음 위 · 본음 · 한 음 아래를 똑같이 나눠 내요"),
            SigimsaeSymbol(Res.drawable.sig_shake_rep, "겹요성표", "본음과 한 음 위로 요성을 두 번 해요"),
            SigimsaeSymbol(Res.drawable.sig_repeat, "같은음표", "같은 율명이나 가락을 다시 적지 않고 줄여 쓸 때 써요"),
        ),
    ),
)

val BasicSigimsaeCount: Int get() = BasicSigimsae.sumOf { it.items.size }

val MoreSigimsaeCount: Int get() = MoreSigimsae.sumOf { it.symbols.size }
