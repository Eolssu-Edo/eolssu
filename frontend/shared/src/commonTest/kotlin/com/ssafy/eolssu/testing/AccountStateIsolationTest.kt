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
    fun deviceProgressIsUploadedOnlyToItsOwnerAndLegacyValuesStayUnassigned() = runTest {
        val api = FakeLessonApi(owner = { member.id })
        store.putString("learn_done_danso", "1,2")
        store.putString("learn_done_member_1_A_danso", "3")
        val progress = LearnProgressRepository(api, store, member)

        progress.track("danso")
        assertEquals(setOf(3), api.doneSeqs("danso", "A"))
        member.set("B")
        progress.track("danso")
        assertEquals(emptySet(), api.doneSeqs("danso", "B"), "다른 계정이 남긴 기기 진도를 올리지 않는다")
        assertEquals("1,2", store.getString("learn_done_danso"))
    }

    @Test
    fun previousLearningScreenCannotWriteIntoNextAccount() = runTest {
        val api = FakeLessonApi(owner = { member.id })
        val progress = LearnProgressRepository(api, store, member)
        val model = keep(LearnViewModel(SampleSongRepository(), progress, member))
        runCurrent()
        model.toggleDone(1)
        runCurrent()
        assertTrue(model.uiState.value.doneStages.isNotEmpty())
        assertEquals(setOf(1), api.doneSeqs("danso", "A"))
        member.set("B")
        model.toggleDone(2)
        runCurrent()
        assertEquals(emptySet(), model.uiState.value.doneStages)
        assertEquals(emptySet(), api.doneSeqs("danso", "B"))
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
