package com.ssafy.eolssu.feature.guide

import com.ssafy.eolssu.core.model.fingering.Blow
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.data.song.ReferenceAudioClip
import com.ssafy.eolssu.data.song.ReferenceAudioQuery
import com.ssafy.eolssu.data.song.ReferenceAudioRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReferenceAudioViewModelTest {
    @Test fun fourInstrumentIdsAndRegisterLabelsAreExplicit() {
        val expected = listOf("danso", "sogeum", "sanjo-daegeum", "jeongak-daegeum")
        assertEquals(expected, GuideInstrument.entries.map { ReferenceAudioSelection(it, null, "중").datasetId })
        val jeongak = GuideInstrument.DaegeumJeongak
        assertEquals(listOf("저취", "평취", "역취"), Blow.entries.map {
            ReferenceAudioSelection.forNote(jeongak, jeongak.notesByPitch(it).first(), it).register
        })
    }

    @Test fun notePrefixesArePreservedAndLearningUsesTheActualNoteRow() {
        val sogeum = GuideInstrument.Sogeum
        val note = sogeum.notesByPitch(Blow.High).first { it.reading == "중청임" }
        val selection = ReferenceAudioSelection.forNote(sogeum, note)
        assertEquals("역취", selection.register)
        assertEquals("중청임", selection.yulmyeong)
        val danso = GuideInstrument.Danso
        val highDanso = ReferenceAudioSelection.forNote(danso, danso.notesByPitch(Blow.High).first())
        assertEquals("중", highDanso.yulmyeong)
        assertEquals("청중", highDanso.displayYulmyeong)
    }

    @Test fun documentedInstrumentAndBlowAliasesFindOriginalRecordingLabels() {
        fun selection(instrument: GuideInstrument, blow: Blow, reading: String) = ReferenceAudioSelection.forNote(
            instrument, instrument.notesByPitch(blow).single { it.reading == reading }, blow)
        assertEquals("중", selection(GuideInstrument.Danso, Blow.High, "청중").yulmyeong)
        assertEquals("임", selection(GuideInstrument.DaegeumJeongak, Blow.Low, "탁임").yulmyeong)
        assertEquals("황", selection(GuideInstrument.DaegeumSanjo, Blow.Mid, "청황").yulmyeong)
        assertEquals("황", selection(GuideInstrument.DaegeumJeongak, Blow.High, "중청황").yulmyeong)
    }

    @Test fun sogeumHighImRemainsDistinctAndUnknownHighNamIsNotGuessed() {
        val instrument = GuideInstrument.Sogeum
        fun selection(reading: String) = ReferenceAudioSelection.forNote(instrument,
            instrument.notesByPitch(Blow.High).single { it.reading == reading }, Blow.High)
        assertEquals("임", selection("청임").yulmyeong)
        assertEquals("중청임", selection("중청임").yulmyeong)
        assertEquals("중청남", selection("중청남").yulmyeong)
    }

    @Test fun eachNotePlaysTheStrongBasicToneAndPlainYoseongFromTheActualRecordingNames() {
        // 2026-11-04 운영 /api/reference-audios 응답의 악기별 같은 음 녹음 이름이다.
        fun pick(dataset: String, vararg techniques: String): Pair<String?, String?> {
            val state = ReferenceAudioUiState(clips = techniques.mapIndexed { index, technique ->
                clip(dataset).copy(assetId = "$index".repeat(64), technique = technique)
            })
            val selection = ReferenceAudioSelection(GuideInstrument.entries.single {
                ReferenceAudioSelection(it, null, "").datasetId == dataset
            }, "평취", "중")
            return state.clipFor(selection, ReferenceAudioKind.Basic)?.technique to
                state.clipFor(selection, ReferenceAudioKind.Yoseong)?.technique
        }
        assertEquals("평취 강" to "음계+요성", pick("danso", "평취 약", "평취 중", "평취 강", "음계+요성"))
        assertEquals("역취" to "음계+요성", pick("danso", "역취", "음계+요성"))
        assertEquals("기본음 강" to "요성 강", pick("sogeum", "기본음 약", "기본음 강", "요성 중", "요성 강", "깊은요성"))
        assertEquals("기본음 강" to "요성 중", pick("sogeum", "기본음 강", "요성 중", "요성 약"))
        assertEquals("기본음" to "기본음+요성", pick("jeongak-daegeum", "기본음+깊은요성", "기본음+요성", "기본음"))
        assertEquals(null to "기본음+요성", pick("sanjo-daegeum", "기본음+요성", "기본음+깊은요성"))
        assertEquals("기본음 강" to null, pick("sogeum", "기본음 강", "깊은요성"))
    }

    @Test fun examplesComeOnlyFromTheExactNoteAndBasicScaleRecordings() {
        val basic = clip("danso")
        val state = ReferenceAudioUiState(clips = listOf(
            basic.copy(assetId = "b".repeat(64), category = "시김새", technique = "요성(황종)"),
            basic.copy(assetId = "c".repeat(64), register = "역취"),
            basic.copy(assetId = "d".repeat(64), yulmyeong = "황", technique = "음계+요성"),
        ))
        val selection = ReferenceAudioSelection(GuideInstrument.Danso, "평취", "중")
        assertNull(state.clipFor(selection, ReferenceAudioKind.Basic))
        assertNull(state.clipFor(selection, ReferenceAudioKind.Yoseong))
        assertEquals("d".repeat(64), state.clipFor(selection.copy(yulmyeong = "황"), ReferenceAudioKind.Yoseong)?.assetId)
        assertNull(state.clipFor(selection.copy(instrument = GuideInstrument.Sogeum, register = "역취"), ReferenceAudioKind.Basic))
    }

    @Test fun reopeningTheSameInstrumentReusesItsList() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var requests = 0
        val vm = ReferenceAudioViewModel(repository { requests++; listOf(clip(it.datasetId)) })
        try {
            vm.open(GuideInstrument.Danso); advanceUntilIdle()
            vm.open(GuideInstrument.Danso)
            assertEquals(ReferenceLoadState.Ready, vm.uiState.value.loadState)
            assertEquals(listOf(clip("danso")), vm.uiState.value.clips)
            advanceUntilIdle()
            assertEquals(1, requests)
        } finally { vm.dismiss(); Dispatchers.resetMain() }
    }

    @Test fun reopeningWhileLoadingAndRepeatedRetryKeepTheOriginalRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pending = CompletableDeferred<Unit>()
        var requests = 0
        val vm = ReferenceAudioViewModel(repository { requests++; pending.await(); listOf(clip(it.datasetId)) })
        try {
            vm.open(GuideInstrument.Danso); runCurrent()
            vm.open(GuideInstrument.Danso); vm.retry(); runCurrent()
            assertEquals(1, requests)
            pending.complete(Unit); advanceUntilIdle()
            assertEquals(ReferenceLoadState.Ready, vm.uiState.value.loadState)
            assertEquals(listOf(clip("danso")), vm.uiState.value.clips)
        } finally { pending.complete(Unit); vm.dismiss(); Dispatchers.resetMain() }
    }

    @Test fun returningToLoadedInstrumentsReusesSuccessfulAndEmptyLists() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val requests = mutableListOf<String>()
        val vm = ReferenceAudioViewModel(repository {
            requests += it.datasetId
            if (it.datasetId == "sogeum") emptyList() else listOf(clip(it.datasetId))
        })
        try {
            vm.open(GuideInstrument.Danso); advanceUntilIdle()
            vm.open(GuideInstrument.Sogeum); advanceUntilIdle()
            vm.open(GuideInstrument.Danso)
            assertEquals(listOf(clip("danso")), vm.uiState.value.clips)
            vm.open(GuideInstrument.Sogeum)
            assertEquals(ReferenceLoadState.Ready, vm.uiState.value.loadState)
            assertTrue(vm.uiState.value.clips.isEmpty())
            advanceUntilIdle()
            assertEquals(listOf("danso", "sogeum"), requests)
        } finally { vm.dismiss(); Dispatchers.resetMain() }
    }

    @Test fun explicitRetryRefreshesTheListAndDismissEndsItsLifetime() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var requests = 0
        val vm = ReferenceAudioViewModel(repository {
            requests++
            listOf(clip(it.datasetId).copy(labelId = "revision-$requests"))
        })
        try {
            vm.open(GuideInstrument.Danso); advanceUntilIdle()
            vm.retry(); advanceUntilIdle()
            assertEquals("revision-2", vm.uiState.value.clips.single().labelId)
            vm.open(GuideInstrument.Sogeum); advanceUntilIdle()
            vm.open(GuideInstrument.Danso)
            assertEquals("revision-2", vm.uiState.value.clips.single().labelId)
            vm.dismiss()
            vm.open(GuideInstrument.Danso); advanceUntilIdle()
            assertEquals("revision-4", vm.uiState.value.clips.single().labelId)
            assertEquals(4, requests)
        } finally { vm.dismiss(); Dispatchers.resetMain() }
    }

    @Test fun failedListsAreNotReusedWhenReturningToAnInstrument() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val requests = mutableListOf<String>()
        val vm = ReferenceAudioViewModel(repository {
            requests += it.datasetId
            if (requests.size == 1) error("offline")
            listOf(clip(it.datasetId))
        })
        try {
            vm.open(GuideInstrument.Danso); advanceUntilIdle()
            assertEquals(ReferenceLoadState.Failed, vm.uiState.value.loadState)
            vm.open(GuideInstrument.Sogeum); advanceUntilIdle()
            vm.open(GuideInstrument.Danso); advanceUntilIdle()
            assertEquals(ReferenceLoadState.Ready, vm.uiState.value.loadState)
            assertEquals(listOf("danso", "sogeum", "danso"), requests)
        } finally { vm.dismiss(); Dispatchers.resetMain() }
    }

    @Test fun failedRequestCanRetryAndKeepsTheInstrument() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var failed = true
        val vm = ReferenceAudioViewModel(repository { if (failed) error("offline"); emptyList() })
        try {
            val instrument = GuideInstrument.Danso
            vm.open(instrument)
            advanceUntilIdle()
            assertEquals(ReferenceLoadState.Failed, vm.uiState.value.loadState)
            failed = false
            vm.retry()
            assertEquals(ReferenceLoadState.Loading, vm.uiState.value.loadState)
            advanceUntilIdle()
            assertEquals(ReferenceLoadState.Ready, vm.uiState.value.loadState)
            assertEquals(instrument, vm.uiState.value.instrument)
        } finally { vm.dismiss(); Dispatchers.resetMain() }
    }

    @Test fun oldResultCannotReplaceAChangedInstrumentEvenIfTransportFinishesAfterCancellation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val old = CompletableDeferred<Unit>()
        val vm = ReferenceAudioViewModel(repository { query ->
            if (query.datasetId == "danso") withContext(NonCancellable) { old.await() }
            listOf(clip(query.datasetId))
        })
        try {
            vm.open(GuideInstrument.Danso); runCurrent()
            vm.open(GuideInstrument.Sogeum); runCurrent()
            assertEquals("sogeum", vm.uiState.value.clips.single().datasetId)
            old.complete(Unit); advanceUntilIdle()
            assertEquals("sogeum", vm.uiState.value.clips.single().datasetId)
        } finally { old.complete(Unit); vm.dismiss(); Dispatchers.resetMain() }
    }

    @Test fun dismissCancelsLoadingAndClearsSelection() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pending = CompletableDeferred<Unit>()
        val vm = ReferenceAudioViewModel(repository { pending.await(); listOf(clip("danso")) })
        try {
            vm.open(GuideInstrument.Danso); runCurrent()
            vm.dismiss(); pending.complete(Unit); advanceUntilIdle()
            assertNull(vm.uiState.value.instrument)
            assertTrue(vm.uiState.value.clips.isEmpty())
        } finally { vm.dismiss(); Dispatchers.resetMain() }
    }

    private fun repository(load: suspend (ReferenceAudioQuery) -> List<ReferenceAudioClip>) = object : ReferenceAudioRepository {
        override suspend fun clips(query: ReferenceAudioQuery) = load(query)
    }
    private fun clip(dataset: String) = ReferenceAudioClip("a".repeat(64), dataset, "source-a", "기본음계", "기본음", "평취", "중", "중", 1200, "inferred", "https://example.test/play")
}
