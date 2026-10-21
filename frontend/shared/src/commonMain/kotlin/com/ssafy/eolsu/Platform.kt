package com.ssafy.eolsu

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform