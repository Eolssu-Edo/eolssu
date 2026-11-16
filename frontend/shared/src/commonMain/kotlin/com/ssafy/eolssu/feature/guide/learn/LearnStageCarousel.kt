package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.guide.component.GuideFaint
import com.ssafy.eolssu.feature.guide.component.dashedRoundBorder
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learn_card_again
import eolssu.shared.generated.resources.learn_card_next
import eolssu.shared.generated.resources.learn_card_open
import eolssu.shared.generated.resources.learn_card_open_done
import eolssu.shared.generated.resources.learn_card_prev
import eolssu.shared.generated.resources.learn_card_stage
import eolssu.shared.generated.resources.learn_card_start
import eolssu.shared.generated.resources.learn_done
import eolssu.shared.generated.resources.learn_optional
import eolssu.shared.generated.resources.learn_stage_1
import eolssu.shared.generated.resources.learn_stage_2
import eolssu.shared.generated.resources.learn_stage_3
import eolssu.shared.generated.resources.learn_stage_4
import eolssu.shared.generated.resources.learn_stage_5
import eolssu.shared.generated.resources.learn_stage_6
import eolssu.shared.generated.resources.learn_stage_7
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/*
 * LEARN-02 단계 목록의 그림 카드 넘기기(`.lc`): 이전 · 정사각형 카드 · 다음, 카드 아래 단계 이름과 시작 버튼.
 * 손가락으로 밀거나 화살표 · 위 달리기 지도 · 키보드 ←→로 넘기고, 시작 버튼이나 카드를 누르면 그 단계 상세를 연다.
 */

/**
 * 1~7단계 그림(S15P31E102-749). 원본(1536 × 1024, 회색 바탕)에서 바탕을 걷어 내 투명하게 하고, 호랑이·음표·바람이 있는 곳을 가운데 둔
 * 720 × 720 정사각형으로 미리 잘라 두었다. 흰 카드에서 사라지는 흰 바람·빛줄기는 옅은 회청색으로 바꿨다.
 * 미리 잘라 작게 둔 덕에 넘길 때 읽는 그림이 원본의 1/3 크기다. 8·9단계는 서버가 숨겨 그림도 뺐다(S15P31E102-658).
 */
private val LearnArts: List<DrawableResource> = listOf(
    Res.drawable.learn_stage_1,
    Res.drawable.learn_stage_2,
    Res.drawable.learn_stage_3,
    Res.drawable.learn_stage_4,
    Res.drawable.learn_stage_5,
    Res.drawable.learn_stage_6,
    Res.drawable.learn_stage_7,
)

/** 카드 넘기기의 최대 너비(`.lc`). 태블릿 세로는 [LargeCarouselMaxWidth]를 쓴다. */
private val CarouselMaxWidth = 480.dp

/** 단계 카드 한 변의 최대 크기. 카드를 조금 줄여 목록이 한눈에 들어오게 한다. */
private val CardMaxSize = 240.dp

/** 태블릿 세로([large])의 카드 넘기기 최대 너비. 240dp 카드로는 화면 아래 절반이 비어 카드를 키운다(S15P31E102-1031). */
private val LargeCarouselMaxWidth = 600.dp

/** 태블릿 세로([large])의 단계 카드 한 변 최대 크기. 남는 높이가 모자라면 [cardMaxSize]가 더 줄인다. */
private val LargeCardMaxSize = 420.dp

/** 공개 단계 수만큼 그림이 있는지 테스트에서 확인한다. */
internal val LearnArtCount: Int get() = LearnArts.size

/** 단계 카드에서 그림이 차지하는 비율. 그림 둘레 여백에 더해 카드 테두리·귀퉁이 표시와 겹치지 않게 조금 띄운다. */
private const val LearnCardArtInset = 0.94f

/**
 * 단계 카드 넘기기.
 *
 * @param current 펼칠 단계. 휴대폰·태블릿 세로는 [LearnUiState.cardStage], 가로 배치는 오른쪽 칸의 [LearnUiState.shownStage]다.
 * @param onShow 넘겨서 다른 단계 카드가 자리 잡으면 그 단계를 알린다.
 * @param onOpen 카드를 누르면 그 단계 상세를 연다.
 * @param cardMaxSize 카드 한 변의 최대 크기. 낮은 화면에서 시작 버튼까지 보이도록 줄일 때 쓴다.
 * @param onCardHeight 그려진 카드 높이(px)를 알린다.
 * @param large 태블릿 세로처럼 넓고 키 큰 창이면 카드를 [LargeCardMaxSize]까지 키운다.
 */
