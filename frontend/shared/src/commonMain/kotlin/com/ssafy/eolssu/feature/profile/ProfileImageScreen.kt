package com.ssafy.eolssu.feature.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.ssafy.eolssu.core.ui.component.drawable
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.model.ProfileImage
import com.ssafy.eolssu.feature.profile.component.MyPageCard
import com.ssafy.eolssu.core.ui.component.ProfileImageCircle
import com.ssafy.eolssu.feature.profile.component.ProfileScaffold
import com.ssafy.eolssu.feature.profile.component.label
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.my_profile_change
import eolssu.shared.generated.resources.my_save
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** 프로필 그림 고르기 연결. 바꿨으면 [onSaved], 그대로면 [onBack]으로 마이페이지에 돌아간다. */
@Composable
fun ProfileImageRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ProfileImageViewModel = koinViewModel(),
) {
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileImageScreen(
        state = state,
        widthClass = currentWindowLayout().widthClass,
        onBack = onBack,
        onSelect = viewModel::select,
        onSave = {
            scope.launch {
                when (viewModel.save()) {
                    ProfileImageSave.Saved -> onSaved()
                    ProfileImageSave.Unchanged -> onBack()
                    ProfileImageSave.Failed -> Unit
                }
            }
        },
    )
}

/**
 * 프로필 변경(S15P31E102-738). 마이페이지와 같은 회색 바탕 위 흰 카드에 절쑤 그림 12종을 이름 없이 원으로 늘어놓고,
 * 고른 그림은 먹초록 테두리로 표시한다. 휴대폰은 한 줄에 3개, 태블릿은 4개다.
 */
@Composable
fun ProfileImageScreen(
    state: ProfileImageUiState,
    widthClass: WindowWidthClass,
    onBack: () -> Unit,
    onSelect: (ProfileImage) -> Unit,
    onSave: () -> Unit,
) {
    val colors = EolssuTheme.colors
    val columns = if (widthClass == WindowWidthClass.Compact) 3 else 4
    ProfileScaffold(
        widthClass,
        stringResource(Res.string.my_profile_change),
        onBack,
        bottomBar = { EolssuButton(stringResource(Res.string.my_save), onClick = onSave) },
        gap = 12.dp,
        topPadding = 8.dp,
        containerColor = colors.groupedBackground,
    ) {
        MyPageCard {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProfileImage.entries.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { image ->
                            ProfileImageOption(image, image == state.selected, { onSelect(image) }, Modifier.weight(1f))
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        state.error?.let {
            Text(
                stringResource(it.message),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = colors.text,
            )
        }
    }
}

/**
 * 그림 하나: 원으로 자르지 않은 그림만 보이고 이름은 화면 낭독으로만 읽는다. 누르는 영역은 칸 전체다.
 * 고른 그림은 누를 때와 같은 회색 칸(모서리 16dp)으로 표시한다. 동그란 얼굴 원은 마이페이지에만 둔다(S15P31E102-768).
 */
@Composable
private fun ProfileImageOption(image: ProfileImage, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = EolssuTheme.colors
    val label = stringResource(image.label)
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.groupedBackground else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 칸이 84dp보다 좁은 화면(320dp 등)에서는 옆 칸과 겹치지 않게 칸 너비에 맞춰 줄인다(S15P31E102-972).
        Image(
            painterResource(image.drawable),
            contentDescription = null,
            modifier = Modifier.widthIn(max = 84.dp).fillMaxWidth().aspectRatio(1f),
            contentScale = ContentScale.Fit,
        )
    }
}
