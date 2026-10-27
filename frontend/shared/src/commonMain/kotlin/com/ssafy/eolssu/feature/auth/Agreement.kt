package com.ssafy.eolssu.feature.auth

import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_agreement_age
import eolssu.shared.generated.resources.auth_agreement_news
import eolssu.shared.generated.resources.auth_agreement_privacy
import eolssu.shared.generated.resources.auth_agreement_recording
import eolssu.shared.generated.resources.auth_agreement_recording_detail
import eolssu.shared.generated.resources.auth_agreement_terms
import org.jetbrains.compose.resources.StringResource

/**
 * AUTH-01의 동의 항목. 나열 순서가 화면 순서다.
 * [hasTerms]이면 '보기'로 AUTH-02 약관 화면을 열고, 약관 화면 제목은 [label]을 쓴다.
 */
enum class Agreement(
    val label: StringResource,
    val required: Boolean,
    val hasTerms: Boolean = false,
    val detail: StringResource? = null,
) {
    Age(Res.string.auth_agreement_age, required = true),
    Terms(Res.string.auth_agreement_terms, required = true, hasTerms = true),
    Privacy(Res.string.auth_agreement_privacy, required = true, hasTerms = true),
    Recording(Res.string.auth_agreement_recording, required = true, hasTerms = true, detail = Res.string.auth_agreement_recording_detail),
    News(Res.string.auth_agreement_news, required = false),
}
