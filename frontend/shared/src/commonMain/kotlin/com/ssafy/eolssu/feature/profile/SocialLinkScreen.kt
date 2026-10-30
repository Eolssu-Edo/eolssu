package com.ssafy.eolssu.feature.profile

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.feature.profile.component.ProfileScaffold
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun SocialLinkScreen(provider: SocialProvider, onBack: () -> Unit,
                     widthClass: WindowWidthClass = currentWindowLayout().widthClass) {
    ProfileScaffold(widthClass, stringResource(Res.string.my_link_title, stringResource(provider.displayName)), onBack,
        bottomBar = { EolssuButton(stringResource(Res.string.common_confirm), onBack) },
    ) { Text(stringResource(Res.string.member_api_social_unavailable)) }
}
