package com.ssafy.eolssu.app.navigation

import com.ssafy.eolssu.core.diagnostics.PerfTarget
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.feature.history.CompareRoute
import com.ssafy.eolssu.feature.practice.SectionLoopRoute
import com.ssafy.eolssu.feature.practice.SectionSelectRoute
import com.ssafy.eolssu.feature.result.SectionResultRoute

/**
 * 2차 취약 구간 연습·이전 연주 비교 화면 경로(S15P31E102-455·343).
 * `{source}`는 바탕이 된 연습 세션 ID다(평가 결과의 평가 ID와 기록의 기록 ID가 모두 연습 세션 ID다).
 * 구간은 처음·끝 정간(각 `sc`·`ec`, 정간 `sr`·`er`, 0부터)으로, 반복 횟수 0은 ‘계속’이다.
 * 구간 재평가 결과의 `{sessionId}`는 구간만 녹음한 새 연습 세션이다.
 */
object WeakSectionDestination {
    const val SELECT = "weak/{source}/sections"
    const val LOOP = "weak/{source}/loop?sc={sc}&sr={sr}&ec={ec}&er={er}&bpm={bpm}&repeats={repeats}"
    const val RESULT = "weak/{source}/result/{sessionId}"
    const val COMPARE = "compare/{recordId}?previous={previousId}"

    fun select(source: String) = "weak/$source/sections"

    fun loop(source: String, section: ScoreSection, bpm: Int, repeats: Int?) =
        "weak/$source/loop?${section.query()}&bpm=$bpm&repeats=${repeats ?: 0}"

    fun sectionResult(source: String, sessionId: String) = "weak/$source/result/$sessionId"

    /** [previousId]는 이전 연주 결과의 기록 고르기 시트에서 고른 기록이다. null이면 바로 전 기록과 견준다. */
    fun compare(recordId: String, previousId: String? = null) = "compare/$recordId" + (previousId?.let { "?previous=$it" } ?: "")

    private fun ScoreSection.query() = "sc=${start.column}&sr=${start.row}&ec=${end.column}&er=${end.row}"
}

/**
 * 화면 이동: 평가 결과 ‘취약 구간 연습’ → 구간 고르기 → 구간 반복 → 재평가,
 * 기록 상세 ‘지난 연주와 비교’ → 기록 고르기 시트 → 이전 연주 비교. 재평가의 ‘곡 전체 다시 평가’는 그 곡의 연습 화면으로 간다.
 */
internal fun NavGraphBuilder.weakSectionGraph(navController: NavController, performance: PerformanceNavigation) {
    val sourceArg = navArgument("source") { type = NavType.StringType }
    val sectionArgs = listOf("sc", "sr", "ec", "er").map { name -> navArgument(name) { type = NavType.IntType; defaultValue = 0 } }
    val bpmArg = navArgument("bpm") { type = NavType.IntType; defaultValue = 60 }

    composable(WeakSectionDestination.SELECT, arguments = listOf(sourceArg)) { entry ->
        val source = entry.source()
        val perf = performance.visit(entry, PerfTarget.WEAK_SELECT)
        SectionSelectRoute(
            perf = perf,
            sourceId = source,
            onBack = { navController.popBackStackFrom(entry) },
            onStart = { section, bpm, repeats ->
                performance.move(perf, PerfTarget.WEAK_LOOP) { navController.navigate(WeakSectionDestination.loop(source, section, bpm, repeats)) }
            },
        )
    }
    composable(
        WeakSectionDestination.LOOP,
        arguments = listOf(sourceArg) + sectionArgs + bpmArg + navArgument("repeats") { type = NavType.IntType; defaultValue = 0 },
    ) { entry ->
        val source = entry.source()
        val section = entry.section()
        val bpm = entry.savedStateHandle.get<Int>("bpm") ?: 60
        val perf = performance.visit(entry, PerfTarget.WEAK_LOOP)
        SectionLoopRoute(
            perf = perf,
            sourceId = source,
            section = section,
            bpm = bpm,
            repeats = entry.savedStateHandle.get<Int>("repeats")?.takeIf { it > 0 },
            onBack = { navController.popBackStackFrom(entry) },
            // 구간만 녹음한 연주를 분석에 맡긴다. 분석이 끝나면 구간 재평가 결과로 간다.
            onEvaluate = { songId, take, sessionSection ->
                performance.move(perf, PerfTarget.ANALYSIS) { navController.navigate(EolssuDestination.analysis(songId, bpm, take, section = sessionSection)) }
            },
        )
    }
    composable(WeakSectionDestination.RESULT, arguments = listOf(sourceArg, navArgument("sessionId") { type = NavType.StringType })) { entry ->
        val source = entry.source()
        val perf = performance.visit(entry, PerfTarget.SECTION_RESULT)
        SectionResultRoute(
            perf = perf,
            sessionId = entry.savedStateHandle.get<String>("sessionId").orEmpty(),
            onBack = { navController.popBackStackFrom(entry) },
            onPracticeAgain = { navController.popBackStackFrom(entry) },
            onOtherSection = {
                if (!navController.popBackStack(WeakSectionDestination.SELECT, inclusive = false)) {
                    navController.navigate(WeakSectionDestination.select(source))
                }
            },
            onWholeSong = { songId -> navController.navigate(EolssuDestination.practice(songId)) },
        )
    }
    composable(
        WeakSectionDestination.COMPARE,
        arguments = listOf(
            navArgument("recordId") { type = NavType.StringType },
            navArgument("previousId") { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) { entry ->
        CompareRoute(
            recordId = entry.savedStateHandle.get<String>("recordId").orEmpty(),
            previousId = entry.savedStateHandle.get<String>("previousId"),
            onBack = { navController.popBackStackFrom(entry) },
        )
    }
}

private fun NavBackStackEntry.source(): String = savedStateHandle.get<String>("source").orEmpty()

/** 경로의 처음·끝 정간. 순서가 뒤집혀 들어와도 앞뒤를 맞춘다. */
private fun NavBackStackEntry.section(): ScoreSection {
    fun arg(name: String) = savedStateHandle.get<Int>(name) ?: 0
    return ScoreSection.between(ScorePosition(arg("sc"), arg("sr")), ScorePosition(arg("ec"), arg("er")))
}
