package com.ssafy.eolssu.backend.feature.catalog.infrastructure.storage

import com.ssafy.eolssu.backend.feature.catalog.domain.ReferenceAudioReadUrls
import com.ssafy.eolssu.backend.feature.catalog.domain.ReferenceAudioUnavailable
import java.net.URI
import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner

/** Read-only signing: no uploader/deleter, recording configuration or lifecycle dependency. */
class S3ReferenceAudioReadUrls(private val presigner: S3Presigner, private val bucket: String) : ReferenceAudioReadUrls, AutoCloseable {
    override fun readUrl(objectKey: String): String {
        require(KEY.matches(objectKey))
        return try {
            presigner.presignGetObject { it.signatureDuration(Duration.ofSeconds(900))
                .getObjectRequest { get -> get.bucket(bucket).key(objectKey) } }.url().toString()
        } catch (_: SdkException) { throw ReferenceAudioUnavailable() }
    }
    override fun close() = presigner.close()

    companion object {
        private val KEY = Regex("reference-audio/v1/(danso|sogeum|jeongak-daegeum|sanjo-daegeum)/[0-9a-f]{64}/[0-9a-f]{64}\\.m4a")
        fun create(properties: ReferenceAudioStorageProperties): S3ReferenceAudioReadUrls {
            val endpoint = URI(properties.endpoint)
            require(endpoint.scheme == "https" && endpoint.host != null && endpoint.userInfo == null &&
                endpoint.query == null && endpoint.fragment == null && endpoint.path in listOf("", "/"))
            require(properties.bucket.matches(Regex("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")))
            val presigner = S3Presigner.builder().endpointOverride(endpoint).region(Region.of(properties.region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKeyId,properties.secretAccessKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build()
            return S3ReferenceAudioReadUrls(presigner,properties.bucket)
        }
    }
}

@ConfigurationProperties("eolssu.reference-audio-storage")
data class ReferenceAudioStorageProperties(val endpoint: String = "", val bucket: String = "", val accessKeyId: String = "",
    val secretAccessKey: String = "", val region: String = "auto") {
    val isConfigured get() = listOf(endpoint,bucket,accessKeyId,secretAccessKey,region).none(String::isBlank)
    override fun toString() = "ReferenceAudioStorageProperties(configured=$isConfigured)"
}
