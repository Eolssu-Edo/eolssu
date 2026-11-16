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
import com.ssafy.eolssu.feature.song.edit.SheetConversionRoute
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
    const val NEW = "sheet-edit/new?failure={failure}&replace={replace}"
    const val PICK = "sheet-edit/pick"
    const val IMPORT = "sheet-edit/import/{kind}?hwang={hwang}&replace={replace}"
    const val IMPORT_RESULT = "sheet-edit/import/{kind}/result?hwang={hwang}"
    const val EDIT = "sheet-edit/edit?from={from}&id={id}&pending={pending}&hwang={hwang}"
    const val VIEW = "sheet-edit/view/{id}"
    const val CONVERSION = "sheet-edit/conversion/{id}"

    /**
     * 악보 추가. 내 악보 '다시 불러오기'면 [failure]로 변환 실패 원인을 넘겨 안내 창을 먼저 띄우고,
     * [replace]로 그 실패 악보를 넘겨 새 악보가 접수되면 대신하게 한다(S15P31E102-1052).
     */
    fun new(failure: SheetConversionFailure? = null, replace: String? = null): String {
        val query = listOfNotNull(failure?.let { "failure=${it.name}" }, replace?.let { "replace=$it" })
        return if (query.isEmpty()) "sheet-edit/new" else "sheet-edit/new?" + query.joinToString("&")
    }

    /** 파일·악기·장단 선택. 기기 파일 선택 창은 화면의 'PDF·이미지 선택'을 눌렀을 때만 연다(S15P31E102-889). */
    fun import(kind: ImportKind, hwang: HwangPitch, replace: String? = null) =
        "sheet-edit/import/${kind.routeId}?hwang=${hwang.name}" + replace?.let { "&replace=$it" }.orEmpty()

    fun importResult(kind: ImportKind, hwang: HwangPitch) = "sheet-edit/import/${kind.routeId}/result?hwang=${hwang.name}"

    fun edit(source: EditSource): String = when (source) {
        is EditSource.Import -> "sheet-edit/edit?from=${source.kind.routeId}&pending=${source.pendingChecks}&hwang=${source.hwang.name}"
        is EditSource.CopySong -> "sheet-edit/edit?from=song&id=${source.songId}"
        is EditSource.CopySheet -> "sheet-edit/edit?from=sheet&id=${source.sheetId}"
        is EditSource.Mine -> "sheet-edit/edit?from=mine&id=${source.sheetId}"
    }

    /** 접수한 악보의 변환 대기 화면(S15P31E102-998). */
    fun conversion(sheetId: String) = "sheet-edit/conversion/$sheetId"

    /** 내 악보 목록의 '편집'(Edit-Mine). */
    fun editMine(sheetId: String) = edit(EditSource.Mine(sheetId))

}

/**
 * 악보 추가·편집 화면 이동. 내 악보 '악보 추가' → 악보 추가(EDIT-01) →
 * 정간보·오선보: 파일·악기·장단 선택 → 실제 업로드 접수 → 변환 대기 화면(S15P31E102-998) → 완료되면 편집 화면.
 * 대기 화면을 나가도 변환 진행·실패는 두 화면의 내 악보 줄에서 보이고(SHT-10·14), 연습곡 고르기의 줄을 누르면 대기 화면을 다시 연다.
 * 기존 예시 결과·복사 편집 경로는 이 접수 흐름에서 사용하지 않는다.
 * 편집과 저장은 편집 경로 안에서 처리한다. 편집 닫기는 내 악보(SONG-03)로 돌아간다.
 */
