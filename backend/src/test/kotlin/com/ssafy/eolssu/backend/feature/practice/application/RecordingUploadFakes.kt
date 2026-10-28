package com.ssafy.eolssu.backend.feature.practice.application

import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorage
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingStorageException
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingUploadRepository
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingUploadTarget
import com.ssafy.eolssu.backend.feature.practice.domain.UploadedRecording
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/** 녹음 저장소 메모리 대역. 키별 내용을 보관하고, 실패를 흉내 낼 수 있다. */
class InMemoryRecordingStorage : RecordingStorage {
    val objects = linkedMapOf<String, ByteArray>()
    var failPuts = false

    override fun put(key: String, file: Path) {
        if (failPuts) throw RecordingStorageException("storage unavailable")
        objects[key] = Files.readAllBytes(file)
    }

    override fun delete(key: String) {
        objects.remove(key)
    }

    override fun readUrl(key: String, ttl: Duration) = "memory://$key?ttl=${ttl.toSeconds()}"
}

/** `practice_sessions`의 업로드 관련 칸만 흉내 낸다. 조건부 갱신은 `RECORDING`일 때만 바꾼다. */
class InMemoryRecordingUploadRepository : RecordingUploadRepository {
    class Session(var userId: Long, var target: RecordingUploadTarget, var recording: UploadedRecording? = null, var attempt: Int = 0)

    val sessions = mutableMapOf<Long, Session>()
    var failUpdates = false

    /** 조건부 갱신 직전에 끼어드는 다른 요청·배치. */
    var beforeUpdate: (Long) -> Unit = {}

    override fun findOwned(sessionId: Long, userId: Long) = sessions[sessionId]?.takeIf { it.userId == userId }?.target

    override fun markUploaded(sessionId: Long, userId: Long, recording: UploadedRecording): Boolean {
        beforeUpdate(sessionId)
        if (failUpdates) throw IllegalStateException("db unavailable")
        val session = sessions[sessionId]?.takeIf { it.userId == userId && it.target.status == RecordingUploadTarget.RECORDING } ?: return false
        session.target = session.target.copy(status = "PENDING")
        session.recording = recording
        session.attempt++
        return true
    }
}
