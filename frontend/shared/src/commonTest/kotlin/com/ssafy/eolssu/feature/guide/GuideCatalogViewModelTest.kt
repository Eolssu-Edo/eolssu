package com.ssafy.eolssu.feature.guide

import com.ssafy.eolssu.core.network.ServerAddress
import com.ssafy.eolssu.core.network.createHttpClient
import com.ssafy.eolssu.data.song.*
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingering.Blow

@OptIn(ExperimentalCoroutinesApi::class)
class GuideCatalogViewModelTest {
    @Test fun serverListControlsOptionsNamesOrderAndSelectionWithoutReloadingOnSwitch() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var loads = 0
        val client = createHttpClient(MockEngine {
            loads++
            respond("""{"isSuccess":true,"result":{"items":[{"instrumentId":"94","code":"SANJO_DAEGEUM","name":"서버 산조대금"},{"instrumentId":"999","code":"DAEGEUM","name":"구형 대금"},{"instrumentId":"42","code":"DANSO","name":"서버 단소"},{"instrumentId":"555","code":"FUTURE","name":"새 악기"}]}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        try {
            val references = StubReferences()
            val vm = FingeringViewModel(instruments = InstrumentCatalog(client), references = references)
            assertFalse(vm.uiState.value.hasSelectedInstrument)
            vm.uiState.first { it.catalogState != ReferenceLoadState.Loading }
            assertEquals(listOf("서버 단소", "서버 산조대금"), vm.uiState.value.instrumentOptions.map { it.name })
            assertEquals(listOf("42", "94"), vm.uiState.value.instrumentOptions.map { it.instrumentId })
            assertEquals(GuideInstrument.Danso, vm.uiState.value.instrument)
            assertTrue(vm.uiState.value.hasSelectedInstrument)
            vm.selectInstrument(GuideInstrument.DaegeumSanjo)
            vm.uiState.first { it.catalogState != ReferenceLoadState.Loading }
            vm.selectInstrument(GuideInstrument.Sogeum)
            advanceUntilIdle()
            assertEquals(GuideInstrument.DaegeumSanjo, vm.uiState.value.instrument)
            assertEquals(listOf("42", "94"), references.pitchRequests)
            assertEquals(1, loads)
        } finally { client.close(); Dispatchers.resetMain() }
    }

    @Test fun failedInstrumentListRetriesThenShowsEmptyWithoutLocalFallback() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var available = false
        var loads = 0
        val client = createHttpClient(MockEngine {
            loads++
            if (!available) respond("", io.ktor.http.HttpStatusCode.ServiceUnavailable)
            else respond("""{"isSuccess":true,"result":{"items":[{"instrumentId":"88","code":"DAEGEUM","name":"대금"}]}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        try {
            val references = StubReferences()
            val vm = FingeringViewModel(instruments = InstrumentCatalog(client), references = references)
            vm.uiState.first { it.catalogState != ReferenceLoadState.Loading }
            assertEquals(ReferenceLoadState.Failed, vm.uiState.value.instrumentListState)
            assertFalse(vm.uiState.value.hasSelectedInstrument)
            vm.toggleDropdown()
            assertFalse(vm.uiState.value.isDropdownOpen)
            available = true
            vm.retryCatalog(); vm.retryCatalog()
            vm.uiState.first { it.catalogState != ReferenceLoadState.Loading }
            assertEquals(ReferenceLoadState.Ready, vm.uiState.value.instrumentListState)
            assertTrue(vm.uiState.value.instrumentOptions.isEmpty())
            assertFalse(vm.uiState.value.hasSelectedInstrument)
            assertTrue(references.pitchRequests.isEmpty())
            assertEquals(2, loads)
        } finally { client.close(); Dispatchers.resetMain() }
    }

    @Test fun explicitDaegeumVariantsUseTheirOwnServerIdentities() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val client = createHttpClient(MockEngine {
            respond("""{"isSuccess":true,"result":{"items":[{"instrumentId":"93","code":"JEONGAK_DAEGEUM","name":"정악대금"},{"instrumentId":"94","code":"SANJO_DAEGEUM","name":"산조대금"}]}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }, ServerAddress.Production)
        try {
            for ((instrument,id) in listOf(GuideInstrument.DaegeumJeongak to "93", GuideInstrument.DaegeumSanjo to "94")) {
                val references = StubReferences()
                val vm = FingeringViewModel(instrument, instruments = InstrumentCatalog(client), references = references)
                vm.uiState.first { it.catalogState != ReferenceLoadState.Loading }
                assertEquals(ReferenceLoadState.Ready, vm.uiState.value.catalogState)
                assertEquals(listOf(id), references.pitchRequests)
                assertEquals(listOf(id), references.fingeringRequests)
            }
        } finally { client.close(); Dispatchers.resetMain() }
    }

    @Test fun alternateGlyphsResolveTheSameRegisteredYulAndOctave() {
        val state = FingeringUiState(catalogPitches = listOf(
            CatalogPitch("10","태",2,"㳲","중청태",26),
            CatalogPitch("11","중",2,"㴢","중청중",29),
            CatalogPitch("21","고",2,"㵈","중청고",28),
            CatalogPitch("22","임",2,"㵉","중청임",31),
        ))
        for ((glyph,id) in listOf("㶃" to "10", "㶌" to "11", "㴩" to "21", "㶍" to "22")) {
            assertEquals(id, state.pitchFor(glyph)?.pitchId, glyph)
        }
        assertNull(state.pitchFor("unknown"))
    }

    @Test fun actualCatalogIdsFollowSelectedInstrumentAndEmptyContentIsReady() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val client = instrumentsClient()
        try {
            val references = StubReferences()
            val vm = FingeringViewModel(instruments = InstrumentCatalog(client), references = references)
            vm.uiState.first { it.catalogState != ReferenceLoadState.Loading }
            assertEquals(listOf("42"), references.pitchRequests)
            assertEquals(listOf("42"), references.fingeringRequests)
            assertEquals(ReferenceLoadState.Ready, vm.uiState.value.catalogState)
            assertTrue(vm.uiState.value.catalogFingerings.items.isEmpty())
            vm.selectInstrument(GuideInstrument.Sogeum)
            vm.uiState.first { it.catalogState != ReferenceLoadState.Loading }
            assertEquals(listOf("42", "57"), references.pitchRequests)
            assertEquals("57", vm.uiState.value.catalogPitches.single().pitchId)
            assertEquals(GuideInstrument.Sogeum.notesByPitch(Blow.Mid), vm.uiState.value.notes)
        } finally { client.close(); Dispatchers.resetMain() }
    }

    @Test fun missingPreferredInstrumentSelectsFirstSupportedServerOption() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val client = instrumentsClient()
        try {
            val references = StubReferences()
            val vm = FingeringViewModel(GuideInstrument.DaegeumSanjo, instruments = InstrumentCatalog(client), references = references)
            vm.uiState.first { it.catalogState != ReferenceLoadState.Loading }
            assertEquals(ReferenceLoadState.Ready, vm.uiState.value.catalogState)
            assertEquals(GuideInstrument.Danso, vm.uiState.value.instrument)
            assertEquals(listOf("42"), references.pitchRequests)
            assertTrue(vm.uiState.value.notes.isNotEmpty())
        } finally { client.close(); Dispatchers.resetMain() }
    }

    @Test fun failedCatalogRetriesWithoutLosingBlowAndNoParallelDuplicate() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val client = instrumentsClient()
        try {
            val references = StubReferences().apply { failPitches = true }
            val vm = FingeringViewModel(blow = Blow.High, instruments = InstrumentCatalog(client), references = references)
            vm.uiState.first { it.catalogState != ReferenceLoadState.Loading }
            assertEquals(ReferenceLoadState.Failed, vm.uiState.value.catalogState)
            assertEquals(ReferenceLoadState.Ready, vm.uiState.value.instrumentListState)
            assertTrue(vm.uiState.value.hasSelectedInstrument)
            references.failPitches = false
            vm.retryCatalog(); vm.retryCatalog()
            vm.uiState.first { it.catalogState != ReferenceLoadState.Loading }
            assertEquals(ReferenceLoadState.Ready, vm.uiState.value.catalogState)
            assertEquals(Blow.High, vm.uiState.value.shownBlow)
            assertEquals(2, references.pitchRequests.size)
        } finally { client.close(); Dispatchers.resetMain() }
    }

    @Test fun previousInstrumentResponseCannotReplaceNewSelection() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val client = instrumentsClient()
        try {
            val pending = CompletableDeferred<Unit>()
            val references = StubReferences().apply { firstPitches = pending }
            val vm = FingeringViewModel(instruments = InstrumentCatalog(client), references = references)
            references.firstPitchesStarted.await()
            vm.selectInstrument(GuideInstrument.Sogeum)
            vm.uiState.first { it.catalogState != ReferenceLoadState.Loading }
            pending.complete(Unit)
            advanceUntilIdle()
            assertEquals(GuideInstrument.Sogeum, vm.uiState.value.instrument)
            assertEquals("57", vm.uiState.value.catalogPitches.single().pitchId)
        } finally { client.close(); Dispatchers.resetMain() }
    }

    private fun instrumentsClient() = createHttpClient(MockEngine {
        respond("""{"isSuccess":true,"result":{"items":[{"instrumentId":"42","code":"DANSO","name":"단소"},{"instrumentId":"57","code":"SOGEUM","name":"소금"},{"instrumentId":"88","code":"DAEGEUM","name":"대금"}]}}""",
            headers = headersOf(HttpHeaders.ContentType, "application/json"))
    }, ServerAddress.Production)
}

private class StubReferences : ReferenceCatalogRepository {
    var failPitches = false
    var firstPitches: CompletableDeferred<Unit>? = null
    val firstPitchesStarted = CompletableDeferred<Unit>()
    val pitchRequests = mutableListOf<String>()
    val fingeringRequests = mutableListOf<String>()
    override suspend fun pitches(instrumentId: String): List<CatalogPitch> {
        pitchRequests += instrumentId
        if (instrumentId == "42") { firstPitchesStarted.complete(Unit); firstPitches?.await() }
        if (failPitches) throw CatalogUnavailable()
        return listOf(CatalogPitch(instrumentId, "황", 1, "潢", "청황", 75))
    }
    override suspend fun fingerings(instrumentId: String, pitchId: String?): CatalogFingerings {
        fingeringRequests += instrumentId
        return CatalogFingerings(emptyList())
    }
    override suspend fun jangdans() = listOf(CatalogJangdanSummary("7", "장단", 60, "QUARTER", 1, 1))
    override suspend fun jangdan(jangdanId: String): CatalogJangdanDetail? = null
    override suspend fun symbols(): List<CatalogNotationSymbol> = emptyList()
}
