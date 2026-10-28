package com.ssafy.eolssu.feature.practice.component

import androidx.compose.runtime.Composable
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.practice.PracticePopup
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_popup_abort_body
import eolssu.shared.generated.resources.practice_popup_abort_title
import eolssu.shared.generated.resources.practice_popup_continue
import eolssu.shared.generated.resources.practice_popup_end_body
import eolssu.shared.generated.resources.practice_popup_end_title
import eolssu.shared.generated.resources.practice_popup_evaluate
import eolssu.shared.generated.resources.practice_popup_exit_confirm
import eolssu.shared.generated.resources.practice_popup_exit_title
import eolssu.shared.generated.resources.practice_popup_go_home
import eolssu.shared.generated.resources.practice_popup_interrupted_body
import eolssu.shared.generated.resources.practice_popup_interrupted_title
import eolssu.shared.generated.resources.practice_popup_keep_playing
import eolssu.shared.generated.resources.practice_popup_later
import eolssu.shared.generated.resources.practice_popup_permission_body
import eolssu.shared.generated.resources.practice_popup_permission_title
import eolssu.shared.generated.resources.practice_popup_record_failed_body
import eolssu.shared.generated.resources.practice_popup_record_failed_title
import eolssu.shared.generated.resources.practice_popup_restart_confirm
import eolssu.shared.generated.resources.practice_popup_restart_title
import eolssu.shared.generated.resources.practice_popup_start_again
import eolssu.shared.generated.resources.practice_popup_to_ready
import eolssu.shared.generated.resources.practice_popup_too_large_body
import eolssu.shared.generated.resources.practice_popup_too_large_title
import eolssu.shared.generated.resources.practice_restart
import org.jetbrains.compose.resources.stringResource

/** 확인 창에서 부르는 동작. */
class PracticeDialogActions(
    val onGrantMic: () -> Unit,
    val onDenyMic: () -> Unit,
    val onClose: () -> Unit,
    val onGoHome: () -> Unit,
    val onResume: () -> Unit,
    val onEvaluate: () -> Unit,
    val onRestartNow: () -> Unit,
    val onExit: () -> Unit,
    val onToReady: () -> Unit,
)

@Composable
fun PracticeDialog(popup: PracticePopup, actions: PracticeDialogActions) {
    when (popup) {
        PracticePopup.Permission -> EolssuDialog(
            title = stringResource(Res.string.practice_popup_permission_title),
            message = stringResource(Res.string.practice_popup_permission_body),
            icon = EolssuIcons.Mic,
        ) {
            EolssuButton(stringResource(Res.string.practice_popup_continue), actions.onGrantMic)
            EolssuButton(stringResource(Res.string.practice_popup_later), actions.onDenyMic, style = EolssuButtonStyle.Text)
        }
        PracticePopup.Abort -> EolssuDialog(
            title = stringResource(Res.string.practice_popup_abort_title),
            message = stringResource(Res.string.practice_popup_abort_body),
        ) {
            EolssuButton(stringResource(Res.string.practice_popup_go_home), actions.onGoHome)
            EolssuButton(stringResource(Res.string.practice_popup_keep_playing), actions.onResume, style = EolssuButtonStyle.Secondary)
        }
        PracticePopup.End -> EolssuDialog(
            title = stringResource(Res.string.practice_popup_end_title),
            message = stringResource(Res.string.practice_popup_end_body),
        ) {
            EolssuButton(stringResource(Res.string.practice_popup_evaluate), actions.onEvaluate)
            EolssuButton(stringResource(Res.string.practice_restart), actions.onRestartNow, style = EolssuButtonStyle.Secondary)
        }
        PracticePopup.Restart -> EolssuDialog(title = stringResource(Res.string.practice_popup_restart_title)) {
            EolssuButton(stringResource(Res.string.practice_popup_restart_confirm), actions.onRestartNow)
            EolssuButton(stringResource(Res.string.practice_popup_keep_playing), actions.onClose, style = EolssuButtonStyle.Secondary)
        }
        PracticePopup.Exit -> EolssuDialog(title = stringResource(Res.string.practice_popup_exit_title)) {
            EolssuButton(stringResource(Res.string.practice_popup_exit_confirm), actions.onExit)
            EolssuButton(stringResource(Res.string.practice_popup_keep_playing), actions.onClose, style = EolssuButtonStyle.Secondary)
        }
        PracticePopup.Interrupted -> EolssuDialog(
            title = stringResource(Res.string.practice_popup_interrupted_title),
            message = stringResource(Res.string.practice_popup_interrupted_body),
            icon = EolssuIcons.Alert,
        ) {
            EolssuButton(stringResource(Res.string.practice_popup_start_again), actions.onRestartNow)
            EolssuButton(stringResource(Res.string.practice_popup_to_ready), actions.onToReady, style = EolssuButtonStyle.Secondary)
        }
        PracticePopup.RecordFailed -> EolssuDialog(
            title = stringResource(Res.string.practice_popup_record_failed_title),
            message = stringResource(Res.string.practice_popup_record_failed_body),
            icon = EolssuIcons.Alert,
        ) {
            EolssuButton(stringResource(Res.string.practice_popup_start_again), actions.onRestartNow)
            EolssuButton(stringResource(Res.string.practice_popup_to_ready), actions.onToReady, style = EolssuButtonStyle.Secondary)
        }
        PracticePopup.TooLarge -> EolssuDialog(
            title = stringResource(Res.string.practice_popup_too_large_title),
            message = stringResource(Res.string.practice_popup_too_large_body),
            icon = EolssuIcons.Alert,
        ) {
            EolssuButton(stringResource(Res.string.practice_restart), actions.onRestartNow)
            EolssuButton(stringResource(Res.string.practice_popup_to_ready), actions.onToReady, style = EolssuButtonStyle.Secondary)
        }
    }
}
