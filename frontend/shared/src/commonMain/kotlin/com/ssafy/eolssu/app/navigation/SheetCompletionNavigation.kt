package com.ssafy.eolssu.app.navigation

import androidx.navigation.NavController

/** 완료 알림의 '열기'가 여는 내 악보 화면. */
internal enum class SheetCompletionTarget { Practice, Editor }

/**
 * 음원 생성 완료 알림이 여는 화면. 이 플랫폼에서 내 악보 음원을 연습에 틀 수 있으면(기기 저장·웹 스트리밍) 연습,
 * 아니면 편집기다(S15P31E102-1261 전에는 웹이 편집기로 갔다).
 */
internal fun sheetAudioCompletionTarget(personalAudioPlayable: Boolean): SheetCompletionTarget =
    if (personalAudioPlayable) SheetCompletionTarget.Practice else SheetCompletionTarget.Editor

/**
 * 완료 알림의 '열기'로 [sheetId] 악보를 [target] 화면에 연다(S15P31E102-1038).
 * `launchSingleTop`은 같은 경로 패턴이면 인자만 바꾼 채 기존 화면 항목(ViewModel·SavedStateHandle)을 재사용해,
 * 다른 내 악보의 연습·편집 화면에 있을 때 누르면 알림의 악보로 바뀌지 않았다. 이미 그 악보 화면일 때만 다시 쌓지 않는다.
 */
internal fun NavController.openCompletedSheet(sheetId: String, target: SheetCompletionTarget) {
    val current = currentBackStackEntry
    if (current != null && showsSheet(current.destination.route, { current.savedStateHandle.get<Any>(it) }, sheetId, target)) return
    navigate(
        when (target) {
            SheetCompletionTarget.Practice -> EolssuDestination.practice(sheetId, mine = true)
            SheetCompletionTarget.Editor -> SheetEditDestination.editMine(sheetId)
        },
    )
}

/** 현재 화면([route] 경로 패턴과 [arg] 인자)이 이미 [sheetId] 악보의 [target] 화면인지. */
internal fun showsSheet(route: String?, arg: (String) -> Any?, sheetId: String, target: SheetCompletionTarget): Boolean = when (target) {
    SheetCompletionTarget.Practice -> route == EolssuDestination.PRACTICE && arg("songId") == sheetId && arg("mine") == true
    SheetCompletionTarget.Editor -> route == SheetEditDestination.EDIT && arg("from") == "mine" && arg("id") == sheetId
}
