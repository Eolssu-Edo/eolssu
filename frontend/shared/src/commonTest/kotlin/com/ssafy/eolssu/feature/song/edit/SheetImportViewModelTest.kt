package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.*
import com.ssafy.eolssu.data.song.*
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
internal class SheetImportViewModelTest : CatalogViewModelTest() {
    private val instruments = listOf(CatalogInstrument("4", "DANSO", "단소"))
    // 실제 카탈로그 id: 굿거리 2(V6), 세마치 3, 4박 기본 반주 12(V40, 편곡 반주라 정간보 올리기 목록에서 뺀다).
    private val guguri = CatalogJangdanSummary("2", "굿거리", 66, "DOTTED_QUARTER", 4, 4)
    private val semachi = CatalogJangdanSummary("3", "세마치", 90, "DOTTED_QUARTER", 3, 3)
    private val fourBeat = CatalogJangdanSummary("12", "4박 기본 반주", 160, "QUARTER", 4, 4)
    private val jangdans = listOf(guguri, semachi, fourBeat)
    private val pdf = SheetFile("실제 악보.pdf", "%PDF-1.7\n".encodeToByteArray())

    @Test fun incompleteSubmissionKeepsOnlyUnresolvedFieldErrorsUntilAccepted() = runTest {
        var uploads = 0
        val repository = object : SheetImportRepository {
            override suspend fun upload(input: SheetImportInput, key: String): SheetImportResult {
                uploads++
                assertTrue(input.valid())
                return SheetImportResult.Accepted("91")
            }
        }
        val model = keep(SheetImportViewModel(ImportKind.Jeongganbo, HwangPitch.Default, repository, { instruments }, { jangdans }))
        runCurrent()
        fun errors() = model.uiState.value.inputErrors(ImportKind.Jeongganbo)
        assertTrue(errors().isEmpty())
        model.submit(); runCurrent()
        assertEquals(SheetImportField.entries.toSet(), errors())
        assertEquals(0, uploads)
        model.title("곡")
        assertEquals(setOf(SheetImportField.Instrument, SheetImportField.Jangdan, SheetImportField.Files), errors())
        model.instrument("4")
        assertEquals(setOf(SheetImportField.Jangdan, SheetImportField.Files), errors())
        model.jangdan("2")
        assertEquals(setOf(SheetImportField.Files), errors())
        model.selectFiles(SheetFileSelection.Selected(listOf(pdf)))
        assertTrue(errors().isEmpty())
        model.submit(); runCurrent()
        assertEquals(1, uploads)
        assertEquals("91", model.uiState.value.acceptedId)
        assertTrue(errors().isEmpty())
    }

    @Test fun aFullListShowsTheLimit() = runTest {
        var uploads = 0
        val repository = object : SheetImportRepository {
            override suspend fun upload(input: SheetImportInput, key: String): SheetImportResult {
                uploads++
                return SheetImportResult.Failed("SHEET_LIMIT_EXCEEDED")
            }
        }
        val model = keep(SheetImportViewModel(ImportKind.Staff, HwangPitch.Default, repository, { instruments }, { jangdans }))
        runCurrent()
        model.title("곡"); model.instrument("4")
        model.selectFiles(SheetFileSelection.Selected(listOf(pdf)))
        model.submit(); runCurrent()
        assertEquals(1, uploads)
        assertEquals("SHEET_LIMIT_EXCEEDED", model.uiState.value.error)
    }

    @Test fun staffValidationSurvivesCancelledPickerAndDoesNotRequireJangdan() = runTest {
        val repository = object : SheetImportRepository {
            override suspend fun upload(input: SheetImportInput, key: String): SheetImportResult {
                assertNull(input.jangdanId)
                return SheetImportResult.Failed("INVALID_REQUEST")
            }
        }
        val model = keep(SheetImportViewModel(ImportKind.Staff, HwangPitch.Eb4, repository, { instruments }, { jangdans }))
        runCurrent()
        model.submit()
        assertEquals(setOf(SheetImportField.Title, SheetImportField.Instrument, SheetImportField.Files), model.uiState.value.inputErrors(ImportKind.Staff))
        model.selectFiles(SheetFileSelection.Selected(listOf(pdf)))
        assertEquals("실제 악보", model.uiState.value.title)
        assertTrue(model.beginPicking())
        model.selectFiles(SheetFileSelection.Cancelled)
        assertEquals(listOf(pdf), model.uiState.value.files)
        assertEquals(setOf(SheetImportField.Instrument), model.uiState.value.inputErrors(ImportKind.Staff))
        model.instrument("4")
        model.submit(); runCurrent()
        assertEquals("INVALID_REQUEST", model.uiState.value.error)
        assertTrue(model.uiState.value.inputErrors(ImportKind.Staff).isEmpty())
    }

