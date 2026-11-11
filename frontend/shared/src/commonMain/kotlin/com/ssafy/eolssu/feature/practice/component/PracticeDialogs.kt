package com.ssafy.eolssu.feature.practice.component

import androidx.compose.runtime.Composable
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.practice.PracticePopup
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_close
import eolssu.shared.generated.resources.common_confirm
import eolssu.shared.generated.resources.practice_score_check_title
import eolssu.shared.generated.resources.weak_score_changed
import eolssu.shared.generated.resources.weak_score_unavailable
import eolssu.shared.generated.resources.practice_popup_abort_body
import eolssu.shared.generated.resources.practice_popup_abort_title
import eolssu.shared.generated.resources.practice_popup_blocked_body
import eolssu.shared.generated.resources.practice_popup_blocked_title
import eolssu.shared.generated.resources.practice_popup_continue
import eolssu.shared.generated.resources.practice_popup_denied_body
import eolssu.shared.generated.resources.practice_popup_denied_title
import eolssu.shared.generated.resources.practice_popup_end_body
import eolssu.shared.generated.resources.practice_popup_end_limit_body
import eolssu.shared.generated.resources.practice_popup_end_limit_title
import eolssu.shared.generated.resources.practice_popup_end_title
import eolssu.shared.generated.resources.practice_popup_evaluate
import eolssu.shared.generated.resources.practice_popup_exit_confirm
import eolssu.shared.generated.resources.practice_popup_exit_title
import eolssu.shared.generated.resources.practice_popup_go_back
import eolssu.shared.generated.resources.practice_popup_go_home
import eolssu.shared.generated.resources.practice_popup_interrupted_body
import eolssu.shared.generated.resources.practice_popup_interrupted_title
import eolssu.shared.generated.resources.practice_popup_keep_playing
import eolssu.shared.generated.resources.practice_settings_paused_title
import eolssu.shared.generated.resources.practice_resume_recording
import eolssu.shared.generated.resources.practice_popup_later
import eolssu.shared.generated.resources.practice_popup_no_input_body
import eolssu.shared.generated.resources.practice_popup_no_input_title
import eolssu.shared.generated.resources.practice_popup_open_settings
import eolssu.shared.generated.resources.practice_popup_permission_body
import eolssu.shared.generated.resources.practice_popup_permission_title
import eolssu.shared.generated.resources.practice_popup_play_again
import eolssu.shared.generated.resources.practice_popup_record_failed_body
import eolssu.shared.generated.resources.practice_popup_record_failed_title
import eolssu.shared.generated.resources.practice_popup_restart_confirm
import eolssu.shared.generated.resources.practice_popup_restart_title
import eolssu.shared.generated.resources.practice_popup_start_again
import eolssu.shared.generated.resources.practice_popup_to_ready
import eolssu.shared.generated.resources.practice_popup_to_waiting
import eolssu.shared.generated.resources.practice_popup_too_large_body
import eolssu.shared.generated.resources.practice_popup_too_large_title
import eolssu.shared.generated.resources.practice_step_blocked_body
import eolssu.shared.generated.resources.practice_step_denied_body
import eolssu.shared.generated.resources.practice_step_permission_body
import eolssu.shared.generated.resources.practice_step_record_failed_body
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
    /** 나가기·처음부터 확인 창의 ‘계속 연주’: 멈춘 연주·카운트다운을 3·2·1 뒤 이어 간다. */
    val onKeepPlayingAfterExit: () -> Unit,
    val onToReady: () -> Unit,
    /** ‘설정에서 허용하기’: 기기 앱 설정으로 보낸다. */
    val onOpenMicSettings: () -> Unit = {},
)

/**
 * PRACTICE-01 확인 창(보드 Dialog-*). 아이콘 고리는 권한·완료·경고 셋이고, 제목만 있는 창(재시작·나가기·중단 확인)은 고리가 없다.
 * [forStep]이면(혼자 연습) 마이크 권한·녹음 실패 안내를 평가 대신 한 음씩 따라 불기 문구로 보이고, 녹음 실패는 확인만 둔다.
 * [evaluation]이면(연주 평가) 연주 중단 창에서 화면을 나가는 ‘홈으로 가기’ 대신 ‘연주 대기로’를 보이고, 평가 대기 상태로 돌아간다(S15P31E102-750).
 * ‘연습 준비로’는 연주 평가에서도 평가 대기 상태로 돌아가므로 그대로 둔다(S15P31E102-747).
 */
