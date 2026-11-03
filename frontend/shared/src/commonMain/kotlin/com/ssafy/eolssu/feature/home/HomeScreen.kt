package com.ssafy.eolssu.feature.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.home.layout.HomeScrollLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.home_feature_edit_sheets
import eolssu.shared.generated.resources.home_feature_fingering
import eolssu.shared.generated.resources.home_feature_learn
import eolssu.shared.generated.resources.home_feature_reading
import eolssu.shared.generated.resources.home_feature_records
import eolssu.shared.generated.resources.home_feature_song_select
import org.jetbrains.compose.resources.StringResource

/** 홈의 학습·연습 카드. 연결된 화면이 없는 카드는 '준비 중' 안내를 띄운다([HomeRoute]). */
enum class HomeFeature(val label: StringResource, val icon: ImageVector) {
    SongSelect(Res.string.home_feature_song_select, EolssuIcons.Flute),
    Records(Res.string.home_feature_records, EolssuIcons.Save),
    Reading(Res.string.home_feature_reading, EolssuIcons.Grid),
    Fingering(Res.string.home_feature_fingering, EolssuIcons.Hand),
    Learn(Res.string.home_feature_learn, EolssuIcons.Book),
    EditSheets(Res.string.home_feature_edit_sheets, EolssuIcons.Text),
}

/** 카드 격자의 순서. 휴대폰·넓은 창은 2열(2×3), 태블릿 세로는 3열(3×2)로 나뉜다. */
internal val HomeFeatureGrid = listOf(
    HomeFeature.Learn, HomeFeature.SongSelect,
    HomeFeature.Fingering, HomeFeature.Reading,
    HomeFeature.EditSheets, HomeFeature.Records,
)

/** 상단 '현재 악기' 드롭다운의 순서. 악기를 아직 확인하지 못했을 때의 기본 표시도 첫 항목(단소)이다. */
internal val HomeInstruments = listOf(Instrument.Danso, Instrument.Sogeum, Instrument.DaegeumSanjo, Instrument.DaegeumJeongak)

/**
 * HOME-01. 전체 그림에서 스크롤하면 상단 판과 학습·연습 카드가 나타난다.
 * 현재 악기 판은 드롭다운으로 연습 악기를 바꾸고, 오른쪽 위 아이콘으로 마이페이지에 간다.
 */
@Composable
fun HomeScreen(
    instrument: Instrument?,
    instrumentBusy: Boolean,
    widthClass: WindowWidthClass,
    snackbarHostState: SnackbarHostState,
    onSelectInstrument: (Instrument) -> Unit,
    onOpenMyPage: () -> Unit,
    onStartEvaluation: () -> Unit,
    onFeatureClick: (HomeFeature) -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = EolssuTheme.colors.background,
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        HomeScrollLayout(instrument, instrumentBusy, widthClass, onSelectInstrument, onOpenMyPage, onStartEvaluation, onFeatureClick, modifier)
    }
}
