package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.data.song.SheetAudio
import com.ssafy.eolssu.core.audio.DeviceSheetAudio
import com.ssafy.eolssu.data.song.SheetAudioFailure
import com.ssafy.eolssu.data.song.SheetAudioStatus
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sheetedit_audio_failure_expired
import eolssu.shared.generated.resources.sheetedit_audio_failure_invalid
import eolssu.shared.generated.resources.sheetedit_audio_failure_server
import eolssu.shared.generated.resources.sheetedit_audio_failure_tempo
import eolssu.shared.generated.resources.sheetedit_audio_failure_timeout
import eolssu.shared.generated.resources.sheetedit_audio_failure_too_large
import eolssu.shared.generated.resources.sheetedit_audio_failure_too_long
import eolssu.shared.generated.resources.sheetedit_audio_failure_unknown
import org.jetbrains.compose.resources.StringResource

/**
 * 미리보기(EDIT-04)의 내 악보 음원 칸 상태. 서버 상태(SHT-13)를 화면 단계로 옮긴 것이다.
 * 기기에 내려받은 파일의 재생 상태는 서버 요청 상태와 별개다.
 */
sealed interface SheetAudioUi {
    /** 만든 음원이 없다(SHT-13 404). '음원 만들기'를 보인다. */
    data object None : SheetAudioUi

    /** SHT-12 응답을 기다리는 중. 실패하면 [previous]로 돌아간다. */
    data class Requesting(val previous: SheetAudio?) : SheetAudioUi

    /** 서버가 만드는 중(QUEUED·RUNNING). `pollAfterMs` 간격으로 상태를 다시 묻는다. */
    data class Generating(val audio: SheetAudio) : SheetAudioUi

    /** 만들어졌다(DONE). [SheetAudio.outdated]면 '악보가 바뀌었어요 · 다시 만들기'를 보인다. */
    data class Ready(val audio: SheetAudio) : SheetAudioUi

    /** 만들지 못했다(FAILED). 사유는 [SheetAudio.failureCode]. '다시 시도'를 보인다. */
    data class Failed(val audio: SheetAudio) : SheetAudioUi

    /** 만드는 중이었는데 상태 조회 응답을 거듭 받지 못했다. '다시 확인'을 보인다. */
    data class Unknown(val audio: SheetAudio?) : SheetAudioUi

    /** 서버가 마지막으로 알려 준 음원. 파일은 만드는 중·실패에도 남는다. */
    val latest: SheetAudio?
        get() = when (this) {
            None -> null
            is Requesting -> previous
            is Generating -> audio
            is Ready -> audio
            is Failed -> audio
            is Unknown -> audio
        }

    /** 요청·생성이 진행 중이라 새 요청을 받지 않는다. */
    val busy: Boolean get() = this is Requesting || this is Generating

    /** 새로 만들 수 있다: 없음, 실패, 확인 못 함, 악보가 바뀜. 현재 버전의 음원이 있으면 서버가 같은 것을 돌려주므로 받지 않는다. */
    val canRequest: Boolean
        get() = when (this) {
            None, is Failed, is Unknown -> true
            is Ready -> audio.outdated
            is Requesting, is Generating -> false
        }
}

/** 서버 상태를 화면 단계로 옮긴다. */
internal fun SheetAudio.toUi(): SheetAudioUi = when (status) {
    SheetAudioStatus.Queued, SheetAudioStatus.Running -> SheetAudioUi.Generating(this)
    SheetAudioStatus.Done -> SheetAudioUi.Ready(this)
    SheetAudioStatus.Failed -> SheetAudioUi.Failed(this)
}

/**
 * 음원을 만들지 못한 이유와 할 일 한 줄. 다시 받기는 연습 화면에서 하고, 악보를 고쳐야 하면 고친 뒤 저장하게 한다.
 * 모르는 코드나 사유가 없으면 일반 안내로 돌린다(S15P31E102-1097).
 */
internal fun SheetAudioFailure?.reasonText(): StringResource = when (this) {
    SheetAudioFailure.Timeout -> Res.string.sheetedit_audio_failure_timeout
    SheetAudioFailure.TooLarge -> Res.string.sheetedit_audio_failure_too_large
    SheetAudioFailure.ServerError -> Res.string.sheetedit_audio_failure_server
    SheetAudioFailure.Expired -> Res.string.sheetedit_audio_failure_expired
    SheetAudioFailure.TooLong -> Res.string.sheetedit_audio_failure_too_long
    SheetAudioFailure.TempoUnrenderable -> Res.string.sheetedit_audio_failure_tempo
    SheetAudioFailure.InvalidRequest -> Res.string.sheetedit_audio_failure_invalid
    SheetAudioFailure.Unknown, null -> Res.string.sheetedit_audio_failure_unknown
}

/** 다운로드 링크나 경로를 노출하지 않고 기기에 저장한 음원을 식별한다. */
val DeviceSheetAudio.playbackId: String get() = "sheet-audio:$sha256"

internal fun DeviceSheetAudio.matchesDownload(audio: SheetAudio): Boolean =
    sha256 == audio.audio?.sha256 && instrumentId == audio.instrumentId &&
        bpm == audio.bpm && withJangdan == audio.withJangdan &&
        (audio.audio.performance == null || performance == audio.audio.performance)
