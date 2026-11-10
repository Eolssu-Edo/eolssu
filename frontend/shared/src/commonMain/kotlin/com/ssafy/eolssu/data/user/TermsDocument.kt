package com.ssafy.eolssu.data.user

/** 약관 본문의 한 덩어리. 서버 약관 문서(HTML)의 제목·문단·목록 항목을 화면에 그릴 수 있게 나눈 것이다. */
sealed interface TermsBlock {
    val text: String

    /** `h1`~`h6`. [level]은 1~6이다. */
    data class Heading(val level: Int, override val text: String) : TermsBlock

    data class Paragraph(override val text: String) : TermsBlock

    /** `li` 항목. */
    data class Bullet(override val text: String) : TermsBlock
}

// 주석은 여러 줄일 수 있다. `DOT_MATCHES_ALL`은 JS·Wasm에 없어 `[\s\S]`로 맞춘다.
private val TagPattern = Regex("""<(/?)([a-zA-Z][a-zA-Z0-9]*)[^>]*>|<!--[\s\S]*?-->|<![^>]*>""")
private val SkippedContentTags = setOf("head", "style", "script", "title", "template")
private val BlockBoundaryTags = setOf("p", "div", "section", "article", "main", "body", "ul", "ol", "table", "tr", "blockquote")

/**
 * 서버 약관 문서(HTML)를 [TermsBlock] 목록으로 바꾼다. 글자는 바꾸지 않고, 링크·강조 같은 글자 안 태그는 글자만 남긴다.
 * `head`·`style`·`script` 안의 내용은 본문이 아니라 버린다. `br`은 줄바꿈으로 남긴다.
 */
fun parseTermsDocument(html: String): List<TermsBlock> {
    val blocks = mutableListOf<TermsBlock>()
    val text = StringBuilder()
    var kind: (String) -> TermsBlock = { TermsBlock.Paragraph(it) }
    var skipUntil: String? = null

    fun flush() {
        val value = text.toString().lines().joinToString("\n") { it.trim() }.trim()
        if (value.isNotEmpty()) blocks += kind(value)
        text.clear()
        kind = { TermsBlock.Paragraph(it) }
    }

    var index = 0
    for (match in TagPattern.findAll(html)) {
        if (skipUntil == null) appendText(text, html.substring(index, match.range.first))
        index = match.range.last + 1
        val closing = match.groupValues[1] == "/"
        val tag = match.groupValues[2].lowercase()
        if (tag.isEmpty()) continue
        if (skipUntil != null) {
            if (closing && tag == skipUntil) skipUntil = null
            continue
        }
        when {
            tag in SkippedContentTags && !closing -> skipUntil = tag
            tag == "br" -> text.append('\n')
            tag.length == 2 && tag[0] == 'h' && tag[1] in '1'..'6' -> {
                flush()
                if (!closing) {
                    val level = tag[1].digitToInt()
                    kind = { TermsBlock.Heading(level, it) }
                }
            }
            tag == "li" -> {
                flush()
                if (!closing) kind = { TermsBlock.Bullet(it) }
            }
            tag in BlockBoundaryTags -> flush()
        }
    }
    if (skipUntil == null) appendText(text, html.substring(index))
    flush()
    return blocks
}

/** HTML 글자 조각을 붙인다. 연속 공백·줄바꿈은 공백 하나로 줄이고(HTML 표시 규칙), 문자 참조는 글자로 바꾼다. */
private fun appendText(out: StringBuilder, raw: String) {
    if (raw.isEmpty()) return
    val decoded = decodeEntities(raw)
    for (char in decoded) {
        val space = char == ' ' || char == '\n' || char == '\r' || char == '\t'
        if (space) {
            if (out.isNotEmpty() && out.last() != ' ' && out.last() != '\n') out.append(' ')
        } else {
            out.append(if (char == ' ') ' ' else char)
        }
    }
}

private val EntityPattern = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z]+);")
private val NamedEntities = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "middot" to "·")

private fun decodeEntities(raw: String): String = EntityPattern.replace(raw) { match ->
    val name = match.groupValues[1]
    val code = when {
        name.startsWith("#x") || name.startsWith("#X") -> name.substring(2).toIntOrNull(16)
        name.startsWith("#") -> name.substring(1).toIntOrNull()
        else -> null
    }
    when {
        code != null && code in 1..0x10FFFF -> codePointToString(code)
        code != null -> match.value
        else -> NamedEntities[name] ?: match.value
    }
}

private fun codePointToString(code: Int): String = if (code < 0x10000) {
    code.toChar().toString()
} else {
    val offset = code - 0x10000
    charArrayOf((0xD800 + (offset shr 10)).toChar(), (0xDC00 + (offset and 0x3FF)).toChar()).concatToString()
}
