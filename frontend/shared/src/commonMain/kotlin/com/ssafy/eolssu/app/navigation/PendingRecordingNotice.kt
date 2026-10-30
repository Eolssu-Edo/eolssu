package com.ssafy.eolssu.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ssafy.eolssu.core.audio.RecordingFiles
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.data.practice.PendingRecording
import com.ssafy.eolssu.data.practice.RecordingUploads
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.pending_recording_body
import eolssu.shared.generated.resources.pending_recording_later
import eolssu.shared.generated.resources.pending_recording_send
import eolssu.shared.generated.resources.pending_recording_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin

/**
 * 앱을 켰을 때 보내지 못한 녹음이 기기에 남아 있으면 한 번 알린다(PRA-03 '녹음 다시 보내기'). '녹음 보내기'는 분석 대기 화면에서 그 녹음을 보낸다.
 * 현재 회원의 녹음만 묻는다. 로그인 화면에서 돌아오면 [routeId] 변경으로 다시 읽는다.
 * 녹음을 보관하지 않는 플랫폼(현재 iOS·Web)은 아무것도 하지 않는다.
 */
@Composable
internal fun PendingRecordingNotice(routeId: String?, onSend: (PendingRecording) -> Unit) {
    val koin = getKoin()
    val memberId = koin.get<SignedInMember>().id
    var asked by rememberSaveable(memberId) { mutableStateOf(false) }
    val pending = remember(memberId, routeId) {
        koin.getOrNull<RecordingFiles>()?.let { koin.get<RecordingUploads>() }?.let { uploads ->
            uploads.cleanUp()
            uploads.pending()
        }
    }
    if (asked || pending == null) return
    EolssuDialog(
        title = stringResource(Res.string.pending_recording_title),
        message = stringResource(Res.string.pending_recording_body),
    ) {
        EolssuButton(stringResource(Res.string.pending_recording_send), {
            asked = true
            onSend(pending)
        })
        EolssuButton(stringResource(Res.string.pending_recording_later), { asked = true }, style = EolssuButtonStyle.Secondary)
    }
}
