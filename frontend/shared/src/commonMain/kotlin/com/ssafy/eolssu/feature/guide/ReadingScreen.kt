package com.ssafy.eolssu.feature.guide

import com.ssafy.eolssu.core.diagnostics.QaKind
import com.ssafy.eolssu.core.diagnostics.QaContent
import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.ScreenBackHandler
import com.ssafy.eolssu.feature.guide.component.GuideCard
import com.ssafy.eolssu.feature.guide.component.GuideTopBar
import com.ssafy.eolssu.feature.guide.component.LengthRow
import com.ssafy.eolssu.feature.guide.component.PitchSteps
import com.ssafy.eolssu.feature.guide.component.ReadIntro
import com.ssafy.eolssu.feature.guide.component.ReadOrderBoard
import com.ssafy.eolssu.feature.guide.component.ReadingBookNav
import com.ssafy.eolssu.feature.guide.component.ReadingTocButton
import com.ssafy.eolssu.feature.guide.component.ReadingTocMenu
import com.ssafy.eolssu.feature.guide.component.SigimsaeBasicBlock
import com.ssafy.eolssu.feature.guide.component.SigimsaeMorePage
import com.ssafy.eolssu.feature.guide.component.guidePageMaxWidth
import com.ssafy.eolssu.feature.guide.component.guideSidePadding
import com.ssafy.eolssu.feature.guide.component.title
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_reading_sigimsae_small
import eolssu.shared.generated.resources.guide_reading_title
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import org.jetbrains.compose.resources.stringResource

/**
 * GUIDE-02 정간보 쉽게 읽기. 정간보란 · 1 읽는 순서 · 2 음의 길이 · 3 음의 높낮이 · 4 시김새 · 더 많은 시김새를 한 쪽씩 둔 책이다.
 * 휴대폰은 쪽 목차 토글 · 가로로 넘기는 쪽 · 이전/점/다음으로 한 쪽씩 보고(키보드 ←·→ · PageUp·PageDown, 목차는 Esc로 닫음),
 * 태블릿은 넘기지 않고 모든 쪽을 격자로 펼친다([readingTabletRows]).
 * 쪽 · 목차 · 시김새 상태는 화면이 들고, 오디오 상태와 재생 동작은 Route가 전달한다.
 *
 * @param initialPage 처음 펼칠 쪽. 기초부터 배우기 7·8·9단계가 해당 쪽을 넘긴다.
 */
@Composable
internal fun ReadingScreen(
    onBack: () -> Unit,
    audio: ReadingAudioState,
    onPlayAudio: (ReadingAudioClip) -> Unit,
    onStopAudio: () -> Unit,
    initialPage: ReadingPage = ReadingPage.Intro,
    widthClass: WindowWidthClass = currentWindowLayout().widthClass,
) {
    var moreTip by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(widthClass) { onStopAudio() }
    EolssuScaffold(
        topBar = { GuideTopBar(stringResource(Res.string.guide_reading_title), onBack = onBack, widthClass = widthClass) },
        containerColor = EolssuTheme.colors.groupedBackground,
        contentMaxWidth = widthClass.guidePageMaxWidth,
    ) {
        if (widthClass == WindowWidthClass.Compact) {
            ReadingBook(initialPage, moreTip, { moreTip = it }, audio, onPlayAudio, onStopAudio)
        } else {
            ReadingTabletGrid(widthClass, moreTip, { moreTip = it }, audio, onPlayAudio, onStopAudio)
        }
    }
}

