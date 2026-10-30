package com.ssafy.eolssu.feature.auth

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.feature.auth.component.AuthBody
import com.ssafy.eolssu.feature.auth.component.AuthBodyTopWithBar
import com.ssafy.eolssu.feature.auth.component.AuthScaffold
import com.ssafy.eolssu.feature.auth.component.AuthTopBar
import com.ssafy.eolssu.feature.auth.component.authFrameOf
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_confirm
import eolssu.shared.generated.resources.member_api_open_terms
import eolssu.shared.generated.resources.member_api_terms_loading
import eolssu.shared.generated.resources.member_api_terms_version
import eolssu.shared.generated.resources.member_api_unavailable
import org.jetbrains.compose.resources.stringResource

@Composable
fun TermsScreen(state: TermsUiState, onClose: () -> Unit, onOpen: () -> Unit, widthClass: WindowWidthClass) {
    AuthScaffold(authFrameOf(widthClass, split = false),
        topBar = { AuthTopBar(state.term?.title.orEmpty(), onBack = onClose) },
        bottomBar = { EolssuButton(stringResource(Res.string.common_confirm), onClose) },
    ) {
        AuthBody(AuthBodyTopWithBar) {
            val term = state.term
            if (state.loading) Text(stringResource(Res.string.member_api_terms_loading))
            else if (term == null) Text(stringResource(Res.string.member_api_unavailable))
            else {
                Text(term.title)
                Text(stringResource(Res.string.member_api_terms_version, term.version))
                EolssuButton(stringResource(Res.string.member_api_open_terms), onOpen)
            }
            if (state.openFailed) Text(stringResource(Res.string.member_api_unavailable))
        }
    }
}
