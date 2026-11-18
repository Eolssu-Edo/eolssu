package com.ssafy.eolssu.feature.home.component

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.runtime.remember
import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.indication
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.ProfileImage
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.drawable
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.home_my_page
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 홈 마이페이지 입구: 마이페이지 얼굴 원과 같은 흰 원 안의 내 프로필 그림([image]) 40dp, 옅은 그림자(S15P31E102-768).
 * 누르면 [onClick]으로 마이페이지(MY-01)에 간다. 누르는 영역은 Compose의 최소 터치 영역(48dp)으로 원 바깥까지 넓어진다.
 */
@Composable
internal fun MyPagePlate(image: ProfileImage, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    // 누르는 영역(48dp)과 그리는 원(40dp)을 나눠, 누르는 최소 크기 때문에 원이 타원으로 늘어나지 않게 한다(S15P31E102-768).
    // 48dp 전체가 눌리지만 눌림 효과는 40dp 원 안에만 그린다(S15P31E102-969).
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = UserActionLog.click(QaTarget.HOME_HEADER_01, onClick)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(image.drawable),
            contentDescription = stringResource(Res.string.home_my_page),
            modifier = Modifier
                .requiredSize(40.dp)
                .shadow(1.dp, CircleShape)
                .clip(CircleShape)
                .background(colors.surface)
                .indication(interaction, ripple()),
            contentScale = ContentScale.Crop,
        )
    }
}
