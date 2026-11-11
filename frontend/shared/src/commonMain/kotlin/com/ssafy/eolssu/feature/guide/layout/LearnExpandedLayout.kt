package com.ssafy.eolssu.feature.guide.layout

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.model.fingering.FingeringNote
import com.ssafy.eolssu.core.ui.adaptive.WindowBreakpoints
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.feature.guide.component.GuideTabletBottomContent
import com.ssafy.eolssu.feature.guide.component.GuideTabletSidePadding
import com.ssafy.eolssu.feature.guide.learn.LearnListActions
import com.ssafy.eolssu.feature.guide.learn.LearnListContent
import com.ssafy.eolssu.feature.guide.learn.LearnTitleFontSize
import com.ssafy.eolssu.feature.guide.learn.LearnStage
import com.ssafy.eolssu.feature.guide.learn.LearnStageActions
import com.ssafy.eolssu.feature.guide.learn.LearnStageBottomBar
import com.ssafy.eolssu.feature.guide.learn.StepPractice
import com.ssafy.eolssu.feature.guide.learn.LearnStageColumns
import com.ssafy.eolssu.feature.guide.learn.LearnStageContent
import com.ssafy.eolssu.feature.guide.learn.rememberLearnTour
import com.ssafy.eolssu.feature.guide.learn.LearnUiState
import com.ssafy.eolssu.feature.guide.learn.learnStageSubtitle
import com.ssafy.eolssu.feature.guide.learn.learnStageTitle
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learn_title
import org.jetbrains.compose.resources.stringResource

/** 1280dp 화면 기준 왼쪽 칸 너비(`.tl-left`, 460px)의 비율. */
private const val ListPaneRatio = 460f / 1280f

/** 오른쪽 칸 본문이 이 너비 이상이면 태블릿처럼 여러 칸으로 놓는다. 좁은 창에서는 휴대폰 배치로 쌓는다. */
private val DetailWideMinWidth = 560.dp

/**
 * 태블릿 가로·넓은 웹 창의 기초부터 배우기(와이어프레임 `Tab-Learn-Land`): 왼쪽 단계 목록 + 오른쪽 단계 상세를 한 화면에 둔다.
 * 왼쪽에서 단계를 고르면 오른쪽만 바뀌고, 두 칸은 따로 스크롤한다. 오른쪽 상단 바의 뒤로 가기는 목록이 옆에 있어 비워 둔다.
 * 1280dp 창에서 바깥 여백 80dp · 왼쪽 칸 460dp가 되고, 더 넓은 창에서는 그 너비로 가운데에 둔다.
 */
@Composable
internal fun LearnExpandedLayout(
    state: LearnUiState,
    stage: LearnStage?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    listActions: LearnListActions,
    stageActions: LearnStageActions,
    noteAudioContent: @Composable (FingeringNote) -> Unit,
    practice: StepPractice? = null,
) {
    val colors = EolssuTheme.colors
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            val content = WindowBreakpoints.ContentMaxWidth
            val total = minOf(maxWidth, content + 160.dp)
            val outer = ((total - content) / 2).coerceIn(GuideTabletSidePadding, 80.dp)
            val listWidth = (total * ListPaneRatio).coerceIn(320.dp, 460.dp)
            Row(Modifier.width(total).fillMaxHeight()) {
                Column(Modifier.width(listWidth).fillMaxHeight()) {
                    // EolssuTopBar 안쪽 여백(20dp)을 빼고 와이어프레임 `.tl-left .topbar`(왼쪽 80 · 오른쪽 24)에 맞춘다.
                    EolssuTopBar(
                        stringResource(Res.string.learn_title),
                        onBack = onBack,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = outer - 20.dp, end = 4.dp),
                        maxWidth = Dp.Infinity,
                        titleFontSize = LearnTitleFontSize,
                    )
                    LearnListContent(
                        state,
                        listActions,
                        Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(start = outer, end = 24.dp, bottom = 24.dp),
                        currentStage = stage?.number ?: state.cardStage,
                    )
                }
                VerticalDivider(color = colors.line)
                // 단계 목록을 받기 전·받지 못했으면 오른쪽 칸을 비운다(왼쪽 칸이 상태와 '다시 시도'를 보인다).
                if (stage == null) return@Row
                val tour = rememberLearnTour(stage.number, state.instrument, { stageActions.stepsDone(stage.number) }) { stageActions.onStepsDone(stage.number, it) }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    EolssuTopBar(
                        learnStageTitle(stage),
                        onBack = null,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 20.dp, end = outer - 20.dp),
                        subtitle = learnStageSubtitle(state, stage),
                        maxWidth = Dp.Infinity,
                    )
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val wide = maxWidth - GuideTabletSidePadding - outer >= DetailWideMinWidth
                        // 단계를 바꾸면 본문을 새로 그려 맨 위에서 시작한다.
                        key(stage.number) {
                            LearnStageContent(
                                state,
                                stage,
                                stageActions,
                                noteAudioContent,
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = GuideTabletSidePadding, end = outer, bottom = 24.dp),
                                columns = if (wide) LearnStageColumns.Landscape else LearnStageColumns.Compact,
                                tour = tour,
                                practice = practice,
                            )
                        }
                    }
                    HorizontalDivider(color = colors.lineSoft)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(start = GuideTabletSidePadding, end = outer, top = 14.dp, bottom = 24.dp),
                    ) {
                        GuideTabletBottomContent { LearnStageBottomBar(state, stage, stageActions, tour, practice) }
                    }
                }
            }
        }
    }
}
