package com.ssafy.eolssu.platform.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import com.ssafy.eolssu.core.audio.MicPermission
import kotlinx.coroutines.CompletableDeferred

/**
 * `RECORD_AUDIO` 권한. 권한 창은 화면(`App(context)`)이 등록한 [launcher]로 띄우고, 결과는 [onResult]로 받는다.
 * 화면이 다시 만들어져도(태블릿 회전 등) 기다리던 요청이 결과를 받도록 앱에 하나만 둔다([of]).
 */
class AndroidMicPermission private constructor(private val context: Context) : MicPermission {
    var launcher: ActivityResultLauncher<String>? = null
    private var pending: CompletableDeferred<Boolean>? = null

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

    fun onResult(granted: Boolean) {
        pending?.complete(granted)
        pending = null
    }

    companion object {
        private var instance: AndroidMicPermission? = null

        fun of(context: Context): AndroidMicPermission =
            instance ?: AndroidMicPermission(context.applicationContext).also { instance = it }
    }
}
