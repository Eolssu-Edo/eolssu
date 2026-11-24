package com.ssafy.eolssu.platform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.ssafy.eolssu.app.di.appModule
import com.ssafy.eolssu.core.diagnostics.PerformanceVisit
import com.ssafy.eolssu.core.audio.RecordedTake
import com.ssafy.eolssu.core.model.SocialProvider
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.fingering.Blow
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.data.practice.SessionSection
import com.ssafy.eolssu.data.user.PublishedLegalDocument
import com.ssafy.eolssu.data.user.VerificationPurpose
import com.ssafy.eolssu.feature.practice.PracticeViewModel
import com.ssafy.eolssu.feature.result.AnalysisViewModel
import com.ssafy.eolssu.feature.song.edit.EditSource
import com.ssafy.eolssu.feature.song.edit.EditView
import com.ssafy.eolssu.feature.song.edit.HwangPitch
import com.ssafy.eolssu.feature.song.edit.ImportKind
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.definition.BeanDefinition
import org.koin.core.definition.Kind
import org.koin.core.error.NoDefinitionFoundException
import org.koin.core.parameter.ParametersHolder
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 실제 웹 플랫폼 모듈(`webPlatformModule`)과 앱 모듈(`appModule`)로 모든 Koin 정의를 만들어 본다(S15P31E102-1270).
 * 공통 코드가 웹에 없는 구현을 `get()`하면 여기서 실패한다. 등록 목록을 적지 않고 Koin에 등록된 정의를 모두 훑으므로,
 * 플랫폼 모듈에 바인딩을 더해도 이 테스트를 고칠 필요가 없다.
 */
@OptIn(KoinInternalApi::class)
class WebKoinResolutionTest {

    /**
     * 웹에 구현이 없는 플랫폼 기능에만 기대는 정의와 그 기능. 쓰는 쪽은 그 기능을 `getOrNull`로 확인한 뒤에만 받아야 한다.
     * 웹에 구현이 생기면 [platformOnlyDefinitionsStayUnresolvedOnlyForTheirMissingFeature]가 이 목록에서 빼라고 알린다.
     */
    private val platformOnly: Map<KClass<*>, KClass<*>> = emptyMap() // 웹 RecordingFiles 등록으로 RecordingUploads를 뺐다(S15P31E102-1262).

    /**
     * 화면 인자(`parametersOf`)의 형식별 예시 값. 인자가 있는 ViewModel은 이 값으로 만든다.
     * null 예시는 null을 허용하는 인자(곡 정보 화면 진입 측정 등)다. 새 형식의 인자를 받는 정의를 더하면 여기에 예시를 더한다.
     */
    private val samples: Map<KClass<*>, Any?> = mapOf(
        String::class to "sample-id",
        Boolean::class to false,
        Int::class to 0,
        PerformanceVisit::class to null,
        RecordedTake::class to null,
        SessionSection::class to null,
        Song::class to null,
        SocialProvider::class to SocialProvider.entries.first(),
        GuideInstrument::class to GuideInstrument.entries.first(),
        Blow::class to Blow.entries.first(),
        VerificationPurpose::class to VerificationPurpose.entries.first(),
        PublishedLegalDocument::class to PublishedLegalDocument.entries.first(),
        ImportKind::class to ImportKind.entries.first(),
        HwangPitch::class to HwangPitch.entries.first(),
        EditView::class to EditView.entries.first(),
        EditSource::class to EditSource.Import(ImportKind.entries.first(), HwangPitch.entries.first(), pendingChecks = 0),
    )

    /** 여섯째 인자부터는 형식 정보 없이 순서로 읽으므로(`parameters[5]`) 정의마다 그 자리의 값을 정한다. */
    private val positional: Map<KClass<*>, Map<Int, Any?>> = mapOf(
        PracticeViewModel::class to mapOf(5 to false),
        AnalysisViewModel::class to mapOf(5 to null, 6 to null),
    )

    // 화면 모델이 시작하자마자 보내는 요청이 테스트 페이지 서버로 나가지 않게 엔진만 바꾼다. 실제 엔진은 WebPlatformModuleTest가 확인한다.
    private val offline = module {
        single<HttpClientEngine> { MockEngine { respondError(HttpStatusCode.ServiceUnavailable) } }
    }

