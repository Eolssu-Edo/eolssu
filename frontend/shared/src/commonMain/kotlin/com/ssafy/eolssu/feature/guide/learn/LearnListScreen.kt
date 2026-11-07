package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.feature.guide.component.GuideFaint
import com.ssafy.eolssu.feature.guide.component.GuideText
import com.ssafy.eolssu.feature.guide.component.GuideTopBar
import com.ssafy.eolssu.feature.guide.component.InstrumentDropdown
import com.ssafy.eolssu.feature.guide.component.guidePageMaxWidth
import com.ssafy.eolssu.feature.guide.component.guideSidePadding
import com.ssafy.eolssu.feature.guide.component.label
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learn_list_note
import eolssu.shared.generated.resources.learn_progress_all_done
import eolssu.shared.generated.resources.learn_progress_done
import eolssu.shared.generated.resources.learn_progress_label
import eolssu.shared.generated.resources.learn_progress_next
import eolssu.shared.generated.resources.learn_progress_not_started
import eolssu.shared.generated.resources.learn_progress_total
import eolssu.shared.generated.resources.learn_reset
import eolssu.shared.generated.resources.learn_stages
import eolssu.shared.generated.resources.learn_subtitle
import eolssu.shared.generated.resources.learn_title
import eolssu.shared.generated.resources.learn_track_failed
import org.jetbrains.compose.resources.stringResource
import com.ssafy.eolssu.core.model.fingering.label

/** 단계 목록의 동작. 휴대폰·태블릿 세로 목록과 태블릿 가로 왼쪽 칸이 함께 쓴다. */
class LearnListActions(
    val onToggleDropdown: () -> Unit,
    val onDismissDropdown: () -> Unit,
    val onSelectInstrument: (GuideInstrument) -> Unit,
    val onResetDone: () -> Unit,
    val onOpenStage: (Int) -> Unit,
    /** 단계 카드를 넘겨 [Int] 단계가 자리 잡았다. */
    val onShowCard: (Int) -> Unit,
    /** 단계 목록을 받지 못했을 때 '다시 시도'. */
    val onRetryTrack: () -> Unit = {},
)

/**
 * LEARN-02 단계 목록. 악기별 진행률과 9단계 그림 카드를 보여주고, 잠금 없이 아무 단계나 열 수 있다.
 * 태블릿 세로도 휴대폰처럼 카드를 넘기고 카드는 최대 480dp로 가운데에 둔다. 태블릿 가로는 단계 상세와 한 화면이라 [com.ssafy.eolssu.feature.guide.layout.LearnExpandedLayout]이 그린다.
 */
@Composable
fun LearnListScreen(
    state: LearnUiState,
    widthClass: WindowWidthClass,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    actions: LearnListActions,
) {
    EolssuScaffold(
        topBar = {
            GuideTopBar(stringResource(Res.string.learn_title), onBack = onBack, widthClass = widthClass, subtitle = stringResource(Res.string.learn_subtitle))
        },
        snackbarHostState = snackbarHostState,
        contentMaxWidth = widthClass.guidePageMaxWidth,
    ) {
        val tablet = widthClass != WindowWidthClass.Compact
        val side = widthClass.guideSidePadding
        LearnListContent(
            state,
            actions,
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = side, end = side, bottom = 24.dp),
            currentStage = state.cardStage,
            dropdownMaxWidth = if (tablet) TabletDropdownMaxWidth else Dp.Unspecified,
        )
    }
}

/** 태블릿 세로 목록의 악기 고르기 최대 너비(이름 + 드롭다운 360dp, `.tabapp .fg-select .dd`). */
private val TabletDropdownMaxWidth = 400.dp

/**
 * 단계 목록 본문(악기 · 진행 카드 · 단계 카드 넘기기 · 단계 판정 안내). 세로로 스크롤한다. 단계 구성 출처는 마이페이지 '자료 및 데이터 출처'에 둔다.
 *
 * @param currentStage 펼칠 단계 카드. 태블릿 가로 배치에서는 오른쪽 칸에 보이는 단계다.
 */
@Composable
internal fun LearnListContent(
    state: LearnUiState,
    actions: LearnListActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
    currentStage: Int = state.cardStage,
    dropdownMaxWidth: Dp = Dp.Unspecified,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        InstrumentDropdown(
            selected = state.instrument,
            open = state.isDropdownOpen,
            onToggle = actions.onToggleDropdown,
            onSelect = actions.onSelectInstrument,
            onDismiss = actions.onDismissDropdown,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp).widthIn(max = dropdownMaxWidth),
        )
        val track = state.track
        if (track !is LearnTrackLoad.Loaded) {
            LearnLoadStatus(failed = track == LearnTrackLoad.Failed, failedMessage = Res.string.learn_track_failed, onRetry = actions.onRetryTrack)
            return@Column
        }
        ProgressCard(state)
        // `.lp-head`: '단계' 옆에 지금 카드 번호(`.lc-count`, 옅은 회색 고정폭 숫자)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    append(stringResource(Res.string.learn_stages))
                    withStyle(SpanStyle(color = GuideFaint, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum")) {
                        append("  $currentStage / ${state.stages.size}")
                    }
                },
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = EolssuTheme.colors.textMuted,
            )
            Text(
                stringResource(Res.string.learn_reset),
                Modifier.clickable(role = Role.Button, onClick = actions.onResetDone),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, textDecoration = TextDecoration.Underline),
                color = EolssuTheme.colors.textMuted,
            )
        }
        LearnStageCarousel(
            state,
            currentStage,
            onShow = actions.onShowCard,
            onOpen = actions.onOpenStage,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            stringResource(Res.string.learn_list_note),
            Modifier.padding(top = 14.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 17.sp),
            color = EolssuTheme.colors.textMuted,
        )
    }
}

/** 진행 카드(`.lp-progress`). 진행률은 필수 8단계 기준이다. */
@Composable
private fun ProgressCard(state: LearnUiState) {
    val colors = EolssuTheme.colors
    val small = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .background(colors.surface, RoundedCornerShape(18.dp))
            .border(1.dp, colors.lineSoft, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.learn_progress_label, stringResource(state.instrument.label)), Modifier.weight(1f), style = small, color = colors.textMuted)
            val doneCount = state.doneStages.size
            Text(
                if (doneCount > 0) stringResource(Res.string.learn_progress_done, doneCount) else stringResource(Res.string.learn_progress_not_started),
                style = small,
                color = colors.textMuted,
            )
        }
        val total = stringResource(Res.string.learn_progress_total, state.requiredCount)
        Text(
            buildAnnotatedString {
                append("${state.requiredDoneCount}")
                withStyle(SpanStyle(fontFamily = MaterialTheme.typography.bodySmall.fontFamily, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.textMuted)) {
                    append("  ")
                    append(total)
                }
            },
            style = EolssuTheme.serif.yul.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
        )
        Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.background)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(state.progress).background(colors.primary))
        }
        val next = state.nextStage
        GuideText(
            if (next != null) stringResource(Res.string.learn_progress_next, next.number, next.title) else stringResource(Res.string.learn_progress_all_done),
            Modifier.padding(top = 10.dp),
            style = small,
        )
    }
}
