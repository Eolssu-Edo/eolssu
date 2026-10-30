package com.ssafy.eolssu.data.user

/** Public, immutable release documents; opening these never sends an account token. */
enum class PublishedLegalDocument(val url: String) {
    Service("https://k15e102.p.ssafy.io/api/terms/documents/2026-10-30/service"),
    Privacy("https://k15e102.p.ssafy.io/api/terms/documents/2026-10-30/privacy"),
    Deletion("https://k15e102.p.ssafy.io/api/terms/documents/account-deletion"),
}
