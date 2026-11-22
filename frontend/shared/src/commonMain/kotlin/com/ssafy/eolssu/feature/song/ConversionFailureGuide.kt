package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.data.song.SheetConversion
import com.ssafy.eolssu.data.song.SheetConversionFailure
import com.ssafy.eolssu.data.song.SheetNotation
import eolssu.shared.generated.resources.Res
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

/**
 * 실패 뒤 사용자가 할 일의 종류. 대기 화면의 호랑이 그림을 고르는 데만 쓴다.
 * 서버가 원본을 보관하지 않아(SHT-14 409) 실패 화면에서 바로 다시 불러오는 버튼은 두지 않는다(S15P31E102-1140).
 */
enum class ConversionNextAction {
    /** 악보가 잘 보이게 다시 찍거나 악보 부분만 잘라 올린다. */
    Retake,

    /** 다른 PDF나 다시 저장한 PDF로 올린다. */
    OtherFile,

    /** 악보 문제가 아니거나 단정할 수 없으므로 잠시 뒤 같은 파일로 다시 올린다. */
    TryLater,
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

    /** 시간 상한을 넘었다. 처리가 오래 걸린 때뿐 아니라 대기 중 기한이 지나거나 원본이 만료돼도 난다(사용자 탓으로 단정하지 않는다). */
    Timeout(Res.string.mysongs_conversion_failed_timeout, Res.string.mysongs_conversion_failed_timeout_row,
        listOf(Res.string.mysongs_conversion_failed_timeout_tip1, Res.string.mysongs_conversion_failed_timeout_tip2), ConversionNextAction.TryLater),

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
