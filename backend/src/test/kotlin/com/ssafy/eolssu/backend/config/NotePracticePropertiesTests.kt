package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.practice.domain.NotePracticeCriteria
import com.ssafy.eolssu.backend.feature.practice.domain.NotePracticeSigimsaeMode
import com.ssafy.eolssu.backend.feature.practice.domain.NotePracticeTuning
import java.math.BigDecimal
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class NotePracticePropertiesTests {
    private val runner = ApplicationContextRunner()
        .withInitializer(ConfigDataApplicationContextInitializer())
        .withUserConfiguration(Enabled::class.java)

    @Test fun `application settings register the unconfirmed draft for the four instruments by code`() {
        runner.run { context ->
            val criteria = context.getBean(NotePracticeProperties::class.java).criteria
            assertEquals(setOf("DANSO", "SOGEUM", "JEONGAK_DAEGEUM", "SANJO_DAEGEUM"), criteria.keys)
            val draft = NotePracticeCriteria(
                "note-step-1", BigDecimal("50"), 300, BigDecimal("0.8"), BigDecimal("-45"), true,
                NotePracticeTuning.FIRST_NOTE, BigDecimal("50"), NotePracticeSigimsaeMode.MAIN_PITCH, 8000,
            )
            assertTrue(criteria.values.all { it == draft }, criteria.toString())
        }
    }

    @Test fun `one instrument can change without touching the others`() {
        runner.withPropertyValues("eolssu.note-practice.criteria.SOGEUM.hold-ms=450").run { context ->
            val criteria = context.getBean(NotePracticeProperties::class.java).criteria
            assertEquals(450, criteria.getValue("SOGEUM").holdMs)
            assertEquals(300, criteria.getValue("DANSO").holdMs)
        }
    }

    @Test fun `invalid settings stop startup instead of reaching devices`() {
        for (invalid in listOf("criteria-version=", "min-confidence=1.1", "min-confidence=-0.1", "hold-ms=-1",
            "hint-after-ms=-1", "pitch-tolerance-cent=-1", "max-tuning-offset-cent=-1", "min-level-dbfs=1", "tuning=UNKNOWN")) {
            runner.withPropertyValues("eolssu.note-practice.criteria.DANSO.$invalid").run { context ->
                assertNotNull(context.startupFailure, invalid)
            }
        }
    }

    @Test fun `a block missing any key stops startup instead of defaulting`() {
        val complete = mapOf(
            "criteria-version" to "fixture", "pitch-tolerance-cent" to "50", "hold-ms" to "300", "min-confidence" to "0.8",
            "min-level-dbfs" to "-45", "octave-must-match" to "true", "tuning" to "NONE", "max-tuning-offset-cent" to "50",
            "sigimsae-mode" to "SKIP", "hint-after-ms" to "8000",
        )
        fun settings(entries: Map<String, String>) = entries.map { "eolssu.note-practice.criteria.NEW.${it.key}=${it.value}" }.toTypedArray()
        runner.withPropertyValues(*settings(complete)).run { context -> assertNull(context.startupFailure) }
        for (missing in complete.keys) {
            runner.withPropertyValues(*settings(complete - missing)).run { context ->
                assertNotNull(context.startupFailure, missing)
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(NotePracticeProperties::class)
    private class Enabled
}