@Composable
fun PracticeDialog(popup: PracticePopup, actions: PracticeDialogActions, forStep: Boolean = false, evaluation: Boolean = false) {
    val toReady = stringResource(Res.string.practice_popup_to_ready)
    val colors = EolssuTheme.colors
    when (popup) {
        PracticePopup.ScoreChanged, PracticePopup.ScoreUnavailable -> EolssuDialog(
            title = stringResource(Res.string.practice_score_check_title),
            message = stringResource(if (popup == PracticePopup.ScoreChanged) Res.string.weak_score_changed else Res.string.weak_score_unavailable),
        ) {
            EolssuButton(stringResource(Res.string.common_confirm), actions.onClose)
        }
        PracticePopup.Permission -> EolssuDialog(
            title = stringResource(Res.string.practice_popup_permission_title),
            message = stringResource(if (forStep) Res.string.practice_step_permission_body else Res.string.practice_popup_permission_body),
            icon = EolssuIcons.Mic,
        ) {
            EolssuButton(stringResource(Res.string.practice_popup_continue), actions.onGrantMic)
            EolssuButton(stringResource(Res.string.practice_popup_later), actions.onDenyMic, style = EolssuButtonStyle.Ghost)
        }
        PracticePopup.SettingsPaused -> EolssuDialog(
            title = stringResource(Res.string.practice_settings_paused_title),
        ) {
            EolssuButton(stringResource(Res.string.practice_resume_recording), actions.onResume)
        }
        PracticePopup.Abort -> EolssuDialog(
            title = stringResource(Res.string.practice_popup_abort_title),
            message = stringResource(Res.string.practice_popup_abort_body),
        ) {
            if (evaluation) {
                EolssuButton(stringResource(Res.string.practice_popup_to_waiting), actions.onToReady)
            } else {
                EolssuButton(stringResource(Res.string.practice_popup_go_home), actions.onGoHome)
            }
            EolssuButton(stringResource(Res.string.practice_popup_keep_playing), actions.onResume, style = EolssuButtonStyle.Secondary)
        }
        PracticePopup.End, PracticePopup.EndAtLimit -> EolssuDialog(
            title = stringResource(if (popup == PracticePopup.EndAtLimit) Res.string.practice_popup_end_limit_title else Res.string.practice_popup_end_title),
            message = stringResource(if (popup == PracticePopup.EndAtLimit) Res.string.practice_popup_end_limit_body else Res.string.practice_popup_end_body),
            icon = EolssuIcons.Check,
            iconBackground = colors.accentTintStrong,
        ) {
            EolssuButton(stringResource(Res.string.practice_popup_evaluate), actions.onEvaluate)
            EolssuButton(stringResource(Res.string.practice_restart), actions.onRestartNow, style = EolssuButtonStyle.Secondary)
        }
        PracticePopup.EndNoInput -> EolssuDialog(
            title = stringResource(Res.string.practice_popup_no_input_title),
            message = stringResource(Res.string.practice_popup_no_input_body),
            icon = EolssuIcons.MicOff,
            iconBackground = colors.recSoft,
            iconTint = colors.recText,
        ) {
            EolssuButton(stringResource(Res.string.practice_popup_play_again), actions.onRestartNow)
            EolssuButton(stringResource(Res.string.practice_popup_go_back), actions.onToReady, style = EolssuButtonStyle.Secondary)
        }
        PracticePopup.PermDenied -> EolssuDialog(
            title = stringResource(Res.string.practice_popup_denied_title),
            message = stringResource(if (forStep) Res.string.practice_step_denied_body else Res.string.practice_popup_denied_body),
            icon = EolssuIcons.Alert,
            iconBackground = colors.recSoft,
            iconTint = colors.recText,
        ) {
            EolssuButton(stringResource(Res.string.common_confirm), actions.onClose)
        }
        PracticePopup.PermBlocked -> EolssuDialog(
            title = stringResource(Res.string.practice_popup_blocked_title),
            message = stringResource(if (forStep) Res.string.practice_step_blocked_body else Res.string.practice_popup_blocked_body),
            icon = EolssuIcons.Alert,
            iconBackground = colors.recSoft,
            iconTint = colors.recText,
        ) {
            EolssuButton(stringResource(Res.string.practice_popup_open_settings), actions.onOpenMicSettings)
            EolssuButton(stringResource(Res.string.common_close), actions.onClose, style = EolssuButtonStyle.Ghost)
        }
        PracticePopup.Restart -> EolssuDialog(title = stringResource(Res.string.practice_popup_restart_title)) {
            EolssuButton(stringResource(Res.string.practice_popup_restart_confirm), actions.onRestartNow)
            EolssuButton(stringResource(Res.string.practice_popup_keep_playing), actions.onKeepPlayingAfterExit, style = EolssuButtonStyle.Secondary)
        }
        PracticePopup.Exit -> EolssuDialog(title = stringResource(Res.string.practice_popup_exit_title)) {
            EolssuButton(stringResource(Res.string.practice_popup_exit_confirm), actions.onExit)
            EolssuButton(stringResource(Res.string.practice_popup_keep_playing), actions.onKeepPlayingAfterExit, style = EolssuButtonStyle.Secondary)
        }
        PracticePopup.Interrupted -> EolssuDialog(
            title = stringResource(Res.string.practice_popup_interrupted_title),
            message = stringResource(Res.string.practice_popup_interrupted_body),
            icon = EolssuIcons.Alert,
            iconBackground = colors.recSoft,
            iconTint = colors.recText,
        ) {
            EolssuButton(stringResource(Res.string.practice_popup_start_again), actions.onRestartNow)
            EolssuButton(toReady, actions.onToReady, style = EolssuButtonStyle.Secondary)
        }
        PracticePopup.RecordFailed -> EolssuDialog(
            title = stringResource(Res.string.practice_popup_record_failed_title),
            message = stringResource(if (forStep) Res.string.practice_step_record_failed_body else Res.string.practice_popup_record_failed_body),
            icon = EolssuIcons.Alert,
            iconBackground = colors.recSoft,
            iconTint = colors.recText,
        ) {
            if (forStep) {
                EolssuButton(stringResource(Res.string.common_confirm), actions.onClose)
            } else {
                EolssuButton(stringResource(Res.string.practice_popup_start_again), actions.onRestartNow)
                EolssuButton(toReady, actions.onToReady, style = EolssuButtonStyle.Secondary)
            }
        }
        PracticePopup.TooLarge -> EolssuDialog(
            title = stringResource(Res.string.practice_popup_too_large_title),
            message = stringResource(Res.string.practice_popup_too_large_body),
            icon = EolssuIcons.Alert,
            iconBackground = colors.recSoft,
            iconTint = colors.recText,
        ) {
            EolssuButton(stringResource(Res.string.practice_restart), actions.onRestartNow)
            EolssuButton(toReady, actions.onToReady, style = EolssuButtonStyle.Secondary)
        }
    }
}
