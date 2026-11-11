package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.model.ProfileImage
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** 절쑤 그림 12종의 그림(S15P31E102-738). 얼굴과 갓이 원 가운데 오도록 자른 정사각형이다. */
val ProfileImage.drawable: DrawableResource
    get() = when (this) {
        ProfileImage.Hello -> Res.drawable.profile_tiger_hello
        ProfileImage.Danso -> Res.drawable.profile_tiger_danso
        ProfileImage.Study -> Res.drawable.profile_tiger_study
        ProfileImage.Look -> Res.drawable.profile_tiger_look
        ProfileImage.Great -> Res.drawable.profile_tiger_great
        ProfileImage.Jangdan -> Res.drawable.profile_tiger_jangdan
        ProfileImage.Again -> Res.drawable.profile_tiger_again
        ProfileImage.Surprise -> Res.drawable.profile_tiger_surprise
        ProfileImage.Proud -> Res.drawable.profile_tiger_proud
        ProfileImage.Okay -> Res.drawable.profile_tiger_okay
        ProfileImage.Rest -> Res.drawable.profile_tiger_rest
        ProfileImage.Done -> Res.drawable.profile_tiger_done
    }

/**
 * 흰 원 안의 절쑤 그림(마이페이지 얼굴 원). 부모가 높이·너비를 줄여도 타원이 되지 않도록 [size] 정사각형을 강제한다(S15P31E102-768).
 */
@Composable
fun ProfileImageCircle(image: ProfileImage, size: Dp) {
    val colors = EolssuTheme.colors
    Box(
        Modifier
            .requiredSize(size)
            .clip(CircleShape)
            .background(colors.surface),
    ) {
        Image(painterResource(image.drawable), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