    @Test fun explicitJangdanStableKeyDuplicateTapAndAcceptedOnlyAfterReply() = runTest {
        val keys = mutableListOf<String>()
        var response = CompletableDeferred<SheetImportResult>()
        val repository = object : SheetImportRepository {
            override suspend fun upload(input: SheetImportInput, key: String): SheetImportResult {
                assertEquals("2", input.jangdanId); assertNull(input.hwangPitch)
                keys += key
                return response.await()
            }
        }
        val model = keep(SheetImportViewModel(ImportKind.Jeongganbo, HwangPitch.Default, repository, { instruments }, { jangdans }))
        runCurrent()
        model.selectFiles(SheetFileSelection.Selected(listOf(pdf)))
        model.title("곡"); model.instrument("4")
        model.submit(); runCurrent(); assertTrue(keys.isEmpty())
        model.jangdan("2"); model.submit(); model.submit(); runCurrent()
        assertEquals(1, keys.size); assertNull(model.uiState.value.acceptedId)
        response.complete(SheetImportResult.Failed()); runCurrent()
        response = CompletableDeferred()
        model.submit(); runCurrent(); assertEquals(keys[0], keys[1])
        response.complete(SheetImportResult.Accepted("91")); runCurrent()
        assertEquals("91", model.uiState.value.acceptedId)
        assertTrue(model.uiState.value.files.isEmpty())
        model.submit(); runCurrent(); assertEquals(2, keys.size)
    }

    @Test fun unknownFormatAndInvalidFileSetGetSeparateErrorsAndDismissKeepsFiles() = runTest {
        val repository = object : SheetImportRepository {
            override suspend fun upload(input: SheetImportInput, key: String): SheetImportResult = SheetImportResult.Failed()
        }
        val model = keep(SheetImportViewModel(ImportKind.Jeongganbo, HwangPitch.Default, repository, { instruments }, { jangdans }))
        runCurrent()
        model.selectFiles(SheetFileSelection.Selected(listOf(pdf)))
        model.selectFiles(SheetFileSelection.Selected(listOf(SheetFile("메모.txt", "악보".encodeToByteArray()))))
        assertEquals("UNSUPPORTED_FILE_TYPE", model.uiState.value.error)
        model.dismissError()
        assertNull(model.uiState.value.error)
        assertEquals(listOf(pdf), model.uiState.value.files)
        model.selectFiles(SheetFileSelection.Selected(listOf(pdf, pdf)))
        assertEquals("INVALID_FILE_SET", model.uiState.value.error)
        model.selectFiles(SheetFileSelection.Failed)
        assertEquals("PICK_FAILED", model.uiState.value.error)
    }

    @Test fun cancelledPickerPreservesFilesAndAccountSwitchClearsPrivateInput() = runTest {
        val member = SignedInMember(FakeKeyValueStore().apply { putString("auth.member_id", "1") })
        val response = CompletableDeferred<SheetImportResult>()
        var sent = 0
        val repository = object : SheetImportRepository {
            override suspend fun upload(input: SheetImportInput, key: String): SheetImportResult { sent++; return response.await() }
        }
        val model = keep(SheetImportViewModel(ImportKind.Staff, HwangPitch.Eb4, repository, { instruments }, { jangdans }, member))
        runCurrent(); model.submit()
        model.selectFiles(SheetFileSelection.Selected(listOf(pdf))); model.instrument("4"); model.title("제목")
        model.selectFiles(SheetFileSelection.Cancelled)
        assertEquals(listOf(pdf), model.uiState.value.files)
        model.submit(); runCurrent(); assertEquals(1, sent)
        member.set("2"); runCurrent()
        response.complete(SheetImportResult.Accepted("91")); runCurrent()
        assertNull(model.uiState.value.acceptedId)
        assertTrue(model.uiState.value.files.isEmpty())
        assertFalse(model.uiState.value.validationRequested)
        assertTrue(model.uiState.value.inputErrors(ImportKind.Staff).isEmpty())
        model.selectFiles(SheetFileSelection.Selected(listOf(pdf))); model.submit(); runCurrent()
        assertEquals(1, sent)
    }

