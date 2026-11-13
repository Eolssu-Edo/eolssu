package com.ssafy.eolssu.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ssafy.eolssu.feature.song.edit.EditNewRoute
import com.ssafy.eolssu.feature.song.edit.EditPickRoute
import com.ssafy.eolssu.feature.song.edit.EditSource
import com.ssafy.eolssu.feature.song.edit.EditView
import com.ssafy.eolssu.feature.song.edit.HwangPitch
import com.ssafy.eolssu.feature.song.edit.ImportKind
import com.ssafy.eolssu.feature.song.edit.ImportResultRoute
import com.ssafy.eolssu.feature.song.edit.SheetImportRoute
import com.ssafy.eolssu.feature.song.edit.SheetEditRoute
import com.ssafy.eolssu.feature.song.edit.SheetEditViewModel
import com.ssafy.eolssu.feature.song.edit.component.SheetFileProblem
import com.ssafy.eolssu.data.song.SheetConversionFailure
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 악보 추가·편집 화면 경로(2차 EDIT-01~06). 가져오기 종류(`kind`)는 `jg`·`staff`, 황 음(`hwang`)은 [HwangPitch] 이름이다.
 * 편집 경로의 `from`은 `jg`·`staff`(인식 결과에서) · `song`·`sheet`(목록에서 복사) · `mine`(내 악보 '편집')이다.
 */
object SheetEditDestination {
    const val NEW = "sheet-edit/new?failure={failure}"
    const val PICK = "sheet-edit/pick"
    const val IMPORT = "sheet-edit/import/{kind}?hwang={hwang}&pick={pick}"
    const val IMPORT_RESULT = "sheet-edit/import/{kind}/result?hwang={hwang}"
    const val EDIT = "sheet-edit/edit?from={from}&id={id}&pending={pending}&hwang={hwang}"
    const val VIEW = "sheet-edit/view/{id}"

    /** 악보 추가. 내 악보 '다시 불러오기'면 [failure]로 변환 실패 원인을 넘겨 안내 창을 먼저 띄운다. */
    fun new(failure: SheetConversionFailure? = null) = if (failure == null) "sheet-edit/new" else "sheet-edit/new?failure=${failure.name}"

    /** 파일·악기·장단 선택. [pick]이면 들어오자마자 기기 파일 선택 창을 연다. */
    fun import(kind: ImportKind, hwang: HwangPitch, pick: Boolean = false) = "sheet-edit/import/${kind.routeId}?hwang=${hwang.name}&pick=$pick"

    fun importResult(kind: ImportKind, hwang: HwangPitch) = "sheet-edit/import/${kind.routeId}/result?hwang=${hwang.name}"

    fun edit(source: EditSource): String = when (source) {
        is EditSource.Import -> "sheet-edit/edit?from=${source.kind.routeId}&pending=${source.pendingChecks}&hwang=${source.hwang.name}"
        is EditSource.CopySong -> "sheet-edit/edit?from=song&id=${source.songId}"
        is EditSource.CopySheet -> "sheet-edit/edit?from=sheet&id=${source.sheetId}"
        is EditSource.Mine -> "sheet-edit/edit?from=mine&id=${source.sheetId}"
    }

    /** 내 악보 목록의 '편집'(Edit-Mine). */
    fun editMine(sheetId: String) = edit(EditSource.Mine(sheetId))

}

/**
 * 악보 추가·편집 화면 이동. 내 악보 '악보 추가' → 악보 추가(EDIT-01) →
 * 정간보·오선보: 파일·악기·장단 선택 → 실제 업로드 접수 → 내 악보 목록 또는 악보 추가. 변환 진행·실패는 두 화면의 내 악보 줄에서 보이고(SHT-10·14), 인식 결과 확인 화면 연결은 후속 작업이다.
 * 기존 예시 결과·복사 편집 경로는 이 접수 흐름에서 사용하지 않는다.
 * 편집과 저장은 편집 경로 안에서 처리한다. 편집 닫기는 내 악보(SONG-03)로 돌아간다.
 */
