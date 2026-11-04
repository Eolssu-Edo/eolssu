package com.ssafy.eolssu.testing

import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.song.SampleMySheetRepository
import com.ssafy.eolssu.data.song.SamplePdfFile
import com.ssafy.eolssu.data.song.SampleSongRepository
import com.ssafy.eolssu.data.user.LearnProgressRepository
import com.ssafy.eolssu.data.user.SampleUserRepository
import com.ssafy.eolssu.data.user.UserRepository
import com.ssafy.eolssu.feature.guide.learn.LearnViewModel
import com.ssafy.eolssu.feature.profile.NicknameEditViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class AccountStateIsolationTest : CatalogViewModelTest() {
    private val store = FakeKeyValueStore()
    private val member = SignedInMember(store).apply { set("A") }

    @Test
    fun progressIsOwnedAndLegacyValuesArePreservedUnassigned() {
        store.putString("learn_done_danso", "1,2")
        val progress = LearnProgressRepository(store, member)
        assertEquals(emptySet(), progress.doneStages("danso"))
        progress.saveDoneStages("danso", setOf(3))
        member.set("B")
        assertEquals(emptySet(), progress.doneStages("danso"))
        progress.saveDoneStages("danso", setOf(4))
        member.set("A")
        assertEquals(setOf(3), progress.doneStages("danso"))
        assertEquals("1,2", store.getString("learn_done_danso"))
        member.set(null)
        progress.saveDoneStages("danso", setOf(9))
        assertEquals(emptySet(), progress.doneStages("danso"))
    }

    @Test
    fun previousLearningScreenCannotWriteIntoNextAccount() = runTest {
        val progress = LearnProgressRepository(store, member)
        val model = keep(LearnViewModel(SampleSongRepository(), progress, member))
        runCurrent()
        model.toggleDone(1)
        assertTrue(model.uiState.value.doneStages.isNotEmpty())
        member.set("B")
        model.toggleDone(2)
        assertEquals(emptySet(), model.uiState.value.doneStages)
        assertEquals(emptySet(), progress.doneStages(model.uiState.value.instrument.id))
    }

    @Test
    fun interruptedPdfUploadCannotReportProgressOrLeaveAnUploadForNextLogin() = runTest {
        val sheets = SampleMySheetRepository({ 0L }, uploadStepMillis = 1, member = member)
        var progress = 0
        val upload = async { runCatching { sheets.uploadPdf(SamplePdfFile) { progress++ } } }
        runCurrent(); advanceTimeBy(1); runCurrent()
        assertEquals(1, progress)
        member.set("B")
        advanceUntilIdle()
        assertTrue(upload.await().isFailure)
        assertEquals(1, progress)
        assertNull(sheets.getUploadedPdf("upload-1"))
        assertEquals(0, sheets.sheets.value.size)
    }

    @Test
    fun completedPdfDataIsClearedWhileSameSessionUploadAndSaveWork() = runTest {
        val sheets = SampleMySheetRepository({ 0L }, uploadStepMillis = 0, member = member)
        val upload = sheets.uploadPdf(SamplePdfFile) {}
        sheets.savePdf(upload.id, "A의 악보", null)
        assertEquals(1, sheets.sheets.value.size)
        val pending = sheets.uploadPdf(SamplePdfFile) {}
        member.set("B")
        assertEquals(0, sheets.sheets.value.size)
        assertNull(sheets.getUploadedPdf(pending.id))
    }

    @Test
    fun oldNicknameScreenCannotSubmitEvenAfterSameMemberLogsInAgain() = runTest {
        var writes = 0
        val users = object : UserRepository by SampleUserRepository() {
            override suspend fun changeNickname(nickname: String) = com.ssafy.eolssu.data.user.MemberResult.Done.also { writes++ }
        }
        val model = keep(NicknameEditViewModel(users, member))
        model.changeNickname("회원 A")
        assertTrue(model.save())
        assertEquals(1, writes)
        member.set("B"); member.set("A")
        assertEquals("", model.uiState.value.nickname)
        model.changeNickname("늦은 저장")
        assertTrue(runCatching { model.save() }.isFailure)
        assertEquals(1, writes)
    }
}
