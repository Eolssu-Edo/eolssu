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
    private val jangdans = listOf(CatalogJangdanSummary("12", "굿거리", 60, "QUARTER", 12, 4))
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
        model.jangdan("12")
        assertEquals(setOf(SheetImportField.Files), errors())
        model.selectFiles(SheetFileSelection.Selected(listOf(pdf)))
        assertTrue(errors().isEmpty())
        model.submit(); runCurrent()
        assertEquals(1, uploads)
        assertEquals("91", model.uiState.value.acceptedId)
        assertTrue(errors().isEmpty())
    }

    @Test fun reimportReplacesTheFailedSheetOnlyAfterTheNewUploadIsAccepted() = runTest {
        val failed = com.ssafy.eolssu.core.model.MySheet("7", com.ssafy.eolssu.core.model.MySheetOrigin.Imported, "강원도 아리랑",
            updatedAt = com.ssafy.eolssu.core.model.KoreanDateTime(2026, 11, 16, 9, 0), conversionStatus = "FAILED")
        val deleted = mutableListOf<String>()
        val sheets = object : MySheetRepository {
            override val sheets = kotlinx.coroutines.flow.MutableStateFlow(listOf(failed))
            override suspend fun delete(sheetId: String) { deleted += sheetId }
        }
        var reply: SheetImportResult = SheetImportResult.Failed("UPLOAD_FAILED")
        val repository = object : SheetImportRepository {
            override suspend fun upload(input: SheetImportInput, key: String) = reply
        }
        val model = keep(SheetImportViewModel(ImportKind.Staff, HwangPitch.Default, repository, { instruments }, { jangdans },
            replacing = "7", mySheets = sheets))
        runCurrent()
        assertEquals("강원도 아리랑", model.uiState.value.title)
        model.instrument("4")
        model.selectFiles(SheetFileSelection.Selected(listOf(pdf)))
        assertEquals("강원도 아리랑", model.uiState.value.title)
        model.submit(); runCurrent()
        assertTrue(deleted.isEmpty())
        reply = SheetImportResult.Accepted("91")
        model.submit(); runCurrent()
        assertEquals(listOf("7"), deleted)
        assertEquals("91", model.uiState.value.acceptedId)
    }

    @Test fun aFullListMakesRoomByDeletingTheFailedSheetAndUploadsOnceMore() = runTest {
        val failed = com.ssafy.eolssu.core.model.MySheet("7", com.ssafy.eolssu.core.model.MySheetOrigin.Imported, "강원도 아리랑",
            updatedAt = com.ssafy.eolssu.core.model.KoreanDateTime(2026, 11, 16, 9, 0), conversionStatus = "FAILED")
        val deleted = mutableListOf<String>()
        val sheets = object : MySheetRepository {
            override val sheets = kotlinx.coroutines.flow.MutableStateFlow(listOf(failed))
            override suspend fun delete(sheetId: String) { deleted += sheetId }
        }
        val keys = mutableListOf<String>()
        val repository = object : SheetImportRepository {
            override suspend fun upload(input: SheetImportInput, key: String): SheetImportResult {
                keys += key
                return if (deleted.isEmpty()) SheetImportResult.Failed("SHEET_LIMIT_EXCEEDED") else SheetImportResult.Accepted("91")
            }
        }
        val model = keep(SheetImportViewModel(ImportKind.Staff, HwangPitch.Default, repository, { instruments }, { jangdans },
            replacing = "7", mySheets = sheets))
        runCurrent()
        model.instrument("4")
        model.selectFiles(SheetFileSelection.Selected(listOf(pdf)))
        model.submit(); runCurrent()
        assertEquals(listOf("7"), deleted)
        assertEquals(2, keys.size)
        assertNotEquals(keys[0], keys[1])
        assertEquals("91", model.uiState.value.acceptedId)
        assertNull(model.uiState.value.error)
    }

    @Test fun aFullListWithoutAFailedSheetToReplaceStillShowsTheLimit() = runTest {
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
                assertEquals("12", input.jangdanId); assertNull(input.hwangPitch)
                keys += key
                return response.await()
            }
        }
        val model = keep(SheetImportViewModel(ImportKind.Jeongganbo, HwangPitch.Default, repository, { instruments }, { jangdans }))
        runCurrent()
        model.selectFiles(SheetFileSelection.Selected(listOf(pdf)))
        model.title("곡"); model.instrument("4")
        model.submit(); runCurrent(); assertTrue(keys.isEmpty())
        model.jangdan("12"); model.submit(); model.submit(); runCurrent()
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
        assertEquals(jangdans, model.uiState.value.jangdans)
        model.kind(ImportKind.Jeongganbo)
        model.jangdan("12")
        assertEquals("12", model.uiState.value.jangdanId)
    }
}
