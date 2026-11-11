package com.ssafy.eolssu.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.ssafy.eolssu.core.diagnostics.PerfTarget
import com.ssafy.eolssu.core.diagnostics.PerformanceFlow
import com.ssafy.eolssu.core.diagnostics.PerformanceLog
import com.ssafy.eolssu.core.diagnostics.PerformanceVisit

/** In-memory links for the measured navigation paths. Route arguments stay with the navigator. */
internal class PerformanceNavigation(private val nav: NavController) {
    private data class Arrival(val flow: PerformanceFlow, val startedAt: Long, val submission: Int?)
    private val incoming = LinkedHashMap<String, Arrival>()
    private val visits = LinkedHashMap<String, PerformanceVisit>()
    private var current: PerformanceVisit? = null

    fun move(from: PerformanceVisit?, target: PerfTarget, navigate: () -> Unit) {
        if (PerformanceLog.sink == null) return navigate()
        val origin = from ?: PerformanceVisit(PerformanceFlow(), target)
        val startedAt = PerformanceLog.now()
        val submission = origin.submissionFor(target)
        origin.action(target, submission)
        val before = nav.currentBackStackEntry?.id
        navigate()
        nav.currentBackStackEntry?.id?.takeIf { it != before }?.let { id ->
            if (incoming.size == 8) incoming.remove(incoming.keys.first())
            incoming[id] = Arrival(origin.flow, startedAt, submission)
        }
    }

    @Composable
    fun visit(entry: NavBackStackEntry, target: PerfTarget): PerformanceVisit? {
        if (PerformanceLog.sink == null) return null
        val visit = remember(entry.id) {
            val arrival = incoming[entry.id]
            visits[entry.id] ?: if (arrival == null) PerformanceVisit(PerformanceFlow(), target)
                else PerformanceVisit(arrival.flow, target, arrival.startedAt, arrival.submission)
        }
        LaunchedEffect(visit) {
            incoming.remove(entry.id)
            if (current?.flow !== visit.flow) current?.ended()
            current = visit
            if (entry.id !in visits) {
                if (visits.size == 32) visits.remove(visits.keys.first())
                visits[entry.id] = visit
                visit.started()
            }
        }
        return visit
    }

    fun leave() {
        current?.ended()
        current = null
        incoming.clear()
    }

    fun reset() {
        leave()
        visits.clear()
    }
}
