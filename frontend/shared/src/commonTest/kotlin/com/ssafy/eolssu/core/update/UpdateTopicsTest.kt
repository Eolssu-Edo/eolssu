package com.ssafy.eolssu.core.update

import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateTopicsTest {
    private class FakeTopics(var subscribeOk: Boolean = true, var unsubscribeOk: Boolean = true) : TopicSubscriptions {
        val calls = mutableListOf<String>()

        override fun subscribe(topic: String, done: (Boolean) -> Unit) {
            calls += "+$topic"
            done(subscribeOk)
        }

        override fun unsubscribe(topic: String, done: (Boolean) -> Unit) {
            calls += "-$topic"
            done(unsubscribeOk)
        }
    }

    @Test
    fun topicNamesFollowInstalledVersion() {
        assertEquals("update-android-11", UpdateTopics.android(11))
        assertEquals("update-ios-1.0.3", UpdateTopics.ios("1.0.3"))
        // FCM 토픽 이름에 쓸 수 없는 글자는 뺀다.
        assertEquals("update-ios-1.0.3", UpdateTopics.ios(" 1.0.3 (베타)"))
    }

    @Test
    fun firstLaunchSubscribesCurrentVersionOnly() {
        val topics = FakeTopics()

        UpdateTopicSync(topics, FakeKeyValueStore()).sync("update-android-11")

        assertEquals(listOf("+update-android-11"), topics.calls)
    }

    @Test
    fun updatedAppLeavesPreviousVersionTopic() {
        val topics = FakeTopics()
        val store = FakeKeyValueStore()
        UpdateTopicSync(topics, store).sync("update-android-11")
        topics.calls.clear()

        // 업데이트한 뒤 처음 켤 때: 새 토픽을 구독하고 옛 토픽을 해제한다.
        UpdateTopicSync(topics, store).sync("update-android-12")
        assertEquals(listOf("+update-android-12", "-update-android-11"), topics.calls)

        // 그다음 실행부터는 옛 토픽을 다시 해제하지 않는다.
        topics.calls.clear()
        UpdateTopicSync(topics, store).sync("update-android-12")
        assertEquals(listOf("+update-android-12"), topics.calls)
    }

    @Test
    fun failedUnsubscribeIsRetriedNextLaunch() {
        val topics = FakeTopics()
        val store = FakeKeyValueStore()
        UpdateTopicSync(topics, store).sync("update-android-11")
        topics.unsubscribeOk = false
        UpdateTopicSync(topics, store).sync("update-android-12")

        topics.unsubscribeOk = true
        topics.calls.clear()
        UpdateTopicSync(topics, store).sync("update-android-12")

        assertEquals(listOf("+update-android-12", "-update-android-11"), topics.calls)
    }

    @Test
    fun failedSubscribeKeepsPreviousTopicUntilNextLaunch() {
        val topics = FakeTopics()
        val store = FakeKeyValueStore()
        UpdateTopicSync(topics, store).sync("update-android-11")
        topics.subscribeOk = false
        UpdateTopicSync(topics, store).sync("update-android-12")

        topics.subscribeOk = true
        topics.calls.clear()
        UpdateTopicSync(topics, store).sync("update-android-12")

        assertEquals(listOf("+update-android-12", "-update-android-11"), topics.calls)
    }
}
