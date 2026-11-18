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
    const val EVALUATE = "weak/{source}/evaluate?sc={sc}&sr={sr}&ec={ec}&er={er}"
    const val RESULT = "weak/{source}/result/{sessionId}"
    const val COMPARE = "compare/{recordId}?previous={previousId}"

    /** 연습 기록의 ‘구간 연습’ → 구간 고르기(‘구간 연습 시작’ · ‘구간 재평가 시작’). */
    fun select(source: String) = "weak/$source/sections"


    /** 구간 고르기의 ‘구간 재평가 시작’ → 고른 구간·속도의 구간 재평가(S15P31E102-776). */
    fun evaluate(source: String, section: ScoreSection) = "weak/$source/evaluate?${section.query()}"

    fun sectionResult(source: String, sessionId: String) = "weak/$source/result/$sessionId"

    /** [previousId]는 이전 연주 결과의 기록 고르기 시트에서 고른 기록이다. null이면 바로 전 기록과 견준다. */
    fun compare(recordId: String, previousId: String? = null) = "compare/$recordId" + (previousId?.let { "?previous=$it" } ?: "")

    private fun ScoreSection.query() = "sc=${start.column}&sr=${start.row}&ec=${end.column}&er=${end.row}"
}

/**
 * 화면 이동: 연습 기록 ‘구간 연습’ → 구간 고르기 → ‘구간 연습 시작’이면 연습 화면의 구간 연습(S15P31E102-794),
 * ‘구간 재평가 시작’이면 구간 재평가 → 분석 → 구간 재평가 결과(S15P31E102-776),
 * 기록 상세 ‘지난 연주와 비교’ → 기록 고르기 시트 → 이전 연주 비교. 재평가의 ‘곡 전체 다시 평가’는 그 곡의 연습 화면으로,
 * 저장 완료 시트의 ‘기록으로 이동’은 방금 저장한 기록 상세로 간다(S15P31E102-832).
 */
internal fun NavGraphBuilder.weakSectionGraph(navController: NavController, performance: PerformanceNavigation) {
    val sourceArg = navArgument("source") { type = NavType.StringType }
    val sectionArgs = listOf("sc", "sr", "ec", "er").map { name -> navArgument(name) { type = NavType.IntType; defaultValue = 0 } }


    composable(WeakSectionDestination.SELECT, arguments = listOf(sourceArg)) { entry ->
        val source = entry.source()
        val perf = performance.visit(entry, PerfTarget.WEAK_SELECT)
        SectionSelectRoute(
            perf = perf,
            sourceId = source,
            onBack = { navController.popBackStackFrom(entry) },
            onStart = { songId, section ->
                performance.move(perf, PerfTarget.PRACTICE) { navController.navigate(EolssuDestination.sectionPractice(songId, source, section)) }
            },
            onEvaluate = { section -> navController.navigate(WeakSectionDestination.evaluate(source, section)) },
        )
    }
    composable(WeakSectionDestination.EVALUATE, arguments = listOf(sourceArg) + sectionArgs) { entry ->
        val source = entry.source()
        val section = entry.section()
        val perf = performance.visit(entry, PerfTarget.WEAK_LOOP)
        SectionLoopRoute(
            perf = perf,
            sourceId = source,
            section = section,
            onBack = { navController.popBackStackFrom(entry) },
            // 구간만 녹음한 연주를 분석에 맡긴다. 분석이 끝나면 구간 재평가 결과로 간다.
            onEvaluate = { songId, bpm, take, sessionSection ->
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
            onHome = { navController.popBackStack(EolssuDestination.HOME, inclusive = false) },
            onOtherSection = {
                if (!navController.popBackStack(WeakSectionDestination.SELECT, inclusive = false)) {
                    navController.navigate(WeakSectionDestination.select(source))
                }
            },
            onWholeSong = { songId -> navController.navigate(EolssuDestination.practice(songId)) },
            // 뒤로 가면 이 결과 화면으로 돌아온다. 그 기록을 지우면 이 결과 화면까지 닫는다.
            onOpenRecord = { id ->
                if (navController.currentBackStackEntry?.id == entry.id) {
                    entry.savedStateHandle[OPENED_RECORD_KEY] = id
                    performance.move(perf, PerfTarget.RECORD_DETAIL) { navController.navigate(EolssuDestination.recordResult(id)) }
                }
            },
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
