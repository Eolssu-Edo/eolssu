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

    @Test fun cancelledPickerPreservesFilesAndAccountSwitchClearsPrivateInput() = runTest {
        val member = SignedInMember(FakeKeyValueStore().apply { putString("auth.member_id", "1") })
        val response = CompletableDeferred<SheetImportResult>()
        var sent = 0
        val repository = object : SheetImportRepository {
            override suspend fun upload(input: SheetImportInput, key: String): SheetImportResult { sent++; return response.await() }
        }
        val model = keep(SheetImportViewModel(ImportKind.Staff, HwangPitch.Eb4, repository, { instruments }, { jangdans }, member))
        runCurrent(); model.selectFiles(SheetFileSelection.Selected(listOf(pdf))); model.instrument("4"); model.title("제목")
        model.selectFiles(SheetFileSelection.Cancelled)
        assertEquals(listOf(pdf), model.uiState.value.files)
        model.submit(); runCurrent(); assertEquals(1, sent)
        member.set("2"); runCurrent()
        response.complete(SheetImportResult.Accepted("91")); runCurrent()
        assertNull(model.uiState.value.acceptedId)
        assertTrue(model.uiState.value.files.isEmpty())
        model.selectFiles(SheetFileSelection.Selected(listOf(pdf))); model.submit(); runCurrent()
        assertEquals(1, sent)
    }
}
