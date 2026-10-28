package com.ssafy.eolssu.backend.common.error

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestComponent
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.*

class GlobalExceptionHandlerTests {
    private val mvc = MockMvcBuilders.standaloneSetup(ErrorFixtureController())
        .setControllerAdvice(GlobalExceptionHandler()).build()

    @Test
    fun `malformed JSON uses the common 400 body without echoing input`() {
        mvc.perform(post("/fixture").contentType(MediaType.APPLICATION_JSON).content("{\"secret\":"))
            .andExpect(status().isBadRequest)
            .andExpect(content().json(error(400, "BAD_REQUEST", "INVALID_REQUEST", "요청 형식이 올바르지 않아요")))
    }

    @Test
    fun `invalid fields missing parameters and type mismatches use common 400`() {
        mvc.perform(post("/fixture").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
        for (url in listOf("/fixture", "/fixture?id=not-a-number")) {
            mvc.perform(get(url)).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.result.errorCode").value("INVALID_REQUEST"))
        }
    }

    @Test
    fun `unsupported method keeps Allow and uses common 405`() {
        mvc.perform(post("/fixture/failure"))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(header().string("Allow", "GET"))
            .andExpect(content().json(error(405, "METHOD_NOT_ALLOWED", "METHOD_NOT_ALLOWED", "지원하지 않는 요청 메서드예요")))
    }

    @Test
    fun `unexpected exception uses generic 500 without exposing internals`() {
        mvc.perform(get("/fixture/failure"))
            .andExpect(status().isInternalServerError)
            .andExpect(content().json(error(500, "INTERNAL_SERVER_ERROR", "INTERNAL_SERVER_ERROR", "서버 오류가 발생했어요")))
            .andExpect(jsonPath("$.result.details").doesNotExist())
    }

    @Test
    fun `framework media errors retain their status instead of becoming 500`() {
        mvc.perform(post("/fixture").contentType(MediaType.TEXT_PLAIN).content("secret"))
            .andExpect(status().isUnsupportedMediaType)
        mvc.perform(get("/fixture?id=1").accept(MediaType.IMAGE_PNG))
            .andExpect(status().isNotAcceptable)
    }

    @Test
    fun `multipart size exception uses common 413 without ProblemDetail`() {
        mvc.perform(get("/fixture/too-large"))
            .andExpect(status().isContentTooLarge)
            .andExpect(jsonPath("$.httpStatus").value("CONTENT_TOO_LARGE"))
            .andExpect(jsonPath("$.isSuccess").value(false))
            .andExpect(jsonPath("$.code").value(413))
            .andExpect(jsonPath("$.result.errorCode").value("RECORDING_TOO_LARGE"))
            .andExpect(jsonPath("$.result.details").doesNotExist())
            .andExpect(jsonPath("$.type").doesNotExist())
    }

    private fun error(code: Int, status: String, errorCode: String, message: String) =
        """{"httpStatus":"$status","isSuccess":false,"message":"$message","code":$code,"result":{"errorCode":"$errorCode"}}"""
}

@RestController
@TestComponent
internal class ErrorFixtureController {
    data class Input(@field:NotBlank val name: String)

    @PostMapping("/fixture", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun validate(@Valid @RequestBody input: Input) = input

    @GetMapping("/fixture")
    fun parameter(@RequestParam id: Long) = mapOf("id" to id)

    @GetMapping("/fixture/failure")
    fun failure(): Nothing = throw IllegalStateException("private SQL password=secret")

    @GetMapping("/fixture/too-large")
    fun tooLarge(): Nothing = throw org.springframework.web.multipart.MaxUploadSizeExceededException(1024)
}
