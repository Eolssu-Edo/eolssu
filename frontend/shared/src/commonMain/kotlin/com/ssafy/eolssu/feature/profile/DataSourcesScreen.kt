package com.ssafy.eolssu.feature.profile

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.browser.rememberInAppLinkOpener
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.profile.component.ProfileScaffold
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sources_intro
import eolssu.shared.generated.resources.sources_link_failed
import eolssu.shared.generated.resources.sources_open_link
import eolssu.shared.generated.resources.sources_title
import eolssu.shared.generated.resources.sources_usage
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** MY-01 푸터에서 여는 '자료 및 데이터 출처'. 외부 링크는 누를 때만 열고, 열지 못하면 안내를 띄운다. */
@Composable
fun DataSourcesRoute(onBack: () -> Unit) {
    // 브라우저에서 뒤로 가면 앱으로 돌아오도록 앱 위에 겹쳐 연다(S15P31E102-768).
    val openLink = rememberInAppLinkOpener()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    DataSourcesScreen(
        sources = DataSources,
        widthClass = currentWindowLayout().widthClass,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onOpenLink = { url ->
            if (runCatching { openLink(url) }.isFailure) {
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(getString(Res.string.sources_link_failed))
                }
            }
        },
    )
}

/** 출처 목록. 항목이 늘어도 마이페이지와 같은 세로 스크롤 틀([ProfileScaffold]) 안에서 모두 본다. */
@Composable
fun DataSourcesScreen(
    sources: List<DataSource>,
    widthClass: WindowWidthClass,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    ProfileScaffold(
        widthClass, stringResource(Res.string.sources_title), onBack, snackbarHostState, gap = 12.dp, topPadding = 8.dp,
        containerColor = EolssuTheme.colors.groupedBackground,
    ) {
        Text(
            stringResource(Res.string.sources_intro),
            Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
            color = EolssuTheme.colors.textMuted,
        )
        sources.forEach { DataSourceCard(it, onOpenLink) }
    }
}

/**
 * 출처 한 칸(마이페이지 카드 모양, S15P31E102-768): 회색 바탕 위 테두리 없는 흰 카드(모서리 20dp).
 * 위에 자료 종류 연두 꼬리표, 자료명, 쓰는 곳, 이용 조건·가공(회색), 아래에 회색 채움 링크 칸들을 둔다.
 */
@Composable
private fun DataSourceCard(source: DataSource, onOpenLink: (String) -> Unit) {
    val colors = EolssuTheme.colors
    val small = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(source.kind),
            Modifier.background(colors.accentTint, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = colors.accentText,
        )
        Text(
            stringResource(source.name),
            Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
            color = colors.text,
        )
        Text(stringResource(Res.string.sources_usage, stringResource(source.usage)), style = small, color = colors.text)
        source.note?.let { Text(stringResource(it), style = small.copy(fontSize = 12.sp, lineHeight = 18.sp), color = colors.textMuted) }
        if (source.links.isNotEmpty()) {
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                source.links.forEach { link -> SourceLinkRow(stringResource(link.label), link.url, onOpenLink) }
            }
        }
    }
}

/** 링크 한 칸(회색 채움 · 모서리 12dp). 누르는 영역은 52dp 이상이고, 화면 읽기에는 '원본 페이지 열기'처럼 동작을 읽힌다. */
@Composable
private fun SourceLinkRow(label: String, url: String, onOpenLink: (String) -> Unit) {
    val colors = EolssuTheme.colors
    val description = stringResource(Res.string.sources_open_link, label)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.groupedBackground)
            .clickable(role = Role.Button, onClick = UserActionLog.click(QaTarget.DATA_SOURCES_SCREEN_01, { onOpenLink(url) }))
            .semantics { contentDescription = description }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = colors.accentText)
            Text(
                url,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.textMuted)
    }
}
