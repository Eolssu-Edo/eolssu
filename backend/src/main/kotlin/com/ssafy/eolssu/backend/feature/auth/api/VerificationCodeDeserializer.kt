package com.ssafy.eolssu.backend.feature.auth.api

import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer

/** 숫자로 받으면 선행 0을 잃을 수 있어 명세대로 JSON 문자열만 받는다. */
class VerificationCodeDeserializer : ValueDeserializer<String>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): String =
        if (parser.currentToken() == JsonToken.VALUE_STRING) parser.string
        else context.reportInputMismatch(String::class.java, "Verification code must be a JSON string")
}