/** 휴대폰: 한 쪽씩 넘기는 책. 보이지 않는 쪽은 화면 읽기·누르기에서 뺀다. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ReadingBook(
    initialPage: ReadingPage, moreTip: Int?, onMoreTip: (Int?) -> Unit,
    audio: ReadingAudioState, onPlayAudio: (ReadingAudioClip) -> Unit, onStopAudio: () -> Unit,
) {
    val pages = ReadingPage.entries
    val pagerState = rememberPagerState(initialPage = initialPage.ordinal) { pages.size }
    var tocOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val current = pages[pagerState.currentPage]
    val settled = pages[pagerState.settledPage]
    LaunchedEffect(settled) {
        UserActionLog.event(QaKind.CONTENT_SELECTED, QaTarget.NAVIGATION, content = when (settled) {
            ReadingPage.Intro -> QaContent.READING_INTRO; ReadingPage.Order -> QaContent.READING_ORDER
            ReadingPage.Length -> QaContent.READING_LENGTH; ReadingPage.Pitch -> QaContent.READING_PITCH
            ReadingPage.Sigimsae -> QaContent.READING_SIGIMSAE; ReadingPage.SigimsaeMore -> QaContent.READING_SIGIMSAE_MORE
        })
    }
    LaunchedEffect(current, pagerState.isScrollInProgress) { onStopAudio() }
    val go = { page: ReadingPage ->
        onStopAudio()
        tocOpen = false
        scope.launch { pagerState.animateScrollToPage(page.ordinal) }
        Unit
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // Web 브라우저 뒤로 가기도 목차만 닫는다(S15P31E102-1265).
    ScreenBackHandler(enabled = tocOpen) { tocOpen = false }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || tocOpen || e.isAltPressed || e.isCtrlPressed || e.isMetaPressed) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionRight, Key.PageDown -> settled.next?.let(go) != null
                    Key.DirectionLeft, Key.PageUp -> settled.previous?.let(go) != null
                    else -> false
                }
            },
    ) {
        UniformCardHeight { uniformHeight ->
            Column(Modifier.fillMaxSize()) {
                ReadingTocButton(current, tocOpen, onClick = UserActionLog.click(QaTarget.READING_SCREEN_01, { tocOpen = !tocOpen }), Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp))
                HorizontalPager(pagerState, Modifier.weight(1f).fillMaxWidth().mouseDragPaging(pagerState)) { index ->
                    val page = pages[index]
                    val visible = index == pagerState.settledPage
                    BoxWithConstraints(
                        Modifier
                            .fillMaxSize()
                            .then(if (visible) Modifier else Modifier.clearAndSetSemantics {}),
                    ) {
                        val viewport = maxHeight
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            // 내용이 짧으면 위아래 여백을 똑같이 나눠 가운데에 놓고, 길면 위에서부터 스크롤한다
                            Box(
                                Modifier.fillMaxWidth().heightIn(min = viewport).padding(start = BookSidePadding, end = BookSidePadding, top = 8.dp, bottom = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                ReadingPageCard(
                                    page,
                                    Modifier,
                                    sigimsaeColumns = 1,
                                    moreColumns = 4,
                                    animate = visible && page == ReadingPage.Order,
                                    moreTip = moreTip,
                                    onMoreTip = onMoreTip,
                                    interactive = visible,
                                    showTitle = false,
                                    audio = audio,
                                    onPlayAudio = onPlayAudio,
                                    onStopAudio = onStopAudio,
                                    book = true,
                                    uniformHeight = uniformHeight.takeIf { page in UniformPages },
                                )
                            }
                        }
                    }
                }
                ReadingBookNav(current, onGo = go)
            }
        }
        if (tocOpen) {
            // 목차 밖을 누르면 닫는다
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { tocOpen = false },
            )
            // 목차 단추(48) 아래 여백(8)에서 2 올린 자리(`.rb-menu` top: calc(100% - 2px))
            ReadingTocMenu(current, onSelect = go, Modifier.padding(start = 20.dp, end = 20.dp, top = 54.dp))
        }
    }
}

/**
 * 마우스로 끌어 쪽 넘기기(Web · 데스크톱). Pager는 손가락 밀기만 받아서 마우스 끌기를 직접 옮긴다.
 * 너비의 12% 넘게 끌면 옆 쪽으로, 덜 끌면 제자리로 돌아간다. 끌기 시작하면 안쪽 단추의 누름은 취소된다.
 */
