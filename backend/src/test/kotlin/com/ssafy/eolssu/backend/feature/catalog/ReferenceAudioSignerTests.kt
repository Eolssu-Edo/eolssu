package com.ssafy.eolssu.backend.feature.catalog

import com.ssafy.eolssu.backend.feature.catalog.infrastructure.storage.ReferenceAudioStorageProperties
import com.ssafy.eolssu.backend.feature.catalog.infrastructure.storage.S3ReferenceAudioReadUrls
import java.net.URI
import org.junit.jupiter.api.Test
import kotlin.test.*

class ReferenceAudioSignerTests {
    private val properties = ReferenceAudioStorageProperties("https://r2.example.test", "reference-assets", "synthetic-key", "synthetic-secret")

    @Test fun `presigner issues GET query for exact asset key for fifteen minutes without network calls`() {
        val asset = referenceAudioFixture()
        S3ReferenceAudioReadUrls.create(properties).use {
            val uri = URI(it.readUrl(asset.objectKey))
            assertEquals("https", uri.scheme)
            assertEquals("r2.example.test", uri.host)
            assertEquals("/reference-assets/${asset.objectKey}", uri.path)
            val query = uri.query.split('&').associate { p -> p.substringBefore('=') to p.substringAfter('=') }
            assertEquals("900", query["X-Amz-Expires"])
            assertEquals("AWS4-HMAC-SHA256", query["X-Amz-Algorithm"])
            assertEquals(64, query["X-Amz-Signature"]!!.length)
            assertFalse(uri.toString().contains("synthetic-secret"))
        }
    }

    @Test fun `storage never signs a recording external URL or traversal key`() {
        S3ReferenceAudioReadUrls.create(properties).use { signer ->
            for (key in listOf("recordings/user.m4a", "https://other.test/audio.m4a", "reference-audio/v1/../secret", "")) {
                assertFailsWith<IllegalArgumentException> { signer.readUrl(key) }
            }
        }
    }

    @Test fun `endpoint validation and safe property string prevent misdirected signatures and key exposure`() {
        for (url in listOf("http://r2.example.test", "https://user:password@r2.example.test", "https://r2.example.test/path", "https://r2.example.test?token=x")) {
            assertFailsWith<IllegalArgumentException> { S3ReferenceAudioReadUrls.create(properties.copy(endpoint=url)) }
        }
        assertEquals("ReferenceAudioStorageProperties(configured=true)", properties.toString())
        assertFalse(properties.copy(secretAccessKey="").isConfigured)
    }
}
