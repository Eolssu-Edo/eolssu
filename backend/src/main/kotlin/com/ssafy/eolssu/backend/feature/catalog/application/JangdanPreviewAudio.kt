package com.ssafy.eolssu.backend.feature.catalog.application

import java.net.URI

class JangdanPreviewAudio(
    private val hashes: Map<Long, String>,
    private val sign: (String) -> String?,
) {
    fun hasAudio(id: Long): Boolean = id in hashes

    fun playbackUrl(id: Long): URI? {
        val hash = hashes[id] ?: return null
        return runCatching {
            sign("jangdan-preview/v1/$id/$hash.m4a")?.let(::URI)?.takeIf {
                it.scheme == "https" && it.host != null && it.userInfo == null && it.fragment == null
            }
        }.getOrNull()
    }
}
