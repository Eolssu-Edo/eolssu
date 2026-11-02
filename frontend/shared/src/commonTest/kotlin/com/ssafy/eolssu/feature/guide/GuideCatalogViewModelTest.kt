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

    @Test fun missingVariantMappingDoesNotReuseDansoOrInventDaegeumId() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val client = instrumentsClient()
        try {
            val references = StubReferences()
            val vm = FingeringViewModel(GuideInstrument.DaegeumSanjo, instruments = InstrumentCatalog(client), references = references)
            vm.uiState.first { it.catalogState != ReferenceLoadState.Loading }
            assertEquals(ReferenceLoadState.NotRegistered, vm.uiState.value.catalogState)
            assertTrue(references.pitchRequests.isEmpty())
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

    @Test fun readingCatalogLoadsIndependentlyAndRetriesMissingOrFailedDetail() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val references = StubReferences().apply { failSymbols = true }
            val vm = ReadingCatalogViewModel(references)
            advanceUntilIdle()
            assertEquals(ReferenceLoadState.Ready, vm.uiState.value.jangdansState)
            assertEquals(ReferenceLoadState.Failed, vm.uiState.value.symbolsState)
            vm.selectJangdan("7")
            advanceUntilIdle()
            assertEquals(ReferenceLoadState.NotRegistered, vm.uiState.value.detailState)
            references.detail = CatalogJangdanDetail("7", "장단", 60, "QUARTER", CatalogJangdanPattern(listOf("덩"), listOf(1)))
            references.failSymbols = false
            vm.retryDetail(); vm.retrySymbols()
            advanceUntilIdle()
            assertEquals(ReferenceLoadState.Ready, vm.uiState.value.detailState)
            assertEquals("7", vm.uiState.value.detail?.jangdanId)
            assertEquals(ReferenceLoadState.Ready, vm.uiState.value.symbolsState)
            assertTrue(vm.uiState.value.symbols.isEmpty())
            vm.selectJangdan("999")
            assertEquals("7", vm.uiState.value.selectedJangdanId)
        } finally { Dispatchers.resetMain() }
    }

    private fun instrumentsClient() = createHttpClient(MockEngine {
        respond("""{"isSuccess":true,"result":{"items":[{"instrumentId":"42","code":"DANSO","name":"단소"},{"instrumentId":"57","code":"SOGEUM","name":"소금"},{"instrumentId":"88","code":"DAEGEUM","name":"대금"}]}}""",
            headers = headersOf(HttpHeaders.ContentType, "application/json"))
    }, ServerAddress.Production)
}

private class StubReferences : ReferenceCatalogRepository {
    var failPitches = false
    var failSymbols = false
    var firstPitches: CompletableDeferred<Unit>? = null
    val firstPitchesStarted = CompletableDeferred<Unit>()
    var detail: CatalogJangdanDetail? = null
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
    override suspend fun jangdan(jangdanId: String) = detail
    override suspend fun symbols(): List<CatalogNotationSymbol> {
        if (failSymbols) throw CatalogUnavailable()
        return emptyList()
    }
}
