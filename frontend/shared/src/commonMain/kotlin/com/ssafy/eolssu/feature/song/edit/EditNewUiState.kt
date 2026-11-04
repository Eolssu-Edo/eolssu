package com.ssafy.eolssu.feature.song.edit

/**
 * EDIT-01 악보 추가 상태. 정간보·오선보 가져오기 중 하나를 고르고(기본 정간보), 오선보면 황으로 읽을 음을 고른다.
 * '내 악보·제공 곡 불러오기'는 고르는 대신 바로 목록(EditPick)으로 가므로 여기 두지 않는다.
 *
 * @property devicePickOpen 기기에서 사진·PDF를 고르는 창(DevicePick)이 열려 있다.
 */
data class EditNewUiState(
    val kind: ImportKind = ImportKind.Jeongganbo,
    val hwang: HwangPitch = HwangPitch.Default,
    val devicePickOpen: Boolean = false,
)
