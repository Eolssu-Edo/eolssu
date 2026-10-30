package com.ssafy.eolssu.backend.common.security

import java.net.URI
import java.net.http.*
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import tools.jackson.databind.json.JsonMapper

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties=[
    "eolssu.auth.android-app-links.sha256-cert-fingerprints=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
])
class AndroidAppLinksTests {
    @Value("\${local.server.port}") private var port=0
    @Test fun `public android association is raw JSON and never depends on login cookies`() {
        HttpClient.newHttpClient().use { client ->
            for(cookie in listOf(null,"access_token=broken.jwt")) {
                val request=HttpRequest.newBuilder(URI("http://localhost:$port/.well-known/assetlinks.json"))
                cookie?.let{request.header("Cookie",it)}
                val response=client.send(request.GET().build(),HttpResponse.BodyHandlers.ofString())
                assertEquals(200,response.statusCode(),response.body())
                assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"))
                assertFalse(response.headers().firstValue("Location").isPresent)
                val json=JsonMapper.builder().build().readTree(response.body())
                assertTrue(json.isArray)
                assertEquals(1,json.size())
                assertEquals("delegate_permission/common.handle_all_urls",json[0].path("relation")[0].asString())
                val target=json[0].path("target")
                assertEquals("android_app",target.path("namespace").asString())
                assertEquals("com.ssafy.eolssu",target.path("package_name").asString())
                assertEquals("01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF",target.path("sha256_cert_fingerprints")[0].asString())
            }
            val protected=client.send(HttpRequest.newBuilder(URI("http://localhost:$port/api/users/me")).GET().build(),HttpResponse.BodyHandlers.ofString())
            assertEquals(401,protected.statusCode())
        }
    }
}

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class MissingAndroidAppLinksTests {
    @Value("\${local.server.port}") private var port=0
    @Test fun `missing fingerprints do not publish a placeholder association`() {
        HttpClient.newHttpClient().use { client ->
            val response=client.send(HttpRequest.newBuilder(URI("http://localhost:$port/.well-known/assetlinks.json")).GET().build(),HttpResponse.BodyHandlers.ofString())
            assertEquals(404,response.statusCode())
            assertFalse(response.body().contains("delegate_permission"))
        }
    }
}

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties=[
    "eolssu.auth.android-app-links.sha256-cert-fingerprints=",
    "eolssu.auth.android-app-links.qa-sha256-cert-fingerprints=abababababababababababababababababababababababababababababababab",
])
class QaOnlyAndroidAppLinksTests {
    @Value("\${local.server.port}") private var port=0
    @Test fun `packages publish independent signing certificates`() {
        HttpClient.newHttpClient().use { client ->
            val response=client.send(HttpRequest.newBuilder(URI("http://localhost:$port/.well-known/assetlinks.json")).GET().build(),HttpResponse.BodyHandlers.ofString())
            assertEquals(200,response.statusCode(),response.body())
            assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"))
            assertFalse(response.headers().firstValue("Location").isPresent)
            val json=JsonMapper.builder().build().readTree(response.body())
            assertEquals(1,json.size())
            val qa=json[1-1].path("target")
            assertEquals("com.ssafy.eolssu.qa",qa.path("package_name").asString())
            assertEquals(List(32){"AB"}.joinToString(":"),qa.path("sha256_cert_fingerprints")[0].asString())
            if(1==2) {
                val normal=json[0].path("target")
                assertEquals("com.ssafy.eolssu",normal.path("package_name").asString())
                assertEquals(List(32){"CD"}.joinToString(":"),normal.path("sha256_cert_fingerprints")[0].asString())
            }
        }
    }
}

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties=[
    "eolssu.auth.android-app-links.sha256-cert-fingerprints=cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd",
    "eolssu.auth.android-app-links.qa-sha256-cert-fingerprints=abababababababababababababababababababababababababababababababab",
])
class MultipleAndroidAppLinksTests {
    @Value("\${local.server.port}") private var port=0
    @Test fun `packages publish independent signing certificates`() {
        HttpClient.newHttpClient().use { client ->
            val response=client.send(HttpRequest.newBuilder(URI("http://localhost:$port/.well-known/assetlinks.json")).GET().build(),HttpResponse.BodyHandlers.ofString())
            assertEquals(200,response.statusCode(),response.body())
            assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"))
            assertFalse(response.headers().firstValue("Location").isPresent)
            val json=JsonMapper.builder().build().readTree(response.body())
            assertEquals(2,json.size())
            val qa=json[2-1].path("target")
            assertEquals("com.ssafy.eolssu.qa",qa.path("package_name").asString())
            assertEquals(List(32){"AB"}.joinToString(":"),qa.path("sha256_cert_fingerprints")[0].asString())
            if(2==2) {
                val normal=json[0].path("target")
                assertEquals("com.ssafy.eolssu",normal.path("package_name").asString())
                assertEquals(List(32){"CD"}.joinToString(":"),normal.path("sha256_cert_fingerprints")[0].asString())
            }
        }
    }
}
