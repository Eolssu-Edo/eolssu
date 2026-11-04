package com.ssafy.eolssu.testing

import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.song.SampleMySheetRepository
import com.ssafy.eolssu.data.song.SampleSongRepository
import com.ssafy.eolssu.data.user.LearnProgressRepository
import com.ssafy.eolssu.data.user.SampleUserRepository
import com.ssafy.eolssu.data.user.UserRepository
import com.ssafy.eolssu.feature.guide.learn.LearnViewModel
import com.ssafy.eolssu.feature.profile.NicknameEditViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun deletedSheetDoesNotCarryOverToNextAccount() = runTest {
        val sheets = SampleMySheetRepository(member = member)
        val first = sheets.sheets.value.first().id
        sheets.delete(first)
        assertEquals(1, sheets.sheets.value.size)
        member.set("B")
        // 다음 계정은 지운 내용 없이 예시 악보로 다시 시작한다(서버 API 연결 전).
        assertEquals(2, sheets.sheets.value.size)
        assertEquals(first, sheets.sheets.value.first().id)
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
