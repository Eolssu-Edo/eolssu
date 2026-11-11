package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.feature.guide.component.GuideTopBar
import com.ssafy.eolssu.feature.guide.component.guidePageMaxWidth
import com.ssafy.eolssu.feature.guide.component.guideSidePadding
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learn_reset
import eolssu.shared.generated.resources.learn_title
import eolssu.shared.generated.resources.learn_track_failed
import eolssu.shared.generated.resources.learn_trail_count
import org.jetbrains.compose.resources.stringResource

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
    /** 결승 축하(마지막 단계를 마친 뒤 호랑이가 뛰기)를 다 보였다. */
    val onFinishCelebrated: () -> Unit = {},
)

/**
 * LEARN-02 단계 목록. 악기별 진행을 달리기 지도로, 단계는 그림 카드로 보여주고, 잠금 없이 아무 단계나 열 수 있다.
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
            GuideTopBar(stringResource(Res.string.learn_title), onBack = onBack, widthClass = widthClass, titleFontSize = LearnTitleFontSize)
        },
        snackbarHostState = snackbarHostState,
        contentMaxWidth = widthClass.guidePageMaxWidth,
    ) {
        val side = widthClass.guideSidePadding
        LearnListContent(
            state,
            actions,
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = side, end = side, bottom = 24.dp),
            currentStage = state.cardStage,
        )
    }
}

/** 기초부터 배우기 제목 크기. 부제 없이 제목만 두어 다른 화면(16sp)보다 키운다. */
internal val LearnTitleFontSize = 21.sp

/**
 * 단계 목록 본문(악기 · 달리기 지도 · 단계 카드 넘기기). 세로로 스크롤한다. 단계 구성 출처는 마이페이지 '자료 및 데이터 출처'에 둔다.
 *
 * @param currentStage 펼칠 단계 카드. 태블릿 가로 배치에서는 오른쪽 칸에 보이는 단계다.
 *
 * 높이가 낮은 휴대폰에서도 시작 버튼까지 한 화면에 들어오도록, 카드 밖 내용의 높이를 재어 남는 높이만큼만 단계 카드를 키운다
 * (최소 [LearnCardMinSize]). 키 큰 화면에서는 전처럼 너비에 맞춘다.
 */
@Composable
internal fun LearnListContent(
    state: LearnUiState,
    actions: LearnListActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
    currentStage: Int = state.cardStage,
) {
    var contentHeight by remember { mutableIntStateOf(0) }
    var cardHeight by remember { mutableIntStateOf(0) }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val cardMaxSize = if (constraints.hasBoundedHeight && contentHeight > 0 && cardHeight > 0) {
            with(density) { (constraints.maxHeight - (contentHeight - cardHeight)).toDp() }.coerceAtLeast(LearnCardMinSize)
        } else {
            Dp.Unspecified
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .onSizeChanged { contentHeight = it.height }
                .padding(contentPadding),
        ) {
            // 진도는 악기마다 따로라, 작은 악기 칩(악기 그림)과 그 악기의 진도를 한 줄에 둔다.
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                LearnInstrumentChip(
                    selected = state.instrument,
                    open = state.isDropdownOpen,
                    onToggle = actions.onToggleDropdown,
                    onSelect = actions.onSelectInstrument,
                    onDismiss = actions.onDismissDropdown,
                )
                Spacer(Modifier.weight(1f))
                if (state.track is LearnTrackLoad.Loaded) {
                    Text(
                        stringResource(Res.string.learn_trail_count, state.requiredDoneCount, state.requiredCount),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                        color = EolssuTheme.colors.textMuted,
                    )
                }
            }
            val track = state.track
            if (track !is LearnTrackLoad.Loaded) {
                LearnLoadStatus(failed = track == LearnTrackLoad.Failed, failedMessage = Res.string.learn_track_failed, onRetry = actions.onRetryTrack)
                return@Column
            }
            LearnTrailMap(state, viewedStage = currentStage, onShowStage = actions.onShowCard, onCelebrated = actions.onFinishCelebrated, modifier = Modifier.padding(bottom = 6.dp))
            // 진도를 모두 지우는 버튼임이 드러나도록 둥근 화살표와 '초기화하기'로 둔다(S15P31E102-749).
            Row(
                Modifier
                    .align(Alignment.End)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(role = Role.Button, onClick = actions.onResetDone)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(EolssuIcons.Restart, contentDescription = null, Modifier.size(14.dp), tint = EolssuTheme.colors.textMuted)
                Text(
                    stringResource(Res.string.learn_reset),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    color = EolssuTheme.colors.textMuted,
                )
            }
            LearnStageCarousel(
                state,
                currentStage,
                onShow = actions.onShowCard,
                onOpen = actions.onOpenStage,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                cardMaxSize = cardMaxSize,
                onCardHeight = { cardHeight = it },
            )
        }
    }
}

/** 낮은 화면에서 줄인 단계 카드의 최소 한 변. 이보다 낮으면 줄이지 않고 스크롤한다. */
private val LearnCardMinSize = 160.dp
