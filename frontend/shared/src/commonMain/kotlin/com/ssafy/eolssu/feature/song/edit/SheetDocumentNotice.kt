package com.ssafy.eolssu.feature.song.edit

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.component.*
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** 실제 문서를 읽기 전에는 예시 악보와 편집·저장 버튼을 노출하지 않는다. */
@Composable
internal fun SheetDocumentNotice(loading: Boolean, onBack: () -> Unit, onRetry: () -> Unit) {
    EolssuScaffold(topBar = { EolssuTopBar(stringResource(Res.string.sheet_document_title), onBack = onBack) }) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
            if (loading) CircularProgressIndicator()
            Text(stringResource(if (loading) Res.string.sheet_document_loading else Res.string.sheet_document_failed))
            if (!loading) EolssuButton(stringResource(Res.string.song_catalog_retry), onRetry)
        }
    }
}
