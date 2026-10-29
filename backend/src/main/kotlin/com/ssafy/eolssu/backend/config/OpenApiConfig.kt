package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.common.error.ErrorCode.*
import com.ssafy.eolssu.backend.feature.auth.api.RetryDetails
import com.ssafy.eolssu.backend.feature.auth.api.ResendDetails
import com.ssafy.eolssu.backend.feature.auth.api.AttemptDetails
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
            .addProperty("details", ComposedSchema().oneOf(listOf(RetryDetails::class.java,ResendDetails::class.java,AttemptDetails::class.java,RejectionDetails::class.java).map { model(components,it) }).description("있을 때만 포함. 오류별 추가 정보이며 민감값 제외"))
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
            // 본문 없는 POST도 기존 보안 필터가 application/json을 요구한다.
            // 선택적인 {}를 제공해 UI의 Try it out이 Content-Type을 설정하도록 한다. 서버는 이 본문을 읽지 않는다.
            if (doc.method == PathItem.HttpMethod.POST && operation.requestBody == null) {
                operation.requestBody = RequestBody().required(false)
                    .description("업무 본문은 없습니다. Swagger UI 실행 시 선택적인 {}를 보내 application/json 헤더를 설정합니다. 이 본문은 서버가 읽지 않습니다.")
                    .content(jsonContent(ObjectSchema().example(emptyMap<String,Any>())))
            }
            operation.responses = ApiResponses()
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
            operation.parameters?.filter { it.name == "Idempotency-Key" }?.forEach {
                it.description = "출력 가능한 ASCII 1~100자. 재시도 시 같은 키를 사용합니다."
                it.schema = StringSchema().pattern("[!-~]{1,100}").minLength(1).maxLength(100)
            }
        }
        // 커스텀 JSON 역직렬화는 JVM Long이어도 문자열만 받으므로 생성된 타입을 실제 wire 형식에 맞춘다.
        components.schemas?.get("RecordScoreViewRequest")?.properties?.set("songId",StringSchema().pattern("0*[1-9][0-9]*").description("양의 정수 ID를 JSON 문자열로 전송"))
        for ((name,property) in listOf("SignUpRequest" to "password","PasswordChangeRequest" to "newPassword","PasswordResetRequest" to "newPassword")) {
            components.schemas?.get(name)?.properties?.get(property)?.apply {
                minLength = 8; maxLength = 128; format = "password"; writeOnly = true
                description = "영문·숫자·특수문자 포함 8~128자(코드 포인트). 공백은 특수문자로 세지 않습니다."
            }
        }
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
