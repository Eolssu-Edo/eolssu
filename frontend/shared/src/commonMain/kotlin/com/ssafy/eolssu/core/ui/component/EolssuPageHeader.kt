package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_back
import org.jetbrains.compose.resources.stringResource

/**
 * 페이지형 상단 바(`.page-head`, SONG-01 · SONG-02 · PRACTICE-01): 왼쪽 원형 뒤로 가기 · 왼쪽 정렬 15sp 제목 · 오른쪽 버튼 자리 · 아래 구분선.
 * 가운데 제목형 [EolssuTopBar]와 달리 보조 문구가 없고, 큰 제목은 본문의 [PageHeading]이 맡는다.
 * 원형 버튼은 보이는 크기 36dp, 누르는 영역 48dp라 버튼 바깥 여백(6dp)만큼 줄 양옆 여백을 줄여 원이 화면 끝에서 20dp에 오게 한다.
 * 상태 표시줄 영역은 감싸는 쪽(`EolssuScaffold` 등)이 비켜 간다.
 */
@Composable
fun EolssuPageTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    maxWidth: Dp = CompactContentMaxWidth,
    right: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Row(
                Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .heightIn(min = 62.dp)
                    .padding(horizontal = PageButtonInset),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    EolssuPageIconButton(EolssuIcons.Back, stringResource(Res.string.common_back), onBack)
                    Spacer(Modifier.width(12.dp - PageButtonInset))
                } else {
                    Spacer(Modifier.width(20.dp - PageButtonInset))
                }
                Text(
                    title,
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    color = EolssuTheme.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = right)
            }
        }
        HorizontalDivider(color = EolssuTheme.colors.surfaceStrong)
    }
}

/** 누르는 영역(48dp)과 보이는 원(36dp)의 차이 절반. */
private val PageButtonInset: Dp = 6.dp

/**
 * [EolssuPageTopBar]의 원형 버튼(`.back-btn`): 흰 바탕 · 옅은 테두리 36dp 원, 누르는 영역 48dp.
 * [enabled]가 false면 바탕 회색 · 흐린 아이콘(연주 중 잠긴 설정)이다.
 */
@Composable
fun EolssuPageIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = EolssuTheme.colors
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(if (enabled) colors.surface else colors.background, CircleShape)
                .border(1.dp, colors.lineSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription, Modifier.size(18.dp), tint = if (enabled) colors.text else colors.textDisabled)
        }
    }
}

/**
 * 본문 맨 위 페이지 머리(SONG-01 · SONG-02 · PRACTICE-01): 영문 작은 제목(`.eyebrow`) · Song Myung 25sp 큰 제목(`.page-title`) · 설명(`.page-intro`).
 * 줄바꿈이 필요한 제목은 문구에 `\n`을 넣는다. 아래 여백은 부르는 쪽이 정한다.
 * [eyebrowGap]은 작은 제목과 큰 제목 사이(SONG-01 10dp, PRACTICE-01 4dp)다.
 */
@Composable
fun PageHeading(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    intro: String? = null,
    eyebrowGap: Dp = 10.dp,
) {
    val colors = EolssuTheme.colors
    Column(modifier) {
        if (eyebrow != null) {
            Text(
                eyebrow,
                modifier = Modifier.padding(bottom = eyebrowGap),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
                color = colors.textMuted,
            )
        }
        Text(
            title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 25.sp,
                lineHeight = 37.5.sp,
                letterSpacing = (-0.5).sp,
                fontWeight = FontWeight.Normal,
            ),
            color = colors.text,
        )
        if (intro != null) {
            Text(
                intro,
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 22.sp),
                color = colors.textSub,
            )
        }
    }
}
