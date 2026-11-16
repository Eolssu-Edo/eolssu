package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.data.song.SheetConversion
import com.ssafy.eolssu.data.song.SheetConversionFailure
import com.ssafy.eolssu.data.song.SheetNotation
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.mysongs_conversion_action_other_file
import eolssu.shared.generated.resources.mysongs_conversion_action_retake
import eolssu.shared.generated.resources.mysongs_conversion_action_split
import eolssu.shared.generated.resources.mysongs_conversion_action_try_later
import eolssu.shared.generated.resources.mysongs_conversion_failed_file
import eolssu.shared.generated.resources.mysongs_conversion_failed_file_row
import eolssu.shared.generated.resources.mysongs_conversion_failed_file_tip1
import eolssu.shared.generated.resources.mysongs_conversion_failed_file_tip2
import eolssu.shared.generated.resources.mysongs_conversion_failed_no_grid
import eolssu.shared.generated.resources.mysongs_conversion_failed_no_grid_row
import eolssu.shared.generated.resources.mysongs_conversion_failed_no_grid_tip1
import eolssu.shared.generated.resources.mysongs_conversion_failed_no_grid_tip2
import eolssu.shared.generated.resources.mysongs_conversion_failed_no_grid_tip3
import eolssu.shared.generated.resources.mysongs_conversion_failed_no_score
import eolssu.shared.generated.resources.mysongs_conversion_failed_no_score_row
import eolssu.shared.generated.resources.mysongs_conversion_failed_no_score_tip1
import eolssu.shared.generated.resources.mysongs_conversion_failed_no_score_tip2
import eolssu.shared.generated.resources.mysongs_conversion_failed_no_staff
import eolssu.shared.generated.resources.mysongs_conversion_failed_no_staff_row
import eolssu.shared.generated.resources.mysongs_conversion_failed_no_staff_tip1
import eolssu.shared.generated.resources.mysongs_conversion_failed_no_staff_tip2
import eolssu.shared.generated.resources.mysongs_conversion_failed_no_staff_tip3
import eolssu.shared.generated.resources.mysongs_conversion_failed_server
import eolssu.shared.generated.resources.mysongs_conversion_failed_server_row
import eolssu.shared.generated.resources.mysongs_conversion_failed_server_tip1
import eolssu.shared.generated.resources.mysongs_conversion_failed_server_tip2
import eolssu.shared.generated.resources.mysongs_conversion_failed_timeout
import eolssu.shared.generated.resources.mysongs_conversion_failed_timeout_row
import eolssu.shared.generated.resources.mysongs_conversion_failed_timeout_tip1
import eolssu.shared.generated.resources.mysongs_conversion_failed_timeout_tip2
import eolssu.shared.generated.resources.mysongs_conversion_failed_unknown_row
import org.jetbrains.compose.resources.StringResource

/** 실패 뒤 사용자가 할 일. 지금 서버는 같은 원본으로 다시 시도하지 않으므로(SHT-14 409) 모두 새로 불러오기로 이어진다. */
enum class ConversionNextAction(val label: StringResource) {
    /** 악보가 잘 보이게 다시 찍거나 악보 부분만 잘라 올린다. */
    Retake(Res.string.mysongs_conversion_action_retake),

    /** 다른 파일(사진·다시 저장한 PDF)로 올린다. */
    OtherFile(Res.string.mysongs_conversion_action_other_file),

    /** 쪽 수를 나눠 올린다. */
    Split(Res.string.mysongs_conversion_action_split),

    /** 악보 문제가 아니므로 잠시 뒤 같은 파일로 다시 올린다. */
    TryLater(Res.string.mysongs_conversion_action_try_later),
}

/**
 * 변환 실패 안내. 원인([title]), 목록 줄의 한 줄 안내([row]), 대기 화면의 할 일 목록([tips])과 다음 행동([action])을 묶는다.
 * 사용자 잘못이 아닌 실패(시간 초과·서버 오류)는 악보 탓을 하지 않는다.
 */
