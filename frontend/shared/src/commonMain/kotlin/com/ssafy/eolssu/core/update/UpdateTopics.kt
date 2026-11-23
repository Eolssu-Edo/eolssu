package com.ssafy.eolssu.core.update

import com.ssafy.eolssu.core.storage.KeyValueStore

/**
 * 새 버전 푸시를 받을 FCM 토픽(S15P31E102-1257). 설치된 버전마다 토픽이 하나이므로,
 * 출시 뒤 구버전 토픽에만 보내면 이미 업데이트한 기기에는 가지 않는다. 발송 절차는 `docs/decisions/frontend-in-app-update.md`.
 */
object UpdateTopics {
    /** Android는 Play가 버전을 가리는 `versionCode`로 나눈다(예: `update-android-11`). */
    fun android(versionCode: Long): String = "update-android-$versionCode"

    /** iOS는 App Store 버전 문자열로 나눈다(예: `update-ios-1.0.3`). FCM 토픽 이름에는 숫자·영문·`-_.~%`만 쓸 수 있다. */
    fun ios(version: String): String = "update-ios-" + version.trim().filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "-_.~" }
}

/** FCM 토픽 구독. 결과는 [done]으로 알린다(true면 성공). Android는 Kotlin, iOS는 Swift(`FirebaseTopicSubscriptions`)가 구현한다. */
interface TopicSubscriptions {
    fun subscribe(topic: String, done: (Boolean) -> Unit)

    fun unsubscribe(topic: String, done: (Boolean) -> Unit)
}

/**
 * 앱을 켤 때 지금 버전의 토픽을 구독하고, 업데이트 전 버전의 토픽은 해제한다.
 * 구독은 매번 다시 요청해도 같은 결과이므로 실패하면 다음 실행에서 다시 시도한다.
 * 이전 토픽 해제에 성공해야 지금 토픽을 기억하므로, 해제가 실패하면 다음 실행에서 다시 해제한다.
 */
class UpdateTopicSync(private val topics: TopicSubscriptions, private val store: KeyValueStore) {
    fun sync(current: String) {
        topics.subscribe(current) { subscribed ->
            if (!subscribed) return@subscribe
            val previous = store.getString(SUBSCRIBED_KEY)
            if (previous == null || previous == current) {
                store.putString(SUBSCRIBED_KEY, current)
                return@subscribe
            }
            topics.unsubscribe(previous) { unsubscribed ->
                if (unsubscribed) store.putString(SUBSCRIBED_KEY, current)
            }
        }
    }

    private companion object {
        const val SUBSCRIBED_KEY = "update_push_topic"
    }
}
