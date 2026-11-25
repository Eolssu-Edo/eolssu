package com.ssafy.eolssu.platform.update

import com.ssafy.eolssu.core.update.TopicSubscriptions
import com.ssafy.eolssu.core.update.UpdateTopicSync
import com.ssafy.eolssu.core.update.UpdateTopics
import com.ssafy.eolssu.platform.storage.IosKeyValueStore
import platform.Foundation.NSBundle

/**
 * 새 버전 푸시(FCM 토픽, S15P31E102-1257)의 iOS 토픽 구독. Firebase iOS SDK는 Swift 패키지라 Kotlin에서 직접 부를 수 없으므로,
 * iosApp의 `AppDelegate`가 Firebase를 준비한 뒤 Swift 구현(`FirebaseTopicSubscriptions`)을 넘겨 부른다.
 * 설치된 App Store 버전의 토픽(`update-ios-<버전>`)을 구독하고, 업데이트 전 버전의 토픽은 해제한다.
 */
fun syncIosUpdateTopic(topics: TopicSubscriptions) {
    val version = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: return
    UpdateTopicSync(topics, IosKeyValueStore()).sync(UpdateTopics.ios(version))
}
