package com.ssafy.eolssu

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform