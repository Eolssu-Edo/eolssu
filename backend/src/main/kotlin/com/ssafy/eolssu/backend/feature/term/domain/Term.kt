package com.ssafy.eolssu.backend.feature.term.domain

data class Term(
    val id: Long,
    val code: String,
    val title: String,
    val required: Boolean,
    val version: String,
    val contentUrl: String,
)
