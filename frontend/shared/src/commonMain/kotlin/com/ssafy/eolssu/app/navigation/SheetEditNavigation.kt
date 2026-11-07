package com.ssafy.eolssu.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ssafy.eolssu.feature.song.edit.EditNewRoute
import com.ssafy.eolssu.feature.song.edit.EditPickRoute
import com.ssafy.eolssu.feature.song.edit.EditPickTarget
import com.ssafy.eolssu.feature.song.edit.EditPreviewRoute
import com.ssafy.eolssu.feature.song.edit.EditSource
import com.ssafy.eolssu.feature.song.edit.EditView
import com.ssafy.eolssu.feature.song.edit.HwangPitch
import com.ssafy.eolssu.feature.song.edit.ImportKind
import com.ssafy.eolssu.feature.song.edit.ImportResultRoute
import com.ssafy.eolssu.feature.song.edit.ImportScreen
import com.ssafy.eolssu.feature.song.edit.SheetEditRoute
import com.ssafy.eolssu.feature.song.edit.SheetEditViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 악보 추가·편집 화면 경로(2차 EDIT-01~06). 가져오기 종류(`kind`)는 `jg`·`staff`, 황 음(`hwang`)은 [HwangPitch] 이름이다.
 * 편집 경로의 `from`은 `jg`·`staff`(인식 결과에서) · `song`·`sheet`(목록에서 복사) · `mine`(내 악보 '편집')이다.
 */
object SheetEditDestination {
    const val NEW = "sheet-edit/new"
    const val PICK = "sheet-edit/pick"
    const val IMPORT = "sheet-edit/import/{kind}?hwang={hwang}"
    const val IMPORT_RESULT = "sheet-edit/import/{kind}/result?hwang={hwang}"
    const val EDIT = "sheet-edit/edit?from={from}&id={id}&pending={pending}&hwang={hwang}"
    const val PREVIEW = "sheet-edit/preview"

    fun import(kind: ImportKind, hwang: HwangPitch) = "sheet-edit/import/${kind.routeId}?hwang=${hwang.name}"

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
 * 정간보·오선보: 기기 선택 창 → 인식 중(EDIT-05) → 인식 결과(EDIT-06) → 편집(EDIT-02 · 03) / 불러오기: 목록 → 복사본 편집.
 * 편집기의 '미리보기'(EDIT-04)는 편집 경로에 묶인 같은 ViewModel을 쓴다. 편집 닫기는 내 악보(SONG-03)로 돌아간다.
 */
fun NavGraphBuilder.sheetEditGraph(navController: NavController) {
    composable(SheetEditDestination.NEW) { entry ->
        EditNewRoute(
            onBack = { navController.popBackStackFrom(entry) },
            onImport = { kind, hwang -> navController.navigate(SheetEditDestination.import(kind, hwang)) },
            onLoadExisting = { navController.navigate(SheetEditDestination.PICK) },
        )
    }
    composable(SheetEditDestination.PICK) { entry ->
        EditPickRoute(
            onBack = { navController.popBackStackFrom(entry) },
            onSelected = { target ->
                // 내 악보는 그 악보를 직접 고친다(SHT-08 원본·초안 그대로, SHT-04·11 저장). 서버에 복사본을 만드는 API는 없다.
                val source = when (target) {
                    is EditPickTarget.Sheet -> EditSource.Mine(target.sheetId)
                    is EditPickTarget.Song -> EditSource.CopySong(target.songId)
                }
                navController.navigate(SheetEditDestination.edit(source))
            },
        )
    }
    composable(
        SheetEditDestination.IMPORT,
        arguments = listOf(kindArgument(), hwangArgument()),
    ) { entry ->
        val kind = entry.importKind()
        val hwang = entry.hwang()
        ImportScreen(
            kind = kind,
            hwang = hwang,
            onBack = { navController.popBackStackFrom(entry) },
            onResult = { navController.navigate(SheetEditDestination.importResult(kind, hwang)) },
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
            viewModel = sheetEditViewModel(navController, entry),
            onClose = { navController.closeEditor(entry) },
            onPreview = { navController.navigate(SheetEditDestination.PREVIEW) },
            onOpenChecks = { navController.popBackStackFrom(entry) },
        )
    }
    composable(SheetEditDestination.PREVIEW) { entry ->
        EditPreviewRoute(
            viewModel = sheetEditViewModel(navController, entry),
            onBack = { navController.popBackStackFrom(entry) },
        )
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

private fun kindArgument() = navArgument("kind") { type = NavType.StringType; defaultValue = ImportKind.Jeongganbo.routeId }

private fun hwangArgument() = navArgument("hwang") { type = NavType.StringType; defaultValue = HwangPitch.Default.name }

private fun NavBackStackEntry.importKind(): ImportKind = ImportKind.fromRouteId(savedStateHandle.get<String>("kind"))

private fun NavBackStackEntry.hwang(): HwangPitch = HwangPitch.fromRouteId(savedStateHandle.get<String>("hwang"))

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
 * 편집기와 미리보기가 함께 쓰는 ViewModel. 편집 경로([SheetEditDestination.EDIT])에 묶어, 미리보기에서 돌아와도 고친 악보가 남는다.
 * 오선보 가져오기에서 왔으면 오선보 보기로 연다.
 */
@Composable
private fun sheetEditViewModel(navController: NavController, entry: NavBackStackEntry): SheetEditViewModel {
    val editEntry = remember(entry) {
        if (entry.destination.route == SheetEditDestination.EDIT) entry else navController.getBackStackEntry(SheetEditDestination.EDIT)
    }
    val source = remember(editEntry) { editEntry.editSource() }
    val startView = if ((source as? EditSource.Import)?.kind == ImportKind.Staff) EditView.Staff else EditView.Jeongganbo
    return koinViewModel(viewModelStoreOwner = editEntry) { parametersOf(source, startView) }
}
