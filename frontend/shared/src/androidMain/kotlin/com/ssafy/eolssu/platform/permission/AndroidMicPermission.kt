package com.ssafy.eolssu.platform.permission

import com.ssafy.eolssu.core.diagnostics.*
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import com.ssafy.eolssu.core.audio.MicPermission
import kotlinx.coroutines.CompletableDeferred

/**
 * `RECORD_AUDIO` 권한. 권한 창은 화면(`App(context)`)이 등록한 [launcher]로 띄우고, 결과는 [onResult]로 받는다.
 * 화면이 다시 만들어져도(태블릿 회전 등) 기다리던 요청이 결과를 받도록 앱에 하나만 둔다([of]).
 * [shouldShowRationale]은 화면이 Activity로 넣어 주며, 거부 결과가 왔을 때 ‘다시 묻지 않음’인지 가리는 데 쓴다.
 */
class AndroidMicPermission private constructor(private val context: Context) : MicPermission {
    var launcher: ActivityResultLauncher<String>? = null
    var shouldShowRationale: (() -> Boolean)? = null
    private var pending: CompletableDeferred<Boolean>? = null
    private var deniedForGood = false

    override fun isGranted(): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override suspend fun request(): Boolean {
        if (isGranted()) return true
        val launcher = launcher ?: return false
        val result = pending ?: CompletableDeferred<Boolean>().also {
            pending = it
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
        return result.await()
    }

    /** 거부 뒤 설명을 보일 필요가 없다고 하면(‘다시 묻지 않음’ 또는 정책) 앱에서는 다시 묻지 못한다. */
    override fun isPermanentlyDenied(): Boolean = !isGranted() && deniedForGood

    override fun openSettings(): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess

    fun onResult(granted: Boolean) {
        UserActionLog.event(QaKind.INPUT_VALIDATION, QaTarget.RECORDING, if (granted) QaOutcome.READY else QaOutcome.FAILED)
        deniedForGood = !granted && shouldShowRationale?.invoke() == false
        pending?.complete(granted)
        pending = null
    }

    companion object {
        private var instance: AndroidMicPermission? = null

        fun of(context: Context): AndroidMicPermission =
            instance ?: AndroidMicPermission(context.applicationContext).also { instance = it }
    }
}
