import SwiftUI

@main
struct iOSApp: App {
    // 새 버전 푸시(S15P31E102-1257)의 Firebase 준비와 APNs 토큰 전달.
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
