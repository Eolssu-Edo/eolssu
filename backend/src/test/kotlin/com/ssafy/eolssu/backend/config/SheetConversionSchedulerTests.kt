package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.sheet.infrastructure.ConversionWorker
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.TemporarySheetSourceStorage
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.ConversionQueue
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.scheduling.annotation.Scheduled

class SheetConversionSchedulerTests {
    @TempDir lateinit var root: Path
    private class Time : Clock() {
        var now=Instant.parse("2026-11-06T00:00:00Z")
        override fun instant()=now
        override fun getZone(): ZoneId=ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock=this
    }
    @Test fun `source expiry sweep has its own schedule and never waits on the database`() {
        val time=Time()
        TemporarySheetSourceStorage(root.resolve("spool"),"http://backend:8080/api/internal/sheet-imports",time).use { store ->
            val input=root.resolve("input");Files.writeString(input,"raw")
            store.put("key",input,"image/png")
            val path=assertNotNull(store.readable(store.readUrl("key",Duration.ofMinutes(15)).substringAfterLast('/'))).path
            val queue=mock(ConversionQueue::class.java);val worker=mock(ConversionWorker::class.java)
            time.now=time.now.plusSeconds(1800)
            SheetConversionScheduler(queue,worker,time,store).purge()
            assertFalse(Files.exists(path))
            verifyNoInteractions(queue,worker)
        }
        assertNotNull(SheetConversionScheduler::class.java.getMethod("purge").getAnnotation(Scheduled::class.java))
    }
}
