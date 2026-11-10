package com.ssafy.eolssu.data.user

import kotlin.test.Test
import kotlin.test.assertEquals

class TermsDocumentTest {
    @Test
    fun keepsBodyTextAndDropsHeadStyleAndInlineTags() {
        val html = """<!doctype html><html lang="ko"><head><meta charset="utf-8"><title>얼쑤 이용약관</title>
            <style>body{max-width:48rem}h1,h2{line-height:1.4}</style></head><body><main>
            <h1>얼쑤 이용약관</h1><p>개인정보는 <a href="privacy">개인정보 처리방침</a>을
              따릅니다.</p>
            <h2>2. 서비스</h2><ul><li>곡&middot;악보 이용</li><li>연주 &amp; 채점</li></ul>
            <h3>부칙</h3><p>시행일: 2026-11-03<br>문의: 운영자</p>
            </main></body></html>"""
        assertEquals(
            listOf(
                TermsBlock.Heading(1, "얼쑤 이용약관"),
                TermsBlock.Paragraph("개인정보는 개인정보 처리방침을 따릅니다."),
                TermsBlock.Heading(2, "2. 서비스"),
                TermsBlock.Bullet("곡·악보 이용"),
                TermsBlock.Bullet("연주 & 채점"),
                TermsBlock.Heading(3, "부칙"),
                TermsBlock.Paragraph("시행일: 2026-11-03\n문의: 운영자"),
            ),
            parseTermsDocument(html),
        )
    }

    @Test
    fun decodesNumericReferencesAndIgnoresEmptyBlocks() {
        assertEquals(
            listOf(TermsBlock.Paragraph("‘운영자’ 🎵")),
            parseTermsDocument("<p>  </p><p>&#8216;운영자&#x2019; &#127925;</p><p></p>"),
        )
    }
}
