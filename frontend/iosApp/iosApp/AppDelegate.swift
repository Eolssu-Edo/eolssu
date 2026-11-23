import FirebaseCore
import FirebaseMessaging
import Shared
import UIKit
import UserNotifications

/// 새 버전 푸시(FCM 토픽, S15P31E102-1257). Firebase를 준비하고 알림 권한을 물은 뒤, 설치된 버전의 토픽을 구독한다.
/// 발송은 출시 뒤 Firebase 콘솔에서 구버전 토픽으로 보낸다(docs/decisions/frontend-in-app-update.md).
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // GoogleService-Info.plist가 없는 빌드는 푸시 없이 시작한다.
        guard Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else { return true }
        FirebaseApp.configure()
        UNUserNotificationCenter.current().delegate = self
        // iOS는 처음 한 번만 묻고, 그 뒤에는 설정 앱에서 바꾼다.
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
        application.registerForRemoteNotifications()
        // APNs 토큰을 받기 전에 요청한 구독은 Firebase가 기다렸다가 처리한다.
        IosUpdatePushKt.syncIosUpdateTopic(topics: FirebaseTopicSubscriptions())
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }

    // 앱을 쓰는 중에 온 푸시도 배너로 보인다. 누르면 앱이 앞으로 오고 업데이트 안내가 뜬다.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }
}

/// Kotlin `TopicSubscriptions`의 Firebase Messaging 구현. 결과는 성공 여부만 넘긴다.
final class FirebaseTopicSubscriptions: NSObject, TopicSubscriptions {
    func subscribe(topic: String, done: @escaping (KotlinBoolean) -> Void) {
        Messaging.messaging().subscribe(toTopic: topic) { error in done(KotlinBoolean(value: error == nil)) }
    }

    func unsubscribe(topic: String, done: @escaping (KotlinBoolean) -> Void) {
        Messaging.messaging().unsubscribe(fromTopic: topic) { error in done(KotlinBoolean(value: error == nil)) }
    }
}
