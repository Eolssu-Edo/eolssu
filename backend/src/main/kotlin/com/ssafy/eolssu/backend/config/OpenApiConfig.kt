package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorCode.*
import com.ssafy.eolssu.backend.feature.auth.api.RetryDetails
import com.ssafy.eolssu.backend.feature.auth.api.ResendDetails
import com.ssafy.eolssu.backend.feature.auth.api.AttemptDetails
import com.ssafy.eolssu.backend.feature.auth.api.SocialSignUpDetails
import com.ssafy.eolssu.backend.feature.auth.api.MergeDetails
import com.ssafy.eolssu.backend.feature.growth.api.RejectionDetails
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.*
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.*
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.responses.*
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.security.*
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration(proxyBeanMethods = false)
@Profile("db | ops")
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI = OpenAPI().info(Info().title("얼쑤 API").version("1차")
        .description("같은 출처에서 먼저 로그인한 뒤 사용합니다. access_token·refresh_token은 HttpOnly 쿠키이며 브라우저가 전송합니다. Swagger Authorize에서 쿠키를 직접 설정할 수 없고 Bearer 헤더는 사용하지 않습니다."))
        .servers(listOf(Server().url("/")))

    @Bean
    fun businessApiDocumentation() = OpenApiCustomizer { api ->
        val components = api.components ?: Components().also { api.components = it }
        components.addSecuritySchemes("accessCookie", cookie("access_token"))
        components.addSecuritySchemes("refreshCookie", cookie("refresh_token"))
        components.addSchemas("ApiErrorResult", ObjectSchema()
            .addProperty("errorCode", StringSchema().description("클라이언트 분기용 코드")._enum(ErrorCode.entries.map { it.name }))
            .addProperty("details", ComposedSchema().oneOf(listOf(RetryDetails::class.java,ResendDetails::class.java,AttemptDetails::class.java,RejectionDetails::class.java,MergeDetails::class.java,SocialSignUpDetails::class.java).map { model(components,it) }).description("있을 때만 포함. 가입 signupTicket·계정 연결 mergeTicket은 인증 증명값이므로 로그·공유에 남기지 않습니다."))
            .required(listOf("errorCode")))
        components.addSchemas("ApiError", envelope(schemaRef("ApiErrorResult"), false))
        for (doc in apiContracts) {
            val operation = api.paths?.get(doc.path)?.readOperationsMap()?.get(doc.method)
                ?: error("Documented API mapping is missing: ${doc.method} ${doc.path}")
            operation.operationId = doc.id
            operation.summary = doc.summary
            operation.description = doc.description
            operation.tags = listOf(doc.tag)
            operation.security = doc.security?.let { listOf(SecurityRequirement().addList(it)) } ?: emptyList()
            if(doc.id=="kakaoSecurityEvent") {
                operation.requestBody=RequestBody().required(true).content(Content().addMediaType("application/secevent+jwt",
                    MediaType().schema(StringSchema().maxLength(16384).description("카카오가 서명한 SET 원문. 실제 토큰을 Swagger에 저장하지 않습니다."))))
                operation.responses=ApiResponses()
                    .addApiResponse("202",ApiResponse().description("검증·DB 처리 완료. 본문 없음"))
                    .addApiResponse("400",ApiResponse().description("SET 검증 실패").content(jsonContent(ObjectSchema()
                        .addProperty("err",StringSchema()._enum(listOf("invalid_request","invalid_key","invalid_issuer","invalid_audience")))
                        .addProperty("description",StringSchema()).required(listOf("err","description")))))
                    .addApiResponse("503",ApiResponse().description("일시 장애·설정 누락·처리 시간 초과. 재전송 필요, 본문 없음"))
                continue
            }
            // 연습 기록 저장은 memo 생략과 null을 구분하려고 JSON 노드로 받는다. 생성된 JsonNode 스키마 대신 실제 본문을 적는다.
            if (doc.id == "savePracticeRecord") {
                operation.requestBody = RequestBody().required(false)
                    .description("본문 생략·{}·memo 생략은 메모를 유지하고 null은 지웁니다. 문자열은 앞뒤 공백을 자르고 비면 지우며, 자른 뒤 500자를 넘으면 400입니다.")
                    .content(jsonContent(ObjectSchema().addProperty("memo", StringSchema().nullable(true).maxLength(500))))
            }
            // 본문 없는 POST도 기존 보안 필터가 application/json을 요구한다.
            // 선택적인 {}를 제공해 UI의 Try it out이 Content-Type을 설정하도록 한다. 서버는 이 본문을 읽지 않는다.
            if (doc.method == PathItem.HttpMethod.POST && operation.requestBody == null) {
                operation.requestBody = RequestBody().required(false)
                    .description("업무 본문은 없습니다. Swagger UI 실행 시 선택적인 {}를 보내 application/json 헤더를 설정합니다. 이 본문은 서버가 읽지 않습니다.")
                    .content(jsonContent(ObjectSchema().example(emptyMap<String,Any>())))
            }
            if (doc.requestExamples.isNotEmpty()) {
                val media = operation.requestBody?.content?.get("application/json")
                    ?: error("Documented JSON request body is missing: ${doc.method} ${doc.path}")
                media.example = null
                // 순서를 지켜 첫 예시가 Swagger UI의 기본 선택이 되게 한다.
                media.examples = linkedMapOf<String, Example>().apply {
                    for (example in doc.requestExamples) put(example.name, Example().summary(example.summary).description(example.description).value(example.value))
                }
            }
            operation.responses = ApiResponses()
            if (doc.id in setOf("relayIosSocialLogin","startSocialLogin","startSocialLink","socialLogin","mergeSocialAccount","linkSocialAccount","startSocialWithdrawal","confirmSocialWithdrawal")) {
                operation.parameters?.filter { it.name == "provider" }?.forEach { it.schema=StringSchema()._enum(listOf("kakao","google","naver")) }
            }
            for (status in doc.statuses) {
                val response = ApiResponse().description(if (status == 204) "성공, 응답 본문 없음" else doc.summary)
                if (doc.result != null) {
                    val result = model(components, doc.result)
                    val name = "${doc.id}Response"
                    components.addSchemas(name, envelope(result, true))
                    response.content = jsonContent(schemaRef(name))
                }
                if (doc.setsCookies) response.addHeaderObject("Set-Cookie", Header().schema(StringSchema())
                    .description("HttpOnly·SameSite=Lax 쿠키 발급·교체 또는 삭제. access_token Path=/api, refresh_token Path=/api/auth. Secure는 AUTH_COOKIE_SECURE(기본 true)를 따릅니다."))
                if (doc.id in setOf("startSocialLogin","startSocialLink","startSocialWithdrawal")) response.addHeaderObject("Set-Cookie",Header().schema(StringSchema()).description("oauth_flow_{provider} 결속 쿠키: HttpOnly·SameSite=Lax·Path=/api·Max-Age=600. Secure는 AUTH_COOKIE_SECURE를 따릅니다."))
                if (doc.id in setOf("playReferenceAudio", "playModelAudio")) {
                    response.addHeaderObject("Location", Header().schema(StringSchema().format("uri")).description("900초 동안 유효한 서명 GET URL"))
                    response.addHeaderObject("Cache-Control", Header().schema(StringSchema()._enum(listOf("no-store"))))
                }
                if (doc.id == "relayIosSocialLogin") {
                    response.addHeaderObject("Location", Header().schema(StringSchema()).description("com.ssafy.eolssu://oauth/{provider}/callback과 허용한 쿼리"))
                    response.addHeaderObject("Cache-Control", Header().schema(StringSchema()._enum(listOf("no-store"))))
                    response.addHeaderObject("Referrer-Policy", Header().schema(StringSchema()._enum(listOf("no-referrer"))))
                }
                if (doc.id == "getNotationSymbolImage") {
                    response.content = Content().addMediaType("image/svg+xml", MediaType().schema(StringSchema().format("binary")))
                }
                if (doc.id == "getModelAudioPerformance") {
                    response.content = jsonContent(ObjectSchema().description("생성기가 보존한 공통 악보 수행 시간표 원문"))
                    response.addHeaderObject("Cache-Control", Header().schema(StringSchema()._enum(listOf("no-store"))))
                }
                operation.responses.addApiResponse(status.toString(), response)
            }
            val common = listOf(INVALID_REQUEST,FORBIDDEN,METHOD_NOT_ALLOWED,NOT_ACCEPTABLE,INTERNAL_SERVER_ERROR) +
                (if (doc.method != PathItem.HttpMethod.GET) listOf(UNSUPPORTED_MEDIA_TYPE) else emptyList()) +
                (if (doc.security != null) listOf(UNAUTHENTICATED) else emptyList())
            for ((status,codes) in (common + doc.errors).distinct().groupBy { it.status.value() }) {
                val media = MediaType().schema(schemaRef("ApiError"))
                for (code in codes) media.addExamples(code.name, Example().summary(code.message).value(linkedMapOf(
                    "httpStatus" to code.status.name,"isSuccess" to false,"message" to code.message,"code" to status,
                    "result" to mapOf("errorCode" to code.name),
                )))
                operation.responses.addApiResponse(status.toString(),ApiResponse().description(codes.joinToString { "${it.name}: ${it.message}" })
                    .content(Content().addMediaType("application/json",media)))
            }
            // 모르는 제공자는 공통 오류 envelope 없이 본문 없는 404다.
            if (doc.id == "relayIosSocialLogin") operation.responses.addApiResponse("404", ApiResponse().description("모르는 제공자, 응답 본문 없음"))
            operation.parameters?.filter { it.name == "Idempotency-Key" }?.forEach {
                it.description = "출력 가능한 ASCII 1~100자. 재시도 시 같은 키를 사용합니다."
                it.schema = StringSchema().pattern("[!-~]{1,100}").minLength(1).maxLength(100)
            }
        }
        documentFileResponses(api)
        // 커스텀 JSON 역직렬화는 JVM Long이어도 문자열만 받으므로 생성된 타입을 실제 wire 형식에 맞춘다.
        components.schemas?.get("RecordScoreViewRequest")?.properties?.set("songId",StringSchema().pattern("0*[1-9][0-9]*").description("양의 정수 ID를 JSON 문자열로 전송"))
        for ((name,property) in listOf("SignUpRequest" to "password","PasswordChangeRequest" to "newPassword","PasswordResetRequest" to "newPassword")) {
            components.schemas?.get(name)?.properties?.get(property)?.apply {
                minLength = 8; maxLength = 128; format = "password"; writeOnly = true
                description = "영문·숫자·특수문자 포함 8~128자(코드 포인트). 공백은 특수문자로 세지 않습니다."
            }
        }
    }

    // JSON envelope를 쓰지 않는 기존 HTML/파일 경로도 전체 매핑 검사의 대상이다.
    private fun documentFileResponses(api: OpenAPI) {
        val htmlPages = linkedMapOf(
            "/api/terms/documents/{version}/{document}" to ("getLegalVersionPage" to "버전별 약관 HTML"),
            "/api/terms/documents/current/{document}" to ("getCurrentLegalPage" to "현행 약관 HTML"),
            "/api/terms/documents/account-deletion" to ("getAccountDeletionPage" to "계정 삭제 안내 HTML"),
        )
        for ((path, names) in htmlPages) {
            val operation = api.paths[path]?.get ?: error("Legal page mapping is missing")
            operation.operationId = names.first
            operation.summary = names.second
            operation.description = "공개 준비가 끝난 문서의 HTML입니다. 등록된 버전·문서 경로만 공개하며 미준비는503입니다. JSON 성공 envelope를 사용하지 않습니다."
            operation.tags = listOf("약관")
            operation.security = emptyList()
            operation.responses = ApiResponses()
                .addApiResponse("200", ApiResponse().description(names.second).content(Content().addMediaType("text/html",MediaType().schema(StringSchema()))))
                .addApiResponse("404", ApiResponse().description("없는 문서, 본문 없음"))
                .addApiResponse("503", ApiResponse().description("공개 준비 중").content(Content().addMediaType("text/html",MediaType().schema(StringSchema()))))
                .addApiResponse("500", ApiResponse().description("서버 오류").content(jsonContent(schemaRef("ApiError"))))
        }
        val recording = api.paths["/api/recordings/read"]?.get ?: error("Recording read mapping is missing")
        recording.operationId = "readLocalRecording"
        recording.summary = "서명된 로컬 녹음 읽기"
        recording.description = "key·expires·signature가 모두 유효한 로컬 녹음만 반환합니다. 회원 쿠키 대신 URL 서명을 검증하며 local 저장소가 아니거나 접근할 수 없으면404입니다. 서명값을 예시에 기록하지 않습니다."
        recording.tags = listOf("연습")
        recording.security = emptyList()
        recording.responses = ApiResponses()
        for (status in listOf("200", "206")) recording.responses.addApiResponse(status,
            ApiResponse().description(if (status == "206") "요청한 바이트 구간" else "녹음 원본")
                .content(Content().addMediaType("audio/mp4",MediaType().schema(StringSchema().format("binary"))))
                .addHeaderObject("Cache-Control",Header().schema(StringSchema()._enum(listOf("no-store")))))
        recording.responses.addApiResponse("404",ApiResponse().description("접근할 수 없는 녹음, 본문 없음"))
            .addApiResponse("416",ApiResponse().description("만족할 수 없는 바이트 범위"))
            .addApiResponse("400",ApiResponse().description("잘못된 요청").content(jsonContent(schemaRef("ApiError"))))
            .addApiResponse("500",ApiResponse().description("서버 오류").content(jsonContent(schemaRef("ApiError"))))
    }

    private fun cookie(name: String) = SecurityScheme().type(SecurityScheme.Type.APIKEY).`in`(SecurityScheme.In.COOKIE).name(name)
    private fun schemaRef(name: String): Schema<Any> = Schema<Any>().`$ref`("#/components/schemas/$name")
    private fun jsonContent(schema: Schema<*>) = Content().addMediaType("application/json",MediaType().schema(schema))
    private fun model(components: Components,type: Class<*>): Schema<*> {
        val resolved = ModelConverters.getInstance().resolveAsResolvedSchema(AnnotatedType(type).resolveAsRef(true))
        resolved.referencedSchemas.forEach { (name,schema) -> components.addSchemas(name,schema) }
        return resolved.schema
    }
    private fun envelope(result: Schema<*>, success: Boolean) = ObjectSchema()
        .addProperty("httpStatus",StringSchema().description("HTTP 상태 이름(예: OK, CREATED, ACCEPTED)"))
        .addProperty("isSuccess",BooleanSchema()._default(success))
        .addProperty("message",StringSchema())
        .addProperty("code",IntegerSchema().description("HTTP 상태 숫자"))
        .addProperty("result",result).required(listOf("httpStatus","isSuccess","message","code","result"))
}
