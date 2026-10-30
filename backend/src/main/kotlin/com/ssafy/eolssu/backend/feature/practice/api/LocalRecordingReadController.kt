package com.ssafy.eolssu.backend.feature.practice.api

import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorage
import com.ssafy.eolssu.backend.feature.practice.infrastructure.storage.LocalRecordingStorage
import org.springframework.beans.factory.ObjectProvider
import org.springframework.core.io.FileSystemResource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class LocalRecordingReadController(private val storage: ObjectProvider<RecordingStorage>) {
    @GetMapping("/api/recordings/read")
    fun read(@RequestParam key: String, @RequestParam expires: Long, @RequestParam signature: String): ResponseEntity<FileSystemResource> {
        val local = storage.ifAvailable as? LocalRecordingStorage ?: return ResponseEntity.notFound().build()
        val path = local.readable(key, expires, signature) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .header("Referrer-Policy", "no-referrer")
            .header("X-Content-Type-Options", "nosniff")
            .contentType(MediaType.parseMediaType("audio/mp4")).body(FileSystemResource(path))
    }
}
