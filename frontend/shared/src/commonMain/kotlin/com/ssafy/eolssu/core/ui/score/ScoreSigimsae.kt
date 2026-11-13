package com.ssafy.eolssu.core.ui.score

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sig_bend_up
import eolssu.shared.generated.resources.sig_push
import eolssu.shared.generated.resources.sig_bend_down
import eolssu.shared.generated.resources.sig_flow
import eolssu.shared.generated.resources.sig_shake
import eolssu.shared.generated.resources.sig_vib
import eolssu.shared.generated.resources.sig_nire
import eolssu.shared.generated.resources.sig_nina
import eolssu.shared.generated.resources.sig_niro
import eolssu.shared.generated.resources.sig_noniro
import eolssu.shared.generated.resources.sig_staccato
import eolssu.shared.generated.resources.sig_fermata
import eolssu.shared.generated.resources.sig_flow_rep
import eolssu.shared.generated.resources.sig_vib_long
import eolssu.shared.generated.resources.sig_roll
import eolssu.shared.generated.resources.sig_nanina
import eolssu.shared.generated.resources.sig_naneuna
import eolssu.shared.generated.resources.sig_none
import eolssu.shared.generated.resources.sig_neonye
import eolssu.shared.generated.resources.sig_norino
import eolssu.shared.generated.resources.sig_nerone
import eolssu.shared.generated.resources.sig_neuneneu
import eolssu.shared.generated.resources.sig_naniro
import eolssu.shared.generated.resources.sig_neuronireu
import eolssu.shared.generated.resources.sig_neunireu
import eolssu.shared.generated.resources.sig_niruni
import eolssu.shared.generated.resources.sig_nanireunonireu
import eolssu.shared.generated.resources.sig_accent
import eolssu.shared.generated.resources.sig_len_double
import eolssu.shared.generated.resources.sig_len_half
import eolssu.shared.generated.resources.sig_no
import eolssu.shared.generated.resources.sig_ni
import eolssu.shared.generated.resources.sig_ro
import eolssu.shared.generated.resources.sig_ri
import eolssu.shared.generated.resources.sig_nina_dur
import eolssu.shared.generated.resources.sig_neuna
import eolssu.shared.generated.resources.sig_nora
import eolssu.shared.generated.resources.sig_neuni
import eolssu.shared.generated.resources.sig_noraneuni
import eolssu.shared.generated.resources.sig_nirena
import eolssu.shared.generated.resources.sig_nerona
import eolssu.shared.generated.resources.sig_nirona
import eolssu.shared.generated.resources.sig_nineurani
import eolssu.shared.generated.resources.sig_neunanina
import eolssu.shared.generated.resources.sig_neunareunani
import eolssu.shared.generated.resources.sig_shake_rep
import eolssu.shared.generated.resources.sig_repeat
import eolssu.shared.generated.resources.sig_tongue
import eolssu.shared.generated.resources.sig_sharp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Display the original registered glyph; this registry does not choose an acoustic interpretation. */
private val ScoreSigimsaeImages: Map<String, DrawableResource> = buildMap {
    put("추성", Res.drawable.sig_bend_up)
    put("bend-up", Res.drawable.sig_bend_up)
    put("미는표", Res.drawable.sig_push)
    put("push", Res.drawable.sig_push)
    put("퇴성", Res.drawable.sig_bend_down)
    put("bend-down", Res.drawable.sig_bend_down)
    put("흘림표", Res.drawable.sig_flow)
    put("flow", Res.drawable.sig_flow)
    put("요성표", Res.drawable.sig_shake)
    put("shake", Res.drawable.sig_shake)
    put("농음표", Res.drawable.sig_vib)
    put("vib", Res.drawable.sig_vib)
    put("니레", Res.drawable.sig_nire)
    put("nire", Res.drawable.sig_nire)
    put("니라", Res.drawable.sig_nina)
    put("nina", Res.drawable.sig_nina)
    put("니로", Res.drawable.sig_niro)
    put("niro", Res.drawable.sig_niro)
    put("노니로", Res.drawable.sig_noniro)
    put("noniro", Res.drawable.sig_noniro)
    put("끊는표", Res.drawable.sig_staccato)
    put("staccato", Res.drawable.sig_staccato)
    put("늘임표", Res.drawable.sig_fermata)
    put("fermata", Res.drawable.sig_fermata)
    put("겹흘림표", Res.drawable.sig_flow_rep)
    put("flow-rep", Res.drawable.sig_flow_rep)
    put("풀어내림표", Res.drawable.sig_vib_long)
    put("vib-long", Res.drawable.sig_vib_long)
    put("떠이어표", Res.drawable.sig_roll)
    put("roll", Res.drawable.sig_roll)
    put("나니나", Res.drawable.sig_nanina)
    put("nanina", Res.drawable.sig_nanina)
    put("나느나", Res.drawable.sig_naneuna)
    put("naneuna", Res.drawable.sig_naneuna)
    put("노네", Res.drawable.sig_none)
    put("none", Res.drawable.sig_none)
    put("너녜", Res.drawable.sig_neonye)
    put("neonye", Res.drawable.sig_neonye)
    put("노리노", Res.drawable.sig_norino)
    put("norino", Res.drawable.sig_norino)
    put("네로네", Res.drawable.sig_nerone)
    put("nerone", Res.drawable.sig_nerone)
    put("느네느", Res.drawable.sig_neuneneu)
    put("neuneneu", Res.drawable.sig_neuneneu)
    put("나니로", Res.drawable.sig_naniro)
    put("naniro", Res.drawable.sig_naniro)
    put("느로니르", Res.drawable.sig_neuronireu)
    put("neuronireu", Res.drawable.sig_neuronireu)
    put("느니르", Res.drawable.sig_neunireu)
    put("neunireu", Res.drawable.sig_neunireu)
    put("니루니", Res.drawable.sig_niruni)
    put("niruni", Res.drawable.sig_niruni)
    put("나니르노니르", Res.drawable.sig_nanireunonireu)
    put("nanireunonireu", Res.drawable.sig_nanireunonireu)
    put("특강표", Res.drawable.sig_accent)
    put("accent", Res.drawable.sig_accent)
    put("덧길이표", Res.drawable.sig_len_double)
    put("len-double", Res.drawable.sig_len_double)
    put("반길이표", Res.drawable.sig_len_half)
    put("len-half", Res.drawable.sig_len_half)
    put("노", Res.drawable.sig_no)
    put("no", Res.drawable.sig_no)
    put("니", Res.drawable.sig_ni)
    put("ni", Res.drawable.sig_ni)
    put("로", Res.drawable.sig_ro)
    put("ro", Res.drawable.sig_ro)
    put("리", Res.drawable.sig_ri)
    put("ri", Res.drawable.sig_ri)
    put("니나", Res.drawable.sig_nina_dur)
    put("nina-dur", Res.drawable.sig_nina_dur)
    put("느나", Res.drawable.sig_neuna)
    put("neuna", Res.drawable.sig_neuna)
    put("노라", Res.drawable.sig_nora)
    put("nora", Res.drawable.sig_nora)
    put("느니", Res.drawable.sig_neuni)
    put("neuni", Res.drawable.sig_neuni)
    put("노라느니", Res.drawable.sig_noraneuni)
    put("noraneuni", Res.drawable.sig_noraneuni)
    put("니레나", Res.drawable.sig_nirena)
    put("nirena", Res.drawable.sig_nirena)
    put("네로나", Res.drawable.sig_nerona)
    put("nerona", Res.drawable.sig_nerona)
    put("니로나", Res.drawable.sig_nirona)
    put("nirona", Res.drawable.sig_nirona)
    put("니느라니", Res.drawable.sig_nineurani)
    put("nineurani", Res.drawable.sig_nineurani)
    put("느나니나", Res.drawable.sig_neunanina)
    put("neunanina", Res.drawable.sig_neunanina)
    put("느나르나니", Res.drawable.sig_neunareunani)
    put("neunareunani", Res.drawable.sig_neunareunani)
    put("겹요성표", Res.drawable.sig_shake_rep)
    put("shake-rep", Res.drawable.sig_shake_rep)
    put("같은음표", Res.drawable.sig_repeat)
    put("repeat", Res.drawable.sig_repeat)
}

