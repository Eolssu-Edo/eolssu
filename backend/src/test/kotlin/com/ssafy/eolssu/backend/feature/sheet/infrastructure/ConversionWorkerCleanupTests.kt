package com.ssafy.eolssu.backend.feature.sheet.infrastructure

import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorage
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorageException
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.ConversionFile
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.ConversionQueue
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import tools.jackson.databind.json.JsonMapper

@ExtendWith(OutputCaptureExtension::class)
class ConversionWorkerCleanupTests {
    private val warning="Temporary sheet source cleanup will be retried"
    private class Time : Clock() {
        var now=Instant.parse("2026-11-06T00:00:00Z")
        override fun instant()=now
        override fun getZone(): ZoneId=ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock=this
    }
    @Test fun `lasting source deletion failure warns once a minute with its cause`(output: CapturedOutput) {
        val time=Time()
        val queue=mock(ConversionQueue::class.java);val storage=mock(SheetSourceStorage::class.java)
        `when`(queue.finishedSources()).thenReturn(listOf(ConversionFile("key","image/png")))
        doThrow(SheetSourceStorageException("locked source")).`when`(storage).delete("key")
        val worker=ConversionWorker(queue,storage,{ error("not called") },mock(RecognitionResultBuilder::class.java),time,JsonMapper.builder().build())
        worker.cleanup();worker.cleanup()
        assertEquals(1,output.out.split(warning).size-1)
        assertEquals(1,output.out.split("locked source").size-1)
        time.now=time.now.plusSeconds(60);worker.cleanup()
        assertEquals(2,output.out.split(warning).size-1)
    }
}
