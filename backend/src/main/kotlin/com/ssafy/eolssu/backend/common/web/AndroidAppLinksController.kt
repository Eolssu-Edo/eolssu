package com.ssafy.eolssu.backend.common.web

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorResponse
import com.ssafy.eolssu.backend.config.AndroidAppLinksProperties
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** Android가 직접 읽는 공개 프로토콜 문서. 업무 API의 성공 envelope를 붙이지 않는다. */
@RestController
class AndroidAppLinksController(private val properties: AndroidAppLinksProperties) {
    @GetMapping("/.well-known/assetlinks.json", produces = ["application/json"])
    fun association(): ResponseEntity<Any> {
        if (properties.normalizedFingerprints.isEmpty()) {
            return ResponseEntity.status(404).cacheControl(CacheControl.noStore())
                .body(ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND))
        }
        val statements = listOf(mapOf(
            "relation" to listOf("delegate_permission/common.handle_all_urls"),
            "target" to mapOf(
                "namespace" to "android_app",
                "package_name" to "com.ssafy.eolssu",
                "sha256_cert_fingerprints" to properties.normalizedFingerprints,
            ),
        ))
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(statements)
    }
}