    @Test fun switchingKindOnUploadScreenChangesNotationAndJangdanRequirement() = runTest {
        val sent = mutableListOf<SheetImportInput>()
        val repository = object : SheetImportRepository {
            override suspend fun upload(input: SheetImportInput, key: String): SheetImportResult {
                sent += input
                return SheetImportResult.Accepted("7")
            }
        }
        val model = keep(SheetImportViewModel(ImportKind.Jeongganbo, HwangPitch.Eb4, repository, { instruments }, { jangdans }))
        runCurrent()
        assertEquals(ImportKind.Jeongganbo, model.uiState.value.kind)
        model.title("곡"); model.instrument("4"); model.selectFiles(SheetFileSelection.Selected(listOf(pdf)))
        model.submit(); runCurrent()
        assertEquals(setOf(SheetImportField.Jangdan), model.uiState.value.inputErrors(model.uiState.value.kind))
        assertTrue(sent.isEmpty())

        model.kind(ImportKind.Staff)
        assertEquals(ImportKind.Staff, model.uiState.value.kind)
        assertTrue(model.uiState.value.inputErrors(model.uiState.value.kind).isEmpty())
        model.submit(); runCurrent()
        assertEquals("STAFF", sent.single().notation)
        assertNull(sent.single().jangdanId)
        assertEquals("Eb4", sent.single().hwangPitch)
    }

    @Test fun jangdanCatalogLoadsForStaffStartSoSwitchingToJeongganboCanChoose() = runTest {
        val model = keep(SheetImportViewModel(ImportKind.Staff, HwangPitch.Default, object : SheetImportRepository {
            override suspend fun upload(input: SheetImportInput, key: String) = SheetImportResult.Accepted("1")
        }, { instruments }, { jangdans }))
        runCurrent()
        assertEquals(listOf(guguri, semachi), model.uiState.value.jangdans)
        model.kind(ImportKind.Jeongganbo)
        model.jangdan("2")
        assertEquals("2", model.uiState.value.jangdanId)
    }

    @Test fun fourBeatAccompanimentIsHiddenAndCannotBeChosenForJeongganboUpload() = runTest {
        val model = keep(SheetImportViewModel(ImportKind.Jeongganbo, HwangPitch.Default, object : SheetImportRepository {
            override suspend fun upload(input: SheetImportInput, key: String) = SheetImportResult.Accepted("1")
        }, { instruments }, { jangdans }))
        runCurrent()
        assertEquals(listOf("2", "3"), model.uiState.value.jangdans.map { it.jangdanId })
        model.jangdan("12")
        assertNull(model.uiState.value.jangdanId)
    }

    @Test fun unsureUploadsRecommendedGuguriAndExplicitChoiceClearsIt() = runTest {
        val sent = mutableListOf<SheetImportInput>()
        val model = keep(SheetImportViewModel(ImportKind.Jeongganbo, HwangPitch.Default, object : SheetImportRepository {
            override suspend fun upload(input: SheetImportInput, key: String): SheetImportResult { sent += input; return SheetImportResult.Failed() }
        }, { instruments }, { jangdans }))
        runCurrent()
        model.title("곡"); model.instrument("4"); model.selectFiles(SheetFileSelection.Selected(listOf(pdf)))
        model.jangdanUnsure()
        assertTrue(model.uiState.value.jangdanUnsure)
        assertTrue(model.uiState.value.inputErrors().isEmpty())
        model.submit(); runCurrent()
        assertEquals("2", sent.single().jangdanId)
        model.jangdan("3")
        assertFalse(model.uiState.value.jangdanUnsure)
        // 추천과 같은 굿거리를 직접 고르면 '잘 모르겠어요' 표시만 풀린다.
        model.jangdanUnsure(); model.jangdan("2")
        assertEquals("2", model.uiState.value.jangdanId)
        assertFalse(model.uiState.value.jangdanUnsure)
    }

    @Test fun unsureIsUnavailableWhenCatalogHasNoGuguri() = runTest {
        val model = keep(SheetImportViewModel(ImportKind.Jeongganbo, HwangPitch.Default, object : SheetImportRepository {
            override suspend fun upload(input: SheetImportInput, key: String) = SheetImportResult.Accepted("1")
        }, { instruments }, { listOf(semachi) }))
        runCurrent()
        assertFalse(model.uiState.value.canChooseJangdanUnsure)
        model.jangdanUnsure()
        assertNull(model.uiState.value.jangdanId)
        assertFalse(model.uiState.value.jangdanUnsure)
    }
}
