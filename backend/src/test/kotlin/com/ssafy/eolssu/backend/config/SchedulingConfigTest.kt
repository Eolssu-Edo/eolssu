package com.ssafy.eolssu.backend.config

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

class SchedulingConfigTest {
    private val runner = ApplicationContextRunner().withUserConfiguration(SchedulingConfig::class.java)

    private fun schedulerCount(vararg properties: String): Int {
        var count = -1
        runner.withPropertyValues(*properties).run { count = it.getBeansOfType(ScheduledAnnotationBeanPostProcessor::class.java).size }
        return count
    }

    @Test
    fun `enables scheduling in the db and ops profiles unless switched off`() {
        assertEquals(1, schedulerCount("spring.profiles.active=db"))
        assertEquals(0, schedulerCount("spring.profiles.active=db", "eolssu.scheduling.enabled=false"))
        assertEquals(0, schedulerCount())
        // 운영(ops)에서도 업로드 만료 작업이 돌아야 한다.
        assertEquals(1, schedulerCount("spring.profiles.active=ops"))
        assertEquals(0, schedulerCount("spring.profiles.active=ops", "eolssu.scheduling.enabled=false"))
    }

    @Test
    fun `long audio and conversion jobs get their own threads beside the short periodic work`() {
        for (profile in listOf("db", "ops")) {
            runner.withInitializer(ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration::class.java))
                .withPropertyValues("spring.profiles.active=$profile")
                .run { assertEquals(4, it.getBean(ThreadPoolTaskScheduler::class.java).scheduledThreadPoolExecutor.corePoolSize, profile) }
        }
    }
}