@Composable
internal fun LearnStageCarousel(
    state: LearnUiState,
    current: Int,
    onShow: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
    cardMaxSize: Dp = Dp.Unspecified,
    onCardHeight: (Int) -> Unit = {},
    large: Boolean = false,
) {
    val cardCap = if (large) LargeCardMaxSize else CardMaxSize
    val stages = state.stages
    val count = stages.size
    val pager = rememberPagerState(initialPage = (current - 1).coerceIn(0, (count - 1).coerceAtLeast(0))) { count }
    val scope = rememberCoroutineScope()
    val show by rememberUpdatedState(onShow)
    val latest by rememberUpdatedState(current)
    val go = { stage: Int -> scope.launch { pager.animateScrollToPage(stage - 1) }; Unit }
    // 바깥에서 단계가 바뀌면(악기 바꿈 · 가로 배치에서 고름) 그 카드로 넘긴다.
    // 넘기기 위치는 화면 이동 뒤 저장된 쪽으로 되살아나므로, 처음 그릴 때는 지금 단계로 바로 옮긴다(상세에서 돌아옴)
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(current) {
        val target = (current - 1).coerceIn(0, (count - 1).coerceAtLeast(0))
        if (pager.currentPage != target) {
            if (placed) pager.animateScrollToPage(target) else pager.scrollToPage(target)
        }
        placed = true
    }
    // 손으로 넘겨 자리 잡은 카드를 알린다. 되살아난 처음 위치는 알리지 않는다
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect { page -> if (page + 1 != latest) show(page + 1) }
    }
    val shownPage = pager.currentPage + 1
    val stage = stages.getOrNull(shownPage - 1) ?: return

    Column(
        modifier
            .widthIn(max = if (large) LargeCarouselMaxWidth else CarouselMaxWidth)
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 6.dp)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> (shownPage > 1).also { if (it) go(shownPage - 1) }
                    Key.DirectionRight -> (shownPage < count).also { if (it) go(shownPage + 1) }
                    else -> false
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CarouselArrow(next = false, enabled = shownPage > 1) { go(shownPage - 1) }
            // 옆 카드를 미리 그려 두어 넘기는 순간 그림을 읽느라 멈칫하지 않게 한다.
            HorizontalPager(pager, Modifier.weight(1f), pageSpacing = 16.dp, beyondViewportPageCount = 1) { page ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    StageCard(stages[page], state.isDone(stages[page].number), onOpen, cardMaxSize, cardCap, onCardHeight)
                }
            }
            CarouselArrow(next = true, enabled = shownPage < count) { go(shownPage + 1) }
        }
        Column(
            Modifier.padding(start = 48.dp, end = 48.dp, top = 12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(Res.string.learn_card_stage, stage.number),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = EolssuTheme.colors.textMuted,
            )
            Text(
                stage.title,
                Modifier.padding(top = 2.dp),
                style = EolssuTheme.serif.yul.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
            )
        }
        // 카드 그림만으로는 누를 수 있는지 알기 어려워, 눈에 띄는 시작 버튼을 함께 둔다(카드를 눌러도 열린다).
        val done = state.isDone(stage.number)
        EolssuButton(
            text = stringResource(if (done) Res.string.learn_card_again else Res.string.learn_card_start),
            onClick = { onOpen(stage.number) },
            modifier = Modifier.padding(top = 14.dp).widthIn(min = 180.dp),
            style = if (done) EolssuButtonStyle.Secondary else EolssuButtonStyle.Primary,
            icon = EolssuIcons.Play,
            small = true,
        )
    }
}

/** 이전 · 다음 화살표(`.lc-arrow`): 옅은 테두리의 흰 원. 끝 카드에서는 흐리게 끈다. */
@Composable
private fun CarouselArrow(next: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    val label = stringResource(if (next) Res.string.learn_card_next else Res.string.learn_card_prev)
    Box(
        Modifier
            .alpha(if (enabled) 1f else 0.3f)
            // 보이는 원은 40dp, 누르는 영역은 48dp(S15P31E102-971).
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .clip(CircleShape)
            .background(colors.surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (next) EolssuIcons.Chevron else EolssuIcons.Back, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.text)
    }
}

