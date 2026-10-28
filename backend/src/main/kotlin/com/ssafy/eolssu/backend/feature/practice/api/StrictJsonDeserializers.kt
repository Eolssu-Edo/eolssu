package com.ssafy.eolssu.backend.feature.practice.api

import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer

// Jackson 기본값은 96.7을 96으로 자르고 "96"·7·1을 각각 숫자·문자열·불리언으로 바꾼다.
// 명세의 STRING·INT·BOOLEAN 타입이 아닌 값은 400으로 거절하려고 JSON 토큰 종류를 그대로 확인한다. null은 Jackson이 먼저 처리한다.

class StrictStringDeserializer : ValueDeserializer<String>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): String =
        if (parser.currentToken() == JsonToken.VALUE_STRING) parser.string
        else context.reportInputMismatch(String::class.java, "Expected a JSON string")
}

class StrictIntDeserializer : ValueDeserializer<Int>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): Int =
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT && parser.numberType == JsonParser.NumberType.INT) parser.intValue
        else context.reportInputMismatch(Int::class.java, "Expected a JSON integer")
}

class StrictBooleanDeserializer : ValueDeserializer<Boolean>() {
    override fun deserialize(parser: JsonParser, context: DeserializationContext): Boolean = when (parser.currentToken()) {
        JsonToken.VALUE_TRUE -> true
        JsonToken.VALUE_FALSE -> false
        else -> context.reportInputMismatch(Boolean::class.java, "Expected a JSON boolean")
    }
}
