package com.ssafy.eolssu.backend.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.parameters.RequestBody
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/** 실제 springdoc 문서는 databaseTest(OpenApiDatabaseTests)가 본다. 여기서는 DB 없이 요청 예시를 넣는 규칙만 확인한다. */
class OpenApiRequestExampleTests {
    @Test
    fun `practice session create offers runnable examples without mission`() {
        val api = documentedApi()
        val media = api.paths["/api/practice-sessions"]!!.post.requestBody.content["application/json"]!!

        OpenApiConfig().businessApiDocumentation().customise(api)

        assertNull(media.example)
        assertEquals(listOf("whole", "section", "retry"), media.examples.keys.toList())
        // 첫 예시는 Swagger UI의 기본값이다. 필수 필드만 보내 선택 필드의 임의 값으로 404·409가 나지 않게 한다.
        assertEquals(mapOf("songId" to "15", "bpm" to 96), media.examples.getValue("whole").value)
        assertEquals(mapOf("songId" to "15", "bpm" to 96, "startNoteIdx" to 0, "endNoteIdx" to 10), media.examples.getValue("section").value)
        assertNotNull(media.examples.getValue("retry").description)
        for (example in media.examples.values) {
            @Suppress("UNCHECKED_CAST")
            assertNull((example.value as Map<String, Any>)["missionId"])
        }
    }

    @Test
    fun `sheet authoring documents score instrument default and actionable rejection examples`() {
        val api = documentedApi()
        OpenApiConfig().businessApiDocumentation().customise(api)
        val audio = api.paths["/api/users/me/sheets/{sheetId}/audio"]!!.post
        val properties = audio.requestBody.content["application/json"]!!.schema.properties
        assertTrue("악보 악기" in properties["instrumentId"]!!.description)
        for (operation in listOf(audio, api.paths["/api/users/me/sheets/{sheetId}/score"]!!.put)) {
            val unsupported = operation.responses["400"]!!.content["application/json"]!!.examples["UNSUPPORTED_PITCHES"]!!.value as Map<*, *>
            val result = unsupported["result"] as Map<*, *>
            assertEquals("INVALID_REQUEST", result["errorCode"])
            assertEquals("UNSUPPORTED_PITCHES", (result["details"] as Map<*, *>)["reason"])
        }
    }

    // springdoc이 만드는 것처럼 문서 계약의 모든 경로와 JSON 요청 본문을 갖춘 최소 문서.
    private fun documentedApi(): OpenAPI {
        val paths = Paths()
        val extra = listOf(
            "/api/terms/documents/{version}/{document}", "/api/terms/documents/current/{document}",
            "/api/terms/documents/account-deletion", "/api/recordings/read",
        ).map { PathItem.HttpMethod.GET to it }
        for ((method, path) in apiContracts.map { it.method to it.path } + extra) {
            val operation = Operation()
            if (method != PathItem.HttpMethod.GET) {
                operation.requestBody = RequestBody().content(Content().addMediaType("application/json", MediaType().schema(ObjectSchema())))
            }
            paths.computeIfAbsent(path) { PathItem() }.operation(method, operation)
        }
        return OpenAPI().paths(paths)
    }
}
