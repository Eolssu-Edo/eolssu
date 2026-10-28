package com.ssafy.eolssu.backend.feature.term.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.ssafy.eolssu.backend.feature.term.application.ListTerms
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/terms")
@Profile("db")
class TermsController(private val listTerms: ListTerms) {
    @GetMapping
    fun list() = TermsResponse(
        result = TermsResult(listTerms.execute().map {
            TermItem(it.id.toString(), it.code, it.title, it.required, it.version, it.contentUrl)
        }),
    )
}

data class TermsResponse(
    val httpStatus: String = "OK",
    @get:JsonProperty("isSuccess") val isSuccess: Boolean = true,
    val message: String = "약관 목록 조회 성공",
    val code: Int = 200,
    val result: TermsResult,
)

data class TermsResult(val items: List<TermItem>)

data class TermItem(
    val termId: String,
    val code: String,
    val title: String,
    val required: Boolean,
    val version: String,
    val contentUrl: String,
)
