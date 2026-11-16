package com.ssafy.eolssu.feature.practice.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.fingering.label
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.CompactContentMaxWidth
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuPageIconButton
import com.ssafy.eolssu.core.ui.component.EolssuPageTopBar
import com.ssafy.eolssu.core.ui.component.PageHeading
import com.ssafy.eolssu.core.ui.component.songLevelLabel
import com.ssafy.eolssu.feature.practice.PracticeUiState
import com.ssafy.eolssu.feature.practice.sectionEvaluation
import eolssu.shared.generated.resources.jangdan_practice_label
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_eval_title
import eolssu.shared.generated.resources.practice_settings
import eolssu.shared.generated.resources.practice_subtitle
import eolssu.shared.generated.resources.practice_subtitle_mine
import eolssu.shared.generated.resources.practice_subtitle_solo
import eolssu.shared.generated.resources.practice_title
import eolssu.shared.generated.resources.weak_loop_evaluate
import eolssu.shared.generated.resources.weak_loop_title
import org.jetbrains.compose.resources.stringResource

/** 상단 제목: 혼자 연습·내 악보 연습은 ‘연습’, 평가 연습은 ‘연주 평가’, 구간 연습은 ‘구간 연습’, 구간 재평가는 ‘구간 재평가’. */
@Composable
fun practicePageTitle(state: PracticeUiState): String = stringResource(
    when {
        state.sectionEvaluation -> Res.string.weak_loop_evaluate
        state.sectionPractice != null -> Res.string.weak_loop_title
        state.solo || state.mine -> Res.string.practice_title
        else -> Res.string.practice_eval_title
    },
)

/**
 * PRACTICE-01 상단(`.page-head`): 원형 뒤로 가기 · 제목([practicePageTitle]) · 오른쪽 연습 설정 톱니. 평가 연주 중에도 열린다(속도·장단만 설정 안에서 잠긴다).
 * [showSettings]가 아니면 오른쪽을 비운다(태블릿 가로는 상태 패널에 바로가기가 있다).
 * 혼자 연습이면 톱니 왼쪽에 작은 ‘한 음씩’ 토글([PracticeStepToggle])을 둔다(2차 Practice-Ext).
 */
@Composable
fun PracticeTopBar(
    state: PracticeUiState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = CompactContentMaxWidth,
    showSettings: Boolean = true,
    onToggleStep: () -> Unit = {},
    /** false면 머리에 ‘한 음씩’을 두지 않는다. 휴대폰은 연습·구간 연습 모두 설정 버튼 아래 제목 줄에 둔다(S15P31E102-794·816). */
    showStepToggle: Boolean = true,
) {
    EolssuPageTopBar(
        title = practicePageTitle(state),
        onBack = onBack,
        modifier = modifier,
        maxWidth = maxWidth,
        // 회색 바탕 화면(S15P31E102-768)이라 구분선 없이 흰 버튼만 띄운다.
        divider = false,
        right = {
            // 서버에 이 악기의 한 음씩 판정 기준이 없으면 토글을 숨긴다(PRA-06 404).
            if (showStepToggle && state.solo && state.step.available) {
                PracticeStepToggle(state, onToggleStep)
                if (showSettings) Spacer(Modifier.width(2.dp))
            }
            if (showSettings) {
                EolssuPageIconButton(EolssuIcons.Cog, stringResource(Res.string.practice_settings), onOpenSettings, bordered = false)
            }
        },
    )
}

/**
 * 본문 첫 줄(`.section-row`): 곡 제목(Song Myung 25sp)과 오른쪽 `.pill` ‘{악기} · {난이도}’.
 * 곡 제목 크기는 ‘운지법과 함께 보기’를 켜고 꺼도 그대로다(S15P31E102-1017).
 * 연습 모드는 ‘{악기} · 연습 모드’, 내 악보 연습은 ‘{악기} · 내 악보’다.
 * [showPill]이 false이면(휴대폰 연습 화면) 오른쪽을 비워 둔다. 상태 문구(‘연습 준비’·‘연주 중’ 등)는 어느 화면에도 두지 않는다.
 * ‘운지법과 함께 보기’·‘한글 읽는 법 표시’는 연습 설정 안에 있다(S15P31E102-875·980).
 * 곡 제목은 어느 경우에도 한 줄이고, 다 들어가지 않을 때만 왼쪽으로 흘린다(S15P31E102-730).
 */
@Composable
fun PracticeTitleRow(
    state: PracticeUiState,
    modifier: Modifier = Modifier,
    showPill: Boolean = true,
    /** 알약 대신 제목 오른쪽에 둘 내용(휴대폰 연습·구간 연습의 ‘한 음씩’). */
    trailing: (@Composable () -> Unit)? = null,
) {
    val song = state.song ?: return
    val instrument = stringResource(state.instrument.label)
    val pill = if (state.mine) stringResource(Res.string.practice_subtitle_mine, instrument)
        else if (state.solo) stringResource(Res.string.practice_subtitle_solo, instrument)
        else stringResource(Res.string.practice_subtitle, instrument, songLevelLabel(song.level))
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeading(state.title, Modifier.weight(1f), singleLine = true)
        if (trailing != null) trailing() else if (showPill) PracticePill(pill)
    }
}

/** `.pill`: 연두 20% 바탕에 10sp 600 짙은 연두 글자, 반지름 4, 여백 3·7. */
@Composable
fun PracticePill(text: String, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Text(
        text,
        modifier = modifier.background(colors.accentTint, RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold),
        color = colors.accentText,
        maxLines = 1,
    )
}
