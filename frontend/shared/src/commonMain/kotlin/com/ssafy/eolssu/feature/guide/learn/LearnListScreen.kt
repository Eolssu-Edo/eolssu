package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.feature.guide.component.GuideTopBar
import com.ssafy.eolssu.feature.guide.component.guidePageMaxWidth
import com.ssafy.eolssu.feature.guide.component.guideSidePadding
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_cancel
import eolssu.shared.generated.resources.common_confirm
import eolssu.shared.generated.resources.learn_reset
import eolssu.shared.generated.resources.learn_reset_confirm_title
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
 * 태블릿 세로도 휴대폰처럼 카드를 넘기고, 카드를 키운 넘기기를 최대 720dp로 가운데에 둔다. 태블릿 가로는 단계 상세와 한 화면이라 [com.ssafy.eolssu.feature.guide.layout.LearnExpandedLayout]이 그린다.
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
            GuideTopBar(stringResource(Res.string.learn_title), onBack = onBack, widthClass = widthClass) {
                LearnResetAction(state, actions.onResetDone)
            }
        },
        snackbarHostState = snackbarHostState,
        containerColor = EolssuTheme.colors.groupedBackground,
        contentMaxWidth = widthClass.guidePageMaxWidth,
    ) {
        val side = widthClass.guideSidePadding
        LearnListContent(
            state,
            actions,
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = side, end = side, bottom = 24.dp),
            currentStage = state.cardStage,
            largeCard = widthClass == WindowWidthClass.Medium,
        )
    }
}

/**
 * 단계 목록 본문(악기 · 달리기 지도 · 단계 카드 넘기기). 세로로 스크롤한다. 단계 구성 출처는 마이페이지 '자료 및 데이터 출처'에 둔다.
 *
 * @param currentStage 펼칠 단계 카드. 태블릿 가로 배치에서는 오른쪽 칸에 보이는 단계다.
 * @param largeCard 태블릿 세로 목록이면 단계 카드를 키워 남는 아래 공간을 채운다.
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
    largeCard: Boolean = false,
) {
    var contentHeight by remember { mutableIntStateOf(0) }
    var cardHeight by remember { mutableIntStateOf(0) }
    // 단계 카드를 화살표·키보드로 넘겨 가고 있는 단계. 카드가 자리 잡기 전에 지도 호랑이가 먼저 따라가는 데만 쓴다.
    // 손으로 끌거나 바깥에서 다른 단계를 고르면 카드 넘기기가 null로 되돌린다.
    var aimedStage by remember(state.instrument) { mutableStateOf<Int?>(null) }
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
            // 호랑이는 카드가 자리 잡기를 기다리지 않고, 아래 카드가 가고 있는 단계로 바로 건너간다(S15P31E102-1102).
            // '초기화하기'는 상단 바 오른쪽 화살표로 옮겼다([LearnResetAction], S15P31E102-1238). 그 줄(48dp)이 빠진 만큼 카드가 커지고, 지도와 카드 사이에는 여백만 둔다.
            // 지도와 카드가 붙어 보이지 않도록 여백을 28dp로 넓혔다(S15P31E102-1276).
            LearnTrailMap(state, viewedStage = aimedStage ?: currentStage, onShowStage = actions.onShowCard, onCelebrated = actions.onFinishCelebrated, modifier = Modifier.padding(bottom = TrailCardGap))
            LearnStageCarousel(
                state,
                currentStage,
                onShow = actions.onShowCard,
                onOpen = actions.onOpenStage,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onAim = { aimedStage = it },
                cardMaxSize = cardMaxSize,
                onCardHeight = { cardHeight = it },
                large = largeCard,
            )
        }
    }
}

/**
 * 상단 바 오른쪽의 '초기화하기'(S15P31E102-1238): 글자 없이 기존 둥근 화살표만 두고, 누르면 바로 확인 창을 띄운다.
 * 진도를 모두 지우므로 바로 지우지 않고 한 번 묻는다(S15P31E102-845). 단계 목록을 받기 전·받지 못했으면 지울 진도가 없어 두지 않는다.
 * 휴대폰·태블릿 세로 상단 바와 태블릿 가로 왼쪽 칸 상단 바가 함께 쓴다. 제목은 [com.ssafy.eolssu.core.ui.component.EolssuTopBar]가 이 자리 너비와 관계없이 가운데에 둔다.
 */
@Composable
internal fun LearnResetAction(state: LearnUiState, onReset: () -> Unit) {
    if (state.track !is LearnTrackLoad.Loaded) return
    var confirming by rememberSaveable { mutableStateOf(false) }
    if (confirming) LearnResetDialog(onConfirm = { confirming = false; onReset() }, onCancel = { confirming = false })
    val label = stringResource(Res.string.learn_reset)
    Box(
        Modifier
            // 뒤로 가기 꺽쇠처럼 화살표 오른쪽 끝을 본문 오른쪽 끝에 맞춘다. 누르는 영역(48dp)과 제목 자리는 그대로다.
            .offset(x = LearnResetIconPull)
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = UserActionLog.click(QaTarget.LEARN_LIST_SCREEN_01, { confirming = true }))
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(EolssuIcons.Restart, contentDescription = null, Modifier.size(LearnResetIconSize), tint = EolssuTheme.colors.textMuted)
    }
}

/** '초기화하기' 화살표 크기. 예전 알약 버튼의 화살표 그대로다. */
private val LearnResetIconSize = 14.dp

/** 48dp 누르는 칸 가운데 화살표를 오른쪽으로 밀어 화살표 오른쪽 끝을 본문 끝에 맞추는 거리. */
private val LearnResetIconPull = (48.dp - LearnResetIconSize) / 2

/** '초기화하기' 확인 창: '진행 과정을 초기화할까요?'와 확인·취소. 바깥을 누르거나 뒤로 가면 취소다. */
@Composable
private fun LearnResetDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    EolssuDialog(title = stringResource(Res.string.learn_reset_confirm_title), icon = EolssuIcons.Restart, onDismissRequest = onCancel) {
        EolssuButton(stringResource(Res.string.common_confirm), onConfirm, Modifier.fillMaxWidth(), qaTarget = QaTarget.LEARN_LIST_SCREEN_BUTTON_01)
        EolssuButton(stringResource(Res.string.common_cancel), onCancel, Modifier.fillMaxWidth(), style = EolssuButtonStyle.Secondary, qaTarget = QaTarget.LEARN_LIST_SCREEN_BUTTON_02)
    }
}

/** 달리기 지도와 단계 카드 사이 여백. */
private val TrailCardGap = 28.dp

/** 낮은 화면에서 줄인 단계 카드의 최소 한 변. 이보다 낮으면 줄이지 않고 스크롤한다. */
private val LearnCardMinSize = 160.dp
