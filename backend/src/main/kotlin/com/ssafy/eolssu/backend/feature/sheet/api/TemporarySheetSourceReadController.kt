package com.ssafy.eolssu.backend.feature.sheet.api

import com.ssafy.eolssu.backend.feature.sheet.infrastructure.TemporarySheetSourceStorage
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.core.io.FileSystemResource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/** Internal OMR fetch only. Nginx blocks /api/internal/; a random expiring capability is still required. */
@RestController
@Profile("db | ops")
class TemporarySheetSourceReadController(
    @Qualifier("temporarySheetSourceStorage") private val storage: TemporarySheetSourceStorage,
) {
    @GetMapping("/api/internal/sheet-imports/{token}")
    fun read(@PathVariable token: String): ResponseEntity<FileSystemResource> {
        val source=storage.readable(token) ?: return ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build()
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .header("Referrer-Policy","no-referrer").header("X-Content-Type-Options","nosniff")
            .contentType(MediaType.parseMediaType(source.contentType)).body(FileSystemResource(source.path))
    }
}
