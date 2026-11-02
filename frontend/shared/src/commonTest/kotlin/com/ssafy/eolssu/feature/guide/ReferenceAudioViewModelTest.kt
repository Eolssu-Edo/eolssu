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
        val expected = listOf("danso", "sogeum", "jeongak-daegeum", "sanjo-daegeum")
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

    @Test fun optionsCycleOnlyWithinTheExactNoteWithoutAnAutomaticSelection() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val requests = mutableListOf<ReferenceAudioQuery>()
        val basic = clip("danso")
        val ornament = basic.copy(assetId = "b".repeat(64), category = "시김새", technique = "요성")
        val vm = ReferenceAudioViewModel(repository {
            requests += it
            listOf(basic, ornament, basic.copy(assetId = "c".repeat(64), register = "역취"),
                basic.copy(assetId = "d".repeat(64), yulmyeong = "황"))
        })
        val selection = ReferenceAudioSelection(GuideInstrument.Danso, "평취", "중")
        try {
            vm.open(GuideInstrument.Danso); advanceUntilIdle()
            assertEquals(listOf(ReferenceAudioQuery("danso")), requests)
            assertEquals(listOf(basic, ornament), vm.uiState.value.clipsFor(selection))
            assertNull(vm.uiState.value.selectedClip(selection))
            vm.nextOption(selection)
            assertEquals(basic, vm.uiState.value.selectedClip(selection))
            vm.nextOption(selection)
            assertEquals(ornament, vm.uiState.value.selectedClip(selection))
            vm.nextOption(selection)
            assertEquals(basic, vm.uiState.value.selectedClip(selection))
            assertEquals(1, requests.size)
            val missing = selection.copy(yulmyeong = "태")
            vm.nextOption(missing)
            assertNull(vm.uiState.value.selectedClip(missing))
            assertTrue(vm.uiState.value.clipsFor(selection.copy(instrument = GuideInstrument.Sogeum)).isEmpty())
        } finally { vm.dismiss(); Dispatchers.resetMain() }
    }

    @Test fun choicesStayWithTheirCardAndChangingInstrumentClearsThem() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = ReferenceAudioViewModel(repository { query -> listOf(clip(query.datasetId),
            clip(query.datasetId).copy(assetId = "b".repeat(64), yulmyeong = "황")) })
        val jung = ReferenceAudioSelection(GuideInstrument.Danso, "평취", "중")
        val hwang = jung.copy(yulmyeong = "황")
        try {
            vm.open(GuideInstrument.Danso); advanceUntilIdle()
            vm.nextOption(jung); vm.nextOption(hwang)
            assertEquals("a".repeat(64), vm.uiState.value.selectedClip(jung)?.assetId)
            assertEquals("b".repeat(64), vm.uiState.value.selectedClip(hwang)?.assetId)
            vm.open(GuideInstrument.Sogeum); advanceUntilIdle()
            assertTrue(vm.uiState.value.selectedAssets.isEmpty())
            assertTrue(vm.uiState.value.clipsFor(jung).isEmpty())
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
