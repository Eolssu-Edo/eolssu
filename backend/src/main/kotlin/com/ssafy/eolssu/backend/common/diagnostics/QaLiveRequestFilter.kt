package com.ssafy.eolssu.backend.common.diagnostics

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.TimeUnit.NANOSECONDS

/** Opt-in QA observation. Request bodies, query strings and authentication are never logged. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@ConditionalOnProperty(prefix = "eolssu.qa", name = ["live"], havingValue = "true")
class QaLiveRequestFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val id = request.getHeader("X-QA-Request-Id")?.takeIf(uuid::matches)
        val target = target(request.requestURI)
        if (id == null || target == null) {
            chain.doFilter(request, response)
            return
        }
        response.setHeader("X-QA-Request-Id", id)
        val started = System.nanoTime()
        log.info("QA_EVENT_JSON {\"source\":\"server\",\"kind\":\"request_received\",\"requestId\":\"{}\",\"target\":\"{}\"}", id, target)
        var completed = false
        try {
            chain.doFilter(request, response)
            completed = true
        } finally {
            val elapsed = NANOSECONDS.toMillis(System.nanoTime() - started)
            val kind = if (completed) "response_finished" else "request_failed"
            log.info("QA_EVENT_JSON {\"source\":\"server\",\"kind\":\"{}\",\"requestId\":\"{}\",\"target\":\"{}\",\"status\":{},\"durationMs\":{}}", kind, id, target, response.status, elapsed)
        }
    }

    internal fun target(path: String): String? = when {
        path == "/api/auth/sign-in" -> "sign_in"
        path == "/api/songs" -> "song_list"
        songScore.matches(path) -> "score"
        song.matches(path) -> "song_detail"
        path == "/api/practice-sessions" -> "session_create"
        recording.matches(path) -> "recording_upload"
        evaluation.matches(path) -> "evaluation"
        retry.matches(path) -> "analysis_retry"
        session.matches(path) -> "practice_status"
        else -> null
    }

    private companion object {
        val log = LoggerFactory.getLogger(QaLiveRequestFilter::class.java)
        val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
        val song = Regex("/api/songs/[1-9][0-9]{0,18}")
        val songScore = Regex("/api/songs/[1-9][0-9]{0,18}/score")
        val session = Regex("/api/practice-sessions/[1-9][0-9]{0,18}")
        val recording = Regex("/api/practice-sessions/[1-9][0-9]{0,18}/recording")
        val evaluation = Regex("/api/practice-sessions/[1-9][0-9]{0,18}/evaluation")
        val retry = Regex("/api/practice-sessions/[1-9][0-9]{0,18}/retry")
    }
}