/** 그림이 등록된 시김새의 한글 이름(등록 순서). 같은 그림의 영문 별칭은 뺀다. */
internal val RegisteredSigimsaeNames: List<String> = ScoreSigimsaeImages.keys.filter { name -> name.any { it in '가'..'힣' } }

internal fun scoreSigimsaeName(raw: String): String = raw.substringBefore('@')
internal fun scoreSigimsaeImage(raw: String): DrawableResource? = when (val name = scoreSigimsaeName(raw)) {
    // 산조 원문 표기의 표시만 연결한다. 편집 입력 목록과 재생·채점 해석은 바꾸지 않는다.
    "혀치기" -> Res.drawable.sig_tongue
    "반음올림표" -> Res.drawable.sig_sharp
    // 같은 닫는 괄호 모양이며, 흘림표와 꺾는음의 연주 의미는 원본 이름으로 구분한다.
    "꺾는음" -> Res.drawable.sig_flow
    else -> ScoreSigimsaeImages[name]
}

@Composable
internal fun ScoreSigimsaeGlyph(raw: String, size: TextUnit) {
    val resource = scoreSigimsaeImage(raw)
    if (resource == null) {
        Text(scoreSigimsaeName(raw), fontSize = size, lineHeight = size, maxLines = 2)
        return
    }
    val painter = painterResource(resource)
    val height = with(LocalDensity.current) { size.toDp() }.coerceAtLeast(7.dp)
    val ratio = painter.intrinsicSize.let { if (it.height > 0f) it.width / it.height else 1f }
    Image(painter, contentDescription = null, modifier = Modifier.size(minOf(height * ratio, 18.dp), height), contentScale = ContentScale.Fit)
}