private fun Modifier.mouseDragPaging(state: PagerState): Modifier = pointerInput(state) {
    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.type != PointerType.Mouse) return@awaitEachGesture
            val from = state.currentPage
            var total = 0f
            var dragging = false
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val dx = change.position.x - change.previousPosition.x
                total += dx
                if (!dragging && abs(total) > viewConfiguration.touchSlop) dragging = true
                if (dragging) {
                    state.dispatchRawDelta(-dx)
                    change.consume()
                }
            }
            if (dragging) {
                val threshold = size.width * 0.12f
                val target = when {
                    total < -threshold -> from + 1
                    total > threshold -> from - 1
                    else -> from
                }.coerceIn(0, state.pageCount - 1)
                launch { state.animateScrollToPage(target) }
            }
        }
    }
}

/** 태블릿: 넘기지 않고 모든 쪽을 격자로 펼친다. 세로 2열 · 가로 3열이고 같은 줄 카드의 높이를 맞춘다. 읽는 순서 그림은 늘 움직인다. */
@Composable
private fun ReadingTabletGrid(
    widthClass: WindowWidthClass, moreTip: Int?, onMoreTip: (Int?) -> Unit,
    audio: ReadingAudioState, onPlayAudio: (ReadingAudioClip) -> Unit, onStopAudio: () -> Unit,
) {
    val expanded = widthClass == WindowWidthClass.Expanded
    val side = widthClass.guideSidePadding
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = side, end = side, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        readingTabletRows(if (expanded) 3 else 2).forEach { row ->
            Row(
                Modifier.fillMaxWidth().then(if (row.size > 1) Modifier.height(IntrinsicSize.Min) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                row.forEach { page ->
                    ReadingPageCard(
                        page,
                        Modifier.weight(1f).then(if (row.size > 1) Modifier.fillMaxHeight() else Modifier),
                        sigimsaeColumns = if (expanded) 3 else 2,
                        moreColumns = if (expanded) 9 else 6,
                        animate = true,
                        moreTip = moreTip,
                        onMoreTip = onMoreTip,
                        audio = audio,
                        onPlayAudio = onPlayAudio,
                        onStopAudio = onStopAudio,
                    )
                }
            }
        }
    }
}

/** 휴대폰에서 스크롤 없이 한 화면에 다 보이는 쪽. 세 카드의 높이를 하나로 맞춘다([UniformCardHeight]). */
private val UniformPages = listOf(ReadingPage.Intro, ReadingPage.Order, ReadingPage.Pitch)

/** 높이를 맞춘 카드에 위 · 아래로 각각 더하는 세로 여백(QA: 음의 높낮이 카드 기준 위아래 13px). */
private val UniformCardExtra = 13.dp

/** 휴대폰 책의 정간보란 井 그림 최대 너비(기본 170). 높이를 맞춘 카드의 빈자리를 채우되 3 음의 높낮이 카드보다 높아지지 않는 크기다. */
private val BookIntroFigureMaxWidth = 200.dp

/** 휴대폰 쪽의 좌우 여백. 카드 너비는 쪽 너비에서 이 값의 두 배를 뺀 값이다. */
private val BookSidePadding = 20.dp

/**
 * 휴대폰 책의 [UniformPages] 카드 높이를 구해 [content]에 넘긴다.
 * 세 카드를 쪽과 같은 너비로 보이지 않게 재어 가장 높은 카드(보통 3 음의 높낮이)에 위아래 [UniformCardExtra]를 더한다.
 * 최소 높이로만 쓰므로 글자가 커지거나 오류 문구가 붙어 내용이 더 높아지면 카드도 늘어나 잘리지 않는다.
 */
