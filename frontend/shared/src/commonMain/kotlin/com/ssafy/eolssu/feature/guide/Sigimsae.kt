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
 * 정간보 쉽게 읽기 5번 시김새(와이어프레임 SIG_BASIC·SIG_MORE). 기초부터 배우기 9단계가 같은 목록을 쓴다.
 * 기호 그림: 우물사이(umulsai.com) jeongganbo-generator, MIT 라이선스(composeResources/files/licenses).
 */

/** 꾸밈음 순서의 한 음. [main]이면 본음이라 검게 칠한다. */
data class SigimsaeStep(val label: String, val length: String = "", val main: Boolean = false)

/** 처음 배우는 시김새 하나. [chip]은 정악·민속악 구분. */
data class SigimsaeItem(
    val images: List<DrawableResource>,
    val name: String,
    val description: String,
    val chip: String? = null,
    val sequence: List<SigimsaeStep> = emptyList(),
)

data class SigimsaeGroup(val title: String, val caption: String, val items: List<SigimsaeItem>)

/** '더 많은 시김새'의 기호와 이름. */
data class SigimsaeSymbol(val image: DrawableResource, val name: String)

data class SigimsaeSymbolGroup(val title: String, val symbols: List<SigimsaeSymbol>)

val BasicSigimsae: List<SigimsaeGroup> = listOf(
    SigimsaeGroup(
        "음을 올리고 내려요",
        "같은 동작을 정악에서는 추성⁠·⁠퇴성, 민속악에서는 미는표⁠·⁠흘림표로 적어요.",
        listOf(
            SigimsaeItem(listOf(Res.drawable.sig_bend_up), "추성", "본음 끝을 위로 부드럽게 밀어 올려요.", chip = "정악"),
            SigimsaeItem(listOf(Res.drawable.sig_push), "미는표", "본음을 밑에서부터 밀어 올려요.", chip = "민속악"),
            SigimsaeItem(listOf(Res.drawable.sig_bend_down), "퇴성", "본음 끝을 아래로 부드럽게 끌어내려요.", chip = "정악"),
            SigimsaeItem(listOf(Res.drawable.sig_flow), "흘림표", "본음 끝을 아래로 흘리듯 꺾어 내려요.", chip = "민속악"),
        ),
    ),
    SigimsaeGroup(
        "음을 떨어요",
        "정악에서는 요성, 민속악에서는 농음이라고 해요. 기호는 メ 모양과 물결 모양을 모두 써요.",
        listOf(SigimsaeItem(listOf(Res.drawable.sig_shake, Res.drawable.sig_vib), "요성·농음", "본음을 위아래로 흔들어 떨어요.")),
    ),
    SigimsaeGroup(
        "앞에서 스치는 꾸밈음",
        "본음 앞에 다른 음을 짧게 스쳐 꾸며요.",
        listOf(
            SigimsaeItem(
                listOf(Res.drawable.sig_nire), "니레", "본음 앞에 한 음 위를 스쳐요.",
                sequence = listOf(SigimsaeStep("한 음 위", "짧게"), SigimsaeStep("본음", main = true)),
            ),
            SigimsaeItem(
                listOf(Res.drawable.sig_nina), "니라", "본음 앞에 두 음 위를 스쳐요.",
                sequence = listOf(SigimsaeStep("두 음 위", "짧게"), SigimsaeStep("본음", main = true)),
            ),
            SigimsaeItem(
                listOf(Res.drawable.sig_niro), "니로", "본음 앞에 세 음 위를 스쳐요.",
                sequence = listOf(SigimsaeStep("세 음 위", "짧게"), SigimsaeStep("본음", main = true)),
            ),
            SigimsaeItem(
                listOf(Res.drawable.sig_noniro), "노니로", "본음 앞에 본음과 한 음 위를 굴려요.",
                sequence = listOf(SigimsaeStep("본음", "짧게"), SigimsaeStep("한 음 위", "짧게"), SigimsaeStep("본음", "길게", main = true)),
            ),
        ),
    ),
    SigimsaeGroup(
        "끊고 늘여요",
        "음높이는 그대로 두고 길이만 바꿔요.",
        listOf(
            SigimsaeItem(listOf(Res.drawable.sig_staccato), "끊는표", "본음을 짧고 또렷하게 끊어 내요."),
            SigimsaeItem(listOf(Res.drawable.sig_fermata), "늘임표", "본음을 원래 길이의 2~3배로 늘여 내요."),
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
