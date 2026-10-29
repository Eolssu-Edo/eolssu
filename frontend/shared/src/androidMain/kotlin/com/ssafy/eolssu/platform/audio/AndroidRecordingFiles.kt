package com.ssafy.eolssu.platform.audio

import android.content.Context
import com.ssafy.eolssu.core.audio.RecordingFiles
import java.io.File

/**
 * 업로드를 기다리는 녹음을 앱 내부 저장소의 `pending-recordings/`에 둔다.
 * [AndroidAudioRecorder]는 새 녹음을 시작할 때 캐시의 `recordings/`를 비우고, 캐시는 시스템이 지울 수도 있어 따로 옮긴다.
 */
class AndroidRecordingFiles(context: Context) : RecordingFiles {
    private val directory = File(context.applicationContext.filesDir, DIRECTORY)

    override fun keep(path: String): String? {
        val source = File(path)
        if (!source.isFile) return null
        if (source.parentFile == directory) return source.path
        directory.mkdirs()
        val target = File(directory, source.name)
        if (source.renameTo(target)) return target.path
        return try {
            source.copyTo(target, overwrite = true)
            source.delete()
            target.path
        } catch (e: Exception) {
            target.delete()
            null
        }
    }

    override fun exists(path: String): Boolean = File(path).isFile

    override fun delete(path: String) {
        File(path).delete()
    }

    override fun deleteKeptExcept(path: String?) {
        directory.listFiles()?.filter { it.path != path }?.forEach { it.delete() }
    }

    private companion object {
        const val DIRECTORY = "pending-recordings"
    }
}
