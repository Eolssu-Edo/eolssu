package com.ssafy.eolssu.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.adaptive.currentWindowLayout
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.data.user.PublishedLegalDocument
import com.ssafy.eolssu.data.user.TermsBlock
import com.ssafy.eolssu.feature.profile.component.ProfileScaffold
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.legal_deletion
import eolssu.shared.generated.resources.legal_privacy
import eolssu.shared.generated.resources.legal_service
import eolssu.shared.generated.resources.member_api_retry
import eolssu.shared.generated.resources.member_api_terms_loading
import eolssu.shared.generated.resources.member_api_terms_unavailable
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** 마이페이지 하단 문서의 화면 제목(하단 줄 이름과 같다). */
val PublishedLegalDocument.title: StringResource
    get() = when (this) {
        PublishedLegalDocument.Service -> Res.string.legal_service
        PublishedLegalDocument.Privacy -> Res.string.legal_privacy
        PublishedLegalDocument.Deletion -> Res.string.legal_deletion
    }

/** 법적 문서 화면 연결. 들어오면 문서를 받아 바로 보여 준다. */
@Composable
fun LegalDocumentRoute(
    document: PublishedLegalDocument,
    onBack: () -> Unit,
    viewModel: LegalDocumentViewModel = koinViewModel(key = "legal-${document.name}") { parametersOf(document) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) { viewModel.load() }
    LegalDocumentScreen(
        title = stringResource(document.title),
        state = state,
        widthClass = currentWindowLayout().widthClass,
        onBack = onBack,
        onRetry = { scope.launch { viewModel.load() } },
    )
}

/**
 * 법적 문서(서비스 이용약관·개인정보 처리방침·계정·데이터 삭제 안내). '자료 및 데이터 출처'와 같은 마이페이지 화면 틀([ProfileScaffold])
 * 안에서 제목·문단·목록을 펼친다. 받지 못하면 안내와 다시 시도 버튼을 둔다.
 */
@Composable
fun LegalDocumentScreen(
    title: String,
    state: LegalDocumentUiState,
    widthClass: WindowWidthClass,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = EolssuTheme.colors
    ProfileScaffold(widthClass, title, onBack, snackbarHostState, gap = 12.dp, topPadding = 8.dp) {
        val body = state.body
        val notice = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp)
        when {
            state.loading -> Text(stringResource(Res.string.member_api_terms_loading), style = notice, color = colors.textMuted)
            body == null -> {
                Text(stringResource(Res.string.member_api_terms_unavailable), style = notice, color = colors.textMuted)
                EolssuButton(stringResource(Res.string.member_api_retry), onRetry)
            }
            else -> body.forEach { LegalBlockText(it) }
        }
    }
}

/** 문서 한 덩어리: 제목은 굵게(화면 낭독 제목), 문단·목록은 차분한 본문 색. 약관 상세(AUTH-02)와 같은 글자 크기다. */
@Composable
private fun LegalBlockText(block: TermsBlock) {
    val colors = EolssuTheme.colors
    val bodyStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 23.8.sp)
    when (block) {
        is TermsBlock.Heading -> Text(
            block.text,
            modifier = Modifier.padding(top = if (block.level <= 1) 0.dp else 8.dp).semantics { heading() },
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = if (block.level <= 1) 18.sp else 15.sp,
                lineHeight = if (block.level <= 1) 25.2.sp else 21.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = colors.text,
        )
        is TermsBlock.Paragraph -> Text(block.text, style = bodyStyle, color = colors.textSub)
        is TermsBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("•", style = bodyStyle, color = colors.textSub)
            Column { Text(block.text, style = bodyStyle, color = colors.textSub) }
        }
    }
}
