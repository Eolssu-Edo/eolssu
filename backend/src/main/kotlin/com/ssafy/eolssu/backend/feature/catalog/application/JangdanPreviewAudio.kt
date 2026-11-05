package com.ssafy.eolssu.backend.feature.catalog.application

import java.net.URI

class JangdanPreviewAudio(
    private val hashes: Map<Long, String>,
    private val sampleHashes: Map<String, String> = emptyMap(),
    private val sign: (String) -> String?,
) {
    fun hasAudio(id: Long): Boolean = id in hashes

    fun playbackUrl(id: Long): URI? {
        val hash = hashes[id] ?: return null
        return signedUrl("jangdan-preview/v1/$id/$hash.m4a")
    }

    fun hasSample(sample: String, hash: String): Boolean = sampleHashes[sample] == hash

    fun sampleUrl(sample: String, hash: String): URI? =
        if (hasSample(sample, hash)) signedUrl("jangdan-practice/v1/$sample/$hash.m4a") else null

    private fun signedUrl(key: String): URI? = runCatching {
        sign(key)?.let(::URI)?.takeIf {
            it.scheme == "https" && it.host != null && it.userInfo == null && it.fragment == null
        }
    }.getOrNull()
}