@Composable
private fun UniformCardHeight(content: @Composable (Dp?) -> Unit) {
    SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
        val cardWidth = (constraints.maxWidth - (BookSidePadding * 2).roundToPx()).coerceAtLeast(0)
        val tallest = subcompose(UniformSlot.Measure) {
            UniformPages.forEach { page ->
                ReadingPageCard(
                    page,
                    Modifier.clearAndSetSemantics {},
                    sigimsaeColumns = 1,
                    moreColumns = 4,
                    animate = false,
                    moreTip = null,
                    onMoreTip = {},
                    audio = ReadingAudioState(),
                    onPlayAudio = {},
                    onStopAudio = {},
                    interactive = false,
                    showTitle = false,
                    book = true,
                )
            }
        }.maxOfOrNull { it.measure(Constraints(minWidth = cardWidth, maxWidth = cardWidth)).height } ?: 0
        val height = if (tallest > 0) tallest.toDp() + UniformCardExtra * 2 else null
        val body = subcompose(UniformSlot.Content) { content(height) }.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) { body.forEach { it.place(0, 0) } }
    }
}

private enum class UniformSlot { Measure, Content }

/**
 * 쪽 하나의 카드. 휴대폰 쪽과 태블릿 격자가 함께 쓴다.
 * 휴대폰은 위쪽 목차 단추가 이미 쪽 표시·이름을 보여 주므로 카드 머리([showTitle])를 빼고, 목차가 없는 태블릿 격자만 카드마다 머리를 단다.
 *
 * @param book 휴대폰 책의 쪽이면 참. 정간보란 井 그림을 키운다([BookIntroFigureMaxWidth]).
 * @param uniformHeight 휴대폰 책에서 맞춘 카드 최소 높이. 있으면 내용을 카드 세로 가운데에 놓는다.
 */
@Composable
private fun ReadingPageCard(
    page: ReadingPage,
    modifier: Modifier,
    sigimsaeColumns: Int,
    moreColumns: Int,
    animate: Boolean,
    moreTip: Int?,
    onMoreTip: (Int?) -> Unit,
    audio: ReadingAudioState,
    onPlayAudio: (ReadingAudioClip) -> Unit,
    onStopAudio: () -> Unit,
    interactive: Boolean = true,
    showTitle: Boolean = true,
    book: Boolean = false,
    uniformHeight: Dp? = null,
) {
    val title = if (showTitle) page.title() else null
    val sized = if (uniformHeight != null) modifier.heightIn(min = uniformHeight) else modifier
    val arrangement = if (uniformHeight != null) Arrangement.Center else Arrangement.Top
    when (page) {
        ReadingPage.Intro -> GuideCard(sized, mark = page.mark, title = title, verticalArrangement = arrangement) {
            if (book) ReadIntro(figureMaxWidth = BookIntroFigureMaxWidth) else ReadIntro()
        }
        ReadingPage.Order -> GuideCard(sized, mark = page.mark, title = title, verticalArrangement = arrangement) { ReadOrderBoard(animate = animate) }
        ReadingPage.Length -> GuideCard(modifier, mark = page.mark, title = title) { LengthRow(audio = audio, onPlayAudio = onPlayAudio, enabled = interactive) }
        ReadingPage.Pitch -> GuideCard(sized, mark = page.mark, title = title, verticalArrangement = arrangement) { PitchSteps(audio, onPlayAudio, enabled = interactive) }
        ReadingPage.Sigimsae -> GuideCard(modifier, mark = page.mark, title = title, small = stringResource(Res.string.guide_reading_sigimsae_small)) {
            SigimsaeBasicBlock(columns = sigimsaeColumns, enabled = interactive, onOpen = onStopAudio)
        }
        ReadingPage.SigimsaeMore -> GuideCard(modifier, mark = page.mark, title = title) {
            SigimsaeMorePage(moreTip, onMoreTip, columns = moreColumns, enabled = interactive)
        }
    }
}
