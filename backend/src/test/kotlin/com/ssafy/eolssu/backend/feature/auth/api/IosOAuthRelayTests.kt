package com.ssafy.eolssu.backend.feature.auth.api

import java.net.URI
import java.net.http.*
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class IosOAuthRelayTests {
    @Value("\${local.server.port}") private var port=0

    private fun get(path:String,cookie:String?=null):HttpResponse<String> = HttpClient.newHttpClient().use { client ->
        val request=HttpRequest.newBuilder(URI("http://localhost:$port$path"))
        cookie?.let{request.header("Cookie",it)}
        client.send(request.GET().build(),HttpResponse.BodyHandlers.ofString())
    }

    @Test fun `provider return is relayed to the iOS app scheme without login`() {
        for(cookie in listOf(null,"access_token=broken.jwt")) {
            val response=get("/api/auth/ios/oauth/kakao/callback?code=abc%2Bdef&state=AAAA-_&unknown=drop",cookie)
            assertEquals(302,response.statusCode(),response.body())
            assertEquals("com.ssafy.eolssu://oauth/kakao/callback?code=abc%2Bdef&state=AAAA-_",response.headers().firstValue("Location").orElse(""))
            assertEquals("no-store",response.headers().firstValue("Cache-Control").orElse(""))
            assertEquals("no-referrer",response.headers().firstValue("Referrer-Policy").orElse(""))
        }
    }

    @Test fun `provider errors keep their error parameters`() {
        val response=get("/api/auth/ios/oauth/naver/callback?state=s&error=access_denied&error_code=KOE101&error_description=user%20cancel")
        assertEquals(302,response.statusCode())
        assertEquals("com.ssafy.eolssu://oauth/naver/callback?state=s&error=access_denied&error_code=KOE101&error_description=user%20cancel",response.headers().firstValue("Location").orElse(""))
    }

    @Test fun `repeated parameters keep only the first value`() {
        val response=get("/api/auth/ios/oauth/google/callback?state=first&code=c1&state=second&code=c2")
        assertEquals(302,response.statusCode())
        assertEquals("com.ssafy.eolssu://oauth/google/callback?state=first&code=c1",response.headers().firstValue("Location").orElse(""))
    }

    @Test fun `unknown provider is not relayed`() {
        for(path in listOf("/api/auth/ios/oauth/apple/callback?code=x","/api/auth/ios/oauth/KAKAO/callback?code=x")) {
            val response=get(path)
            assertEquals(404,response.statusCode(),path)
            assertFalse(response.headers().firstValue("Location").isPresent,path)
        }
    }
}
