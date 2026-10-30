package com.ssafy.eolssu.feature.guide

/** 빈 등록 목록은 Ready이며, 통신 실패와 미등록 악기를 구분한다. */
enum class ReferenceLoadState { Loading, Ready, Failed, NotRegistered }
