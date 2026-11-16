package com.ssafy.eolssu.backend.common.diagnostics

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QaLiveRequestFilterTest {
    @Test
    fun `only explicit QA property installs the filter`() {
        val context = ApplicationContextRunner().withUserConfiguration(QaLiveRequestFilter::class.java)
        context.run { assertTrue(it.getBeansOfType(QaLiveRequestFilter::class.java).isEmpty()) }
        context.withPropertyValues("eolssu.qa.live=true").run {
            assertEquals(1, it.getBeansOfType(QaLiveRequestFilter::class.java).size)
        }
    }

    @Test
    fun `records receipt before processing and response afterward without request secrets`() {
        val logger = LoggerFactory.getLogger(QaLiveRequestFilter::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().also { it.start() }
        logger.addAppender(appender)
        try {
            val request = MockHttpServletRequest("POST", "/api/practice-sessions/12/recording")
            val response = MockHttpServletResponse()
            val id = "12345678-1234-4321-8123-123456789012"
            request.addHeader("X-QA-Request-Id", id)
            request.addHeader("Authorization", "secret-bearer")
            request.queryString = "token=secret-query"
            request.setContent("secret-audio-body".toByteArray())
            QaLiveRequestFilter().doFilter(request, response, FilterChain { _, output ->
                assertEquals(1, appender.list.size)
                assertTrue(appender.list[0].formattedMessage.contains("request_received"))
                (output as MockHttpServletResponse).status = 202
            })
            assertEquals(id, response.getHeader("X-QA-Request-Id"))
            assertEquals(2, appender.list.size)
            val messages = appender.list.joinToString { it.formattedMessage }
            assertTrue(messages.contains("response_finished"))
            assertTrue(messages.contains("\"status\":202"))
            assertFalse(messages.contains("secret-"))
            assertFalse(messages.contains("/12/"))
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    @Test
    fun `invalid correlation identifiers never alter the response`() {
        val request = MockHttpServletRequest("GET", "/api/songs/1")
        request.addHeader("X-QA-Request-Id", "arbitrary-value")
        val response = MockHttpServletResponse()
        var invoked = false
        QaLiveRequestFilter().doFilter(request, response, FilterChain { _, _ -> invoked = true })
        assertTrue(invoked)
        assertNull(response.getHeader("X-QA-Request-Id"))
    }
}
