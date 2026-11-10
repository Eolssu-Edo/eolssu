package com.ssafy.eolssu.feature.song.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.network.inSession
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.core.ui.score.ScoreNotation
import com.ssafy.eolssu.data.song.SheetDocument
import com.ssafy.eolssu.data.song.SheetDocumentRepository
import com.ssafy.eolssu.feature.song.edit.component.EditScoreBoard
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sheet_view_edit
import eolssu.shared.generated.resources.sheet_view_practice
import eolssu.shared.generated.resources.sheet_view_review_hint
import eolssu.shared.generated.resources.sheet_view_title
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun SheetViewRoute(
    sheetId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onPractice: () -> Unit,
    documents: SheetDocumentRepository = koinInject(),
    member: SignedInMember = koinInject(),
) {
    val session by member.session.collectAsState()
    val openedFor = remember(sheetId) { member.session.value }
    var attempt by remember(sheetId) { mutableStateOf(0) }
    var loading by remember(sheetId, session, attempt) { mutableStateOf(true) }
    var content by remember(sheetId, session, attempt) { mutableStateOf<SheetViewContent?>(null) }
    LaunchedEffect(sheetId, session, attempt) {
        if (session == openedFor) {
            content = try {
                member.inSession {
                    if (member.session.value == openedFor) documents.get(sheetId)?.viewingContent() else null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
        loading = false
    }

    val title = stringResource(Res.string.sheet_view_title)
    val loaded = content
    if (loaded == null) {
        SheetDocumentNotice(loading, onBack, { attempt++ }, title)
        return
    }
    val draft = loaded.draft
    EolssuScaffold(
        topBar = { EolssuTopBar(title, onBack = onBack, subtitle = draft.title) },
        bottomBar = {
            if (loaded.practiceReady) EolssuButton(stringResource(Res.string.sheet_view_practice), onPractice)
            else EolssuButton(stringResource(Res.string.sheet_view_edit), onEdit)
        },
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (!loaded.practiceReady) {
                Text(
                    stringResource(Res.string.sheet_view_review_hint),
                    Modifier.padding(bottom = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = EolssuTheme.colors.textMuted,
                )
            }
            EditScoreBoard(
                song = remember(draft) { draft.toSong() },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                annotations = remember(draft) { draft.annotations() },
                notation = ScoreNotation(showHangul = true),
            )
        }
    }
}

internal data class SheetViewContent(val draft: SheetDraft, val practiceReady: Boolean)

internal fun SheetDocument.viewingContent(): SheetViewContent? {
    if (conversionStatus != null && conversionStatus != "DONE") return null
    val view = score?.editingDraft(title, bpm, jangdan = null)
        ?: draft?.let { it.score.editingDraft(it.title, it.bpm, jangdan = null) }
        ?: return null
    return SheetViewContent(view, practiceReady = reviewStatus == "EVALUABLE" && score != null)
}
