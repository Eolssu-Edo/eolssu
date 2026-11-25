package com.ssafy.eolssu.platform.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ssafy.eolssu.core.storage.KeyValueStore
import com.ssafy.eolssu.core.update.TopicSubscriptions
import com.ssafy.eolssu.core.update.UpdateTopicSync
import com.ssafy.eolssu.core.update.UpdateTopics
import com.ssafy.eolssu.platform.storage.AndroidKeyValueStore

/**
 * 새 버전 푸시(FCM 토픽, S15P31E102-1257)의 Android 쪽 준비. `App(context)`가 앱을 켤 때 부른다.
 * 알림 채널을 만들고, 설치된 `versionCode`의 토픽(`update-android-<versionCode>`)을 구독한다.
 * 앱이 백그라운드일 때 온 푸시는 Firebase가 매니페스트의 기본 아이콘·채널로 바로 배너를 띄우고,
 * 앱을 쓰는 중에 온 푸시는 [UpdateNotificationService]가 같은 채널로 띄운다. 알림을 누르면 앱이 열리고 업데이트 안내가 뜬다.
 */
internal object AndroidUpdatePush {
    /** 매니페스트의 `com.google.firebase.messaging.default_notification_channel_id`와 같은 값이어야 한다. */
    const val CHANNEL_ID = "app_update"

    // 앱은 한국어만 지원한다. 채널 이름은 시스템 설정의 알림 목록에 보인다.
    private const val CHANNEL_NAME = "새 버전 알림"
    private const val PERMISSION_ASKED_KEY = "notification_permission_asked"

    fun start(context: Context) {
        val appContext = context.applicationContext
        createChannel(appContext)
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        UpdateTopicSync(FirebaseTopicSubscriptions(), AndroidKeyValueStore(appContext))
            .sync(UpdateTopics.android(PackageInfoCompat.getLongVersionCode(info)))
    }

    /**
     * 알림 권한(Android 13 이상)을 아직 묻지 않았으면 true를 돌려주고 물은 것으로 남긴다.
     * 거부한 사용자에게 실행마다 다시 묻지 않는다. 다시 켜려면 시스템 설정에서 켠다.
     */
    fun shouldAskNotificationPermission(context: Context, store: KeyValueStore = AndroidKeyValueStore(context)): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return false
        if (store.getString(PERMISSION_ASKED_KEY) != null) return false
        store.putString(PERMISSION_ASKED_KEY, "true")
        return true
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}

/** Firebase Messaging으로 토픽을 구독한다. Firebase 설정이 없는 빌드는 실패로 알리고 아무것도 하지 않는다. */
internal class FirebaseTopicSubscriptions : TopicSubscriptions {
    override fun subscribe(topic: String, done: (Boolean) -> Unit) = call(done) { it.subscribeToTopic(topic) }

    override fun unsubscribe(topic: String, done: (Boolean) -> Unit) = call(done) { it.unsubscribeFromTopic(topic) }

    private fun call(done: (Boolean) -> Unit, request: (FirebaseMessaging) -> Task<Void>) {
        val messaging = try {
            FirebaseMessaging.getInstance()
        } catch (e: IllegalStateException) {
            // Firebase 설정(`firebase.xml`)이 없어 FirebaseApp이 초기화되지 않았다.
            done(false)
            return
        }
        request(messaging).addOnCompleteListener { done(it.isSuccessful) }
    }
}

/**
 * 앱을 쓰는 중(포그라운드)에 온 새 버전 푸시를 배너로 띄운다. 백그라운드에서는 Firebase가 직접 띄우므로 여기로 오지 않는다.
 * 매니페스트(`androidApp`)에 `com.google.firebase.MESSAGING_EVENT`로 등록한다.
 * 기기 토큰으로는 보내지 않으므로 토큰 변경을 따로 처리하지 않는다. 토픽은 앱을 켤 때마다 다시 구독한다.
 */
class UpdateNotificationService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val content = message.notification ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        AndroidUpdatePush.createChannel(this)
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        val open = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, AndroidUpdatePush.CHANNEL_ID)
            .setSmallIcon(notificationIcon())
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
    }

    // 백그라운드 배너와 같은 아이콘을 쓰도록 매니페스트의 Firebase 기본 아이콘을 읽는다. 없으면 앱 아이콘을 쓴다.
    @Suppress("DEPRECATION")
    private fun notificationIcon(): Int {
        val metaData = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData
        return metaData?.getInt(ICON_KEY)?.takeIf { it != 0 } ?: applicationInfo.icon
    }

    private companion object {
        const val ICON_KEY = "com.google.firebase.messaging.default_notification_icon"
        const val NOTIFICATION_ID = 1257
    }
}
