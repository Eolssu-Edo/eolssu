package com.ssafy.eolssu.feature.home.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.ProfileImage
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.mountainRidges
import com.ssafy.eolssu.feature.home.HomeFeature
import com.ssafy.eolssu.feature.home.HomeFeatureColumns
import com.ssafy.eolssu.feature.home.HomeScrollRange
import com.ssafy.eolssu.feature.home.HomeStageFlingBehavior
import com.ssafy.eolssu.feature.home.PrepareHomeFonts
import com.ssafy.eolssu.feature.home.homeScrollFrame
import com.ssafy.eolssu.feature.home.homeSnapTarget
import com.ssafy.eolssu.feature.home.mix
import com.ssafy.eolssu.feature.home.rememberHomeEnvironment
import com.ssafy.eolssu.feature.home.component.FeatureSection
import com.ssafy.eolssu.feature.home.component.HomeInkButton
import com.ssafy.eolssu.feature.home.component.MyPagePlate
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.home_hero_brush_lettering
import eolssu.shared.generated.resources.home_hero_logo_white
import eolssu.shared.generated.resources.home_hero_title
import eolssu.shared.generated.resources.home_illustration
import eolssu.shared.generated.resources.home_scroll_to_features
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** 고정된 그림·본문 무대를 400dp 스크롤로 펼친 뒤, 낮은 창에서는 카드 끝까지 계속 스크롤한다. */
@Composable
internal fun HomeScrollLayout(
    widthClass: WindowWidthClass,
    profileImage: ProfileImage,
    onOpenMyPage: () -> Unit,
    onStartEvaluation: () -> Unit,
    onFeatureClick: (HomeFeature) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val dragged by scroll.interactionSource.collectIsDraggedAsState()
    val scope = rememberCoroutineScope()
    val environment = rememberHomeEnvironment()
    PrepareHomeFonts()
    val homeGround = EolssuTheme.colors.groupedBackground
    val reduceMotion = environment.reduceMotion || scope.coroutineContext[MotionDurationScale]?.scaleFactor == 0f
    var wasOpen by remember { mutableStateOf(false) }
    var snapping by remember { mutableStateOf(false) }
    val bounce = remember { Animatable(0f) }
    val rangePx = with(density) { HomeScrollRange.dp.roundToPx() }
    suspend fun goTo(target: Int) {
        snapping = true
        try {
            if (reduceMotion) scroll.scrollTo(target) else scroll.animateScrollTo(target, tween(500))
            wasOpen = target >= rangePx
        } finally { snapping = false }
    }
    // 끌기를 마치면 손을 뗀 속도를 이어받아 관성과 스냅을 한 번에 처리한다.
    val decay = rememberSplineBasedDecay<Float>()
    val freeFling = ScrollableDefaults.flingBehavior()
    val fling = remember(decay, freeFling, density, reduceMotion) {
        HomeStageFlingBehavior({ scroll.value.toFloat() }, density.density, { wasOpen }, reduceMotion, decay, freeFling)
    }
    LaunchedEffect(scroll, density, reduceMotion) {
        var dragGesture = false
        snapshotFlow { Triple(scroll.value, scroll.isScrollInProgress, dragged) }.collectLatest { (value, busy, touching) ->
            if (touching) dragGesture = true
            if (value >= rangePx) wasOpen = true
            if (value == 0) wasOpen = false
            if (busy || touching || snapping) return@collectLatest
            // 끌기는 fling이 스냅까지 맡는다. 손을 뗀 뒤 fling이 시작되기 전의 빈틈에 따로 스냅을 띄우면 두 번 움직인다.
            if (dragGesture) {
                dragGesture = false
                return@collectLatest
            }
            // 휠·키보드처럼 관성이 없는 스크롤만 이벤트 사이를 기다린 뒤 가까운 쪽으로 맞춘다.
            delay(160)
            val target = with(density) { homeSnapTarget(value / density.density, wasOpen).dp.roundToPx() }
            if (target != value) scope.launch { goTo(target) }
        }
    }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) bounce.snapTo(0f)
        else bounce.animateTo(6f, infiniteRepeatable(tween(1000), RepeatMode.Reverse))
    }
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().background(homeGround)) {
        val w = maxWidth.value
        val h = maxHeight.value
        val expanded = widthClass == WindowWidthClass.Expanded
        val medium = widthClass == WindowWidthClass.Medium
        val side = if (expanded || medium) 40f else 20f
        val contentW = (w - side * 2).coerceAtMost(1120f)
        val contentLeft = (w - contentW) / 2
        val finalHeroW = if (expanded) contentW * .52f else w
        val buttonW = (if (expanded) finalHeroW else contentW).coerceAtMost(360f)
        var buttonH by remember { mutableFloatStateOf(56.5f) }
        var headlineH by remember { mutableFloatStateOf(100f) }
        var featureH by remember(widthClass) { mutableFloatStateOf(480f) }
        val s = scroll.value / density.density
        val frame = homeScrollFrame(w, h, s, buttonH)
        val featureTop = if (expanded) mix(h + 32f, 140f, frame.eased)
            else frame.buttonTop + buttonH + 16f + 76f * (1 - frame.eased)
        val featureEndTop = if (expanded) 140f else frame.bandHeight + 12f + buttonH + 16f
        val overflow = (maxOf(featureEndTop + featureH + 34f, frame.bandHeight + buttonH + 58f) - h).coerceAtLeast(0f)
        val artWidth = mix(w, finalHeroW, frame.eased)
        val artLeft = if (expanded) contentLeft * frame.eased else 0f
        val openFeatures = frame.progress > .6f
        val featureAlpha = ((frame.progress - .6f) / .25f).coerceIn(0f, 1f)
        val mountainColor = EolssuTheme.colors.radical
        val open: () -> Unit = { scope.launch { goTo(rangePx) } }
        // 화살표는 펼친 무대 아래 카드 끝까지 내린다. 펼치는 도중 카드 높이를 처음 재면 바닥이 늘 수 있어 한 번 더 맞춘다.
        val openToBottom: () -> Unit = {
            scope.launch {
                goTo(scroll.maxValue)
                if (scroll.value < scroll.maxValue) goTo(scroll.maxValue)
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(scroll, flingBehavior = fling)) {
            Box(Modifier.fillMaxWidth().height((h + HomeScrollRange + overflow).dp)) {
                Box(
                    Modifier.offset { IntOffset(0, minOf(scroll.value, rangePx)) }
                        .fillMaxWidth().height((h + overflow).dp),
                ) {
                    HomeArt(
                        artWidth, artLeft, frame.artScale,
                        if (expanded) mix(-200f * w / 390f, (frame.bandHeight - finalHeroW * 1448f / 1086f) * .62f, frame.eased) else frame.artY,
                        mix(.18f, .5f, frame.eased), frame.groundTop, frame.eased, homeGround,
                        Modifier.fillMaxSize().clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                            enabled = frame.progress < .6f, onClick = open,
                        ).clearAndSetSemantics {},
                    )
                    if (expanded) {
                        // 넓은 창의 끝 배치: 왼쪽 그림·CTA와 오른쪽 2×3 카드.
                        Box(Modifier.offset(x = (contentLeft + finalHeroW + 24f).dp)
                            .width((contentW - finalHeroW).dp).height((h + overflow).dp)
                            .graphicsLayer { alpha = frame.eased }.background(homeGround))
                    }
                    if (featureAlpha > 0f) {
                        // 카드 뒤 화면 아래의 산 능선. 무대가 멈춘 뒤 더 스크롤한 만큼 내려 늘 화면 바닥에 붙인다.
                        Box(
                            Modifier.offset(y = (s - HomeScrollRange).coerceAtLeast(0f).dp)
                                .fillMaxWidth().height(h.dp)
                                .graphicsLayer { alpha = featureAlpha }
                                .mountainRidges(mountainColor, HomeRidgeLifts),
                        )
                    }
                    Column(
                        Modifier.offset(x = artLeft.dp, y = (frame.headlineBottom - headlineH).dp)
                            .width(artWidth.dp).padding(horizontal = 20.dp)
                            .onSizeChanged { headlineH = it.height / density.density }
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(1f, 1f)
                                scaleX = frame.headlineScale; scaleY = frame.headlineScale
                            },
                        horizontalAlignment = Alignment.End,
                        // 확대되는 첫 화면에서도 화면에 보이는 간격은 4dp로 유지한다.
                        verticalArrangement = Arrangement.spacedBy(4.dp / frame.headlineScale),
                    ) {
                        Image(
                            painterResource(Res.drawable.home_hero_logo_white),
                            contentDescription = null,
                            modifier = Modifier.size(width = 96.dp, height = 47.dp),
                            colorFilter = HomeHeroLogoColor,
                        )
                        Image(
                            painterResource(Res.drawable.home_hero_brush_lettering),
                            contentDescription = stringResource(Res.string.home_hero_title),
                            modifier = Modifier.size(width = 171.dp, height = 42.dp),
                        )
                    }
                    val buttonLeft = mix((w - buttonW) / 2f,
                        if (expanded) contentLeft + (finalHeroW - buttonW) / 2f else (w - buttonW) / 2f, frame.eased)
                    HomeInkButton(
                        onClick = onStartEvaluation,
                        darkBackground = frame.darkBehindButton,
                        scrollProgress = frame.progress,
                        reduceMotion = reduceMotion,
                        modifier = Modifier.offset(x = buttonLeft.dp, y = frame.buttonTop.dp).width(buttonW.dp)
                            .heightIn(min = (buttonW * 50f / 310f).dp)
                            .onSizeChanged { buttonH = it.height / density.density },
                    )
                    // 카드는 처음부터 구성해 두고 숨긴다. 펼치는 도중 진행률 .6에서 처음 구성하면 그 프레임이 길어져 스크롤이 건너뛴다.
                    FeatureSection(onFeatureClick,
                        columns = HomeFeatureColumns, cardMinHeight = 132.dp,
                        large = medium || expanded,
                        enabled = openFeatures,
                        modifier = Modifier.offset(
                            x = (if (expanded) contentLeft + finalHeroW + 40f else contentLeft).dp,
                            y = featureTop.dp,
                        ).width((if (expanded) contentW - finalHeroW - 40f else contentW).dp)
                            .onSizeChanged { featureH = it.height / density.density }
                            .then(if (openFeatures) Modifier else Modifier.clearAndSetSemantics {})
                            .graphicsLayer { alpha = featureAlpha },
                    )
                    if (frame.arrowAlpha > 0f) {
                        Box(
                            Modifier.offset(x = ((w - 48f) / 2f).dp, y = (h - 82f).dp).size(48.dp)
                                .graphicsLayer { alpha = frame.arrowAlpha }
                                .clickable(role = Role.Button, onClickLabel = stringResource(Res.string.home_scroll_to_features), onClick = openToBottom),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(EolssuIcons.ChevronDown, stringResource(Res.string.home_scroll_to_features),
                                Modifier.offset(y = bounce.value.dp).size(28.dp), tint = homeGround)
                        }
                    }
                }
            }
        }
        // 마이페이지 판은 스크롤 상자 밖에 두어 첫 화면에서는 숨기고, 펼친 뒤 추가 스크롤에도 위 12dp · 내용 오른쪽 끝에 고정한다.
        // 연습 악기 판은 곡 고르기 화면 상단 바로 옮겼다. 왼쪽 위 로고 판은 두지 않는다.
        if (frame.headerAlpha > 0f) {
            Row(
                Modifier.offset(x = contentLeft.dp, y = HomeHeaderTop).width(contentW.dp).graphicsLayer { alpha = frame.headerAlpha },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                MyPagePlate(profileImage, onOpenMyPage)
            }
        }
    }
}

