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
 * 정간보 쉽게 읽기 4번 시김새(와이어프레임 SIG_BASIC·SIG_MORE). 기초부터 배우기 9단계가 같은 목록을 쓴다.
 * 기호 그림: 우물사이(umulsai.com) jeongganbo-generator, MIT 라이선스(composeResources/files/licenses).
 */

/** 처음 배우는 시김새 하나. 목록에는 기호 · 이름 · 짧은 설명만 둔다. [chip]은 정악·민속악 구분. */
data class SigimsaeItem(
    val images: List<DrawableResource>,
    val name: String,
    val description: String,
    val chip: String? = null,
)

data class SigimsaeGroup(val title: String, val items: List<SigimsaeItem>)

/** '더 많은 시김새'의 기호와 이름. */
data class SigimsaeSymbol(val image: DrawableResource, val name: String)

data class SigimsaeSymbolGroup(val title: String, val symbols: List<SigimsaeSymbol>)

val BasicSigimsae: List<SigimsaeGroup> = listOf(
    SigimsaeGroup(
        "음을 올리고 내려요",
        listOf(
            SigimsaeItem(listOf(Res.drawable.sig_bend_up), "추성", "끝을 올려요", chip = "정악"),
            SigimsaeItem(listOf(Res.drawable.sig_push), "미는표", "밑에서 밀어 올려요", chip = "민속악"),
            SigimsaeItem(listOf(Res.drawable.sig_bend_down), "퇴성", "아래 음으로 꺾어 내려요", chip = "정악"),
            SigimsaeItem(listOf(Res.drawable.sig_flow), "흘림표", "끝을 흐리며 흘려 내려요", chip = "민속악"),
        ),
    ),
    SigimsaeGroup("음을 떨어요", listOf(SigimsaeItem(listOf(Res.drawable.sig_shake, Res.drawable.sig_vib), "요성·농음", "위아래로 떨어요"))),
    SigimsaeGroup(
        "앞에서 스쳐요",
        listOf(
            SigimsaeItem(listOf(Res.drawable.sig_nire), "니레", "한 음 위를 스쳐요"),
            SigimsaeItem(listOf(Res.drawable.sig_nina), "니라", "두 음 위를 스쳐요"),
            SigimsaeItem(listOf(Res.drawable.sig_niro), "니로", "세 음 위를 스쳐요"),
            SigimsaeItem(listOf(Res.drawable.sig_noniro), "노니로", "본음·위 음을 굴려요"),
        ),
    ),
    SigimsaeGroup(
        "끊고 늘여요",
        listOf(
            SigimsaeItem(listOf(Res.drawable.sig_staccato), "끊는표", "짧게 끊어요"),
            SigimsaeItem(listOf(Res.drawable.sig_fermata), "늘임표", "2~3배 늘여요"),
        ),
    ),
)

val MoreSigimsae: List<SigimsaeSymbolGroup> = listOf(
    SigimsaeSymbolGroup(
        "음 옆에 붙는 기호",
        listOf(
            SigimsaeSymbol(Res.drawable.sig_flow_rep, "겹흘림표"), SigimsaeSymbol(Res.drawable.sig_vib_long, "풀어내림표"),
            SigimsaeSymbol(Res.drawable.sig_roll, "떠이어표"), SigimsaeSymbol(Res.drawable.sig_nanina, "나니나"),
            SigimsaeSymbol(Res.drawable.sig_naneuna, "나느나"), SigimsaeSymbol(Res.drawable.sig_none, "노네"),
            SigimsaeSymbol(Res.drawable.sig_neonye, "너녜"), SigimsaeSymbol(Res.drawable.sig_norino, "노리노"),
            SigimsaeSymbol(Res.drawable.sig_nerone, "네로네"), SigimsaeSymbol(Res.drawable.sig_neuneneu, "느네느"),
            SigimsaeSymbol(Res.drawable.sig_naniro, "나니로"), SigimsaeSymbol(Res.drawable.sig_neuronireu, "느로니르"),
            SigimsaeSymbol(Res.drawable.sig_neunireu, "느니르"), SigimsaeSymbol(Res.drawable.sig_niruni, "니루니"),
            SigimsaeSymbol(Res.drawable.sig_nanireunonireu, "나니르노니르"), SigimsaeSymbol(Res.drawable.sig_accent, "특강표"),
            SigimsaeSymbol(Res.drawable.sig_len_double, "덧길이표"), SigimsaeSymbol(Res.drawable.sig_len_half, "반길이표"),
        ),
    ),
    SigimsaeSymbolGroup(
        "한 칸을 차지하는 기호",
        listOf(
            SigimsaeSymbol(Res.drawable.sig_no, "노"), SigimsaeSymbol(Res.drawable.sig_ni, "니"),
            SigimsaeSymbol(Res.drawable.sig_ro, "로"), SigimsaeSymbol(Res.drawable.sig_ri, "리"),
            SigimsaeSymbol(Res.drawable.sig_nina_dur, "니나"), SigimsaeSymbol(Res.drawable.sig_neuna, "느나"),
            SigimsaeSymbol(Res.drawable.sig_nora, "노라"), SigimsaeSymbol(Res.drawable.sig_neuni, "느니"),
            SigimsaeSymbol(Res.drawable.sig_noraneuni, "노라느니"), SigimsaeSymbol(Res.drawable.sig_nirena, "니레나"),
            SigimsaeSymbol(Res.drawable.sig_nerona, "네로나"), SigimsaeSymbol(Res.drawable.sig_nirona, "니로나"),
            SigimsaeSymbol(Res.drawable.sig_nineurani, "니느라니"), SigimsaeSymbol(Res.drawable.sig_neunanina, "느나니나"),
            SigimsaeSymbol(Res.drawable.sig_neunareunani, "느나르나니"), SigimsaeSymbol(Res.drawable.sig_shake_rep, "겹요성표"),
            SigimsaeSymbol(Res.drawable.sig_repeat, "같은음표"),
        ),
    ),
)

val BasicSigimsaeCount: Int get() = BasicSigimsae.sumOf { it.items.size }

val MoreSigimsaeCount: Int get() = MoreSigimsae.sumOf { it.symbols.size }