fun NavGraphBuilder.sheetEditGraph(navController: NavController) {
    composable(
        SheetEditDestination.NEW,
        arguments = listOf(
            navArgument("failure") { type = NavType.StringType; nullable = true; defaultValue = null },
            replaceArgument(),
        ),
    ) { entry ->
        EditNewRoute(
            onBack = { navController.popBackStackFrom(entry) },
            onImport = { kind, hwang, replace -> navController.navigate(SheetEditDestination.import(kind, hwang, replace)) },
            reimportSheetId = entry.replaceSheetId(),
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
            onOpenConversion = { sheet ->
                if (navController.currentBackStackEntry?.id == entry.id) {
                    navController.navigate(SheetEditDestination.conversion(sheet.id))
                }
            },
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
        arguments = listOf(kindArgument(), hwangArgument(), replaceArgument()),
    ) { entry ->
        val kind = entry.importKind()
        val hwang = entry.hwang()
        SheetImportRoute(
            kind = kind,
            hwang = hwang,
            replacing = entry.replaceSheetId(),
            onBack = { navController.popBackStackFrom(entry) },
            onList = { navController.returnToSheetList(entry) },
            // 접수되면 올리기 화면 대신 변환 대기 화면을 연다. 뒤로 가면 올리기 화면이 아닌 목록으로 돌아간다.
            onAccepted = { sheetId ->
                if (navController.currentBackStackEntry?.id == entry.id) {
                    navController.navigate(SheetEditDestination.conversion(sheetId)) {
                        popUpTo(entry.destination.id) { inclusive = true }
                    }
                }
            },
        )
    }
    composable(
        SheetEditDestination.CONVERSION,
        arguments = listOf(navArgument("id") { type = NavType.StringType }),
    ) { entry ->
        val sheetId = entry.savedStateHandle.get<String>("id").orEmpty()
        SheetConversionRoute(
            sheetId = sheetId,
            onList = { navController.returnToSheetList(entry) },
            // 완료: 대기 화면을 닫고 편집 화면을 연다. 편집을 닫으면 대기 화면 아래(목록)로 돌아간다.
            onDone = { id ->
                if (navController.currentBackStackEntry?.id == entry.id) {
                    navController.navigate(SheetEditDestination.editMine(id)) {
                        popUpTo(entry.destination.id) { inclusive = true }
                    }
                }
            },
            // 실패의 다시 불러오기: 아래에 악보 추가가 있으면 그것까지 닫고 원인 안내와 함께 새로 연다.
            onReimport = { failure ->
                if (navController.currentBackStackEntry?.id == entry.id) {
                    val newInStack = runCatching { navController.getBackStackEntry(SheetEditDestination.NEW) }.isSuccess
                    navController.navigate(SheetEditDestination.new(failure, replace = entry.savedStateHandle.get<String>("id"))) {
                        if (newInStack) popUpTo(SheetEditDestination.NEW) { inclusive = true }
                        else popUpTo(entry.destination.id) { inclusive = true }
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
 * 접수 뒤·대기 화면에서 목록으로: 연습곡 고르기에서 왔으면 그 '내 악보' 탭으로, 홈에서 악보 추가로 바로 왔으면 진행 상황이 보이는 악보 추가로 돌아간다.
 * 둘 다 없으면 이 화면을 닫고 '내 악보' 탭을 연다.
 */
private fun NavController.returnToSheetList(entry: NavBackStackEntry) {
    if (currentBackStackEntry?.id != entry.id) return
    val returned = popBackStack(EolssuDestination.MY_SONGS, inclusive = false) ||
        popBackStack(SheetEditDestination.NEW, inclusive = false)
    if (!returned) navigate(EolssuDestination.mySongs(sheets = true)) {
        popUpTo(entry.destination.id) { inclusive = true }
        launchSingleTop = true
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

/** '다시 불러오기'로 대신할 변환 실패 악보 ID(S15P31E102-1052). 없으면 새 악보로 더한다. */
private fun replaceArgument() = navArgument("replace") { type = NavType.StringType; nullable = true; defaultValue = null }

private fun NavBackStackEntry.replaceSheetId(): String? = savedStateHandle.get<String>("replace")?.takeIf { it.isNotBlank() }

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
