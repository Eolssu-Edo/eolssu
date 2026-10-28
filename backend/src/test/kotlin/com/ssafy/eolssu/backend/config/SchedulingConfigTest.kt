package com.ssafy.eolssu.backend.config

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor

class SchedulingConfigTest {
    private val runner = ApplicationContextRunner().withUserConfiguration(SchedulingConfig::class.java)

    private fun schedulerCount(vararg properties: String): Int {
        var count = -1
        runner.withPropertyValues(*properties).run { count = it.getBeansOfType(ScheduledAnnotationBeanPostProcessor::class.java).size }
        return count
    }

    @Test
    fun `enables scheduling only in the db profile unless switched off`() {
        assertEquals(1, schedulerCount("spring.profiles.active=db"))
        assertEquals(0, schedulerCount("spring.profiles.active=db", "eolssu.scheduling.enabled=false"))
        assertEquals(0, schedulerCount())
        assertEquals(0, schedulerCount("spring.profiles.active=ops"))
    }
}
