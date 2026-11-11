package com.ssafy.eolssu.core.model

/**
 * 마이페이지 프로필 그림(S15P31E102-738): 앱에 담긴 절쑤 그림 12종. 서버에는 [key]만 저장한다(`users.profile_image`).
 * 순서는 고르기 화면의 순서다.
 */
enum class ProfileImage(val key: String) {
    Hello("HELLO"), Danso("DANSO"), Study("STUDY"), Look("LOOK"),
    Great("GREAT"), Jangdan("JANGDAN"), Again("AGAIN"), Surprise("SURPRISE"),
    Proud("PROUD"), Okay("OKAY"), Rest("REST"), Done("DONE");

    companion object {
        /** 키가 없거나(이전 서버) 모르는 키면 기본 그림이다. */
        fun fromKey(key: String?): ProfileImage = entries.firstOrNull { it.key == key } ?: Hello
    }
}