/**
 * 정사각형 단계 카드(`.lc-card`). 완료는 연두 굵은 테두리와 '✓ 완료', 선택 단계는 회색 점선 테두리와 '선택'.
 */
@Composable
private fun StageCard(stage: LearnStage, done: Boolean, onOpen: (Int) -> Unit, maxSize: Dp, cap: Dp, onHeight: (Int) -> Unit) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(20.dp)
    val description = stringResource(
        if (done) Res.string.learn_card_open_done else Res.string.learn_card_open,
        stage.number,
        stage.title,
    )
    Box(
        Modifier
            .widthIn(max = if (maxSize == Dp.Unspecified) cap else minOf(maxSize, cap))
            .fillMaxWidth()
            .aspectRatio(1f)
            .onSizeChanged { onHeight(it.height) }
            .clip(shape)
            .background(colors.surface),
    ) {
        LearnStageArt(stage.number, Modifier.fillMaxSize(), inset = LearnCardArtInset)
        // 테두리와 누름 표시는 그림 위에 그린다. 둥근 카드 모양으로 잘라 누르면 카드 자체만 눌린 것으로 보이게 한다(S15P31E102-749).
        // 완료는 1dp 테두리 + 안쪽 2dp 그림자(`box-shadow: inset`)라 3dp다
        val border = when {
            done -> Modifier.border(3.dp, colors.primary, shape)
            stage.optional -> Modifier.dashedRoundBorder(GuideFaint, 20.dp)
            else -> Modifier
        }
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .clickable(role = Role.Button) { onOpen(stage.number) }
                .semantics { contentDescription = description }
                .then(border),
        )
        if (done) {
            CardBadge(Modifier.align(Alignment.TopEnd), check = true, text = stringResource(Res.string.learn_done))
        } else if (stage.optional) {
            CardBadge(Modifier.align(Alignment.TopStart), check = false, text = stringResource(Res.string.learn_optional))
        }
    }
}

/**
 * 단계 그림 카드: 목록 카드와 같은 흰 바탕·둥근 모서리에 그림을 [LearnCardArtInset]만큼 줄여 놓는다.
 * 단계 상세 머리·과제 안내도 이 카드를 써서 목록 카드와 같은 모습으로 보인다(S15P31E102-793).
 */
@Composable
internal fun LearnStageArtCard(stage: Int, modifier: Modifier, corner: Dp = 20.dp) {
    val shape = RoundedCornerShape(corner)
    LearnStageArt(stage, modifier.clip(shape).background(EolssuTheme.colors.surface), inset = LearnCardArtInset)
}

/**
 * 단계 그림을 [inset] 비율로 줄여 가운데에 그린다(1이면 꽉 채운다). 단계 카드·상세 머리·과제 안내에 쓴다. 바탕은 투명이라 놓인 곳의 색이 비친다.
 */
@Composable
internal fun LearnStageArt(stage: Int, modifier: Modifier, inset: Float = 1f) {
    val bitmap = imageResource(LearnArts[stage - 1])
    Canvas(modifier.clearAndSetSemantics {}) {
        drawImage(
            bitmap,
            dstOffset = IntOffset((size.width * (1 - inset) / 2).roundToInt(), (size.height * (1 - inset) / 2).roundToInt()),
            dstSize = IntSize((size.width * inset).roundToInt(), (size.height * inset).roundToInt()),
            filterQuality = FilterQuality.Medium,
        )
    }
}

/** 카드 귀퉁이 표시(`.lc-badge`). 완료는 연두 바탕 '✓ 완료'(오른쪽 위), 선택은 흰 바탕 회색 '선택'(왼쪽 위). */
@Composable
private fun CardBadge(modifier: Modifier, check: Boolean, text: String) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier
            .padding(18.dp)
            .height(22.dp)
            .then(
                if (check) {
                    Modifier.background(colors.primary, shape)
                } else {
                    Modifier.background(colors.surface, shape).border(1.dp, colors.lineSoft, shape)
                },
            )
            .padding(horizontal = 9.dp)
            .clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        val fg = if (check) colors.onPrimary else colors.textMuted
        if (check) Icon(EolssuIcons.Check, contentDescription = null, modifier = Modifier.size(11.dp), tint = fg)
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold), color = fg)
    }
}