/** 원본 그림의 비율·잘림을 직접 그린다. CSS mask 대신 아래에서 올라오는 흰 그라데이션을 겹친다. */
@Composable
private fun HomeArt(width: Float, left: Float, scale: Float, artY: Float, x: Float, ground: Float, progress: Float, groundColor: Color, modifier: Modifier) {
    val painter = painterResource(Res.drawable.home_illustration)
    val density = LocalDensity.current.density
    Canvas(modifier) {
        val d = density
        drawRect(lerp(HomeNight, groundColor, progress))
        val imageW = width * scale * d
        val imageH = imageW * 1448f / 1086f
        val imageTop = artY * d
        val imageBottom = imageTop + imageH
        clipRect(left = left * d, right = (left + width) * d) {
            translate(left * d + (width * d - imageW) * x, imageTop) {
                with(painter) { draw(androidx.compose.ui.geometry.Size(imageW, imageH)) }
            }
            drawRect(Brush.verticalGradient(
                0f to Color.Transparent, 1f to HomeNight,
                startY = imageBottom - 118f * d, endY = imageBottom,
            ))
            drawRect(Brush.linearGradient(
                .52f to Color.Transparent, .88f to Color(0x9E141824),
                start = Offset(left * d, 0f), end = Offset((left + width) * d, ground * d),
            ), size = androidx.compose.ui.geometry.Size(size.width, ground * d))
        }
        drawRect(Brush.verticalGradient(
            0f to Color.Transparent, 1f to groundColor,
            startY = (ground - 60f) * d, endY = (ground + 30f) * d,
        ))
    }
}

/** 홈 산 능선은 맨 앞 능선은 두고 가운데·맨 뒤 능선만 차례로 더 높인다(뒤 → 앞, 보드 좌표). */
private val HomeRidgeLifts = listOf(36f, 20f, 0f)

/** 상단 판과 화면(안전 영역) 위 가장자리 사이의 틈. */
private val HomeHeaderTop = 12.dp
// 흰색 RGB는 그대로 두고 반투명한 붓 획의 불투명도만 높인다.
private val HomeHeroLogoColor = ColorFilter.colorMatrix(ColorMatrix().apply { this[3, 3] = 2.2f })
private val HomeNight = Color(0xFF1B2233)
