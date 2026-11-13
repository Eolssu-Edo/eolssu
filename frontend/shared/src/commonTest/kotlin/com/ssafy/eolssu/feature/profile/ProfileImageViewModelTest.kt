package com.ssafy.eolssu.feature.profile

import com.ssafy.eolssu.data.user.MemberResult
import com.ssafy.eolssu.core.model.ProfileImage
import com.ssafy.eolssu.data.user.SampleUserRepository
import com.ssafy.eolssu.data.user.UserRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 마이페이지 프로필 그림 고르기(S15P31E102-738). */
class ProfileImageViewModelTest {
    @Test
    fun startsWithCurrentImageAndSavesTheChosenOne() = runTest {
        val users = SampleUserRepository()
        val viewModel = ProfileImageViewModel(users)
        assertEquals(ProfileImage.Hello, viewModel.uiState.value.selected)
        assertEquals(12, ProfileImage.entries.size)

        // 고르지 않고 저장하면 요청 없이 그대로 돌아간다.
        assertEquals(ProfileImageSave.Unchanged, viewModel.save())

        viewModel.select(ProfileImage.Rest)
        assertEquals(ProfileImage.Rest, viewModel.uiState.value.selected)
        assertEquals(ProfileImageSave.Saved, viewModel.save())
        assertEquals(ProfileImage.Rest, users.profile.value.profileImage)
    }

    @Test
    fun failedSaveKeepsSelectionAndShowsError() = runTest {
        var result: MemberResult = MemberResult.Unavailable
        val users = object : UserRepository by SampleUserRepository() {
            override suspend fun changeProfileImage(image: ProfileImage) = result
        }
        val viewModel = ProfileImageViewModel(users)
        viewModel.select(ProfileImage.Done)

        assertEquals(ProfileImageSave.Failed, viewModel.save())
        assertEquals(ProfileFieldError.Unavailable, viewModel.uiState.value.error)
        assertEquals(ProfileImage.Done, viewModel.uiState.value.selected)

        result = MemberResult.Unauthenticated
        assertEquals(ProfileImageSave.Failed, viewModel.save())
        assertEquals(ProfileFieldError.Unauthenticated, viewModel.uiState.value.error)

        // 다시 고르면 안내를 지운다.
        viewModel.select(ProfileImage.Okay)
        assertNull(viewModel.uiState.value.error)
    }
}
