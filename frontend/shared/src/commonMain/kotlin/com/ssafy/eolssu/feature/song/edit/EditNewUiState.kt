package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.feature.song.MySheetList
import com.ssafy.eolssu.feature.song.SongLoadState

/**
 * EDIT-01 악보 추가 상태. 정간보·오선보 가져오기 중 하나를 고른다(기본 정간보).
 * '내 악보·제공 곡 불러오기'는 고르는 대신 바로 목록(EditPick)으로 가므로 여기 두지 않는다.
 * 아래에는 내 악보와 그 변환 진행 상황([mine])을 내 악보 탭과 같게 보인다.
 *
 * @property devicePickOpen 기기에서 사진·PDF를 고르는 창(DevicePick)이 열려 있다.
 * @property sheetLoad 내 악보 목록을 받는 상태.
 * @property deleting 삭제를 묻는 창에 띄운 내 악보. 창이 닫혀 있으면 null.
 */
data class EditNewUiState(
    val kind: ImportKind = ImportKind.Jeongganbo,
    val devicePickOpen: Boolean = false,
    val mine: MySheetList = MySheetList(),
    val sheetLoad: SongLoadState = SongLoadState.Loading,
    val deleting: MySheet? = null,
    val deleteFailed: Boolean = false,
)