enum class ConversionFailureGuide(
    val title: StringResource,
    val row: StringResource,
    val tips: List<StringResource>,
    val action: ConversionNextAction,
) {
    /** 정간보에서 정간 칸(격자)을 찾지 못했다. */
    NoGrid(Res.string.mysongs_conversion_failed_no_grid, Res.string.mysongs_conversion_failed_no_grid_row,
        listOf(Res.string.mysongs_conversion_failed_no_grid_tip1, Res.string.mysongs_conversion_failed_no_grid_tip2, Res.string.mysongs_conversion_failed_no_grid_tip3), ConversionNextAction.Retake),

    /** 오선보에서 오선·음표를 찾지 못했다. */
    NoStaff(Res.string.mysongs_conversion_failed_no_staff, Res.string.mysongs_conversion_failed_no_staff_row,
        listOf(Res.string.mysongs_conversion_failed_no_staff_tip1, Res.string.mysongs_conversion_failed_no_staff_tip2, Res.string.mysongs_conversion_failed_no_staff_tip3), ConversionNextAction.Retake),

    /** 악보를 찾지 못했지만 표기법을 모른다. */
    NoScore(Res.string.mysongs_conversion_failed_no_score, Res.string.mysongs_conversion_failed_no_score_row,
        listOf(Res.string.mysongs_conversion_failed_no_score_tip1, Res.string.mysongs_conversion_failed_no_score_tip2), ConversionNextAction.Retake),

    /** 형식·크기·쪽 수가 맞지 않거나 열 수 없는 파일이다. */
    File(Res.string.mysongs_conversion_failed_file, Res.string.mysongs_conversion_failed_file_row,
        listOf(Res.string.mysongs_conversion_failed_file_tip1, Res.string.mysongs_conversion_failed_file_tip2), ConversionNextAction.OtherFile),

    /** 시간 상한을 넘었다. */
    Timeout(Res.string.mysongs_conversion_failed_timeout, Res.string.mysongs_conversion_failed_timeout_row,
        listOf(Res.string.mysongs_conversion_failed_timeout_tip1, Res.string.mysongs_conversion_failed_timeout_tip2), ConversionNextAction.Split),

    /** 인식기·서버 오류(인식기의 `ENGINE_ERROR` 포함). */
    Server(Res.string.mysongs_conversion_failed_server, Res.string.mysongs_conversion_failed_server_row,
        listOf(Res.string.mysongs_conversion_failed_server_tip1, Res.string.mysongs_conversion_failed_server_tip2), ConversionNextAction.TryLater),
}

/** 서버 실패 코드와 표기법으로 안내를 고른다. 원인을 모르면 서버 오류로 본다. */
internal fun conversionFailureGuide(failure: SheetConversionFailure?, notation: SheetNotation?): ConversionFailureGuide = when (failure) {
    SheetConversionFailure.NoScoreFound -> when (notation) {
        SheetNotation.Jeongganbo -> ConversionFailureGuide.NoGrid
        SheetNotation.Staff -> ConversionFailureGuide.NoStaff
        null -> ConversionFailureGuide.NoScore
    }
    SheetConversionFailure.UnsupportedFile -> ConversionFailureGuide.File
    SheetConversionFailure.Timeout -> ConversionFailureGuide.Timeout
    SheetConversionFailure.ServerError, null -> ConversionFailureGuide.Server
}

/**
 * 내 악보 목록 실패 줄의 한 줄 안내. 원인 코드를 아직 받지 못했으면(목록만 받았거나 상세 조회 실패)
 * '서버 문제'라고 단정하지 않고 줄을 눌러 원인을 보게 한다.
 */
internal fun conversionFailureRow(conversion: SheetConversion?): StringResource =
    conversion?.failure?.let { conversionFailureGuide(it, conversion.notation).row } ?: Res.string.mysongs_conversion_failed_unknown_row
