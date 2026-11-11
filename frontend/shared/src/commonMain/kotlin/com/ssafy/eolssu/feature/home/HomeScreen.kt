package com.ssafy.eolssu.feature.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.ssafy.eolssu.core.model.ProfileImage
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

/**
 * 2열 카드 격자의 순서(왼쪽 · 오른쪽 한 줄씩). 왼쪽 열은 학습(기초 배우기 · 운지법 보기 · 정간보 읽기),
 * 오른쪽 열은 연습·관리(연습하기 · 연습 기록 보기 · 악보 추가·편집하기)다. 휴대폰·태블릿·넓은 창 모두 2열(2×3)로 놓는다.
 */
internal val HomeFeatureGrid = listOf(
    HomeFeature.Learn, HomeFeature.SongSelect,
    HomeFeature.Fingering, HomeFeature.Records,
    HomeFeature.Reading, HomeFeature.EditSheets,
)

/** 카드 격자의 열 수. 왼쪽 학습 · 오른쪽 연습·관리 구분을 지키려고 창 크기와 관계없이 2열이다. */
internal const val HomeFeatureColumns = 2

/**
 * HOME-01. 전체 그림에서 스크롤하면 상단 판과 학습·연습 카드가 나타난다. 오른쪽 위 아이콘으로 마이페이지에 간다.
 * 연습 악기는 홈이 아닌 평가할 곡 고르기 · 연습곡 고르기의 상단 바에서 바꾼다.
 */
@Composable
fun HomeScreen(
    widthClass: WindowWidthClass,
    profileImage: ProfileImage,
    snackbarHostState: SnackbarHostState,
    onOpenMyPage: () -> Unit,
    onStartEvaluation: () -> Unit,
    onFeatureClick: (HomeFeature) -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = EolssuTheme.colors.groupedBackground,
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        HomeScrollLayout(widthClass, profileImage, onOpenMyPage, onStartEvaluation, onFeatureClick, modifier)
    }
}