fun NavGraphBuilder.sheetEditGraph(navController: NavController) {
    composable(
        SheetEditDestination.NEW,
        arguments = listOf(navArgument("failure") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) { entry ->
        EditNewRoute(
            onBack = { navController.popBackStackFrom(entry) },
            onImport = { kind, hwang -> navController.navigate(SheetEditDestination.import(kind, hwang, pick = true)) },
            onLoadExisting = { navController.navigate(SheetEditDestination.PICK) },
            reimportProblem = entry.reimportProblem(),
            // 내 악보 탭과 같게: 연습할 수 있으면 연습, 아니면 편집기로 연다.
            onOpenSheet = { sheet ->
                navController.navigate(
                    if (sheet.practiceReady) EolssuDestination.practice(sheet.id, mine = true)
                    else SheetEditDestination.editMine(sheet.id),
                )
            },
            onEditSheet = { sheet -> navController.navigate(SheetEditDestination.editMine(sheet.id)) },
        )
    }
    composable(SheetEditDestination.PICK) { entry ->
        EditPickRoute(
            onBack = { navController.popBackStackFrom(entry) },
            onOpenSheet = { sheetId ->
                if (navController.currentBackStackEntry?.id == entry.id) {
                    navController.navigate(SheetEditDestination.editMine(sheetId))
                }
            },
        )
    }
    composable(
        SheetEditDestination.IMPORT,
        arguments = listOf(kindArgument(), hwangArgument(), navArgument("pick") { type = NavType.BoolType; defaultValue = false }),
    ) { entry ->
        val kind = entry.importKind()
        val hwang = entry.hwang()
        SheetImportRoute(
            kind = kind,
            hwang = hwang,
            pickOnStart = entry.savedStateHandle.get<Boolean>("pick") == true,
            onBack = { navController.popBackStackFrom(entry) },
            // 접수 뒤: 연습곡 고르기에서 왔으면 그 '내 악보' 탭으로, 홈에서 악보 추가로 바로 왔으면 진행 상황이 보이는 악보 추가로 돌아간다.
            onList = {
                if (navController.currentBackStackEntry?.id == entry.id) {
                    val returned = navController.popBackStack(EolssuDestination.MY_SONGS, inclusive = false) ||
                        navController.popBackStack(SheetEditDestination.NEW, inclusive = false)
                    if (!returned) navController.navigate(EolssuDestination.mySongs(sheets = true)) {
                        popUpTo(entry.destination.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            },
        )
    }
    composable(
        SheetEditDestination.IMPORT_RESULT,
        arguments = listOf(kindArgument(), hwangArgument()),
    ) { entry ->
        val kind = entry.importKind()
        val hwang = entry.hwang()
        ImportResultRoute(
            kind = kind,
            hwang = hwang,
            onBack = { navController.popBackStackFrom(entry) },
            onEdit = { pending -> navController.navigate(SheetEditDestination.edit(EditSource.Import(kind, hwang, pending))) },
        )
    }
    composable(
        SheetEditDestination.EDIT,
        arguments = listOf(
            navArgument("from") { type = NavType.StringType; defaultValue = "mine" },
            navArgument("id") { type = NavType.StringType; defaultValue = "" },
            navArgument("pending") { type = NavType.IntType; defaultValue = 0 },
            hwangArgument(),
        ),
    ) { entry ->
        SheetEditRoute(
            viewModel = sheetEditViewModel(entry),
            onClose = { navController.closeEditor(entry) },
            onOpenChecks = { navController.popBackStackFrom(entry) },
            onSaved = { navController.openMySheetsAfterSave(entry) },
        )
    }
    composable(
        SheetEditDestination.VIEW,
        arguments = listOf(navArgument("id") { type = NavType.StringType }),
    ) { entry ->
        val sheetId = entry.savedStateHandle.get<String>("id").orEmpty()
        // 이전 버전의 열람 경로도 중간 화면 없이 편집기로 연결한다.
        LaunchedEffect(sheetId) {
            if (navController.currentBackStackEntry?.id == entry.id) {
                navController.navigate(SheetEditDestination.editMine(sheetId)) {
                    popUpTo(SheetEditDestination.VIEW) { inclusive = true }
                }
            }
        }
    }
}

/**
 * 편집 닫기: 아래에 내 악보가 있으면 그 사이 화면(악보 추가·인식·목록)을 모두 닫고 내 악보로 간다.
 * 홈 '악보 추가·편집하기'처럼 악보 추가부터 들어왔으면 악보 추가를 연 화면으로, 둘 다 없으면 한 화면 뒤로 간다.
 */
private fun NavController.closeEditor(entry: NavBackStackEntry) {
    if (currentBackStackEntry?.id != entry.id) return
    if (popBackStack(EolssuDestination.MY_SONGS, inclusive = false)) return
    if (!popBackStack(SheetEditDestination.NEW, inclusive = true)) popBackStack()
}

/**
 * 저장 뒤: 편집기와 그 앞의 악보 추가·불러오기 화면을 닫고 연습곡 고르기 '내 악보' 탭을 새로 연다.
 * 아래에 연습곡 고르기가 있으면 그 화면도 바꿔 열어 탭을 '내 악보'로 맞춘다.
 */
private fun NavController.openMySheetsAfterSave(entry: NavBackStackEntry) {
    if (currentBackStackEntry?.id != entry.id) return
    val inStack = { route: String -> runCatching { getBackStackEntry(route) }.isSuccess }
    val popTo = when {
        inStack(EolssuDestination.MY_SONGS) -> EolssuDestination.MY_SONGS
        inStack(SheetEditDestination.NEW) -> SheetEditDestination.NEW
        else -> null
    }
    navigate(EolssuDestination.mySongs(sheets = true)) {
        if (popTo != null) popUpTo(popTo) { inclusive = true } else popUpTo(entry.destination.id) { inclusive = true }
        launchSingleTop = true
    }
}

private fun kindArgument() = navArgument("kind") { type = NavType.StringType; defaultValue = ImportKind.Jeongganbo.routeId }

private fun hwangArgument() = navArgument("hwang") { type = NavType.StringType; defaultValue = HwangPitch.Default.name }

private fun NavBackStackEntry.importKind(): ImportKind = ImportKind.fromRouteId(savedStateHandle.get<String>("kind"))

private fun NavBackStackEntry.hwang(): HwangPitch = HwangPitch.fromRouteId(savedStateHandle.get<String>("hwang"))

/** 내 악보 '다시 불러오기'로 넘긴 변환 실패 원인을 안내 창 종류로 바꾼다([SheetFileProblem.ofConversionFailure]). */
private fun NavBackStackEntry.reimportProblem(): SheetFileProblem? = SheetFileProblem.ofConversionFailure(
    savedStateHandle.get<String>("failure")?.let { name -> SheetConversionFailure.entries.firstOrNull { it.name == name } },
)

/** 편집 경로 인자를 [EditSource]로 되돌린다. */
private fun NavBackStackEntry.editSource(): EditSource {
    val id = savedStateHandle.get<String>("id").orEmpty()
    return when (val from = savedStateHandle.get<String>("from")) {
        ImportKind.Jeongganbo.routeId, ImportKind.Staff.routeId ->
            EditSource.Import(ImportKind.fromRouteId(from), hwang(), savedStateHandle.get<Int>("pending") ?: 0)
        "song" -> EditSource.CopySong(id)
        "sheet" -> EditSource.CopySheet(id)
        else -> EditSource.Mine(id)
    }
}

/**
 * 편집 경로([SheetEditDestination.EDIT])에 묶인 ViewModel.
 * 가져온 파일 종류와 관계없이 정간보 편집기로 연다.
 */
@Composable
private fun sheetEditViewModel(entry: NavBackStackEntry): SheetEditViewModel {
    val source = remember(entry) { entry.editSource() }
    return koinViewModel(viewModelStoreOwner = entry) { parametersOf(source, EditView.Jeongganbo) }
}
