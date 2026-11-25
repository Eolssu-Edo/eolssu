package com.ssafy.eolssu.platform.audio

import com.ssafy.eolssu.core.audio.RecordingFiles
import kotlinx.io.Buffer
import kotlinx.io.Source

/**
 * 웹 녹음 파일(WAV)을 탭 메모리에 둔다(S15P31E102-1262). 브라우저에는 앱처럼 쓸 파일 시스템이 없다.
 * 페이지를 새로 고치거나 닫으면 녹음이 사라진다. 남은 업로드 기록은 [com.ssafy.eolssu.data.practice.RecordingUploads]가
 * 파일을 찾지 못해(`Missing`) 정리한다.
 */
class WebRecordingStore {
    private val files = mutableMapOf<String, ByteArray>()

    fun put(key: String, bytes: ByteArray) {
        files[key] = bytes
    }

    operator fun get(key: String): ByteArray? = files[key]

    fun remove(key: String): ByteArray? = files.remove(key)

    fun keys(): Set<String> = files.keys.toSet()
}

/**
 * [RecordingFiles]의 웹 구현. 녹음기가 만든 키(`web-recording-….wav`)를 보관 키(`kept/…`)로 옮긴다.
 * 녹음기는 새 녹음을 시작할 때 자기 키만 지우므로, 보관한 녹음은 업로드가 끝나거나 다음 녹음으로 바뀔 때까지 남는다.
 */
class WebRecordingFiles(private val store: WebRecordingStore) : RecordingFiles {
    override fun keep(path: String): String? {
        if (path.startsWith(KEPT_PREFIX)) return path.takeIf(::exists)
        val bytes = store.remove(path) ?: return null
        val kept = KEPT_PREFIX + path
        store.put(kept, bytes)
        return kept
    }

    override fun exists(path: String): Boolean = store[path] != null

    override fun delete(path: String) {
        store.remove(path)
    }

    override fun deleteKeptExcept(path: String?) {
        store.keys().filter { it.startsWith(KEPT_PREFIX) && it != path }.forEach(store::remove)
    }

    override fun size(path: String): Long? = store[path]?.size?.toLong()

    override fun open(path: String): Source = Buffer().apply { write(checkNotNull(store[path]) { "No recording $path" }) }

    private companion object {
        const val KEPT_PREFIX = "kept/"
    }
}