    private fun webApp(): KoinApplication = koinApplication {
        allowOverride(true)
        modules(webPlatformModule(), appModule, offline)
    }

    @Test
    fun everyDefinitionResolvesWithTheWebPlatformModule() {
        val app = webApp()
        val viewModels = ViewModelStore()
        try {
            val definitions = rootDefinitions(app.koin)
            // 표에 적은 정의가 훑는 목록에 있어야 한다. 목록이 비거나(Koin 내부 변경) 표가 낡으면 검사 없이 통과하지 않게 막는다.
            val stale = (positional.keys + platformOnly.keys) - definitions.map { it.primaryType }.toSet()
            assertTrue(stale.isEmpty(), "Koin 정의 목록에 없는 표 항목: ${stale.map { it.simpleName }}")
            val failures = definitions
                .filterNot { it.primaryType in platformOnly }
                .mapNotNull { definition ->
                    runCatching { resolve(app.koin, definition) }
                        .onSuccess { if (it is ViewModel) viewModels.put(definition.toString(), it) }
                        .exceptionOrNull()
                        ?.let { "${definition.primaryType.simpleName}: ${describe(it)}" }
                }
            if (failures.isNotEmpty()) {
                fail("웹에서 만들 수 없는 Koin 정의 ${failures.size}개:\n" + failures.joinToString("\n"))
            }
        } finally {
            viewModels.clear()
            close(app)
        }
    }

    @Test
    fun platformOnlyDefinitionsStayUnresolvedOnlyForTheirMissingFeature() {
        val app = webApp()
        try {
            platformOnly.forEach { (definition, feature) ->
                assertNull(
                    app.koin.getOrNull<Any>(feature),
                    "웹에 ${feature.simpleName} 구현이 생겼다. platformOnly에서 ${definition.simpleName}을 뺀다.",
                )
                val failure = runCatching { app.koin.get<Any>(definition) }.exceptionOrNull()
                assertNotNull(failure, "${definition.simpleName}이 웹에서 만들어졌다. platformOnly에서 뺀다.")
                val missing = causes(failure).filterIsInstance<NoDefinitionFoundException>().firstOrNull()
                assertTrue(
                    missing?.message.orEmpty().contains(feature.simpleName.orEmpty()),
                    "${definition.simpleName}은 ${feature.simpleName}만 없어서 실패해야 한다: ${describe(failure)}",
                )
            }
        } finally {
            close(app)
        }
    }

    private fun rootDefinitions(koin: Koin): List<BeanDefinition<*>> {
        val root = koin.scopeRegistry.rootScope.scopeQualifier
        return koin.instanceRegistry.instances.values
            .map { it.beanDefinition }
            .distinct()
            .filter { it.kind != Kind.Scoped && it.scopeQualifier == root }
    }

    private fun resolve(koin: Koin, definition: BeanDefinition<*>): Any? =
        koin.get<Any?>(definition.primaryType, definition.qualifier) {
            SampleParameters(samples, positional[definition.primaryType].orEmpty())
        }

    private fun close(app: KoinApplication) {
        app.koin.getOrNull<CoroutineScope>()?.cancel()
        app.close()
    }

    private fun causes(error: Throwable): Sequence<Throwable> = generateSequence(error) { it.cause }

    private fun describe(error: Throwable): String =
        causes(error).joinToString(" ← ") { "${it::class.simpleName}: ${it.message}" }

    /**
     * 구조 분해(`val (a: A, b: B) = parameters`)는 형식으로 [samples]의 값을 받고, 순서로 읽는 자리는 [positional] 값을 받는다.
     * 예시가 없는 형식이면 어떤 예시를 더할지 알려 주며 실패한다.
     */
    private class SampleParameters(
        private val samples: Map<KClass<*>, Any?>,
        positional: Map<Int, Any?>,
    ) : ParametersHolder(MutableList(POSITIONS) { positional[it] }) {
        @Suppress("UNCHECKED_CAST")
        override fun <T> elementAt(i: Int, clazz: KClass<*>): T {
            check(clazz in samples) { "화면 인자 #$i 형식 ${clazz.simpleName}의 예시가 없다. samples에 더한다." }
            return samples[clazz] as T
        }

        private companion object {
            const val POSITIONS = 8
        }
    }
}
