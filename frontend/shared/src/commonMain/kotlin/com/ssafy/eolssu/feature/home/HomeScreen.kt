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
import eolssu.shared.generated.resources.home_feature_fingering
import eolssu.shared.generated.resources.home_feature_learn
import eolssu.shared.generated.resources.home_feature_reading
import eolssu.shared.generated.resources.home_feature_song_select
import org.jetbrains.compose.resources.StringResource

/** 홈의 연습·학습 카드. 연결된 화면이 없는 카드는 '준비 중' 안내를 띄운다([HomeRoute]). */
enum class HomeFeature(val label: StringResource, val icon: ImageVector) {
    SongSelect(Res.string.home_feature_song_select, EolssuIcons.Flute),
    Fingering(Res.string.home_feature_fingering, EolssuIcons.Hand),
    Reading(Res.string.home_feature_reading, EolssuIcons.Grid),
    Learn(Res.string.home_feature_learn, EolssuIcons.Book),
}

/**
 * HOME-01. 전체 그림에서 스크롤하면 연습·학습 카드가 나타난다.
 * 현재 악기는 보여 주기만 하고 오른쪽 위 아이콘으로 마이페이지에 간다.
 */
@Composable
fun HomeScreen(
    instrument: Instrument?,
    widthClass: WindowWidthClass,
    snackbarHostState: SnackbarHostState,
    onOpenMyPage: () -> Unit,
    onStartEvaluation: () -> Unit,
    onFeatureClick: (HomeFeature) -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = EolssuTheme.colors.background,
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        HomeScrollLayout(instrument, widthClass, onOpenMyPage, onStartEvaluation, onFeatureClick, modifier)
    }
}
