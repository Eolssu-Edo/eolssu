package com.ssafy.eolssu.backend.common.storage

import java.net.URI
import java.nio.file.Path
import java.time.Duration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner

/**
 * S3 호환 저장소의 버킷 하나(녹음·악보 원본이 같이 쓴다). 업체 전용 기능 없이 표준 S3 API만 쓴다(처음 쓰는 업체는 Cloudflare R2).
 * 실패는 SDK 예외 그대로 던지며, 쓰는 쪽이 자기 예외로 바꾼다. 예외 메시지에 서명 URL·키를 싣지 않는다.
 */
class S3ObjectStore(private val client: S3Client, private val presigner: S3Presigner, private val bucket: String) : AutoCloseable {
    fun put(key: String, file: Path, contentType: String) {
        client.putObject({ it.bucket(bucket).key(key).contentType(contentType) }, RequestBody.fromFile(file))
    }

    fun delete(key: String) {
        client.deleteObject { it.bucket(bucket).key(key) }
    }

    // 서명은 로컬 계산이라 네트워크를 쓰지 않는다. R2의 서명 URL은 S3 API 도메인에서만 동작한다.
    fun readUrl(key: String, ttl: Duration, fileName: String? = null): String =
        presigner.presignGetObject { it.signatureDuration(ttl).getObjectRequest { request ->
            request.bucket(bucket).key(key)
            if(fileName!=null) {
                require(fileName.isNotBlank() && fileName.none { it.code < 32 || it in "\\/\"\r\n" })
                val encoded=java.net.URLEncoder.encode(fileName,Charsets.UTF_8).replace("+","%20")
                request.responseContentDisposition("attachment; filename=\"download\"; filename*=UTF-8''$encoded")
            }
        } }
            .url().toString()

    // Spring이 빈을 내릴 때 부른다. 연결 풀 스레드를 정리한다.
    override fun close() {
        client.close()
        presigner.close()
    }

    companion object {
        fun create(
            endpoint: String,
            region: String,
            accessKeyId: String,
            secretAccessKey: String,
            bucket: String,
            maxAttempts: Int,
            callTimeout: Duration,
        ): S3ObjectStore {
            val uri = URI(endpoint)
            val awsRegion = Region.of(region)
            val credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey))
            // 경로 방식(엔드포인트/버킷/키)은 R2와 다른 S3 호환 업체가 모두 받는다.
            val s3Configuration = S3Configuration.builder().pathStyleAccessEnabled(true).build()
            val client = S3Client.builder()
                .endpointOverride(uri)
                .region(awsRegion)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Configuration)
                // SDK 기본값(모든 요청에 CRC 체크섬)은 S3 호환 업체마다 지원이 달라, 필요한 요청에만 쓴다.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .overrideConfiguration {
                    it.retryStrategy { retry -> retry.maxAttempts(maxAttempts) }
                    it.apiCallTimeout(callTimeout)
                }
                .build()
            val presigner = S3Presigner.builder()
                .endpointOverride(uri)
                .region(awsRegion)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Configuration)
                .build()
            return S3ObjectStore(client, presigner, bucket)
        }
    }
}
